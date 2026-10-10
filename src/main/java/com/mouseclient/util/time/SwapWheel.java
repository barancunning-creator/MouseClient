package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.GuiShapeRenderer;
import com.mouseclient.gui.ClosingOverlayRegistry;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.StyledTextParser;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.avatar.Easings;
import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class SwapWheel extends Screen implements ClosingOverlayRegistry.ClosingOverlay {

   private final ItemStack[] slots;
   private final IntConsumer onChooseSlot;
   private final TweenAnimation[] slotHoverAnimations;
   private final TweenAnimation openingAnimation = new TweenAnimation();
   private int hoveredSlot = -1;
   private float radius;
   private boolean closing;

   private int sliceColor(float hoverProgress) {
      int panelColor = HudPanelRenderer.panelTint();
      return hoverProgress > (0.004F) ? GuiVisuals.lerpColor(panelColor, GuiVisuals.white, (0.16F) * hoverProgress) : panelColor;
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

   private int hoveredAt(float mouseX, float mouseY, float centerX, float centerY) {
      float offsetX = mouseX - centerX;
      float offsetY = mouseY - centerY;
      if (Math.hypot(offsetX, offsetY) > this.radius * (1.25F)) {
         return -1;
      }

      double sectorDegrees = (360.0D) / this.slots.length;
      double angleDegrees = Math.toDegrees(Math.atan2(offsetY, offsetX)) + (90.0D) + sectorDegrees / (2.0D);

      while (angleDegrees < 0.0) {
         angleDegrees += (360.0D);
      }

      angleDegrees %= (360.0D);
      return Math.min(this.slots.length - 1, (int)(angleDegrees / sectorDegrees));
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
   }

   private static float inputScale() {
      return GuiScale.getWindowScale() / GuiScale.getBaseScale();
   }

   @Override
   public void close() {
      this.startClosing();
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (!this.closing) {
         this.drawWheel(context, true);
      }
   }

   public boolean closing() {
      return this.closing;
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubled) {
      if (this.hoveredSlot == -1 || click.button() != 1 && (click.button() != 0 || !this.slots[this.hoveredSlot].isEmpty())) {
         return super.mouseClicked(click, doubled);
      }

      this.onChooseSlot.accept(this.hoveredSlot);
      return true;
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   public int hoveredSlot() {
      return this.hoveredSlot;
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
         if (interactive) {
            this.clampCursor(client, screenWidth, screenHeight);
         }

         this.openingAnimation.update();
         float mouseX = (float)client.mouse.getScaledX(client.getWindow()) * inputScale();
         float mouseY = (float)client.mouse.getScaledY(client.getWindow()) * inputScale();
         this.hoveredSlot = interactive && !this.closing ? this.hoveredAt(mouseX, mouseY, centerX, centerY) : -1;
         float openingProgress = this.openingAnimation.floatValue();
         if (!(openingProgress <= (0.004F))) {
            float animatedRadius = this.radius * ((0.88F) + (0.12F) * openingProgress);
            float wheelScale = animatedRadius / (92.0F);
            float sectorDegrees = (360.0F) / this.slots.length;

            for (int slotIndex = 0; slotIndex < this.slots.length; slotIndex++) {
               this.slotHoverAnimations[slotIndex].update();
               this.slotHoverAnimations[slotIndex].animateTo(slotIndex == this.hoveredSlot ? 1.0 : 0.0, (0.18D), Easings.QUAD_OUT);
               float hoverProgress = this.slotHoverAnimations[slotIndex].floatValue();
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
                  this.sliceColor(hoverProgress),
                  (0.8F) * (1.0F - (0.28F) * hoverProgress),
                  openingProgress
               );
               float iconX = centerX + (float)Math.cos(middleRadians) * animatedRadius * (0.62F);
               float iconY = centerY + (float)Math.sin(middleRadians) * animatedRadius * (0.62F);
               this.drawSlot(slotIndex, iconX, iconY, wheelScale, openingProgress, hoverProgress);
               HudRenderContext.popTransform();
            }

            if (this.hoveredSlot != -1 && !this.slots[this.hoveredSlot].isEmpty()) {
               String label = this.trimmedName(this.hoveredSlot);
               GuiTextRenderer.drawCentered(
                  GuiFont.SEMI_BOLD, label, centerX, centerY + animatedRadius + (6.0F), (6.0F), GuiVisuals.multiplyAlpha(HudPanelRenderer.primaryTextColor(1.0F), openingProgress)
               );
            }

            Render2D.end();
         }
      }
   }

   @Override
   public boolean renderClosing(DrawContext context) {
      this.drawWheel(context, false);
      return this.openingAnimation.floatValue() > (0.01F);
   }

   public void startClosing() {
      if (!this.closing) {
         this.closing = true;
         this.hoveredSlot = -1;
         this.openingAnimation.animateTo(0.0, (0.22D), Easings.QUAD_OUT);
         ClosingOverlayRegistry.add(this);
         if (this.client != null && this.client.currentScreen == this) {
            this.client.setScreen(null);
         }
      }
   }

   public SwapWheel(ItemStack[] slots, IntConsumer onChooseSlot) {
      super(Text.literal(("SwapWheel")));
      this.radius = (92.0F);
      this.slots = slots;
      this.onChooseSlot = onChooseSlot;
      this.slotHoverAnimations = new TweenAnimation[slots.length];

      for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
         this.slotHoverAnimations[slotIndex] = new TweenAnimation();
      }
   }

   @Override
   protected void init() {
      ClosingOverlayRegistry.removeType(SwapWheel.class);
      if (!this.closing) {
         this.openingAnimation.animateTo(1.0, (0.22D), Easings.QUAD_OUT);
      }
   }

   private String trimmedName(int slotIndex) {
      String name = StyledTextParser.stripFormatting(this.slots[slotIndex].getName().getString());
      return name.length() <= 16 ? name : name.substring(0, 15) + "…";
   }

   public boolean pickerOpen() {
      return false;
   }

   private void drawSlot(int slotIndex, float centerX, float centerY, float scale, float alpha, float hoverProgress) {
      ItemStack stack = this.slots[slotIndex];
      if (stack.isEmpty()) {
         float placeholderSize = (20.0F) * scale;
         GuiTextRenderer.drawCentered(
            GuiFont.BOLD, ("+"), centerX, centerY - placeholderSize / 2.0F, placeholderSize, GuiVisuals.multiplyAlpha(HudPanelRenderer.secondaryTextColor((0.4F) + (0.3F) * hoverProgress), alpha)
         );
      } else {
         float iconSize = (20.0F) * scale;
         float iconX = Math.round((centerX - iconSize / 2.0F) * 2.0F) / 2.0F;
         float iconY = Math.round((centerY - iconSize / 2.0F) * 2.0F) / 2.0F;
         GuiItemRenderer.render(stack, iconX, iconY, iconSize, ItemRenderOptions.noDecorations(alpha));
      }
   }
}
