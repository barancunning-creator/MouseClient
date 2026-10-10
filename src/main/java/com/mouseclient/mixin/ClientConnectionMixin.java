package com.mouseclient.mixin;

import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.network.ClientInitializer;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {
   @Unique
   private static boolean resendGuard;

   @Inject(method = "handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V", at = @At("HEAD"), cancellable = true)
   private static void receivePacket(Packet<?> packet, PacketListener listener, CallbackInfo callback) {
      if (EventBus.getInstance().hasListeners(PacketEvent.class)) {
         PacketEvent event = new PacketEvent(PacketEvent.PacketDirection.RECEIVE, packet);

         try {
            EventBus.getInstance().post(event);
         } catch (Throwable listenerFailure) {
            ClientInitializer.logger().error(("[Net] Слушатель входящего пакета {} упал"), packet.getClass().getSimpleName(), listenerFailure);
            return;
         }

         if (event.isCancelled()) {
            callback.cancel();
         }
      }
   }

   @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
   private void sendPacket(Packet<?> packet, CallbackInfo callback) {
      if (!resendGuard && EventBus.getInstance().hasListeners(PacketEvent.class)) {
         PacketEvent event = new PacketEvent(PacketEvent.PacketDirection.SENT, packet);

         try {
            EventBus.getInstance().post(event);
         } catch (Throwable listenerFailure) {
            ClientInitializer.logger().error(("[Net] Слушатель исходящего пакета {} упал"), packet.getClass().getSimpleName(), listenerFailure);
            return;
         }

         if (event.isCancelled()) {
            callback.cancel();
         } else {
            if (event.getPacket() != packet) {
               callback.cancel();
               resendGuard = true;
               ((ClientConnection)(Object)this).send(event.getPacket());
               resendGuard = false;
            }
         }
      }
   }

   static {
      VMBridge.identifyClass(ClientConnectionMixin.class, "YORiXNiE");
   }
}
