package com.mouseclient.mixin;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityAccessor {
   public @Accessor("fluidHeight")
   Object2DoubleMap<TagKey<Fluid>> getFluidHeight();

   public @Accessor("submergedFluidTag")
   Set<TagKey<Fluid>> getFluidOnEyes();

   public @Invoker("unsetRemoved")
   void invokeUnsetRemoved();
}
