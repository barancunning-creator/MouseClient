package com.mouseclient.gui;

import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.RoundedClipStack;
import com.mouseclient.internal.StyledTextParser;
import com.mouseclient.mixin.DrawContextAccessor;
import com.mouseclient.modules.impl.misc.NameProtect;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.avatar.MsdfFontRegistry;
import com.mouseclient.render.avatar.MsdfFont;
import com.mouseclient.render.pipeline.ArcRenderer;
import com.mouseclient.render.pipeline.GuiLayerTracker;
import com.mouseclient.render.pipeline.BlobRenderer;
import com.mouseclient.render.pipeline.BracketRenderer;
import com.mouseclient.render.pipeline.CircleRenderer;
import com.mouseclient.render.pipeline.AtlasTextRenderer;
import com.mouseclient.render.pipeline.GuiBlurRenderer;
import com.mouseclient.render.pipeline.MouseClientClientPalette;
import com.mouseclient.render.pipeline.GuiGlowRenderer;
import com.mouseclient.render.pipeline.DitherRenderer;
import com.mouseclient.render.pipeline.GlassOutlineRenderer;
import com.mouseclient.render.pipeline.GlassRenderer;
import com.mouseclient.render.pipeline.Glow;
import com.mouseclient.render.pipeline.ImageRenderer;
import com.mouseclient.render.pipeline.LineRenderer;
import com.mouseclient.render.pipeline.MenuGridRenderer;
import com.mouseclient.render.pipeline.Outline360Renderer;
import com.mouseclient.render.pipeline.OutlineRenderer;
import com.mouseclient.render.pipeline.ColorPickerRenderer;
import com.mouseclient.render.pipeline.EffectIconRenderer;
import com.mouseclient.render.pipeline.MsdfTextRenderer;
import com.mouseclient.render.pipeline.MsdfTextSpec;
import com.mouseclient.render.pipeline.EffectIconSpec;
import com.mouseclient.render.pipeline.ImageSpec;
import com.mouseclient.render.pipeline.MenuGridSpec;
import com.mouseclient.render.pipeline.DitherSpec;
import com.mouseclient.render.pipeline.AtlasTextSpec;
import com.mouseclient.render.pipeline.BlurSpecBuilder;
import com.mouseclient.render.pipeline.RectHalfIconRenderer;
import com.mouseclient.render.pipeline.IconPatternSpec;
import com.mouseclient.render.pipeline.RoundedRectangleRenderer;
import com.mouseclient.render.pipeline.RippleRenderer;
import com.mouseclient.render.pipeline.GlassShapeRenderer;
import com.mouseclient.render.pipeline.ShimmerRenderer;
import com.mouseclient.render.pipeline.SoftGlowRenderer;
import com.mouseclient.render.pipeline.WarpTextRenderer;
import com.mouseclient.render.pipeline.ArcSpec;
import com.mouseclient.render.pipeline.RoundedRectangleSpec;
import com.mouseclient.render.pipeline.OutlineSpec;
import com.mouseclient.render.pipeline.RippleSpec;
import com.mouseclient.render.pipeline.WarpTextSpec;
import com.mouseclient.render.pipeline.BlobSpec;
import com.mouseclient.render.pipeline.SoftGlowSpec;
import com.mouseclient.render.pipeline.GlassShapeSpec;
import com.mouseclient.render.pipeline.BlurSpec;
import com.mouseclient.render.pipeline.CircleSpec;
import com.mouseclient.render.pipeline.ColorPickerSpec;
import com.mouseclient.render.pipeline.BracketSpec;
import com.mouseclient.render.pipeline.GlassSpec;
import com.mouseclient.render.pipeline.Outline360Spec;
import com.mouseclient.render.pipeline.GlassOutlineSpec;
import com.mouseclient.render.pipeline.LineSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

public final class Render2D {
   private static boolean resourceReloaderRegistered;
   private static boolean frameActive;
   
   private static String OBFUSCATION_ALPHABET = ("∅∆∏∑√∞∫≈≠₡₣₤₦₩₫₭₮₱₲₴₵₸₹₺₼₽ƏƒƠƷǅǎǓǤǯǺȞȫȷʒѢѫѲҊҝҠӿḈḝṚẅẎẓẞΔΩμπ№™℮‰†‡ĐđĦħĿŀŁłŊŋŒœŦŧĲĳĸſǆǇǈǉǊǋǌǪǫǮǻǼǽǾǿȀȁȂȃȄȅȆȇȈȉȊȋȌȍȎȏȐȑȒȓȘșȚțȟȪȬȭȰȱȲȳəʹʻʾˆˇ˘˙˚˛˜˝ЀЂЃЄЅІЇЈЉЊЋЌЍЎЏѣѳѴѵҋҌҍҎҏҐґҒғҔҕҖҗҘҙҚқҜҡҢңҤҥҦҧҨҩҪҫҬҭҮүҰұҲҳҴҵҶҷҸҹҺһҼҽҾҿӀӁӂӃӄӆӇӈӉӊӋӌӍӎӏӔӕӘәӚӛӜӝӞӟӠӡӢӣӤӥӦӧӨөӪӫӬӭӮӯӰӱӲӳӴӵӶӷӸӹӺӻӼӽӾԯḉḌḍḎḏḔḕḖḗḜḠḡḤḥḪḫḮḯḶḷḺḻṂṃṄṅṆṇṈṉṌṍṎṏṐṑṒṓṛṞṟṠṡṢṣṤṥṦṧṨṩṬṭṮṯṸṹṺṻẀẁẂẃẄẏẒẗẠạẢảẤấẦầẨẩẪẫẬậẮắẰằẲẳẴẵẶặẸẹẺẻẼẽẾếỀềỂểỄễỆệỈỉỊịỌọỎỏỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỤụỦủỨứỪừỬửỮữỰựỲỳỴỵỶỷỸỹ⁰⁴⁵⁶⁷⁸⁹₀₁₂₃₄₅₆₇₈₉€₧ℓ′″⁄⁒•…アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン天地火水木金土日月山川風雨雷電光闇魔剣龍神鬼王力心目手口人大小中上下左右前後東西南北春夏秋冬星空海島城門道車馬鳥魚虎狼熊蛇竜獣霊魂死生愛憎戦争平和");
   private static final HashMap<String, String> obfuscationAlphabets = new HashMap<>();
   private static final ArrayDeque<Float> alphaStack = new ArrayDeque<>();
   private static float alpha = 1.0F;

   public static void drawText(String fontName, String text, float x, float y, float size, int color) {
      breakTextureBatches();
      if (text != null && text.indexOf(167) >= 0) {
         StringBuilder textBuilder = new StringBuilder();

         for (StyledTextParser.StyledTextRun styledRun : StyledTextParser.parseLegacy(text, color)) {
            String runText = styledRun.text().indexOf(167) >= 0 ? StyledTextParser.stripFormatting(styledRun.text()) : styledRun.text();
            if (!runText.isEmpty()) {
               if (styledRun.obfuscated()) {
                  drawObfuscatedText(fontName, runText, x + getTextWidth(fontName, textBuilder.toString(), size), y, size, styledRun.color() & (16777215) | color & (-16777216));
               } else {
                  drawProtectedText(fontName, runText, x + getTextWidth(fontName, textBuilder.toString(), size), y, size, styledRun.color() & (16777215) | color & (-16777216));
               }

               textBuilder.append(runText);
            }
         }
      } else {
         drawProtectedText(fontName, text, x, y, size, color);
      }
   }

