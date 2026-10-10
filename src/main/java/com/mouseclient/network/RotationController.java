package com.mouseclient.network;

import com.mouseclient.internal.ClientMath;
import com.mouseclient.modules.impl.combat.MovementInputEvent;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.MouseSensitivity;
import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.Stopwatch;
import com.mouseclient.annotation.EventPriority;
import lombok.Generated;
import net.minecraft.util.math.MathHelper;

public final class RotationController implements MinecraftAccess {
   private static RotationController INSTANCE;
   private float unusedYaw;
   private float unusedPitch;
   private float cameraYaw;
   private float cameraPitch;
   private float smoothedYawInput;
   private float smoothedPitchInput;
   private float yawInput;
   private float pitchInput;
   private RotationAngles targetRotation;
   private RotationLimits limits;
   private boolean active;
   private long tickCounter;
   private long lastRequestTick;
   private long holdDurationMillis;
   private long unusedMarker;
   private final Stopwatch holdTimer;
   private RotationController.ReturnMode returnMode;
   private boolean decoupledSilent;
   private long lastSilentTick;
   private final EventListener<PlayerTickEvent> playerTickListener;
   @EventPriority(-10)
   private final EventListener<MovementInputEvent> movementInputListener;

   public boolean isDecoupledSilent() {
      return this.decoupledSilent;
   }

   public void lockCamera() {
      if (minecraftClient.player == null) {
         return;
      }

      if (!this.active) {
         this.cameraYaw = minecraftClient.player.getYaw();
         this.cameraPitch = minecraftClient.player.getPitch();
         this.active = true;
      }

      this.decoupledSilent = true;
      this.lastSilentTick = this.tickCounter;
   }

   public void requestForDuration(RotationAngles rotation, RotationLimits limits, long durationMillis) {
      this.requestForDuration(rotation, limits, durationMillis, RotationController.ReturnMode.SMOOTH);
   }

   public void refreshCameraRotation() {
      if (minecraftClient.player != null && this.active) {
         this.cameraYaw = minecraftClient.player.getYaw();
         this.cameraPitch = minecraftClient.player.getPitch();
      }
   }

   public static void correctMovement(MovementInputEvent event, float desiredYaw) {
      if (event == null || event.isMovementCorrected()) {
         return;
      }
      float originalForward = event.getForward();
      float originalStrafe = event.getStrafe();
      double desiredMovementAngle = MathHelper.wrapDegrees(Math.toDegrees(movementAngle(desiredYaw, originalForward, originalStrafe)));
      if (originalForward != 0.0F || originalStrafe != 0.0F) {
         float bestForward = 0.0F;
         float bestStrafe = 0.0F;
         float minimumAngleError = (3.4028235E38F);

         for (float candidateForward = (-1.0F); candidateForward <= 1.0F; candidateForward++) {
            for (float candidateStrafe = (-1.0F); candidateStrafe <= 1.0F; candidateStrafe++) {
               if (candidateStrafe != 0.0F || candidateForward != 0.0F) {
                  double candidateMovementAngle = MathHelper.wrapDegrees(Math.toDegrees(movementAngle(minecraftClient.player.getYaw(), candidateForward, candidateStrafe)));
                  double angleError = Math.abs(desiredMovementAngle - candidateMovementAngle);
                  if (angleError < minimumAngleError) {
                     minimumAngleError = (float)angleError;
                     bestForward = candidateForward;
                     bestStrafe = candidateStrafe;
                  }
               }
            }
         }

         event.setForward(bestForward);
         event.setStrafe(bestStrafe);
         event.setMovementCorrected(true);
      }
   }

   @Generated
   public float smoothedYawInput() {
      return this.smoothedYawInput;
   }

   public void requestRotation(RotationAngles rotation, RotationLimits limits) {
      this.requestForDuration(rotation, limits, 0L);
   }

