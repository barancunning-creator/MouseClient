package com.mouseclient.internal;

import com.mouseclient.modules.impl.movement.CancelableEvent;
import lombok.Generated;
import net.minecraft.network.packet.Packet;

public class PacketEvent extends CancelableEvent {
   private final PacketEvent.PacketDirection direction;
   private Packet<?> packet;

   @Generated
   public Packet<?> getPacket() {
      return this.packet;
   }

   @Generated
   public void setPacket(Packet<?> packet) {
      this.packet = packet;
   }

   @Generated
   public PacketEvent.PacketDirection getDirection() {
      return this.direction;
   }

   public boolean isOutgoing() {
      return this.getDirection() == PacketEvent.PacketDirection.SENT;
   }

   public boolean isIncoming() {
      return this.getDirection() == PacketEvent.PacketDirection.RECEIVE;
   }

   @Generated
   public PacketEvent(PacketEvent.PacketDirection direction, Packet<?> packet) {
      this.direction = direction;
      this.packet = packet;
   }

   public enum PacketDirection {
      SENT,
      RECEIVE;
   }
}
