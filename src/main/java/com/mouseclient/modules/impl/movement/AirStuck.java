package com.mouseclient.modules.impl.movement;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "AirStuck", description = "Зависает в воздухе.", category = ModuleCategory.MOVEMENT)
public final class AirStuck extends Module {
   private static AirStuck INSTANCE;
   private Vec3d anchorPosition = Vec3d.ZERO;
   private boolean frozen;
   private final EventListener<MoveVectorEvent> moveVectorListener = event -> {
      if (minecraftClient.player != null) {
         if (!this.frozen) {
            boolean isFalling = (minecraftClient.player.getVelocity().y < (-0.05D) || event.getMovement().y < (-0.05D)) && !minecraftClient.player.isOnGround();
            if (isFalling && minecraftClient.player.fallDistance > 0.0) {
               this.anchorPosition = minecraftClient.player.getEntityPos();
               this.frozen = true;
            }
         }

         if (this.frozen) {
            event.setMovement(Vec3d.ZERO);
            minecraftClient.player.setPosition(this.anchorPosition.x, this.anchorPosition.y, this.anchorPosition.z);
            minecraftClient.player.setVelocity(Vec3d.ZERO);
         }
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (this.frozen && event.isOutgoing() && event.getPacket() instanceof PlayerMoveC2SPacket) {
         event.markCancelled();
      }
   };

   public static AirStuck getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AirStuck();
      }

      return INSTANCE;
   }

   public boolean isFrozen() {
      return this.isEnabled() && this.frozen;
   }

   @Override
   protected void onDisable() {
      this.frozen = false;
   }

   @Override
   protected void onEnable() {
      this.frozen = false;
   }

   private AirStuck() {
   }
}