   public void reset() {
      if (this.active && minecraftClient.player != null) {
         minecraftClient.player.setYaw(this.cameraYaw);
         minecraftClient.player.setPitch(this.cameraPitch);
         minecraftClient.player.headYaw = this.cameraYaw;
         minecraftClient.player.bodyYaw = this.cameraYaw;
      }
      this.active = false;
      this.decoupledSilent = false;
      this.targetRotation = null;
      this.unusedMarker = (-1L);
   }

   public void snapSilent(float yaw, float pitch) {
      if (minecraftClient.player == null) {
         return;
      }

      if (!this.active) {
         this.cameraYaw = minecraftClient.player.getYaw();
         this.cameraPitch = minecraftClient.player.getPitch();
         this.active = true;
      }

      minecraftClient.player.setYaw(yaw);
      minecraftClient.player.setPitch(pitch);
      minecraftClient.player.headYaw = yaw;
      minecraftClient.player.bodyYaw = yaw;

      this.targetRotation = new RotationAngles(yaw, pitch);
      this.limits = new RotationLimits(360.0F, 360.0F, 50.0F, 50.0F);
      this.lastRequestTick = this.tickCounter;
      this.holdDurationMillis = 100L;
      this.holdTimer.reset();
      this.returnMode = RotationController.ReturnMode.FAST;
   }

   public boolean applyMouseInput(double yawDelta, double pitchDelta) {
      if (!this.active) {
         return false;
      }

      this.cameraYaw = this.cameraYaw + (float)yawDelta * (0.15F);
      this.cameraPitch = MathHelper.clamp(this.cameraPitch + (float)pitchDelta * (0.15F), (-90.0F), (90.0F));
      this.yawInput = this.yawInput + Math.abs((float)yawDelta * (0.15F));
      this.pitchInput = this.pitchInput + Math.abs((float)pitchDelta * (0.15F));
      return true;
   }

   @Generated
   public float cameraPitch() {
      return this.cameraPitch;
   }