   private static WarpTextRenderer warpTextRenderer() {
      return WarpTextRenderer.getInstance();
   }

   private static RectHalfIconRenderer iconPatternRenderer() {
      return RectHalfIconRenderer.getInstance();
   }

   public static void drawRotatedText(String fontName, String text, float x, float y, float size, int color, float rotationDegrees, float originX, float originY) {
      breakTextureBatches();
      text = NameProtect.getInstance().replaceNickname(text);
      String msdfFontName = resolveMsdfFontName(fontName);
      if (msdfFontName != null) {
         msdfTextRenderer().render(new MsdfTextSpec(msdfFontName, text, x, y, size, applyTextAlpha(color), rotationDegrees, originX, originY));
      } else {
         atlasTextRenderer()
            .render(
               new AtlasTextSpec(fontName, text, x, y, size, color, color, color, color, rotationDegrees, originX, originY, false, false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, true)
            );
      }
   }

   public static void drawOutlineRanges(
      float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, int color, List<OutlineColorRange> ranges
   ) {
      breakTextureBatches();
      color = applyAlpha(color);
      ranges = applyAlphaToRanges(ranges);
      outlineRangesRenderer().render(new Outline360Spec(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, color, ranges));
   }

   private static ImageRenderer imageRenderer() {
      return ImageRenderer.getInstance();
   }

   public static void drawSoftGlow(float x, float y, float width, float height, float radius, int color, float softness, float intensity) {
      drawSoftGlow(new SoftGlowSpec(x, y, width, height, radius, color, softness).withIntensity(intensity));
   }

   private static void drawProtectedText(String fontName, String text, float x, float y, float size, int color) {
      if (text != null && !NameProtect.getInstance().replaceNickname(text).equals(text)) {
         String protectedText = NameProtect.getInstance().replaceNickname(text);
         String replacementNickname = NameProtect.getInstance().nickname.getText();
         float penX = x;
         int textIndex = 0;

         while (textIndex < protectedText.length()) {
            int nicknameIndex = protectedText.indexOf(replacementNickname, textIndex);
            if (nicknameIndex < 0) {
               drawPlainText(fontName, protectedText.substring(textIndex), penX, y, size, color);
               return;
            }

            if (nicknameIndex > textIndex) {
               drawPlainText(fontName, protectedText.substring(textIndex, nicknameIndex), penX, y, size, color);
               penX += getTextWidth(fontName, protectedText.substring(textIndex, nicknameIndex), size);
            }

            for (int nicknameCharacterIndex = 0; nicknameCharacterIndex < replacementNickname.length(); nicknameCharacterIndex++) {
               drawPlainText(fontName, String.valueOf(replacementNickname.charAt(nicknameCharacterIndex)), penX, y, size, NameProtect.getInstance().nicknameColor(nicknameCharacterIndex, color));
               penX += getTextWidth(fontName, String.valueOf(replacementNickname.charAt(nicknameCharacterIndex)), size);
            }

            textIndex = nicknameIndex + replacementNickname.length();
         }
      } else {
         drawPlainText(fontName, text, x, y, size, color);
      }
   }

   public static void drawImageGradient(String path, float x, float y, float width, float height, int... colors) {
      int[] effectiveColors = new int[colors.length];

      for (int colorIndex = 0; colorIndex < colors.length; colorIndex++) {
         effectiveColors[colorIndex] = applyAlpha(colors[colorIndex]);
      }

      imageRenderer().render(new ImageSpec(path, x, y, width, height).withColors(effectiveColors));
   }

   private static GuiGlowRenderer glowRenderer() {
      return GuiGlowRenderer.getInstance();
   }

   public static void setSharedBlurLayer(int layer) {
      GuiBlurRenderer.setGlobalLayer(layer);
   }

   public static void popAlpha() {
      alpha = alphaStack.isEmpty() ? 1.0F : alphaStack.pop();
   }

   private static AtlasTextRenderer atlasTextRenderer() {
      return AtlasTextRenderer.getInstance();
   }

   public static void reloadTextures() {
      ImageRenderer.getInstance().invalidateTextures();
      MsdfTextRenderer.getInstance().invalidateTextures();
      EffectIconRenderer.getInstance().invalidateTextures();
      MsdfFontRegistry.clear();
      ClientInitializer.logger().info(("[Render2D] textures reloaded"));
   }

   private static BracketRenderer bracketRenderer() {
      return BracketRenderer.getInstance();
   }

   public static void drawRotatedGradientText(
      String fontName, String text, float x, float y, float size, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft, float rotationDegrees, float originX, float originY
   ) {
      breakTextureBatches();
      text = NameProtect.getInstance().replaceNickname(text);
      String msdfFontName = resolveMsdfFontName(fontName);
      if (msdfFontName != null) {
         msdfTextRenderer()
            .render(
               new MsdfTextSpec(msdfFontName, text, x, y, size, applyTextAlpha(colorTopLeft), applyTextAlpha(colorTopRight), applyTextAlpha(colorBottomRight), applyTextAlpha(colorBottomLeft), rotationDegrees, originX, originY)
            );
      } else {
         atlasTextRenderer()
            .render(
               new AtlasTextSpec(fontName, text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, rotationDegrees, originX, originY, false, false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, true)
            );
      }
   }

   public static float[] getMsdfTextBounds(String fontName, String text, float size) {
      return msdfTextRenderer().getInkBounds(fontName, text, size);
   }

   private static OutlineRenderer outlineRenderer() {
      return OutlineRenderer.getInstance();
   }

   public static void drawRipple(float x, float y, float width, float height, float radius, float centerX, float centerY, float rippleRadius, int sourceColor, int targetColor) {
      drawRipple(x, y, width, height, radius, (0.5F), centerX, centerY, rippleRadius, (25.0F), sourceColor, targetColor);
   }

   private Render2D() {
   }

   public static void drawRotatedImage(String path, float x, float y, float width, float height, float rotationDegrees, float originX, float originY, int color) {
      imageRenderer().render(new ImageSpec(path, x, y, width, height, applyAlpha(color)).withRotation(rotationDegrees, originX, originY));
   }

   public static void drawDashedLine(float startX, float startY, float endX, float endY, float thickness, int color, float dashLength, float gapLength, int maxDashes) {
      float deltaX = endX - startX;
      float deltaY = endY - startY;
      float length = (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY);
      if (!(length < (0.01F)) && color >>> 24 != 0 && !(thickness <= 0.0F)) {
         float directionX = deltaX / length;
         float directionY = deltaY / length;
         float dashPeriod = Math.max((0.5F), dashLength + gapLength);
         int dashCount = (int)Math.ceil(length / dashPeriod);
         if (maxDashes > 0 && dashCount > maxDashes) {
            dashCount = maxDashes;
         }

         for (int dashIndex = 0; dashIndex < dashCount; dashIndex++) {
            float startDistance = dashIndex * dashPeriod;
            float endDistance = Math.min(startDistance + dashLength, length);
            if (!(endDistance <= startDistance)) {
               drawLine(startX + directionX * startDistance, startY + directionY * startDistance, startX + directionX * endDistance, startY + directionY * endDistance, thickness, color);
            }
         }
      }
   }

