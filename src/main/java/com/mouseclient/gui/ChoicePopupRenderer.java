package com.mouseclient.gui;

import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.ClickGuiTheme;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

public final class ChoicePopupRenderer {

   public static void enqueue(
      ClickGuiContext context, float anchorX, float anchorY, List<String> options, IntPredicate isSelected, IntConsumer onSelect, boolean closeOnSelect, float progress, boolean interactive
   ) {
      context.overlayRenderers
         .add(
            () -> {
               float scale = context.scale;
               float maximumLabelWidth = 0.0F;

               for (String optionLabel : options) {
                  maximumLabelWidth = Math.max(maximumLabelWidth, GuiTextRenderer.getWidth(GuiFont.INSTANCE, optionLabel, (6.0F) * scale));
               }

               float rowHeight = (8.93F) * scale;
               float toggleWidth = (10.74F) * scale;
               float popupWidth = ClickGuiContext.clamp(
                  maximumLabelWidth + (5.3F) * scale + (4.0F) * scale + toggleWidth + (5.3F) * scale, (52.4F) * scale, (110.0F) * scale
               );
               float viewportHeight = Math.min(options.size(), 10) * rowHeight;
               float popupHeight = (6.6F) * scale + viewportHeight;
               float popupX = anchorX - popupWidth - (2.2F) * scale;
               float popupY = Math.max((4.0F) * scale, anchorY - popupHeight - 1.0F * scale) + (1.0F - progress) * (5.0F) * scale;
               float viewportY = popupY + (3.3F) * scale;
               float maxPopupScroll = Math.max(0.0F, options.size() * rowHeight - viewportHeight);
               float scrollOffset = ClickGuiContext.clamp(context.popupScrollOffsets.getOrDefault(options, 0.0F), 0.0F, maxPopupScroll);
               context.popupScrollOffsets.put(options, scrollOffset);
               float animatedScroll = context.animateValue("popupScroll:" + System.identityHashCode(options), scrollOffset);
               if (interactive) {
                  context.registerClick((-100000.0F), (-100000.0F), (200000.0F), (200000.0F), null, () -> context.popupOwner = null);
                  context.scrollOwner = options;
                  context.scrollBounds = new float[]{popupX, popupY, popupWidth, popupHeight};
                  context.maxPopupScroll = maxPopupScroll;
               }

               Render2D.pushAlpha(progress);
               Render2D.drawSharedBlur(
                  popupX + (0.4F) * scale,
                  popupY + (0.4F) * scale,
                  popupWidth - (0.8F) * scale,
                  popupHeight - (0.8F) * scale,
                  (4.1F) * scale,
                  (15.0F),
                  1.0F,
                  -1
               );
               Render2D.fillRoundedRectangle(popupX, popupY, popupWidth, popupHeight, (4.5F) * scale, ClickGuiTheme.popupBackground);
               Render2D.drawOutline(popupX, popupY, popupWidth, popupHeight, (4.5F) * scale, (0.5F), ClickGuiTheme.popupOutline);
               float[] viewportClip = new float[]{popupX, viewportY, popupWidth, viewportHeight};
               Render2D.pushScissor(context.drawContext, viewportClip[0], viewportClip[1], viewportClip[2], viewportClip[3]);

               for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
                  float rowY = popupY + (4.3F) * scale + optionIndex * rowHeight - animatedScroll;
                  if (!(rowY + rowHeight < viewportY) && !(rowY - rowHeight > viewportY + viewportHeight)) {
                     float selectedProgress = context.animateBoolean("popupOpt:" + System.identityHashCode(options) + ":" + optionIndex, isSelected.test(optionIndex));
                     context.drawMarqueeText(
                        (String)options.get(optionIndex),
                        popupX + (5.3F) * scale,
                        rowY,
                        (6.0F) * scale,
                        GuiVisuals.lerpColor(ClickGuiTheme.choiceText, ClickGuiTheme.activeChoiceText, selectedProgress),
                        popupWidth - (5.3F) * scale - (4.0F) * scale - toggleWidth - (5.3F) * scale
                     );
                     context.drawToggle(popupX + popupWidth - (5.3F) * scale - toggleWidth, rowY - (0.2F) * scale, selectedProgress);
                     int clickedOptionIndex = optionIndex;
                     if (interactive) {
                        context.registerClick(popupX, rowY - (1.2F) * scale, popupWidth, rowHeight, viewportClip, () -> {
                           onSelect.accept(clickedOptionIndex);
                           if (closeOnSelect) {
                              context.popupOwner = null;
                           }
                        });
                     }
                  }
               }

               Render2D.popScissor(context.drawContext);
               if (maxPopupScroll > 0.0F) {
                  float scrollbarTrackHeight = viewportHeight - 2.0F * scale;
                  float scrollbarThumbHeight = Math.max((6.0F) * scale, scrollbarTrackHeight * viewportHeight / (options.size() * rowHeight));
                  Render2D.fillRoundedRectangle(
                     popupX + popupWidth - (2.4F) * scale,
                     viewportY + 1.0F * scale + (scrollbarTrackHeight - scrollbarThumbHeight) * (animatedScroll / maxPopupScroll),
                     (1.1F) * scale,
                     scrollbarThumbHeight,
                     (0.55F) * scale,
                     GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.32F))
                  );
               }

               Render2D.popAlpha();
            }
         );
   }

   private ChoicePopupRenderer() {
   }
}
