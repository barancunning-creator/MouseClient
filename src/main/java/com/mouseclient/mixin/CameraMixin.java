package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.CameraFeature;
import com.mouseclient.modules.impl.render.FreeCamera;
import com.mouseclient.modules.impl.render.SmoothFeature;
import com.mouseclient.network.RotationController;
import com.mouseclient.util.entity.MinecraftAccess;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin implements MinecraftAccess {
   @Shadow
   protected abstract void setPos(Vec3d pos);

   @Shadow
   protected abstract void setRotation(float yaw, float pitch);

   @Inject(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("TAIL"))
   private void moveCameraToFreeCamera(World world, Entity entity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo callback) {
      if (FreeCamera.getInstance().isCameraActive()) {
         this.setRotation(FreeCamera.getInstance().cameraYaw(), FreeCamera.getInstance().cameraPitch());
         this.setPos(FreeCamera.getInstance().cameraPosition(tickDelta));
      } else if (CameraFeature.getInstance().shouldOverrideCamera()) {
         this.setRotation(CameraFeature.getInstance().cameraYaw(), CameraFeature.getInstance().cameraPitch());
         this.setPos(CameraFeature.getInstance().cameraPosition(tickDelta));
      }
   }

   @Inject(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At("TAIL"))
   private void glideCameraAfterFlag(World world, Entity entity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo callback) {
      if (SmoothFeature.getInstance().hasCorrectionOffset()) {
         this.setPos(((Camera)(Object)this).getCameraPos().add(SmoothFeature.getInstance().correctionOffset()));
      }
   }

   @Redirect(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
   private float steerCameraYaw(Entity entity, float tickProgress) {
      return entity == minecraftClient.player && RotationController.getInstance().isActive() ? RotationController.getInstance().cameraYaw() : entity.getYaw(tickProgress);
   }

   @Redirect(method = "update(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZZF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
   private float steerCameraPitch(Entity entity, float tickProgress) {
      return entity == minecraftClient.player && RotationController.getInstance().isActive() ? RotationController.getInstance().cameraPitch() : entity.getPitch(tickProgress);
   }

   @Inject(method = "isThirdPerson()Z", at = @At("HEAD"), cancellable = true)
   private void forceDetachedWhileFreeCamera(CallbackInfoReturnable<Boolean> callback) {
      if (FreeCamera.getInstance().shouldRenderDetached() || CameraFeature.getInstance().shouldRenderDetached()) {
         callback.setReturnValue(true);
      }
   }
}
