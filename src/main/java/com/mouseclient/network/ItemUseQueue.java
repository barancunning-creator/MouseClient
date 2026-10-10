package com.mouseclient.network;

import com.mouseclient.mixin.ClientInteractionManagerInvoker;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.util.entity.MinecraftAccess;
import java.util.ArrayList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

public class ItemUseQueue implements MinecraftAccess {
   private static ItemUseQueue INSTANCE;
   private final ArrayList<ItemUseQueue.ItemUseRequest> pendingUses = new ArrayList<>();
   private int displaySlot = -1;
   private ItemStack originalDisplayStack = ItemStack.EMPTY;
   private int displayHoldTicks;
   private final EventListener<PlayerTickEvent> itemUseListener = event -> {
      if (!this.pendingUses.isEmpty() && minecraftClient.player != null && minecraftClient.interactionManager != null) {
         ItemUseQueue.ItemUseRequest request = this.pendingUses.get(0);
         int hotbarSlot = this.findHotbarItem(request.requestedStack);
         int inventorySlot = this.findInventoryItem(request.requestedStack);
         if (request.phase != -1 || (hotbarSlot != -1 || inventorySlot != -1) && !minecraftClient.player.getItemCooldownManager().isCoolingDown(request.requestedStack)) {
            if (request.phase < 0 || !InventorySwapQueue.getInstance().hasPendingSwaps()) {
               request.phase++;
               if (request.phase == 0) {
                  request.selectedSlot = InventoryActions.selectedHotbarSlot();
                  if (hotbarSlot != -1) {
                     request.itemSlot = hotbarSlot;
                     if (hotbarSlot != request.selectedSlot) {
                        InventoryActions.selectHotbarSlot(hotbarSlot, false);
                     }
                  } else if (inventorySlot != -1) {
                     int bundleIndex = this.findBundleItem(minecraftClient.player.getInventory().getStack(inventorySlot), request.requestedStack, this.requiresMatchingComponents(request.requestedStack));
                     if (bundleIndex == -1) {
                        request.itemSlot = inventorySlot;
                        if (!request.temporarySlot) {
                           InventorySwapQueue.getInstance().queueSwap(inventorySlot, request.selectedSlot, 1);
                        } else {
                           request.temporaryHotbarSlot = this.temporaryHotbarSlot(request.selectedSlot, inventorySlot, -1);
                           this.captureDisplayStack(request.temporaryHotbarSlot);
                           InventorySwapQueue.getInstance().queueSwap(inventorySlot, request.temporaryHotbarSlot, 1);
                        }
                     } else {
                        request.emptyInventorySlot = InventoryActions.findEmptySlot(9, 35);
                        if (request.emptyInventorySlot == -1) {
                           request.emptyInventorySlot = InventoryActions.findEmptySlot(0, 8);
                        }

                        if (request.emptyInventorySlot == -1) {
                           this.pendingUses.remove(request);
                        } else {
                           request.bundleSourceSlot = inventorySlot;
                           if (!request.temporarySlot) {
                              if (inventorySlot == request.selectedSlot && InventoryActions.findEmptySlot(0, 8) != -1) {
                                 request.previousSelectedSlot = request.selectedSlot;
                                 request.selectedSlot = InventoryActions.findEmptySlot(0, 8);
                                 InventoryActions.selectHotbarSlot(request.selectedSlot, false);
                              }

                              request.itemSlot = request.selectedSlot;
                              request.displacedStack = minecraftClient.player.getInventory().getStack(request.selectedSlot).copy();
                              InventorySwapQueue.getInstance().queueBundleSwap(inventorySlot, request.selectedSlot, request.emptyInventorySlot, bundleIndex, 1);
                           } else {
                              request.temporaryHotbarSlot = this.temporaryHotbarSlot(request.selectedSlot, inventorySlot, request.emptyInventorySlot);
                              this.captureDisplayStack(request.temporaryHotbarSlot);
                              request.itemSlot = request.temporaryHotbarSlot;
                              request.displacedStack = minecraftClient.player.getInventory().getStack(request.temporaryHotbarSlot).copy();
                              InventorySwapQueue.getInstance().queueBundleSwap(inventorySlot, request.temporaryHotbarSlot, request.emptyInventorySlot, bundleIndex, 1);
                           }
                        }
                     }
                  }
               } else if (request.phase == 1) {
                  if (request.temporaryHotbarSlot != -1) {
                     if (this.matchesRequestedStack(minecraftClient.player.getInventory().getStack(request.temporaryHotbarSlot), request.requestedStack)
                        && !minecraftClient.player.getItemCooldownManager().isCoolingDown(request.requestedStack)) {
                        InventoryActions.selectHotbarSlot(request.temporaryHotbarSlot, true);
                        ((ClientInteractionManagerInvoker)(Object)minecraftClient.interactionManager)
                           .invokeStartPrediction(
                              minecraftClient.world,
                              sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, minecraftClient.player.getYaw(), minecraftClient.player.getPitch())
                           );
                        InventoryActions.selectHotbarSlot(request.selectedSlot, true);
                     }

                     if (request.bundleSourceSlot != -1) {
                        if (!ItemStack.areItemsAndComponentsEqual(minecraftClient.player.getInventory().getStack(request.temporaryHotbarSlot), request.displacedStack)) {
                           InventorySwapQueue.getInstance().queueTemporarySwap(request.bundleSourceSlot, request.temporaryHotbarSlot, request.emptyInventorySlot, 1);
                        }
                     } else {
                        InventorySwapQueue.getInstance().queueSwap(request.temporaryHotbarSlot, request.itemSlot, 1);
                     }

                     this.pendingUses.remove(request);
                  } else {
                     if (this.matchesRequestedStack(minecraftClient.player.getMainHandStack(), request.requestedStack)
                        && !minecraftClient.player.getItemCooldownManager().isCoolingDown(request.requestedStack)) {
                        ((ClientInteractionManagerInvoker)(Object)minecraftClient.interactionManager)
                           .invokeStartPrediction(
                              minecraftClient.world,
                              sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, minecraftClient.player.getYaw(), minecraftClient.player.getPitch())
                           );
                     }

                     if (request.bundleSourceSlot != -1) {
                        if (!ItemStack.areItemsAndComponentsEqual(minecraftClient.player.getInventory().getStack(request.selectedSlot), request.displacedStack)) {
                           InventorySwapQueue.getInstance().queueTemporarySwap(request.bundleSourceSlot, request.selectedSlot, request.emptyInventorySlot, 1);
                        }

                        if (request.previousSelectedSlot != -1) {
                           InventoryActions.selectHotbarSlot(request.previousSelectedSlot, false);
                        }
                     } else if (request.itemSlot > 8) {
                        InventorySwapQueue.getInstance().queueSwap(request.selectedSlot, request.itemSlot, 1);
                     } else if (request.selectedSlot != InventoryActions.selectedHotbarSlot()) {
                        InventoryActions.selectHotbarSlot(request.selectedSlot, false);
                     }

                     this.pendingUses.remove(request);
                  }
               }
            }
         } else {
            this.pendingUses.remove(request);
         }
      }
   };
   private final EventListener<PlayerTickEvent> displayRestoreListener = event -> {
      if (this.displaySlot != -1) {
         if (this.pendingUses.isEmpty() && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
            if (--this.displayHoldTicks <= 0) {
               this.displaySlot = -1;
            }
         } else {
            this.displayHoldTicks = 4;
         }
      }
   };

   public void enqueue(ItemStack stack) {
      this.pendingUses.add(new ItemUseQueue.ItemUseRequest(stack, false));
   }

   public static ItemUseQueue getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ItemUseQueue();
         EventBus.getInstance().subscribe(INSTANCE);
      }

      return INSTANCE;
   }

   private int temporaryHotbarSlot(int selectedSlot, int sourceSlot, int emptySlot) {
      if (minecraftClient.player == null) {
         return -1;
      }

      for (int emptyHotbarSlot = 8; emptyHotbarSlot >= 0; emptyHotbarSlot--) {
         if (emptyHotbarSlot != selectedSlot && emptyHotbarSlot != sourceSlot && emptyHotbarSlot != emptySlot && minecraftClient.player.getInventory().getStack(emptyHotbarSlot).isEmpty()) {
            return emptyHotbarSlot;
         }
      }

      for (int fallbackHotbarSlot = 8; fallbackHotbarSlot >= 0; fallbackHotbarSlot--) {
         if (fallbackHotbarSlot != selectedSlot && fallbackHotbarSlot != sourceSlot && fallbackHotbarSlot != emptySlot) {
            return fallbackHotbarSlot;
         }
      }

      return -1;
   }

   private int findHotbarItem(ItemStack stack) {
      return InventoryActions.findSlot(candidate -> this.matchesRequestedStack(candidate, stack), 0, 8);
   }

   public boolean hasPendingUses() {
      return !this.pendingUses.isEmpty();
   }

   public ItemStack displayStack(int slot, ItemStack actualStack) {
      return this.displaySlot == slot ? this.originalDisplayStack : actualStack;
   }

   private ItemUseQueue() {
   }

   private boolean matchesRequestedStack(ItemStack candidate, ItemStack requestedStack) {
      return this.requiresMatchingComponents(requestedStack) ? ItemStack.areItemsAndComponentsEqual(candidate, requestedStack) : candidate.getItem() == requestedStack.getItem();
   }

   public boolean hasUsableItem(ItemStack stack) {
      return this.findHotbarItem(stack) != -1 || this.findInventoryItem(stack) != -1;
   }

   private boolean requiresMatchingComponents(ItemStack stack) {
      return stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION;
   }

   private boolean tryUseFromHotbar(ItemStack stack) {
      if (minecraftClient.player != null
         && minecraftClient.interactionManager != null
         && minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
         int hotbarSlot = this.findHotbarItem(stack);
         if (hotbarSlot != -1 && !minecraftClient.player.getItemCooldownManager().isCoolingDown(stack)) {
            int previousSlot = InventoryActions.selectedHotbarSlot();
            if (hotbarSlot != previousSlot) {
               InventoryActions.selectHotbarSlot(hotbarSlot, true);
            }

            ((ClientInteractionManagerInvoker)(Object)minecraftClient.interactionManager)
               .invokeStartPrediction(
                  minecraftClient.world,
                  sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, minecraftClient.player.getYaw(), minecraftClient.player.getPitch())
               );
            if (hotbarSlot != previousSlot) {
               InventoryActions.selectHotbarSlot(previousSlot, true);
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private int findInventoryItem(ItemStack stack) {
      int exactInventorySlot = InventoryActions.findSlot(exactCandidate -> ItemStack.areItemsAndComponentsEqual(exactCandidate, stack), 9, 35);
      return exactInventorySlot != -1
         ? exactInventorySlot
         : InventoryActions.findSlot(matchingCandidate -> this.matchesRequestedStack(matchingCandidate, stack) || this.findBundleItem(matchingCandidate, stack, this.requiresMatchingComponents(stack)) != -1, 0, 35);
   }

   public void use(ItemStack stack) {
      this.use(stack, false);
   }

   public boolean isSpookyTime() {
      return ServerHelper.getInstance().server.isSelected(("SpookyTime"));
   }

   private void captureDisplayStack(int slot) {
      if (minecraftClient.player != null && slot != -1) {
         this.displaySlot = slot;
         this.originalDisplayStack = minecraftClient.player.getInventory().getStack(slot).copy();
         this.displayHoldTicks = 4;
      }
   }

   public void use(ItemStack stack, boolean temporarySlot) {
      if (!this.isSpookyTime() || !this.tryUseFromHotbar(stack)) {
         this.pendingUses.add(new ItemUseQueue.ItemUseRequest(stack, temporarySlot));
      }
   }

   private int findBundleItem(ItemStack bundle, ItemStack requestedStack, boolean matchingComponents) {
      if (bundle.get(DataComponentTypes.BUNDLE_CONTENTS) == null) {
         return -1;
      }

      for (int exactBundleIndex = 0; exactBundleIndex < bundle.get(DataComponentTypes.BUNDLE_CONTENTS).size(); exactBundleIndex++) {
         if (ItemStack.areItemsAndComponentsEqual(bundle.get(DataComponentTypes.BUNDLE_CONTENTS).get(exactBundleIndex), requestedStack)) {
            return exactBundleIndex;
         }
      }

      if (matchingComponents) {
         return -1;
      }

      for (int matchingBundleIndex = 0; matchingBundleIndex < bundle.get(DataComponentTypes.BUNDLE_CONTENTS).size(); matchingBundleIndex++) {
         if (ItemStack.areItemsEqual(bundle.get(DataComponentTypes.BUNDLE_CONTENTS).get(matchingBundleIndex), requestedStack)) {
            return matchingBundleIndex;
         }
      }

      return -1;
   }

   public static final class ItemUseRequest {
      private final ItemStack requestedStack;
      private final boolean temporarySlot;
      private ItemStack displacedStack = ItemStack.EMPTY;
      private int selectedSlot;
      private int itemSlot;
      private int emptyInventorySlot = -1;
      private int temporaryHotbarSlot = -1;
      private int bundleSourceSlot = -1;
      private int previousSelectedSlot = -1;
      private int phase = -1;

      private ItemUseRequest(ItemStack requestedStack, boolean temporarySlot) {
         this.requestedStack = requestedStack;
         this.temporarySlot = temporarySlot;
      }
   }
}
