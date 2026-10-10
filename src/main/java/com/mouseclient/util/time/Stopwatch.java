package com.mouseclient.util.time;

import lombok.Generated;

public class Stopwatch {
   private long startedAt;

   public String formatElapsed() {
      long elapsedMillis = this.getElapsedMillis();
      long hours = elapsedMillis / (3600000L);
      long minutes = elapsedMillis % (3600000L) / (60000L);
      long seconds = elapsedMillis % (60000L) / (1000L);
      StringBuilder textBuilder = new StringBuilder();
      if (hours > 0L) {
         textBuilder.append(hours).append(("ч, "));
      }

      if (minutes > 0L || hours > 0L) {
         textBuilder.append(minutes).append(("м, "));
      }

      textBuilder.append(seconds).append(("с"));
      return textBuilder.toString();
   }

   public void reset() {
      this.startedAt = System.currentTimeMillis();
   }

   public boolean hasElapsed(double durationMillis) {
      return System.currentTimeMillis() - durationMillis >= this.startedAt;
   }

   public boolean hasElapsed(long durationMillis) {
      return System.currentTimeMillis() - this.startedAt > durationMillis;
   }

   @Generated
   public long getStartTime() {
      return this.startedAt;
   }

   public void delayStart(long delayMillis) {
      this.startedAt = System.currentTimeMillis() + delayMillis;
   }

   public Stopwatch() {
      this.reset();
   }

   public long getElapsedMillis() {
      return System.currentTimeMillis() - this.startedAt;
   }
}
