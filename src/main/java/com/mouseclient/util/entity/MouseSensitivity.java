package com.mouseclient.util.entity;

public final class MouseSensitivity implements MinecraftAccess {

   public static float rotationStep() {
      return (float)(sensitivityScale() * (0.15D));
   }

   public static float rescaleFineStep(float delta) {
      return delta / fineRotationStep() * fineRotationStep();
   }

   public static float quantize(float delta) {
      return stepCount(delta) * rotationStep();
   }

   public float preciseRotationStep() {
      return (float)(
         Math.pow(minecraftClient.options.getMouseSensitivity().getValue() * (0.6D) + (0.2D), (3.0D)) * (8.0D) * (0.15D)
      );
   }

   public static float quantizeWithMinimum(float delta) {
      return stepCount(delta) == 0.0F && Math.abs(delta) >= rotationStep() * (0.2F) ? Math.signum(delta) * rotationStep() : quantize(delta);
   }

   public static float quantizeFine(float delta) {
      return Math.round(delta / fineRotationStep()) * fineRotationStep();
   }

   public static float stepCount(float delta) {
      return Math.round(delta / rotationStep());
   }

   public static float fineSensitivityScale() {
      return (float)(Math.pow(minecraftClient.options.getMouseSensitivity().getValue() * (0.6D) + (0.2D), (3.0D)) * (0.78D));
   }

   public static float fineRotationStep() {
      return fineSensitivityScale() * (0.15F);
   }

   public float quantizePrecise(float delta) {
      return Math.round(delta / this.preciseRotationStep()) * this.preciseRotationStep();
   }

   public static float sensitivityScale() {
      double mouseSensitivity = minecraftClient.options.getMouseSensitivity().getValue();
      return (float)(Math.pow(mouseSensitivity * (0.6000000238418579D) + (0.20000000298023224D), (3.0D)) * (8.0D));
   }
}
