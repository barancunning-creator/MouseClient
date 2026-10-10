package com.mouseclient.mixin;

import com.mouseclient.render.ItemCosmeticsRenderer;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
   @Inject(method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/World;Lnet/minecraft/util/HeldItemContext;I)V", at = @At("HEAD"), cancellable = true)
   private void appendWeaponSkin(ItemRenderState state, ItemStack stack, ItemDisplayContext displayContext, World world, HeldItemContext heldItemContext, int seed, CallbackInfo callback) {
      if (ItemCosmeticsRenderer.applyCosmetic(state, stack, displayContext, heldItemContext == null ? null : heldItemContext.getEntity())) {
         callback.cancel();
      }
   }
}
