package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.GuiBlurState;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.ColorSetting;
import com.mouseclient.render.pipeline.SliderSetting;
import com.mouseclient.render.pipeline.RoundedRectangleSpec;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.DrawContext;

public final class ClickGuiContext {
   public static final float DEFAULT_BLUR_RADIUS = 30.0F;
   public DrawContext drawContext;
   public float scale = 1.0F;
   public float mouseX;
   public float mouseY;
   private final Map<String, ScalarAnimation> animationsByKey = new HashMap<>();
   private final Map<Object, ScalarAnimation> animationsByIdentity = new IdentityHashMap<>();
   private final Map<ClickGuiContext.TextWrapKey, List<String>> wrappedTextCache;
   private final Map<Object, Boolean> flags;
   private final Map<Object, Object> stateByIdentity;
   private final Map<ColorSetting, float[]> colorStates;
   public final Map<Object, float[]> sliderBounds;
   public final Map<Object, float[]> colorPickerBounds;
   public final List<ClickGuiContext.ClickRegion> clickRegions;
   public int modalClickStart;
   public SliderSetting draggedSlider;
   public ColorSetting draggedColor;
   public int colorDragMode;
   public TextSetting focusedText;
   public BindSetting bindingSetting;
   public Module bindingModule;
   public ClickGuiContext.GuiInputHandler inputHandler;
   public String searchText;
   public Object popupOwner;
   public String hoveredTooltip;
   public String lastTooltip;
   public final List<Runnable> overlayRenderers;
   private Map<String, long[]> marqueeTimes;
   public Map<Object, Float> popupScrollOffsets;
   public Object scrollOwner;
   public float[] scrollBounds;
   public float maxPopupScroll;

   private List<String> wrappedLines(String text, float fontSize, float width) {
      ClickGuiContext.TextWrapKey cacheKey = new ClickGuiContext.TextWrapKey(text, fontSize, width);
      java.util.List<java.lang.String> lines = this.wrappedTextCache.get(cacheKey);
      if (lines == null) {
         lines = List.copyOf(GuiVisuals.wrapWords(GuiFont.INSTANCE, text, fontSize, width));
         this.wrappedTextCache.put(cacheKey, lines);
      }

      return lines;
   }

