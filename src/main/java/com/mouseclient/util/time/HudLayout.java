package com.mouseclient.util.time;

import com.mouseclient.gui.OutlineColorRange;
import com.mouseclient.gui.Render2D;
import com.mouseclient.internal.GuiScale;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

public final class HudLayout {
   private static final Map<String, float[]> savedAnchors = new HashMap<>();
   private static final Map<String, float[]> elementSettings = new HashMap<>();
   private static final Map<String, HudLayout.ElementBounds> frameBounds = new LinkedHashMap<>();
   private static final Map<String, float[]> elementMetrics = new HashMap<>();
   private static final Map<String, float[]> elementCenters = new HashMap<>();
   private static final Map<String, Long> hoverStartedAtMillis = new HashMap<>();
   private static final Map<String, Float> pinnedCenters = new HashMap<>();
   private static final Map<String, float[]> pinnedBounds = new HashMap<>();
   private static final ScalarAnimation snapGuideAnimation = new ScalarAnimation((160L)).reset(0.0);
   private static final ScalarAnimation dragHintAnimation = new ScalarAnimation((160L)).reset(0.0);
   private static final Map<String, ScalarAnimation> scaleAnimations = new HashMap<>();
   private static final Map<String, ScalarAnimation> resizeHandleAnimations = new HashMap<>();
   private static final ScalarAnimation scaleHintAnimation = new ScalarAnimation((160L)).reset(0.0);
   private static float[] lastDraggedBounds;
   private static float[] lastResizedBounds;
   private static String lastResizedElement;
   private static final List<String> elementOrder = new ArrayList<>(
      List.of(
         ("watermark"), ("keybinds"), ("serverbinds"), ("potions"), ("cooldowns"), ("staffs"), ("target"), ("inventory"), ("Target"), ("arrows")
      )
   );
   private static String draggedElement;
   private static String resizedElement;
   private static float dragOffsetX;
   private static float dragOffsetY;
   private static float resizeStartX;
   private static float resizeStartY;
   private static float resizeStartScale;
   private static float resizeHalfWidth;
   private static float resizeHalfHeight;
   private static float screenWidth;
   private static float screenHeight;
   private static boolean loaded;
   private static Path configPath;

   public static List<String> elementOrder() {
      return elementOrder;
   }

   public static boolean isPinned(String elementId) {
      return settingsFor(elementId)[3] > (0.5F);
   }

   public static String elementAt(float mouseX, float mouseY) {
      java.util.ArrayList<java.util.Map.Entry<java.lang.String,com.mouseclient.util.time.HudLayout.ElementBounds>> entries = new ArrayList<>(frameBounds.entrySet());

      for (int var3 = entries.size() - 1; var3 >= 0; var3--) {
         HudLayout.ElementBounds bounds = (HudLayout.ElementBounds)((Entry)entries.get(var3)).getValue();
         if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width && mouseY >= bounds.y && mouseY <= bounds.y + bounds.height) {
            return (String)((Entry)entries.get(var3)).getKey();
         }
      }

