package com.mouseclient.network;

import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.MovementFreezeController;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.util.entity.MinecraftAccess;
import java.util.ArrayList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.BundleItemSelectedC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

public class InventorySwapQueue implements MinecraftAccess {
   private static InventorySwapQueue INSTANCE;
   private final ArrayList<InventorySwapQueue.SwapRequest> requests = new ArrayList<>();
   private boolean hasClicked;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (!this.requests.isEmpty() && minecraftClient.player != null && minecraftClient.interactionManager != null) {
         InventorySwapQueue.SwapRequest request = this.requests.get(0);
         if (MovementFreezeController.getInstance().getRemainingTicks() < request.freezeTicks) {
            this.hasClicked = false;
            int sourceSlot = !request.expectedStack.isEmpty()
                  && !ItemStack.areItemsAndComponentsEqual(minecraftClient.player.getInventory().getStack(request.sourceSlot), request.expectedStack)
               ? InventoryActions.findSlot(stack -> ItemStack.areItemsAndComponentsEqual(stack, request.expectedStack), 0, 35)
               : request.sourceSlot;
            if (sourceSlot != -1 && (sourceSlot != request.targetSlot || request.targetIsScreenSlot)) {
               int sourceScreenSlot = this.playerScreenSlot(sourceSlot);
               int targetScreenSlot = request.targetIsScreenSlot ? request.targetSlot : this.playerScreenSlot(request.targetSlot);
               if (request.temporaryInventorySlot != -1) {
                  this.swapThroughTemporarySlot(request, sourceScreenSlot, targetScreenSlot);
               } else if (minecraftClient.player.playerScreenHandler.getSlot(sourceScreenSlot).getStack().get(DataComponentTypes.BUNDLE_CONTENTS) != null) {
                  this.clickSlot(sourceScreenSlot, 1, SlotActionType.PICKUP);
                  this.clickSlot(targetScreenSlot, 0, SlotActionType.PICKUP);
                  if (!minecraftClient.player.playerScreenHandler.getCursorStack().isEmpty()) {
                     this.clickSlot(sourceScreenSlot, 0, SlotActionType.PICKUP);
                  }
               } else {
                  int targetSwapButton = this.hotbarSwapButton(request.targetSlot, targetScreenSlot);
                  if (targetSwapButton != -1) {
                     this.clickSlot(sourceScreenSlot, targetSwapButton, SlotActionType.SWAP);
                  } else {
                     int sourceSwapButton = this.hotbarSwapButton(sourceSlot, sourceScreenSlot);
                     if (sourceSwapButton != -1) {
                        this.clickSlot(targetScreenSlot, sourceSwapButton, SlotActionType.SWAP);
                     } else if (sourceScreenSlot != targetScreenSlot) {
                        this.clickSlot(sourceScreenSlot, 0, SlotActionType.SWAP);
                        this.clickSlot(targetScreenSlot, 0, SlotActionType.SWAP);
                        this.clickSlot(sourceScreenSlot, 0, SlotActionType.SWAP);
                     }
                  }
               }

               minecraftClient.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(minecraftClient.player.currentScreenHandler.syncId));
               this.finishRequest(request);
            } else {
               this.finishRequest(request);
            }
         }
      }
   };

   private void finishRequest(InventorySwapQueue.SwapRequest request) {
      this.requests.remove(request);
      if (!this.requests.isEmpty()) {
         MovementFreezeController.getInstance().freezeForTicks(this.requests.get(0).freezeTicks);
      }
   }

   public static InventorySwapQueue getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new InventorySwapQueue();
         EventBus.getInstance().subscribe(INSTANCE);
      }

      return INSTANCE;
   }

   private void enqueue(InventorySwapQueue.SwapRequest request) {
      if (request.sourceSlot != -1 && request.targetSlot != -1) {
         if (this.requests.isEmpty() && request.freezeTicks > 0) {
            MovementFreezeController.getInstance().freezeForTicks(request.freezeTicks);
         }

         this.requests.add(request);
      }
   }

   private InventorySwapQueue() {
   }

   private void swapThroughTemporarySlot(InventorySwapQueue.SwapRequest request, int sourceScreenSlot, int targetScreenSlot) {
      int temporaryScreenSlot = this.playerScreenSlot(request.temporaryInventorySlot);
      int swapButton = this.hotbarSwapButton(request.targetSlot, targetScreenSlot);
      if (request.bundleItemIndex == -1) {
         this.clickSlot(temporaryScreenSlot, swapButton, SlotActionType.SWAP);
      } else if (minecraftClient.player.getInventory().getStack(request.temporaryInventorySlot).isEmpty()
         && minecraftClient.player.playerScreenHandler.getCursorStack().isEmpty()) {
         if (BundleItem.getSelectedStackIndex(minecraftClient.player.playerScreenHandler.getSlot(sourceScreenSlot).getStack()) != request.bundleItemIndex) {
            minecraftClient.player.playerScreenHandler.selectBundleStack(sourceScreenSlot, request.bundleItemIndex);
            minecraftClient.player.networkHandler.sendPacket(new BundleItemSelectedC2SPacket(sourceScreenSlot, request.bundleItemIndex));
         }

         this.clickSlot(sourceScreenSlot, 1, SlotActionType.PICKUP);
         this.clickSlot(temporaryScreenSlot, 0, SlotActionType.PICKUP);
         this.clickSlot(temporaryScreenSlot, swapButton, SlotActionType.SWAP);
      }
   }

   public void queueBundleSwap(int sourceSlot, int targetSlot, int temporarySlot, int bundleItemIndex, int freezeTicks) {
      if (temporarySlot != -1 && bundleItemIndex != -1) {
         this.enqueue(new InventorySwapQueue.SwapRequest(sourceSlot, targetSlot, temporarySlot, bundleItemIndex, freezeTicks));
      }
   }

   public void queueTrackedSwap(int sourceSlot, int targetSlot, int freezeTicks) {
      this.enqueue(
         new InventorySwapQueue.SwapRequest(
            sourceSlot,
            targetSlot,
            freezeTicks,
            false,
            minecraftClient.player != null && sourceSlot >= 0 && sourceSlot <= 35 ? minecraftClient.player.getInventory().getStack(sourceSlot).copy() : ItemStack.EMPTY
         )
      );
   }

   private int hotbarSwapButton(int inventorySlot, int screenSlot) {
      if (inventorySlot == 40 || inventorySlot == 45 || screenSlot == 45) {
         return 40;
      } else {
         return screenSlot >= 36 && screenSlot <= 44 ? screenSlot - 36 : -1;
      }
   }

   public void queueSwap(int sourceSlot, int targetSlot, int freezeTicks) {
      this.enqueue(new InventorySwapQueue.SwapRequest(sourceSlot, targetSlot, freezeTicks, false, ItemStack.EMPTY));
   }

   private int playerScreenSlot(int inventorySlot) {
      return inventorySlot >= 0 && inventorySlot <= 8 ? inventorySlot + 36 : inventorySlot;
   }

   public void queueTemporarySwap(int sourceSlot, int targetSlot, int temporarySlot, int freezeTicks) {
      if (temporarySlot != -1) {
         this.enqueue(new InventorySwapQueue.SwapRequest(sourceSlot, targetSlot, temporarySlot, -1, freezeTicks));
      }
   }

   public boolean hasPendingSwaps() {
      return !this.requests.isEmpty();
   }

   private void clickSlot(int screenSlot, int button, SlotActionType action) {
      if (screenSlot != -1 && button != -1) {
         if (this.hasClicked) {
            minecraftClient.player.playerScreenHandler.nextRevision();
         }

         this.hasClicked = true;
         minecraftClient.interactionManager.clickSlot(minecraftClient.player.playerScreenHandler.syncId, screenSlot, button, action, minecraftClient.player);
      }
   }

   public void queueScreenSlotSwap(int sourceSlot, int targetScreenSlot, int freezeTicks) {
      if (minecraftClient.player != null && minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
         this.enqueue(new InventorySwapQueue.SwapRequest(sourceSlot, targetScreenSlot, freezeTicks, true, ItemStack.EMPTY));
      }
   }

   public static final class SwapRequest {
      private final int sourceSlot;
      private final int targetSlot;
      private final int freezeTicks;
      private final boolean targetIsScreenSlot;
      private final ItemStack expectedStack;
      private final int temporaryInventorySlot;
      private final int bundleItemIndex;

      private SwapRequest(int sourceSlot, int targetSlot, int freezeTicks, boolean targetIsScreenSlot, ItemStack expectedStack) {
         this.sourceSlot = sourceSlot;
         this.targetSlot = targetSlot;
         this.freezeTicks = freezeTicks;
         this.targetIsScreenSlot = targetIsScreenSlot;
         this.expectedStack = expectedStack;
         this.temporaryInventorySlot = -1;
         this.bundleItemIndex = -1;
      }

      private SwapRequest(int sourceSlot, int targetSlot, int temporarySlot, int bundleItemIndex, int freezeTicks) {
         this.sourceSlot = sourceSlot;
         this.targetSlot = targetSlot;
         this.freezeTicks = freezeTicks;
         this.targetIsScreenSlot = false;
         this.expectedStack = ItemStack.EMPTY;
         this.temporaryInventorySlot = temporarySlot;
         this.bundleItemIndex = bundleItemIndex;
      }
   }
}
