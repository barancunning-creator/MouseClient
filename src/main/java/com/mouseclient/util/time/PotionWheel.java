package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.GuiShapeRenderer;
import com.mouseclient.gui.ClosingOverlayRegistry;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.player.PotionThrower;
import com.mouseclient.modules.impl.player.ServerItemDefinition;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.avatar.Easings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class PotionWheel extends Screen implements ClosingOverlayRegistry.ClosingOverlay {

   private final ArrayList<TweenAnimation> slotHoverAnimations = new ArrayList<>();
   private final TweenAnimation openingAnimation = new TweenAnimation();
   private final TweenAnimation pickerAnimation = new TweenAnimation();
   private final ArrayList<TweenAnimation> pickerHoverAnimations = new ArrayList<>();
   private final HashMap<String, ItemStack> potionStackCache = new HashMap<>();
   private int hoveredSlot = -1;
   private int draggedSlot = -1;
   private int pickerSlot = -1;
   private int hoveredPickerRow = -1;
   private float radius;
   private float mouseX;
   private float mouseY;
   private float pickerX;
   private float pickerY;
   private float pickerWidth;
   private float pickerHeight;
   private boolean closing;

   private static PotionThrower thrower() {
      return PotionThrower.getInstance();
   }

   @Override
   protected void init() {
      ClosingOverlayRegistry.remove(this);
      this.closing = false;
      this.hoveredSlot = -1;
      this.draggedSlot = -1;
      this.pickerSlot = -1;
      this.openingAnimation.animateTo(1.0, (0.22D), Easings.QUAD_OUT);
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
   }

   private void drawSlot(int slotIndex, float centerX, float centerY, float scale, float alpha, float hoverProgress) {
      ServerItemDefinition potion = thrower().potionForSlot(slotIndex);
      if (potion == null) {
         float placeholderSize = (20.0F) * scale;
         GuiTextRenderer.drawCentered(
            GuiFont.BOLD, ("+"), centerX, centerY - placeholderSize / 2.0F, placeholderSize, GuiVisuals.multiplyAlpha(HudPanelRenderer.secondaryTextColor((0.4F) + (0.3F) * hoverProgress), alpha)
         );
      } else {
         float iconSize = (20.0F) * scale;
         float iconX = Math.round((centerX - iconSize / 2.0F) * 2.0F) / 2.0F;
         float iconY = Math.round((centerY - iconSize / 2.0F) * 2.0F) / 2.0F;
         GuiItemRenderer.render(this.potionStack(potion), iconX, iconY, iconSize, ItemRenderOptions.noDecorations(alpha));
      }
   }

   private void drawPicker(float x, float centerY, float screenHeight, float alpha) {
      java.util.ArrayList<com.mouseclient.modules.impl.player.ServerItemDefinition> potions = thrower().availablePotions();
      ArrayList<String> rows = this.pickerRows(potions);
      float maxLabelWidth = 0.0F;

      for (String label : rows) {
         maxLabelWidth = Math.max(maxLabelWidth, GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, label, (6.0F)));
      }

      this.pickerWidth = (16.0F) + maxLabelWidth + (6.0F);
      this.pickerHeight = rows.size() * (13.0F) + (4.0F);
      this.pickerX = x;
      this.pickerY = Math.max((4.0F), Math.min(screenHeight - this.pickerHeight - (4.0F), centerY - this.pickerHeight / 2.0F));
      float slideOffsetX = (6.0F) * (1.0F - alpha);
      float animatedPickerX = this.pickerX + slideOffsetX;
      HudPanelRenderer.drawGlassPanel(animatedPickerX, this.pickerY, this.pickerWidth, this.pickerHeight, alpha, (6.0F));
      this.hoveredPickerRow = -1;

      while (this.pickerHoverAnimations.size() < rows.size()) {
         this.pickerHoverAnimations.add(new TweenAnimation());
      }

      float pickerProgress = this.pickerAnimation.floatValue();

      for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
         float rowY = this.pickerY + 2.0F + rowIndex * (13.0F);
         boolean hovered = this.pickerOpen()
            && this.mouseX >= animatedPickerX
            && this.mouseX <= animatedPickerX + this.pickerWidth
            && this.mouseY >= rowY
            && this.mouseY < rowY + (13.0F);
         if (hovered) {
            this.hoveredPickerRow = rowIndex;
         }

         TweenAnimation hoverAnimation = this.pickerHoverAnimations.get(rowIndex);
         hoverAnimation.update();
         hoverAnimation.animateTo(hovered ? 1.0 : 0.0, (0.16D), Easings.QUAD_OUT);
         float rowHoverProgress = hoverAnimation.floatValue();
         float rowProgress = (float)Easings.QUART_OUT
            .apply(Math.max(0.0F, Math.min(1.0F, pickerProgress * (1.0F + rows.size() * (0.12F)) - rowIndex * (0.12F))));
         float rowAlpha = alpha * rowProgress * ((0.72F) + (0.28F) * rowHoverProgress);
         float contentX = animatedPickerX + (4.0F) + (6.0F) * (1.0F - rowProgress) + 2.0F * rowHoverProgress;
         boolean clearRow = rowIndex >= potions.size();
         if (!clearRow) {
            GuiItemRenderer.render(
               this.potionStack((ServerItemDefinition)potions.get(rowIndex)), contentX, rowY + (2.5F), (8.0F), ItemRenderOptions.noDecorations(alpha * rowProgress)
            );
         }

         if (clearRow) {
            GuiTextRenderer.draw(
               GuiFont.SEMI_BOLD,
               (String)rows.get(rowIndex),
               contentX + (8.0F) + (4.0F),
               rowY + (6.5F) - (3.0F),
               (6.0F),
               GuiVisuals.multiplyAlpha(GuiVisuals.lerpColor(HudPanelRenderer.secondaryTextColor(1.0F), (-42386), rowHoverProgress), rowAlpha)
            );
         } else {
            GuiTextRenderer.drawStyledText(
               GuiFont.SEMI_BOLD,
               this.potionName((ServerItemDefinition)potions.get(rowIndex)),
               contentX + (8.0F) + (4.0F),
               rowY + (6.5F) - (3.0F),
               (6.0F),
               GuiVisuals.multiplyAlpha(HudPanelRenderer.primaryTextColor(1.0F), rowAlpha)
            );
         }
      }
   }

   private ArrayList<String> pickerRows(ArrayList<ServerItemDefinition> potions) {
      ArrayList rows = new ArrayList();

      for (ServerItemDefinition potion : potions) {
         rows.add(potion.name());
      }

      if (this.pickerSlot != -1 && this.pickerSlot < slots().size() && !slots().get(this.pickerSlot).isEmpty()) {
         rows.add(("Очистить"));
      }

      return rows;
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public boolean renderClosing(DrawContext context) {
      this.drawWheel(context, false);
      return this.openingAnimation.floatValue() > (0.01F);
   }

   private void closePicker() {
      this.pickerSlot = -1;
      this.hoveredPickerRow = -1;
      if (!thrower().isWheelHeld()) {
         this.startClosing();
      }
   }

   private ItemStack potionStack(ServerItemDefinition definition) {
      return this.potionStackCache
         .computeIfAbsent(
            definition.name(),
            potionName -> {
               SpecialPotion potion = SpecialPotion.fromName(potionName);
               ItemStack stack = definition.displayStack().copy();
               if (potion != null) {
                  stack.set(
                     DataComponentTypes.POTION_CONTENTS,
                     new PotionContentsComponent(Optional.empty(), Optional.of(potion.colorRgb() | (-16777216)), List.of(), Optional.empty())
                  );
               }

               return stack;
            }
         );
   }

   private void clampCursor(MinecraftClient client, float screenWidth, float screenHeight) {
      if (client.isWindowFocused()) {
         Window window = client.getWindow();
         double pixelScaleX = (double)window.getFramebufferWidth() / screenWidth;
         double pixelScaleY = (double)window.getFramebufferHeight() / screenHeight;
         double centerPixelX = screenWidth / (2.0D) * pixelScaleX;
         double centerPixelY = screenHeight / (2.0D) * pixelScaleY;
         double cursorOffsetX = client.mouse.getX() - centerPixelX;
         double cursorOffsetY = client.mouse.getY() - centerPixelY;
         double maxCursorRadius = (this.radius - 1.0) * pixelScaleX;
         double cursorDistance = Math.hypot(cursorOffsetX, cursorOffsetY);
         if (cursorDistance > maxCursorRadius) {
            double clampFactor = maxCursorRadius / cursorDistance;
            GLFW.glfwSetCursorPos(window.getHandle(), centerPixelX + cursorOffsetX * clampFactor, centerPixelY + cursorOffsetY * clampFactor);
         }
      }
   }

   public boolean closing() {
      return this.closing;
   }

   private void openPicker(int slotIndex) {
      this.pickerSlot = slotIndex;
      this.draggedSlot = -1;
   }

   private static float inputScale() {
      return GuiScale.getWindowScale() / GuiScale.getBaseScale();
   }

   public PotionWheel() {
      super(Text.literal(("PotionWheel")));
      this.radius = (92.0F);
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (!this.closing) {
         this.drawWheel(context, true);
      }
   }

   public void releaseHeldSlot(int slotIndex) {
      if (this.draggedSlot != -1) {
         if (slotIndex != -1 && slotIndex != this.draggedSlot && slotIndex < slots().size() && this.draggedSlot < slots().size()) {
            Collections.swap(slots(), this.draggedSlot, slotIndex);
            thrower().saveSlotNames();
         }

         this.draggedSlot = -1;
      }
   }

   public int draggedSlot() {
      return this.draggedSlot;
   }

   private Text potionName(ServerItemDefinition definition) {
      SpecialPotion potion = SpecialPotion.fromName(definition.name());
      return potion != null ? potion.shortName() : Text.literal(definition.name());
   }

   public boolean pickerOpen() {
      return this.pickerSlot != -1;
   }

   private int hoveredAt(float mouseX, float mouseY, float centerX, float centerY, int slotCount) {
      float offsetX = mouseX - centerX;
      float offsetY = mouseY - centerY;
      if (Math.hypot(offsetX, offsetY) > this.radius * (1.25F)) {
         return -1;
      }

      double sectorDegrees = (360.0D) / slotCount;
      double angleDegrees = Math.toDegrees(Math.atan2(offsetY, offsetX)) + (90.0D) + sectorDegrees / (2.0D);

      while (angleDegrees < 0.0) {
         angleDegrees += (360.0D);
      }

      angleDegrees %= (360.0D);
      return Math.min(slotCount - 1, (int)(angleDegrees / sectorDegrees));
   }

   public void startClosing() {
      if (!this.closing) {
         this.closing = true;
         this.hoveredSlot = -1;
         this.draggedSlot = -1;
         this.pickerSlot = -1;
         this.openingAnimation.animateTo(0.0, (0.22D), Easings.QUAD_OUT);
         ClosingOverlayRegistry.add(this);
         if (this.client != null && this.client.currentScreen == this) {
            this.client.setScreen(null);
         }
      }
   }

   private static ArrayList<String> slots() {
      return thrower().slotNames();
   }

   private void drawWheel(DrawContext context, boolean interactive) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null) {
         Render2D.begin(context);
         HudRenderContext.setDrawContext(context);
         float screenWidth = this.width * inputScale();
         float screenHeight = this.height * inputScale();
         float centerX = screenWidth / 2.0F;
         float centerY = screenHeight / 2.0F;
         this.radius = Math.min((140.0F), Math.max((56.0F), Math.min(screenWidth, screenHeight) * (0.17F)));
         if (interactive && !this.pickerOpen()) {
            this.clampCursor(client, screenWidth, screenHeight);
         }

         this.openingAnimation.update();
         this.pickerAnimation.update();
         this.pickerAnimation.animateTo(this.pickerOpen() ? 1.0 : 0.0, this.pickerOpen() ? (0.32D) : (0.16D), Easings.QUAD_OUT);
         this.mouseX = (float)client.mouse.getScaledX(client.getWindow()) * inputScale();
         this.mouseY = (float)client.mouse.getScaledY(client.getWindow()) * inputScale();
         int slotCount = slots().size();

         while (this.slotHoverAnimations.size() < slotCount) {
            this.slotHoverAnimations.add(new TweenAnimation());
         }

         this.hoveredSlot = interactive && !this.closing && !this.pickerOpen() ? this.hoveredAt(this.mouseX, this.mouseY, centerX, centerY, slotCount) : -1;
         float openingProgress = this.openingAnimation.floatValue();
         if (!(openingProgress <= (0.004F))) {
            float animatedRadius = this.radius * ((0.88F) + (0.12F) * openingProgress);
            float wheelScale = animatedRadius / (92.0F);
            float sectorDegrees = (360.0F) / slotCount;

            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
               TweenAnimation hoverAnimation = this.slotHoverAnimations.get(slotIndex);
               hoverAnimation.update();
               hoverAnimation.animateTo(
                  slotIndex != this.hoveredSlot && slotIndex != this.draggedSlot && slotIndex != this.pickerSlot ? 0.0 : 1.0, (0.18D), Easings.QUAD_OUT
               );
               float hoverProgress = hoverAnimation.floatValue();
               float startDegrees = (-90.0F) - sectorDegrees / 2.0F + sectorDegrees * slotIndex;
               double middleRadians = Math.toRadians(startDegrees + sectorDegrees / 2.0F);
               float hoverScale = 1.0F + (0.03F) * hoverProgress;
               HudRenderContext.pushScale(centerX, centerY, hoverScale);
               GuiShapeRenderer.drawBlurredSector(
                  GuiShapeRenderer.BlurQuality.MIDDLE,
                  centerX,
                  centerY,
                  animatedRadius,
                  startDegrees,
                  sectorDegrees,
                  (6.0F) * wheelScale / hoverScale,
                  (10.0F) * wheelScale,
                  slotIndex == this.draggedSlot
                     ? GuiVisuals.lerpColor(HudPanelRenderer.panelTint(), HudPanelRenderer.accentColor(1.0F), (0.35F))
                     : HudPanelRenderer.panelTint(),
                  (0.8F),
                  openingProgress
               );
               float iconX = centerX + (float)Math.cos(middleRadians) * animatedRadius * (0.62F);
               float iconY = centerY + (float)Math.sin(middleRadians) * animatedRadius * (0.62F);
               if (slotIndex != this.draggedSlot) {
                  this.drawSlot(slotIndex, iconX, iconY, wheelScale, openingProgress, hoverProgress);
               }

               HudRenderContext.popTransform();
            }

            if (this.draggedSlot != -1) {
               ServerItemDefinition draggedPotion = thrower().potionForSlot(this.draggedSlot);
               if (draggedPotion != null) {
                  float draggedIconSize = (20.0F) * wheelScale;
                  GuiItemRenderer.render(
                     this.potionStack(draggedPotion), this.mouseX - draggedIconSize / 2.0F, this.mouseY - draggedIconSize / 2.0F, draggedIconSize, ItemRenderOptions.noDecorations(openingProgress)
                  );
               }
            }

            int labelSlot = this.draggedSlot != -1 ? this.draggedSlot : this.hoveredSlot;
            ServerItemDefinition labelPotion = thrower().potionForSlot(labelSlot);
            if (labelPotion != null) {
               Text text = this.potionName(labelPotion);
               GuiTextRenderer.drawStyledText(
                  GuiFont.SEMI_BOLD,
                  text,
                  centerX - GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, text.getString(), (6.0F)) / 2.0F,
                  centerY + animatedRadius + (6.0F),
                  (6.0F),
                  GuiVisuals.multiplyAlpha(HudPanelRenderer.primaryTextColor(1.0F), openingProgress)
               );
            }

            float pickerProgress = this.pickerAnimation.floatValue();
            if (pickerProgress > (0.004F)) {
               this.drawPicker(centerX + animatedRadius + (12.0F), centerY, screenHeight, openingProgress * pickerProgress);
            } else {
               this.hoveredPickerRow = -1;
            }

            Render2D.end();
         }
      }
   }

   @Override
   public void close() {
      if (this.pickerOpen()) {
         this.closePicker();
      } else {
         this.startClosing();
      }
   }

   @Override
   public boolean mouseReleased(Click click) {
      if (click.button() == 0 && this.draggedSlot != -1) {
         this.releaseHeldSlot(this.hoveredSlot);
         return true;
      } else {
         return super.mouseReleased(click);
      }
   }

   public int hoveredSlot() {
      return this.hoveredSlot;
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubled) {
      if (this.closing) {
         return true;
      }

      if (this.pickerOpen()) {
         if (click.button() == 0 && this.hoveredPickerRow != -1) {
            java.util.ArrayList<com.mouseclient.modules.impl.player.ServerItemDefinition> potions = thrower().availablePotions();
            ArrayList rows = this.pickerRows(potions);
            if (this.hoveredPickerRow < potions.size()) {
               slots().set(this.pickerSlot, ((ServerItemDefinition)potions.get(this.hoveredPickerRow)).name());
            } else if (((String)rows.get(this.hoveredPickerRow)).equals(("Очистить"))) {
               slots().set(this.pickerSlot, "");
            }

            thrower().saveSlotNames();
         }

         this.closePicker();
         return true;
      } else {
         if (this.hoveredSlot != -1) {
            if (click.button() == 1) {
               this.openPicker(this.hoveredSlot);
               return true;
            }

            if (click.button() == 0) {
               if (thrower().potionForSlot(this.hoveredSlot) != null) {
                  this.draggedSlot = this.hoveredSlot;
               } else {
                  this.openPicker(this.hoveredSlot);
               }

               return true;
            }
         }

         return super.mouseClicked(click, doubled);
      }
   }
}
