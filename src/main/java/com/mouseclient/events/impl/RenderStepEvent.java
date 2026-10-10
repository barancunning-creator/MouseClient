package com.mouseclient.events.impl;

import com.mouseclient.network.Event;
import lombok.Generated;

public class RenderStepEvent extends Event {
   private final int stepIndex;
   private final int stepCount;
   private final float tickDelta;

   @Generated
   public RenderStepEvent(int stepIndex, int stepCount, float tickDelta) {
      this.stepIndex = stepIndex;
      this.stepCount = stepCount;
      this.tickDelta = tickDelta;
   }

   @Generated
   public int getStepIndex() {
      return this.stepCount;
   }

   @Generated
   public int getStepCount() {
      return this.stepIndex;
   }

   @Generated
   public float getTickDelta() {
      return this.tickDelta;
   }
}
