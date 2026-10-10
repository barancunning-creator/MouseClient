package com.mouseclient.network;

import com.mouseclient.util.entity.MinecraftAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

public class InventoryActions implements MinecraftAccess {
   public static void swapInventorySlotWithHotbar(int inventorySlot, int hotbarSlot) {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null && inventorySlot != -1 && hotbarSlot >= 0 && hotbarSlot <= 8) {
         minecraftClient.interactionManager
            .clickSlot(minecraftClient.player.currentScreenHandler.syncId, screenSlotForInventoryIndex(inventorySlot), hotbarSlot, SlotActionType.SWAP, minecraftClient.player);
      }
   }

   public static int screenSlotForInventoryIndex(int inventorySlot) {
      if (minecraftClient.player == null) {
         return inventorySlot;
      }

      for (Slot slot : minecraftClient.player.currentScreenHandler.slots) {
         if (slot.inventory == minecraftClient.player.getInventory() && slot.getIndex() == inventorySlot) {
            return slot.id;
         }
      }

      return -1;
   }

   public static boolean extractBundleItem(Item item) {
      if (minecraftClient.player != null && minecraftClient.currentScreen == null) {
         for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            BundleContentsComponent bundleContents = minecraftClient.player.getInventory().getStack(inventorySlot).get(DataComponentTypes.BUNDLE_CONTENTS);
            if (bundleContents != null && !bundleContents.isEmpty()) {
               int itemIndex = -1;
               int bundleIndex = 0;

               for (ItemStack stack : bundleContents.iterate()) {
                  if (stack.getItem() == item) {
                     itemIndex = bundleIndex;
                     break;
                  }

                  bundleIndex++;
               }

               if (itemIndex != -1) {
                  for (int extractedItems = 0; extractedItems <= itemIndex && findEmptySlot(0, 35) != -1; extractedItems++) {
                     clickSlot(screenSlotForInventoryIndex(inventorySlot), 1, SlotActionType.PICKUP, false);
                     clickSlot(screenSlotForInventoryIndex(findEmptySlot(0, 35)), 0, SlotActionType.PICKUP, false);
                  }

                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static int countItems(Item item) {
      if (minecraftClient.player == null) {
         return 0;
      }

      int count = 0;

      for (int inventorySlot = 0; inventorySlot < minecraftClient.player.getInventory().size(); inventorySlot++) {
         if (minecraftClient.player.getInventory().getStack(inventorySlot).isOf(item)) {
            count += minecraftClient.player.getInventory().getStack(inventorySlot).getCount();
         }
      }

      return count;
   }

   public static void swapWithHand(int inventorySlot, Hand hand) {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null && inventorySlot != -1) {
         minecraftClient.interactionManager
            .clickSlot(
               minecraftClient.player.currentScreenHandler.syncId,
               screenSlotForInventoryIndex(inventorySlot),
               hand == Hand.OFF_HAND ? 40 : selectedHotbarSlot(),
               SlotActionType.SWAP,
               minecraftClient.player
            );
      }
   }

   public static void swapByPickup(int sourceSlot, int targetSlot, boolean packetOnly) {
      if (sourceSlot != targetSlot && sourceSlot != -1 && targetSlot != -1) {
         clickSlot(screenSlotForInventoryIndex(sourceSlot), 0, SlotActionType.PICKUP, packetOnly);
         clickSlot(screenSlotForInventoryIndex(targetSlot), 0, SlotActionType.PICKUP, packetOnly);
         clickSlot(screenSlotForInventoryIndex(sourceSlot), 0, SlotActionType.PICKUP, packetOnly);
      }
   }

   public static void useInventoryItem(int inventorySlot) {
      useInventoryItem(inventorySlot, Hand.MAIN_HAND);
   }

   public static int findHotbarItem(Item item) {
      return findSlot(stack -> stack.getItem() == item, 0, 8);
   }

   public static void pickupAllPacket() {
      clickSlot(0, 0, SlotActionType.PICKUP_ALL, true);
   }

   public static ActionResult interactItem(Hand hand) {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null) {
         return minecraftClient.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR
            ? ActionResult.PASS
            : minecraftClient.interactionManager.interactItem(minecraftClient.player, hand);
      } else {
         return ActionResult.PASS;
      }
   }

   public static void useInventoryItem(int inventorySlot, Hand hand) {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null && inventorySlot != -1) {
         swapWithHand(inventorySlot, hand);
         interactItem(hand);
         swapWithHand(inventorySlot, hand);
      }
   }

   public static void clickSlot(Slot slot, int button, SlotActionType action, boolean packetOnly) {
      if (slot != null) {
         clickSlot(slot.id, button, action, packetOnly);
      }
   }

   public static void swapScreenSlotWithHotbar(int screenSlot, int hotbarSlot, boolean packetOnly) {
      if (screenSlot != -1 && hotbarSlot >= 0 && hotbarSlot <= 8) {
         clickSlot(screenSlot, hotbarSlot, SlotActionType.SWAP, packetOnly);
      }
   }

   public static int findEmptySlot(int minimumSlot, int maximumSlot) {
      return findSlot(stack -> stack.isEmpty(), minimumSlot, maximumSlot);
   }

   public static void clickSlot(int screenSlot, int button, SlotActionType action, boolean packetOnly) {
      if (minecraftClient.player != null) {
         clickSlot(minecraftClient.player.currentScreenHandler.syncId, screenSlot, button, action, packetOnly);
      }
   }

   public static void closeInventory(boolean packetOnly) {
      if (minecraftClient.player != null) {
         if (packetOnly) {
            if (minecraftClient.player.currentScreenHandler != minecraftClient.player.playerScreenHandler) {
               return;
            }

            minecraftClient.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(minecraftClient.player.currentScreenHandler.syncId));
         } else {
            minecraftClient.player.closeHandledScreen();
         }
      }
   }

   public static void clickSlot(int syncId, int screenSlot, int button, SlotActionType action, boolean packetOnly) {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null && screenSlot != -1) {
         if (packetOnly) {
            minecraftClient.player
               .networkHandler
               .sendPacket(
                  new ClickSlotC2SPacket(
                     syncId,
                     minecraftClient.player.currentScreenHandler.getRevision(),
                     (short)screenSlot,
                     (byte)button,
                     action,
                     Int2ObjectMaps.emptyMap(),
                     ItemStackHash.EMPTY
                  )
               );
         } else {
            minecraftClient.interactionManager.clickSlot(syncId, screenSlot, button, action, minecraftClient.player);
         }
      }
   }

   public static int findMainInventoryItem(Item item) {
      return findSlot(stack -> stack.getItem() == item, 9, 35);
   }

   public static void selectHotbarSlot(int hotbarSlot, boolean packetOnly) {
      if (minecraftClient.player != null && hotbarSlot >= 0 && hotbarSlot <= 8) {
         if (packetOnly) {
            minecraftClient.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
         } else {
            minecraftClient.player.getInventory().setSelectedSlot(hotbarSlot);
         }
      }
   }

   public static int selectedHotbarSlot() {
      return minecraftClient.player == null ? -1 : minecraftClient.player.getInventory().getSelectedSlot();
   }

   public static int findSlot(Predicate<ItemStack> predicate) {
      return findSlot(predicate, 0, 35);
   }

   public static int findSlot(Predicate<ItemStack> predicate, int minimumSlot, int maximumSlot) {
      if (minecraftClient.player == null) {
         return -1;
      }

      for (int inventorySlot = maximumSlot; inventorySlot >= minimumSlot; inventorySlot--) {
         if (predicate.test(minecraftClient.player.getInventory().getStack(inventorySlot))) {
            return inventorySlot;
         }
      }

      return -1;
   }

   public static int findItem(Item item) {
      return findSlot(stack -> stack.getItem() == item, 0, 35);
   }
}
