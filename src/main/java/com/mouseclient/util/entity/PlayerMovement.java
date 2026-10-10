package com.mouseclient.util.entity;

import lombok.Generated;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Vector3f;

public final class PlayerMovement implements MinecraftAccess {

   private static final float QUARTER_TURN_DEGREES = 90.0F;

   public static double unusedNumericConstant() {
      return (1488.0D);
   }

   public static PlayerInput addKeysForAngle(PlayerInput input, double angle, float deadzone) {
      boolean forward = input.forward();
      boolean backward = input.backward();
      boolean left = input.left();
      boolean right = input.right();
      if (angle >= (-90.0F) + deadzone && angle <= (90.0F) - deadzone) {
         forward = true;
      } else if (angle < (-90.0F) - deadzone || angle > (90.0F) + deadzone) {
         backward = true;
      }

      if (angle >= 0.0F + deadzone && angle <= (180.0F) - deadzone) {
         right = true;
      } else if (angle >= (-180.0F) + deadzone && angle <= 0.0F - deadzone) {
         left = true;
      }

      return new PlayerInput(forward, backward, left, right, input.jump(), input.sneak(), input.sprint());
   }

   @Generated
   private PlayerMovement() {
      throw new UnsupportedOperationException(("This is a utility class and cannot be instantiated"));
   }

   public static float smoothBodyYaw(float headYaw, float bodyYaw, double previousX, double previousZ, double x, double z, float unusedTickDelta) {
      double deltaX = x - previousX;
      double deltaZ = z - previousZ;
      float movementSquared = (float)(deltaX * deltaX + deltaZ * deltaZ);
      float nextBodyYaw = bodyYaw;
      float unusedSwingProgress = minecraftClient.player.handSwingProgress;
      if (movementSquared > (0.0025000002F)) {
         float movementYaw = (float)MathHelper.atan2(deltaZ, deltaX) * (57.295776F) - QUARTER_TURN_DEGREES;
         float yawDifference = MathHelper.abs(MathHelper.wrapDegrees(headYaw) - movementYaw);
         if ((95.0F) < yawDifference && yawDifference < (265.0F)) {
            nextBodyYaw = movementYaw - (180.0F);
         } else {
            nextBodyYaw = movementYaw;
         }
      }

      if (minecraftClient.player != null && minecraftClient.player.handSwingProgress - (0.3F) > 0.0F) {
         nextBodyYaw = headYaw;
      }

      float bodyYawDelta = MathHelper.wrapDegrees(nextBodyYaw - bodyYaw);
      nextBodyYaw = bodyYaw + bodyYawDelta * (0.3F);
      float headBodyDifference = MathHelper.wrapDegrees(headYaw - nextBodyYaw);
      float maxHeadBodyDifference = (52.0F);
      if (Math.abs(headBodyDifference) > maxHeadBodyDifference) {
         nextBodyYaw += headBodyDifference - MathHelper.sign(headBodyDifference) * maxHeadBodyDifference;
      }

      return nextBodyYaw;
   }

   public static String legacyBrandString() {
      return ("lunarclient:v2.21.5-2540    ");
   }

   public static double movementYawDifference(Vec3d movement, float yaw) {
      float movementRadians = (float)Math.atan2(-movement.x, movement.z);
      double yawRadians = Math.toRadians(MathHelper.wrapDegrees(yaw));
      return Math.toDegrees(MathHelper.wrapDegrees(movementRadians - yawRadians));
   }

   public static final boolean isDirectionKeyPressed(int directionIndex) {
      boolean forward = minecraftClient.options.forwardKey.isPressed();
      boolean left = minecraftClient.options.leftKey.isPressed();
      boolean backward = minecraftClient.options.backKey.isPressed();
      boolean right = minecraftClient.options.rightKey.isPressed();
      return directionIndex == 0 ? forward : (directionIndex == 1 ? left : (directionIndex == 2 ? backward : directionIndex == 3 && right));
   }

   public static final boolean isForwardPressed() {
      return isDirectionKeyPressed(0);
   }

   public static PlayerInput addKeysForAngle(PlayerInput input, double angle) {
      return addKeysForAngle(input, angle, (20.0F));
   }

   public static final boolean isRightPressed() {
      return isDirectionKeyPressed(3);
   }

