package com.mouseclient.render.pipeline;

import com.mouseclient.mode.ItemRenderOptions;
import net.minecraft.item.ItemStack;

public record ItemRenderSpec(ItemStack stack, float x, float y, float size, ItemRenderOptions options, int seed) {
   public ItemRenderSpec(ItemStack stack, float x, float y, float size) {
      this(stack, x, y, size, ItemRenderOptions.defaults(), 0);
   }

   public ItemRenderSpec(ItemStack stack, float x, float y, float size, ItemRenderOptions options, int seed) {
      stack = stack == null ? ItemStack.EMPTY : stack;
      options = options == null ? ItemRenderOptions.defaults() : options;
      this.stack = stack;
      this.x = x;
      this.y = y;
      this.size = size;
      this.options = options;
      this.seed = seed;
   }

   public boolean visible() {
      return !this.stack.isEmpty() && this.size > 0.0F && this.options.alpha() > 0.0F && this.options.color() >>> 24 != 0;
   }
}
