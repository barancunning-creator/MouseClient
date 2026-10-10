package com.mouseclient.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerSprintAccessor {
   public @Accessor("lastSprinting")
   boolean serverSprintState();

   public @Accessor("lastSprinting")
   @Mutable
   void setServerSprintState(boolean serverSprintState);
}
