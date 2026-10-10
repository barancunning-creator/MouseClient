package com.mouseclient.modules.impl.player;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.mixin.HandledScreenAccessor;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.render.pipeline.SliderSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(name = "ItemScroller", description = "Шифт с ЛКМ мгновенно перекидывает предметы под курсором.", category = ModuleCategory.PLAYER)
public final class ItemScroller extends Module {
   private static ItemScroller INSTANCE;
   private final SliderSetting delay;
   private long lastTransferMillis;
   private final EventListener<PlayerTickEvent> playerTickListener;

   private ItemScroller() {
      this.delay = this.addSetting(new SliderSetting(("Задержка"), ("Задержка между перекидываниями.")).withRange(0.0F, (500.0F)).withStep((10.0F)).setValue((50.0F)).withSuffix(("ms")));
      this.playerTickListener = event -> {
         if (minecraftClient.player != null && minecraftClient.currentScreen instanceof HandledScreen handledScreen) {
            if (InputUtil.isKeyPressed(minecraftClient.getWindow(), 340) || InputUtil.isKeyPressed(minecraftClient.getWindow(), 344)) {
               if (GLFW.glfwGetMouseButton(minecraftClient.getWindow().getHandle(), 0) == 1) {
                  if (!((float)(System.currentTimeMillis() - this.lastTransferMillis) < this.delay.floatValue())) {
                     Slot slot = ((HandledScreenAccessor)(Object)handledScreen).hoveredSlot();
                     if (slot != null && slot.hasStack()) {
                        this.lastTransferMillis = System.currentTimeMillis();
                        InventoryActions.clickSlot(slot.id, 0, SlotActionType.QUICK_MOVE, false);
                     }
                  }
               }
            }
         }
      };
   }

   public static ItemScroller getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ItemScroller();
      }

      return INSTANCE;
   }
}
