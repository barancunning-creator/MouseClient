package com.mouseclient.gui;

import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.util.time.HudLayout;
import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.Setting;
import com.mouseclient.util.time.SettingRendererRegistry;
import com.mouseclient.util.time.ScalarAnimation;
import java.util.ArrayList;

public final class HudSettingsPopup {
   private static final ScalarAnimation visibilityAnimation = new ScalarAnimation((180L));
   private static final ClickGuiContext ctx = new ClickGuiContext();
   private static String elementId;
   private static boolean open;
   private static float x;
   private static float y;
   private static float height = (60.0F);
   private static boolean dragging;
   private static float dragOffsetX;
   private static float dragOffsetY;
   private static String pendingElementId;
   private static float pendingX;
   private static float pendingY;

   public static boolean mouseClicked(double mouseX, double mouseY, int button) {
      float[] hudCoordinates = HudLayout.toHudCoordinates(mouseX, mouseY);
      float hudMouseX = hudCoordinates[0];
      float hudMouseY = hudCoordinates[1];
      boolean insidePopup = elementId != null
         && open
         && hudMouseX >= x
         && hudMouseX <= x + (103.3F)
         && hudMouseY >= y
         && hudMouseY <= y + height;
      if (button == 1) {
         if (insidePopup) {
            for (int rightRegionIndex = ctx.clickRegions.size() - 1; rightRegionIndex >= 0; rightRegionIndex--) {
               ClickGuiContext.ClickRegion rightRegion = ctx.clickRegions.get(rightRegionIndex);
               if (rightRegion.rightAction() != null && GuiVisuals.containsPoint(hudMouseX, hudMouseY, rightRegion.x(), rightRegion.y(), rightRegion.getWidth(), rightRegion.getHeight())) {
                  rightRegion.rightAction().run();
                  return true;
               }
            }

            close();
            return true;
         } else {
            String clickedElementId = HudLayout.elementAt(hudMouseX, hudMouseY);
            if (clickedElementId != null && !getSettings(clickedElementId).isEmpty()) {
               open(clickedElementId, hudMouseX, hudMouseY);
               return true;
            } else if (elementId != null && open) {
               close();
               return true;
            } else {
               return false;
            }
         }
      } else if (button == 0 && elementId != null && open) {
         if (ctx.popupOwner != null) {
            for (int overlayRegionIndex = ctx.clickRegions.size() - 1; overlayRegionIndex >= 0; overlayRegionIndex--) {
               ClickGuiContext.ClickRegion overlayRegion = ctx.clickRegions.get(overlayRegionIndex);
               if (GuiVisuals.containsPoint(hudMouseX, hudMouseY, overlayRegion.x(), overlayRegion.y(), overlayRegion.getWidth(), overlayRegion.getHeight())) {
                  overlayRegion.action().run();
                  return true;
               }
            }

            return true;
         } else {
            if (!insidePopup) {
               close();
               return false;
            }

            for (int leftRegionIndex = ctx.clickRegions.size() - 1; leftRegionIndex >= 0; leftRegionIndex--) {
               ClickGuiContext.ClickRegion leftRegion = ctx.clickRegions.get(leftRegionIndex);
               if (GuiVisuals.containsPoint(hudMouseX, hudMouseY, leftRegion.x(), leftRegion.y(), leftRegion.getWidth(), leftRegion.getHeight())) {
                  leftRegion.action().run();
                  return true;
               }
            }

            dragging = true;
            dragOffsetX = hudMouseX - x;
            dragOffsetY = hudMouseY - y;
            return true;
         }
      } else {
         return false;
      }
   }

   public static void render(float mouseX, float mouseY, boolean chatOpen) {
      visibilityAnimation.setTarget(open && chatOpen && elementId != null ? 1.0 : 0.0);
      float visibilityProgress = (float)visibilityAnimation.value();
      if (elementId != null && !(visibilityProgress < (0.01F))) {
         if (dragging) {
            x = Math.max((5.0F), Math.min(Math.max((5.0F), HudLayout.screenWidth() - (103.3F) - (5.0F)), mouseX - dragOffsetX));
            y = Math.max((5.0F), Math.min(Math.max((5.0F), HudLayout.screenHeight() - height - (5.0F)), mouseY - dragOffsetY));
         }

         java.util.ArrayList<com.mouseclient.util.time.Setting<?>> elementSettings = getSettings(elementId);
         float settingsHeight = 0.0F;

         for (int settingIndex = 0; settingIndex < elementSettings.size(); settingIndex++) {
            settingsHeight += SettingRendererRegistry.getSettingHeight(ctx, (Setting<?>)elementSettings.get(settingIndex), (90.8F)) + (settingIndex < elementSettings.size() - 1 ? (3.5F) : 0.0F);
         }

         float popupHeight = (5.1F) + settingsHeight + (5.1F);
         height = popupHeight;
         ctx.beginFrame(HudRenderContext.getDrawContext(), 1.0F, mouseX, mouseY);
         HudRenderContext.pushScale(x + (51.65F), y + popupHeight / 2.0F, (0.96F) + (0.04F) * visibilityProgress);
         HudPanelRenderer.drawGlassPanel(x, y, (103.3F), popupHeight, visibilityProgress, (4.5F), GuiShapeRenderer.BlurQuality.LOW);
         Render2D.pushAlpha(visibilityProgress);
         float settingY = y + (5.1F);

         for (Setting setting : elementSettings) {
            settingY += SettingRendererRegistry.renderSetting(ctx, setting, 1.0F, x + (6.3F), settingY, (90.8F), null) + (3.5F);
         }

         ctx.renderOverlays();
         Render2D.popAlpha();
         HudRenderContext.popTransform();
      } else {
         if (!open) {
            elementId = null;
            if (pendingElementId != null && chatOpen) {
               openNow(pendingElementId, pendingX, pendingY);
            }
         }
      }
   }

   public static void close() {
      open = false;
      dragging = false;
      pendingElementId = null;
      ctx.popupOwner = null;
   }

   private HudSettingsPopup() {
   }

   public static void open(String newElementId, float mouseX, float mouseY) {
      if (elementId != null && open && visibilityAnimation.value() > (0.05D)) {
         pendingElementId = newElementId;
         pendingX = mouseX;
         pendingY = mouseY;
         open = false;
      } else {
         openNow(newElementId, mouseX, mouseY);
      }
   }

   private static void openNow(String newElementId, float mouseX, float mouseY) {
      pendingElementId = null;
      elementId = newElementId;
      open = true;
      ctx.popupOwner = null;
      x = Math.max((5.0F), Math.min(Math.max((5.0F), HudLayout.screenWidth() - (103.3F) - (5.0F)), mouseX + 2.0F));
      y = Math.max((5.0F), Math.min(Math.max((5.0F), HudLayout.screenHeight() - height - (5.0F)), mouseY + 2.0F));
   }

   private static ArrayList<Setting<?>> getSettings(String elementId) {
      ArrayList elementSettings = new ArrayList();
      if (("watermark").equals(elementId)) {
         elementSettings.add(HudSettingsModule.getInstance().centered);
         elementSettings.add(HudSettingsModule.getInstance().components);
      }

      if (("potions").equals(elementId)) {
         elementSettings.add(HudSettingsModule.getInstance().showSpecialPotions);
      }

      if (("target").equals(elementId)) {
         elementSettings.add(HudSettingsModule.getInstance().headHitParticles);
      }

      return elementSettings;
   }

   public static void stopDragging() {
      dragging = false;
   }

   public static boolean isVisible() {
      return elementId != null && visibilityAnimation.value() > (0.01D);
   }
}
