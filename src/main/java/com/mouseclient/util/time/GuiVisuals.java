package com.mouseclient.util.time;

import com.mouseclient.gui.OutlineColorRange;
import com.mouseclient.gui.Render2D;
import com.mouseclient.render.pipeline.Outline360Spec;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class GuiVisuals {
   public static int white = new Color(255, 255, 255, 255).getRGB();
   public static int brightTextColor = new Color(255, 255, 255, 184).getRGB();
   public static int mutedTextColor = new Color(255, 255, 255, 122).getRGB();
   public static int dimTextColor = new Color(255, 255, 255, 61).getRGB();
   public static int hoverFillColor = new Color(255, 255, 255, 26).getRGB();
   public static int faintDividerColor = new Color(255, 255, 255, 10).getRGB();
   public static int accentColor = new Color(71, 123, 255, 255).getRGB();
   public static int accentLightColor = new Color(123, 163, 255, 255).getRGB();
   public static int accentGradientColor = new Color(97, 142, 255, 255).getRGB();
   public static int buttonTextColor = new Color(255, 255, 255, 255).getRGB();
   public static int rgb15_14_17 = new Color(15, 14, 17, 255).getRGB();
   public static int whiteAlias = new Color(255, 255, 255, 255).getRGB();
   public static int speedOptionBackgroundColor = new Color(19, 18, 22, 255).getRGB();
   public static int rgb26_25_31 = new Color(26, 25, 31, 255).getRGB();
   public static int rgb21_20_23 = new Color(21, 20, 23, 255).getRGB();
   public static int accentAlpha20 = new Color(71, 123, 255, 20).getRGB();
   public static int panelOutlineColor = new Color(255, 255, 255, 5).getRGB();
   public static int focusedFieldOutlineColor = new Color(255, 255, 255, 20).getRGB();
   public static int whiteAlpha10Alias = new Color(255, 255, 255, 10).getRGB();
   public static int whiteAlpha5Alias = new Color(255, 255, 255, 5).getRGB();
   public static int whiteAlpha20Alias = new Color(255, 255, 255, 20).getRGB();
   public static final int unavailableCosmeticColor = new Color(248, 197, 111, 255).getRGB();
   public static final int clearCosmeticHoverColor = new Color(231, 130, 137, 255).getRGB();
   public static int whiteAlpha8 = new Color(255, 255, 255, 8).getRGB();
   public static int whiteAlpha20ExtraAlias = new Color(255, 255, 255, 20).getRGB();
   public static int whiteAlpha6 = new Color(255, 255, 255, 6).getRGB();
   public static int whiteAlpha26Alias = new Color(255, 255, 255, 26).getRGB();
   public static int menuRowTextColor = new Color(255, 255, 255, 178).getRGB();
   public static int menuDescriptionColor = new Color(255, 255, 255, 128).getRGB();
   public static int placeholderColor = new Color(255, 255, 255, 102).getRGB();
   public static int listDividerColor = new Color(255, 255, 255, 38).getRGB();
   public static int whiteAlpha8Alias = new Color(255, 255, 255, 8).getRGB();
   public static int menuFieldOutlineColor = new Color(255, 255, 255, 20).getRGB();
   public static int accentOutlineColor = new Color(102, 144, 255, 255).getRGB();
   public static int dangerColor = new Color(255, 81, 81, 255).getRGB();
   public static final String MENU_BACKGROUND = "assets/mouseclient/textures/gui/mainmenu/background/menu.png";
   public static final String MENU_BLUR_BACKGROUND = "assets/mouseclient/textures/gui/mainmenu/background/menu_blur.png";
   public static final String LOADING_BACKGROUND = "assets/mouseclient/textures/gui/mainmenu/background/loading.png";
   public static final String WELCOME_BACKGROUND = "assets/mouseclient/textures/gui/mainmenu/background/welcome.png";
   public static final String LOGO_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/logo.png";
   public static final String ARROW_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/arrow.png";
   public static final String PERSON_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/person.png";
   public static final String TELEGRAM_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/telegram.png";
   public static final String DISCORD_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/discord.png";
   public static final String CHEVRON_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/chevron.png";
   public static final String EXIT_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/exit.png";
   public static final String SETTINGS_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/settings.png";
   public static final String ACCOUNTS_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/accounts.png";
   public static final String SINGLEPLAYER_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/singleplayer.png";
   public static final String BELL_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/bell.png";
   public static final String PATTERN_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/pattern.png";
   public static final String SEARCH_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/search.png";
   public static final String BACK_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/back.png";
   public static final String DICE_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/dice.png";
   public static final String PLUS_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/plus.png";
   public static final String USER_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/user.png";
   public static final String DELETE_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/delete.png";
   public static final String RENAME_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/rename.png";
   public static final String GRASS_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/grass.png";
   public static final String WORDMARK_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/mouseclient_serif.png";
   public static final String COSMETICS_TEXTURE = "assets/mouseclient/textures/gui/mainmenu/icons/cos_menu.png";
   public static boolean loadingSceneEnabled;
   public static final String ICON_FONT = "icons";
   public static final String GLYPH_ALPHA = "α";
   public static final String GLYPH_BETA = "β";
   public static final String GLYPH_GAMMA = "γ";
   public static final String GLYPH_DELTA = "δ";
   public static final String GLYPH_EPSILON = "ε";
   public static final String GLYPH_LOWER_B = "b";
   public static final String GLYPH_DIGIT_9 = "9";
   public static final String GLYPH_UPPER_B = "B";
   public static final String GLYPH_LOWER_E = "e";
   public static final String GLYPH_LOWER_D = "d";
   public static final String GLYPH_ZETA = "ζ";
   public static final String GLYPH_UPPER_E = "E";
   public static final String GLYPH_UPPER_S = "S";
   public static final String GLYPH_UPPER_W = "W";
   public static final String GLYPH_LAMBDA = "λ";
   public static final String GLYPH_MU = "μ";
   public static final String GLYPH_THETA = "θ";
   public static final String GLYPH_ETA = "η";
   public static final String GLYPH_UPPER_N = "N";
   public static final String GLYPH_UPPER_O = "O";
   public static final String GLYPH_UPPER_P = "P";
   public static final String GLYPH_IOTA = "ι";
   public static final String GLYPH_UPPER_R = "R";

   public static void drawOutlinedPanel(float x, float y, float width, float height, float radius, int fillColor, int outlineColor) {
      Render2D.fillRoundedRectangle(x, y, width, height, radius, fillColor);
      Render2D.drawOutline(x, y, width, height, radius, (0.5F), outlineColor);
   }

   public static void drawLoadingSpinner(float centerX, float centerY, float radius, float thickness, int color) {
      long nowMillis = System.currentTimeMillis();
      float rotationDegrees = -((float)(nowMillis % (1100L)) / (1100.0F) * (360.0F));
      float sweepProgress = (float)(nowMillis % (1800L)) / (1800.0F);
      sweepProgress = sweepProgress < (0.5F) ? easeOutQuad(sweepProgress * 2.0F) : 1.0F - easeOutQuad((sweepProgress - (0.5F)) * 2.0F);
      float sweepDegrees = (55.0F) + (230.0F) * sweepProgress;
      Render2D.drawOutlineRanges(
         new Outline360Spec(
               centerX - radius,
               centerY - radius,
               radius * 2.0F,
               radius * 2.0F,
               radius,
               thickness,
               Render2D.applyAlpha(multiplyAlpha(white, (0.051F))),
               List.of(OutlineColorRange.of(rotationDegrees, rotationDegrees + sweepDegrees, Render2D.applyAlpha(color)))
            )
            .withBlendDegrees(Math.min((48.0F), sweepDegrees * (0.42F)))
      );
   }

   public static List<String> wrapWords(GuiFont font, String text, float fontSize, float maxWidth) {
      ArrayList lines = new ArrayList();
      StringBuilder textBuilder = new StringBuilder();

      for (String word : text.split((" "))) {
         String candidateLine = textBuilder.length() == 0 ? word : textBuilder + " " + word;
         if (textBuilder.length() != 0 && !(GuiTextRenderer.getWidth(font, candidateLine, fontSize) <= maxWidth)) {
            lines.add(textBuilder.toString());
            textBuilder = new StringBuilder(word);
         } else {
            textBuilder = new StringBuilder(candidateLine);
         }
      }

      if (textBuilder.length() > 0) {
         lines.add(textBuilder.toString());
      }

      return lines;
   }

   public static float easeOutBack(float progress) {
      progress = Math.max(0.0F, Math.min(1.0F, progress));
      return 1.0F + (2.70158F) * (progress - 1.0F) * (progress - 1.0F) * (progress - 1.0F) + (1.70158F) * (progress - 1.0F) * (progress - 1.0F);
   }

   public static float easeOutQuad(float progress) {
      progress = Math.max(0.0F, Math.min(1.0F, progress));
      return 1.0F - (1.0F - progress) * (1.0F - progress);
   }

   public static void drawCenteredImage(String texture, float centerX, float centerY, float width, float height, int color) {
      Render2D.drawRoundedImage(texture, centerX - width / 2.0F, centerY - height / 2.0F, width, height, 0.0F, color);
   }

   public static int multiplyAlpha(int color, float alpha) {
      return Math.round((color >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha))) << 24 | color & (16777215);
   }

   private GuiVisuals() {
   }

   public static void drawLoadingScene(long shownAtMillis, float screenWidth, float screenHeight) {
      float scale = Math.min(screenWidth / (960.0F), screenHeight / (540.0F));
      float top = (screenHeight - (540.0F) * scale) / 2.0F;
      long nowMillis = System.currentTimeMillis();
      float spinnerProgress = easeOutBack(elapsedProgress(shownAtMillis, 0L, (520L)));
      float centerX = screenWidth / 2.0F;
      float spinnerY = top + (229.0F) * scale;
      Render2D.pushAlpha(Math.min(1.0F, spinnerProgress * 2.0F));
      drawLoadingSpinner(centerX, spinnerY, (28.5F) * scale * spinnerProgress, (2.7F) * scale * Math.max((0.001F), spinnerProgress), accentColor);
      float logoPulse = 1.0F + (0.045F) * (float)Math.sin((float)(nowMillis % (1600L)) / (1600.0F) * (3.141592653589793D) * (2.0D));
      drawCenteredImage(("assets/mouseclient/textures/gui/mainmenu/icons/logo.png"), centerX, spinnerY, (15.5F) * scale * spinnerProgress * logoPulse, (12.0F) * scale * spinnerProgress * logoPulse, accentColor);
      Render2D.popAlpha();
      float titleProgress = easeOutQuad(elapsedProgress(shownAtMillis, (140L), (480L)));
      float titleWidth = GuiTextRenderer.getWidth(GuiFont.SEMI, ("Запускаем маусклиент..."), (15.0F) * scale);
      Render2D.pushAlpha(titleProgress);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         "Запускаем маусклиент" + (".").repeat(1 + (int)(nowMillis / (450L) % (3L))),
         centerX - titleWidth / 2.0F,
         top + (283.0F) * scale + (1.0F - titleProgress) * (10.0F) * scale,
         (15.0F) * scale,
         white
      );
      Render2D.popAlpha();
      float descriptionProgress = easeOutQuad(elapsedProgress(shownAtMillis, (240L), (480L)));
      Render2D.pushAlpha(descriptionProgress);
      float lineY = top + (313.0F) * scale + (1.0F - descriptionProgress) * (10.0F) * scale;

      for (String line : wrapWords(GuiFont.SEMI, ("Загружаем необходимые компоненты и применяем ваши настройки. Ещё немного - и маусклиент будет готов к работе."), (7.5F) * scale, (190.0F) * scale)) {
         GuiTextRenderer.draw(
            GuiFont.SEMI, line, centerX - GuiTextRenderer.getWidth(GuiFont.SEMI, line, (7.5F) * scale) / 2.0F, lineY, (7.5F) * scale, menuDescriptionColor
         );
         lineY += (9.0F) * scale;
      }

      Render2D.popAlpha();
   }

   public static boolean containsPoint(float pointX, float pointY, float x, float y, float width, float height) {
      return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
   }

   public static void drawCenteredMsdfText(String font, String text, float centerX, float centerY, float fontSize, int color) {
      float[] bounds = Render2D.getMsdfTextBounds(font, text, fontSize);
      Render2D.drawMsdfText(font, text, centerX - (bounds[0] + bounds[2]) / 2.0F, centerY - (bounds[1] + bounds[3]) / 2.0F + 1.0F, fontSize, color);
   }

   public static int lerpColor(int startColor, int endColor, float progress) {
      float blendProgress = Math.max(0.0F, Math.min(1.0F, progress));
      return lerpChannel(startColor >>> 24 & 0xFF, endColor >>> 24 & 0xFF, blendProgress) << 24
         | lerpChannel(startColor >>> 16 & 0xFF, endColor >>> 16 & 0xFF, blendProgress) << 16
         | lerpChannel(startColor >>> 8 & 0xFF, endColor >>> 8 & 0xFF, blendProgress) << 8
         | lerpChannel(startColor & 0xFF, endColor & 0xFF, blendProgress);
   }

   public static void drawCoverBackground(String texture, float screenWidth, float screenHeight, float alpha) {
      float coverScale = Math.max(screenWidth / (960.0F), screenHeight / (540.0F));
      Render2D.drawRoundedImage(
         texture,
         (screenWidth - (960.0F) * coverScale) / 2.0F,
         (screenHeight - (540.0F) * coverScale) / 2.0F,
         (960.0F) * coverScale,
         (540.0F) * coverScale,
         0.0F,
         multiplyAlpha(white, alpha)
      );
   }

   private static int lerpChannel(int startChannel, int endChannel, float progress) {
      return Math.round(startChannel + (endChannel - startChannel) * progress);
   }

   public static void drawCenteredIcon(String glyph, float centerX, float centerY, float size, int color) {
      drawCenteredMsdfText(("icons"), glyph, centerX, centerY, size, color);
   }

   public static float elapsedProgress(long startedAtMillis, long delayMillis, long durationMillis) {
      return Math.max(0.0F, Math.min(1.0F, (float)(System.currentTimeMillis() - startedAtMillis - delayMillis) / (float)durationMillis));
   }
}