   public static void drawClippedMsdfText(String fontName, String text, float x, float y, float size, int color, float progress, float halfWidth, float intensity, float opacity) {
      breakTextureBatches();
      msdfTextRenderer().renderShimmer(new MsdfTextSpec(fontName, text, x, y, size, color), progress, halfWidth, intensity, opacity);
   }

   public static void popScissor(DrawContext context) {
      ScissorStack.pop();
      if (context != null) {
         context.disableScissor();
      }
   }

   public static void drawSoftGlow(float x, float y, float width, float height, float radius, int color, float softness) {
      drawSoftGlow(new SoftGlowSpec(x, y, width, height, radius, color, softness));
   }

   private static DitherRenderer ditherRenderer() {
      return DitherRenderer.getInstance();
   }

   public static void strokeCircle(float centerX, float centerY, float radius, float thickness, int color) {
      breakTextureBatches();
      circleRenderer().render(new CircleSpec(centerX, centerY, radius, applyAlpha(color)).withThickness(thickness));
   }

   public static void drawGuiBlur(float x, float y, float width, float height, float radius, int color) {
      breakTextureBatches();
      blurRenderer().renderHeavyBlur(new BlurSpec(x, y, width, height, radius, 1.0F, (36.0F)).withColor(applyAlpha(color)));
   }

   public static void drawGlass(
      float x,
      float y,
      float width,
      float height,
      float[] radii,
      int color,
      float globalAlpha,
      float fresnelPower,
      int fresnelColor,
      float baseAlpha,
      boolean fresnelInvert,
      float fresnelMix,
      float distortStrength,
      float squirt,
      float z
   ) {
      breakTextureBatches();
      glassRenderer().render(new GlassSpec(x, y, width, height, radii, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z));
   }

   public static void drawIconPattern(
      float x,
      float y,
      float width,
      float height,
      float radiusTopLeft,
      float radiusTopRight,
      float radiusBottomRight,
      float radiusBottomLeft,
      int backgroundColor,
      int iconColor,
      String fontName,
      String icon,
      float iconSize,
      float spacingX,
      float spacingY,
      float paddingX,
      float paddingY,
      float jitterX,
      float jitterY,
      float rotationMaxDegrees,
      long seed
   ) {
      drawIconPattern(
         new IconPatternSpec(
            x,
            y,
            width,
            height,
            radiusTopLeft,
            radiusTopRight,
            radiusBottomRight,
            radiusBottomLeft,
            backgroundColor,
            backgroundColor,
            backgroundColor,
            backgroundColor,
            0.0F,
            fontName,
            icon,
            iconSize,
            iconColor,
            spacingX,
            spacingY,
            paddingX,
            paddingY,
            jitterX,
            jitterY,
            rotationMaxDegrees,
            seed
         )
      );
   }

   public static void drawGlow(Glow glow) {
      if (glow != null) {
         glowRenderer().render(glow);
      }
   }

   public static void drawGradientText(String fontName, String text, float x, float y, float size, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      breakTextureBatches();
      text = NameProtect.getInstance().replaceNickname(text);
      String msdfFontName = resolveMsdfFontName(fontName);
      if (msdfFontName != null) {
         msdfTextRenderer().render(new MsdfTextSpec(msdfFontName, text, x, y, size, applyTextAlpha(colorTopLeft), applyTextAlpha(colorTopRight), applyTextAlpha(colorBottomRight), applyTextAlpha(colorBottomLeft)));
      } else {
         atlasTextRenderer().render(new AtlasTextSpec(fontName, text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft));
      }
   }

   private static SoftGlowRenderer softGlowRenderer() {
      return SoftGlowRenderer.getInstance();
   }

   public static void drawOutline(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, int color) {
      breakTextureBatches();
      outlineRenderer().render(new OutlineSpec(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, applyAlpha(color)));
   }

   public static void drawGlassShape(GlassShapeSpec spec) {
      breakTextureBatches();
      glassShapeRenderer().render(spec);
   }

   public static void drawGlowingRotatedImage(String path, float x, float y, float width, float height, float rotationDegrees, float originX, float originY, int color) {
      imageRenderer().render(new ImageSpec(path, x, y, width, height, applyAlpha(color)).withRotation(rotationDegrees, originX, originY).withGlow());
   }

   public static void drawClippedMsdfText(String fontName, String text, float x, float y, float size, float progress, float halfWidth, float intensity, float opacity) {
      breakTextureBatches();
      msdfTextRenderer().renderShimmer(new MsdfTextSpec(fontName, text, x, y, size, -1), progress, halfWidth, intensity, opacity);
   }

   public static void drawGlassOutline(GlassOutlineSpec spec) {
      breakTextureBatches();
      glassOutlineRenderer().render(spec);
   }

   public static void drawBlur(float x, float y, float width, float height, float radius, float blurRadius) {
      drawBlur(x, y, width, height, radius, blurRadius, 1.0F, -1);
   }

