package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.InventorySwapQueue;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;

@ModuleInfo(name = "AutoTool", description = "Сам берёт лучший инструмент из хотбара или инвентаря при копании блока.", category = ModuleCategory.MISC)
public final class AutoTool extends Module {
   private static AutoTool INSTANCE;
   private int originalHotbarSlot = -1;
   private int swappedInventorySlot = -1;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.interactionManager != null && !minecraftClient.player.isCreative()
         )
       {
         if (!InventorySwapQueue.getInstance().hasPendingSwaps()) {
            if (minecraftClient.options.attackKey.isPressed()
               && minecraftClient.crosshairTarget != null
               && minecraftClient.crosshairTarget.getType() == Type.BLOCK
               && minecraftClient.crosshairTarget instanceof BlockHitResult blockHit) {
               BlockState currentBlockState = minecraftClient.world.getBlockState(blockHit.getBlockPos());
               if (!currentBlockState.isAir()) {
                  int bestSlot = -1;
                  float bestMiningSpeed = minecraftClient.player.getInventory().getStack(InventoryActions.selectedHotbarSlot()).getMiningSpeedMultiplier(currentBlockState);

                  for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
                     float miningSpeed = minecraftClient.player.getInventory().getStack(inventorySlot).getMiningSpeedMultiplier(currentBlockState);
                     if (miningSpeed > bestMiningSpeed + (0.01F)) {
                        bestMiningSpeed = miningSpeed;
                        bestSlot = inventorySlot;
                     }
                  }

                  if (bestSlot != -1 && bestSlot != InventoryActions.selectedHotbarSlot()) {
                     if (this.originalHotbarSlot == -1) {
                        this.originalHotbarSlot = InventoryActions.selectedHotbarSlot();
                     }

                     if (bestSlot < 9) {
                        InventoryActions.selectHotbarSlot(bestSlot, false);
                     } else if (this.swappedInventorySlot == -1) {
                        this.swappedInventorySlot = bestSlot;
                        InventorySwapQueue.getInstance().queueSwap(bestSlot, this.originalHotbarSlot, 1);
                     }
                  }
               }
            } else if (this.originalHotbarSlot != -1 && !minecraftClient.options.attackKey.isPressed()) {
               if (this.swappedInventorySlot == -1) {
                  InventoryActions.selectHotbarSlot(this.originalHotbarSlot, false);
               } else {
                  InventorySwapQueue.getInstance().queueSwap(this.swappedInventorySlot, this.originalHotbarSlot, 1);
               }

               this.originalHotbarSlot = -1;
               this.swappedInventorySlot = -1;
            }
         }
      }
   };

   public static AutoTool getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoTool();
      }

      return INSTANCE;
   }

   @Override
   protected void onEnable() {
      this.originalHotbarSlot = -1;
      this.swappedInventorySlot = -1;
   }
}
