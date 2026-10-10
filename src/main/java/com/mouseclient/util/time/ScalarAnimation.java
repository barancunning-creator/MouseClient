package com.mouseclient.util.time;

public final class ScalarAnimation {
   private final QuadraticAnimation animation = new QuadraticAnimation();
   private double startValue;
   private double targetValue;

   public void setTarget(double target) {
      if (!(Math.abs(target - this.targetValue) < (1.0E-6D))) {
         this.startValue = this.value();
         this.targetValue = target;
         this.animation.timer.reset();
      }
   }

   public ScalarAnimation reset(double value) {
      this.startValue = value;
      this.targetValue = value;
      this.animation.timer.reset();
      return this;
   }

   public double value() {
      return this.startValue + (this.targetValue - this.startValue) * this.animation.value();
   }

   public double target() {
      return this.targetValue;
   }

   public ScalarAnimation(long durationMillis) {
      this.animation.setDurationMillis(durationMillis);
      this.animation.setAmplitude(1.0);
      this.animation.setDirection(AnimationDirection.IN);
   }
}
