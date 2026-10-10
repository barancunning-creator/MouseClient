package com.mouseclient.mixin;

import com.mouseclient.modules.impl.combat.NoServerPack;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket.Status;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public abstract class ServerResourcePackMixin {
   @Shadow
   @Final
   protected ClientConnection connection;

   @Inject(method = "onResourcePackSend(Lnet/minecraft/network/packet/s2c/common/ResourcePackSendS2CPacket;)V", at = @At("HEAD"), cancellable = true)
   private void skipServerPackDownload(ResourcePackSendS2CPacket packet, CallbackInfo callback) {
      if (NoServerPack.getInstance().isEnabled()) {
         this.connection.send(new ResourcePackStatusC2SPacket(packet.id(), Status.ACCEPTED));
         this.connection.send(new ResourcePackStatusC2SPacket(packet.id(), Status.SUCCESSFULLY_LOADED));
         callback.cancel();
      }
   }
}
