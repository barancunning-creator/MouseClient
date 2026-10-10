package com.mouseclient.mode;

import com.mouseclient.modules.impl.misc.YoutubeModeModule;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.item.ItemStack;

public enum GlintMode {
   AUTO,
   OFF,
   FORCE;

   public boolean shouldRender(ItemStack stack, Glint originalGlint) {
      return this.shouldRender(stack, originalGlint != Glint.NONE);
   }

   public boolean shouldRender(ItemStack stack, boolean originalGlint) {
      if (YoutubeModeModule.getInstance().isEnabled()) {
         return false;
      }

      return switch (this) {
         case AUTO -> stack != null && (stack.hasGlint() || originalGlint);
         case OFF -> false;
         case FORCE -> true;
      };
   }
}
