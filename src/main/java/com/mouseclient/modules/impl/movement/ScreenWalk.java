package com.mouseclient.modules.impl.movement;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.MouseClientClickGUI;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.ClientCommonNetworkHandlerAccessor;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.MovementFreezeController;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.network.PlayerTickEvent;
import java.util.ArrayList;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

@ModuleInfo(
   name = "ScreenWalk",
   description = "Позволяет двигаться с открытым контейнером, задерживая пакеты инвентаря.",
   category = ModuleCategory.MOVEMENT
)
public final class ScreenWalk extends Module {
   private static ScreenWalk INSTANCE;
   private final ArrayList<ScreenWalk.DeferredInventoryPacket> deferredPackets = new ArrayList<>();
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isOutgoing()) {
         if (minecraftClient.currentScreen instanceof InventoryScreen && event.getPacket() instanceof ClickSlotC2SPacket clickPacket && this.isMovementInputActive()) {
            boolean shulkerPlacement = clickPacket.button() == 1
               && minecraftClient.player.currentScreenHandler.getCursorStack().getItem() instanceof BlockItem cursorBlock
               && cursorBlock.getBlock() instanceof ShulkerBoxBlock;
            if (shulkerPlacement) {
               MovementFreezeController.getInstance().freezeForTicks(2);
            }

            this.deferredPackets.add(new ScreenWalk.DeferredInventoryPacket(event.getPacket(), 2, shulkerPlacement));
            event.markCancelled();
         }

         if (event.getPacket() instanceof CloseHandledScreenC2SPacket && this.isMovementInputActive() && minecraftClient.currentScreen instanceof InventoryScreen) {
            event.markCancelled();

            for (ScreenWalk.DeferredInventoryPacket deferredPacket : this.deferredPackets) {
               MovementFreezeController.getInstance().freezeForTicks(deferredPacket.freezeTicks);
            }
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         if (minecraftClient.currentScreen != null
            && !(minecraftClient.currentScreen instanceof ChatScreen)
            && !(minecraftClient.currentScreen instanceof SignEditScreen)
            && !(minecraftClient.currentScreen instanceof AnvilScreen)
            && !(minecraftClient.currentScreen instanceof CreativeInventoryScreen)) {
            for (KeyBinding movementKey : new KeyBinding[]{
               minecraftClient.options.forwardKey,
               minecraftClient.options.backKey,
               minecraftClient.options.leftKey,
               minecraftClient.options.rightKey,
               minecraftClient.options.jumpKey
            }) {
               movementKey.setPressed(
                  !(
                        minecraftClient.currentScreen instanceof MouseClientClickGUI clickGui
                           && (clickGui.getCtx().focusedText != null || clickGui.getCtx().inputHandler != null)
                     )
                     && InputUtil.isKeyPressed(minecraftClient.getWindow(), KeyBindingHelper.getBoundKeyOf(movementKey).getCode())
               );
            }
         }

         if (!this.isMovementInputActive() && !this.deferredPackets.isEmpty()) {
            for (ScreenWalk.DeferredInventoryPacket deferredPacket : this.deferredPackets) {
               ((ClientCommonNetworkHandlerAccessor)(Object)minecraftClient.getNetworkHandler()).connection().send(deferredPacket.packet, null);
            }

            this.deferredPackets.clear();
            if (minecraftClient.currentScreen == null) {
               ((ClientCommonNetworkHandlerAccessor)(Object)minecraftClient.getNetworkHandler())
                  .connection()
                  .send(new CloseHandledScreenC2SPacket(minecraftClient.player.currentScreenHandler.syncId), null);
            }
         }
      }
   };

   @Override
   protected void onDisable() {
      this.deferredPackets.clear();
   }

   public boolean isSpookyTimeConfigured() {
      return ServerHelper.getInstance().server.matchesOption(("SpookyTime"));
   }

   private ScreenWalk() {
   }

   public void dropInventoryWithClicks() {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null) {
         int syncId = minecraftClient.player.playerScreenHandler.syncId;
         if (!minecraftClient.player.playerScreenHandler.getCursorStack().isEmpty()) {
            minecraftClient.interactionManager.clickSlot(syncId, -999, 0, SlotActionType.PICKUP, minecraftClient.player);
         }

         for (int var2 = 1; var2 < minecraftClient.player.playerScreenHandler.slots.size(); var2++) {
            if (minecraftClient.player.playerScreenHandler.getSlot(var2).hasStack()) {
               minecraftClient.interactionManager.clickSlot(syncId, var2, 1, SlotActionType.THROW, minecraftClient.player);
            }
         }
      }
   }

   public static ScreenWalk getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ScreenWalk();
      }

      return INSTANCE;
   }

   public boolean isMovementInputActive() {
      return minecraftClient.player != null
         && (minecraftClient.player.input.getMovementInput().lengthSquared() > 0.0F || minecraftClient.player.input.playerInput.jump());
   }

   public static final class DeferredInventoryPacket {
      private final Packet<?> packet;
      private final int freezeTicks;
      private final boolean unusedShulkerPlacement;

      private DeferredInventoryPacket(Packet<?> packet, int freezeTicks, boolean unusedShulkerPlacement) {
         this.packet = packet;
         this.freezeTicks = freezeTicks;
         this.unusedShulkerPlacement = unusedShulkerPlacement;
      }
   }
}
