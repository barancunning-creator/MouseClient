package com.mouseclient.gui;

import com.mouseclient.modules.impl.movement.CancelableEvent;
import lombok.Generated;
import net.minecraft.screen.slot.SlotActionType;

public class SlotClickEvent extends CancelableEvent {
   private int syncId;
   private int slotId;
   private int button;
   private SlotActionType actionType;

   @Generated
   public int getButton() {
      return this.button;
   }

   @Generated
   public SlotClickEvent(int syncId, int slotId, int button, SlotActionType actionType) {
      this.syncId = syncId;
      this.slotId = slotId;
      this.button = button;
      this.actionType = actionType;
   }

   @Generated
   public void setSyncId(int syncId) {
      this.syncId = syncId;
   }

   @Generated
   public int getSlotId() {
      return this.slotId;
   }

   @Generated
   public void setSlotId(int slotId) {
      this.slotId = slotId;
   }

   @Generated
   public int getSyncId() {
      return this.syncId;
   }

   @Generated
   public void setActionType(SlotActionType actionType) {
      this.actionType = actionType;
   }

   @Generated
   public void setButton(int button) {
      this.button = button;
   }

   @Generated
   public SlotActionType getActionType() {
      return this.actionType;
   }
}
