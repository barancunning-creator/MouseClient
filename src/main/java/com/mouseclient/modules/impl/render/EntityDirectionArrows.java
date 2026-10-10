package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.HudStyle;
import com.mouseclient.util.entity.RotationAngles;
import java.awt.Color;
import java.util.HashMap;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

@ModuleInfo(name = "Arrows", description = "Кольцо стрелок вокруг прицела показывает, куда повернуться до игрока.", category = ModuleCategory.RENDER)
public final class EntityDirectionArrows extends Module {
   private static EntityDirectionArrows INSTANCE;
   public MultiSelectSetting renderTargets;
   public ColorSetting friendColor;
   public ModeSetting unusedMode;
   public BooleanSetting renderInWorld;
   public DecimalSetting size;
   public DecimalSetting radius;
   public BooleanSetting changeWhileSprinting;
   public DecimalSetting sprintRadius;
   public BooleanSetting changeInMenu;
   public DecimalSetting menuRadius;
   private float animatedRadius;
   private final HashMap<Integer, float[]> entityArrowStates;
   private final EventListener<HudRenderEvent> hudRenderListener;

   private static final double DEFAULT_ARROW_SIZE = 15.0;
   private static final double MIN_ARROW_SIZE = 6.0;

   private static final double DEFAULT_RADIUS = 50.0;

   private static final String SPRINT_RADIUS_DESCRIPTION = "Разводить кольцо шире, пока бежишь.";

   private static final double DEFAULT_SPRINT_RADIUS = 100.0;
   
   private static final double MAX_SPRINT_RADIUS = 150.0;

   private static final String MENU_RADIUS_LABEL = "Радиус в меню";

