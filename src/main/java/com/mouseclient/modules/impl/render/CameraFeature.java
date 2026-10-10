package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.RotationController;
import com.mouseclient.render.pipeline.SliderSetting;
import com.mouseclient.util.time.ScalarAnimation;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(name = "CameraFeature", description = "Фрилук, зум и плавное переключение камеры на F5.", category = ModuleCategory.RENDER)
public final class CameraFeature extends Module {
   private static CameraFeature INSTANCE;
   public BindSetting freeLookBind;
   public BindSetting zoomBind;
   public SliderSetting zoomStrength;
   public BooleanSetting smoothPerspective;
   private boolean freeLookHeld;
   private boolean zoomHeld;
   private float freeLookYaw;
   private float freeLookPitch;
   private final ScalarAnimation freeLookAnimation;
   private final ScalarAnimation perspectiveAnimation;
   private final EventListener<PlayerTickEvent> playerTickListener;

   public void rotateFreeLook(double yawDelta, double pitchDelta) {
      this.freeLookYaw = this.freeLookYaw + (float)yawDelta * (0.15F);
      this.freeLookPitch = Math.max((-90.0F), Math.min((90.0F), this.freeLookPitch + (float)pitchDelta * (0.15F)));
   }

   @Override
   protected void onEnable() {
      this.freeLookHeld = false;
      this.zoomHeld = false;
      this.freeLookAnimation.reset(0.0);
      this.perspectiveAnimation
         .reset(minecraftClient.options != null && minecraftClient.options.getPerspective() == Perspective.THIRD_PERSON_BACK ? 1.0 : 0.0);
   }

   public float freeLookProgress() {
      return this.isEnabled() ? (float)this.freeLookAnimation.value() : 0.0F;
   }

   public boolean shouldOverrideCamera() {
      return this.isEnabled()
         && minecraftClient.player != null
         && (
            this.isFreeLookVisible()
               || this.smoothPerspective.getValue()
                  && minecraftClient.options != null
                  && minecraftClient.options.getPerspective() == Perspective.THIRD_PERSON_BACK
                  && this.perspectiveAnimation.value() > (0.001D)
                  && this.perspectiveAnimation.value() < (0.999D)
         );
   }

