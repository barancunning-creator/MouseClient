package com.mouseclient.util.time;

public class QuadraticAnimation {
   public final QuadraticAnimation.AnimationTimer timer = new QuadraticAnimation.AnimationTimer();
   private long durationMillis;
   private double amplitude;
   private AnimationDirection direction;

   public QuadraticAnimation() {
      this.durationMillis = (200L);
      this.amplitude = 1.0;
      this.direction = com.mouseclient.util.time.AnimationDirection.IN;
   }

   public boolean isFinished() {
      return this.timer.elapsedMillis() >= this.durationMillis;
   }

   public void setAmplitude(double amplitude) {
      this.amplitude = amplitude;
   }

   public void setDurationMillis(long durationMillis) {
      this.durationMillis = durationMillis;
   }

   public void setDirection(AnimationDirection direction) {
      this.direction = direction;
   }

   public double value() {
      double elapsedMillis = this.timer.elapsedMillis();
      double progress = this.durationMillis > 0L ? Math.min(elapsedMillis / this.durationMillis, 1.0) : 1.0;
      double easedProgress = 1.0 - (1.0 - progress) * (1.0 - progress);
      return this.direction == com.mouseclient.util.time.AnimationDirection.OUT ? this.amplitude * (1.0 - easedProgress) : this.amplitude * easedProgress;
   }

   public AnimationDirection direction() {
      return this.direction;
   }

   public static class AnimationTimer {
      private long startNanos = System.nanoTime();

      public long elapsedMillis() {
         return (System.nanoTime() - this.startNanos) / (1000000L);
      }

      public void setStartNanos(long startNanos) {
         this.startNanos = startNanos;
      }

      public void reset() {
         this.startNanos = System.nanoTime();
      }
   }
}
