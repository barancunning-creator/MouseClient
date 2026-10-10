package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.GuiShapeRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.modules.impl.player.ServerItemCatalog;

public final class AutoBuyGuiController {
   public static final String CLOSE_GLYPH = "ε";
   private static AutoBuyGuiController INSTANCE;
   private ScalarAnimation openingAnimation;
   private boolean open;
   private long openedAtMillis;
   private ClickGuiContext context;

   public boolean handleEscape() {
      if (AutoBuyState.getInstance().editingItem != null) {
         this.stopEditingPrices();
         return true;
      }

      if (!this.open) {
         return false;
      }

      this.open = false;
      return true;
   }

   public void open() {
      if (!this.open) {
         ServerItemCatalog.ensureInitialized();
         this.open = true;
         this.openedAtMillis = System.currentTimeMillis();
         AutoBuyState.getInstance().gridChangedAtMillis = this.openedAtMillis;
         this.stopEditingPrices();
      }
   }

   public static float staggeredProgress(long elapsedMillis, float delaySeconds, float durationSeconds) {
      float progress = ((float)elapsedMillis / (1000.0F) - delaySeconds) / durationSeconds;
      if (progress <= 0.0F) {
         return 0.0F;
      } else {
         return progress >= 1.0F ? 1.0F : progress * progress * ((3.0F) - 2.0F * progress);
      }
   }

   public boolean isOpen() {
      return this.open;
   }

   private void drawShortcutHint(ClickGuiContext context, String keyLabel, String description, float x, float y) {
      Render2D.fillRoundedRectangle(
         x,
         y,
         GuiTextRenderer.getWidth(GuiFont.SEMI, keyLabel, (4.5F) * context.scale) + (6.0F) * context.scale,
         (9.0F) * context.scale,
         2.0F * context.scale,
         ClickGuiTheme.selectedItemFill
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI, keyLabel, x + (3.0F) * context.scale, y + (2.25F) * context.scale, (4.5F) * context.scale, GuiVisuals.accentColor
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         description,
         x + GuiTextRenderer.getWidth(GuiFont.SEMI, keyLabel, (4.5F) * context.scale) + (10.0F) * context.scale,
         y + 2.0F * context.scale,
         (5.0F) * context.scale,
         GuiVisuals.mutedTextColor
      );
   }

   public static float currentRenderAlpha() {
      return (Render2D.applyAlpha((-16777216)) >>> 24 & 0xFF) / (255.0F);
   }

