package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.hud.HudIcon;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.network.HudStyle;
import com.mouseclient.render.avatar.Easings;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import com.mouseclient.internal.ProfileDisplayName;

public final class WatermarkHud {
   private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern(("HH:mm"));
   private static final List<WatermarkHud.WatermarkComponent> components = new ArrayList<>(
      List.of(
         new WatermarkHud.WatermarkComponent(("logo"), null),
         new WatermarkHud.WatermarkComponent(("avatar"), ("Аватарка")),
         new WatermarkHud.WatermarkComponent(("user"), ("Логин")),
         new WatermarkHud.WatermarkComponent(("fps"), ("ФПС")),
         new WatermarkHud.WatermarkComponent(("ping"), ("Пинг")),
         new WatermarkHud.WatermarkComponent(("coords"), ("Координаты")),
         new WatermarkHud.WatermarkComponent(("time"), ("Время"))
      )
   );
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();
   private static final TweenAnimation fpsAnimation = new TweenAnimation();
   private static final TweenAnimation bossBarOffsetAnimation = new TweenAnimation();
   private static float bottomFraction;
   private static float panelX;
   private static float panelY;
   private static float panelWidth;
   private static WatermarkHud.WatermarkComponent draggedComponent;
   private static float dragOffsetX;

   public static boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && HudRenderContext.isChatOpen()) {
         float[] hudPosition = HudLayout.toHudCoordinates(mouseX, mouseY);
         WatermarkHud.WatermarkComponent clickedComponent = componentAt(hudPosition[0], hudPosition[1]);
         if (clickedComponent == null) {
            return false;
         }

         draggedComponent = clickedComponent;
         dragOffsetX = hudPosition[0] - clickedComponent.positionAnimation.floatValue();
         if (clickedComponent.id.equals(("logo"))) {
            clickedComponent.clickedAtMillis = System.currentTimeMillis();
         }

         return true;
      } else {
         return false;
      }
   }

   public static void render(MinecraftClient client, float x, float y) {
      visibilityAnimation.update();
      visibilityAnimation.animateTo(1.0, (0.3D), Easings.QUART_OUT);
      fpsAnimation.update();
      fpsAnimation.animateTo(client.getCurrentFps(), (0.15D), Easings.QUART_OUT);
      String displayName = ProfileDisplayName.get("Player");
      boolean centered = HudSettingsModule.getInstance().isWatermarkCentered() && HudLayout.screenWidth() > 0.0F;
      if (draggedComponent == null) {
         String configuredOrder = "," + HudSettingsModule.getInstance().watermarkOrder.getValue() + ",";
         components.sort(Comparator.comparingInt(orderedComponent -> configuredOrder.contains("," + orderedComponent.id + ",") ? configuredOrder.indexOf("," + orderedComponent.id + ",") : (2147483647)));
      }

      int latency = client.player != null && client.getNetworkHandler() != null && client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()) != null
         ? client.getNetworkHandler().getPlayerListEntry(client.player.getUuid()).getLatency()
         : 0;

      for (WatermarkHud.WatermarkComponent component : components) {
         component.visible = component.settingLabel == null || HudSettingsModule.getInstance().components.matchesOption(component.settingLabel);
         switch (component.id) {
            case ("logo"):
               component.setContent(null, null, (12.75F));
               break;
            case ("avatar"):
               component.setContent(null, null, (12.0F));
               break;
            case ("user"):
               component.setContent(displayName, null, (7.0F));
               break;
            case ("fps"):
               component.setContent(String.valueOf((int)fpsAnimation.floatValue()), ("FPS"), (7.0F));
               break;
            case ("ping"):
               component.setContent(String.valueOf(latency), ("ms"), (7.0F));
               break;
            case ("coords"):
               component.setContent(
                  client.player == null ? ("0 0 0") : client.player.getBlockX() + " " + client.player.getBlockY() + " " + client.player.getBlockZ(), null, (7.0F)
               );
               break;
            case ("time"):
               component.setContent(LocalTime.now().format(TIME_FORMAT), null, (7.0F));
         }

         component.warningAnimation.update();
         component.warningAnimation
            .animateTo(
               component.id.equals(("fps"))
                  ? warningProgress(fpsAnimation.floatValue(), (60.0F), (25.0F))
                  : (component.id.equals(("ping")) ? warningProgress(latency, (120.0F), (300.0F)) : 0.0),
               (0.5D),
               Easings.QUART_OUT
            );
         component.widthAnimation.update();
         component.widthAnimation.animateTo(component.desiredWidth(), (0.05D), Easings.EXPO_OUT);
      }

      float contentWidth = 0.0F;

      for (WatermarkHud.WatermarkComponent measuredComponent : components) {
         if (!(measuredComponent.animatedWidth() < 1.0F)) {
            if (contentWidth > 0.0F) {
               contentWidth += (4.0F);
            }

            contentWidth += measuredComponent.animatedWidth();
         }
      }

      panelWidth = Math.max(contentWidth + (8.0F), 1.0F);
      float scaleRatio = GuiScale.getWindowScale() / GuiScale.getBaseScale();
      if (centered) {
         x = HudLayout.screenWidth() / 2.0F - panelWidth / 2.0F;
         y = (5.0F);
      } else {
         float[] anchor = HudLayout.resolveAnchor(("watermark"), x, y);
         x = anchor[0];
         y = anchor[1];
      }

      int bossBarCount = client.inGameHud != null ? ((BossBarHudAccessor)(Object)client.inGameHud.getBossBarHud()).getEvents().size() : 0;
      float bossBarOffset = 0.0F;
      if (bossBarCount > 0) {
         float bossBarsBottom = ((12.0F) + (bossBarCount - 1) * (19.0F) + (8.0F) + (1.5F)) * scaleRatio;
         float screenCenterX = HudLayout.screenWidth() / 2.0F;
         if (x < screenCenterX + (91.0F) * scaleRatio && x + panelWidth > screenCenterX - (91.0F) * scaleRatio && y < bossBarsBottom) {
            bossBarOffset = bossBarsBottom - y;
         }
      }

      bossBarOffsetAnimation.update();
      bossBarOffsetAnimation.animateTo(bossBarOffset, (0.2D), Easings.QUAD_OUT);
      y += bossBarOffsetAnimation.floatValue();
      panelX = x;
      panelY = y;
      HudLayout.registerBounds(("watermark"), x, y, panelWidth, (15.0F));
      bottomFraction = HudLayout.screenHeight() > 0.0F ? (y + (15.0F)) / HudLayout.screenHeight() : 0.0F;
      float visibility = visibilityAnimation.floatValue();
      if (!(visibility <= (0.001F))) {
         boolean chatOpen = HudRenderContext.isChatOpen();
         float mouseX = (float)(client.mouse.getScaledX(client.getWindow()) * scaleRatio);
         float mouseY = (float)(client.mouse.getScaledY(client.getWindow()) * scaleRatio);
         if (draggedComponent != null) {
            reorderDraggedComponent(mouseX);
         }

         float cursorX = x + (4.0F);

         for (WatermarkHud.WatermarkComponent positionedComponent : components) {
            positionedComponent.positionAnimation.update();
            float desiredX = positionedComponent == draggedComponent
               ? Math.max(x + (4.0F), Math.min(mouseX - dragOffsetX, x + panelWidth - (4.0F) - positionedComponent.animatedWidth()))
               : cursorX;
            if (!positionedComponent.positionInitialized) {
               positionedComponent.positionInitialized = true;
               positionedComponent.positionAnimation.setValue(desiredX);
            } else if (positionedComponent == draggedComponent) {
               positionedComponent.positionAnimation.setValue(desiredX);
            } else {
               positionedComponent.positionAnimation.animateTo(desiredX, (0.15D), Easings.EXPO_OUT);
            }

            if (positionedComponent.animatedWidth() >= 1.0F) {
               cursorX += positionedComponent.animatedWidth() + (4.0F);
            }
         }

         float leftmostX = (3.4028235E38F);
         float rightmostX = (-3.4028235E38F);

         for (WatermarkHud.WatermarkComponent boundedComponent : components) {
            if (!(boundedComponent.animatedWidth() < 1.0F)) {
               leftmostX = Math.min(leftmostX, boundedComponent.positionAnimation.floatValue());
               rightmostX = Math.max(rightmostX, boundedComponent.positionAnimation.floatValue() + boundedComponent.animatedWidth());
            }
         }

         float backgroundX = leftmostX == (3.4028235E38F) ? x : leftmostX - (4.0F);
         float backgroundWidth = leftmostX == (3.4028235E38F) ? panelWidth : rightmostX - leftmostX + (8.0F);
         HudPanelRenderer.drawGlassPanel(backgroundX, y, backgroundWidth, (15.0F), visibility, (6.75F));
         WatermarkHud.WatermarkComponent hoveredComponent = chatOpen ? (draggedComponent != null ? draggedComponent : componentAt(mouseX, mouseY)) : null;
         float centerY = y + (7.5F);

         for (WatermarkHud.WatermarkComponent renderedComponent : components) {
            renderedComponent.hoverAnimation.update();
            renderedComponent.hoverAnimation.animateTo(renderedComponent == hoveredComponent ? 1.0 : 0.0, (0.2D), Easings.QUART_OUT);
            if (renderedComponent != draggedComponent && renderedComponent.visible && renderedComponent.animatedWidth() >= 1.0F) {
               renderedComponent.render(y, centerY, visibility);
            }
         }

         if (draggedComponent != null && draggedComponent.visible && draggedComponent.animatedWidth() >= 1.0F) {
            draggedComponent.render(y, centerY, visibility);
         }
      }
   }

   private static int warningColor(int color, float progress) {
      int intermediateColor = GuiVisuals.lerpColor(color, (-42406), progress);
      return GuiVisuals.lerpColor(intermediateColor, (-55256), HudStyle.clampUnit((progress - (0.7F)) / (0.3F)));
   }

   public static float bottomFraction() {
      return bottomFraction;
   }

   private static WatermarkHud.WatermarkComponent componentAt(float mouseX, float mouseY) {
      if (!(mouseY < panelY + (3.5F)) && !(mouseY > panelY + (15.0F) - (3.5F))) {
         for (WatermarkHud.WatermarkComponent component : components) {
            float width = component.animatedWidth();
            float hitInset = Math.min(width * (0.25F), (3.0F));
            if (width >= 1.0F && mouseX >= component.positionAnimation.floatValue() + hitInset && mouseX <= component.positionAnimation.floatValue() + width - hitInset) {
               return component;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static void finishDrag() {
      if (draggedComponent != null) {
         draggedComponent = null;
         StringBuilder textBuilder = new StringBuilder();

         for (WatermarkHud.WatermarkComponent component : components) {
            textBuilder.append(textBuilder.isEmpty() ? "" : (",")).append(component.id);
         }

         HudSettingsModule.getInstance().watermarkOrder.setValue(textBuilder.toString());
      }
   }

   private static float warningProgress(float value, float normalThreshold, float warningThreshold) {
      return warningThreshold > normalThreshold ? HudStyle.clampUnit((value - normalThreshold) / (warningThreshold - normalThreshold)) : HudStyle.clampUnit((normalThreshold - value) / (normalThreshold - warningThreshold));
   }

   private static void reorderDraggedComponent(float mouseX) {
      float draggedCenterX = mouseX - dragOffsetX + draggedComponent.animatedWidth() / 2.0F;
      components.remove(draggedComponent);
      float cursorX = panelX + (4.0F);
      int insertionIndex = 0;

      for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
         float width = components.get(componentIndex).animatedWidth();
         if (!(width < 1.0F)) {
            if (draggedCenterX > cursorX + width / 2.0F) {
               insertionIndex = componentIndex + 1;
            }

            cursorX += width + (4.0F);
         }
      }

      components.add(insertionIndex, draggedComponent);
   }

   private static final class WatermarkComponent {
      public final String id;
      public final String settingLabel;
      public boolean visible = true;
      public final TweenAnimation widthAnimation = new TweenAnimation();
      public final TweenAnimation positionAnimation = new TweenAnimation();
      public final TweenAnimation warningAnimation = new TweenAnimation();
      public final TweenAnimation hoverAnimation = new TweenAnimation();
      public boolean positionInitialized;
      public long clickedAtMillis;
      public String text;
      public String suffix;
      public float iconSize;

      public void setContent(String text, String suffix, float iconSize) {
         this.text = text;
         this.suffix = suffix;
         this.iconSize = iconSize;
      }

      public WatermarkComponent(String id, String settingLabel) {
         this.id = id;
         this.settingLabel = settingLabel;
      }

      public float animatedWidth() {
         return Math.round(this.widthAnimation.floatValue());
      }

      public void render(float panelY, float centerY, float alpha) {
         float x = Math.round((this.positionAnimation.floatValue() + this.clickShakeOffset()) * 2.0F) / 2.0F;
         float hoverProgress = this.hoverAnimation.floatValue();
         if (hoverProgress > (0.001F)) {
            Render2D.fillRoundedRectangle(
               x - (3.0F), panelY + 1.0F, this.animatedWidth() + (6.0F), (13.0F), (5.75F), GuiVisuals.multiplyAlpha(-1, hoverProgress * (0.12F) * alpha)
            );
         }

         float warningProgress = this.warningAnimation.floatValue();
         int iconColor = GuiVisuals.multiplyAlpha(WatermarkHud.warningColor(HudStyle.accentColor, warningProgress), alpha);
         int textColor = GuiVisuals.multiplyAlpha(WatermarkHud.warningColor(HudPanelRenderer.primaryTextColor(1.0F), warningProgress), alpha);
         float iconY = centerY - this.iconSize / 2.0F;
         switch (this.id) {
            case ("logo"):
               float markHeight = 9.0F;
               float textureSize = markHeight / (768.0F / 1280.0F);
               float drawX = x - (116.0F / 1280.0F) * textureSize;
               float drawY = centerY - markHeight / 2.0F - (280.0F / 1280.0F) * textureSize;
               Render2D.drawRoundedImage(GuiVisuals.LOGO_TEXTURE, drawX, drawY, textureSize, textureSize, 0.0F, iconColor);
               break;
            case ("avatar"):
               Render2D.drawRoundedImage(("assets/mouseclient/textures/images/avatar.png"), x, iconY, this.iconSize, this.iconSize, this.iconSize / 2.0F, GuiVisuals.multiplyAlpha(-1, alpha));
               break;
            case ("user"):
               HudIcon.USER.draw(x, iconY, this.iconSize, iconColor);
               break;
            case ("fps"):
               HudIcon.FPS.draw(x, iconY, this.iconSize, iconColor);
               break;
            case ("ping"):
               HudIcon.PING.draw(x, iconY, this.iconSize, iconColor);
               break;
            case ("coords"):
               HudIcon.COORDS.draw(x, iconY, this.iconSize, iconColor);
               break;
            default:
               HudIcon.TIME.draw(x, iconY, this.iconSize, iconColor);
         }

         if (this.text != null && !this.text.isEmpty()) {
            float textX = Math.round((x + this.iconSize + (2.5F)) * 2.0F) / 2.0F;
            GuiTextRenderer.draw(GuiFont.SEMI, this.text, textX, centerY - (3.5F), (6.0F), textColor);
            if (this.suffix != null && !this.suffix.isEmpty()) {
               float suffixX = Math.round((textX + GuiTextRenderer.getWidth(GuiFont.SEMI, this.text, (6.0F)) + 2.0F) * 2.0F) / 2.0F;
               GuiTextRenderer.draw(GuiFont.SEMI, this.suffix, suffixX, centerY - (3.5F), (6.0F), textColor);
            }
         }
      }

      public float clickShakeOffset() {
         if (this.clickedAtMillis == 0L) {
            return 0.0F;
         } else {
            long elapsedMillis = System.currentTimeMillis() - this.clickedAtMillis;
            if (elapsedMillis >= (360L)) {
               this.clickedAtMillis = 0L;
               return 0.0F;
            } else {
               return (float)Math.sin((float)elapsedMillis / (1000.0F) * (70.0F)) * (2.5F) * (1.0F - (float)elapsedMillis / (360.0F));
            }
         }
      }

      public float desiredWidth() {
         if (!this.visible) {
            return 0.0F;
         }

         if (this.text != null && !this.text.isEmpty()) {
            float width = this.iconSize + (2.5F) + GuiTextRenderer.getWidth(GuiFont.SEMI, this.text, (6.0F));
            if (this.suffix != null && !this.suffix.isEmpty()) {
               width += 2.0F + GuiTextRenderer.getWidth(GuiFont.SEMI, this.suffix, (6.0F));
            }

            return width;
         } else {
            return this.iconSize;
         }
      }
   }
}
