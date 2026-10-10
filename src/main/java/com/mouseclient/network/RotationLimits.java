package com.mouseclient.network;

import lombok.Generated;

public final class RotationLimits {
   private final float yawStep;
   private final float pitchStep;
   private final float returnYawStep;
   private final float returnPitchStep;

   @Generated
   public float returnYawStep() {
      return this.returnYawStep;
   }

   @Generated
   public float yawStep() {
      return this.yawStep;
   }

   public RotationLimits(float yawStep, float pitchStep, float returnYawStep, float returnPitchStep) {
      this.yawStep = yawStep;
      this.pitchStep = pitchStep;
      this.returnYawStep = returnYawStep;
      this.returnPitchStep = returnPitchStep;
   }

   @Generated
   public float returnPitchStep() {
      return this.returnPitchStep;
   }

   @Generated
   public float pitchStep() {
      return this.pitchStep;
   }

   public RotationLimits(float yawStep, float pitchStep) {
      this(yawStep, pitchStep, yawStep, pitchStep);
   }
}
