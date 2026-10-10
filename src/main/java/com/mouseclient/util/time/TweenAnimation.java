package com.mouseclient.util.time;

import com.mouseclient.render.avatar.Easings;

public class TweenAnimation {
   private long startedAt;
   private boolean active;
   private double durationMillis;
   private double startValue;
   private double targetValue;
   private double currentValue;
   private double previousValue;
   private EasingFunction easing;

   public TweenAnimation animateTo(double target, double durationSeconds, EasingFunction easing) {
      if (this.isTargetUnchanged(target)) {
         return this;
      }

      this.easing = easing;
      this.durationMillis = durationSeconds * (1000.0D);
      this.startedAt = System.currentTimeMillis();
      this.startValue = this.currentValue;
      this.targetValue = target;
      this.active = true;
      return this;
   }

   public TweenAnimation animateTo(double target, double durationSeconds) {
      return this.animateTo(target, durationSeconds, Easings.LINEAR);
   }

   public boolean isRunning() {
      return !this.isFinished();
   }

   public float floatValue() {
      return (float)this.currentValue;
   }

   private boolean isTargetUnchanged(double target) {
      return this.active ? this.approximatelyEqual(target, this.targetValue) : this.approximatelyEqual(target, this.currentValue);
   }

   public double getProgress() {
      return this.active && !(this.durationMillis <= 0.0) ? (System.currentTimeMillis() - this.startedAt) / this.durationMillis : 1.0;
   }

   public float previousFloatValue() {
      return (float)this.previousValue;
   }

   public TweenAnimation() {
      this.easing = Easings.LINEAR;
   }

   public double lerp(double start, double end, double progress) {
      return start + (end - start) * progress;
   }

   public boolean isFinished() {
      return !this.active || this.getProgress() >= 1.0;
   }

   public void setValue(double value) {
      this.active = false;
      this.startedAt = 0L;
      this.currentValue = value;
      this.targetValue = value;
   }

   private boolean approximatelyEqual(double first, double second) {
      return Math.abs(first - second) <= (1.0E-6D);
   }

   public boolean update() {
      this.previousValue = this.currentValue;
      if (!this.active) {
         return false;
      }

      boolean running = this.isRunning();
      if (running) {
         this.currentValue = this.lerp(this.startValue, this.targetValue, this.easing.apply(this.getProgress()));
      } else {
         this.startedAt = 0L;
         this.active = false;
         this.currentValue = this.targetValue;
      }

      return running;
   }
}
