package com.mouseclient.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftActionInvoker {
   public @Invoker("doAttack")
   boolean invokeStartAttack();

   public @Invoker("doItemUse")
   void invokeStartUseItem();

   public @Invoker("handleBlockBreaking")
   void invokeContinueAttack(boolean breaking);
}
