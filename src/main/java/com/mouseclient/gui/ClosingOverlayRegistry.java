package com.mouseclient.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public final class ClosingOverlayRegistry {
   private static final List<ClosingOverlayRegistry.ClosingOverlay> overlays = new ArrayList<>();

   private ClosingOverlayRegistry() {
   }

   public static boolean isEmpty() {
      return overlays.isEmpty();
   }

   public static void add(ClosingOverlayRegistry.ClosingOverlay overlay) {
      if (overlay != null && !overlays.contains(overlay)) {
         overlays.add(overlay);
      }
   }

   public static void render(DrawContext context) {
      if (!overlays.isEmpty()) {
         for (ClosingOverlayRegistry.ClosingOverlay closingOverlay : new ArrayList<>(overlays)) {
            if (!closingOverlay.renderClosing(context)) {
               overlays.remove(closingOverlay);
            }
         }
      }
   }

   public static void removeType(Class<?> type) {
      overlays.removeIf(type::isInstance);
   }

   public static void remove(ClosingOverlayRegistry.ClosingOverlay overlay) {
      overlays.remove(overlay);
   }

   public interface ClosingOverlay {
      public boolean renderClosing(DrawContext context);
   }
}
