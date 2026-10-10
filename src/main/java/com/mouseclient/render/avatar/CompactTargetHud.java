package com.mouseclient.render.avatar;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.GuiShapeRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.CornerRadii;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.hud.HudIcon;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.mode.GlintMode;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.misc.YoutubeModeModule;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.network.HudStyle;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.time.HudLayout;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import com.mouseclient.util.time.TweenAnimation;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class CompactTargetHud {
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();
   private static final TweenAnimation healthAnimation = new TweenAnimation();
   private static final TweenAnimation absorptionAnimation = new TweenAnimation();
   private static final TweenAnimation equipmentAnimation = new TweenAnimation();
   private static final TweenAnimation portraitTransition = new TweenAnimation();
   private static final Map<EntityType<?>, Identifier> entityTextures = new HashMap<>();
   private static final Map<Identifier, float[]> faceUvRegions = new HashMap<>();
   private static final ArrayList<CompactTargetHud.TargetHitParticle> hitParticles = new ArrayList<>();
   private static LivingEntity displayTarget;
   private static LivingEntity portraitTarget;
   private static LivingEntity previousPortraitTarget;
   private static long lastTargetSeenMillis;
   private static int previousHurtTime;

   private static int readBigEndianInt(byte[] data, int byteOffset) {
      return (data[byteOffset] & 0xFF) << 24 | (data[byteOffset + 1] & 0xFF) << 16 | (data[byteOffset + 2] & 0xFF) << 8 | data[byteOffset + 3] & 0xFF;
   }

   private static boolean isPngHeader(byte[] header) {
      return header.length == 24
         && (header[0] & 255) == 137
         && header[1] == 80
         && header[2] == 78
         && header[3] == 71
         && header[12] == 73
         && header[13] == 72
         && header[14] == 68
         && header[15] == 82;
   }

   private static float snapToHalfPixel(float value) {
      return Math.round(value * 2.0F) / 2.0F;
   }

   public static void render(MinecraftClient client, float screenWidth, float screenHeight, float tickDelta) {
      long nowMillis = System.currentTimeMillis();
      LivingEntity target = findDisplayTarget(client);
      if (target != null) {
         displayTarget = target;
         lastTargetSeenMillis = nowMillis;
      }

      boolean shouldHide = displayTarget == null || !displayTarget.isAlive() || displayTarget.isRemoved() || nowMillis - lastTargetSeenMillis > (800L);
      visibilityAnimation.update();
      visibilityAnimation.animateTo(shouldHide ? 0.0 : 1.0, (0.18D), Easings.QUART_OUT);
      float visibility = visibilityAnimation.floatValue();
      if (!(visibility <= (0.01F)) && displayTarget != null) {
         if (displayTarget != portraitTarget) {
            previousPortraitTarget = portraitTarget;
            portraitTarget = displayTarget;
            portraitTransition.setValue(0.0);
            portraitTransition.animateTo(1.0, (0.32D), Easings.EXPO_OUT);
         }

         portraitTransition.update();
         float health = YoutubeModeModule.getInstance().isEnabled() && displayTarget.isInvisible()
            ? (1000.0F)
            : (displayTarget instanceof PlayerEntity ? CombatTargeting.displayedHealth(displayTarget) : displayTarget.getHealth());
         float maxHealth = Math.max(1.0F, Math.max(displayTarget.getMaxHealth(), health));
         float boundedHealth = MathHelper.clamp(health, 0.0F, maxHealth);
         float absorption = MathHelper.clamp(displayTarget.getAbsorptionAmount(), 0.0F, maxHealth);
         boolean hasAbsorption = absorption > (0.01F);
         float healthRatio = boundedHealth / maxHealth;
         float absorptionRatio = hasAbsorption ? absorption / maxHealth : 0.0F;
         healthAnimation.update();
         healthAnimation.animateTo(healthRatio, (0.5D), Easings.EXPO_OUT);
         if (hasAbsorption) {
            absorptionAnimation.update();
            absorptionAnimation.animateTo(absorptionRatio, (0.5D), Easings.EXPO_OUT);
         } else {
            absorptionAnimation.setValue(0.0);
         }

         float[] anchor = HudLayout.resolveAnchor(("target"), screenWidth / 2.0F - (46.0F), screenHeight / 2.0F + (24.0F));
         HudLayout.registerBounds(("target"), anchor[0], anchor[1], (92.0F), (30.0F));
         HudRenderContext.pushIdentityScale(anchor[0] + (46.0F), anchor[1] + (15.0F), visibility);
         float panelX = anchor[0];
         float panelY = anchor[1];
         float hideOffset = 1.0F - (float)Easings.EXPO_OUT.apply(visibility);
         HudPanelRenderer.drawGlassPanel(panelX, panelY, (92.0F), (30.0F), visibility, (7.0F));
         float portraitBlend = portraitTransition.floatValue();
         float portraitX = panelX + (4.0F) - hideOffset * (16.0F);
         float portraitY = panelY + (4.0F);
         GuiShapeRenderer.drawShadow(
            snapToHalfPixel(portraitX), snapToHalfPixel(portraitY), (22.0F), (22.0F), 2.0F, (-16777216), visibility * (0.2F), CornerRadii.uniform((4.0F))
         );
         if (previousPortraitTarget != null && portraitBlend < (0.999F)) {
            drawPortrait(client, previousPortraitTarget, portraitX - (5.0F) * portraitBlend, portraitY, (22.0F), visibility * (1.0F - portraitBlend));
         }

         drawPortrait(client, displayTarget, portraitX + (5.0F) * (1.0F - portraitBlend), portraitY, (22.0F), visibility * (previousPortraitTarget != null ? portraitBlend : 1.0F));
         if (HudSettingsModule.getInstance().headHitParticles.getValue() && displayTarget.hurtTime > previousHurtTime) {
            spawnHitParticles(portraitX + (11.0F), portraitY + (11.0F), nowMillis);
         }

         previousHurtTime = displayTarget.hurtTime;
         equipmentAnimation.update();
         equipmentAnimation.animateTo(hasEquipment(displayTarget) ? 1.0 : 0.0, (0.35D), Easings.QUAD_OUT);
         float nameY = panelY + (7.0F) - hideOffset * (6.0F);
         float barY = panelY + (18.0F) + hideOffset * (6.0F);
         float contentX = panelX + (30.0F);
         float contentRight = panelX + (88.0F);
         float contentWidth = contentRight - contentX;
         String healthText = formatHealth(boundedHealth);
         float healthTextWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, maxHealth >= (100.0F) ? ("000.0") : ("00.0"), (6.0F));
         float barWidth = Math.max((12.0F), contentWidth - healthTextWidth - (3.0F));
         drawName(displayTarget, contentX, nameY, contentWidth, visibility);
         float equipmentProgress = equipmentAnimation.floatValue();
         if (equipmentProgress > (0.01F)) {
            drawEquipment(
               displayTarget,
               panelX + (4.0F),
               panelY - (10.0F) + (1.0F - equipmentProgress) * (4.0F) - hideOffset * (6.0F),
               contentRight - panelX - (4.0F),
               visibility * equipmentProgress
            );
         }

         drawHealthBar(contentX, barY, barWidth, visibility);
         GuiTextRenderer.draw(
            GuiFont.SEMI_BOLD, healthText, contentX + barWidth + (3.0F), barY - (1.5F), (6.0F), GuiVisuals.multiplyAlpha(HudStyle.accentColor, visibility)
         );
         drawHitParticles(nowMillis, visibility);
         HudRenderContext.popTransform();
      } else {
         if (shouldHide && visibility <= (0.01F)) {
            displayTarget = null;
         }

         portraitTarget = null;
         previousPortraitTarget = null;
         portraitTransition.setValue(1.0);
      }
   }

   private static void drawHealthBar(float x, float y, float width, float alpha) {
      CornerRadii radii = CornerRadii.uniform((2.5F));
      int accent = HudStyle.accentColor;
      int darkAccent = ArgbColor.lerp(accent, (-16777216), (0.35F));
      float backgroundAlpha = alpha * (0.15F);
      int backgroundTop = ArgbColor.multiplyAlpha(accent, backgroundAlpha);
      int backgroundBottom = ArgbColor.multiplyAlpha(darkAccent, backgroundAlpha);
      GuiShapeRenderer.fillGradient(x, y, width, (5.0F), backgroundTop, backgroundTop, backgroundBottom, backgroundBottom, 1.0F, radii);
      int healthTop = ArgbColor.multiplyAlpha(accent, alpha);
      int healthBottom = ArgbColor.multiplyAlpha(darkAccent, alpha);
      float healthWidth = width * MathHelper.clamp(healthAnimation.floatValue(), 0.0F, 1.0F);
      if (healthWidth > (0.5F)) {
         GuiShapeRenderer.fillGradient(x, y, healthWidth, (5.0F), healthTop, healthTop, healthBottom, healthBottom, 1.0F, radii);
      }

      if (!ServerHelper.getInstance().server.isSelected(("FunTime"))) {
         float absorptionAlpha = alpha * (0.9F);
         int absorptionTop = ArgbColor.multiplyAlpha((-7717102), absorptionAlpha);
         int absorptionBottom = ArgbColor.multiplyAlpha((-1013958), absorptionAlpha);
         float absorptionWidth = Math.min(width, width * MathHelper.clamp(absorptionAnimation.floatValue(), 0.0F, 1.0F));
         if (absorptionWidth > (0.5F)) {
            GuiShapeRenderer.fillGradient(x, y, absorptionWidth, (5.0F), absorptionTop, absorptionTop, absorptionBottom, absorptionBottom, 1.0F, radii);
         }
      }
   }

   private static void drawEquipment(LivingEntity entity, float x, float y, float width, float alpha) {
      float snappedX = snapToHalfPixel(x);
      float snappedY = snapToHalfPixel(y);
      float right = snappedX + width;
      float cursorX = snappedX;

      for (EquipmentSlot slot : new EquipmentSlot[]{
         EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
      }) {
         ItemStack stack = entity.getEquippedStack(slot);
         if (!stack.isEmpty()) {
            if (cursorX + (8.0F) > right) {
               break;
            }

            GuiItemRenderer.render(stack, cursorX, snappedY, (8.0F), ItemRenderOptions.decorated(alpha).glint(GlintMode.OFF));
            cursorX += (9.0F);
         }
      }
   }

   private static Identifier rendererTexture(MinecraftClient client, LivingEntity entity) {
      try {
         EntityRenderManager dispatcher = client.getEntityRenderDispatcher();
         if (dispatcher.getRenderer(entity) instanceof LivingEntityRenderer renderer && dispatcher.getAndUpdateRenderState(entity, 1.0F) instanceof LivingEntityRenderState renderState) {
            Identifier id = renderer.getTexture(renderState);
            return id != null && client.getResourceManager().getResource(id).isPresent() ? id : null;
         }
      } catch (Throwable textureFailure) {
      }

      return null;
   }

   private static Identifier entityTexture(MinecraftClient client, LivingEntity entity) {
      if (entity instanceof AbstractClientPlayerEntity player) {
         return player.getSkin().body().texturePath();
      } else {
         EntityType entityType = entity.getType();
         if (entityTextures.containsKey(entityType)) {
            return entityTextures.get(entityType);
         }

         ResourceManager resources = client.getResourceManager();
         String entityPath = EntityType.getId(entityType).getPath();
         Identifier id = Identifier.ofVanilla("textures/entity/" + entityPath + ".png");
         if (resources.getResource(id).isEmpty()) {
            id = Identifier.ofVanilla("textures/entity/" + entityPath + "/" + entityPath + ".png");
         }

         if (resources.getResource(id).isEmpty()) {
            id = rendererTexture(client, entity);
         }

         entityTextures.put(entityType, id);
         return id;
      }
   }

   private static String formatHealth(float health) {
      int integerPart = (int)health;
      int decimalDigit = Math.round((health - integerPart) * (10.0F));
      if (decimalDigit >= 10) {
         return Integer.toString(integerPart + 1);
      } else {
         return decimalDigit <= 0 ? Integer.toString(integerPart) : integerPart + "." + decimalDigit;
      }
   }

   private static void drawPortrait(MinecraftClient client, LivingEntity entity, float x, float y, float size, float alpha) {
      if (!(alpha <= (0.004F))) {
         float snappedX = snapToHalfPixel(x);
         float snappedY = snapToHalfPixel(y);
         CornerRadii radii = CornerRadii.uniform((4.0F));
         Identifier id = entityTexture(client, entity);
         if (id == null) {
            float iconInset = size * (0.25F);
            GuiShapeRenderer.fill(snappedX, snappedY, size, size, ArgbColor.lerp((-15790575), -1, (0.15F)), alpha * (0.6F), radii);
            HudIcon.USER.draw(snappedX + iconInset, snappedY + iconInset, size * (0.5F), GuiVisuals.multiplyAlpha(HudPanelRenderer.secondaryTextColor(1.0F), alpha));
         } else {
            float[] uvRegion = faceUvRegion(client, id);
            String texturePath = id.toString();
            int color = GuiVisuals.multiplyAlpha(-1, alpha);
            Render2D.drawNearestImageRegion(texturePath, snappedX, snappedY, size, size, (4.0F), (0.25F), uvRegion[0], uvRegion[1], uvRegion[2], uvRegion[3], color);
            if (uvRegion[4] > (0.5F)) {
               Render2D.drawNearestImageRegion(texturePath, snappedX, snappedY, size, size, (4.0F), (0.25F), uvRegion[5], uvRegion[6], uvRegion[7], uvRegion[8], color);
            }

            float hurtProgress = entity.maxHurtTime > 0 ? (float)entity.hurtTime / entity.maxHurtTime : 0.0F;
            if (hurtProgress > 0.0F) {
               GuiShapeRenderer.fill(snappedX, snappedY, size, size, (-65536), alpha * hurtProgress * (0.55F), radii);
            }
         }
      }
   }

   private static void drawName(LivingEntity entity, float x, float y, float width, float alpha) {
      String name = entity.getName().getString();
      float right = x + Math.max(0.0F, width);
      GuiTextRenderer.drawFaded(
         GuiFont.SEMI_BOLD, name, x, y, (6.0F), GuiVisuals.multiplyAlpha(HudPanelRenderer.primaryTextColor(1.0F), alpha), x, right, (4.0F), false, true
      );
   }

   private static float[] faceUvRegion(MinecraftClient client, Identifier id) {
      float[] cachedRegion = faceUvRegions.get(id);
      if (cachedRegion != null) {
         return cachedRegion;
      }

      int textureWidth = 64;
      int textureHeight = 64;
      Optional resource = client.getResourceManager().getResource(id);
      if (resource.isPresent()) {
         boolean headerRead = false;

         try (InputStream input = ((Resource)resource.get()).getInputStream()) {
            byte[] header = input.readNBytes(24);
            if (isPngHeader(header)) {
               textureWidth = Math.max(1, readBigEndianInt(header, 16));
               textureHeight = Math.max(1, readBigEndianInt(header, 20));
               headerRead = true;
            }
         } catch (Exception error) {
         }

         if (!headerRead) {
            try (
               InputStream imageInput = ((Resource)resource.get()).getInputStream();
               NativeImage image = NativeImage.read(imageInput);
            ) {
               textureWidth = Math.max(1, image.getWidth());
               textureHeight = Math.max(1, image.getHeight());
            } catch (Exception imageFailure) {
            }
         }
      }

      float hasOverlay = textureWidth == 64 && textureHeight == 64 ? 1.0F : 0.0F;
      float[] uvRegion = new float[]{
         (8.0F) / textureWidth,
         (8.0F) / textureHeight,
         (16.0F) / textureWidth,
         (16.0F) / textureHeight,
         hasOverlay,
         (40.0F) / textureWidth,
         (8.0F) / textureHeight,
         (48.0F) / textureWidth,
         (16.0F) / textureHeight
      };
      faceUvRegions.put(id, uvRegion);
      return uvRegion;
   }

   private static void spawnHitParticles(float x, float y, long nowMillis) {
      hitParticles.replaceAll(
         existingParticle -> existingParticle.fadeFromMs() > 0L
            ? existingParticle
            : new CompactTargetHud.TargetHitParticle(existingParticle.startX(), existingParticle.startY(), existingParticle.offsetX(), existingParticle.offsetY(), existingParticle.size(), existingParticle.bornMs(), nowMillis)
      );
      ThreadLocalRandom random = ThreadLocalRandom.current();
      int particleCount = 10 + random.nextInt(4);

      for (int particleIndex = 0; particleIndex < particleCount; particleIndex++) {
         double angle = (6.283185307179586D) * (particleIndex + random.nextDouble((0.7D))) / particleCount;
         float distance = (20.0F) + random.nextFloat() * (14.0F);
         float size = (5.0F) + random.nextFloat() * (4.0F);
         hitParticles.add(new CompactTargetHud.TargetHitParticle(x, y, (float)Math.cos(angle) * distance, (float)Math.sin(angle) * distance, size, nowMillis, 0L));
      }
   }

   private CompactTargetHud() {
   }

   private static void drawHitParticles(long nowMillis, float alpha) {
      hitParticles.removeIf(expiredParticle -> nowMillis - expiredParticle.bornMs() >= (1000L) || expiredParticle.fadeFromMs() > 0L && nowMillis - expiredParticle.fadeFromMs() >= (200L));
      if (!hitParticles.isEmpty()) {
         if (HudRenderContext.getDrawContext() != null) {
            HudRenderContext.getDrawContext().createNewRootLayer();
         }

         for (CompactTargetHud.TargetHitParticle particle : hitParticles) {
            float ageProgress = (float)(nowMillis - particle.bornMs()) / (1000.0F);
            float remainingLife = 1.0F - ageProgress;
            float travelProgress = 1.0F - remainingLife * remainingLife * remainingLife;
            float particleAlpha = Math.min(1.0F, ageProgress / (0.15F)) * remainingLife * alpha;
            float x = particle.startX() + particle.offsetX() * travelProgress;
            float y = particle.startY() + particle.offsetY() * travelProgress;
            if (particle.fadeFromMs() > 0L) {
               particleAlpha *= Math.max(0.0F, 1.0F - (float)(nowMillis - particle.fadeFromMs()) / (200.0F));
            }

            float size = particle.size() * (1.0F - ageProgress * (0.4F));
            float halfSize = size / 2.0F;
            Render2D.drawGlowingRotatedImage(
               ("assets/mouseclient/textures/particles/glow.png"), x - halfSize, y - halfSize, size, 0.0F, 0.0F, x, y, GuiVisuals.multiplyAlpha(HudStyle.accentColor, particleAlpha)
            );
         }
      }
   }

   private static boolean hasEquipment(LivingEntity entity) {
      if (entity.getMainHandStack().isEmpty() && entity.getOffHandStack().isEmpty()) {
         for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!entity.getEquippedStack(slot).isEmpty()) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private static LivingEntity findDisplayTarget(MinecraftClient client) {
      if (client.player == null) {
         return null;
      } else if (HudRenderContext.isChatOpen()) {
         return client.player;
      } else {
         AttackAura aura = AttackAura.getInstance();
         if (aura.isEnabled() && aura.target() instanceof LivingEntity entity && entity.isAlive()) {
            return entity;
         } else {
            return client.targetedEntity instanceof LivingEntity crosshairEntity && crosshairEntity != client.player && crosshairEntity.isAlive() ? crosshairEntity : null;
         }
      }
   }

   public static float visibilityProgress() {
      return visibilityAnimation.floatValue();
   }

   private record TargetHitParticle(float startX, float startY, float offsetX, float offsetY, float size, long bornMs, long fadeFromMs) {
   }
}
