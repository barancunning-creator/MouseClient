package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.GuiShapeRenderer;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.util.entity.WorldProjection;
import com.mouseclient.util.entity.MinecraftAccess;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class WaypointManager implements MinecraftAccess {
   private static WaypointManager INSTANCE;
   private static final int UNUSED_BACKGROUND_COLOR = new Color(0, 0, 0, 120).getRGB();
   private static final int UNUSED_TEXT_COLOR = new Color(200, 200, 200, 220).getRGB();
   private final LinkedHashMap<String, WaypointManager.Waypoint> waypoints = new LinkedHashMap<>();
   private final HashMap<String, Float> revealProgress = new HashMap<>();
   private long lastRenderAt = System.currentTimeMillis();
   private final EventListener<HudRenderEvent> hudRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && !this.waypoints.isEmpty()) {
         long now = System.currentTimeMillis();
         this.waypoints.values().removeIf(candidate -> candidate.endAtMs() > 0L && now - candidate.endAtMs() > (90000L));
         this.revealProgress.keySet().retainAll(this.waypoints.keySet());
         float frameProgress = Math.min(1.0F, (float)(now - this.lastRenderAt) / (300.0F));
         this.lastRenderAt = now;
         HudRenderContext.setDrawContext(event.getDrawContext());

         for (WaypointManager.Waypoint waypoint : this.waypoints.values()) {
            if (waypoint.dimensionPath().isEmpty() || waypoint.dimensionPath().equals(minecraftClient.world.getRegistryKey().getValue().getPath())) {
               float revealAmount = this.revealProgress.merge(waypoint.name(), frameProgress, (previousProgress, frameIncrement) -> Math.min(1.0F, previousProgress + frameIncrement));
               Vec3d position = WorldProjection.projectNearViewport(waypoint.pos(), event.getWidth(), event.getHeight());
               if (position != null) {
                  float easedReveal = revealAmount < (0.5F) ? 2.0F * revealAmount * revealAmount : 1.0F - (2.0F - 2.0F * revealAmount) * (2.0F - 2.0F * revealAmount) / 2.0F;
                  if (!(easedReveal < (0.02F))) {
                     float distance = (float)minecraftClient.player.getEntityPos().distanceTo(waypoint.pos());
                     float screenX = (float)position.x * (GuiScale.getWindowScale() / GuiScale.getBaseScale());
                     float screenY = (float)position.y * (GuiScale.getWindowScale() / GuiScale.getBaseScale());
                     HudRenderContext.pushScale(screenX, screenY, (float)Math.max((0.6D), (1.2D) - distance / (150.0D)) * easedReveal);
                     String distanceText = String.format(Locale.US, ("%.1fм"), distance);
                     float nameWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, waypoint.name(), (6.0F));
                     float distanceWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, distanceText, (6.0F));
                     float headerWidth = (4.0F) + nameWidth + (3.0F) + distanceWidth + (4.0F);
                     float headerHeight = (13.0F);
                     float headerX = Math.round((screenX - headerWidth / 2.0F) * 2.0F) / 2.0F;
                     float headerY = Math.round((screenY - headerHeight / 2.0F) * 2.0F) / 2.0F;
                     HudPanelRenderer.drawGlassPanel(headerX, headerY, headerWidth, headerHeight, easedReveal, (4.5F), GuiShapeRenderer.BlurQuality.LOW);
                     GuiTextRenderer.draw(
                        GuiFont.SEMI_BOLD,
                        waypoint.name(),
                        headerX + (4.0F),
                        headerY + headerHeight / 2.0F - (3.5F),
                        (6.0F),
                        HudPanelRenderer.primaryTextColor(easedReveal)
                     );
                     GuiTextRenderer.draw(
                        GuiFont.SEMI_BOLD,
                        distanceText,
                        headerX + (4.0F) + nameWidth + (3.0F),
                        headerY + headerHeight / 2.0F - (3.5F),
                        (6.0F),
                        HudPanelRenderer.secondaryTextColor(easedReveal)
                     );
                     java.util.ArrayList<java.lang.String> descriptionLines = new ArrayList<>(waypoint.lines());
                     if (waypoint.openAtMs() != 0L) {
                        float secondsUntilOpen = (float)(waypoint.openAtMs() - now) / (1000.0F);
                        float secondsUntilEnd = (float)(waypoint.endAtMs() - now) / (1000.0F);
                        descriptionLines.add(
                           secondsUntilOpen > 0.0F
                              ? "До начала: " + String.format(Locale.US, secondsUntilOpen < (30.0F) ? ("%.1f") : ("%.0f"), secondsUntilOpen) + "с"
                              : (secondsUntilEnd > 0.0F ? "До конца: " + String.format(Locale.US, secondsUntilEnd < (30.0F) ? ("%.1f") : ("%.0f"), secondsUntilEnd) + "с" : ("Конец ивента!"))
                        );
                     }

                     if (!descriptionLines.isEmpty()) {
                        float descriptionWidth = 0.0F;

                        for (String descriptionLine : descriptionLines) {
                           descriptionWidth = Math.max(descriptionWidth, GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, descriptionLine, (5.0F)));
                        }

                        descriptionWidth += (8.0F);
                        float descriptionHeight = (3.5F) + descriptionLines.size() * (6.5F) + (1.5F);
                        float descriptionX = Math.round((screenX - descriptionWidth / 2.0F) * 2.0F) / 2.0F;
                        float descriptionTop = headerY + headerHeight + 2.0F;
                        HudPanelRenderer.drawGlassPanel(descriptionX, descriptionTop, descriptionWidth, descriptionHeight, easedReveal, (4.5F), GuiShapeRenderer.BlurQuality.LOW);
                        float lineY = descriptionTop + (3.0F);

                        for (int lineIndex = 0; lineIndex < descriptionLines.size(); lineIndex++) {
                           GuiTextRenderer.draw(
                              GuiFont.SEMI_BOLD,
                              (String)descriptionLines.get(lineIndex),
                              screenX - GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, (String)descriptionLines.get(lineIndex), (5.0F)) / 2.0F,
                              lineY,
                              (5.0F),
                              waypoint.openAtMs() != 0L && lineIndex == descriptionLines.size() - 1 ? HudPanelRenderer.primaryTextColor(easedReveal) : HudPanelRenderer.secondaryTextColor(easedReveal)
                           );
                           lineY += (6.5F);
                        }
                     }

                     HudRenderContext.popTransform();
                  }
               }
            }
         }
      }
   };

   public void clear() {
      this.waypoints.clear();
   }

   public ArrayList<String> getNames() {
      return new ArrayList<>(this.waypoints.keySet());
   }

   public static WaypointManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new WaypointManager();
      }

      return INSTANCE;
   }

   public boolean remove(String name) {
      return this.waypoints.remove(name) != null;
   }

   public void add(String name, int blockX, int blockY, int blockZ) {
      this.waypoints
         .put(name, new WaypointManager.Waypoint(name, new Vec3d(blockX + (0.5D), blockY + (0.5D), blockZ + (0.5D)), new ArrayList<>(), 0L, 0L, ""));
   }

   public void addEventWaypoint(String name, BlockPos position, int opensInSeconds, int durationSeconds, String... lines) {
      ArrayList descriptionLines = new ArrayList();

      for (String line : lines) {
         if (line != null && !line.isBlank()) {
            descriptionLines.add(line.trim());
         }
      }

      this.waypoints
         .put(
            name,
            new WaypointManager.Waypoint(
               name,
               position.toCenterPos(),
               descriptionLines,
               System.currentTimeMillis() + opensInSeconds * (1000L),
               System.currentTimeMillis() + (opensInSeconds + durationSeconds) * (1000L),
               minecraftClient.world == null ? "" : minecraftClient.world.getRegistryKey().getValue().getPath()
            )
         );
   }

   public int size() {
      return this.waypoints.size();
   }

   public record Waypoint(String name, Vec3d pos, ArrayList<String> lines, long openAtMs, long endAtMs, String dimensionPath) {
   }
}
