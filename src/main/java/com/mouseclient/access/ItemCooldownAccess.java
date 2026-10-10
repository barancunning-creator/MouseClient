package com.mouseclient.access;

import net.minecraft.item.ItemStack;

public interface ItemCooldownAccess {
   public int remainingCooldownTicks(ItemStack stack);

   public void wipeAllCooldowns();
}
