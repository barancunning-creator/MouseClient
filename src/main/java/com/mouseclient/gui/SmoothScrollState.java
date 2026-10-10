package com.mouseclient.gui;

import com.mouseclient.internal.GuiScale;

public final class SmoothScrollState {
   public float target;
   public float current;
   public float maxScroll;

   public float getScroll() {
      float pixelScale = GuiScale.getBaseScale() / GuiScale.getWindowScale();
      return pixelScale > (1.0E-4F) ? Math.round(this.current * pixelScale) / pixelScale : this.current;
   }

   public void update(float deltaSeconds) {
      float frameDeltaSeconds = deltaSeconds;
      float requestedScroll = this.target;
      float scrollLimit = this.maxScroll;
      this.target = Math.max(0.0F, Math.min(requestedScroll, scrollLimit));
      this.current = this.current + (this.target - this.current) * (1.0F - (float)Math.exp(-frameDeltaSeconds * (22.0F)));
      if (Math.abs(this.target - this.current) < (0.1F)) {
         this.current = this.target;
      }
   }
}
