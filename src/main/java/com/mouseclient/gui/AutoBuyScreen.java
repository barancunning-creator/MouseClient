package com.mouseclient.gui;

import com.mouseclient.internal.GuiScale;
import com.mouseclient.util.time.AutoBuyGuiController;
import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.GuiVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class AutoBuyScreen extends Screen {
   private static AutoBuyScreen INSTANCE;
   private ClickGuiContext context = new ClickGuiContext();
   private long lastFrameAtMillis = System.currentTimeMillis();

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public boolean charTyped(CharInput input) {
      if (this.context.focusedText == null) {
         return super.charTyped(input);
      }

      if (input.codepoint() >= 32) {
         this.context.focusedText.insertText(String.valueOf((char)input.codepoint()));
      }

      return true;
   }

   @Override
   public void renderBackground(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
   }

   private AutoBuyScreen() {
      super(Text.literal(("маусклиент")));
   }

   @Override
   public boolean mouseReleased(Click click) {
      this.context.draggedSlider = null;
      return super.mouseReleased(click);
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubled) {
      if (click.button() == 0) {
         this.context.focusedText = null;
      }

      for (int regionIndex = this.context.clickRegions.size() - 1; regionIndex >= this.context.modalClickStart; regionIndex--) {
         if (GuiVisuals.containsPoint(
            (float)click.x() * inputScale(),
            (float)click.y() * inputScale(),
            this.context.clickRegions.get(regionIndex).x(),
            this.context.clickRegions.get(regionIndex).y(),
            this.context.clickRegions.get(regionIndex).getWidth(),
            this.context.clickRegions.get(regionIndex).getHeight()
         )) {
            if (click.button() == 0) {
               this.context.clickRegions.get(regionIndex).action().run();
               return true;
            }

            if (click.button() == 1 && this.context.clickRegions.get(regionIndex).rightAction() != null) {
               this.context.clickRegions.get(regionIndex).rightAction().run();
               return true;
            }
         }
      }

      return true;
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      return AutoBuyGuiController.getInstance().mouseScrolled((float)mouseX * inputScale(), (float)mouseY * inputScale(), verticalAmount, this.context.scale);
   }

   private static float inputScale() {
      return GuiScale.getWindowScale() / GuiScale.getBaseScale();
   }

   @Override
   public void render(DrawContext drawContext, int mouseX, int mouseY, float deltaTicks) {
      this.context
         .beginFrame(
            drawContext,
            Math.min(1.0F, Math.min(this.width * inputScale() / (640.0F), this.height * inputScale() / (360.0F))),
            mouseX * inputScale(),
            mouseY * inputScale()
         );
      Render2D.begin(drawContext);
      HudRenderContext.setDrawContext(drawContext);
      AutoBuyGuiController.getInstance()
         .render(
            this.context,
            this.width * inputScale(),
            this.height * inputScale(),
            AutoBuyGuiController.getInstance().openingProgress(),
            Math.min((0.05F), (float)(System.currentTimeMillis() - this.lastFrameAtMillis) / (1000.0F))
         );
      if (this.context.draggedSlider != null && GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(), 0) != 1) {
         this.context.draggedSlider = null;
      } else if (this.context.draggedSlider != null && this.context.sliderBounds.get(this.context.draggedSlider) != null) {
         this.context.draggedSlider.setNormalizedValue(ClickGuiContext.clamp((mouseX * inputScale() - this.context.sliderBounds.get(this.context.draggedSlider)[0]) / this.context.sliderBounds.get(this.context.draggedSlider)[1], 0.0F, 1.0F));
      }

      this.lastFrameAtMillis = System.currentTimeMillis();
      Render2D.end();
      if (!AutoBuyGuiController.getInstance().isOpen() && AutoBuyGuiController.getInstance().openingProgress() <= (0.003F)) {
         MinecraftClient.getInstance().setScreen(null);
      }
   }

   public static AutoBuyScreen instance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoBuyScreen();
      }

      return INSTANCE;
   }

   @Override
   public boolean keyPressed(KeyInput keyInput) {
      if (keyInput.key() == 256) {
         if (!AutoBuyGuiController.getInstance().handleEscape()) {
            AutoBuyGuiController.getInstance().close();
         }

         return true;
      } else {
         if (this.context.focusedText == null) {
            return super.keyPressed(keyInput);
         }

         if (keyInput.key() == 259) {
            this.context.focusedText.backspace();
         } else if (keyInput.key() == 261) {
            this.context.focusedText.deleteForward();
         } else if (keyInput.key() == 263) {
            this.context.focusedText.moveCursor(-1, (keyInput.modifiers() & 1) != 0);
         } else if (keyInput.key() == 262) {
            this.context.focusedText.moveCursor(1, (keyInput.modifiers() & 1) != 0);
         } else if (keyInput.key() == 268) {
            this.context.focusedText.moveToStart((keyInput.modifiers() & 1) != 0);
         } else if (keyInput.key() == 269) {
            this.context.focusedText.moveToEnd((keyInput.modifiers() & 1) != 0);
         } else if (keyInput.key() == 65 && (keyInput.modifiers() & 2) != 0) {
            this.context.focusedText.selectAll();
         } else if (keyInput.key() == 257) {
            this.context.focusedText = null;
         }

         return true;
      }
   }
}
