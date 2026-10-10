package com.mouseclient.gui;

import com.mouseclient.modules.impl.movement.CancelableEvent;
import lombok.Generated;
import net.minecraft.client.gui.screen.Screen;

public class CloseScreenEvent extends CancelableEvent {
   private final Screen screen;

   @Generated
   public Screen getScreen() {
      return this.screen;
   }

   @Generated
   public CloseScreenEvent(Screen screen) {
      this.screen = screen;
   }
}
