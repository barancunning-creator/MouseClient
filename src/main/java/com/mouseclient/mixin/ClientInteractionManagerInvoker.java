package com.mouseclient.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerInteractionManager.class)
public interface ClientInteractionManagerInvoker {
   public @Invoker("sendSequencedPacket")
   void invokeStartPrediction(ClientWorld world, SequencedPacketCreator packetCreator);

   public @Invoker("syncSelectedSlot")
   void invokeEnsureHasSentCarriedItem();
}