      return null;
   }

   public static boolean isAutomaticPlacementActive(String elementId) {
      return false;
   }

   private static float[] settingsFor(String elementId) {
      ensureLoaded();
      return elementSettings.computeIfAbsent(elementId, hudElementId -> new float[]{(0.9F), 1.0F, 0.0F, 0.0F});
   }

   public static void setPinned(String elementId, boolean pinned) {
      settingsFor(elementId)[3] = pinned ? 1.0F : 0.0F;
      if (pinned) {
         pinnedCenters.put(elementId, elementCenters.containsKey(elementId) ? elementCenters.get(elementId)[0] : screenWidth / 2.0F);
      } else {
         pinnedCenters.remove(elementId);
      }

      save();
   }

   public static float screenWidth() {
      return screenWidth;
   }

   public static float[] boundsFor(String elementId) {
      HudLayout.ElementBounds bounds = frameBounds.get(elementId);
      return bounds == null ? null : new float[]{bounds.x, bounds.y, bounds.width, bounds.height};
   }

   public static float screenHeight() {
      return screenHeight;
   }

   public static float[] resolveAnchor(String elementId, float defaultX, float defaultY) {
      ensureLoaded();
      float[] savedAnchor = savedAnchors.get(elementId);
      float anchorX = savedAnchor != null ? savedAnchor[0] : defaultX;
      float anchorY = savedAnchor != null ? savedAnchor[1] : defaultY;
      if (!(screenWidth <= 0.0F) && !(screenHeight <= 0.0F)) {
         float[] metrics = elementMetrics.get(elementId);
         float elementWidth = metrics != null ? metrics[0] : 0.0F;
         float elementHeight = metrics != null ? metrics[1] : 0.0F;
         float anchorOffsetX = metrics != null ? metrics[2] : 0.0F;
         float anchorOffsetY = metrics != null ? metrics[3] : 0.0F;
         if (isPinned(elementId) && metrics != null && pinnedCenters.containsKey(elementId)) {
            anchorX = pinnedCenters.get(elementId) + anchorOffsetX - elementWidth / 2.0F;
         }

         float left = Math.max((5.0F), Math.min(Math.max((5.0F), screenWidth - elementWidth - (5.0F)), anchorX - anchorOffsetX));
         float top = Math.max((5.0F), Math.min(Math.max((5.0F), screenHeight - elementHeight - (5.0F)), anchorY - anchorOffsetY));
         if (metrics != null) {
            top = avoidPinnedOverlap(elementId, left, top, elementWidth, elementHeight);
         }

         return new float[]{left + anchorOffsetX, top + anchorOffsetY};
      } else {
         return new float[]{anchorX, anchorY};
      }
   }

   public static void setHiddenWithF1(String elementId, boolean hidden) {
      settingsFor(elementId)[2] = hidden ? 1.0F : 0.0F;
      save();
   }

   public static void setScale(String elementId, float scale) {
      settingsFor(elementId)[0] = Math.max((0.25F), Math.min(2.0F, scale));
   }

   public static float[] centerFor(String elementId) {
      return elementCenters.get(elementId);
   }

   private static void readJson(JsonObject json) {
      for (String elementId : json.keySet()) {
         JsonObject properties = json.getAsJsonObject(elementId);
         if (properties.has(("x")) && properties.has(("y"))) {
            savedAnchors.put(elementId, new float[]{properties.get(("x")).getAsFloat(), properties.get(("y")).getAsFloat()});
         }

         if (properties.has(("scale")) || properties.has(("auto")) || properties.has(("hideF1")) || properties.has(("pin"))) {
            float[] settings = elementSettings.computeIfAbsent(elementId, hudElementId -> new float[]{(0.9F), 1.0F, 0.0F, 0.0F});
            if (properties.has(("scale"))) {
               settings[0] = properties.get(("scale")).getAsFloat();
            }

            if (properties.has(("auto"))) {
               settings[1] = properties.get(("auto")).getAsBoolean() ? 1.0F : 0.0F;
            }

            if (properties.has(("hideF1"))) {
               settings[2] = properties.get(("hideF1")).getAsBoolean() ? 1.0F : 0.0F;
            }

            if (properties.has(("pin"))) {
               settings[3] = properties.get(("pin")).getAsBoolean() ? 1.0F : 0.0F;
            }
         }
      }
   }

   public static void mouseReleased(int button) {
      if (button == 0 && draggedElement != null) {
         save();
         draggedElement = null;
      }

      if (button == 0 && resizedElement != null) {
         save();
         resizedElement = null;
      }
   }

   private static float avoidPinnedOverlap(String elementId, float x, float y, float width, float height) {
      for (Entry entry : pinnedBounds.entrySet()) {
         if (!((String)entry.getKey()).equals(elementId) && (!isPinned(elementId) || elementPriority((String)entry.getKey()) < elementPriority(elementId))) {
            float[] bounds = (float[])entry.getValue();
            if (x < bounds[0] + bounds[2] && x + width > bounds[0] && y < bounds[1] + bounds[3] && y + height > bounds[1]) {
               y = bounds[1] + bounds[3] + (4.0F);
            }
         }
      }

      return Math.max((5.0F), Math.min(Math.max((5.0F), screenHeight - height - (5.0F)), y));
   }

   private HudLayout() {
   }

   public static void registerBounds(String elementId, float x, float y, float width, float height, float anchorX, float anchorY) {
      float scale = animatedScale(elementId);
      float centerX = x + width / 2.0F;
      float centerY = y + height / 2.0F;
      HudLayout.ElementBounds bounds = new HudLayout.ElementBounds();
      bounds.x = centerX - width * scale / 2.0F;
      bounds.y = centerY - height * scale / 2.0F;
      bounds.width = width * scale;
      bounds.height = height * scale;
      bounds.anchorX = anchorX;
      bounds.anchorY = anchorY;
      frameBounds.put(elementId, bounds);
      elementMetrics.put(elementId, new float[]{bounds.width, bounds.height, anchorX - bounds.x, anchorY - bounds.y});
      elementCenters.put(elementId, new float[]{centerX, centerY});
      if (isPinned(elementId)) {
         pinnedBounds.put(elementId, new float[]{bounds.x, bounds.y, bounds.width, bounds.height});
         pinnedCenters.putIfAbsent(elementId, centerX);
      } else {
         pinnedBounds.remove(elementId);
      }
   }

   public static boolean isDragging() {
      return draggedElement != null;
   }

   public static boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button != 0) {
         return false;
      }

      float coordinateScale = inputScale();
      float hudMouseX = (float)(mouseX * coordinateScale);
      float hudMouseY = (float)(mouseY * coordinateScale);
      String resizeTarget = resizeHandleAt(hudMouseX, hudMouseY);
      if (resizeTarget != null) {
         HudLayout.ElementBounds resizeBounds = frameBounds.get(resizeTarget);
         resizedElement = resizeTarget;
         resizeStartX = hudMouseX;
         resizeStartY = hudMouseY;
         resizeStartScale = Math.max((0.25F), Math.min(2.0F, scaleFor(resizeTarget)));
         resizeHalfWidth = Math.max(1.0F, resizeBounds.width / animatedScale(resizeTarget) / 2.0F);
         resizeHalfHeight = Math.max(1.0F, resizeBounds.height / animatedScale(resizeTarget) / 2.0F);
         if (elementOrder.remove(resizedElement)) {
            elementOrder.add(resizedElement);
         }

         return true;
      } else {
         String dragTarget = elementAt(hudMouseX, hudMouseY);
         if (dragTarget == null) {
            return false;
         }

         HudLayout.ElementBounds dragBounds = frameBounds.get(dragTarget);
         draggedElement = dragTarget;
         dragOffsetX = hudMouseX - dragBounds.anchorX;
         dragOffsetY = hudMouseY - dragBounds.anchorY;
         if (elementOrder.remove(draggedElement)) {
            elementOrder.add(draggedElement);
         }

         return true;
      }
   }

   private static void ensureLoaded() {
      if (!loaded) {
         loaded = true;
         configPath = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("draggables.json"));

         try {
            if (!Files.exists(configPath)) {
               return;
            }

            readJson(JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8)).getAsJsonObject());
         } catch (Exception error) {
         }
      }
   }

   public static JsonObject toJson() {
      ensureLoaded();
      JsonObject json = new JsonObject();
      java.util.LinkedHashSet<java.lang.String> elementIds = new LinkedHashSet<>(savedAnchors.keySet());
      elementIds.addAll(elementSettings.keySet());

      for (String elementId : elementIds) {
         JsonObject properties = new JsonObject();
         float[] anchor = savedAnchors.get(elementId);
         if (anchor != null) {
            properties.addProperty(("x"), anchor[0]);
            properties.addProperty(("y"), anchor[1]);
         }

         float[] settings = elementSettings.get(elementId);
         if (settings != null) {
            properties.addProperty(("scale"), settings[0]);
            properties.addProperty(("auto"), settings[1] > (0.5F));
            properties.addProperty(("hideF1"), settings[2] > (0.5F));
            properties.addProperty(("pin"), settings.length > 3 && settings[3] > (0.5F));
         }

         json.add(elementId, properties);
      }

      return json;
   }

   public static void updateDrag(float mouseX, float mouseY, boolean editing, float width, float height) {
      screenWidth = width;
      screenHeight = height;
      if (!editing) {
         if (draggedElement != null) {
            save();
            draggedElement = null;
         }

         if (resizedElement != null) {
            save();
            resizedElement = null;
         }

         hoverStartedAtMillis.clear();
      } else {
         if (resizedElement != null) {
            setScale(resizedElement, resizeStartScale + ((mouseX - resizeStartX) / resizeHalfWidth + (mouseY - resizeStartY) / resizeHalfHeight) / 2.0F);
         }

         if (draggedElement != null) {
            float[] metrics = elementMetrics.get(draggedElement);
            float anchorOffsetX = metrics != null ? metrics[2] : 0.0F;
            float anchorOffsetY = metrics != null ? metrics[3] : 0.0F;
            float newAnchorX = mouseX - dragOffsetX;
            float newAnchorY = mouseY - dragOffsetY;
            if (isSnapKeyDown() && metrics != null) {
               if (Math.abs(newAnchorX - anchorOffsetX + metrics[0] / 2.0F - screenWidth / 2.0F) < (8.0F)) {
                  newAnchorX = screenWidth / 2.0F - metrics[0] / 2.0F + anchorOffsetX;
               }

               if (Math.abs(newAnchorY - anchorOffsetY + metrics[1] / 2.0F - screenHeight / 2.0F) < (8.0F)) {
                  newAnchorY = screenHeight / 2.0F - metrics[1] / 2.0F + anchorOffsetY;
               }
            }

            savedAnchors.put(draggedElement, new float[]{newAnchorX, newAnchorY});
            if (isPinned(draggedElement) && metrics != null) {
               pinnedCenters.put(draggedElement, newAnchorX - anchorOffsetX + metrics[0] / 2.0F);
            }
         }
      }
   }

   private static String resizeHandleAt(float mouseX, float mouseY) {
      java.util.ArrayList<java.util.Map.Entry<java.lang.String,com.mouseclient.util.time.HudLayout.ElementBounds>> entries = new ArrayList<>(frameBounds.entrySet());

      for (int var3 = entries.size() - 1; var3 >= 0; var3--) {
         HudLayout.ElementBounds bounds = (HudLayout.ElementBounds)((Entry)entries.get(var3)).getValue();
         float handleSize = Math.min((9.0F), Math.min(bounds.width, bounds.height) / (3.0F));
         if (mouseX >= bounds.x + bounds.width - handleSize
            && mouseX <= bounds.x + bounds.width + (4.0F)
            && mouseY >= bounds.y + bounds.height - handleSize
            && mouseY <= bounds.y + bounds.height + (4.0F)) {
            return (String)((Entry)entries.get(var3)).getKey();
         }
      }

      return null;
   }

   public static float animatedScale(String elementId) {
      float targetScale = Math.max((0.25F), Math.min(2.0F, settingsFor(elementId)[0]));
      ScalarAnimation animation = scaleAnimations.computeIfAbsent(elementId, hudElementId -> new ScalarAnimation((140L)).reset(targetScale));
      animation.setTarget(targetScale);
      return (float)animation.value();
   }

   private static int elementPriority(String elementId) {
      int index = elementOrder.indexOf(elementId);
      return index < 0 ? (2147483647) : index;
   }

   public static boolean isHiddenWithF1(String elementId) {
      return settingsFor(elementId)[2] > (0.5F);
   }

   private static void save() {
      ensureLoaded();

      try {
         ConfigStore.writeAsync(configPath, toJson().toString());
      } catch (Exception error) {
      }
   }

   public static float scaleFor(String elementId) {
      return settingsFor(elementId)[0];
   }

   public static void clearFrameBounds() {
      frameBounds.clear();
   }

   public static void saveNow() {
      save();
   }

   public static void renderEditOverlay(float mouseX, float mouseY, boolean editing) {
      if (editing) {
         snapGuideAnimation.setTarget(isSnapKeyDown() && draggedElement != null ? 1.0 : 0.0);
         float snapAlpha = (float)snapGuideAnimation.value();
         if (snapAlpha > (0.01F) && screenWidth > 0.0F && screenHeight > 0.0F) {
            Render2D.fillRoundedRectangle(
               screenWidth / 2.0F - (0.25F),
               screenHeight / 2.0F * (1.0F - snapAlpha),
               (0.5F),
               screenHeight * snapAlpha,
               0.0F,
               GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.14F) * snapAlpha)
            );
            Render2D.fillRoundedRectangle(
               screenWidth / 2.0F * (1.0F - snapAlpha),
               screenHeight / 2.0F - (0.25F),
               screenWidth * snapAlpha,
               (0.5F),
               0.0F,
               GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.14F) * snapAlpha)
            );
         }

         long nowMillis = System.currentTimeMillis();

         for (Entry hoverEntry : frameBounds.entrySet()) {
            HudLayout.ElementBounds hoverBounds = (HudLayout.ElementBounds)hoverEntry.getValue();
            boolean hovered = ((String)hoverEntry.getKey()).equals(draggedElement)
               || mouseX >= hoverBounds.x
                  && mouseX <= hoverBounds.x + hoverBounds.width
                  && mouseY >= hoverBounds.y
                  && mouseY <= hoverBounds.y + hoverBounds.height;
            if (!hovered) {
               hoverStartedAtMillis.remove(hoverEntry.getKey());
            } else {
               float hoverProgress = Math.min(1.0F, Math.max((0.02F), (float)(nowMillis - hoverStartedAtMillis.computeIfAbsent((String)hoverEntry.getKey(), hudElementId -> nowMillis)) / (50.0F)));
               Render2D.drawOutlineRanges(
                  hoverBounds.x - (1.5F),
                  hoverBounds.y - (1.5F),
                  hoverBounds.width + (3.0F),
                  hoverBounds.height + (3.0F),
                  (9.5F),
                  (0.5F),
                  0,
                  OutlineColorRange.of((90.0F) - hoverProgress * (360.0F), (90.0F), GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.3F)))
               );
            }
         }

         String hoveredResizeElement = resizeHandleAt(mouseX, mouseY);

         for (Entry handleEntry : frameBounds.entrySet()) {
            HudLayout.ElementBounds handleBounds = (HudLayout.ElementBounds)handleEntry.getValue();
            ScalarAnimation handleAnimation = resizeHandleAnimations.computeIfAbsent((String)handleEntry.getKey(), hudElementId -> new ScalarAnimation((160L)).reset(0.0));
            handleAnimation.setTarget(
               !((String)handleEntry.getKey()).equals(resizedElement)
                     && (
                        !(mouseX >= handleBounds.x - (4.0F))
                           || !(mouseX <= handleBounds.x + handleBounds.width + (4.0F))
                           || !(mouseY >= handleBounds.y - (4.0F))
                           || !(mouseY <= handleBounds.y + handleBounds.height + (4.0F))
                     )
                  ? 0.0
                  : 1.0
            );
            float handleProgress = (float)handleAnimation.value();
            if (!(handleProgress < (0.01F))) {
               float handleAlpha = handleProgress * (!((String)handleEntry.getKey()).equals(resizedElement) && !((String)handleEntry.getKey()).equals(hoveredResizeElement) ? (0.45F) : (0.9F));
               float handleX = handleBounds.x + handleBounds.width + (1.5F);
               float handleY = handleBounds.y + handleBounds.height + (1.5F);

               for (int var15 = 0; var15 < 3; var15++) {
                  for (int var16 = 0; var16 < 3; var16++) {
                     if (var15 + var16 >= 2) {
                        Render2D.fillRoundedRectangle(
                           handleX - (2.6F) - (2 - var15) * (2.2F),
                           handleY - (2.6F) - (2 - var16) * (2.2F),
                           (1.1F),
                           (1.1F),
                           (0.55F),
                           GuiVisuals.multiplyAlpha(GuiVisuals.white, handleAlpha)
                        );
                     }
                  }
               }
            }
         }

         scaleHintAnimation.setTarget(resizedElement != null && frameBounds.containsKey(resizedElement) ? 1.0 : 0.0);
         if (resizedElement != null && frameBounds.containsKey(resizedElement)) {
            lastResizedBounds = boundsFor(resizedElement);
            lastResizedElement = resizedElement;
         }

         float scaleHintAlpha = (float)scaleHintAnimation.value();
         if (scaleHintAlpha > (0.01F) && lastResizedBounds != null) {
            String scaleLabel = Math.round(scaleFor(lastResizedElement) * (100.0F)) + "%";
            float scaleLabelWidth = GuiTextRenderer.getWidth(GuiFont.SEMI, scaleLabel, (5.0F));
            float scaleLabelX = lastResizedBounds[0] + lastResizedBounds[2] + (5.0F);
            if (scaleLabelX + scaleLabelWidth > screenWidth - 2.0F) {
               scaleLabelX = lastResizedBounds[0] + lastResizedBounds[2] - (10.0F) - scaleLabelWidth;
            }

            GuiTextRenderer.draw(
               GuiFont.SEMI,
               scaleLabel,
               scaleLabelX,
               lastResizedBounds[1] + lastResizedBounds[3] + (3.0F) + (1.0F - scaleHintAlpha) * 2.0F,
               (5.0F),
               GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.8F) * scaleHintAlpha)
            );
         }

         dragHintAnimation.setTarget(draggedElement != null && frameBounds.containsKey(draggedElement) ? 1.0 : 0.0);
         if (draggedElement != null && frameBounds.containsKey(draggedElement)) {
            lastDraggedBounds = boundsFor(draggedElement);
         }

         float dragHintAlpha = (float)dragHintAnimation.value();
         if (dragHintAlpha > (0.01F) && lastDraggedBounds != null) {
            GuiTextRenderer.draw(
               GuiFont.SEMI,
               ("Зажмите LCTRL для перемещения по сетке"),
               lastDraggedBounds[0] + lastDraggedBounds[2] / 2.0F - GuiTextRenderer.getWidth(GuiFont.SEMI, ("Зажмите LCTRL для перемещения по сетке"), (5.0F)) / 2.0F,
               lastDraggedBounds[1] + lastDraggedBounds[3] + (4.0F) + (1.0F - dragHintAlpha) * (3.0F),
               (5.0F),
               GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.7F) * dragHintAlpha)
            );
         }
      }
   }

   private static float inputScale() {
      return GuiScale.getWindowScale() / GuiScale.getBaseScale();
   }

   public static boolean isSnapKeyDown() {
      return MinecraftClient.getInstance().getWindow() != null && InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow(), 341);
   }

   public static void registerBounds(String elementId, float x, float y, float width, float height) {
      registerBounds(elementId, x, y, width, height, x, y);
   }

   public static boolean hasSavedPosition(String elementId) {
      ensureLoaded();
      return savedAnchors.containsKey(elementId);
   }

   public static float[] toHudCoordinates(double mouseX, double mouseY) {
      float coordinateScale = inputScale();
      return new float[]{(float)(mouseX * coordinateScale), (float)(mouseY * coordinateScale)};
   }

   public static void loadJson(JsonObject json) {
      ensureLoaded();
      savedAnchors.clear();
      elementSettings.clear();
      pinnedCenters.clear();
      readJson(json);
      save();
   }

   public static void setAutoScale(String elementId, boolean enabled) {
      settingsFor(elementId)[1] = enabled ? 1.0F : 0.0F;
      save();
   }

   public static void finishEditing() {
      if (draggedElement != null) {
         save();
         draggedElement = null;
      }

      if (resizedElement != null) {
         save();
         resizedElement = null;
      }

      hoverStartedAtMillis.clear();
   }

   private static final class ElementBounds {
      public float x;
      public float y;
      public float width;
      public float height;
      public float anchorX;
      public float anchorY;
   }
}
