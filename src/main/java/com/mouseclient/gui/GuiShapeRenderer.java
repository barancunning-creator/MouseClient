package com.mouseclient.gui;

import com.mouseclient.internal.GuiScale;
import com.mouseclient.render.avatar.MouseClientEasingLut;
import com.mouseclient.render.avatar.Easings;
import com.mouseclient.render.pipeline.GuiShapeBlurCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public final class GuiShapeRenderer {

   public static void drawBlurredAnnularArc(
      GuiShapeRenderer.BlurQuality quality,
      float centerX,
      float centerY,
      float innerRadius,
      float outerRadius,
      float startDegrees,
      float sweepDegrees,
      float gap,
      float cornerRadius,
      int color,
      float tintMix,
      float alpha
   ) {
      TextureSetup textureSetup = GuiShapeBlurCache.INSTANCE.textureForQuality(quality.ordinal(), false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      if (textureSetup == null) {
         RoundedArcRenderer.drawAnnularArc(getDrawContext(), null, getTransform(), centerX, centerY, innerRadius, outerRadius, startDegrees, sweepDegrees, gap, cornerRadius, color, alpha * tintMix, 1.0F);
      } else {
         RoundedArcRenderer.drawAnnularArc(getDrawContext(), textureSetup, getTransform(), centerX, centerY, innerRadius, outerRadius, startDegrees, sweepDegrees, gap, cornerRadius, color, alpha, tintMix);
      }
   }

   public static int multiplyAlpha(int color, float alpha) {
      int scaledAlpha = Math.round((color >>> 24 & 0xFF) * MathHelper.clamp(alpha, 0.0F, 1.0F));
      return scaledAlpha << 24 | color & (16777215);
   }

   private static void drawTintedBlur(
      Matrix3x2f transform,
      float x,
      float y,
      float width,
      float height,
      int color,
      float tintAmount,
      float alpha,
      CornerRadii radii,
      GuiShapeRenderer.BlurQuality quality,
      boolean includeGuiBlur
   ) {
      TextureSetup textureSetup = getBlurTexture(transform, x, y, width, height, 0.0F, radii, quality, includeGuiBlur);
      if (textureSetup != null) {
         RoundedTextureRenderer.submit(
            getDrawContext(), textureSetup, transform, x, y, width, height, 0.0F, 0.0F, 1.0F, 1.0F, radii, tintAmount, (-0.5F), (0.5F), alpha, color, color, 3, includeGuiBlur
         );
      }
   }

   private static DrawContext getDrawContext() {
      return HudRenderContext.getDrawContext();
   }

   public static void drawBlurredSector(
      GuiShapeRenderer.BlurQuality quality,
      float centerX,
      float centerY,
      float radius,
      float startDegrees,
      float sweepDegrees,
      float gap,
      float cornerRadius,
      int color,
      float tintMix,
      float alpha
   ) {
      TextureSetup textureSetup = GuiShapeBlurCache.INSTANCE.textureForQuality(quality.ordinal(), false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
      if (textureSetup == null) {
         RoundedArcRenderer.drawSector(getDrawContext(), null, getTransform(), centerX, centerY, radius, startDegrees, sweepDegrees, gap, cornerRadius, color, alpha * tintMix, 1.0F);
      } else {
         RoundedArcRenderer.drawSector(getDrawContext(), textureSetup, getTransform(), centerX, centerY, radius, startDegrees, sweepDegrees, gap, cornerRadius, color, alpha, tintMix);
      }
   }

   private GuiShapeRenderer() {
   }

   public static void drawBlur(
      float x, float y, float width, float height, float alpha, CornerRadii radii, GuiShapeRenderer.BlurQuality quality, boolean includeGuiBlur
   ) {
      drawTintedBlur(getTransform(), x, y, width, height, -1, 0.0F, alpha, radii, quality, includeGuiBlur);
   }

   public static void drawShadow(float x, float y, float width, float height, float spread, int color, float alpha, CornerRadii radii) {
      RoundedShapeRenderer.submit(
         getDrawContext(), getTransform(), x - spread, y - spread, width + spread * 2.0F, height + spread * 2.0F, radii, spread, 0.0F, alpha, color, color, color, color, 1
      );
   }

   public static void fillGradient(float x, float y, float width, float height, int colorBottomRight, int colorTopRight, int colorBottomLeft, int colorTopLeft, float alpha, CornerRadii radii) {
      RoundedShapeRenderer.submit(getDrawContext(), getTransform(), x, y, width, height, radii, 0.0F, 0.0F, alpha, colorTopLeft, colorBottomLeft, colorBottomRight, colorTopRight, 0);
   }

   private static float snapToHalfPixel(float value) {
      return Math.round(value * 2.0F) / 2.0F;
   }

   private static Matrix3x2f getTransform() {
      DrawContext context = getDrawContext();
      return context == null ? new Matrix3x2f() : new Matrix3x2f(GuiScale.getScaledMatrices(context));
   }

   public static short packRadius(float radius, boolean squircle) {
      int packedRadius = MathHelper.clamp(Math.round(Math.max(0.0F, radius) * (16.0F)), 0, 32767);
      return (short)(squircle ? -packedRadius : packedRadius);
   }

   public static void drawSoftBlur(
      GuiShapeRenderer.BlurQuality quality,
      boolean includeGuiBlur,
      float x,
      float y,
      float width,
      float height,
      float alpha,
      CornerRadii radii,
      int tintColor,
      float tintAmount,
      int shadowColor
   ) {
      x = snapToHalfPixel(x);
      y = snapToHalfPixel(y);
      width = snapToHalfPixel(width);
      height = snapToHalfPixel(height);
      float blurSpread = radii.maxRadius() * (0.65F);
      float shadowAlpha = alpha * alpha * (0.8F);
      Matrix3x2f transform = getTransform();
      if (!(blurSpread <= 0.0F) && !(shadowAlpha <= (5.0E-4F))) {
         TextureSetup textureSetup = getBlurTexture(transform, x, y, width, height, 0.0F, radii, quality, includeGuiBlur);
         TextureSetup easingTexture = MouseClientEasingLut.textureSetup(Easings.QUAD_IN);
         if (textureSetup != null && textureSetup.texure0() != null && easingTexture != null) {
            TextureSetup blurAndEasingTexture = TextureSetup.of(textureSetup.texure0(), textureSetup.sampler0(), easingTexture.texure0(), easingTexture.sampler0());
            float shadowStrength = (shadowColor >>> 24 & 0xFF) / (255.0F) * shadowAlpha;
            SoftBlurQuadRenderer.submit(
               getDrawContext(),
               blurAndEasingTexture,
               transform,
               x - blurSpread,
               y - blurSpread,
               width + blurSpread * 2.0F,
               height + blurSpread * 2.0F,
               radii,
               blurSpread,
               tintColor,
               alpha,
               tintAmount,
               shadowColor,
               shadowStrength,
               includeGuiBlur
            );
         }
      } else {
         drawTintedBlur(transform, x, y, width, height, tintColor, tintAmount, alpha, radii, quality, includeGuiBlur);
      }
   }

   private static TextureSetup getBlurTexture(
      Matrix3x2f transform, float x, float y, float width, float height, float padding, CornerRadii radii, GuiShapeRenderer.BlurQuality quality, boolean includeGuiBlur
   ) {
      float u0 = 0.0F;
      float v0 = 0.0F;
      float u1 = 0.0F;
      float v1 = 0.0F;
      float pixelRadius = 0.0F;
      if (includeGuiBlur) {
         float paddedX = x - padding;
         float paddedY = y - padding;
         float paddedWidth = width + padding * 2.0F;
         float paddedHeight = height + padding * 2.0F;
         Vector2f transformedStart = transform.transformPosition(paddedX, paddedY, new Vector2f());
         Vector2f transformedEnd = transform.transformPosition(paddedX + paddedWidth, paddedY + paddedHeight, new Vector2f());
         float minimumX = Math.min(transformedStart.x, transformedEnd.x);
         float minimumY = Math.min(transformedStart.y, transformedEnd.y);
         float maximumX = Math.max(transformedStart.x, transformedEnd.x);
         float maximumY = Math.max(transformedStart.y, transformedEnd.y);
         Window window = MinecraftClient.getInstance().getWindow();
         float windowScale = window.getScaleFactor();
         float logicalWidth = windowScale <= 0.0F ? window.getScaledWidth() : window.getFramebufferWidth() / windowScale;
         float logicalHeight = windowScale <= 0.0F ? window.getScaledHeight() : window.getFramebufferHeight() / windowScale;
         u0 = minimumX / logicalWidth;
         u1 = maximumX / logicalWidth;
         v0 = 1.0F - maximumY / logicalHeight;
         v1 = 1.0F - minimumY / logicalHeight;
         pixelRadius = radii.maxRadius() * windowScale * transform.m00();
      }

      return GuiShapeBlurCache.INSTANCE.textureForQuality(quality.ordinal(), includeGuiBlur, u0, v0, u1, v1, pixelRadius);
   }

   public static ScreenRect transformedClippedBounds(Matrix3x2f transform, ScreenRect scissor, float x, float y, float width, float height) {
      ScreenRect bounds = new ScreenRect(Math.round(x), Math.round(y), Math.max(1, Math.round(width)), Math.max(1, Math.round(height)))
         .transformEachVertex(transform);
      return scissor == null ? bounds : scissor.intersection(bounds);
   }

   public static void fillSector(float centerX, float centerY, float radius, float startDegrees, float sweepDegrees, float gap, float cornerRadius, int color, float alpha) {
      RoundedArcRenderer.drawSector(getDrawContext(), null, getTransform(), centerX, centerY, radius, startDegrees, sweepDegrees, gap, cornerRadius, color, alpha, 1.0F);
   }

   public static void fill(float x, float y, float width, float height, int color, float alpha, CornerRadii radii) {
      RoundedShapeRenderer.submit(getDrawContext(), getTransform(), x, y, width, height, radii, 0.0F, 0.0F, alpha, color, color, color, color, 0);
   }

   public static void outline(float x, float y, float width, float height, float thickness, int color, float alpha, CornerRadii radii) {
      RoundedShapeRenderer.submit(getDrawContext(), getTransform(), x, y, width, height, radii, 0.0F, thickness, alpha, color, color, color, color, 4);
   }

   public enum BlurQuality {
      LOW,
      MIDDLE,
      MAX;
   }
}