   public void drawTextInput(TextSetting setting, float x, float y, float fontSize, int color) {
      if (setting.hasSelection()) {
         Render2D.fillRoundedRectangle(
            x + GuiTextRenderer.getWidth(GuiFont.INSTANCE, setting.getValue().substring(0, setting.getSelectionStart()), fontSize),
            y - 1.0F * this.scale,
            GuiTextRenderer.getWidth(GuiFont.INSTANCE, setting.getValue().substring(setting.getSelectionStart(), setting.getSelectionEnd()), fontSize),
            fontSize + 2.0F * this.scale,
            1.0F * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.4F))
         );
      }

      GuiTextRenderer.draw(GuiFont.INSTANCE, setting.getValue(), x, y, fontSize, color);
      if (System.currentTimeMillis() / (530L) % (2L) == 0L) {
         Render2D.fillRoundedRectangle(
            x + GuiTextRenderer.getWidth(GuiFont.INSTANCE, setting.getValue().substring(0, setting.getCursor()), fontSize),
            y - (0.5F) * this.scale,
            (0.5F) * this.scale,
            fontSize + 1.0F * this.scale,
            (0.25F) * this.scale,
            color
         );
      }
   }

   public void drawLayeredPanel(float x, float y, float width, float height, float radius, int outlineColor) {
      Render2D.drawGuiBlur(x, y, width, height, radius);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.basePanelTint);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.flatPanelOverlay);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.panelHighlight);
      Render2D.drawOutline(x, y, width, height, radius, (0.3F), outlineColor);
   }

   public static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   public static String blinkingCursor() {
      return System.currentTimeMillis() / (500L) % (2L) == 0L ? ("_") : "";
   }

   public void drawTintedPanel(float x, float y, float width, float height, float radius, int fillColor, int outlineColor) {
      Render2D.drawGuiBlur(x, y, width, height, radius);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.basePanelTint);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.strongPanelTint);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, fillColor);
      Render2D.drawOutline(x, y, width, height, radius, (0.3F), outlineColor);
   }

   public float animateBoolean(String key, boolean enabled) {
      ScalarAnimation animation = this.animationsByKey.computeIfAbsent(key, cacheKey -> new ScalarAnimation((150L)).reset(enabled ? 1.0 : 0.0));
      animation.setTarget(enabled ? 1.0 : 0.0);
      return (float)animation.value();
   }

   public static float[] intersectClip(float[] clip, float x, float y, float width, float height) {
      if (clip == null) {
         return new float[]{x, y, Math.max(0.0F, width), Math.max(0.0F, height)};
      }

      float left = Math.max(clip[0], x);
      float top = Math.max(clip[1], y);
      float right = Math.min(clip[0] + clip[2], x + width);
      return new float[]{left, top, Math.max(0.0F, right - left), Math.max(0.0F, Math.min(clip[1] + clip[3], y + height) - top)};
   }

   public float openingProgress(String key) {
      ScalarAnimation animation = this.animationsByKey.computeIfAbsent(key, cacheKey -> new ScalarAnimation((440L)));
      animation.setTarget(1.0);
      return (float)animation.value();
   }

   public ClickGuiContext() {
      this.wrappedTextCache = new LinkedHashMap<ClickGuiContext.TextWrapKey, List<String>>(64, (0.75F), true) {
         @Override
         protected boolean removeEldestEntry(Entry<ClickGuiContext.TextWrapKey, List<String>> eldestEntry) {
            return this.size() > 256;
         }
      };
      this.flags = new HashMap<>();
      this.stateByIdentity = new IdentityHashMap<>();
      this.colorStates = new IdentityHashMap<>();
      this.sliderBounds = new IdentityHashMap<>();
      this.colorPickerBounds = new IdentityHashMap<>();
      this.clickRegions = new ArrayList<>();
      this.searchText = "";
      this.lastTooltip = "";
      this.overlayRenderers = new ArrayList<>();
      this.marqueeTimes = new HashMap<>();
      this.popupScrollOffsets = new IdentityHashMap<>();
   }

   public void drawIcon(String glyph, float x, float y, float size, int color) {
      GuiVisuals.drawCenteredIcon(glyph, x, y - (0.5F) * this.scale, size, color);
   }

   public boolean flagValue(Object key, boolean defaultValue) {
      return this.flags.getOrDefault(key, defaultValue);
   }

   public void toggleFlag(Object key, boolean defaultValue) {
      this.flags.put(key, !this.flagValue(key, defaultValue));
   }

   public void registerClick(float x, float y, float width, float height, float[] clip, Runnable action, Runnable rightAction) {
      this.registerClick(x, y, width, height, clip, action, rightAction, null);
   }

   public void renderOverlays() {
      if (!this.overlayRenderers.isEmpty()) {
         Render2D.nextLayer(this.drawContext);
         if (!GuiBlurState.isEnabled()) {
            GuiBlurState.setRequested(true);
         }

         this.drawContext.createNewRootLayer();

         try {
            this.drawContext.applyBlur();
         } catch (IllegalStateException error) {
         }

         Render2D.setSharedBlurLayer(1);

         for (Runnable renderer : List.copyOf(this.overlayRenderers)) {
            renderer.run();
         }

         Render2D.nextLayer(this.drawContext);
         Render2D.setSharedBlurLayer(0);
      }
   }

   public float[] hsbaState(ColorSetting setting) {
      return this.colorStates.computeIfAbsent(setting, colorSetting -> new float[]{colorSetting.getHue(), colorSetting.getSaturation(), colorSetting.getBrightness(), colorSetting.getAlpha() / (255.0F)});
   }

   public void registerClick(float x, float y, float width, float height, float[] clip, Runnable action) {
      this.registerClick(x, y, width, height, clip, action, null);
   }

   public void drawSettingLabel(String name, String description, float x, float y, float width, float enabledProgress) {
      this.drawMarqueeText(name, x, y, (5.5F) * this.scale, GuiVisuals.lerpColor(GuiVisuals.brightTextColor, GuiVisuals.white, enabledProgress), width);
      if (description != null && !description.isEmpty()) {
         float descriptionY = y + (8.0F) * this.scale;

         for (String line : this.wrappedLines(description, (5.0F) * this.scale, width)) {
            GuiTextRenderer.draw(
               GuiFont.INSTANCE, line, x, descriptionY, (5.0F) * this.scale, GuiVisuals.lerpColor(GuiVisuals.mutedTextColor, GuiVisuals.brightTextColor, enabledProgress)
            );
            descriptionY += (6.0F) * this.scale;
         }
      }
   }

   public float settingLabelHeight(String description, float width) {
      int lineCount = this.descriptionLineCount(description, width);
      return (7.0F) * this.scale + (lineCount > 0 ? 1.0F * this.scale + lineCount * (6.0F) * this.scale : 0.0F);
   }

   public void drawStrongTintPanel(float x, float y, float width, float height, float radius, int fillColor, int outlineColor) {
      Render2D.drawGuiBlur(x, y, width, height, radius, (-1090519041));
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.basePanelTint);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ArgbColor.multiplyAlpha(fillColor, (0.8F)));
      Render2D.drawOutline(x, y, width, height, radius, (0.5F), outlineColor);
   }

   public void applyHsba(ColorSetting setting, float[] hsba) {
      setting.setArgb(Math.round(Math.max(0.0F, Math.min(1.0F, hsba[3])) * (255.0F)) << 24 | Color.HSBtoRGB(hsba[0], hsba[1], hsba[2]) & (16777215));
   }

   public void registerClick(float x, float y, float width, float height, float[] clip, Runnable action, Runnable rightAction, Runnable middleAction) {
      if (clip != null) {
         float right = Math.min(x + width, clip[0] + clip[2]);
         float bottom = Math.min(y + height, clip[1] + clip[3]);
         x = Math.max(x, clip[0]);
         y = Math.max(y, clip[1]);
         width = right - x;
         height = bottom - y;
         if (width <= 0.0F || height <= 0.0F) {
            return;
         }
      }

      this.clickRegions.add(new ClickGuiContext.ClickRegion(x, y, width, height, action, rightAction, middleAction));
   }

   public float animateValue(String key, float target) {
      ScalarAnimation animation = this.animationsByKey.computeIfAbsent(key, cacheKey -> new ScalarAnimation((150L)).reset(target));
      animation.setTarget(target);
      return (float)animation.value();
   }

   public static float snapToPixel(float coordinate) {
      float pixelScale = GuiScale.getBaseScale() / GuiScale.getWindowScale();
      return pixelScale > (1.0E-4F) ? Math.round(coordinate * pixelScale) / pixelScale : coordinate;
   }

   public <T> T stateFor(Object key, T initialState) {
      return (T)this.stateByIdentity.computeIfAbsent(key, stateKey -> initialState);
   }

   public void drawFlatTintPanel(float x, float y, float width, float height, float radius, int fillColor, int outlineColor) {
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.strongPanelTint);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, fillColor);
      Render2D.drawOutline(x, y, width, height, radius, (0.3F), outlineColor);
   }

   public static float toGuiUnits(float coordinate) {
      return GuiScale.toGuiUnits(coordinate);
   }

   public void drawSoftTintPanel(float x, float y, float width, float height, float radius, int fillColor, int outlineColor) {
      Render2D.drawGuiBlur(x, y, width, height, radius);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.basePanelTint);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ArgbColor.multiplyAlpha(fillColor, (0.8F)));
      Render2D.drawOutline(x, y, width, height, radius, (0.5F), outlineColor);
   }

   public float animateObjectBoolean(Object key, boolean enabled) {
      ScalarAnimation animation = this.animationsByIdentity.get(key);
      if (animation == null) {
         this.animationsByIdentity.put(key, animation = new ScalarAnimation((220L)).reset(enabled ? 1.0 : 0.0));
      }

      animation.setTarget(enabled ? 1.0 : 0.0);
      return (float)animation.value();
   }

   public void drawRotatedIcon(String glyph, float x, float y, float size, int color, float rotation) {
      if (Math.abs(rotation) < (0.01F)) {
         GuiVisuals.drawCenteredIcon(glyph, x, y - (0.5F) * this.scale, size, color);
      } else {
         float iconY = y - (0.5F) * this.scale;
         float[] glyphBounds = Render2D.getMsdfTextBounds(("icons"), glyph, size);
         float glyphX = x - (glyphBounds[0] + glyphBounds[2]) / 2.0F;
         Render2D.drawRotatedMsdfText(("icons"), glyph, glyphX, iconY - (glyphBounds[1] + glyphBounds[3]) / 2.0F + 1.0F, size, color, rotation, x, iconY + 1.0F);
      }
   }

   public void drawMarqueeText(String text, float x, float y, float fontSize, int color, float width) {
      float overflowWidth = GuiTextRenderer.getWidth(GuiFont.INSTANCE, text, fontSize) - width;
      if (overflowWidth <= 0.0F) {
         GuiTextRenderer.draw(GuiFont.INSTANCE, text, x, y, fontSize, color);
      } else {
         String animationKey = "marquee:" + text + ":" + Math.round(x) + ":" + Math.round(width);
         boolean hovered = GuiVisuals.containsPoint(
            this.mouseX, this.mouseY, x, y - (1.5F) * this.scale, width, fontSize + (3.0F) * this.scale
         );
         long nowMillis = System.currentTimeMillis();
         long[] hoverTimes = this.marqueeTimes.get(animationKey);
         float targetProgress = 0.0F;
         if (hovered) {
            if (hoverTimes == null) {
               this.marqueeTimes.put(animationKey, hoverTimes = new long[]{nowMillis, nowMillis});
            }

            if (nowMillis - hoverTimes[1] > (120L)) {
               hoverTimes[0] = nowMillis;
            }

            hoverTimes[1] = nowMillis;
            long travelMillis = Math.max((700L), (long)(overflowWidth / this.scale * (55.0F)));
            long pauseMillis = (650L);
            long elapsedMillis = nowMillis - hoverTimes[0] - (100L);
            long cycleMillis = Math.max(0L, elapsedMillis) % ((2L) * (travelMillis + pauseMillis));
            targetProgress = elapsedMillis < 0L
               ? 0.0F
               : (
                  cycleMillis < travelMillis
                     ? (float)cycleMillis / (float)travelMillis
                     : (cycleMillis < travelMillis + pauseMillis ? 1.0F : (cycleMillis < (2L) * travelMillis + pauseMillis ? 1.0F - (float)(cycleMillis - travelMillis - pauseMillis) / (float)travelMillis : 0.0F))
               );
            targetProgress = targetProgress * targetProgress * ((3.0F) - 2.0F * targetProgress);
         }

         boolean recentlyHovered = hoverTimes != null && nowMillis - hoverTimes[1] < (400L);
         float scrollOffset = recentlyHovered ? overflowWidth * this.animateValue(animationKey, targetProgress) : 0.0F;
         float fadeWidth = (4.0F) * this.scale;
         GuiTextRenderer.drawFaded(
            GuiFont.INSTANCE,
            text,
            x - scrollOffset,
            y,
            fontSize,
            color,
            x,
            x + width,
            fadeWidth,
            clamp(scrollOffset / fadeWidth, 0.0F, 1.0F),
            clamp((overflowWidth - scrollOffset) / fadeWidth, 0.0F, 1.0F)
         );
      }
   }

   public int descriptionLineCount(String description, float width) {
      return description != null && !description.isEmpty() ? this.wrappedLines(description, (5.0F) * this.scale, width).size() : 0;
   }

   public void drawSharedBlurPanel(float x, float y, float width, float height, float radius, int outlineColor) {
      Render2D.drawSharedBlur(x, y, width, height, radius, (30.0F), 1.0F, ClickGuiTheme.blurTint);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.basePanelTint);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.opaquePanelTint);
      Render2D.drawOutline(x, y, width, height, radius, (0.5F), outlineColor);
   }

   public void offerTooltip(String text, float x, float y, float width, float height) {
      if (text != null && !text.isEmpty()) {
         if (GuiVisuals.containsPoint(this.mouseX, this.mouseY, x, y, width, height)) {
            this.hoveredTooltip = text;
         }
      }
   }

   public void beginFrame(DrawContext drawContext, float scale, float mouseX, float mouseY) {
      this.drawContext = drawContext;
      this.scale = scale;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      this.clickRegions.clear();
      this.modalClickStart = 0;
      this.sliderBounds.clear();
      this.colorPickerBounds.clear();
      this.hoveredTooltip = null;
      this.overlayRenderers.clear();
      this.scrollOwner = null;
      this.scrollBounds = null;
   }

   public void drawToggle(float x, float y, float progress) {
      float toggleWidth = (10.74F) * this.scale;
      float toggleHeight = (6.64F) * this.scale;
      float thumbDiameter = (4.88F) * this.scale;
      Render2D.fillRoundedRectangle(
         new RoundedRectangleSpec(
               x,
               y,
               toggleWidth,
               toggleHeight,
               toggleHeight / 2.0F,
               toggleHeight / 2.0F,
               toggleHeight / 2.0F,
               toggleHeight / 2.0F,
               Render2D.applyAlpha(GuiVisuals.lerpColor(ClickGuiTheme.disabledModuleCheckbox, ClickGuiTheme.enabledModuleCheckbox, progress))
            )
            .withSmoothness((0.5F))
      );
      Render2D.fillCircle(
         x + (0.93F) * this.scale + progress * (toggleWidth - thumbDiameter - (1.86F) * this.scale) + thumbDiameter / 2.0F,
         y + toggleHeight / 2.0F,
         thumbDiameter / 2.0F,
         (0.5F),
         Render2D.applyAlpha(GuiVisuals.lerpColor(ClickGuiTheme.disabledModuleText, GuiVisuals.accentColor, progress))
      );
   }

   public interface GuiInputHandler {
      public void keyPressed(int keyCode);

      public void charTyped(char character);
   }

   public record ClickRegion(float x, float y, float width, float height, Runnable action, Runnable rightAction, Runnable middleAction) {
      public float getHeight() {
         return this.height;
      }

      public float getWidth() {
         return this.width;
      }
   }

   private record TextWrapKey(String text, float size, float width) {
   }
}
