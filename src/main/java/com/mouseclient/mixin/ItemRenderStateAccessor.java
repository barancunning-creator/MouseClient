package com.mouseclient.mixin;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderState.LayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderState.class)
public interface ItemRenderStateAccessor {
   public @Accessor("layerCount")
   int getActiveLayerCount();

   public @Accessor("layers")
   LayerRenderState[] getLayers();
}