   public static CameraFeature getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new CameraFeature();
      }

      return INSTANCE;
   }

   public float cameraPitch() {
      return this.isFreeLookVisible()
         ? this.freeLookPitch
            + ((RotationController.getInstance().isActive() ? RotationController.getInstance().cameraPitch() : minecraftClient.player.getPitch()) - this.freeLookPitch)
               * (1.0F - (float)this.freeLookAnimation.value())
         : (RotationController.getInstance().isActive() ? RotationController.getInstance().cameraPitch() : minecraftClient.player.getPitch());
   }

   private float cameraTransitionProgress() {
      return this.isFreeLookVisible() ? (float)this.freeLookAnimation.value() : (float)this.perspectiveAnimation.value();
   }

   public float zoomFovMultiplier() {
      return this.isEnabled() && this.zoomHeld ? 1.0F / this.zoomStrength.floatValue() : (-1.0F);
   }

   private double clipCameraDistance(Vec3d origin, Vec3d direction, double desiredDistance) {
      if (minecraftClient.world == null) {
         return desiredDistance;
      }

      double clippedDistance = desiredDistance;

      for (int var7 = 0; var7 < 8; var7++) {
         Vec3d rayOrigin = origin.add(
            (var7 & 1) == 0 ? (-0.1D) : (0.1D),
            (var7 >> 1 & 1) == 0 ? (-0.1D) : (0.1D),
            (var7 >> 2 & 1) == 0 ? (-0.1D) : (0.1D)
         );
         BlockHitResult hitResult = minecraftClient.world
            .raycast(new RaycastContext(rayOrigin, rayOrigin.add(direction.multiply(desiredDistance)), ShapeType.VISUAL, FluidHandling.NONE, minecraftClient.player));
         if (hitResult.getType() != Type.MISS) {
            clippedDistance = Math.min(clippedDistance, hitResult.getPos().distanceTo(origin));
         }
      }

      return clippedDistance;
   }

   private CameraFeature() {
      this.freeLookAnimation = new ScalarAnimation((280L));
      this.perspectiveAnimation = new ScalarAnimation((240L));
      this.playerTickListener = event -> {
         if (minecraftClient.player != null && minecraftClient.options != null) {
            boolean freeLookPressed = this.freeLookBind.isBound() && this.freeLookBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            if (freeLookPressed && !this.freeLookHeld) {
               this.freeLookYaw = minecraftClient.player.getYaw();
               this.freeLookPitch = minecraftClient.player.getPitch();
            }

            this.freeLookHeld = freeLookPressed;
            this.zoomHeld = this.zoomBind.isBound() && this.zoomBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            this.freeLookAnimation.setTarget(this.freeLookHeld ? 1.0 : 0.0);
            this.perspectiveAnimation
               .setTarget(this.smoothPerspective.getValue() && minecraftClient.options.getPerspective() == Perspective.THIRD_PERSON_BACK ? 1.0 : 0.0);
         }
      };
      this.freeLookBind = this.addSetting(new BindSetting(("Обзор"), ("Свободный обзор не позволяющий сменить траекторию вашего игрока.")));
      this.zoomBind = this.addSetting(new BindSetting(("Зум"), ("Зум который был вырезан в 1.16.")));
      this.zoomStrength = this.addSetting(new SliderSetting(("Сила зума"), ("Во сколько раз приближать.")).withRange(2.0F, (10.0F)).withStep((0.5F)).setValue((4.0F)));
      this.smoothPerspective = this.addSetting(new BooleanSetting(("Плавная смена вида"), ("Анимация переключения камеры по F5."), true));
   }

   @Override
   protected void onDisable() {
      this.freeLookHeld = false;
      this.zoomHeld = false;
   }

   public float cameraYaw() {
      return this.isFreeLookVisible()
         ? MathHelper.lerpAngleDegrees(
            1.0F - (float)this.freeLookAnimation.value(),
            this.freeLookYaw,
            RotationController.getInstance().isActive() ? RotationController.getInstance().cameraYaw() : minecraftClient.player.getYaw()
         )
         : (RotationController.getInstance().isActive() ? RotationController.getInstance().cameraYaw() : minecraftClient.player.getYaw());
   }

   private boolean isFreeLookVisible() {
      return this.freeLookHeld || this.freeLookAnimation.value() > (0.001D);
   }

   public boolean handleLookInput(double yawDelta, double pitchDelta) {
      if (this.isEnabled()
         && minecraftClient.player != null
         && this.freeLookBind.isBound()
         && this.freeLookBind.getBinding().isPressed(minecraftClient.getWindow().getHandle())) {
         if (!this.freeLookHeld) {
            this.freeLookYaw = minecraftClient.player.getYaw();
            this.freeLookPitch = minecraftClient.player.getPitch();
            this.freeLookHeld = true;
            this.freeLookAnimation.setTarget(1.0);
         }

         this.rotateFreeLook(yawDelta, pitchDelta);
         return true;
      } else {
         return false;
      }
   }

   public Vec3d cameraPosition(float tickDelta) {
      Vec3d backwardDirection = Vec3d.fromPolar(this.cameraPitch(), this.cameraYaw()).multiply((-1.0D));
      return minecraftClient.player
         .getCameraPosVec(tickDelta)
         .add(
            backwardDirection.multiply(
               this.clipCameraDistance(
                  minecraftClient.player.getCameraPosVec(tickDelta),
                  backwardDirection,
                  (4.0D)
                     * (
                        this.isFreeLookVisible()
                           ? Math.max(
                              this.freeLookAnimation.value(),
                              minecraftClient.options != null && minecraftClient.options.getPerspective() != Perspective.FIRST_PERSON ? 1.0 : 0.0
                           )
                           : this.perspectiveAnimation.value()
                     )
               )
            )
         );
   }

   public boolean shouldRenderDetached() {
      return this.shouldOverrideCamera() && this.cameraTransitionProgress() > (0.15F);
   }

   public boolean isFreeLookHeld() {
      return this.isEnabled() && this.freeLookHeld;
   }
}