   public static double distanceMovedLastTick(Entity entity) {
      return Math.sqrt(entity.squaredDistanceTo(new Vec3d(entity.lastX, entity.lastY, entity.lastZ)));
   }

   public static boolean hasMovementInput() {
      return minecraftClient.player.input.getMovementInput().lengthSquared() > 0.0F;
   }

   public static final boolean isLeftPressed() {
      return isDirectionKeyPressed(1);
   }

   public static final boolean isBackwardPressed() {
      return isDirectionKeyPressed(2);
   }

   public static float predictFallDistance(int ticks) {
      Vector3f velocity = new Vector3f(0.0F, (float)minecraftClient.player.getVelocity().y, 0.0F);
      if (velocity.y != 0.0F && !minecraftClient.player.isOnGround()) {
         float fallDistance = 0.0F;
         double gravity = (0.08D);
         boolean falling = velocity.y <= 0.0;
         if (falling && minecraftClient.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            gravity = (0.01D);
         }

         for (int var6 = 0; var6 < ticks + 1; var6++) {
            double verticalVelocity = velocity.y;
            verticalVelocity -= gravity;
            velocity.y = (float)(verticalVelocity * (0.9800000190734863D));
            if (velocity.y > 0.0F) {
               fallDistance = 0.0F;
            } else {
               fallDistance -= velocity.y;
            }
         }

         return fallDistance;
      } else {
         return 0.0F;
      }
   }

   public static PlayerMovement.MotionPrediction predictMotion(PlayerEntity player, int ticks) {
      PlayerMovement.MotionPrediction prediction = new PlayerMovement.MotionPrediction();
      prediction.position = player.getEntityPos().toVector3f();
      prediction.velocity = player.getVelocity().toVector3f();
      double unusedStartY = player.getEntityPos().y;

      for (int var5 = 0; var5 < ticks; var5++) {
         double gravity = (0.08D);
         boolean falling = player.getVelocity().y <= 0.0;
         if (falling && player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            gravity = (0.01D);
         }

         prediction.velocity = prediction.velocity.add(0.0F, (float)(-gravity / (4.0D)), 0.0F);
         BlockHitResult hitResult = player.getEntityWorld()
            .raycast(new RaycastContext(new Vec3d(prediction.position), new Vec3d(prediction.position.add(prediction.velocity)), ShapeType.OUTLINE, FluidHandling.WATER, player));
         prediction.position = prediction.position.add(prediction.velocity);
      }

      prediction.fallDistance = predictFallDistance(ticks);
      return prediction;
   }

   public static final float yawForPressedKeys(float yaw) {
      return yaw
         + (
            !isLeftPressed() || !isRightPressed() || isForwardPressed() && isBackwardPressed() || !isForwardPressed() && !isBackwardPressed()
               ? (
                  !isForwardPressed() || !isBackwardPressed() || isLeftPressed() && isRightPressed() || !isLeftPressed() && !isRightPressed()
                     ? (
                        (!isLeftPressed() || !isRightPressed() || isForwardPressed() && isBackwardPressed()) && (!isForwardPressed() || !isBackwardPressed() || isLeftPressed() && isRightPressed())
                           ? (
                              !isLeftPressed() && !isRightPressed() && !isBackwardPressed()
                                 ? 0
                                 : (
                                       isForwardPressed() && !isBackwardPressed()
                                          ? 45
                                          : (
                                             !isBackwardPressed() || isForwardPressed()
                                                ? (!isForwardPressed() && !isBackwardPressed() || isForwardPressed() && isBackwardPressed() ? 90 : 0)
                                                : (!isLeftPressed() && !isRightPressed() ? 180 : 135)
                                          )
                                    )
                                    * (isLeftPressed() ? -1 : 1)
                           )
                           : 0
                     )
                     : (isLeftPressed() ? -90 : (isRightPressed() ? 90 : 0))
               )
               : (isForwardPressed() ? 0 : (isBackwardPressed() ? 180 : 0))
         );
   }

   public static class MotionPrediction {
      public boolean unusedCollisionFlag = false;
      public float fallDistance = 0.0F;
      public Vector3f position = new Vector3f();
      public Vector3f velocity = new Vector3f();
   }
}
