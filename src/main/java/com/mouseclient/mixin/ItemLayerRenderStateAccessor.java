package com.mouseclient.mixin;

import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.item.ItemRenderState.LayerRenderState;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.render.model.json.Transformation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LayerRenderState.class)
public interface ItemLayerRenderStateAccessor {
   public @Accessor("glint")
   Glint getFoilType();

   public @Accessor("transform")
   Transformation getItemTransform();

   public @Accessor("useLight")
   boolean getUsesBlockLight();

   public @Accessor("specialModelType")
   SpecialModelRenderer<Object> getSpecialRenderer();

   public @Accessor("tints")
   int[] getTintLayers();
}
