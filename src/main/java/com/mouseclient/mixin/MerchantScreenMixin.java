package com.mouseclient.mixin;

import com.mouseclient.access.MerchantSelectionAccess;

import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MerchantScreen.class)
public class MerchantScreenMixin implements MerchantSelectionAccess {
   @Shadow
   private int selectedIndex;

   @Shadow
   private void syncRecipeIndex() {
      throw new AssertionError();
   }

   @Override
   public int mouseclient$getSelectedIndex() {
      return this.selectedIndex;
   }

   @Override
   public void mouseclient$selectTrade(int selectedIndex) {
      this.selectedIndex = selectedIndex;
      this.syncRecipeIndex();
   }
}
