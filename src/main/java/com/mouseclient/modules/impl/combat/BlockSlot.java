package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.SlotClickEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import net.minecraft.screen.slot.SlotActionType;

@ModuleInfo(name = "BlockSlot", description = "Не даёт выкинуть предмет из выбранных слотов хотбара.", category = ModuleCategory.COMBAT)
public final class BlockSlot extends Module {
   private static BlockSlot INSTANCE;
   public MultiSelectSetting slots;
   private final EventListener<SlotClickEvent> slotClickListener = event -> {
      if (minecraftClient.player != null
         && event.getActionType() == SlotActionType.THROW
         && minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
         if (event.getSlotId() >= 36 && event.getSlotId() <= 44 && this.protectsHotbarSlot(event.getSlotId() - 36)) {
            event.markCancelled();
         }
      }
   };

   public boolean protectsHotbarSlot(int slotIndex) {
      return this.isEnabled() && this.slots.isSelectedAt(slotIndex);
   }

   public static BlockSlot getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new BlockSlot();
      }

      return INSTANCE;
   }

   private BlockSlot() {
      this.slots = this.addSetting(new MultiSelectSetting(("Слоты"), ("Какие слоты хотбара защищать от выброса."), new String[]{("1"), ("2"), ("3"), ("4"), ("5"), ("6"), ("7"), ("8"), ("9")}));
   }
}
