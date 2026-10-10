package com.mouseclient.gui;

import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.player.InputBinding;
import com.mouseclient.util.time.PotionPresetStore;
import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.GuiVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public final class PotionCombinerScreen extends Screen {
   private static PotionCombinerScreen INSTANCE;
   private ClickGuiContext ctx = new ClickGuiContext();
   private long lastFrameTime = System.currentTimeMillis();

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
      this.ctx
         .beginFrame(
            context,
            Math.min((1.66F), Math.min(this.width * coordinateScale() / (640.0F), this.height * coordinateScale() / (360.0F)) * (1.66F)),
            mouseX * coordinateScale(),
            mouseY * coordinateScale()
         );
      Render2D.begin(context);
      HudRenderContext.setDrawContext(context);
      PotionPresetEditor.getInstance()
         .render(
            this.ctx,
            this.width * coordinateScale(),
            this.height * coordinateScale(),
            PotionPresetEditor.getInstance().visibilityProgress(),
            Math.min((0.05F), (float)(System.currentTimeMillis() - this.lastFrameTime) / (1000.0F))
         );
      this.lastFrameTime = System.currentTimeMillis();
      Render2D.end();
      if (!PotionPresetEditor.getInstance().isOpen() && PotionPresetEditor.getInstance().visibilityProgress() <= (0.003F)) {
         MinecraftClient.getInstance().setScreen(null);
      }
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   public static PotionCombinerScreen instance() {
      if (INSTANCE == null) {
         INSTANCE = new PotionCombinerScreen();
      }

      return INSTANCE;
   }

   private PotionCombinerScreen() {
      super(Text.literal(("маусклиент")));
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float tickDelta) {
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalScroll, double verticalScroll) {
      return PotionPresetEditor.getInstance().mouseScrolled((float)mouseX * coordinateScale(), (float)mouseY * coordinateScale(), verticalScroll, this.ctx.scale);
   }

   @Override
   public boolean charTyped(CharInput charInput) {
      if (this.ctx.focusedText == null) {
         return super.charTyped(charInput);
      }

      if (charInput.codepoint() >= 32) {
         this.ctx.focusedText.insertText(String.valueOf((char)charInput.codepoint()));
      }

      return true;
   }

   @Override
   public boolean keyPressed(KeyInput keyInput) {
      if (this.ctx.bindingSetting != null) {
         this.ctx.bindingSetting.withKeyCode(keyInput.key() != 256 && keyInput.key() != 261 ? keyInput.key() : -1);
         this.ctx.bindingSetting = null;
         PotionPresetStore.getInstance().save();
         return true;
      }

      if (keyInput.key() == 256) {
         if (!PotionPresetEditor.getInstance().handleEscape()) {
            PotionPresetEditor.getInstance().close();
         }

         return true;
      } else {
         if (this.ctx.focusedText == null) {
            return super.keyPressed(keyInput);
         }

         if (keyInput.key() == 259) {
            this.ctx.focusedText.backspace();
         } else if (keyInput.key() == 261) {
            this.ctx.focusedText.deleteForward();
         } else if (keyInput.key() == 263) {
            this.ctx.focusedText.moveCursor(-1, (keyInput.modifiers() & 1) != 0);
         } else if (keyInput.key() == 262) {
            this.ctx.focusedText.moveCursor(1, (keyInput.modifiers() & 1) != 0);
         } else if (keyInput.key() == 268) {
            this.ctx.focusedText.moveToStart((keyInput.modifiers() & 1) != 0);
         } else if (keyInput.key() == 269) {
            this.ctx.focusedText.moveToEnd((keyInput.modifiers() & 1) != 0);
         } else if (keyInput.key() == 65 && (keyInput.modifiers() & 2) != 0) {
            this.ctx.focusedText.selectAll();
         } else if (keyInput.key() == 257) {
            this.ctx.focusedText = null;
         }

         return true;
      }
   }

   private static float coordinateScale() {
      return GuiScale.getWindowScale() / GuiScale.getBaseScale();
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubled) {
      if (this.ctx.bindingSetting != null) {
         if (click.button() != 0) {
            this.ctx.bindingSetting.setBinding(InputBinding.mouse(click.button()));
         }

         this.ctx.bindingSetting = null;
         PotionPresetStore.getInstance().save();
         if (click.button() != 0) {
            return true;
         }
      }

      if (click.button() == 0) {
         this.ctx.focusedText = null;
      }

      for (int regionIndex = this.ctx.clickRegions.size() - 1; regionIndex >= this.ctx.modalClickStart; regionIndex--) {
         if (GuiVisuals.containsPoint(
            (float)click.x() * coordinateScale(),
            (float)click.y() * coordinateScale(),
            this.ctx.clickRegions.get(regionIndex).x(),
            this.ctx.clickRegions.get(regionIndex).y(),
            this.ctx.clickRegions.get(regionIndex).getWidth(),
            this.ctx.clickRegions.get(regionIndex).getHeight()
         )) {
            if (click.button() == 0) {
               this.ctx.clickRegions.get(regionIndex).action().run();
               return true;
            }

            if (click.button() == 1 && this.ctx.clickRegions.get(regionIndex).rightAction() != null) {
               this.ctx.clickRegions.get(regionIndex).rightAction().run();
               return true;
            }
         }
      }

      return true;
   }
}