   private RotationController() {
      this.unusedMarker = (-1L);
      this.holdTimer = new Stopwatch();
      this.returnMode = RotationController.ReturnMode.SMOOTH;
      this.movementInputListener = event -> {
         if (minecraftClient.player != null && this.active) {
            correctMovement(event, this.cameraYaw);
         }
      };
      this.playerTickListener = event -> {
         if (minecraftClient.player != null) {
            this.tickCounter++;
            this.smoothedYawInput = this.smoothedYawInput + (this.yawInput - this.smoothedYawInput) * (0.4F);
            this.smoothedPitchInput = this.smoothedPitchInput + (this.pitchInput - this.smoothedPitchInput) * (0.4F);
            this.yawInput = this.pitchInput = 0.0F;

            if (this.decoupledSilent) {
               if (this.tickCounter - this.lastSilentTick > 1L) {
                  this.reset();
               }
               return;
            }

            if (this.tickCounter - this.lastRequestTick <= 1L && this.targetRotation != null && this.limits != null) {
               if (!this.active) {
                  this.cameraYaw = minecraftClient.player.getYaw();
                  this.cameraPitch = minecraftClient.player.getPitch();
                  this.active = true;
               }

               this.applyRotationStep(this.targetRotation, this.limits.yawStep(), this.limits.pitchStep());
            } else if (this.active && this.targetRotation != null && !this.holdTimer.hasElapsed(this.holdDurationMillis)) {
               this.applyRotationStep(this.targetRotation, this.limits.yawStep(), this.limits.pitchStep());
            } else if (this.active) {
               if (this.returnMode == RotationController.ReturnMode.FAST) {
                  if (this.limits == null) {
                     this.reset();
                     return;
                  }
                  float fastYawError = MathHelper.wrapDegrees(this.cameraYaw - minecraftClient.player.getYaw());
                  float fastPitchError = this.cameraPitch - minecraftClient.player.getPitch();
                  if (Math.abs(fastYawError) <= this.limits.returnYawStep() && Math.abs(fastPitchError) <= this.limits.returnPitchStep()) {
                     minecraftClient.player.setYaw(minecraftClient.player.headYaw = this.cameraYaw);
                     minecraftClient.player.setPitch(this.cameraPitch);
                     this.unusedMarker = (-1L);
                     this.active = false;
                     return;
                  }

                  float fastAngularDistance = (float)Math.hypot(fastYawError, fastPitchError);
                  float fastReturnSpeed = minecraftClient.player.getAttackCooldownProgress(1.0F) >= 1.0F ? ClientMath.randomFloat((35.0F), (45.0F)) : 0.0F;
                  float fastYawStep = Math.abs(fastYawError / fastAngularDistance) * fastReturnSpeed;
                  float fastPitchStep = Math.abs(fastPitchError / fastAngularDistance) * fastReturnSpeed;
                  float yawJitter = ClientMath.randomFloat((-0.4F), (0.4F));
                  float pitchJitter = ClientMath.randomFloat((-0.2F), (0.2F));
                  float blendedYaw = MathHelper.lerp(
                     (0.85F), minecraftClient.player.getYaw(), minecraftClient.player.getYaw() + MathHelper.clamp(fastYawError, -fastYawStep, fastYawStep) + yawJitter
                  );
                  float blendedPitch = MathHelper.lerp(
                     (0.85F), minecraftClient.player.getPitch(), minecraftClient.player.getPitch() + MathHelper.clamp(fastPitchError, -fastPitchStep, fastPitchStep) + pitchJitter
                  );
                  minecraftClient.player
                     .setYaw(
                        minecraftClient.player.headYaw = minecraftClient.player.headYaw
                           + MouseSensitivity.quantize(MathHelper.wrapDegrees(blendedYaw - minecraftClient.player.getYaw()))
                     );
                  minecraftClient.player
                     .setPitch(
                        MathHelper.clamp(
                           minecraftClient.player.getPitch() + MouseSensitivity.quantize(blendedPitch - minecraftClient.player.getPitch()),
                           (-90.0F),
                           (90.0F)
                        )
                     );
                  return;
               }

               if (this.returnMode == RotationController.ReturnMode.NONE || this.limits == null) {
                  this.active = false;
                  return;
               }

               float smoothYawError = MathHelper.wrapDegrees(this.cameraYaw - minecraftClient.player.getYaw());
               float smoothPitchError = this.cameraPitch - minecraftClient.player.getPitch();
               float smoothYawStep = Math.min(Math.abs(smoothYawError), this.limits.returnYawStep());
               float smoothPitchStep = Math.min(Math.abs(smoothPitchError), this.limits.returnPitchStep());
               minecraftClient.player
                  .setYaw(minecraftClient.player.headYaw = minecraftClient.player.headYaw + MouseSensitivity.quantize(MathHelper.clamp(smoothYawError, -smoothYawStep, smoothYawStep)));
               minecraftClient.player
                  .setPitch(
                     MathHelper.clamp(
                        minecraftClient.player.getPitch() + MouseSensitivity.quantize(MathHelper.clamp(smoothPitchError, -smoothPitchStep, smoothPitchStep)), (-90.0F), (90.0F)
                     )
                  );
               if (Math.abs(smoothYawError) <= this.limits.returnYawStep() && Math.abs(smoothPitchError) <= this.limits.returnPitchStep()) {
                  minecraftClient.player.setYaw(minecraftClient.player.headYaw = this.cameraYaw);
                  minecraftClient.player.setPitch(this.cameraPitch);
                  this.active = false;
               }
            }
         }
      };
   }

   private static double movementAngle(float yaw, float forward, float strafe) {
      return Math.atan2(
         forward * Math.cos(Math.toRadians(yaw)) + strafe * Math.sin(Math.toRadians(yaw)),
         strafe * Math.cos(Math.toRadians(yaw)) - forward * Math.sin(Math.toRadians(yaw))
      );
   }

   @Generated
   public float unusedYaw() {
      return this.unusedYaw;
   }

