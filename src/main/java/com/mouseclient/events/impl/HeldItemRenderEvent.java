package com.mouseclient.events.impl;

import com.mouseclient.network.Event;
import lombok.Generated;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

public final class HeldItemRenderEvent extends Event {
   private final MatrixStack matrixStack;
   private final ItemStack itemStack;
   private final Hand hand;
   private final Arm arm;
   private final float swingProgress;

   @Generated
   public float getSwingProgress() {
      return this.swingProgress;
   }

   @Generated
   public Arm getArm() {
      return this.arm;
   }

   @Generated
   public MatrixStack getMatrices() {
      return this.matrixStack;
   }

   @Generated
   public Hand getHand() {
      return this.hand;
   }

   @Generated
   public ItemStack getStack() {
      return this.itemStack;
   }

   @Generated
   public HeldItemRenderEvent(MatrixStack matrixStack, ItemStack itemStack, Hand hand, Arm arm, float swingProgress) {
      this.matrixStack = matrixStack;
      this.itemStack = itemStack;
      this.hand = hand;
      this.arm = arm;
      this.swingProgress = swingProgress;
   }
}
