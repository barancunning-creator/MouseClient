package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.gui.PotionPresetEditor;
import com.mouseclient.gui.PotionCombinerScreen;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.time.PotionPresetStore;
import java.util.ArrayDeque;
import java.util.Random;
import net.minecraft.client.gui.screen.ChatScreen;

@ModuleInfo(
   name = "PotionCombiner",
   description = "Комбинирует бафы SpookyTime: по бинду кидает зелья пресета по очереди.",
   category = ModuleCategory.MISC
)
public final class PotionCombiner extends Module {
   private static PotionCombiner INSTANCE;
   public BindSetting openMenuBind;
   private final Random random = new Random();
   private final ArrayDeque<String> pendingPotionNames = new ArrayDeque<>();
   private boolean menuKeyHeld;
   private long nextPotionAtMillis;
   private long messageRepeatAfterMillis;
   private String lastMessage = "";
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.getWindow() != null) {
         if (this.openMenuBind.isBound()
            && this.openMenuBind.getBinding().isPressed(minecraftClient.getWindow().getHandle())
            && !this.menuKeyHeld
            && !(minecraftClient.currentScreen instanceof ChatScreen)
            && !(minecraftClient.currentScreen instanceof PotionCombinerScreen)) {
            PotionPresetEditor.getInstance().open();
            minecraftClient.setScreen(PotionCombinerScreen.instance());
         }

         this.menuKeyHeld = this.openMenuBind.isBound() && this.openMenuBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (minecraftClient.world != null && minecraftClient.interactionManager != null) {
            this.pollPresetKeys();
            this.useNextPotion();
         }
      }
   };

   private void useNextPotion() {
      if (!this.pendingPotionNames.isEmpty() && minecraftClient.currentScreen == null && System.currentTimeMillis() >= this.nextPotionAtMillis) {
         if (!ItemUseQueue.getInstance().hasPendingUses() && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
            String potionName = this.pendingPotionNames.poll();

            for (ServerHelper.ServerItemBind serverBinding : ServerHelper.getInstance().itemBinds()) {
               if (serverBinding.item() == null && serverBinding.binding().name.equals(potionName)) {
                  ServerHelper.getInstance().requestDownwardPitch();
                  ServerHelper.getInstance().useBoundItem(serverBinding);
                  break;
               }
            }

            this.nextPotionAtMillis = System.currentTimeMillis() + (80L) + this.random.nextInt(40);
         }
      }
   }

   private void disableWithMessage(String message) {
      this.printThrottledMessage(message);
      if (this.isEnabled()) {
         this.toggle();
      }
   }

   @Override
   protected void onDisable() {
      this.pendingPotionNames.clear();
   }

   @Override
   protected void onEnable() {
      this.lastMessage = "";
      this.messageRepeatAfterMillis = 0L;
   }

   private PotionCombiner() {
      this.openMenuBind = this.addSetting(new BindSetting(("Открыть меню"), ("Клавиша, которая открывает окно Potion Combiner.")));
   }

   private void printThrottledMessage(String message) {
      if (message != null && !message.isBlank()) {
         if (!message.equals(this.lastMessage) || System.currentTimeMillis() >= this.messageRepeatAfterMillis) {
            ChatMessages.printPrefixed("&8[&dPotionCombiner&8] &f" + message);
            this.lastMessage = message;
            this.messageRepeatAfterMillis = System.currentTimeMillis() + (2500L);
         }
      }
   }

   private void pollPresetKeys() {
      for (PotionPresetStore.PotionPreset preset : PotionPresetStore.getInstance().presets) {
         boolean keyWasPressed = preset.hotkey.isBound()
            && preset.hotkey.getBinding().isPressed(minecraftClient.getWindow().getHandle())
            && minecraftClient.currentScreen == null;
         if (preset.enabled && keyWasPressed && !preset.keyWasPressed && this.pendingPotionNames.isEmpty()) {
            for (PotionPresetStore.PresetPotion potion : preset.potions) {
               this.pendingPotionNames.add(potion.name);
            }

            this.nextPotionAtMillis = System.currentTimeMillis();
            this.printThrottledMessage("Комбо &d" + preset.name + "&f: кидаю " + preset.potions.size() + " зелья по очереди.");
         }

         preset.keyWasPressed = keyWasPressed;
      }
   }

   public static PotionCombiner getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new PotionCombiner();
      }

      return INSTANCE;
   }
}