   public static RotationController getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new RotationController();
         EventBus.getInstance().subscribe(INSTANCE);
      }

      return INSTANCE;
   }

   public static void correctMovementForGliding(MovementInputEvent event, float rotationYaw, float cameraYaw) {
      if (event.getForward() != 0.0F || event.getStrafe() != 0.0F) {
         double desiredMovementAngle = MathHelper.wrapDegrees(
            (float)Math.toDegrees(movementAngle(minecraftClient.player.isGliding() ? rotationYaw : cameraYaw, event.getForward(), event.getStrafe()))
         );
         float bestForward = 0.0F;
         float bestStrafe = 0.0F;
         float minimumAngleError = (3.4028235E38F);

         for (float candidateForward = (-1.0F); candidateForward <= 1.0F; candidateForward++) {
            for (float candidateStrafe = (-1.0F); candidateStrafe <= 1.0F; candidateStrafe++) {
               if ((candidateStrafe != 0.0F || candidateForward != 0.0F) && (float)Math.abs(desiredMovementAngle - MathHelper.wrapDegrees((float)Math.toDegrees(movementAngle(rotationYaw, candidateForward, candidateStrafe)))) < minimumAngleError) {
                  minimumAngleError = (float)Math.abs(desiredMovementAngle - MathHelper.wrapDegrees((float)Math.toDegrees(movementAngle(rotationYaw, candidateForward, candidateStrafe))));
                  bestForward = candidateForward;
                  bestStrafe = candidateStrafe;
               }
            }
         }

         event.setForward(bestForward);
         event.setStrafe(bestStrafe);
      }
   }

   public void requestRotation(RotationAngles rotation, RotationLimits limits, RotationController.ReturnMode returnMode) {
      this.requestForDuration(rotation, limits, 0L, returnMode);
   }

   @Generated
   public float smoothedPitchInput() {
      return this.smoothedPitchInput;
   }

   @Generated
   public float unusedPitch() {
      return this.unusedPitch;
   }

   private boolean applyRotationStep(RotationAngles rotation, float yawStep, float pitchStep) {
      float yawError = MathHelper.wrapDegrees(rotation.getYaw() - minecraftClient.player.getYaw());
      float pitchError = rotation.getPitch() - minecraftClient.player.getPitch();
      float clampedYawStep = Math.min(Math.abs(yawError), yawStep);
      float clampedPitchStep = Math.min(Math.abs(pitchError), pitchStep);
      minecraftClient.player
         .setYaw(minecraftClient.player.headYaw = minecraftClient.player.headYaw + MouseSensitivity.quantize(MathHelper.clamp(yawError, -clampedYawStep, clampedYawStep)));
      minecraftClient.player
         .setPitch(
            MathHelper.clamp(minecraftClient.player.getPitch() + MouseSensitivity.quantize(MathHelper.clamp(pitchError, -clampedPitchStep, clampedPitchStep)), (-90.0F), (90.0F))
         );
      return Math.abs(yawError) <= yawStep && Math.abs(pitchError) <= pitchStep;
   }

   public void requestForDuration(RotationAngles rotation, RotationLimits limits, long durationMillis, RotationController.ReturnMode returnMode) {
      RotationAngles visibleAimRotation = CombatTargeting.findVisibleAimRotation(rotation);
      if (visibleAimRotation != rotation) {
         rotation = visibleAimRotation;
         limits = new RotationLimits(Math.max(limits.yawStep(), (15.0F)), Math.max(limits.pitchStep(), (15.0F)), limits.returnYawStep(), limits.returnPitchStep());
      }

      this.returnMode = returnMode;
      this.targetRotation = rotation;
      this.limits = limits;
      this.lastRequestTick = this.tickCounter;
      this.holdDurationMillis = durationMillis;
      this.unusedMarker = (-1L);
      this.holdTimer.reset();
   }

   @Generated
   public float cameraYaw() {
      return this.cameraYaw;
   }

   @Generated
   public boolean isActive() {
      return this.active;
   }

   public enum ReturnMode {
      NONE,
      SMOOTH,
      FAST;
   }
}
