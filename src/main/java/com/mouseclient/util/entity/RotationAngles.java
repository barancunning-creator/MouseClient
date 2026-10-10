package com.mouseclient.util.entity;

import lombok.Generated;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationAngles implements MinecraftAccess {
   private final float yaw;
   private final float pitch;

   @Generated
   public float getYaw() {
      return this.yaw;
   }

   public RotationAngles(float yaw, float pitch) {
      this.yaw = yaw;
      this.pitch = pitch;
   }

   public Vec3d direction() {
      float pitchRadians = this.pitch * (0.017453292F);
      float negativeYawRadians = -this.yaw * (0.017453292F);
      float yawCosine = MathHelper.cos(negativeYawRadians);
      float yawSine = MathHelper.sin(negativeYawRadians);
      float pitchCosine = MathHelper.cos(pitchRadians);
      float pitchSine = MathHelper.sin(pitchRadians);
      return new Vec3d(yawSine * pitchCosine, -pitchSine, yawCosine * pitchCosine);
   }

   public static float cameraPitch() {
      return (minecraftClient.options.getPerspective() == Perspective.THIRD_PERSON_FRONT ? -1 : 1) * minecraftClient.gameRenderer.getCamera().getPitch();
   }

   @Generated
   public float getPitch() {
      return this.pitch;
   }

   public static float cameraYaw() {
      return MathHelper.wrapDegrees(
         minecraftClient.gameRenderer.getCamera().getYaw() + (minecraftClient.options.getPerspective() == Perspective.THIRD_PERSON_FRONT ? 180 : 0)
      );
   }
}
