package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.ClientMath;
import com.mouseclient.mixin.MinecraftActionInvoker;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import com.mouseclient.util.entity.RotationAngles;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

@ModuleInfo(name = "ClanUpgrader", description = "Быстро прокачивает клан с помощью редстоуна и факела.", category = ModuleCategory.MISC)
public final class ClanUpgrader extends Module {
   private static ClanUpgrader INSTANCE;
   private int previousHotbarSlot = -1;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.interactionManager != null) {
         int redstoneSlot = InventoryActions.findHotbarItem(Items.REDSTONE);
         int torchSlot = InventoryActions.findHotbarItem(Items.TORCH);
         int itemSlot = redstoneSlot != -1 ? redstoneSlot : torchSlot;
         if (itemSlot == -1) {
            ChatMessages.printPrefixed(("Вам необходимо иметь факел или редстоун в хотбаре"));
            this.toggle();
         } else {
            float pitchOffset = (float)(Math.sin(System.currentTimeMillis() / (1220.0D)) * (Math.abs((90.0F) - minecraftClient.player.getPitch()) / (8.0F)))
               + ClientMath.randomFloat((-0.1F), (0.1F));
            RotationAngles rotation = new RotationAngles(
               minecraftClient.player.getYaw() + ClientMath.randomFloat((-1.0F), 1.0F), MathHelper.clamp((88.0F) + pitchOffset, (-90.0F), (90.0F))
            );
            RotationController.getInstance().requestRotation(rotation, new RotationLimits((90.0F), (90.0F), (90.0F), (90.0F)));
            if (this.previousHotbarSlot == -1) {
               this.previousHotbarSlot = InventoryActions.selectedHotbarSlot();
            }

            if (InventoryActions.selectedHotbarSlot() != itemSlot) {
               minecraftClient.player.getInventory().setSelectedSlot(itemSlot);
            }

            if (!(
               Math.hypot(
                     Math.abs(MathHelper.wrapDegrees(rotation.getYaw() - minecraftClient.player.getYaw())),
                     Math.abs(MathHelper.wrapDegrees(rotation.getPitch() - minecraftClient.player.getPitch()))
                  )
                  > 1.0
            )) {
               BlockPos position = minecraftClient.player.getBlockPos();
               if (!minecraftClient.world.getBlockState(position).isOf(Blocks.REDSTONE_WIRE) && !minecraftClient.world.getBlockState(position).isOf(Blocks.TORCH)) {
                  ((MinecraftActionInvoker)(Object)minecraftClient).invokeStartUseItem();
               } else {
                  minecraftClient.interactionManager.attackBlock(position, Direction.UP);
                  minecraftClient.player.swingHand(Hand.MAIN_HAND);
               }
            }
         }
      }
   };

   public static ClanUpgrader getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ClanUpgrader();
      }

      return INSTANCE;
   }

   private ClanUpgrader() {
   }

   @Override
   protected void onDisable() {
      if (minecraftClient.player != null && this.previousHotbarSlot != -1) {
         minecraftClient.player.getInventory().setSelectedSlot(this.previousHotbarSlot);
         this.previousHotbarSlot = -1;
      }
   }
}