   public static void drawNearestImageRegion(
      String path, float x, float y, float width, float height, float radius, float smoothness, float u0, float v0, float u1, float v1, int color
   ) {
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         imageRenderer()
            .render(
               new ImageSpec(path, x, y, width, height, radius, applyAlpha(color)).withSmoothness(smoothness).withUv(u0, v0, u1, v1).withNearest()
            );
      }
   }

   public static void pushScissor(DrawContext context, float x, float y, float width, float height) {
      if (context == null) {
         ScissorStack.push(x, y, width, height);
      } else {
         ScissorStack.pushTransformed(GuiScale.getScaledMatrices(context), x, y, width, height);
         context.enableScissor(
            GuiScale.toGuiPixels(x),
            GuiScale.toGuiPixels(y),
            GuiScale.toGuiPixels(x + width),
            GuiScale.toGuiPixels(y + height)
         );
      }
   }

   public static void drawLine(float startX, float startY, float endX, float endY, float thickness, int color) {
      lineRenderer().render(new LineSpec(startX, startY, endX, endY, thickness, color));
   }

   public static void drawGlassOutlineEffect(float x, float y, float width, float height, float squirt, float distortStrength, float cornerRadiusMultiplier, float thickness, int color) {
      drawGlassOutline(
         x,
         y,
         width,
         height,
         new float[]{cornerRadiusMultiplier * squirt / 2.0F, cornerRadiusMultiplier * squirt / 2.0F, cornerRadiusMultiplier * squirt / 2.0F, cornerRadiusMultiplier * squirt / 2.0F},
         thickness,
         color,
         (color >>> 24 & 0xFF) / (255.0F),
         height == (240.0F) ? (100.0F) : (50.0F),
         color | (-16777216),
         1.0F,
         true,
         0.0F,
         distortStrength,
         squirt,
         0.0F
      );
   }

   public static void drawEffectIcon(RegistryEntry<StatusEffect> effect, float x, float y, float size, int color) {
      effectIconRenderer().render(new EffectIconSpec(effect, x, y, size, applyAlpha(color)));
   }

   public static void fillCircle(float centerX, float centerY, float radius, float smoothness, int color) {
      breakTextureBatches();
      circleRenderer().render(new CircleSpec(centerX, centerY, radius, applyAlpha(color)).withSmoothness(smoothness));
   }

   public static void drawEffectIcon(StatusEffectInstance effect, float x, float y, float size, int color) {
      if (effect != null && effect.shouldShowIcon()) {
         effectIconRenderer().render(new EffectIconSpec(effect, x, y, size, applyAlpha(color)));
      }
   }

   private static GlassOutlineRenderer glassOutlineRenderer() {
      return GlassOutlineRenderer.getInstance();
   }

   public static void drawRipple(
      float x, float y, float width, float height, float radius, float smoothness, float centerX, float centerY, float rippleRadius, float rippleSmoothness, int sourceColor, int targetColor
   ) {
      rippleRenderer().render(new RippleSpec(x, y, width, height, radius, radius, radius, radius, smoothness, centerX, centerY, rippleRadius, rippleSmoothness, sourceColor, targetColor, false));
   }

   public static void drawText(DrawContext context, String fontName, String text, float x, float y, float size, int color) {
      begin(context);
      drawText(fontName, text, x, y, size, color);
      end();
   }

   public static void drawGlass(
      float x,
      float y,
      float width,
      float height,
      float radiusTopLeft,
      float radiusTopRight,
      float radiusBottomRight,
      float radiusBottomLeft,
      int color,
      float globalAlpha,
      float fresnelPower,
      int fresnelColor,
      float baseAlpha,
      boolean fresnelInvert,
      float fresnelMix,
      float distortStrength,
      float squirt,
      float z
   ) {
      breakTextureBatches();
      glassRenderer().render(new GlassSpec(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z));
   }

   public static void drawIconPattern(IconPatternSpec spec) {
      breakTextureBatches();
      if (spec != null && spec.backgroundVisible()) {
         fillRoundedGradient(
            spec.x(),
            spec.y(),
            spec.width(),
            spec.height(),
            spec.radiusTopLeft(),
            spec.radiusTopRight(),
            spec.radiusBottomRight(),
            spec.radiusBottomLeft(),
            spec.colorTopLeft(),
            spec.colorTopRight(),
            spec.colorBottomRight(),
            spec.colorBottomLeft()
         );
      }

      if (spec != null && spec.iconsVisible()) {
         iconPatternRenderer().render(spec);
      }
   }

   public static void drawSoftGlowRing(float x, float y, float width, float height, float radius, int color, float softness, float intensity) {
      drawSoftGlow(new SoftGlowSpec(x, y, width, height, radius, color, softness).withIntensity(intensity).asRing());
   }

   public static void drawOutline(OutlineSpec spec) {
      breakTextureBatches();
      outlineRenderer().render(spec);
   }

   public static void fillRoundedGradient(float x, float y, float width, float height, float radius, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      fillRoundedGradient(x, y, width, height, radius, radius, radius, radius, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
   }

   public static void nextLayer(DrawContext context) {
      if (context != null) {
         ((GuiLayerTracker)((DrawContextAccessor)(Object)context).getGuiRenderState()).bumpLayerSerial();
      }
   }

   private static GlassShapeRenderer glassShapeRenderer() {
      return GlassShapeRenderer.getInstance();
   }

   public static void drawFadedMsdfText(
      String fontName, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeftStrength, float fadeRightStrength
   ) {
      breakTextureBatches();
      msdfTextRenderer().render(new MsdfTextSpec(fontName, text, x, y, size, applyTextAlpha(color)).withHorizontalFade(fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength));
   }

   public static void drawImageRegion(
      String path, float x, float y, float width, float height, float radius, float u0, float v0, float u1, float v1, int color
   ) {
      if (!(width <= 0.0F) && !(height <= 0.0F)) {
         imageRenderer().render(new ImageSpec(path, x, y, width, height, radius, applyAlpha(color)).withUv(u0, v0, u1, v1));
      }
   }

   public static void fillRoundedGradient(
      float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft
   ) {
      breakTextureBatches();
      roundedRectangleRenderer()
         .render(
            new RoundedRectangleSpec(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, applyAlpha(colorTopLeft), applyAlpha(colorTopRight), applyAlpha(colorBottomRight), applyAlpha(colorBottomLeft), (0.5F))
         );
   }

   private static GlassRenderer glassRenderer() {
      return GlassRenderer.getInstance();
   }

   public static void drawGlassOutline(
      float x,
      float y,
      float width,
      float height,
      float[] radii,
      float thickness,
      int color,
      float globalAlpha,
      float fresnelPower,
      int fresnelColor,
      float baseAlpha,
      boolean fresnelInvert,
      float fresnelMix,
      float distortStrength,
      float squirt,
      float z
   ) {
      breakTextureBatches();
      glassOutlineRenderer().render(new GlassOutlineSpec(x, y, width, height, radii, thickness, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z));
   }

   public static void drawOutlineRanges(Outline360Spec spec) {
      breakTextureBatches();
      outlineRangesRenderer().render(spec);
   }

   public static OutlineColorRange outlineRange(float startDegrees, float endDegrees, int color) {
      return OutlineColorRange.of(startDegrees, endDegrees, color);
   }

   public static void drawWarpedText(
      String fontName, String text, float x, float y, float size, float padding, float influenceX, float influenceY, float influenceRadiusPx, float cellPx, int color
   ) {
      MsdfFont font = MsdfFontRegistry.getFont(fontName);
      if (font != null) {
         float[] glyphQuads = font.buildGlyphQuads(text, size);

         for (int quadOffset = 0; quadOffset < glyphQuads.length; quadOffset += 8) {
            glyphQuads[quadOffset] += padding;
            glyphQuads[quadOffset + 1] = glyphQuads[quadOffset + 1] + padding;
            glyphQuads[quadOffset + 2] = glyphQuads[quadOffset + 2] + padding;
            glyphQuads[quadOffset + 3] = glyphQuads[quadOffset + 3] + padding;
         }

         warpTextRenderer()
            .render(
               new WarpTextSpec(
                  x - padding,
                  y - padding,
                  font.getTextWidth(text, size) + padding * 2.0F,
                  font.getLineHeight(size) + padding * 2.0F,
                  glyphQuads,
                  influenceX - x + padding,
                  influenceY - y + padding,
                  influenceRadiusPx,
                  cellPx,
                  font.getPxRange(),
                  font.getAtlasTexture(),
                  applyAlpha(color)
               )
            );
      }
   }

   public static void drawGuiBlur(float x, float y, float width, float height, float radius) {
      breakTextureBatches();
      blurRenderer().renderHeavyBlur(new BlurSpec(x, y, width, height, radius, 1.0F, (36.0F)).withColor(applyAlpha(-1)));
   }

   private static ArcRenderer arcRenderer() {
      return ArcRenderer.getInstance();
   }

   public static void drawRotatedMsdfText(String fontName, String text, float x, float y, float size, int color, float rotationDegrees, float originX, float originY) {
      breakTextureBatches();
      msdfTextRenderer().render(new MsdfTextSpec(fontName, text, x, y, size, applyTextAlpha(color), rotationDegrees, originX, originY));
   }

   public static void pushAlpha(float multiplier) {
      alphaStack.push(alpha);
      alpha = alpha * Math.max(0.0F, Math.min(1.0F, multiplier));
   }

   private static void registerResourceReloader() {
      if (!resourceReloaderRegistered) {
         Identifier id = Identifier.of(("mouseclient"), ("render2d_msdf_cache"));
         ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(id, (SynchronousResourceReloader)resourceManager -> MsdfTextRenderer.resetInstanceCaches());
         resourceReloaderRegistered = true;
      }
   }

   public static void drawGradientOutline(float x, float y, float width, float height, float radius, float thickness, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      drawGradientOutline(x, y, width, height, radius, radius, radius, radius, thickness, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
   }

   private static GuiBlurRenderer blurRenderer() {
      return GuiBlurRenderer.getInstance();
   }

   public static void drawShimmer(float x, float y, float width, float height, float progress, float halfWidth, float intensity, int color) {
      shimmerRenderer().render(new ShimmerSpec(x, y, width, height, progress, halfWidth, intensity, color));
   }

   public static void drawFadedDashedLine(
      float startX, float startY, float endX, float endY, float thickness, int color, float dashLength, float gapLength, int maxDashes, float startAlpha, float endAlpha
   ) {
      float deltaX = endX - startX;
      float deltaY = endY - startY;
      float length = (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY);
      if (!(length < (0.01F)) && color >>> 24 != 0 && !(thickness <= 0.0F)) {
         float directionX = deltaX / length;
         float directionY = deltaY / length;
         float dashPeriod = Math.max((0.5F), dashLength + gapLength);
         int dashCount = (int)Math.ceil(length / dashPeriod);
         if (maxDashes > 0 && dashCount > maxDashes) {
            dashCount = maxDashes;
         }

         for (int dashIndex = 0; dashIndex < dashCount; dashIndex++) {
            float startDistance = dashIndex * dashPeriod;
            float endDistance = Math.min(startDistance + dashLength, length);
            if (!(endDistance <= startDistance)) {
               float dashStartAlpha = lineFadeAt(startDistance / length, startAlpha, endAlpha);
               float dashEndAlpha = lineFadeAt(endDistance / length, startAlpha, endAlpha);
               drawLine(startX + directionX * startDistance, startY + directionY * startDistance, startX + directionX * endDistance, startY + directionY * endDistance, thickness, color, dashStartAlpha, dashEndAlpha);
            }
         }
      }
   }

   public static void drawGradientBlur(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      breakTextureBatches();
      blurRenderer().render(new BlurSpec(x, y, width, height, radius, smoothness, blurRadius).withColors(colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft));
   }

   public static void begin(DrawContext context) {
      if (!frameActive) {
         frameActive = true;
         alpha = 1.0F;
         alphaStack.clear();
         ScissorStack.clear();
         RoundedClipStack.clear();
         blurRenderer().begin(context);
         glassRenderer().begin(context);
         glassShapeRenderer().begin(context);
         glassOutlineRenderer().begin(context);
         circleRenderer().begin(context);
         bracketRenderer().begin(context);
         blobRenderer().begin(context);
         arcRenderer().begin(context);
         colorPickerRenderer().begin(context);
         roundedRectangleRenderer().begin(context);
         iconPatternRenderer().begin(context);
         outlineRenderer().begin(context);
         outlineRangesRenderer().begin(context);
         atlasTextRenderer().begin(context);
         msdfTextRenderer().begin(context);
         shimmerRenderer().begin(context);
         ditherRenderer().begin(context);
         menuGridRenderer().begin(context);
         warpTextRenderer().begin(context);
         imageRenderer().begin(context);
         lineRenderer().begin(context);
         effectIconRenderer().begin(context);
         rippleRenderer().begin(context);
         glowRenderer().begin(context);
         softGlowRenderer().begin(context);
         GuiItemRenderer.setDrawContext(context);
      }
   }

   public static void close() {
      GuiBlurRenderer.closeInstance();
      GlassRenderer.closeInstance();
      GlassShapeRenderer.closeInstance();
      GlassOutlineRenderer.closeInstance();
      CircleRenderer.closeInstance();
      BracketRenderer.closeInstance();
      BlobRenderer.closeInstance();
      ArcRenderer.closeInstance();
      ColorPickerRenderer.closeInstance();
      RoundedRectangleRenderer.closeInstance();
      RectHalfIconRenderer.closeInstance();
      OutlineRenderer.closeInstance();
      Outline360Renderer.closeInstance();
      AtlasTextRenderer.closeInstance();
      MsdfTextRenderer.closeInstance();
      ShimmerRenderer.closeInstance();
      DitherRenderer.closeInstance();
      MenuGridRenderer.closeInstance();
      WarpTextRenderer.closeInstance();
      ImageRenderer.closeInstance();
      LineRenderer.closeInstance();
      EffectIconRenderer.closeInstance();
      RippleRenderer.closeInstance();
      GuiGlowRenderer.closeInstance();
      SoftGlowRenderer.closeInstance();
      MouseClientClientPalette.close();
      ClientInitializer.logger().info(("[Render2D] Render2D closed"));
   }

   public static void fillRoundedRectangle(float x, float y, float width, float height, float radius, int color) {
      breakTextureBatches();
      roundedRectangleRenderer().render(new RoundedRectangleSpec(x, y, width, height, radius, applyAlpha(color)));
   }

   public static void drawOutlineRanges(
      float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, int color, OutlineColorRange... ranges
   ) {
      breakTextureBatches();
      drawOutlineRanges(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, color, Arrays.asList(ranges));
   }

   public static void drawAsymmetricRoundedImage(String path, float x, float y, float size, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, int color) {
      int effectiveColor = applyAlpha(color);
      imageRenderer().render(new ImageSpec(path, x, y, size, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, effectiveColor, effectiveColor, effectiveColor, effectiveColor));
   }

   public static void drawObfuscatedText(String fontName, String text, float x, float y, float size, int color) {
      String alphabet = obfuscationAlphabet(fontName);
      msdfTextRenderer().setForceAtlasGlyphs(true);
      float penX = x;
      int textIndex = 0;

      while (textIndex < text.length()) {
         int codePoint = text.codePointAt(textIndex);
         textIndex += Character.charCount(codePoint);
         if (!Character.isWhitespace(codePoint)) {
            String randomGlyph = String.valueOf(
               alphabet.charAt((int)(((System.currentTimeMillis() / (10L) + textIndex * (1337L)) * (-7046029254386353131L) >>> 10) % alphabet.length()))
            );
            drawText(
               fontName, randomGlyph, penX + (getTextWidth(fontName, new String(Character.toChars(codePoint)), size) - getTextWidth(fontName, randomGlyph, size)) / 2.0F, y, size, color
            );
         }

         penX += getTextWidth(fontName, new String(Character.toChars(codePoint)), size);
      }

      msdfTextRenderer().setForceAtlasGlyphs(false);
   }

   public static void fillCircle(float centerX, float centerY, float radius, int color) {
      breakTextureBatches();
      circleRenderer().render(new CircleSpec(centerX, centerY, radius, applyAlpha(color)));
   }

   public static void drawHuePicker(float x, float y, float width, float height, float opacity) {
      breakTextureBatches();
      colorPickerRenderer().render(ColorPickerSpec.hue(x, y, width, height, opacity * alpha));
   }

   public static void drawIconPattern(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int backgroundColor,
      int iconColor,
      String fontName,
      String icon,
      float iconSize,
      float spacingX,
      float spacingY,
      float rotationMaxDegrees
   ) {
      drawIconPattern(new IconPatternSpec(x, y, width, height, radius, backgroundColor, fontName, icon, iconSize, iconColor, spacingX, spacingY, rotationMaxDegrees));
   }

   public static void drawSoftGlow(SoftGlowSpec spec) {
      if (spec != null) {
         breakTextureBatches();
         softGlowRenderer().render(spec);
      }
   }

   public static void drawOutlineRanges(float x, float y, float width, float height, float radius, float thickness, int color, List<OutlineColorRange> ranges) {
      breakTextureBatches();
      outlineRangesRenderer().render(new Outline360Spec(x, y, width, height, radius, thickness, applyAlpha(color), applyAlphaToRanges(ranges)));
   }

   public static int applyAlpha(int color) {
      if (alpha >= (0.999F)) {
         return color;
      }

      int effectiveAlpha = Math.round((color >>> 24 & 0xFF) * alpha);
      return effectiveAlpha << 24 | color & (16777215);
   }

   public static void drawSharedBlur(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
      breakTextureBatches();
      blurRenderer().renderGlobalBlur(new BlurSpec(x, y, width, height, radius, smoothness, blurRadius).withColor(applyAlpha(color)));
   }

   public static void drawGlass(GlassSpec spec) {
      breakTextureBatches();
      glassRenderer().render(spec);
   }

   public static void fillRectangle(float x, float y, float width, float height, int color) {
      fillRoundedRectangle(x, y, width, height, 0.0F, color);
   }

   public static void initialize() {
      registerResourceReloader();
      ClientInitializer.logger().info(("[Render2D] Render2D initialized"));
   }

   public static void drawImage(String path, float x, float y, float width, float height) {
      imageRenderer().render(new ImageSpec(path, x, y, width, height));
   }

   public static void fillRoundedRectangle(DrawContext context, float x, float y, float width, float height, float radius, int color) {
      begin(context);
      fillRoundedRectangle(x, y, width, height, radius, color);
      end();
   }

   public static void drawBlur(float x, float y, float width, float height, float radius, float blurRadius, float smoothness, int color) {
      breakTextureBatches();
      blurRenderer().render(new BlurSpec(x, y, width, height, radius, smoothness, blurRadius).withColor(color));
   }

   public static void drawText(AtlasTextSpec spec) {
      breakTextureBatches();
      if (spec != null && !spec.hasHorizontalFade() && Math.abs(spec.rotationDegrees()) < (0.001F)) {
         String msdfFontName = resolveMsdfFontName(spec.fontName());
         if (msdfFontName != null) {
            msdfTextRenderer()
               .render(
                  new MsdfTextSpec(
                     msdfFontName,
                     spec.text(),
                     spec.x(),
                     spec.y(),
                     spec.size(),
                     applyTextAlpha(spec.colorTopLeft()),
                     applyTextAlpha(spec.colorTopRight()),
                     applyTextAlpha(spec.colorBottomRight()),
                     applyTextAlpha(spec.colorBottomLeft())
                  )
               );
            return;
         }
      }

      atlasTextRenderer().render(spec);
   }

   public static void drawMsdfText(MsdfTextSpec spec) {
      breakTextureBatches();
      if (spec != null) {
         msdfTextRenderer()
            .render(
               new MsdfTextSpec(
                     spec.fontName(),
                     spec.text(),
                     spec.x(),
                     spec.y(),
                     spec.size(),
                     applyTextAlpha(spec.colorTopLeft()),
                     applyTextAlpha(spec.colorTopRight()),
                     applyTextAlpha(spec.colorBottomRight()),
                     applyTextAlpha(spec.colorBottomLeft()),
                     spec.rotationDegrees(),
                     spec.rotationOriginX(),
                     spec.rotationOriginY()
                  )
                  .withHorizontalFade(spec.fadeLeftX(), spec.fadeRightX(), spec.fadeWidth(), spec.fadeLeftStrength(), spec.fadeRightStrength())
            );
      }
   }

   public static void drawBlur(DrawContext context, float x, float y, float width, float height, float radius, float blurRadius) {
      begin(context);
      drawBlur(x, y, width, height, radius, blurRadius);
      end();
   }

   private static List<OutlineColorRange> applyAlphaToRanges(List<OutlineColorRange> ranges) {
      if (!(alpha >= (0.999F)) && ranges != null && !ranges.isEmpty()) {
         ArrayList effectiveRanges = new ArrayList(ranges.size());

         for (OutlineColorRange range : ranges) {
            effectiveRanges.add(new OutlineColorRange(range.startDegrees(), range.endDegrees(), applyAlpha(range.color())));
         }

         return effectiveRanges;
      } else {
         return ranges;
      }
   }

   public static boolean isTextureReady(String path) {
      return imageRenderer().hasTexture(path);
   }

   public static void drawFadedText(
      String fontName, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeft, float fadeRight
   ) {
      breakTextureBatches();
      text = NameProtect.getInstance().replaceNickname(text);
      if (text != null && text.indexOf(167) >= 0) {
         StringBuilder textBuilder = new StringBuilder();

         for (StyledTextParser.StyledTextRun styledRun : StyledTextParser.parseLegacy(text, color)) {
            String runText = styledRun.text().indexOf(167) >= 0 ? StyledTextParser.stripFormatting(styledRun.text()) : styledRun.text();
            if (!runText.isEmpty()) {
               if (styledRun.obfuscated()) {
                  drawObfuscatedText(fontName, runText, x + getTextWidth(fontName, textBuilder.toString(), size), y, size, styledRun.color() & (16777215) | color & (-16777216));
               } else {
                  drawFadedText(
                     fontName,
                     runText,
                     x + getTextWidth(fontName, textBuilder.toString(), size),
                     y,
                     size,
                     styledRun.color() & (16777215) | color & (-16777216),
                     fadeLeftX,
                     fadeRightX,
                     fadeWidth,
                     fadeLeft,
                     fadeRight
                  );
               }

               textBuilder.append(runText);
            }
         }
      } else {
         String msdfFontName = resolveMsdfFontName(fontName);
         if (msdfFontName != null) {
            msdfTextRenderer().render(new MsdfTextSpec(msdfFontName, text, x, y, size, applyTextAlpha(color)).withHorizontalFade(fadeLeftX, fadeRightX, fadeWidth, fadeLeft, fadeRight));
         } else {
            atlasTextRenderer().render(new AtlasTextSpec(fontName, text, x, y, size, color).withHorizontalFade(fadeLeftX, fadeRightX, fadeWidth, fadeLeft, fadeRight));
         }
      }
   }

   public static float getTextWidth(String fontName, String text, float size) {
      if (text != null && text.indexOf(167) >= 0) {
         text = StyledTextParser.stripFormatting(text);
      }

      text = NameProtect.getInstance().replaceNickname(text);
      String msdfFontName = resolveMsdfFontName(fontName);
      return msdfFontName != null ? msdfTextRenderer().getTextWidth(msdfFontName, text, size) : atlasTextRenderer().getTextWidth(fontName, text, size);
   }

   private static RoundedRectangleRenderer roundedRectangleRenderer() {
      return RoundedRectangleRenderer.getInstance();
   }

   private static void drawPlainText(String fontName, String text, float x, float y, float size, int color) {
      String msdfFontName = resolveMsdfFontName(fontName);
      if (msdfFontName != null) {
         msdfTextRenderer().render(new MsdfTextSpec(msdfFontName, text, x, y, size, applyTextAlpha(color)));
      } else {
         atlasTextRenderer().render(new AtlasTextSpec(fontName, text, x, y, size, color));
      }
   }

   public static void drawEffectIcon(RegistryEntry<StatusEffect> effect, float x, float y, float size) {
      effectIconRenderer().render(new EffectIconSpec(effect, x, y, size, applyAlpha(-1)));
   }

   public static void drawBracket(float x, float y, float size, float radius, float stroke, int quadrant, int color) {
      breakTextureBatches();
      bracketRenderer().render(new BracketSpec(x, y, size, radius, stroke, quadrant, applyAlpha(color)));
   }

   public static void drawEffectIcon(StatusEffectInstance effect, float x, float y, float size) {
      if (effect != null && effect.shouldShowIcon()) {
         effectIconRenderer().render(new EffectIconSpec(effect, x, y, size, applyAlpha(-1)));
      }
   }

   public static void fillRoundedRectangle(RoundedRectangleSpec spec) {
      roundedRectangleRenderer().render(spec);
   }

   private static CircleRenderer circleRenderer() {
      return CircleRenderer.getInstance();
   }

   public static void drawMsdfText(String fontName, String text, float x, float y, float size, int color) {
      breakTextureBatches();
      msdfTextRenderer().render(new MsdfTextSpec(fontName, text, x, y, size, applyTextAlpha(color)));
   }

   private static ShimmerRenderer shimmerRenderer() {
      return ShimmerRenderer.getInstance();
   }

   public static void drawGuiBlur(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft) {
      breakTextureBatches();
      blurRenderer().renderHeavyBlur(new BlurSpec(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, 1.0F, (36.0F), applyAlpha(-1)));
   }

   public static void drawOutlineRanges(float x, float y, float width, float height, float radius, float thickness, int color, OutlineColorRange... ranges) {
      breakTextureBatches();
      drawOutlineRanges(x, y, width, height, radius, thickness, color, Arrays.asList(ranges));
   }

   private static RippleRenderer rippleRenderer() {
      return RippleRenderer.getInstance();
   }

   public static void drawFadedText(
      String fontName, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, boolean fadeLeft, boolean fadeRight
   ) {
      drawFadedText(fontName, text, x, y, size, color, fadeLeftX, fadeRightX, fadeWidth, fadeLeft ? 1.0F : 0.0F, fadeRight ? 1.0F : 0.0F);
   }

   private static ColorPickerRenderer colorPickerRenderer() {
      return ColorPickerRenderer.getInstance();
   }

   public static void drawAlphaPicker(float x, float y, float width, float height, int solidColor, float opacity) {
      breakTextureBatches();
      colorPickerRenderer().render(ColorPickerSpec.alpha(x, y, width, height, solidColor, opacity * alpha));
   }

   public static void end() {
      if (frameActive) {
         frameActive = false;
         blurRenderer().end();
         glassRenderer().end();
         glassShapeRenderer().end();
         glassOutlineRenderer().end();
         circleRenderer().end();
         bracketRenderer().end();
         blobRenderer().end();
         arcRenderer().end();
         colorPickerRenderer().end();
         roundedRectangleRenderer().end();
         iconPatternRenderer().end();
         outlineRenderer().end();
         outlineRangesRenderer().end();
         atlasTextRenderer().end();
         msdfTextRenderer().endFrame();
         shimmerRenderer().end();
         ditherRenderer().end();
         menuGridRenderer().end();
         warpTextRenderer().end();
         imageRenderer().endFrame();
         lineRenderer().end();
         effectIconRenderer().endFrame();
         rippleRenderer().end();
         glowRenderer().end();
         softGlowRenderer().end();
         GuiItemRenderer.clearDrawContext();
      }
   }

   public static void drawMenuGrid(float x, float y, float width, float height, float cellPx, float lineWidthPx, float mouseX, float mouseY, float influenceRadiusPx, int color) {
      menuGridRenderer().render(new MenuGridSpec(x, y, width, height, cellPx, lineWidthPx, mouseX, mouseY, influenceRadiusPx, color));
   }

   private static int applyTextAlpha(int color) {
      return applyAlpha(color);
   }

   public static void drawBlur(BlurSpec spec) {
      breakTextureBatches();
      blurRenderer().render(spec);
   }

   public static void drawBlob(
      float x,
      float y,
      float width,
      float height,
      float centerX,
      float centerY,
      float radiusX,
      float radiusY,
      float falloff,
      float clipTL,
      float clipTR,
      float clipBR,
      float clipBL,
      int color
   ) {
      breakTextureBatches();
      blobRenderer().render(new BlobSpec(x, y, width, height, centerX, centerY, radiusX, radiusY, falloff, (0.25F), clipTL, clipTR, clipBR, clipBL, applyAlpha(color)));
   }

   public static void drawGlowingRipple(float x, float y, float width, float height, float radius, float centerX, float centerY, float rippleRadius, int sourceColor, int targetColor) {
      rippleRenderer().render(new RippleSpec(x, y, width, height, radius, radius, radius, radius, (0.5F), centerX, centerY, rippleRadius, (25.0F), sourceColor, targetColor, true));
   }

   private static String resolveMsdfFontName(String fontName) {
      if (fontName != null && !fontName.isBlank()) {
         String normalizedName = fontName.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');

         return switch (normalizedName) {
            case ("montserrat"), ("montserrat_regular") -> ("montserrat-regular");
            case ("montserrat_medium") -> ("montserrat-medium");
            case ("montserrat_semibold"), ("montserrat_semi_bold") -> ("montserrat-semibold");
            case ("montserrat_bold") -> ("montserrat-bold");
            case ("montserrat_extrabold"), ("montserrat_extra_bold") -> ("montserrat-extrabold");
            case ("montserrat_black") -> ("montserrat-black");
            case ("montserrat_light") -> ("montserrat-light");
            case ("montserrat_extralight"), ("montserrat_extra_light") -> ("montserrat-extralight");
            case ("montserrat_thin") -> ("montserrat-thin");
            case ("sf"), ("sf_regular") -> ("sf-regular");
            case ("sf_medium") -> ("sf-medium");
            case ("sf_bold") -> ("sf-bold");
            case ("kimiko") -> ("kimiko");
            case ("event_icons"), ("events_icons"), ("eventicons") -> ("event-icons");
            case ("icons"), ("icon") -> ("icons");
            case ("test"), ("semibold") -> ("test");
            case ("thin") -> ("thin");
            case ("mini"), ("light") -> ("mini");
            case ("semi"), ("medium") -> ("semi");
            case ("semi_bold"), ("semi-bold") -> ("semi_bold");
            case ("bold") -> ("bold");
            default -> null;
         };
      } else {
         return null;
      }
   }

   public static void drawLine(float startX, float startY, float endX, float endY, float thickness, int color, float startAlpha, float endAlpha) {
      lineRenderer().render(new LineSpec(startX, startY, endX, endY, thickness, color, startAlpha, endAlpha));
   }

   public static float getMsdfTextWidth(String fontName, String text, float size) {
      return msdfTextRenderer().getTextWidth(fontName, text, size);
   }

   private static Outline360Renderer outlineRangesRenderer() {
      return Outline360Renderer.getInstance();
   }

   public static void drawDither(float x, float y, float width, float height, float radius, float cellSize, float spread, int color) {
      ditherRenderer().render(new DitherSpec(x, y, width, height, radius, cellSize, spread, color));
   }

   public static BlurSpecBuilder blurBuilder() {
      return new BlurSpecBuilder();
   }

   private static EffectIconRenderer effectIconRenderer() {
      return EffectIconRenderer.getInstance();
   }

   public static void fillRoundedRectangle(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, int color) {
      breakTextureBatches();
      roundedRectangleRenderer().render(new RoundedRectangleSpec(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, applyAlpha(color)));
   }

   public static void drawGlass(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int color,
      float globalAlpha,
      float fresnelPower,
      int fresnelColor,
      float baseAlpha,
      boolean fresnelInvert,
      float fresnelMix,
      float distortStrength,
      float squirt,
      float z
   ) {
      drawGlass(x, y, width, height, radius, radius, radius, radius, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z);
   }

   public static void drawOutline(float x, float y, float width, float height, float radius, float thickness, int color) {
      breakTextureBatches();
      outlineRenderer().render(new OutlineSpec(x, y, width, height, radius, thickness, applyAlpha(color)));
   }

   private static LineRenderer lineRenderer() {
      return LineRenderer.getInstance();
   }

   private static float lineFadeAt(float progress, float startAlpha, float endAlpha) {
      progress = Math.max(0.0F, Math.min(1.0F, progress));
      float edgeAlpha = progress < (0.5F) ? startAlpha : endAlpha;
      float centerWeight = 1.0F - Math.abs(progress - (0.5F)) * 2.0F;
      return edgeAlpha + (1.0F - edgeAlpha) * centerWeight;
   }

   public static void drawArc(float centerX, float centerY, float width, float lift, float thickness, int color) {
      breakTextureBatches();
      arcRenderer().render(new ArcSpec(centerX, centerY, width, lift, thickness, (0.85F), color));
   }

   private static void breakTextureBatches() {
      ImageRenderer.getInstance().breakBatches();
      EffectIconRenderer.getInstance().breakBatches();
   }

   private static BlobRenderer blobRenderer() {
      return BlobRenderer.getInstance();
   }

   private static String obfuscationAlphabet(String fontName) {
      String msdfFontName = resolveMsdfFontName(fontName);
      if (msdfFontName == null) {
         return OBFUSCATION_ALPHABET;
      }

      String alphabet = obfuscationAlphabets.get(msdfFontName);
      if (alphabet != null) {
         return alphabet;
      }

      StringBuilder textBuilder = new StringBuilder(OBFUSCATION_ALPHABET.length());

      for (int characterIndex = 0; characterIndex < OBFUSCATION_ALPHABET.length(); characterIndex++) {
         if (msdfTextRenderer().hasGlyph(msdfFontName, OBFUSCATION_ALPHABET.charAt(characterIndex))) {
            textBuilder.append(OBFUSCATION_ALPHABET.charAt(characterIndex));
         }
      }

      alphabet = textBuilder.length() < 16 ? OBFUSCATION_ALPHABET : textBuilder.toString();
      obfuscationAlphabets.put(msdfFontName, alphabet);
      return alphabet;
   }

   public static void drawMsdfGradientText(String fontName, String text, float x, float y, float size, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      breakTextureBatches();
      msdfTextRenderer().render(new MsdfTextSpec(fontName, text, x, y, size, applyTextAlpha(colorTopLeft), applyTextAlpha(colorTopRight), applyTextAlpha(colorBottomRight), applyTextAlpha(colorBottomLeft)));
   }

   public static void drawRoundedImage(String path, float x, float y, float width, float height, float radius, int color) {
      imageRenderer().render(new ImageSpec(path, x, y, width, height, radius, applyAlpha(color)));
   }

   public static void drawOutline(DrawContext context, float x, float y, float width, float height, float radius, float thickness, int color) {
      begin(context);
      drawOutline(x, y, width, height, radius, thickness, color);
      end();
   }

   public static void drawDefaultBlur(float x, float y, float width, float height, float radius) {
      drawBlur(x, y, width, height, radius, (16.0F), 1.0F, -1);
   }

   public static void drawSoftGlowPoint(float x, float y, int color, float softness, float intensity) {
      drawSoftGlow(new SoftGlowSpec(x, y, 0.0F, 0.0F, 0.0F, color, softness).withIntensity(intensity));
   }

   public static void drawGradientOutline(
      float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft
   ) {
      breakTextureBatches();
      outlineRenderer()
         .render(
            new OutlineSpec(
               x,
               y,
               width,
               height,
               radiusTopLeft,
               radiusTopRight,
               radiusBottomRight,
               radiusBottomLeft,
               thickness,
               applyAlpha(colorTopLeft),
               applyAlpha(colorTopRight),
               applyAlpha(colorBottomRight),
               applyAlpha(colorBottomLeft),
               (0.5F),
               null
            )
         );
   }

   public static void drawGlassEffect(float x, float y, float width, float height, float squirt, float distortStrength, float cornerRadiusMultiplier, int color) {
      drawGlass(
         x,
         y,
         width,
         height,
         new float[]{cornerRadiusMultiplier * squirt / 2.0F, cornerRadiusMultiplier * squirt / 2.0F, cornerRadiusMultiplier * squirt / 2.0F, cornerRadiusMultiplier * squirt / 2.0F},
         color,
         (color >>> 24 & 0xFF) / (255.0F),
         height == (240.0F) ? (100.0F) : (50.0F),
         color | (-16777216),
         1.0F,
         true,
         0.0F,
         distortStrength,
         squirt,
         0.0F
      );
   }

   private static MsdfTextRenderer msdfTextRenderer() {
      return MsdfTextRenderer.getInstance();
   }

   private static MenuGridRenderer menuGridRenderer() {
      return MenuGridRenderer.getInstance();
   }
}
