package com.mouseclient.network;

import java.util.HashMap;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public final class CosmeticMovementTracker {

   private static final HashMap<Integer, CosmeticMovementTracker> trackersByEntity = new HashMap<>();
   private double previousX;
   private double previousZ;
   private int lastTick;
   private int stationaryTicks;
   private boolean moving;

   private boolean update(AbstractClientPlayerEntity player) {
      int tick = player.age;
      if (tick == this.lastTick) {
         return this.moving;
      }

      if (tick >= this.lastTick && tick - this.lastTick <= 20) {
         double deltaX = player.getX() - this.previousX;
         double deltaZ = player.getZ() - this.previousZ;
         this.previousX = player.getX();
         this.previousZ = player.getZ();
         this.lastTick = tick;
         if (deltaX * deltaX + deltaZ * deltaZ >= (4.0E-4D)) {
            this.stationaryTicks = 0;
            this.moving = true;
         } else if (++this.stationaryTicks >= 2) {
            this.moving = false;
         }

         return this.moving;
      } else {
         this.previousX = player.getX();
         this.previousZ = player.getZ();
         this.lastTick = tick;
         this.stationaryTicks = 0;
         this.moving = false;
         return false;
      }
   }

   public static boolean isMoving(AbstractClientPlayerEntity player) {
      if (trackersByEntity.size() > 128) {
         trackersByEntity.clear();
      }

      return trackersByEntity.computeIfAbsent(player.getId(), entityId -> new CosmeticMovementTracker()).update(player);
   }

   private CosmeticMovementTracker() {
      this.lastTick = (-2147483648);
   }
}
