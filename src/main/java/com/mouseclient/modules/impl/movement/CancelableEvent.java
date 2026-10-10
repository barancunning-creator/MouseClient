package com.mouseclient.modules.impl.movement;

import com.mouseclient.events.impl.StagedEvent;
import lombok.Generated;

public class CancelableEvent extends StagedEvent {
   private boolean cancelled;

   @Generated
   public boolean isCancelled() {
      return this.cancelled;
   }

   public void cancel() {
      this.cancelled = true;
   }

   public void markCancelled() {
      this.cancelled = true;
   }

   public void setCancelled(boolean cancelled) {
      this.cancelled = cancelled;
   }
}
