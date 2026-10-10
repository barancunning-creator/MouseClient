package com.mouseclient.gui;

import com.mouseclient.network.Event;
import lombok.Generated;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public class RenderSlotsEvent extends Event {
   private final HandledScreen<?> handledScreen;
   private final DrawContext drawContext;

   @Generated
   public HandledScreen<?> getScreen() {
      return this.handledScreen;
   }

   @Generated
   public RenderSlotsEvent(HandledScreen<?> handledScreen, DrawContext drawContext) {
      this.handledScreen = handledScreen;
      this.drawContext = drawContext;
   }

   @Generated
   public DrawContext getDrawContext() {
      return this.drawContext;
   }
}
