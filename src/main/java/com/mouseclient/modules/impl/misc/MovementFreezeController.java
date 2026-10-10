package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.EventPriority;
import com.mouseclient.modules.impl.combat.MovementInputEvent;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.entity.MinecraftAccess;

public class MovementFreezeController implements MinecraftAccess {
   private static MovementFreezeController INSTANCE;
   private int remainingTicks = -1;
   @EventPriority(Integer.MIN_VALUE)
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (this.remainingTicks > 0) {
         this.remainingTicks--;
      } else if (this.remainingTicks == 0) {
         this.remainingTicks = -1;
      }
   };
   @EventPriority(Integer.MIN_VALUE)
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (this.remainingTicks >= 0) {
         event.setForward(0.0F);
         event.setStrafe(0.0F);
         event.setJumping(false);
      }
   };

   public boolean isFrozen() {
      return this.remainingTicks != -1;
   }

   private MovementFreezeController() {
   }

   public void freezeForTicks(int remainingTicks) {
      this.remainingTicks = remainingTicks;
   }

   public static MovementFreezeController getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new MovementFreezeController();
         EventBus.getInstance().subscribe(INSTANCE);
      }

      return INSTANCE;
   }

   public int getRemainingTicks() {
      return this.remainingTicks;
   }
}