   private EntityDirectionArrows() {
      this.animatedRadius = (50.0F);
      this.entityArrowStates = new HashMap<>();
      this.hudRenderListener = event -> {
         if (minecraftClient.player != null && minecraftClient.world != null) {
            float interpolation = MathHelper.clamp((52.0F) / Math.max(1, minecraftClient.getCurrentFps()), 0.0F, 1.0F);
            float centerX = event.getWidth() / 2.0F * GuiScale.getWindowScale() / GuiScale.getBaseScale();
            float centerY = event.getHeight() / 2.0F * GuiScale.getWindowScale() / GuiScale.getBaseScale();
            this.animatedRadius = this.animatedRadius
               + (
                     (
                           this.changeInMenu.getValue() && minecraftClient.currentScreen instanceof HandledScreen
                              ? this.menuRadius.getValue()
                              : (
                                 this.changeWhileSprinting.getValue() && minecraftClient.player.isSprinting()
                                    ? this.sprintRadius.getValue()
                                    : this.radius.getValue()
                              )
                        )
                        - this.animatedRadius
                  )
                  * interpolation;
            float cameraYawRadians = (float)Math.toRadians(RotationAngles.cameraYaw());
            HudRenderContext.setDrawContext(event.getDrawContext());
            if (this.renderInWorld.getValue()) {
               HudRenderContext.pushScale(
                  centerX, centerY, 1.0F, MathHelper.cos(Math.toRadians(MathHelper.clamp(-RotationAngles.cameraPitch() + (90.0F), (-60.0F), (60.0F))))
               );
            }

            for (Entity entity : minecraftClient.world.getEntities()) {
               if (entity instanceof LivingEntity target && this.shouldShowEntity(target)) {
                  double relativeX = target.getLerpedPos(event.getTickProgress()).x - minecraftClient.gameRenderer.getCamera().getCameraPos().x;
                  double relativeZ = target.getLerpedPos(event.getTickProgress()).z - minecraftClient.gameRenderer.getCamera().getCameraPos().z;
                  float angleDegrees = (float)Math.toDegrees(
                     Math.atan2(-(relativeZ * MathHelper.cos(cameraYawRadians) - relativeX * MathHelper.sin(cameraYawRadians)), -(relativeX * MathHelper.cos(cameraYawRadians) + relativeZ * MathHelper.sin(cameraYawRadians)))
                  );
                  float friendWeight = target instanceof PlayerEntity player && FriendManager.contains(player.getGameProfile().name()) ? 1.0F : 0.0F;
                  float[] state = this.entityArrowStates.computeIfAbsent(target.getId(), entityId -> new float[]{angleDegrees, 0.0F, friendWeight});
                  state[0] = MathHelper.lerpAngleDegrees(interpolation, state[0], angleDegrees);
                  state[1] += (1.0F - state[1]) * interpolation;
                  state[2] += (friendWeight - state[2]) * interpolation;
                  this.drawArrow(state, centerX, centerY);
               }
            }

            this.entityArrowStates.entrySet().removeIf(fadeEntry -> {
               if (minecraftClient.world.getEntityById(fadeEntry.getKey()) instanceof LivingEntity remainingEntity && this.shouldShowEntity(remainingEntity)) {
                  return false;
               } else {
                  fadeEntry.getValue()[1] -= ((float[])fadeEntry.getValue())[1] * interpolation;
                  this.drawArrow(fadeEntry.getValue(), centerX, centerY);
                  return fadeEntry.getValue()[1] < (0.01F);
               }
            });
            if (this.renderInWorld.getValue()) {
               HudRenderContext.popTransform();
            }
         }
      };
      this.renderTargets = this.addSetting(new MultiSelectSetting(("Кого показывать"), ("На кого вести стрелки."), new String[]{("Игроки"), ("Друзья"), ("Мобы")}, ("Игроки"), ("Друзья")));
      this.friendColor = this.addSetting(new ColorSetting(("Цвет друзей"), ("Каким цветом вести стрелку на друга."), new Color(60, 232, 107)));
      this.renderInWorld = this.addSetting(new BooleanSetting(("Рендерить в 3д"), ("Кладёт кольцо стрелок на плоскость по наклону взгляда."), false));
      this.size = this.addSetting(new DecimalSetting(("Размер"), ("Размер стрелки."), DEFAULT_ARROW_SIZE, MIN_ARROW_SIZE, (40.0D), (0.5D)));
      this.radius = this.addSetting(new DecimalSetting(("Радиус"), ("Радиус кольца стрелок."), DEFAULT_RADIUS, (25.0D), (150.0D), (0.1D)));
      this.changeWhileSprinting = this.addSetting(new BooleanSetting(("Менять при спринте"), SPRINT_RADIUS_DESCRIPTION, true));
      this.sprintRadius = this.addSetting(new DecimalSetting(("Радиус при спринте"), ("Радиус кольца во время спринта."), DEFAULT_SPRINT_RADIUS, (25.0D), MAX_SPRINT_RADIUS, (0.1D)));
      this.sprintRadius.setVisibility(() -> this.changeWhileSprinting.getValue());
      this.changeInMenu = this.addSetting(new BooleanSetting(("Менять в меню"), ("Разводить кольцо шире с открытым инвентарём или контейнером."), true));
      this.menuRadius = this.addSetting(new DecimalSetting(MENU_RADIUS_LABEL, ("Радиус кольца с открытым инвентарём или контейнером."), (130.0D), (25.0D), (150.0D), (0.1D)));
      this.menuRadius.setVisibility(() -> this.changeInMenu.getValue());
   }

   @Override
   protected void onDisable() {
      this.entityArrowStates.clear();
   }

   public static EntityDirectionArrows getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new EntityDirectionArrows();
      }

      return INSTANCE;
   }

   private void drawArrow(float[] state, float centerX, float centerY) {
      float x = centerX + this.animatedRadius * MathHelper.cos(Math.toRadians(state[0])) - this.size.getValue() / 2.0F;
      float y = centerY + this.animatedRadius * MathHelper.sin(Math.toRadians(state[0])) - this.size.getValue() / 2.0F;
      Render2D.drawGlowingRotatedImage(
         ("assets/mouseclient/textures/images/arrow_nav_hollow.png"),
         x,
         y,
         this.size.getValue(),
         0.0F,
         state[0] + (90.0F),
         x + this.size.getValue() / 2.0F,
         y + this.size.getValue() / 2.0F,
         ArgbColor.multiplyAlpha(ArgbColor.lerp(HudStyle.accentColor, this.friendColor.getArgb(), state[2]), state[1])
      );
   }

   private boolean shouldShowEntity(LivingEntity entity) {
      if (entity == minecraftClient.player || entity instanceof ArmorStandEntity || !entity.isAlive()) {
         return false;
      } else if (entity instanceof PlayerEntity player) {
         return FriendManager.contains(player.getGameProfile().name()) ? this.renderTargets.matchesOption(("Друзья")) : this.renderTargets.matchesOption(("Игроки"));
      } else {
         return this.renderTargets.matchesOption(("Мобы"));
      }
   }
}
