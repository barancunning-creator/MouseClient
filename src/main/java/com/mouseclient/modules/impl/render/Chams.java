package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

@ModuleInfo(name = "Chams", description = "Показывает выбранных существ сквозь стены вместе с экипировкой.", category = ModuleCategory.RENDER)
public final class Chams extends Module {
   private static Chams INSTANCE;
   public MultiSelectSetting renderTargets;

   private Chams() {
      this.renderTargets = this.addSetting(new MultiSelectSetting(("Кого показывать"), ("Кого видно сквозь стены."), new String[]{("Игроков"), ("Мобов"), ("Голых игроков"), ("Игроков в незерите")}, ("Игроков")));
   }

   public static Chams getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Chams();
      }

      return INSTANCE;
   }

   public boolean shouldRenderThroughWalls(LivingEntity entity) {
      if (this.isEnabled() && entity != minecraftClient.player && !(entity instanceof ArmorStandEntity)) {
         if (!(entity instanceof PlayerEntity player)) {
            return this.renderTargets.matchesOption(("Мобов"));
         } else {
            if (this.renderTargets.matchesOption(("Игроков"))) {
               return true;
            }

            int armoredSlotCount = 0;
            int netheritePieceCount = 0;

            for (EquipmentSlot armorSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
               ItemStack stack = player.getEquippedStack(armorSlot);
               if (!stack.isEmpty()) {
                  armoredSlotCount++;
                  if (stack.isOf(Items.NETHERITE_HELMET)
                     || stack.isOf(Items.NETHERITE_CHESTPLATE)
                     || stack.isOf(Items.NETHERITE_LEGGINGS)
                     || stack.isOf(Items.NETHERITE_BOOTS)) {
                     netheritePieceCount++;
                  }
               }
            }

            return this.renderTargets.matchesOption(("Голых игроков")) && armoredSlotCount == 0 || this.renderTargets.matchesOption(("Игроков в незерите")) && netheritePieceCount > 0;
         }
      } else {
         return false;
      }
   }
}
