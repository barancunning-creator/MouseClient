package com.mouseclient.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftCooldownAccessor {
   public @Accessor("itemUseCooldown")
   void setRightClickDelay(int rightClickDelay);

   public @Accessor("attackCooldown")
   void setMissTime(int missTime);
}
