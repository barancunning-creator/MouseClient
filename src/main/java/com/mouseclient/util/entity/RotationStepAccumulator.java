package com.mouseclient.util.entity;

public final class RotationStepAccumulator implements MinecraftAccess {
   private float remainder;

   public void reset() {
      this.remainder = 0.0F;
   }

   public float quantize(float delta) {
      float rotationStep = MouseSensitivity.rotationStep();
      float accumulatedDelta = delta + this.remainder;
      float quantizedDelta = Math.round(accumulatedDelta / rotationStep) * rotationStep;
      this.remainder = accumulatedDelta - quantizedDelta;
      return quantizedDelta;
   }
}
