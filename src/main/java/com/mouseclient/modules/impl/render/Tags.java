package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.mode.GlintMode;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.misc.MultiChoiceSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.movement.AirStuck;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.network.HudStyle;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.pipeline.PlayerOutlineRenderer;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.WorldProjection;
import com.mouseclient.util.time.SpecialPotion;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.PlayerSpecialPotionTracker;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.joml.Vector4f;

@ModuleInfo(name = "Tags", description = "Ники, предметы, броня и руки игроков поверх мира.", category = ModuleCategory.RENDER)
public final class Tags extends Module {
   private static Tags INSTANCE;
   private static int unusedDefaultBackgroundColor = new Color(15, 15, 15, 155).getRGB();
   private static int FRIEND_BACKGROUND_COLOR = new Color(0, 255, 0, 40).getRGB();
   private static int WARNING_COLOR = new Color(255, 70, 70, 255).getRGB();
   private static int AIRSTUCK_BACKGROUND_COLOR = new Color(190, 30, 30, 150).getRGB();
   private static int DELIMITER_COLOR = new Color(160, 160, 160, 255).getRGB();
   private static int DISTANCE_COLOR = new Color(112, 255, 112, 255).getRGB();
   public MultiChoiceSetting displayMode;
   public MultiChoiceSetting elements;
   public BooleanSetting showPlayerSpecialPotions;
   public BooleanSetting applyShader;
   public DecimalSetting outline;
   public DecimalSetting highlight;
   public DecimalSetting fill;
   public BooleanSetting customShaderColor;
   public ColorSetting shaderColor;
   public BooleanSetting box;
   public BooleanSetting customBoxColor;
   public ColorSetting boxColor;
   private final HashMap<Integer, Vec3d> previousPlayerPositions = new HashMap<>();
   private final HashMap<Integer, Integer> stationaryAirTicks = new HashMap<>();
   private final HashMap<Integer, Integer> movingAirTicks = new HashMap<>();
   private final HashMap<Integer, Integer> airMovementCooldownTicks = new HashMap<>();
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.world != null) {
         if (this.previousPlayerPositions.size() > 256) {
            this.previousPlayerPositions.clear();
            this.stationaryAirTicks.clear();
            this.movingAirTicks.clear();
            this.airMovementCooldownTicks.clear();
         }

         for (AbstractClientPlayerEntity player : minecraftClient.world.getPlayers()) {
            Vec3d position = player.getInterpolator().getLerpedPos();
            Vec3d previousPosition = this.previousPlayerPositions.put(player.getId(), position);
            boolean unsupportedAirborne = !player.hasVehicle()
               && !player.isGliding()
               && player.getGameMode() != GameMode.CREATIVE
               && player.getGameMode() != GameMode.SPECTATOR
               && !player.isTouchingWater()
               && !player.isInLava()
               && !player.isClimbing()
               && minecraftClient.world.isSpaceEmpty(player, player.getBoundingBox().offset(0.0, (-0.0625D), 0.0));
            boolean stationary = previousPosition != null && previousPosition.squaredDistanceTo(position) < (1.0E-4D);
            int movingTicks = unsupportedAirborne && previousPosition != null && !stationary ? this.movingAirTicks.getOrDefault(player.getId(), 0) + 1 : 0;
            this.movingAirTicks.put(player.getId(), movingTicks);
            int cooldownTicks = movingTicks >= 25 ? 40 : Math.max(0, this.airMovementCooldownTicks.getOrDefault(player.getId(), 0) - 1);
            this.airMovementCooldownTicks.put(player.getId(), cooldownTicks);
            this.stationaryAirTicks.put(player.getId(), unsupportedAirborne && stationary && cooldownTicks == 0 ? this.stationaryAirTicks.getOrDefault(player.getId(), 0) + 1 : 0);
         }
      }
   };
   private final EventListener<HudRenderEvent> hudRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.gameRenderer != null) {
         HudRenderContext.setDrawContext(event.getDrawContext());
         int screenWidth = minecraftClient.getWindow().getScaledWidth();
         int screenHeight = minecraftClient.getWindow().getScaledHeight();
         float guiScale = GuiScale.getWindowScale() / GuiScale.getBaseScale();
         if (this.labelCacheWorld != minecraftClient.world || this.itemLabels.size() > 512) {
            this.itemLabels.clear();
            this.labelCacheWorld = minecraftClient.world;
         }

         if (this.shouldShowSpecialPotions()) {
            PlayerSpecialPotionTracker.prune(minecraftClient.world);
         }

         for (Entity entity : minecraftClient.world.getEntities()) {
            boolean rendered = false;
            if (entity instanceof AbstractClientPlayerEntity player && this.box.getValue()) {
               this.renderPlayerBox(player, event.getTickProgress(), screenWidth, screenHeight, guiScale);
            }

            if (entity instanceof AbstractClientPlayerEntity tagPlayer) {
               rendered = this.renderPlayerTags(tagPlayer, event.getTickProgress(), screenWidth, screenHeight, guiScale);
            } else if (entity instanceof ItemEntity itemEntity && this.elements.isSelectedAt(1)) {
               rendered = this.renderDroppedItemTag(itemEntity, event.getTickProgress(), screenWidth, screenHeight, guiScale);
            } else if (entity instanceof ProjectileEntity projectile && this.elements.isSelectedAt(1)) {
               rendered = this.renderProjectileTag(projectile, event.getTickProgress(), screenWidth, screenHeight, guiScale);
            }

            if (rendered) {
               Render2D.nextLayer(event.getDrawContext());
            }
         }
      }
   };
   private final HashMap<Integer, Tags.ItemLabelCache> itemLabels = new HashMap<>();
   private ClientWorld labelCacheWorld;
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> {};
   private static final int[][] UNUSED_BOX_FACES = new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

   private ItemStack projectileStack(ProjectileEntity projectile) {
      ItemStack stack = projectile instanceof PersistentProjectileEntity persistentProjectile
         ? persistentProjectile.getItemStack()
         : (projectile instanceof FlyingItemEntity flyingItem ? flyingItem.getStack() : ItemStack.EMPTY);
      return stack.isEmpty() && projectile instanceof AbstractWindChargeEntity ? Items.WIND_CHARGE.getDefaultStack() : stack;
   }

   private int itemTagBackgroundColor(AbstractClientPlayerEntity player, boolean friend) {
      return this.isAirStuck(player) ? AIRSTUCK_BACKGROUND_COLOR : (friend ? FRIEND_BACKGROUND_COLOR : ArgbColor.withAlpha(HudStyle.backgroundColor, 133));
   }

   private void drawItemBadge(ItemStack stack, float x, float y) {
      int level = 0;
      String badge;
      if (!this.isBreachDensityMace(stack) && !this.isSharpnessEightSword(stack)) {
         if (stack.isIn(ItemTags.SWORDS) && (level = this.enchantmentLevel(stack, ("minecraft:knockback"))) > 0) {
            badge = ("От");
         } else {
            if (!this.hasProtectionFiveAndMending(stack)) {
               return;
            }

            badge = ("По");
            level = this.enchantmentLevel(stack, ("minecraft:mending"));
         }
      } else {
         badge = ("Кр");
      }

      String levelText = level > 0 ? String.valueOf(level) : "";
      float badgeWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, badge, (4.5F));
      float textX = x + (4.0F) - (badgeWidth + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, levelText, (4.5F))) / 2.0F;
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, badge, textX, y - (5.5F), (4.5F), HudStyle.textColor);
      if (!levelText.isEmpty()) {
         GuiTextRenderer.draw(GuiFont.SEMI_BOLD, levelText, textX + badgeWidth, y - (5.5F), (4.5F), WARNING_COLOR);
      }
   }

   private boolean isSharpnessEightSword(ItemStack stack) {
      if (!stack.isIn(ItemTags.SWORDS)) {
         return false;
      }

      HashSet enchantmentKeys = new HashSet();

      for (Entry enchantment : stack.getEnchantments().getEnchantmentEntries()) {
         enchantmentKeys.add(((RegistryEntry)enchantment.getKey()).getIdAsString() + enchantment.getIntValue());
      }

      return enchantmentKeys.contains(("minecraft:sharpness8"));
   }

   private boolean isHighlightedSword(ItemStack stack) {
      if (!stack.isIn(ItemTags.SWORDS)) {
         return false;
      }

      HashSet enchantmentKeys = new HashSet();

      for (Entry enchantment : stack.getEnchantments().getEnchantmentEntries()) {
         enchantmentKeys.add(((RegistryEntry)enchantment.getKey()).getIdAsString() + enchantment.getIntValue());
      }

      return enchantmentKeys.contains(("minecraft:knockback1")) || enchantmentKeys.contains(("minecraft:knockback2")) || enchantmentKeys.contains(("minecraft:sharpness8"));
   }

   private boolean hasProtectionFiveAndMending(ItemStack stack) {
      HashSet enchantmentKeys = new HashSet();

      for (Entry enchantment : stack.getEnchantments().getEnchantmentEntries()) {
         enchantmentKeys.add(((RegistryEntry)enchantment.getKey()).getIdAsString() + enchantment.getIntValue());
      }

      return enchantmentKeys.contains(("minecraft:protection5")) && (enchantmentKeys.contains(("minecraft:mending1")) || enchantmentKeys.contains(("minecraft:mending2")));
   }

   @Override
   protected void onDisable() {
      PlayerOutlineRenderer.getInstance().close();
      this.previousPlayerPositions.clear();
      this.stationaryAirTicks.clear();
      this.movingAirTicks.clear();
      this.airMovementCooldownTicks.clear();
      this.itemLabels.clear();
      this.labelCacheWorld = null;
   }

   public boolean isOutlineShaderEnabled() {
      return this.isEnabled() && this.applyShader.getValue();
   }

   private Tags() {
      this.displayMode = this.addSetting(new MultiChoiceSetting(("Отображать"), ("На ком рисовать элементы и шейдер.")).withOptions(("Игроков"), ("Друзей"), ("Себя")).withSelectedOptions(("Игроков"), ("Друзей"), ("Себя")));
      this.elements = this.addSetting(
         new MultiChoiceSetting(("Элементы"), ("Что рисовать поверх игроков и предметов.")).withOptions(("Ники"), ("Предметы"), ("Броню"), ("Руки")).withSelectedOptions(("Ники"), ("Предметы"), ("Руки"))
      );
      this.showPlayerSpecialPotions = this.addSetting(new BooleanSetting(("Показывать донатные зелья игроков"), ("Донатные зелья, которыми задело игроков и тебя, и сколько осталось."), true));
      this.addSetting(new SettingSeparator(("Применить шейдер")));
      this.applyShader = this.addSetting(new BooleanSetting(("Применить шейдер"), ("Обводка и тень вокруг игроков."), false));
      this.outline = this.addSetting(new DecimalSetting(("Оутлайн"), ("Сила обводки."), (0.8D), 0.0, 1.0, (0.05D)));
      this.highlight = this.addSetting(new DecimalSetting(("Подсветка"), ("Сила свечения вокруг игроков."), (0.55D), 0.0, (3.0D), (0.05D)));
      this.fill = this.addSetting(new DecimalSetting(("Заливка"), ("Заливка игроков цветом."), 0.0, 0.0, 1.0, (0.05D)));
      this.customShaderColor = this.addSetting(new BooleanSetting(("Кастомный цвет"), ("Свой цвет вместо акцента темы."), false));
      this.shaderColor = this.addSetting(new ColorSetting(("Цвет"), ("Цвет обводки и тени.")));
      this.outline.setVisibility(() -> this.applyShader.getValue());
      this.highlight.setVisibility(() -> this.applyShader.getValue());
      this.fill.setVisibility(() -> this.applyShader.getValue());
      this.customShaderColor.setVisibility(() -> this.applyShader.getValue());
      this.shaderColor.setVisibility(() -> this.applyShader.getValue() && this.customShaderColor.getValue());
      this.addSetting(new SettingSeparator(("Бокс")));
      this.box = this.addSetting(new BooleanSetting(("Бокс"), ("Показывать хитбоксы игроков в мире."), false));
      this.customBoxColor = this.addSetting(new BooleanSetting(("Кастомный цвет"), ("Свой цвет вместо акцента темы."), false));
      this.boxColor = this.addSetting(new ColorSetting(("Цвет"), ("Цвет обводки и заливки хитбокса.")));
      this.customBoxColor.setVisibility(() -> this.box.getValue());
      this.boxColor.setVisibility(() -> this.box.getValue() && this.customBoxColor.getValue());
   }

   public float itemTagScale(float distance) {
      return Math.max((0.65F), (1.1F) - distance * (0.004F));
   }

   public float itemTagVerticalOffset(float distance) {
      return this.isEnabled() && this.elements.isSelectedAt(1) ? GuiScale.toGuiUnits((11.0F) * this.itemTagScale(distance)) : 0.0F;
   }

   private void drawBoxEdge(float x, float y, float width, float height) {
      Render2D.fillRoundedRectangle(
         Math.min(x, x + width) - (width == 0.0F ? (0.875F) : (0.5F)),
         Math.min(y, y + height) - (height == 0.0F ? (0.875F) : (0.5F)),
         (width == 0.0F ? (0.75F) : Math.abs(width)) + 1.0F,
         (height == 0.0F ? (0.75F) : Math.abs(height)) + 1.0F,
         0.0F,
         (-16777216)
      );
      Render2D.fillRoundedRectangle(
         Math.min(x, x + width) - (width == 0.0F ? (0.375F) : 0.0F),
         Math.min(y, y + height) - (height == 0.0F ? (0.375F) : 0.0F),
         width == 0.0F ? (0.75F) : Math.abs(width),
         height == 0.0F ? (0.75F) : Math.abs(height),
         0.0F,
         (-16777216) | this.boxRenderColor() & (16777215)
      );
   }

   private boolean renderPlayerTags(AbstractClientPlayerEntity player, float tickDelta, int screenWidth, int screenHeight, float guiScale) {
      if (player == minecraftClient.player && minecraftClient.options.getPerspective().isFirstPerson()) {
         return false;
      }

      if (!this.shouldDisplayPlayer(player)) {
         return false;
      }

      boolean friend = FriendManager.contains(player.getGameProfile().name());
      float distance = (float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(player.getLerpedPos(tickDelta));
      float distanceScale = Math.max((0.3F), 1.0F - distance * (0.008F));
      Vec3d position = WorldProjection.projectNearViewport(player.getLerpedPos(tickDelta), screenWidth, screenHeight);
      if (position != null) {
         float labelY = (float)position.y * guiScale + 2.0F;
         if (this.elements.isSelectedAt(3)) {
            labelY = this.drawHandLabels(player, friend, (float)position.x * guiScale, labelY, distanceScale * Math.max((0.7F), 1.0F - distance * (0.005F)));
         }

         if (this.shouldShowSpecialPotions()) {
            this.drawSpecialPotions(player, (float)position.x * guiScale, labelY, distanceScale * Math.max((0.7F), 1.0F - distance * (0.005F)));
         }
      }

      if (this.elements.isSelectedAt(0)) {
         Vec3d namePosition = WorldProjection.projectNearViewport(
            player.getLerpedPos(tickDelta).add(0.0, player.getBoundingBox().maxY - player.getBoundingBox().minY + (0.3D), 0.0), screenWidth, screenHeight
         );
         if (namePosition != null) {
            this.drawNameTag(player, friend, (float)namePosition.x * guiScale, (float)namePosition.y * guiScale, distance, distanceScale);
         }
      }

      return true;
   }

   public int outlineShaderColor() {
      return this.customShaderColor.getValue() ? this.shaderColor.getArgb() : GuiVisuals.accentColor;
   }

   private float drawHandLabels(AbstractClientPlayerEntity player, boolean friend, float x, float y, float scale) {
      ItemStack stack = player.getMainHandStack();
      ItemStack offHand = player.getOffHandStack();
      if (stack.isEmpty() && offHand.isEmpty()) {
         return y;
      }

      float rowHeight = (12.0F);
      HudRenderContext.pushScale(x, y, scale);
      float cursorY = y + 1.0F;

      for (ItemStack handStack : new ItemStack[]{stack, offHand}) {
         if (!handStack.isEmpty()) {
            String countText = handStack.getCount() > 1 ? " " + handStack.getCount() + "x" : "";
            float labelWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, handStack.getName().getString(), (6.0F))
               + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, countText, (6.0F));
            Render2D.fillRoundedRectangle(x - labelWidth / 2.0F - 2.0F, cursorY, labelWidth + (4.0F), rowHeight, 0.0F, this.itemTagBackgroundColor(player, friend));
            GuiTextRenderer.drawStyledText(
               GuiFont.SEMI_BOLD,
               handStack.getName()
                  .copy()
                  .styled(
                     style -> style.getColor() != null && (style.getColor().getRgb() & (16777215)) != (16777215)
                        ? style
                        : style.withColor(TextColor.fromRgb(HudStyle.textColor & (16777215)))
                  ),
               x - labelWidth / 2.0F,
               cursorY + rowHeight / 2.0F - (3.65F),
               (6.0F),
               HudStyle.textColor
            );
            if (!countText.isEmpty()) {
               GuiTextRenderer.draw(
                  GuiFont.SEMI_BOLD,
                  countText,
                  x - labelWidth / 2.0F + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, handStack.getName().getString(), (6.0F)),
                  cursorY + rowHeight / 2.0F - (3.65F),
                  (6.0F),
                  HudStyle.textColor
               );
            }

            cursorY += rowHeight + 1.0F;
         }
      }

      HudRenderContext.popTransform();
      return y + (cursorY - y) * scale;
   }

   private void renderPlayerBox(AbstractClientPlayerEntity player, float tickDelta, int screenWidth, int screenHeight, float guiScale) {
      if ((player != minecraftClient.player || !minecraftClient.options.getPerspective().isFirstPerson()) && this.shouldDisplayPlayer(player)) {
         Vector4f bounds = WorldProjection.projectBounds(player.getBoundingBox().offset(player.getLerpedPos(tickDelta).subtract(player.getEntityPos())), screenWidth, screenHeight);
         if (bounds != null && !(bounds.z <= 0.0F) && !(bounds.w <= 0.0F)) {
            this.drawBoxEdge(bounds.x * guiScale, bounds.y * guiScale, bounds.z * guiScale, 0.0F);
            this.drawBoxEdge(bounds.x * guiScale, (bounds.y + bounds.w) * guiScale, bounds.z * guiScale, 0.0F);
            this.drawBoxEdge(bounds.x * guiScale, bounds.y * guiScale, 0.0F, bounds.w * guiScale);
            this.drawBoxEdge((bounds.x + bounds.z) * guiScale, bounds.y * guiScale, 0.0F, bounds.w * guiScale);
         }
      }
   }

   public boolean shouldDisplayPlayer(AbstractClientPlayerEntity player) {
      if (player == minecraftClient.player) {
         return this.displayMode.matchesOption(("Себя"));
      } else {
         return FriendManager.contains(player.getGameProfile().name()) ? this.displayMode.matchesOption(("Друзей")) : this.displayMode.matchesOption(("Игроков"));
      }
   }

   public static Tags getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Tags();
      }

      return INSTANCE;
   }

   private boolean renderDroppedItemTag(ItemEntity itemEntity, float tickDelta, int screenWidth, int screenHeight, float guiScale) {
      return this.drawItemTag(itemEntity.getStack(), itemEntity.getLerpedPos(tickDelta), itemEntity.getId(), screenWidth, screenHeight, guiScale);
   }

   private void drawSpecialPotions(AbstractClientPlayerEntity player, float x, float y, float scale) {
      java.util.Map<com.mouseclient.util.time.SpecialPotion,java.lang.Long> expirations = PlayerSpecialPotionTracker.expiresAtMillis(player);
      if (!expirations.isEmpty()) {
         long nowMillis = System.currentTimeMillis();
         float cursorY = y + 2.0F;
         HudRenderContext.pushScale(x, y, scale);

         for (java.util.Map.Entry potionEntry : expirations.entrySet()) {
            String remainingText = " " + Math.max(1L, ((Long)potionEntry.getValue() - nowMillis + (999L)) / (1000L)) + "с";
            float nameWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ((SpecialPotion)potionEntry.getKey()).plainName(), (5.0F));
            float labelWidth = nameWidth + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, remainingText, (5.0F));
            GuiTextRenderer.drawStyledText(
               GuiFont.SEMI_BOLD, ((SpecialPotion)potionEntry.getKey()).fullName(), x - labelWidth / 2.0F, cursorY, (5.0F), HudStyle.textColor
            );
            GuiTextRenderer.draw(GuiFont.SEMI_BOLD, remainingText, x - labelWidth / 2.0F + nameWidth, cursorY, (5.0F), HudPanelRenderer.secondaryTextColor(1.0F));
            cursorY += (6.5F);
         }

         HudRenderContext.popTransform();
      }
   }

   public int boxRenderColor() {
      return this.customBoxColor.getValue() ? this.boxColor.getArgb() : GuiVisuals.accentColor;
   }

   private boolean isBreachDensityMace(ItemStack stack) {
      if (!stack.isOf(Items.MACE)) {
         return false;
      }

      HashSet enchantmentKeys = new HashSet();

      for (Entry enchantment : stack.getEnchantments().getEnchantmentEntries()) {
         enchantmentKeys.add(((RegistryEntry)enchantment.getKey()).getIdAsString() + enchantment.getIntValue());
      }

      return enchantmentKeys.contains(("minecraft:breach3")) && enchantmentKeys.contains(("minecraft:density5"));
   }

   private int enchantmentLevel(ItemStack stack, String enchantmentId) {
      for (Entry enchantment : stack.getEnchantments().getEnchantmentEntries()) {
         if (((RegistryEntry)enchantment.getKey()).getIdAsString().equals(enchantmentId)) {
            return enchantment.getIntValue();
         }
      }

      return 0;
   }

   private boolean shouldShowEquipment() {
      return this.elements.isSelectedAt(2) || ServerHelper.getInstance().isEnabled() && ServerHelper.getInstance().server.isSelected(("SpookyTime"));
   }

   private void drawNameTag(AbstractClientPlayerEntity player, boolean friend, float x, float y, float distance, float scale) {
      String name = player.getGameProfile().name();
      String healthText = CombatTargeting.healthText(player);
      Team team = minecraftClient.world.getScoreboard().getScoreHolderTeam(player.getNameForScoreboard());
      Text text = team == null ? null : team.getPrefix();
      String prefix = text == null ? "" : text.getString().trim();
      String distanceText = String.valueOf(Math.round(distance));
      float nameWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, name, (6.0F));
      float spacing = (3.0F);
      float prefixWidth = prefix.isEmpty() ? 0.0F : GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, prefix, (6.0F)) + spacing;
      boolean airStuck = this.isAirStuck(player);
      float airStuckWidth = airStuck ? GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("AIRSTUCK"), (6.0F)) + spacing : 0.0F;
      float labelWidth = 2.0F
         + prefixWidth
         + airStuckWidth
         + nameWidth
         + spacing
         + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("["), (6.0F))
         + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, healthText, (6.0F))
         + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("/"), (6.0F))
         + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, distanceText, (6.0F))
         + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("]"), (6.0F))
         + 2.0F;
      float labelHeight = (12.0F);
      float labelX = x - labelWidth / 2.0F;
      float labelY = y - labelHeight - (3.0F) + (6.0F) - Math.min((6.0F), distance * (0.08F));
      float textY = labelY + labelHeight / 2.0F - (3.65F);
      HudRenderContext.pushScale(x, y, scale * Math.max((0.7F), 1.0F - distance * (0.005F)));
      Render2D.fillRoundedRectangle(
         labelX, labelY, labelWidth, labelHeight, 0.0F, airStuck ? AIRSTUCK_BACKGROUND_COLOR : (friend ? FRIEND_BACKGROUND_COLOR : ArgbColor.withAlpha(HudStyle.backgroundColor, 133))
      );
      float cursorX = labelX + 2.0F;
      if (!prefix.isEmpty()) {
         GuiTextRenderer.drawStyledText(GuiFont.SEMI_BOLD, text, cursorX, textY, (6.0F), HudStyle.textColor);
         cursorX += GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, prefix, (6.0F)) + spacing;
      }

      if (airStuck) {
         GuiTextRenderer.draw(GuiFont.SEMI_BOLD, ("AIRSTUCK"), cursorX, textY, (6.0F), WARNING_COLOR);
         cursorX += GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("AIRSTUCK"), (6.0F)) + spacing;
      }

      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, name, cursorX, textY, (6.0F), HudStyle.textColor);
      cursorX += nameWidth + spacing;
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, ("["), cursorX, textY, (6.0F), DELIMITER_COLOR);
      cursorX += GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("["), (6.0F));
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, healthText, cursorX, textY, (6.0F), WARNING_COLOR);
      cursorX += GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, healthText, (6.0F));
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, ("/"), cursorX, textY, (6.0F), DELIMITER_COLOR);
      cursorX += GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("/"), (6.0F));
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, distanceText, cursorX, textY, (6.0F), DISTANCE_COLOR);
      cursorX += GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, distanceText, (6.0F));
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, ("]"), cursorX, textY, (6.0F), DELIMITER_COLOR);
      if (!this.shouldShowEquipment()) {
         ArrayList<ItemStack> highlightedItems = new ArrayList();
         if (this.isHighlightedSword(player.getMainHandStack()) || this.isBreachDensityMace(player.getMainHandStack())) {
            highlightedItems.add(player.getMainHandStack());
         }

         for (ItemStack stack : new ItemStack[]{
            player.getEquippedStack(EquipmentSlot.HEAD),
            player.getEquippedStack(EquipmentSlot.CHEST),
            player.getEquippedStack(EquipmentSlot.LEGS),
            player.getEquippedStack(EquipmentSlot.FEET)
         }) {
            if (this.hasProtectionFiveAndMending(stack)) {
               highlightedItems.add(stack);
            }
         }

         cursorX = x - (highlightedItems.size() * (8.0F) + (highlightedItems.size() - 1) * 2.0F) / 2.0F;

         for (ItemStack highlightedItem : highlightedItems) {
            this.drawItemBadge(highlightedItem, cursorX, labelY - (10.0F) - (1.5F) + 1.0F);
            GuiItemRenderer.render(highlightedItem, cursorX, labelY - (10.0F) - (1.5F) + 1.0F, (8.0F), ItemRenderOptions.noDecorations(1.0F).glint(GlintMode.OFF));
            cursorX += (10.0F);
         }
      }

      if (this.shouldShowEquipment()) {
         ArrayList<ItemStack> equippedItems = new ArrayList();

         for (ItemStack equippedStack : new ItemStack[]{
            player.getMainHandStack(),
            player.getEquippedStack(EquipmentSlot.HEAD),
            player.getEquippedStack(EquipmentSlot.CHEST),
            player.getEquippedStack(EquipmentSlot.LEGS),
            player.getEquippedStack(EquipmentSlot.FEET),
            player.getOffHandStack()
         }) {
            if (!equippedStack.isEmpty()) {
               equippedItems.add(equippedStack);
            }
         }

         if (!equippedItems.isEmpty()) {
            cursorX = labelX + 2.0F;

            for (ItemStack equipmentStack : equippedItems) {
               this.drawItemBadge(equipmentStack, cursorX, labelY - (10.0F) - (1.5F) + 1.0F);
               GuiItemRenderer.render(equipmentStack, cursorX, labelY - (10.0F) - (1.5F) + 1.0F, (8.0F), ItemRenderOptions.decorated(1.0F).glint(GlintMode.OFF));
               cursorX += (10.0F);
            }
         }
      }

      HudRenderContext.popTransform();
   }

   public boolean isAirStuck(AbstractClientPlayerEntity player) {
      if (player == minecraftClient.player) {
         return AirStuck.getInstance().isFrozen();
      } else {
         return player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) ? false : this.stationaryAirTicks.getOrDefault(player.getId(), 0) >= 20;
      }
   }

   private boolean shouldShowSpecialPotions() {
      return this.showPlayerSpecialPotions.getValue() && SpecialPotion.isTrackingEnabled();
   }

   private boolean drawItemTag(ItemStack stack, Vec3d position, int entityId, int screenWidth, int screenHeight, float guiScale) {
      if (stack.isEmpty()) {
         return false;
      }

      Vec3d screenPosition = WorldProjection.projectNearViewport(position.add(0.0, (0.25D), 0.0), screenWidth, screenHeight);
      if (screenPosition == null) {
         return false;
      }

      Tags.ItemLabelCache cachedLabel = this.itemLabels.get(entityId);
      if (cachedLabel == null || cachedLabel.stack() != stack && !ItemStack.areItemsAndComponentsEqual(cachedLabel.stack(), stack)) {
         cachedLabel = new Tags.ItemLabelCache(
            stack,
            stack.getName().getString(),
            stack.getName()
               .copy()
               .styled(
                  style -> style.getColor() != null && (style.getColor().getRgb() & (16777215)) != (16777215)
                     ? style
                     : style.withColor(TextColor.fromRgb(HudStyle.textColor & (16777215)))
               )
         );
         this.itemLabels.put(entityId, cachedLabel);
      }

      String label = cachedLabel.label();
      float distance = (float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(position);
      float scale = this.itemTagScale(distance);
      float labelHeight = (9.0F);
      float labelWidth = 2.0F + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, label, (4.0F)) + 2.0F;
      float centerX = (float)screenPosition.x * guiScale;
      float labelY = (float)screenPosition.y * guiScale - labelHeight - 2.0F;
      float labelX = centerX - labelWidth / 2.0F;
      float unusedCenterY = labelY + labelHeight / 2.0F;
      HudRenderContext.pushScale(centerX, (float)screenPosition.y * guiScale, scale);
      Render2D.fillRoundedRectangle(labelX, labelY, labelWidth, labelHeight, 0.0F, ArgbColor.withAlpha(HudStyle.backgroundColor, 133));
      GuiTextRenderer.drawStyledText(
         GuiFont.SEMI_BOLD, cachedLabel.styledName(), labelX + 2.0F, labelY + labelHeight / 2.0F - (2.45F), (4.0F), HudStyle.textColor
      );
      HudRenderContext.popTransform();
      return true;
   }

   private boolean renderProjectileTag(ProjectileEntity projectile, float tickDelta, int screenWidth, int screenHeight, float guiScale) {
      return this.drawItemTag(this.projectileStack(projectile), projectile.getLerpedPos(tickDelta), projectile.getId(), screenWidth, screenHeight, guiScale);
   }

   private record ItemLabelCache(ItemStack stack, String label, Text styledName) {
   }
}
