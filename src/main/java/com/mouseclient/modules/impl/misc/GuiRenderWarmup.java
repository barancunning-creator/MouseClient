package com.mouseclient.modules.impl.misc;

import com.mouseclient.gui.Render2D;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.util.time.ClickGuiTheme;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;

public final class GuiRenderWarmup {
   
   private static final GuiRenderWarmup INSTANCE = new GuiRenderWarmup();
   private static volatile int remainingFrames = 5;
   private static volatile boolean fontsMeasured;

   private final EventListener<HudRenderEvent> hudRenderListener = event -> {
      if (remainingFrames > 0) {
         remainingFrames--;
         if (!fontsMeasured) {
            warmFonts();
         }

         try {
            this.warmShapePipelines();
         } catch (Throwable shapeFailure) {
         }

         try {
            this.warmGuiTextures();
         } catch (Throwable textureFailure) {
         }

         if (remainingFrames <= 0) {
            EventBus.getInstance().unsubscribe(INSTANCE);
         }
      }
   };

   public static void warmFonts() {
      String sampleText = ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 .,:;!?-_()[]{}<>/\\|@#$%^&*+=\"'`~АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя");

      for (GuiFont font : GuiFont.values()) {
         try {
            GuiTextRenderer.getWidth(font, font == GuiFont.ICONS ? ("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789αβγδεζηθικλμνξ") : sampleText, (8.0F));
         } catch (Throwable fontFailure) {
         }
      }

      fontsMeasured = true;
   }

   private void warmShapePipelines() {
      Render2D.fillRoundedRectangle((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F, (33554431));
      Render2D.fillRoundedGradient((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F, (33554431), (33554431), (33554431), (33554431));
      Render2D.drawOutline((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F, (0.5F), (33554431));
      Render2D.drawOutlineRanges((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F, (0.5F), (33554431));
      Render2D.fillCircle((-4000.0F), 1.0F, 1.0F, (33554431));
      Render2D.strokeCircle((-4000.0F), 1.0F, 1.0F, (0.5F), (33554431));
      Render2D.drawBracket((-4000.0F), 0.0F, 2.0F, 1.0F, (0.25F), 2, (33554431));
      Render2D.drawBlob((-4000.0F), 0.0F, (4.0F), (4.0F), 2.0F, 2.0F, 2.0F, 2.0F, (3.0F), 1.0F, 0.0F, 0.0F, 1.0F, (33554431));
      Render2D.drawHuePicker((-4000.0F), 0.0F, 2.0F, 2.0F, (0.003921569F));
      Render2D.drawAlphaPicker((-4000.0F), 0.0F, 2.0F, 2.0F, (33554431), (0.003921569F));
      Render2D.drawBlur((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F, (8.0F));
      Render2D.drawSharedBlur((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F, (8.0F), 1.0F, (33554431));
      Render2D.drawGuiBlur((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F);
      Render2D.drawGlassEffect((-4000.0F), 0.0F, 2.0F, 2.0F, (0.5F), (0.5F), 1.0F, (33554431));
      Render2D.drawGlassOutlineEffect((-4000.0F), 0.0F, 2.0F, 2.0F, (0.5F), (0.5F), 1.0F, (0.5F), (33554431));
      Render2D.drawSoftGlow((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F, (33554431), (0.5F));
      Render2D.drawSoftGlowPoint((-4000.0F), 1.0F, (33554431), (0.5F), (0.5F));
      Render2D.drawRipple((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F, 1.0F, 1.0F, (0.5F), (33554431), (33554431));
      Render2D.drawIconPattern((-4000.0F), 0.0F, 2.0F, 2.0F, 1.0F, (33554431), (33554431), ("icons"), ("a"), 1.0F, 2.0F, 2.0F, 0.0F);
      Render2D.drawClippedMsdfText(
         GuiFont.SEMI_BOLD.getFontKey(), ("Ag"), (-4000.0F), 0.0F, (6.0F), (0.5F), (0.35F), (0.5F), (0.003921569F)
      );

      for (GuiFont font : GuiFont.values()) {
         GuiTextRenderer.draw(font, font == GuiFont.ICONS ? ("aM") : ("Ag"), (-4000.0F), 0.0F, (6.0F), (33554431));
      }
   }

   private GuiRenderWarmup() {
   }

   public static void restart() {
      remainingFrames = 5;
      fontsMeasured = false;
      EventBus.getInstance().unsubscribe(INSTANCE);
      EventBus.getInstance().subscribe(INSTANCE);
   }

   private void warmGuiTextures() {
      for (ModuleCategory category : ModuleCategory.values()) {
         Render2D.drawRoundedImage(ClickGuiTheme.categoryIcon(category), (-4000.0F), 0.0F, 2.0F, 2.0F, 0.0F, (33554431));
      }

      for (String texturePath : new String[]{("assets/mouseclient/textures/gui/clickgui/search.png"), ("assets/mouseclient/textures/gui/clickgui/chev.png"), ("assets/mouseclient/textures/gui/clickgui/chevright.png"), ("assets/mouseclient/textures/gui/clickgui/check.png"), ("assets/mouseclient/textures/gui/clickgui/picker.png"), ("assets/mouseclient/textures/gui/clickgui/bindkey.png")}) {
         Render2D.drawRoundedImage(texturePath, (-4000.0F), 0.0F, 2.0F, 2.0F, 0.0F, (33554431));
      }
   }

   public static void subscribe() {
      EventBus.getInstance().subscribe(INSTANCE);
   }
}
