package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.network.HudStyle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.text.Text;

public final class ToastNotifications {
   private static final List<ToastNotifications.ToastEntry> toasts = new ArrayList<>();
   private static ToastNotifications.ToastEntry previewToast;

   public static void updatePreview(boolean previewEnabled) {
      long nowMillis = System.currentTimeMillis();
      boolean hasRealToast = false;

      for (ToastNotifications.ToastEntry toast : toasts) {
         if (toast != previewToast && nowMillis - toast.createdAtMillis <= (3220L)) {
            hasRealToast = true;
            break;
         }
      }

      if (previewEnabled && !hasRealToast) {
         if (previewToast == null || !toasts.contains(previewToast)) {
            ToastNotifications.ToastEntry newPreview = new ToastNotifications.ToastEntry();
            newPreview.iconGlyph = ("F");
            newPreview.title = ("Example");
            newPreview.message = ("Notification!");
            newPreview.emphasized = true;
            previewToast = newPreview;
            toasts.add(newPreview);
            pruneAndLimit();
         }
      } else if (previewToast != null) {
         if (nowMillis - previewToast.createdAtMillis < (3000L)) {
            previewToast.createdAtMillis = nowMillis - (3000L);
         }

         previewToast = null;
      }
   }

   private static void pruneAndLimit() {
      long nowMillis = System.currentTimeMillis();
      toasts.removeIf(toastEntry -> nowMillis - toastEntry.createdAtMillis > (3220L));
      int activeCount = 0;

      for (ToastNotifications.ToastEntry countedToast : toasts) {
         if (nowMillis - countedToast.createdAtMillis < (3000L)) {
            activeCount++;
         }
      }

      for (ToastNotifications.ToastEntry limitedToast : toasts) {
         if (activeCount <= 6) {
            return;
         }

         if (nowMillis - limitedToast.createdAtMillis < (3000L)) {
            limitedToast.createdAtMillis = nowMillis - (3000L);
            activeCount--;
         }
      }
   }

   public static void render(float screenWidth, float screenHeight) {
      long nowMillis = System.currentTimeMillis();
      Iterator iterator = toasts.iterator();

      while (iterator.hasNext()) {
         if (nowMillis - ((ToastNotifications.ToastEntry)iterator.next()).createdAtMillis > (3220L)) {
            iterator.remove();
         }
      }

      if (!toasts.isEmpty()) {
         float[] anchor = HudLayout.resolveAnchor(("toasts"), screenWidth / 2.0F, screenHeight * (0.62F));
         float top = anchor[1];
         float toastY = top;
         float maxWidth = 0.0F;

         for (int var9 = toasts.size() - 1; var9 >= 0; var9--) {
            ToastNotifications.ToastEntry toast = toasts.get(var9);
            long ageMillis = nowMillis - toast.createdAtMillis;
            float openingProgress = HudStyle.clampUnit((float)ageMillis / (220.0F));
            float alpha = Math.min(openingProgress, HudStyle.clampUnit((float)((3220L) - ageMillis) / (220.0F)));
            if (!(alpha < (0.01F))) {
               maxWidth = Math.max(maxWidth, toastWidth(toast));
               toastY = renderToast(toast, anchor[0], toastY, openingProgress, ageMillis, alpha);
            }
         }

         if (toastY - top > (0.5F)) {
            HudLayout.registerBounds(("toasts"), anchor[0] - maxWidth / 2.0F, top, maxWidth, toastY - top, anchor[0], top);
         }
      }
   }

   public static void showIconToast(String iconGlyph, String title, String message, boolean emphasized) {
      ToastNotifications.ToastEntry toast = new ToastNotifications.ToastEntry();
      toast.iconGlyph = iconGlyph;
      toast.title = title;
      toast.message = message;
      toast.emphasized = emphasized;
      toasts.add(toast);
      pruneAndLimit();
   }

   public static void showEffectToast(StatusEffectInstance effect, String title, String message, boolean emphasized) {
      ToastNotifications.ToastEntry toast = new ToastNotifications.ToastEntry();
      toast.effect = effect;
      toast.title = title;
      toast.message = message;
      toast.emphasized = emphasized;
      toasts.add(toast);
      pruneAndLimit();
   }

   private static float toastWidth(ToastNotifications.ToastEntry toast) {
      return (16.0F)
         + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, toast.title, (5.5F))
         + 1.0F
         + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, toast.message, (5.5F))
         + (4.5F)
         + (5.25F)
         + (4.5F);
   }

   public static void showStyledToast(String iconGlyph, String title, Text message, boolean emphasized) {
      ToastNotifications.ToastEntry toast = new ToastNotifications.ToastEntry();
      toast.iconGlyph = iconGlyph;
      toast.title = title;
      toast.message = message.getString();
      toast.styledMessage = message;
      toast.emphasized = emphasized;
      toasts.add(toast);
      pruneAndLimit();
   }

   private static float renderToast(ToastNotifications.ToastEntry toast, float centerX, float y, float openingProgress, long ageMillis, float alpha) {
      float width = toastWidth(toast);
      float left = centerX - width / 2.0F;
      float top = y - (4.0F) * (1.0F - openingProgress);
      float centerY = top + (7.5F);
      float remainingLife = HudStyle.clampUnit(1.0F - (float)ageMillis / (3000.0F));
      Render2D.pushAlpha(alpha);
      HudPanelRenderer.drawGlassPanel(left, top, width, (15.0F), alpha, (5.5F));
      if (toast.effect != null) {
         Render2D.drawEffectIcon(toast.effect.getEffectType(), left + (4.5F), centerY - (3.5F), (7.0F));
      } else {
         HudStyle.drawIcon(
            toast.iconGlyph,
            left + (4.5F) + (3.5F),
            centerY,
            (7.0F),
            toast.emphasized ? HudStyle.accentColor : HudStyle.accentColor & (16777215) | (2046820352)
         );
      }

      float titleX = left + (4.5F) + (7.0F) + 2.0F + (0.5F) + 2.0F;
      float textY = centerY - (2.75F);
      GuiTextRenderer.draw(
         GuiFont.SEMI_BOLD,
         toast.title,
         titleX,
         textY,
         (5.5F),
         toast.emphasized ? HudPanelRenderer.secondaryTextColor(1.0F) : HudPanelRenderer.secondaryTextColor((0.6F))
      );
      float messageX = titleX + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, toast.title, (5.5F)) + 1.0F;
      int messageColor = toast.emphasized ? HudPanelRenderer.primaryTextColor(1.0F) : HudPanelRenderer.secondaryTextColor(1.0F);
      if (toast.styledMessage != null) {
         GuiTextRenderer.drawStyledText(GuiFont.SEMI_BOLD, toast.styledMessage, messageX, textY, (5.5F), messageColor);
      } else {
         GuiTextRenderer.draw(GuiFont.SEMI_BOLD, toast.message, messageX, textY, (5.5F), messageColor);
      }

      HudStyle.drawProgressRing(
         left + width - (4.5F) - (2.625F),
         centerY,
         (5.25F),
         remainingLife,
         GuiVisuals.lerpColor(HudStyle.whiteAlpha20, toast.emphasized ? HudStyle.accentColor : HudStyle.mutedTextColor, remainingLife)
      );
      Render2D.popAlpha();
      return y + (18.5F) * alpha;
   }

   private static final class ToastEntry {
      public String iconGlyph;
      public StatusEffectInstance effect;
      public String title;
      public String message;
      public Text styledMessage;
      public boolean emphasized;
      public long createdAtMillis = System.currentTimeMillis();
   }
}
