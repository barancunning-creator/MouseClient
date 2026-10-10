package com.mouseclient.util.entity;

import com.mouseclient.internal.ClientMath;
import com.mouseclient.network.RotationController;
import com.mouseclient.network.RotationLimits;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Shard022RotationController implements MinecraftAccess {
   private static final int POINT_COUNT = 8;
   private Vec3d aimPoint;
   private float yawSpeed;
   private float pitchSpeed;
   private float overshootYaw;
   private float overshootPitch;
   private float driftYawRemainder;
   private float driftPitchRemainder;
   private float yawRemainder;
   private float pitchRemainder;
   private int pointIndex;
   private int pointHold;
   private int returnTicks;
   private int ticks;
   private float pitchLag;
   private float flickScale;
   private boolean carrying;
   private boolean wasReady;
   private float hitHeight = 0.6F;
   private float hitSide;
   private float hitLead;

   public void reset() {
      this.aimPoint = null;
      this.yawSpeed = this.pitchSpeed = 0.0F;
      this.overshootYaw = this.overshootPitch = 0.0F;
      this.driftYawRemainder = this.driftPitchRemainder = 0.0F;
      this.yawRemainder = this.pitchRemainder = 0.0F;
      this.pointIndex = 0;
      this.pointHold = 0;
      this.returnTicks = 0;
      this.ticks = 0;
      this.pitchLag = 0.0F;
      this.flickScale = 1.0F;
      this.carrying = false;
      this.wasReady = false;
      this.rollHit();
      RotationController.getInstance().reset();
   }

   public void update(LivingEntity entity, boolean attackReady, float attackRange) {
      if (entity == null || minecraftClient.player == null) {
         return;
      }

      Vec3d eye = minecraftClient.player.getEyePos();
      Box box = entity.getBoundingBox();
      Vec3d motion = new Vec3d(entity.getX() - entity.lastX, entity.getY() - entity.lastY, entity.getZ() - entity.lastZ);
      if (motion.lengthSquared() < 1.0E-6D) {
         motion = entity.getVelocity();
      }

      double rayRange = Math.max(attackRange + 0.8F, eye.distanceTo(box.getCenter()) + 1.25D);
      boolean onBox = CombatTargeting.rayHitsEntityBounds(entity, rayRange, minecraftClient.player.getYaw(), minecraftClient.player.getPitch());
      this.ticks++;
      int nextPoint = this.pickPoint(box, eye, motion, onBox);
      if (nextPoint != this.pointIndex && this.pointHold <= 0) {
         float[] fromAngles = CombatTargeting.anglesBetween(this.sample(box, eye, motion, this.pointIndex), eye);
         float[] toAngles = CombatTargeting.anglesBetween(this.sample(box, eye, motion, nextPoint), eye);
         float switchedYaw = MathHelper.wrapDegrees(toAngles[0] - fromAngles[0]);
         float switchedPitch = toAngles[1] - fromAngles[1];
         if (Math.abs(switchedYaw) > (onBox ? 7.0F : 4.5F) && Math.abs(this.overshootYaw) < 3.0F) {
            this.yawSpeed *= 0.72F;
            this.pitchSpeed *= 0.72F;
         }

         this.pointIndex = nextPoint;
         this.pointHold = onBox ? 18 : 10;
      } else if (this.pointHold > 0) {
         this.pointHold--;
      }

      if (this.wasReady && !attackReady) {
         this.rollHit();
      }

      this.wasReady = attackReady;
      Vec3d desired = this.sample(box, eye, motion, this.pointIndex).lerp(this.randomHit(box, eye, motion), 0.84D);
      double follow = onBox ? 0.16D : attackReady ? 0.46D : 0.32D;
      this.aimPoint = this.aimPoint == null ? desired : this.aimPoint.lerp(desired, follow);
      Vec3d aim = this.clamp(this.aimPoint, this.inner(box));
      float[] angles = CombatTargeting.anglesBetween(aim, eye);
      float idealYaw = angles[0];
      float idealPitch = MathHelper.clamp(angles[1], -90.0F, 90.0F);
      float yawError = MathHelper.wrapDegrees(idealYaw - minecraftClient.player.getYaw());
      float pitchError = idealPitch - minecraftClient.player.getPitch();
      float fovToTarget = (float)Math.hypot(yawError, pitchError);
      if (!onBox && Math.abs(this.yawSpeed) > 6.0F && Math.abs(yawError) < 4.0F && Math.signum(this.yawSpeed) != Math.signum(yawError)) {
         this.overshootYaw = MathHelper.clamp(this.yawSpeed * 0.35F, -4.5F, 4.5F);
         this.overshootPitch = MathHelper.clamp(this.pitchSpeed * 0.25F, -2.0F, 2.0F);
         this.returnTicks = 6;
      }

      if (onBox && this.returnTicks == 0 && (this.carrying || Math.abs(this.overshootYaw) > 1.8F || Math.abs(this.yawSpeed) > 5.5F)) {
         this.carrying = false;
         this.returnTicks = 6;
      }

      if (this.returnTicks > 0) {
         this.returnTicks--;
         float decay = 0.62F;
         this.overshootYaw *= decay;
         this.overshootPitch *= decay;
         this.carrying = false;
      } else if (onBox && Math.abs(this.overshootYaw) < 0.85F && Math.abs(this.yawSpeed) < 2.2F) {
         if (this.holdOnBox(entity, box, eye, motion, rayRange)) {
            return;
         }
      }

      float aimYaw = idealYaw + this.overshootYaw;
      float aimPitch = MathHelper.clamp(idealPitch + this.overshootPitch, -90.0F, 90.0F);
      this.accelerate(aimYaw, aimPitch, this.carrying, this.returnTicks > 0);
   }

   private void accelerate(float aimYaw, float aimPitch, boolean carry, boolean returning) {
      float aimYawError = MathHelper.wrapDegrees(aimYaw - minecraftClient.player.getYaw());
      float aimPitchError = aimPitch - minecraftClient.player.getPitch();
      float fov = (float)Math.hypot(aimYawError, aimPitchError);
      boolean isWideFov = fov > 100.0F;

      this.pitchLag += (aimPitchError - this.pitchLag) * (returning ? 0.26F : (isWideFov ? 0.65F : 0.4F));
      if (!carry && Math.abs(aimYawError) < 6.0F && Math.signum(aimYawError) != 0.0F && Math.signum(this.yawSpeed) != 0.0F && Math.signum(aimYawError) != Math.signum(this.yawSpeed)) {
         this.yawSpeed *= returning ? -0.2F : -0.34F;
         this.pitchSpeed *= 0.45F;
      }

      float yawDistance = Math.abs(aimYawError);
      float yawCruise;
      float pitchCruise;
      float yawWindup;
      float pitchWindup;

      if (!isWideFov) {
         // В пределах 100 FOV: средняя скорость — плавная, естественная и легитная
         float normalizedDist = MathHelper.clamp(yawDistance / 40.0F, 0.0F, 1.0F);
         yawCruise = MathHelper.lerp(this.ease(normalizedDist), 3.5F, 34.0F);

         float pitchDist = MathHelper.clamp(Math.abs(this.pitchLag) / 22.0F, 0.0F, 1.0F);
         pitchCruise = MathHelper.lerp(this.ease(pitchDist), 1.8F, 16.0F);

         yawWindup = 0.24F + 0.34F * this.ease(Math.abs(this.yawSpeed) / 12.0F);
         pitchWindup = 0.18F + 0.28F * this.ease(Math.abs(this.pitchSpeed) / 8.0F);

         if (carry) {
            yawCruise = Math.max(yawCruise, Math.min(20.0F, Math.abs(this.overshootYaw) * 0.65F + 5.0F));
            pitchCruise = Math.max(pitchCruise, Math.min(7.0F, Math.abs(this.overshootPitch) * 0.5F + 1.6F));
         } else if (returning) {
            yawCruise *= 0.48F;
            pitchCruise *= 0.42F;
            yawWindup = Math.min(0.22F, Math.max(yawWindup, 0.14F));
            pitchWindup = Math.min(0.18F, Math.max(pitchWindup, 0.1F));
         } else if (yawDistance < 8.0F) {
            yawWindup = Math.max(yawWindup, 0.34F);
            pitchWindup = Math.max(pitchWindup, 0.22F);
         }
      } else {
         // Более 100 FOV: быстрая скорость и высокий рывок, чтобы мгновенно успеть за игроком
         float excessRatio = MathHelper.clamp((fov - 100.0F) / 60.0F, 0.0F, 1.0F);
         yawCruise = MathHelper.lerp(this.ease(MathHelper.clamp(fov / 140.0F, 0.0F, 1.0F)), 12.0F, 46.0F);
         pitchCruise = yawCruise * 0.42F;
         yawWindup = 0.32F + 0.28F * excessRatio;
         pitchWindup = yawWindup * 0.8F;
      }

      float yawSign = aimYawError == 0.0F ? Math.signum(this.yawSpeed) : Math.signum(aimYawError);
      float pitchSign = this.pitchLag == 0.0F ? Math.signum(this.pitchSpeed) : Math.signum(this.pitchLag);
      this.yawSpeed += (yawSign * yawCruise - this.yawSpeed) * yawWindup;
      this.pitchSpeed += (pitchSign * pitchCruise - this.pitchSpeed) * pitchWindup;
      this.yawRemainder += MathHelper.clamp(this.yawSpeed, -yawDistance, yawDistance);
      this.pitchRemainder += MathHelper.clamp(this.pitchSpeed, -Math.abs(aimPitchError), Math.abs(aimPitchError));
      float yawStep = Math.abs(MouseSensitivity.quantize(this.yawRemainder));
      float pitchStep = Math.abs(MouseSensitivity.quantize(this.pitchRemainder));
      this.yawRemainder -= Math.copySign(yawStep, this.yawRemainder);
      this.pitchRemainder -= Math.copySign(pitchStep, this.pitchRemainder);
      this.request(aimYaw, aimPitch, yawStep, pitchStep);
   }

   private void kickOvershoot(float yawError, float pitchError, float strength) {
      this.flickScale = ClientMath.randomFloatMixture(0.78F, 1.16F);
      float yawSign = yawError != 0.0F ? Math.signum(yawError) : Math.signum(this.yawSpeed == 0.0F ? 1.0F : this.yawSpeed);
      float pitchSign = pitchError != 0.0F ? Math.signum(pitchError) : Math.signum(this.pitchSpeed);
      float yawMag = MathHelper.clamp((Math.abs(yawError) * 0.22F + Math.abs(this.yawSpeed) * 0.36F) * strength * this.flickScale, 5.0F, 18.0F);
      float pitchMag = MathHelper.clamp((Math.abs(pitchError) * 0.16F + Math.abs(this.pitchSpeed) * 0.22F) * strength * this.flickScale, 1.2F, 6.5F);
      this.overshootYaw = yawSign * yawMag;
      this.overshootPitch = pitchSign * pitchMag;
      this.carrying = true;
      this.returnTicks = 0;
   }

   private void rollHit() {
      this.hitHeight = ClientMath.randomFloatMixture(0.18F, 0.92F);
      this.hitSide = ClientMath.randomFloatMixture(-0.84F, 0.84F);
      this.hitLead = ClientMath.randomFloatMixture(-0.4F, 0.62F);
   }

   private Vec3d randomHit(Box box, Vec3d eye, Vec3d motion) {
      Vec3d center = box.getCenter();
      Vec3d right = this.horizontalRight(eye, center);
      Vec3d forward = new Vec3d(-right.z, 0.0, right.x);
      double side = this.hitSide * box.getLengthX() * 0.4;
      double lead = this.hitLead * box.getLengthZ() * 0.24 + MathHelper.clamp(motion.dotProduct(forward), -0.2, 0.28);
      return this.clamp(
         new Vec3d(center.x + right.x * side + forward.x * lead, box.minY + box.getLengthY() * this.hitHeight, center.z + right.z * side + forward.z * lead),
         this.inner(box)
      );
   }

   private boolean holdOnBox(LivingEntity entity, Box box, Vec3d eye, Vec3d motion, double rayRange) {
      this.yawSpeed *= 0.7F;
      this.pitchSpeed *= 0.62F;
      this.pitchLag *= 0.55F;
      this.overshootYaw *= 0.5F;
      this.overshootPitch *= 0.5F;
      float yaw = minecraftClient.player.getYaw();
      float pitch = minecraftClient.player.getPitch();
      Vec3d point = this.sample(box, eye, motion, this.pointIndex);
      float[] pointAngles = CombatTargeting.anglesBetween(point, eye);
      float yawDelta = MathHelper.wrapDegrees(pointAngles[0] - yaw);
      float pitchDelta = pointAngles[1] - pitch;
      if (Math.abs(yawDelta) > 8.0F) {
         return false;
      }

      if ((this.ticks & 1) == 0) {
         this.driftYawRemainder += MathHelper.clamp(yawDelta, -1.6F, 1.6F) * 0.22F;
         this.driftPitchRemainder += MathHelper.clamp(pitchDelta, -0.7F, 0.7F) * 0.16F;
      }
      float step = MouseSensitivity.rotationStep();
      float yawTick = Math.abs(this.driftYawRemainder) >= step ? Math.copySign(step, this.driftYawRemainder) : 0.0F;
      float pitchTick = Math.abs(this.driftPitchRemainder) >= step ? Math.copySign(step, this.driftPitchRemainder) : 0.0F;
      yawTick = MouseSensitivity.quantize(yawTick);
      pitchTick = MouseSensitivity.quantize(pitchTick);
      float nextYaw = yaw + yawTick;
      float nextPitch = MathHelper.clamp(pitch + pitchTick, -90.0F, 90.0F);
      if ((yawTick != 0.0F || pitchTick != 0.0F) && CombatTargeting.rayHitsEntityBounds(entity, rayRange, nextYaw, nextPitch)) {
         this.driftYawRemainder -= yawTick;
         this.driftPitchRemainder -= pitchTick;
         yaw = nextYaw;
         pitch = nextPitch;
      } else if (yawTick != 0.0F || pitchTick != 0.0F) {
         this.driftYawRemainder *= 0.35F;
         this.driftPitchRemainder *= 0.35F;
      }

      float holdStep = Math.max(step, Math.max(Math.abs(MathHelper.wrapDegrees(yaw - minecraftClient.player.getYaw())), Math.abs(pitch - minecraftClient.player.getPitch())));
      this.request(yaw, pitch, holdStep, holdStep);
      return true;
   }

   private int pickPoint(Box box, Vec3d eye, Vec3d motion, boolean onBox) {
      Vec3d right = this.horizontalRight(eye, box.getCenter());
      double side = this.sideFactor(right, motion);
      float bestScore = Float.MAX_VALUE;
      int bestIndex = this.pointIndex;

      for (int index = 0; index < POINT_COUNT; index++) {
         float[] angles = CombatTargeting.anglesBetween(this.sample(box, eye, motion, index), eye);
         float error = (float)Math.hypot(MathHelper.wrapDegrees(angles[0] - minecraftClient.player.getYaw()), angles[1] - minecraftClient.player.getPitch());
         float score = error - (float)(this.pointSide(index) * side) * 2.4F;
         if (onBox && index == this.pointIndex) {
            score -= 3.2F;
         }

         if (score < bestScore) {
            bestScore = score;
            bestIndex = index;
         }
      }

      return bestIndex;
   }

   private float ease(float value) {
      float t = MathHelper.clamp(value, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }

   private Vec3d sample(Box box, Vec3d eye, Vec3d motion, int index) {
      Vec3d center = box.getCenter();
      Vec3d right = this.horizontalRight(eye, center);
      Vec3d forward = new Vec3d(-right.z, 0.0, right.x);
      double side = this.sideFactor(right, motion);
      double signedSide = Math.copySign(this.pointSide(index), side == 0.0 ? 1.0 : side);
      if (index == 5) {
         signedSide = -signedSide;
      }

      double height = box.minY + box.getLengthY() * this.pointHeight(index);
      double arm = signedSide * box.getLengthX() * 0.46D;
      double lead = this.pointLead(index) * MathHelper.clamp(motion.dotProduct(forward), -0.22D, 0.28D);
      return this.clamp(new Vec3d(center.x + right.x * arm + forward.x * lead, height, center.z + right.z * arm + forward.z * lead), this.inner(box));
   }

   private double pointHeight(int index) {
      return switch (index) {
         case 1 -> 0.86D;
         case 2 -> 0.4D;
         case 3 -> 0.7D;
         case 4 -> 0.56D;
         case 5 -> 0.64D;
         case 6 -> 0.76D;
         case 7 -> 0.34D;
         default -> 0.6D;
      };
   }

   private double pointSide(int index) {
      return switch (index) {
         case 3 -> 0.34D;
         case 4 -> 0.48D;
         case 5 -> 0.22D;
         case 6 -> 0.26D;
         case 7 -> 0.3D;
         case 1 -> 0.04D;
         default -> 0.08D;
      };
   }

   private double pointLead(int index) {
      return switch (index) {
         case 4, 6 -> 1.0D;
         case 3, 7 -> 0.65D;
         case 5 -> -0.25D;
         default -> 0.15D;
      };
   }

   private double sideFactor(Vec3d right, Vec3d motion) {
      if (motion.horizontalLengthSquared() <= 1.0E-6D) {
         return 0.0;
      }

      return MathHelper.clamp(motion.dotProduct(right) / Math.max(0.05D, Math.sqrt(motion.horizontalLengthSquared())), -1.0, 1.0);
   }

   private Vec3d horizontalRight(Vec3d eye, Vec3d center) {
      Vec3d forward = new Vec3d(center.x - eye.x, 0.0, center.z - eye.z);
      if (forward.lengthSquared() < 1.0E-6D) {
         forward = new Vec3d(0.0, 0.0, 1.0);
      }

      forward = forward.normalize();
      return new Vec3d(-forward.z, 0.0, forward.x);
   }

   private Box inner(Box box) {
      Box contracted = box.contract(
         Math.min(0.05D, box.getLengthX() * 0.08D), Math.min(0.06D, box.getLengthY() * 0.05D), Math.min(0.05D, box.getLengthZ() * 0.08D)
      );
      return contracted.getLengthX() < 0.05D || contracted.getLengthY() < 0.05D ? box : contracted;
   }

   private Vec3d clamp(Vec3d point, Box box) {
      return new Vec3d(
         MathHelper.clamp(point.x, box.minX, box.maxX),
         MathHelper.clamp(point.y, box.minY, box.maxY),
         MathHelper.clamp(point.z, box.minZ, box.maxZ)
      );
   }

   private void request(float yaw, float pitch, float yawStep, float pitchStep) {
      float safeYawStep = Math.max(0.01F, yawStep);
      float safePitchStep = Math.max(0.01F, pitchStep);
      RotationController.getInstance()
         .requestRotation(
            new RotationAngles(yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)),
            new RotationLimits(safeYawStep, safePitchStep, 6.0F, 4.0F),
            RotationController.ReturnMode.SMOOTH
         );
   }
}
