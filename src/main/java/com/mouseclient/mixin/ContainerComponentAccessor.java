package com.mouseclient.mixin;

import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ContainerComponent.class)
public interface ContainerComponentAccessor {
   public @Accessor("stacks")
   DefaultedList<ItemStack> getItems();
}
