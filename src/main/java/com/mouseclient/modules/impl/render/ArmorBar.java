package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.entity.ModuleSoundPlayer;
import com.mouseclient.util.time.ToastNotifications;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

@ModuleInfo(name = "ArmorBar", description = "Броня на уровне хотбара или над голодом ванильными текстурами.", category = ModuleCategory.RENDER)
public final class ArmorBar extends Module {
   private static ArmorBar INSTANCE;
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private static final Identifier[] EMPTY_SLOT_TEXTURES = new Identifier[]{
      PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE,
      PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
      PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE,
      PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE
   };
   private static final String[] ARMOR_LABELS = new String[]{("Шлем"), ("Нагрудник"), ("Поножи"), ("Ботинки")};
   public ModeSetting mode;
   public BooleanSetting notifyDurability;
   private final boolean[] durabilityNotified = new boolean[4];
   private final EventListener<HudRenderEvent> hudRenderListener = event -> {
      if (minecraftClient.player != null && !minecraftClient.options.hudHidden) {
         for (int var2 = 0; var2 < 4; var2++) {
            if (this.mode.isSelected(("Тип 1"))) {
               this.drawDurability(
                  minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var2]),
                  (
                        minecraftClient.player.getMainArm() == Arm.LEFT
                           ? event.getWidth() / 2.0F - (91.0F) - (4.0F) - (82.0F)
                           : event.getWidth() / 2.0F + (91.0F) + (4.0F)
                     )
                     + (3.0F)
                     + var2 * (20.0F),
                  event.getHeight() - (22.0F) + (3.0F)
               );
            } else {
               this.drawDurability(
                  minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var2]),
                  event.getWidth() / 2.0F + (91.0F) - (70.0F) + var2 * (18.0F),
                  event.getHeight() - (57.0F) - (Math.round(minecraftClient.player.getHungerManager().getSaturationLevel()) > 0 ? (10.0F) : 0.0F)
               );
            }
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && this.notifyDurability.getValue()) {
         for (int var2 = 0; var2 < 4; var2++) {
            ItemStack stack = minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var2]);
            if (!stack.isEmpty() && stack.isDamageable()) {
               if (1.0F - (float)stack.getDamage() / stack.getMaxDamage() > (0.3F)) {
                  this.durabilityNotified[var2] = false;
               } else if (!this.durabilityNotified[var2]) {
                  this.durabilityNotified[var2] = true;
                  ToastNotifications.showIconToast(
                     this.category.iconGlyph(),
                     ARMOR_LABELS[var2],
                     "прочность " + Math.round((100.0F) * (1.0F - (float)stack.getDamage() / stack.getMaxDamage())) + "%, скоро сломается",
                     true
                  );
                  ModuleSoundPlayer.play(("notify_type1_enabled"));
               }
            } else {
               this.durabilityNotified[var2] = false;
            }
         }
      }
   };

   public void renderArmor(DrawContext context, RenderTickCounter tickCounter) {
      if (minecraftClient.player != null) {
         if (this.mode.isSelected(("Тип 1"))) {
            this.renderBesideHotbar(context);
         } else {
            this.renderAboveHunger(context);
         }
      }
   }

   public static ArmorBar getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ArmorBar();
      }

      return INSTANCE;
   }

   private ArmorBar() {
      this.mode = this.addSetting(new ModeSetting(("Тип"), ("Панель сбоку от хотбара."), ("Тип 1"), ("Тип 1")));
      this.mode.setVisibility(() -> false);
      this.notifyDurability = this.addSetting(new BooleanSetting(("Уведомлять о прочности"), ("Уведомление, когда прочность элемента брони падает ниже 30%."), true));
   }

   private void drawDurability(ItemStack stack, float x, float y) {
      if (!stack.isEmpty() && stack.isDamageable()) {
         float guiScale = GuiScale.getWindowScale() / GuiScale.getBaseScale();
         String durabilityText = Math.round((100.0F) * (1.0F - (float)stack.getDamage() / stack.getMaxDamage())) + "%";
         float fontSize = (5.0F) * guiScale;
         float outlineOffset = (0.7F) * guiScale;
         float textX = (x + 1.0F) * guiScale;
         float textY = (y + (2.5F)) * guiScale - fontSize / 2.0F;
         int durabilityColor = Math.round((100.0F) * (1.0F - (float)stack.getDamage() / stack.getMaxDamage())) > 50
            ? (-12517568)
            : (Math.round((100.0F) * (1.0F - (float)stack.getDamage() / stack.getMaxDamage())) > 30 ? -20440 : (-50384));
         GuiTextRenderer.draw(GuiFont.BOLD, durabilityText, textX - outlineOffset, textY, fontSize, (-16777216));
         GuiTextRenderer.draw(GuiFont.BOLD, durabilityText, textX + outlineOffset, textY, fontSize, (-16777216));
         GuiTextRenderer.draw(GuiFont.BOLD, durabilityText, textX, textY - outlineOffset, fontSize, (-16777216));
         GuiTextRenderer.draw(GuiFont.BOLD, durabilityText, textX, textY + outlineOffset, fontSize, (-16777216));
         GuiTextRenderer.draw(GuiFont.BOLD, durabilityText, textX, textY, fontSize, durabilityColor);
      }
   }

   private void renderAboveHunger(DrawContext context) {
      float x = context.getScaledWindowWidth() / 2.0F + (91.0F) - (70.0F);
      float y = context.getScaledWindowHeight()
         - (57.0F)
         - (Math.round(minecraftClient.player.getHungerManager().getSaturationLevel()) > 0 ? (10.0F) : 0.0F);

      for (int var4 = 0; var4 < 4; var4++) {
         if (minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var4]).isEmpty()) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, EMPTY_SLOT_TEXTURES[var4], (int)x + var4 * 18, (int)y, 16, 16);
         } else {
            context.drawItem(minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var4]), (int)x + var4 * 18, (int)y);
            context.drawStackOverlay(
               minecraftClient.textRenderer, minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var4]), (int)x + var4 * 18, (int)y
            );
         }
      }
   }

   private void renderBesideHotbar(DrawContext context) {
      float x = minecraftClient.player.getMainArm() == Arm.LEFT
         ? context.getScaledWindowWidth() / 2.0F - (91.0F) - (4.0F) - (82.0F)
         : context.getScaledWindowWidth() / 2.0F + (91.0F) + (4.0F);
      float y = context.getScaledWindowHeight() - (22.0F);
      context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla(("hud/hotbar")), 182, 22, 0, 0, (int)x, (int)y, 81, 22);
      context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla(("hud/hotbar")), 182, 22, 181, 0, (int)x + 81, (int)y, 1, 22);

      for (int var4 = 0; var4 < 4; var4++) {
         context.drawItem(minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var4]), (int)x + 3 + var4 * 20, (int)y + 3);
         if (!minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var4]).isEmpty()) {
            context.drawStackOverlay(
               minecraftClient.textRenderer, minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var4]), (int)x + 3 + var4 * 20, (int)y + 3
            );
         }
      }
   }
}
