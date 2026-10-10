package com.mouseclient.events.impl;

import com.mouseclient.network.EventStages;
import lombok.Generated;

public abstract class StagedEvent implements EventStages {
   private StagedEvent.EventStage stage;

   @Generated
   protected boolean canEqual(Object other) {
      return other instanceof StagedEvent;
   }

   @Override
   public boolean isPost() {
      return this.stage == StagedEvent.EventStage.POST;
   }

   @Override
   public boolean isPre() {
      return this.stage == StagedEvent.EventStage.PRE;
   }

   @Generated
   @Override
   public String toString() {
      return "EventBase(stage=" + this.getStage() + ")";
   }

   @Generated
   public void setStage(StagedEvent.EventStage stage) {
      this.stage = stage;
   }

   @Generated
   public StagedEvent.EventStage getStage() {
      return this.stage;
   }

   @Generated
   @Override
   public int hashCode() {
      byte var1 = 59;
      byte var2 = 1;
      StagedEvent.EventStage stage = this.getStage();
      return var2 * 59 + (stage == null ? 43 : stage.hashCode());
   }

   @Generated
   @Override
   public boolean equals(Object other) {
      if (other == this) {
         return true;
      } else if (!(other instanceof StagedEvent event)) {
         return false;
      } else {
         if (!event.canEqual(this)) {
            return false;
         }

         StagedEvent.EventStage stage = this.getStage();
         StagedEvent.EventStage otherStage = event.getStage();
         return stage == null ? otherStage == null : stage.equals(otherStage);
      }
   }

   public enum EventStage {
      PRE,
      POST;
   }
}
