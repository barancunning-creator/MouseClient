package com.mouseclient.mixin;

import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityS2CPacket.class)
public interface EntityUpdatePacketAccessor {
   public @Accessor("id")
   int entityId();
}
