package com.mouseclient.modules.impl.render;

import com.mouseclient.network.Event;
import lombok.Generated;
import net.minecraft.client.gui.DrawContext;

public class HudRenderEvent extends Event {
   private final DrawContext drawContext;
   private final float tickProgress;
   private final int width;
   private final int height;

   @Generated
   public DrawContext getDrawContext() {
      return this.drawContext;
   }

   @Generated
   public HudRenderEvent(DrawContext drawContext, float tickProgress, int width, int height) {
      this.drawContext = drawContext;
      this.tickProgress = tickProgress;
      this.width = width;
      this.height = height;
   }

   @Generated
   public int getHeight() {
      return this.height;
   }

   @Generated
   public float getTickProgress() {
      return this.tickProgress;
   }

   @Generated
   public int getWidth() {
      return this.width;
   }
}