   public static AutoBuyGuiController getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoBuyGuiController();
      }

      return INSTANCE;
   }

   private AutoBuyGuiController() {
      this.openingAnimation = new ScalarAnimation((300L)).reset(0.0);
   }

   private void renderWindow(ClickGuiContext context, float screenWidth, float screenHeight, boolean interactive, float deltaSeconds, float slideOffsetY) {
      float windowWidth = (512.0F) * context.scale;
      float windowHeight = (300.0F) * context.scale;
      float left = (screenWidth - windowWidth) / 2.0F;
      float top = (screenHeight - windowHeight) / 2.0F + slideOffsetY;
      long nowMillis = System.currentTimeMillis();
      HudPanelRenderer.drawGlassPanel(left, top, windowWidth, windowHeight, currentRenderAlpha(), (7.0F) * context.scale, GuiShapeRenderer.BlurQuality.LOW);
      if (interactive) {
         context.registerClick(left, top, windowWidth, windowHeight, null, () -> {});
      }

      context.drawIcon(
         ("9"), left + (13.0F) * context.scale, top + (12.0F) * context.scale, (6.6F) * context.scale, GuiVisuals.accentColor
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI, ("AutoBuy"), left + (21.0F) * context.scale, top + (9.0F) * context.scale, (6.0F) * context.scale, GuiVisuals.white
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         "в списке " + AutoBuyState.getInstance().selectedItems.size(),
         left
            + (21.0F) * context.scale
            + GuiTextRenderer.getWidth(GuiFont.SEMI, ("AutoBuy"), (6.0F) * context.scale)
            + (5.0F) * context.scale,
         top + (9.5F) * context.scale,
         (5.0F) * context.scale,
         GuiVisuals.mutedTextColor
      );
      float closeHoverProgress = context.animateBoolean(
         ("ab:close"),
         interactive
            && GuiVisuals.containsPoint(
               context.mouseX,
               context.mouseY,
               left + windowWidth - (19.0F) * context.scale,
               top + (6.0F) * context.scale,
               (12.0F) * context.scale,
               (12.0F) * context.scale
            )
      );
      if (closeHoverProgress > (0.005F)) {
         Render2D.fillCircle(
            left + windowWidth - (13.0F) * context.scale,
            top + (12.0F) * context.scale,
            (6.0F) * context.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.hoverFillColor, closeHoverProgress)
         );
      }

      context.drawIcon(
         ("ε"),
         left + windowWidth - (13.0F) * context.scale,
         top + (12.0F) * context.scale,
         (6.6F) * context.scale,
         GuiVisuals.lerpColor(GuiVisuals.dimTextColor, GuiVisuals.white, closeHoverProgress)
      );
      if (interactive) {
         context.registerClick(
            left + windowWidth - (19.0F) * context.scale,
            top + (6.0F) * context.scale,
            (12.0F) * context.scale,
            (12.0F) * context.scale,
            null,
            () -> this.close()
         );
      }

      Render2D.fillRoundedRectangle(
         left + (8.0F) * context.scale,
         top + (23.5F) * context.scale,
         windowWidth - (16.0F) * context.scale,
         (0.5F) * context.scale,
         0.0F,
         GuiVisuals.panelOutlineColor
      );
      float contentTop = top + (30.0F) * context.scale;
      float contentBottom = top + windowHeight - (8.0F) * context.scale;
      AutoBuyPanels.renderSearch(
         context, AutoBuyState.getInstance(), left + (8.0F) * context.scale, contentTop, (300.0F) * context.scale, (17.0F) * context.scale, interactive
      );
      AutoBuyPanels.renderItemGrid(
         context,
         AutoBuyState.getInstance(),
         this,
         left + (8.0F) * context.scale,
         contentTop + (21.0F) * context.scale,
         (300.0F) * context.scale,
         contentBottom - (14.0F) * context.scale - contentTop - (21.0F) * context.scale,
         interactive,
         deltaSeconds,
         nowMillis
      );
      this.drawShortcutHint(context, ("ЛКМ"), ("добавить или убрать"), left + (8.0F) * context.scale, contentBottom - (9.0F) * context.scale);
      this.drawShortcutHint(context, ("ПКМ"), ("свои цены предмета"), left + (100.0F) * context.scale, contentBottom - (9.0F) * context.scale);
      float rightColumnX = left + (316.0F) * context.scale;
      float rightColumnWidth = windowWidth - (324.0F) * context.scale;
      Render2D.pushAlpha(staggeredProgress(nowMillis - this.openedAtMillis, (0.06F), (0.3F)));
      AutoBuyPanels.renderPricesPanel(context, AutoBuyState.getInstance(), this, rightColumnX, contentTop, rightColumnWidth, (80.0F) * context.scale, interactive, nowMillis);
      Render2D.popAlpha();
      Render2D.pushAlpha(staggeredProgress(nowMillis - this.openedAtMillis, (0.12F), (0.3F)));
      AutoBuyPanels.renderTelegramPanel(context, AutoBuyState.getInstance(), rightColumnX, contentTop + (86.0F) * context.scale, rightColumnWidth, (52.0F) * context.scale, interactive, nowMillis);
      Render2D.popAlpha();
      Render2D.pushAlpha(staggeredProgress(nowMillis - this.openedAtMillis, (0.18F), (0.3F)));
      AutoBuyPanels.renderPurchaseHistory(
         context,
         AutoBuyState.getInstance(),
         rightColumnX,
         contentTop + (144.0F) * context.scale,
         rightColumnWidth,
         contentBottom - contentTop - (144.0F) * context.scale,
         deltaSeconds,
         nowMillis,
         this.openedAtMillis
      );
      Render2D.popAlpha();
   }

   public void close() {
      this.open = false;
      this.stopEditingPrices();
   }

   public boolean mouseScrolled(float mouseX, float mouseY, double verticalAmount, float scale) {
      if (AutoBuyState.getInstance().itemGridBounds != null
         && GuiVisuals.containsPoint(
            mouseX,
            mouseY,
            AutoBuyState.getInstance().itemGridBounds[0],
            AutoBuyState.getInstance().itemGridBounds[1],
            AutoBuyState.getInstance().itemGridBounds[2],
            AutoBuyState.getInstance().itemGridBounds[3]
         )) {
         float scrollDelta = (float)verticalAmount * (24.0F);
         AutoBuyState.getInstance().itemGridScroll.target = ClickGuiContext.clamp(
            AutoBuyState.getInstance().itemGridScroll.target - scrollDelta * scale, 0.0F, AutoBuyState.getInstance().itemGridScroll.maxScroll
         );
      } else if (AutoBuyState.getInstance().purchaseHistoryBounds != null
         && GuiVisuals.containsPoint(
            mouseX,
            mouseY,
            AutoBuyState.getInstance().purchaseHistoryBounds[0],
            AutoBuyState.getInstance().purchaseHistoryBounds[1],
            AutoBuyState.getInstance().purchaseHistoryBounds[2],
            AutoBuyState.getInstance().purchaseHistoryBounds[3]
         )) {
         float historyScrollDelta = (float)verticalAmount * (24.0F);
         AutoBuyState.getInstance().purchaseHistoryScroll.target = ClickGuiContext.clamp(
            AutoBuyState.getInstance().purchaseHistoryScroll.target - historyScrollDelta * scale, 0.0F, AutoBuyState.getInstance().purchaseHistoryScroll.maxScroll
         );
      }

      return true;
   }

   public void editItemPrices(String itemName) {
      if (this.context != null) {
         this.context.focusedText = null;
      }

      if (!AutoBuyState.getInstance().isItemSelected(itemName)) {
         AutoBuyState.getInstance().toggleItem(itemName);
      }

      if (!itemName.equals(AutoBuyState.getInstance().editingItem)) {
         AutoBuyState.getInstance().editingItem = itemName;
         AutoBuyState.getInstance().priceModeChangedAtMillis = System.currentTimeMillis();
      }
   }

   public float openingProgress() {
      this.openingAnimation.setTarget(this.open ? 1.0 : 0.0);
      return (float)this.openingAnimation.value();
   }

   public void render(ClickGuiContext context, float screenWidth, float screenHeight, float progress, float deltaSeconds) {
      if (!(progress < (0.005F))) {
         this.context = context;
         context.modalClickStart = context.clickRegions.size();
         AutoBuyState.getInstance().updateChartHeights(System.currentTimeMillis());
         Render2D.fillRoundedRectangle(0.0F, 0.0F, screenWidth, screenHeight, 0.0F, GuiVisuals.multiplyAlpha(ClickGuiTheme.basePanelTint, progress));
         if (this.open && progress > (0.6F)) {
            context.registerClick(0.0F, 0.0F, screenWidth, screenHeight, null, () -> this.close());
         }

         Render2D.pushAlpha(progress);
         this.renderWindow(context, screenWidth, screenHeight, this.open && progress > (0.6F), deltaSeconds, (1.0F - progress) * (12.0F) * context.scale);
         Render2D.popAlpha();
      }
   }

   public void stopEditingPrices() {
      if (AutoBuyState.getInstance().editingItem != null) {
         if (this.context != null
            && AutoBuyState.getInstance().selectedItems.get(AutoBuyState.getInstance().editingItem) != null
            && (
               this.context.focusedText == AutoBuyState.getInstance().selectedItems.get(AutoBuyState.getInstance().editingItem).maxBuyPrice
                  || this.context.focusedText == AutoBuyState.getInstance().selectedItems.get(AutoBuyState.getInstance().editingItem).minSellPrice
            )) {
            this.context.focusedText = null;
         }

         AutoBuyState.getInstance().editingItem = null;
         AutoBuyState.getInstance().priceModeChangedAtMillis = System.currentTimeMillis();
      }
   }
}
