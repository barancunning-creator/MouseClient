package com.mouseclient.events.impl;

import com.mouseclient.network.Event;
import lombok.Generated;
import net.minecraft.util.math.BlockPos;

public class PotionSplashEvent extends Event {
   private final int potionColor;
   private final BlockPos blockPos;

   @Generated
   public PotionSplashEvent(int potionColor, BlockPos blockPos) {
      this.potionColor = potionColor;
      this.blockPos = blockPos;
   }

   @Generated
   public int getPotionColor() {
      return this.potionColor;
   }

   @Generated
   public BlockPos getPosition() {
      return this.blockPos;
   }
}
