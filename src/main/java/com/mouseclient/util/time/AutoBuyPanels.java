package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import com.mouseclient.internal.RoundedClipStack;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.player.ServerItemDefinition;
import com.mouseclient.modules.impl.player.ServerItemCatalog;
import com.mouseclient.render.GuiItemRenderer;
import java.util.ArrayList;

public final class AutoBuyPanels {
   private static final String[] CHART_LABELS = new String[]{("500K"), ("250K"), ("100K"), ("50K"), ("0")};
   private static String lastEditedItem;

   public static void renderPricesPanel(ClickGuiContext context, AutoBuyState state, AutoBuyGuiController controller, float x, float y, float width, float height, boolean interactive, long nowMillis) {
      context.drawFlatTintPanel(x, y, width, height, (4.0F) * context.scale, ClickGuiTheme.itemPanelFill, GuiVisuals.panelOutlineColor);
      boolean editing = state.editingItem != null && state.selectedItems.get(state.editingItem) != null;
      if (editing) {
         lastEditedItem = state.editingItem;
      }

      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Цены"),
         x + (7.0F) * context.scale,
         y + (7.0F) * context.scale,
         (5.5F) * context.scale,
         GuiVisuals.white
      );
      Render2D.pushAlpha(context.animateBoolean(("ab:pricehint"), !editing));
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("для всех предметов"),
         x
            + (7.0F) * context.scale
            + GuiTextRenderer.getWidth(GuiFont.SEMI, ("Цены"), (5.5F) * context.scale)
            + (4.0F) * context.scale,
         y + (7.5F) * context.scale,
         (5.0F) * context.scale,
         GuiVisuals.mutedTextColor
      );
      Render2D.popAlpha();
      renderEditingBadge(context, state, controller, x, y - (1.5F) * context.scale, width + 1.0F * context.scale, interactive, editing);
      float modeProgress = AutoBuyGuiController.staggeredProgress(nowMillis - state.priceModeChangedAtMillis, 0.0F, (0.16F));
      float inputWidth = (92.0F) * context.scale;
      float inputHeight = (13.0F) * context.scale;
      float inputX = x + width - (7.0F) * context.scale - inputWidth;
      Render2D.pushAlpha(modeProgress);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Покупка до"),
         x + (7.0F) * context.scale,
         y + (24.0F) * context.scale + (3.75F) * context.scale,
         (5.5F) * context.scale,
         GuiVisuals.brightTextColor
      );
      renderInput(
         context,
         state,
         editing ? state.selectedItems.get(state.editingItem).maxBuyPrice : state.maxBuyPrice,
         editing ? "item:buy:" + state.editingItem : ("buyMax"),
         ("$"),
         null,
         inputX,
         y + (24.0F) * context.scale + (1.0F - modeProgress) * 2.0F * context.scale,
         inputWidth,
         inputHeight,
         interactive,
         nowMillis
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Продажа от"),
         x + (7.0F) * context.scale,
         y + (42.0F) * context.scale + (3.75F) * context.scale,
         (5.5F) * context.scale,
         GuiVisuals.brightTextColor
      );
      renderInput(
         context,
         state,
         editing ? state.selectedItems.get(state.editingItem).minSellPrice : state.minSellPrice,
         editing ? "item:sell:" + state.editingItem : ("sellMin"),
         ("$"),
         null,
         inputX,
         y + (42.0F) * context.scale + (1.0F - modeProgress) * 2.0F * context.scale,
         inputWidth,
         inputHeight,
         interactive,
         nowMillis
      );
      Render2D.popAlpha();
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Прочность от"),
         x + (7.0F) * context.scale,
         y + (60.0F) * context.scale + (3.75F) * context.scale,
         (5.5F) * context.scale,
         GuiVisuals.brightTextColor
      );
      renderInput(context, state, state.minimumDurability, ("durability"), null, ("%"), inputX, y + (60.0F) * context.scale, inputWidth, inputHeight, interactive, nowMillis);
   }

   public static void renderPricingConfiguration(ClickGuiContext context, AutoBuyState state, AutoBuyGuiController controller, float x, float y, float width, float height, boolean interactive, long nowMillis) {
      context.drawStrongTintPanel(x, y, width, height, (4.0F) * context.scale, ClickGuiTheme.itemPanelFill, GuiVisuals.panelOutlineColor);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Pricing Configuration"),
         x + (8.0F) * context.scale,
         y + (8.0F) * context.scale,
         (5.0F) * context.scale,
         GuiVisuals.mutedTextColor
      );
      if (state.editingItem != null && state.selectedItems.get(state.editingItem) != null) {
         lastEditedItem = state.editingItem;
      }

      renderEditingBadge(context, state, controller, x, y, width, interactive, state.editingItem != null && state.selectedItems.get(state.editingItem) != null);
      renderSettingDescription(context, ("Auto Buy Speed"), ("Select scan frequency and execution speed"), x + (8.0F) * context.scale, y + (22.0F) * context.scale, (86.0F) * context.scale);
      renderSpeedSlider(context, state, x + (98.0F) * context.scale, y + (25.0F) * context.scale, width - (106.0F) * context.scale, interactive);
      Render2D.pushAlpha(AutoBuyGuiController.staggeredProgress(nowMillis - state.priceModeChangedAtMillis, 0.0F, (0.16F)));
      renderSettingDescription(
         context,
         ("Buy Price (Max)"),
         ("Maximum price limit for automatic purchases"),
         x + (8.0F) * context.scale,
         y + (48.0F) * context.scale + (1.0F - AutoBuyGuiController.staggeredProgress(nowMillis - state.priceModeChangedAtMillis, 0.0F, (0.16F))) * 2.0F * context.scale,
         (86.0F) * context.scale
      );
      renderInput(
         context,
         state,
         state.editingItem != null && state.selectedItems.get(state.editingItem) != null
            ? state.selectedItems.get(state.editingItem).maxBuyPrice
            : state.maxBuyPrice,
         state.editingItem != null && state.selectedItems.get(state.editingItem) != null ? "item:buy:" + state.editingItem : ("buyMax"),
         ("$"),
         null,
         x + (98.0F) * context.scale,
         y + (51.0F) * context.scale + (1.0F - AutoBuyGuiController.staggeredProgress(nowMillis - state.priceModeChangedAtMillis, 0.0F, (0.16F))) * 2.0F * context.scale,
         width - (106.0F) * context.scale,
         (14.0F) * context.scale,
         interactive,
         nowMillis
      );
      Render2D.popAlpha();
      Render2D.pushAlpha(AutoBuyGuiController.staggeredProgress(nowMillis - state.priceModeChangedAtMillis, (0.04F), (0.16F)));
      renderSettingDescription(
         context,
         ("Sell Price (Min)"),
         ("Minimum listing price for automatic reselling"),
         x + (8.0F) * context.scale,
         y + (74.0F) * context.scale + (1.0F - AutoBuyGuiController.staggeredProgress(nowMillis - state.priceModeChangedAtMillis, (0.04F), (0.16F))) * 2.0F * context.scale,
         (86.0F) * context.scale
      );
      renderInput(
         context,
         state,
         state.editingItem != null && state.selectedItems.get(state.editingItem) != null
            ? state.selectedItems.get(state.editingItem).minSellPrice
            : state.minSellPrice,
         state.editingItem != null && state.selectedItems.get(state.editingItem) != null ? "item:sell:" + state.editingItem : ("sellMin"),
         ("$"),
         null,
         x + (98.0F) * context.scale,
         y + (77.0F) * context.scale + (1.0F - AutoBuyGuiController.staggeredProgress(nowMillis - state.priceModeChangedAtMillis, (0.04F), (0.16F))) * 2.0F * context.scale,
         width - (106.0F) * context.scale,
         (14.0F) * context.scale,
         interactive,
         nowMillis
      );
      Render2D.popAlpha();
      renderSettingDescription(context, ("Minimum Durability"), ("Items below this durability will be ignored"), x + (8.0F) * context.scale, y + (100.0F) * context.scale, (86.0F) * context.scale);
      renderInput(
         context,
         state,
         state.minimumDurability,
         ("durability"),
         null,
         ("%"),
         x + (98.0F) * context.scale,
         y + (103.0F) * context.scale,
         width - (106.0F) * context.scale,
         (14.0F) * context.scale,
         interactive,
         nowMillis
      );
   }

   public static void renderSearch(ClickGuiContext context, AutoBuyState state, float x, float y, float width, float height, boolean interactive) {
      context.drawFlatTintPanel(
         x,
         y,
         width,
         height,
         (4.0F) * context.scale,
         ClickGuiTheme.itemPanelFill,
         GuiVisuals.lerpColor(GuiVisuals.panelOutlineColor, GuiVisuals.focusedFieldOutlineColor, context.animateBoolean(("ab:searchf"), context.focusedText == state.search))
      );
      context.drawIcon(
         ("R"),
         x + (8.0F) * context.scale + (2.75F) * context.scale,
         y + height / 2.0F,
         (5.5F) * context.scale,
         GuiVisuals.lerpColor(GuiVisuals.mutedTextColor, GuiVisuals.brightTextColor, context.animateBoolean(("ab:searchf"), context.focusedText == state.search))
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         (state.search.getValue().isEmpty() && context.focusedText != state.search ? state.search.getPlaceholder() : state.search.getValue())
            + (context.focusedText == state.search ? ClickGuiContext.blinkingCursor() : ""),
         x + (8.0F) * context.scale + (5.5F) * context.scale + (4.0F) * context.scale,
         y + height / 2.0F - (2.75F) * context.scale,
         (5.5F) * context.scale,
         state.search.getValue().isEmpty() ? GuiVisuals.brightTextColor : GuiVisuals.white
      );
      if (interactive) {
         context.registerClick(x, y, width, height, null, () -> {
            context.focusedText = state.search;
            state.search.moveToEnd(false);
         });
      }
   }

   public static void renderTelegramPanel(ClickGuiContext context, AutoBuyState state, float x, float y, float width, float height, boolean interactive, long nowMillis) {
      context.drawFlatTintPanel(x, y, width, height, (4.0F) * context.scale, ClickGuiTheme.itemPanelFill, GuiVisuals.panelOutlineColor);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Telegram"),
         x + (7.0F) * context.scale,
         y + (7.0F) * context.scale,
         (5.5F) * context.scale,
         GuiVisuals.white
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("уведомления о покупках"),
         x
            + (7.0F) * context.scale
            + GuiTextRenderer.getWidth(GuiFont.SEMI, ("Telegram"), (5.5F) * context.scale)
            + (4.0F) * context.scale,
         y + (7.5F) * context.scale,
         (5.0F) * context.scale,
         GuiVisuals.mutedTextColor
      );
      float inputWidth = (92.0F) * context.scale;
      float inputHeight = (13.0F) * context.scale;
      float inputX = x + width - (7.0F) * context.scale - inputWidth;
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Токен бота"),
         x + (7.0F) * context.scale,
         y + (20.0F) * context.scale + (3.75F) * context.scale,
         (5.5F) * context.scale,
         GuiVisuals.brightTextColor
      );
      renderInput(context, state, state.telegramBotToken, ("tgToken"), null, null, inputX, y + (20.0F) * context.scale, inputWidth, inputHeight, interactive, nowMillis);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("ID чата"),
         x + (7.0F) * context.scale,
         y + (36.0F) * context.scale + (3.75F) * context.scale,
         (5.5F) * context.scale,
         GuiVisuals.brightTextColor
      );
      renderInput(context, state, state.telegramChatId, ("tgChat"), null, null, inputX, y + (36.0F) * context.scale, inputWidth, inputHeight, interactive, nowMillis);
   }

   public static void renderTelegramConfiguration(ClickGuiContext context, AutoBuyState state, float x, float y, float width, float height, boolean interactive, long nowMillis) {
      context.drawStrongTintPanel(x, y, width, height, (4.0F) * context.scale, ClickGuiTheme.itemPanelFill, GuiVisuals.panelOutlineColor);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Bot API Configuration"),
         x + (8.0F) * context.scale,
         y + (8.0F) * context.scale,
         (5.0F) * context.scale,
         GuiVisuals.mutedTextColor
      );
      renderSettingDescription(context, ("Telegram Bot Token"), ("API token obtained from @BotFather"), x + (8.0F) * context.scale, y + (22.0F) * context.scale, (86.0F) * context.scale);
      renderInput(
         context,
         state,
         state.telegramBotToken,
         ("tgToken"),
         null,
         null,
         x + (98.0F) * context.scale,
         y + (22.0F) * context.scale,
         width - (106.0F) * context.scale,
         (14.0F) * context.scale,
         interactive,
         nowMillis
      );
      renderSettingDescription(context, ("Telegram Chat ID"), ("Target chat or channel ID for alerts"), x + (8.0F) * context.scale, y + (42.0F) * context.scale, (86.0F) * context.scale);
      renderInput(
         context,
         state,
         state.telegramChatId,
         ("tgChat"),
         null,
         null,
         x + (98.0F) * context.scale,
         y + (42.0F) * context.scale,
         width - (106.0F) * context.scale,
         (14.0F) * context.scale,
         interactive,
         nowMillis
      );
   }

   public static void renderPurchaseHistory(ClickGuiContext context, AutoBuyState state, float x, float y, float width, float height, float deltaSeconds, long nowMillis, long openedAtMillis) {
      context.drawFlatTintPanel(x, y, width, height, (4.0F) * context.scale, ClickGuiTheme.itemPanelFill, GuiVisuals.panelOutlineColor);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("История покупок"),
         x + (7.0F) * context.scale,
         y + (7.0F) * context.scale,
         (5.5F) * context.scale,
         GuiVisuals.white
      );
      float listTop = y + (22.0F) * context.scale;
      float listHeight = y + height - (8.0F) * context.scale - listTop;
      float priceColumnWidth = width - (20.0F) * context.scale;
      state.purchaseHistoryBounds = new float[]{x, listTop, width, listHeight};
      state.purchaseHistoryScroll.maxScroll = Math.max(0.0F, state.purchaseHistory.size() * (13.0F) * context.scale - (6.0F) * context.scale - listHeight);
      state.purchaseHistoryScroll.update(deltaSeconds);
      if (state.purchaseHistory.isEmpty()) {
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            ("Покупок пока нет"),
            x + (width - GuiTextRenderer.getWidth(GuiFont.SEMI, ("Покупок пока нет"), (5.0F) * context.scale)) / 2.0F,
            listTop + listHeight / 2.0F - (2.5F) * context.scale,
            (5.0F) * context.scale,
            GuiVisuals.dimTextColor
         );
      }

      Render2D.pushScissor(
         context.drawContext,
         x + (6.0F) * context.scale,
         listTop - 1.0F * context.scale,
         width - (12.0F) * context.scale,
         listHeight + 2.0F * context.scale
      );

      for (int entryIndex = 0; entryIndex < state.purchaseHistory.size(); entryIndex++) {
         float rowY = listTop + entryIndex * (13.0F) * context.scale - state.purchaseHistoryScroll.getScroll();
         if (!(rowY + (7.0F) * context.scale < listTop - 2.0F) && !(rowY > listTop + listHeight + 2.0F)) {
            float rowProgress = AutoBuyGuiController.staggeredProgress(nowMillis - openedAtMillis, (0.22F) + Math.min(4, entryIndex) * (0.06F), (0.25F));
            if (!(rowProgress < (0.005F))) {
               Render2D.pushAlpha(rowProgress);
               GuiItemRenderer.render(
                  state.purchaseHistory.get(entryIndex).item().displayStack(),
                  x + (8.0F) * context.scale + (1.0F - rowProgress) * (4.0F) * context.scale,
                  rowY + (0.75F) * context.scale,
                  (5.5F) * context.scale,
                  ItemRenderOptions.noDecorations(AutoBuyGuiController.currentRenderAlpha())
               );
               Render2D.pushScissor(
                  context.drawContext,
                  x + (17.5F) * context.scale,
                  rowY - 1.0F * context.scale,
                  (75.0F) * context.scale,
                  (9.0F) * context.scale
               );
               GuiTextRenderer.draw(
                  GuiFont.SEMI,
                  state.purchaseHistory.get(entryIndex).item().name(),
                  x + (17.5F) * context.scale + (1.0F - rowProgress) * (4.0F) * context.scale,
                  rowY,
                  (5.5F) * context.scale,
                  GuiVisuals.white
               );
               GuiTextRenderer.draw(
                  GuiFont.SEMI,
                  "x" + state.purchaseHistory.get(entryIndex).count(),
                  x
                     + (19.5F) * context.scale
                     + (1.0F - rowProgress) * (4.0F) * context.scale
                     + GuiTextRenderer.getWidth(GuiFont.SEMI, state.purchaseHistory.get(entryIndex).item().name(), (5.5F) * context.scale),
                  rowY + (0.5F) * context.scale,
                  (5.0F) * context.scale,
                  GuiVisuals.brightTextColor
               );
               Render2D.popScissor(context.drawContext);
               GuiTextRenderer.draw(
                  GuiFont.SEMI,
                  state.purchaseHistory.get(entryIndex).time(),
                  x + (95.0F) * context.scale,
                  rowY,
                  (5.5F) * context.scale,
                  GuiVisuals.mutedTextColor
               );
               GuiTextRenderer.draw(
                  GuiFont.SEMI,
                  AutoBuyState.formatCoins(state.purchaseHistory.get(entryIndex).priceCoins()),
                  x
                     + (8.0F) * context.scale
                     + priceColumnWidth
                     - GuiTextRenderer.getWidth(GuiFont.SEMI, AutoBuyState.formatCoins(state.purchaseHistory.get(entryIndex).priceCoins()), (5.5F) * context.scale),
                  rowY,
                  (5.5F) * context.scale,
                  GuiVisuals.white
               );
               GuiTextRenderer.draw(
                  GuiFont.SEMI,
                  ("$"),
                  x
                     + (8.0F) * context.scale
                     + priceColumnWidth
                     - GuiTextRenderer.getWidth(GuiFont.SEMI, AutoBuyState.formatCoins(state.purchaseHistory.get(entryIndex).priceCoins()), (5.5F) * context.scale)
                     - GuiTextRenderer.getWidth(GuiFont.SEMI, ("$"), (5.5F) * context.scale),
                  rowY,
                  (5.5F) * context.scale,
                  GuiVisuals.accentColor
               );
               Render2D.popAlpha();
            }
         }
      }

      Render2D.popScissor(context.drawContext);
      Render2D.fillRoundedRectangle(
         x + width - (5.0F) * context.scale, listTop, 1.0F * context.scale, listHeight, (0.5F) * context.scale, GuiVisuals.panelOutlineColor
      );
      if (state.purchaseHistoryScroll.maxScroll > 0.0F) {
         Render2D.fillRoundedRectangle(
            x + width - (5.0F) * context.scale,
            listTop
               + (listHeight - Math.max((6.0F) * context.scale, listHeight * (listHeight / (listHeight + state.purchaseHistoryScroll.maxScroll))))
                  * (state.purchaseHistoryScroll.getScroll() / state.purchaseHistoryScroll.maxScroll),
            1.0F * context.scale,
            Math.max((6.0F) * context.scale, listHeight * (listHeight / (listHeight + state.purchaseHistoryScroll.maxScroll))),
            (0.5F) * context.scale,
            GuiVisuals.hoverFillColor
         );
      }
   }

   private AutoBuyPanels() {
   }

   public static void renderInput(
      ClickGuiContext context, AutoBuyState state, TextSetting setting, String inputKey, String prefix, String suffix, float x, float y, float width, float height, boolean interactive, long nowMillis
   ) {
      Render2D.fillRoundedRectangle(x, y, width, height, 2.0F * context.scale, ClickGuiTheme.itemPanelFill);
      Render2D.drawOutline(
         x,
         y,
         width,
         height,
         2.0F * context.scale,
         (0.5F),
         GuiVisuals.lerpColor(GuiVisuals.panelOutlineColor, GuiVisuals.focusedFieldOutlineColor, context.animateBoolean("ab:inf:" + inputKey, context.focusedText == setting))
      );
      Render2D.fillRoundedRectangle(
         x + width - (14.5F) * context.scale,
         y + 1.0F * context.scale,
         (0.5F) * context.scale,
         height - 2.0F * context.scale,
         0.0F,
         GuiVisuals.panelOutlineColor
      );
      String displayText = context.focusedText == setting
         ? (prefix == null ? "" : prefix)
            + (setting.getValue().isEmpty() ? "" : (prefix == null && suffix == null ? setting.getValue() : AutoBuyState.formatCoins(AutoBuyState.parseCoins(setting.getValue()))))
            + ClickGuiContext.blinkingCursor()
            + (suffix == null ? "" : suffix)
         : (
            setting.getValue().isEmpty()
               ? setting.getPlaceholder()
               : (
                  prefix == null && suffix == null
                     ? setting.getValue()
                     : (prefix == null ? "" : prefix) + AutoBuyState.formatCoins(AutoBuyState.parseCoins(setting.getValue())) + (suffix == null ? "" : suffix)
               )
         );
      Render2D.pushScissor(
         context.drawContext, x + (5.0F) * context.scale, y, width - (14.5F) * context.scale - (8.0F) * context.scale, height
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         displayText,
         context.focusedText == setting
               && GuiTextRenderer.getWidth(GuiFont.SEMI, displayText, (5.0F) * context.scale)
                  > width - (14.5F) * context.scale - (8.0F) * context.scale
            ? x + width - (17.5F) * context.scale - GuiTextRenderer.getWidth(GuiFont.SEMI, displayText, (5.0F) * context.scale)
            : x + (5.0F) * context.scale,
         y + height / 2.0F - (2.5F) * context.scale,
         (5.0F) * context.scale,
         setting.getValue().isEmpty() && context.focusedText != setting ? GuiVisuals.mutedTextColor : GuiVisuals.white
      );
      Render2D.popScissor(context.drawContext);
      float confirmationProgress = state.inputConfirmedAtMillis.get(inputKey) != null && (float)(nowMillis - state.inputConfirmedAtMillis.get(inputKey)) / (400.0F) < 1.0F
         ? 1.0F - (float)(nowMillis - state.inputConfirmedAtMillis.get(inputKey)) / (400.0F)
         : 0.0F;
      if (state.inputConfirmedAtMillis.get(inputKey) != null && confirmationProgress <= 0.0F) {
         state.inputConfirmedAtMillis.remove(inputKey);
      }

      Render2D.fillRoundedRectangle(
         x + width - (9.5F) * context.scale,
         y + height / 2.0F - (2.5F) * context.scale,
         (5.0F) * context.scale,
         (5.0F) * context.scale,
         (1.5F) * context.scale,
         GuiVisuals.lerpColor(
            GuiVisuals.lerpColor(
               GuiVisuals.accentColor,
               GuiVisuals.accentGradientColor,
               context.animateBoolean(
                  "ab:int:" + inputKey,
                  interactive
                     && GuiVisuals.containsPoint(
                        context.mouseX, context.mouseY, x + width - (14.0F) * context.scale, y, (14.0F) * context.scale, height
                     )
               )
            ),
            GuiVisuals.white,
            confirmationProgress
         )
      );
      Render2D.fillRoundedRectangle(
         x + width - (9.5F) * context.scale + (1.1F) * context.scale,
         y + height / 2.0F - (2.5F) * context.scale + (1.1F) * context.scale,
         (5.0F) * context.scale - (2.2F) * context.scale,
         (5.0F) * context.scale - (2.2F) * context.scale,
         (0.9F) * context.scale,
         -1
      );
      Render2D.fillCircle(x + width - (7.0F) * context.scale, y + height / 2.0F, (0.7F) * context.scale, (-3158318));
      if (interactive) {
         context.registerClick(x, y, width - (14.0F) * context.scale, height, null, () -> {
            context.focusedText = setting;
            setting.moveToEnd(false);
         });
         context.registerClick(x + width - (14.0F) * context.scale, y, (14.0F) * context.scale, height, null, () -> {
            if (context.focusedText == setting) {
               context.focusedText = null;
            }

            state.inputConfirmedAtMillis.put(inputKey, System.currentTimeMillis());
         });
      }
   }

   private static void renderSpeedSlider(ClickGuiContext context, AutoBuyState state, float x, float y, float width, boolean interactive) {
      if (Math.max(0, Math.min(2, state.autoBuySpeed.intValue())) != 2) {
         GuiTextRenderer.draw(
            GuiFont.SEMI, AutoBuyState.SPEED_LABELS[Math.max(0, Math.min(2, state.autoBuySpeed.intValue()))], x, y, (4.5F) * context.scale, GuiVisuals.brightTextColor
         );
      }

      GuiTextRenderer.draw(
         GuiFont.SEMI,
         AutoBuyState.SPEED_LABELS[2],
         x + width - GuiTextRenderer.getWidth(GuiFont.SEMI, AutoBuyState.SPEED_LABELS[2], (4.5F) * context.scale),
         y,
         (4.5F) * context.scale,
         GuiVisuals.white
      );
      Render2D.fillRoundedRectangle(
         x, y + (7.0F) * context.scale, width, (7.0F) * context.scale, 2.0F * context.scale, ClickGuiTheme.itemHoverFill
      );
      Render2D.fillRoundedRectangle(
         x,
         y + (7.0F) * context.scale + (0.15F),
         x
            + 1.0F * context.scale
            + context.animateValue(("ab:speed"), state.autoBuySpeed.getNormalizedValue()) * (width - 2.0F * context.scale - (5.0F) * context.scale)
            + (6.0F) * context.scale
            - x,
         (7.0F) * context.scale,
         2.0F * context.scale,
         GuiVisuals.lerpColor(
            GuiVisuals.accentColor, GuiVisuals.lerpColor(GuiVisuals.speedOptionBackgroundColor, GuiVisuals.accentColor, (0.3F)), context.animateObjectBoolean(state.autoBuySpeed, state.autoBuySpeed.intValue() == 2)
         )
      );
      if (context.animateObjectBoolean(state.autoBuySpeed, state.autoBuySpeed.intValue() == 2) > (0.005F)) {
         Render2D.drawDither(
            ClickGuiContext.snapToPixel(x),
            ClickGuiContext.snapToPixel((y + (7.0F) * context.scale) * context.scale),
            width,
            (7.0F) * context.scale,
            2.0F * context.scale,
            Math.max(2.0F, Math.round((3.0F) * context.scale)) / 2.0F,
            context.animateObjectBoolean(state.autoBuySpeed, state.autoBuySpeed.intValue() == 2),
            GuiVisuals.accentColor
         );
      }

      if (context.animateObjectBoolean(state.autoBuySpeed, state.autoBuySpeed.intValue() == 2) > (0.005F)) {
         Render2D.pushScissor(context.drawContext, x, y + (7.0F) * context.scale, width, (7.0F) * context.scale);
         RoundedClipStack.push(
            x,
            y + (7.0F) * context.scale,
            width,
            (7.0F) * context.scale,
            2.0F * context.scale,
            2.0F * context.scale,
            2.0F * context.scale,
            2.0F * context.scale
         );

         for (int var6 = 0; var6 * Math.max(2.0F, Math.round((3.0F) * context.scale)) < width; var6++) {
            for (int var7 = 0; var7 * Math.max(2.0F, Math.round((3.0F) * context.scale)) < (7.0F) * context.scale; var7++) {
               if ((var6 + var7 + (int)(System.currentTimeMillis() / (90L))) % 2 != 1) {
                  Render2D.fillRoundedRectangle(
                     x + var6 * Math.max(2.0F, Math.round((3.0F) * context.scale)),
                     y + (7.0F) * context.scale + var7 * Math.max(2.0F, Math.round((3.0F) * context.scale)),
                     Math.max(2.0F, Math.round((3.0F) * context.scale)),
                     Math.max(2.0F, Math.round((3.0F) * context.scale)),
                     0.0F,
                     GuiVisuals.multiplyAlpha(
                        GuiVisuals.accentColor,
                        context.animateObjectBoolean(state.autoBuySpeed, state.autoBuySpeed.intValue() == 2)
                           * ((0.28F) + (0.34F) * (float)Math.sin(System.currentTimeMillis() / (260.0D) + var6 * (0.55F)))
                     )
                  );
               }
            }
         }

         RoundedClipStack.pop();
         Render2D.popScissor(context.drawContext);
      }

      for (int var8 = 0; var8 < 3; var8++) {
         Render2D.fillRoundedRectangle(
            x + (1.5F) * context.scale + var8 * (width - (5.0F) * context.scale) / 2.0F,
            y + (10.5F) * context.scale - 1.0F * context.scale + (0.25F),
            2.0F * context.scale,
            2.0F * context.scale,
            1.0F * context.scale,
            GuiVisuals.dimTextColor
         );
      }

      Render2D.fillRoundedRectangle(
         x + 1.0F * context.scale + context.animateValue(("ab:speed"), state.autoBuySpeed.getNormalizedValue()) * (width - (7.0F) * context.scale),
         y + (8.0F) * context.scale,
         (5.0F) * context.scale,
         (5.0F) * context.scale,
         1.0F * context.scale,
         GuiVisuals.buttonTextColor
      );
      if (interactive) {
         context.sliderBounds.put(state.autoBuySpeed, new float[]{x, width});
         context.registerClick(x, y + (5.0F) * context.scale, width, (11.0F) * context.scale, null, () -> context.draggedSlider = state.autoBuySpeed);
      }
   }

   private static void renderEditingBadge(ClickGuiContext context, AutoBuyState state, AutoBuyGuiController controller, float x, float y, float width, boolean interactive, boolean editing) {
      if (!(context.animateBoolean(("ab:editmode"), editing) < (0.005F)) && lastEditedItem != null && ServerItemCatalog.findByName(lastEditedItem) != null) {
         float badgeWidth = (3.0F) * context.scale
            + (5.5F) * context.scale
            + (3.0F) * context.scale
            + GuiTextRenderer.getWidth(GuiFont.SEMI, lastEditedItem, (5.0F) * context.scale)
            + (4.0F) * context.scale
            + (5.0F) * context.scale
            + (3.0F) * context.scale;
         float badgeX = x + width - (8.0F) * context.scale - badgeWidth + (1.0F - context.animateBoolean(("ab:editmode"), editing)) * (8.0F) * context.scale;
         float badgeY = y + (8.0F) * context.scale + (2.5F) * context.scale - (5.0F) * context.scale;
         Render2D.pushAlpha(context.animateBoolean(("ab:editmode"), editing));
         Render2D.fillRoundedRectangle(badgeX, badgeY, badgeWidth, (10.0F) * context.scale, 2.0F * context.scale, ClickGuiTheme.selectedItemFill);
         GuiItemRenderer.render(
            ServerItemCatalog.findByName(lastEditedItem).displayStack(),
            badgeX + (3.0F) * context.scale,
            badgeY + (2.25F) * context.scale,
            (5.5F) * context.scale,
            ItemRenderOptions.noDecorations(AutoBuyGuiController.currentRenderAlpha())
         );
         GuiTextRenderer.draw(
            GuiFont.SEMI, lastEditedItem, badgeX + (11.5F) * context.scale, badgeY + 2.0F * context.scale, (5.0F) * context.scale, GuiVisuals.white
         );
         context.drawIcon(
            ("ε"),
            badgeX + badgeWidth - (5.5F) * context.scale,
            badgeY + (5.0F) * context.scale,
            (5.5F) * context.scale,
            GuiVisuals.lerpColor(
               GuiVisuals.mutedTextColor,
               GuiVisuals.white,
               context.animateBoolean(
                  ("ab:editx"), interactive && editing && GuiVisuals.containsPoint(context.mouseX, context.mouseY, badgeX, badgeY, badgeWidth, (10.0F) * context.scale)
               )
            )
         );
         Render2D.popAlpha();
         if (interactive && editing) {
            context.registerClick(badgeX, badgeY, badgeWidth, (10.0F) * context.scale, null, () -> controller.stopEditingPrices());
         }
      }
   }

   public static void renderSettingDescription(ClickGuiContext context, String label, String description, float x, float y, float width) {
      GuiTextRenderer.draw(GuiFont.SEMI, label, x, y, (5.5F) * context.scale, GuiVisuals.white);
      float lineY = y + (8.0F) * context.scale;

      for (String line : GuiVisuals.wrapWords(GuiFont.SEMI, description, (5.0F) * context.scale, width)) {
         GuiTextRenderer.draw(GuiFont.SEMI, line, x, lineY, (5.0F) * context.scale, GuiVisuals.brightTextColor);
         lineY += (6.0F) * context.scale;
      }
   }

   public static void renderItemGrid(ClickGuiContext context, AutoBuyState state, AutoBuyGuiController controller, float x, float y, float width, float height, boolean interactive, float deltaSeconds, long nowMillis) {
      float cellSize = (23.0F) * context.scale;
      float gridWidth = width - (5.0F) * context.scale;
      float cellStride = cellSize + (gridWidth - (11.0F) * cellSize) / (10.0F);
      state.itemGridBounds = new float[]{x, y, width, height};
      java.util.ArrayList<com.mouseclient.modules.impl.player.ServerItemDefinition> items = state.filteredItems();
      state.itemGridScroll.maxScroll = Math.max(0.0F, (items.size() + 10) / 11 * cellStride - (gridWidth - (11.0F) * cellSize) / (10.0F) - height);
      state.itemGridScroll.update(deltaSeconds);
      if (items.isEmpty()) {
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            ("Ничего не найдено"),
            x + (gridWidth - GuiTextRenderer.getWidth(GuiFont.SEMI, ("Ничего не найдено"), (5.0F) * context.scale)) / 2.0F,
            y + height / 2.0F - (2.5F) * context.scale,
            (5.0F) * context.scale,
            GuiVisuals.mutedTextColor
         );
      }

      float[] gridClip = new float[]{x, y, gridWidth, height};
      String hoveredItem = "";
      float tooltipCenterX = 0.0F;
      float tooltipAnchorY = 0.0F;
      Render2D.pushScissor(
         context.drawContext,
         x - (3.0F) * context.scale,
         y - 2.0F * context.scale,
         gridWidth + (6.0F) * context.scale,
         height + (4.0F) * context.scale
      );
      RoundedClipStack.push(
         x - (3.0F) * context.scale,
         y - 2.0F * context.scale,
         gridWidth + (6.0F) * context.scale,
         height + (4.0F) * context.scale,
         (4.0F) * context.scale,
         (4.0F) * context.scale,
         (4.0F) * context.scale,
         (4.0F) * context.scale
      );

      for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
         float cellX = x + itemIndex % 11 * cellStride;
         float cellY = y + itemIndex / 11 * cellStride - state.itemGridScroll.getScroll();
         if (!(cellY + cellSize < y - 2.0F) && !(cellY > y + height + 2.0F)) {
            String itemName = ((ServerItemDefinition)items.get(itemIndex)).name();
            float cellProgress = AutoBuyGuiController.staggeredProgress(
               nowMillis - state.gridChangedAtMillis, Math.min((0.5F), (0.05F) + itemIndex / 11 * (0.045F) + itemIndex % 11 * (0.012F)), (0.25F)
            );
            if (!(cellProgress < (0.005F))) {
               float animatedCellY = cellY + (1.0F - cellProgress) * (3.0F) * context.scale;
               float selectedProgress = context.animateBoolean("ab:sel:" + itemName, state.isItemSelected(itemName));
               float hoverProgress = context.animateBoolean(
                  "ab:hov:" + itemName,
                  interactive
                     && GuiVisuals.containsPoint(context.mouseX, context.mouseY, cellX, animatedCellY, cellSize, cellSize)
                     && GuiVisuals.containsPoint(context.mouseX, context.mouseY, x, y, gridWidth, height)
               );
               Render2D.pushAlpha(cellProgress);
               if (hoverProgress > (0.005F)) {
                  Render2D.fillRoundedRectangle(
                     cellX, animatedCellY, cellSize, cellSize, (4.0F) * context.scale, GuiVisuals.multiplyAlpha(ClickGuiTheme.itemHoverFill, hoverProgress)
                  );
               }

               if (selectedProgress > (0.005F)) {
                  Render2D.fillRoundedRectangle(
                     cellX, animatedCellY, cellSize, cellSize, (4.0F) * context.scale, GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.14F) * selectedProgress)
                  );
               }

               if (context.animateBoolean("ab:editc:" + itemName, itemName.equals(state.editingItem)) > (0.005F)) {
                  Render2D.drawOutline(
                     cellX,
                     animatedCellY,
                     cellSize,
                     cellSize,
                     (4.0F) * context.scale,
                     (0.5F),
                     GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, context.animateBoolean("ab:editc:" + itemName, itemName.equals(state.editingItem)))
                  );
               }

               if (state.selectionChangedAtMillis.get(itemName) != null && (float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F) < 1.0F) {
                  Render2D.drawOutline(
                     cellX
                        - (2.5F)
                           * context.scale
                           * ((float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F) * (2.0F - (float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F))),
                     animatedCellY
                        - (2.5F)
                           * context.scale
                           * ((float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F) * (2.0F - (float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F))),
                     cellSize
                        + (5.0F)
                           * context.scale
                           * ((float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F) * (2.0F - (float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F))),
                     cellSize
                        + (5.0F)
                           * context.scale
                           * ((float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F) * (2.0F - (float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F))),
                     (4.0F) * context.scale
                        + (2.5F)
                           * context.scale
                           * ((float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F) * (2.0F - (float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F))),
                     (0.75F),
                     GuiVisuals.multiplyAlpha(
                        GuiVisuals.accentColor,
                        (1.0F - (float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F) * (2.0F - (float)(nowMillis - state.selectionChangedAtMillis.get(itemName)) / (350.0F)))
                           * (0.9F)
                     )
                  );
               } else if (state.selectionChangedAtMillis.get(itemName) != null) {
                  state.selectionChangedAtMillis.remove(itemName);
               }

               GuiItemRenderer.render(
                  ((ServerItemDefinition)items.get(itemIndex)).displayStack(),
                  cellX + cellSize / (4.0F),
                  animatedCellY + cellSize / (4.0F),
                  cellSize * (0.5F),
                  ItemRenderOptions.noDecorations(AutoBuyGuiController.currentRenderAlpha() * ((0.48F) + (0.52F) * Math.max(selectedProgress, hoverProgress * (0.55F))))
               );
               Render2D.popAlpha();
               if (interactive
                  && GuiVisuals.containsPoint(context.mouseX, context.mouseY, cellX, animatedCellY, cellSize, cellSize)
                  && GuiVisuals.containsPoint(context.mouseX, context.mouseY, x, y, gridWidth, height)) {
                  hoveredItem = itemName;
                  tooltipCenterX = cellX + cellSize / 2.0F;
                  tooltipAnchorY = animatedCellY;
               }

               if (interactive) {
                  context.registerClick(cellX, animatedCellY, cellSize, cellSize, gridClip, () -> state.toggleItem(itemName), () -> controller.editItemPrices(itemName));
               }
            }
         }
      }

      RoundedClipStack.pop();
      Render2D.popScissor(context.drawContext);
      if (!hoveredItem.isEmpty()) {
         Render2D.fillRoundedRectangle(
            Math.max(
               x,
               Math.min(
                  x + gridWidth - GuiTextRenderer.getWidth(GuiFont.SEMI, hoveredItem, (5.0F) * context.scale) - (6.0F) * context.scale,
                  tooltipCenterX - GuiTextRenderer.getWidth(GuiFont.SEMI, hoveredItem, (5.0F) * context.scale) / 2.0F - (3.0F) * context.scale
               )
            ),
            tooltipAnchorY - (12.0F) * context.scale < y ? tooltipAnchorY + cellSize + (3.0F) * context.scale : tooltipAnchorY - (12.0F) * context.scale,
            GuiTextRenderer.getWidth(GuiFont.SEMI, hoveredItem, (5.0F) * context.scale) + (6.0F) * context.scale,
            (10.0F) * context.scale,
            (3.0F) * context.scale,
            ClickGuiTheme.itemHoverFill
         );
         Render2D.drawOutline(
            Math.max(
               x,
               Math.min(
                  x + gridWidth - GuiTextRenderer.getWidth(GuiFont.SEMI, hoveredItem, (5.0F) * context.scale) - (6.0F) * context.scale,
                  tooltipCenterX - GuiTextRenderer.getWidth(GuiFont.SEMI, hoveredItem, (5.0F) * context.scale) / 2.0F - (3.0F) * context.scale
               )
            ),
            tooltipAnchorY - (12.0F) * context.scale < y ? tooltipAnchorY + cellSize + (3.0F) * context.scale : tooltipAnchorY - (12.0F) * context.scale,
            GuiTextRenderer.getWidth(GuiFont.SEMI, hoveredItem, (5.0F) * context.scale) + (6.0F) * context.scale,
            (10.0F) * context.scale,
            (3.0F) * context.scale,
            (0.5F),
            GuiVisuals.panelOutlineColor
         );
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            hoveredItem,
            Math.max(
                  x,
                  Math.min(
                     x + gridWidth - GuiTextRenderer.getWidth(GuiFont.SEMI, hoveredItem, (5.0F) * context.scale) - (6.0F) * context.scale,
                     tooltipCenterX - GuiTextRenderer.getWidth(GuiFont.SEMI, hoveredItem, (5.0F) * context.scale) / 2.0F - (3.0F) * context.scale
                  )
               )
               + (3.0F) * context.scale,
            (tooltipAnchorY - (12.0F) * context.scale < y ? tooltipAnchorY + cellSize + (3.0F) * context.scale : tooltipAnchorY - (12.0F) * context.scale)
               + (2.5F) * context.scale,
            (5.0F) * context.scale,
            GuiVisuals.white
         );
      }

      if (state.itemGridScroll.maxScroll > 0.0F) {
         Render2D.fillRoundedRectangle(
            x + width - 1.0F * context.scale,
            y
               + (height - Math.max((8.0F) * context.scale, height * (height / (height + state.itemGridScroll.maxScroll))))
                  * (state.itemGridScroll.getScroll() / state.itemGridScroll.maxScroll),
            1.0F * context.scale,
            Math.max((8.0F) * context.scale, height * (height / (height + state.itemGridScroll.maxScroll))),
            (0.5F) * context.scale,
            GuiVisuals.hoverFillColor
         );
      }
   }

   public static void renderPriceChart(ClickGuiContext context, AutoBuyState state, float x, float y, float width, float height, long nowMillis, long openedAtMillis, float deltaSeconds) {
      context.drawStrongTintPanel(x, y, width, height, (4.0F) * context.scale, ClickGuiTheme.itemPanelFill, GuiVisuals.panelOutlineColor);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Price Chart"),
         x + (8.0F) * context.scale,
         y + (8.0F) * context.scale,
         (5.0F) * context.scale,
         GuiVisuals.mutedTextColor
      );
      state.animatedPurchaseTotal = state.animatedPurchaseTotal + (state.totalPurchaseCoins - state.animatedPurchaseTotal) * (1.0 - Math.exp(-deltaSeconds * (8.0F)));
      if (Math.abs(state.totalPurchaseCoins - state.animatedPurchaseTotal) < 1.0) {
         state.animatedPurchaseTotal = state.totalPurchaseCoins;
      }

      GuiTextRenderer.draw(
         GuiFont.SEMI,
         "Total sales: $" + AutoBuyState.formatCoins(Math.round(state.animatedPurchaseTotal)),
         x
            + width
            - (8.0F) * context.scale
            - GuiTextRenderer.getWidth(GuiFont.SEMI, "Total sales: $" + AutoBuyState.formatCoins(Math.round(state.animatedPurchaseTotal)), (4.5F) * context.scale),
         y + (8.5F) * context.scale,
         (4.5F) * context.scale,
         GuiVisuals.dimTextColor
      );
      float chartTop = y + (22.0F) * context.scale;
      float chartHeight = y + height - (8.0F) * context.scale - chartTop;
      float chartLeft = x + (28.0F) * context.scale;
      float chartWidth = x + width - (8.0F) * context.scale - chartLeft;

      for (int labelIndex = 0; labelIndex < CHART_LABELS.length; labelIndex++) {
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            CHART_LABELS[labelIndex],
            x + (20.0F) * context.scale - GuiTextRenderer.getWidth(GuiFont.SEMI, CHART_LABELS[labelIndex], (4.5F) * context.scale),
            chartTop + labelIndex * (chartHeight - (5.0F) * context.scale) / (CHART_LABELS.length - 1),
            (4.5F) * context.scale,
            GuiVisuals.dimTextColor
         );
         Render2D.fillRoundedRectangle(
            chartLeft,
            chartTop + labelIndex * (chartHeight - (5.0F) * context.scale) / (CHART_LABELS.length - 1) + (2.25F) * context.scale,
            chartWidth,
            (0.5F) * context.scale,
            (0.25F) * context.scale,
            GuiVisuals.faintDividerColor
         );
      }

      for (int barIndex = 0; barIndex < state.chartHeights.length; barIndex++) {
         float barHeight = Math.max(
            0.0F,
            context.animateValue("ab:bar:" + barIndex, state.chartHeights[barIndex])
               * chartHeight
               * AutoBuyGuiController.staggeredProgress(nowMillis - openedAtMillis, (0.18F) + barIndex * (0.025F), (0.3F))
         );
         if (!(barHeight < (0.5F))) {
            Render2D.fillRoundedRectangle(
               chartLeft + barIndex * (chartWidth - (13.0F) * context.scale) / (state.chartHeights.length - 1),
               chartTop + chartHeight - barHeight,
               (13.0F) * context.scale,
               barHeight,
               Math.min(2.0F * context.scale, barHeight / 2.0F),
               Math.min(2.0F * context.scale, barHeight / 2.0F),
               0.0F,
               0.0F,
               GuiVisuals.lerpColor(
                  GuiVisuals.accentColor,
                  GuiVisuals.accentGradientColor,
                  context.animateBoolean(
                     "ab:barh:" + barIndex,
                     GuiVisuals.containsPoint(
                        context.mouseX,
                        context.mouseY,
                        chartLeft + barIndex * (chartWidth - (13.0F) * context.scale) / (state.chartHeights.length - 1),
                        chartTop,
                        (13.0F) * context.scale,
                        chartHeight
                     )
                  )
               )
            );
         }
      }
   }
}
