package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.GuiShapeRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.misc.Globals;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.render.CosmeticEntry;
import com.mouseclient.render.EquippedCosmetics;
import com.mouseclient.render.CosmeticCatalog;
import com.mouseclient.render.avatar.CosmeticAssetCache;
import com.mouseclient.render.avatar.Easings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public final class CosmeticWheelScreen extends Screen {
   private static final CosmeticCategory[] CATEGORIES = new CosmeticCategory[]{
      CosmeticCategory.HAT, CosmeticCategory.ACCESSORY, CosmeticCategory.WINGS, CosmeticCategory.SHIELD, CosmeticCategory.SWORD, CosmeticCategory.MACE, CosmeticCategory.PET, CosmeticCategory.BODYWEAR
   };
   
   private final TweenAnimation visibilityAnimation = new TweenAnimation();
   private final TweenAnimation optionRevealAnimation = new TweenAnimation();
   private final TweenAnimation[] categoryAnimations = new TweenAnimation[CATEGORIES.length];
   private final TweenAnimation[] optionHoverAnimations = new TweenAnimation[7];
   private final int[] categoryScrollOffsets = new int[CATEGORIES.length];
   private int selectedCategoryIndex = 0;
   private int hoveredOptionIndex = -1;
   private boolean closing;
   private float centerX;
   private float centerY;
   private float layoutScale;

   @Override
   protected void init() {
      this.closing = false;
      this.visibilityAnimation.animateTo(1.0, (0.22D), Easings.QUART_OUT);
      this.optionRevealAnimation.setValue(0.0);
      this.optionRevealAnimation.animateTo(1.0, (0.28D), Easings.QUART_OUT);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int totalOptions = this.options(CATEGORIES[this.selectedCategoryIndex]).size();
      if (totalOptions <= 7) {
         return true;
      }

      this.categoryScrollOffsets[this.selectedCategoryIndex] = Math.max(0, Math.min(totalOptions - 7, this.categoryScrollOffsets[this.selectedCategoryIndex] - (int)Math.signum(verticalAmount)));
      return true;
   }

   private void drawCaption(float screenWidth, float screenHeight, float alpha) {
      CosmeticCategory category = CATEGORIES[this.selectedCategoryIndex];
      java.util.List<com.mouseclient.render.CosmeticEntry> categoryOptions = this.options(category);
      CosmeticEntry entry = EquippedCosmetics.equippedEntry(category);
      String caption;
      if (this.hoveredOptionIndex >= 0) {
         CosmeticEntry hoveredEntry = (CosmeticEntry)categoryOptions.get(this.categoryScrollOffsets[this.selectedCategoryIndex] + this.hoveredOptionIndex);
         if (hoveredEntry == null) {
            caption = entry == null ? ("Ничего не надето") : "Снять " + entry.name();
         } else {
            caption = hoveredEntry.name() + (!CosmeticAvailability.isAvailable(hoveredEntry) ? (" · только бета") : (EquippedCosmetics.isEquipped(hoveredEntry) ? (" · надето") : ""));
         }
      } else if (categoryOptions.size() <= 1) {
         caption = ("Здесь пока пусто");
      } else {
         caption = entry == null ? ("Ничего не надето") : "Надето: " + entry.name();
      }

      float captionY = this.centerY + (220.0F) * this.layoutScale;
      GuiTextRenderer.drawCentered(
         GuiFont.SEMI_BOLD, category.displayName(), this.centerX, captionY, (13.0F) * this.layoutScale, GuiVisuals.multiplyAlpha(GuiVisuals.white, alpha)
      );
      GuiTextRenderer.drawCentered(
         GuiFont.SEMI,
         caption,
         this.centerX,
         captionY + (17.0F) * this.layoutScale,
         (8.5F) * this.layoutScale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, alpha * (0.62F))
      );
      GuiTextRenderer.drawCentered(
         GuiFont.SEMI,
         ("ЛКМ — надеть     ПКМ — снять     Колёсико — листать     Esc — закрыть"),
         this.centerX,
         captionY + (32.0F) * this.layoutScale,
         (7.5F) * this.layoutScale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, alpha * (0.4F))
      );
   }

   private List<CosmeticEntry> options(CosmeticCategory category) {
      ArrayList categoryOptions = new ArrayList();
      categoryOptions.add(null);
      categoryOptions.addAll(CosmeticCatalog.inCategory(category));
      return categoryOptions;
   }

   private void closeWithAnimation() {
      if (!this.closing) {
         this.closing = true;
         this.visibilityAnimation.animateTo(0.0, (0.16D), Easings.QUART_OUT);
      }
   }

   private int optionCount(int categoryIndex) {
      return Math.min(7, this.options(CATEGORIES[categoryIndex]).size() - this.categoryScrollOffsets[categoryIndex]);
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float tickDelta) {
   }

   private void drawOptions(float alpha) {
      java.util.List<com.mouseclient.render.CosmeticEntry> categoryOptions = this.options(CATEGORIES[this.selectedCategoryIndex]);
      int scrollOffset = this.categoryScrollOffsets[this.selectedCategoryIndex];
      int visibleCount = this.optionCount(this.selectedCategoryIndex);
      float revealProgress = this.optionRevealAnimation.floatValue();
      CosmeticEntry entry = EquippedCosmetics.equippedEntry(CATEGORIES[this.selectedCategoryIndex]);

      for (int optionIndex = 0; optionIndex < 7; optionIndex++) {
         TweenAnimation hoverAnimation = this.optionHoverAnimations[optionIndex];
         hoverAnimation.update();
         hoverAnimation.animateTo(optionIndex == this.hoveredOptionIndex ? 1.0 : 0.0, (0.14D), Easings.QUART_OUT);
         if (optionIndex < visibleCount) {
            CosmeticEntry optionEntry = (CosmeticEntry)categoryOptions.get(scrollOffset + optionIndex);
            float[] optionPosition = this.optionCenter(optionIndex, visibleCount);
            float staggeredReveal = Math.max(0.0F, Math.min(1.0F, revealProgress * (1.6F) - optionIndex * (0.08F)));
            float hoverProgress = hoverAnimation.floatValue();
            boolean equipped = optionEntry == null ? entry == null : EquippedCosmetics.isEquipped(optionEntry);
            boolean unavailable = optionEntry != null && !CosmeticAvailability.isAvailable(optionEntry);
            float optionAlpha = alpha * staggeredReveal * (unavailable ? (0.28F) : (0.55F) + (0.45F) * Math.max(hoverProgress, equipped ? 1.0F : 0.0F));
            float previewSize = (35.0F) * this.layoutScale * (1.0F + (0.14F) * hoverProgress + (equipped ? (0.04F) : 0.0F));
            if (optionEntry == null) {
               float unequipIconRadius = (7.0F) * this.layoutScale;
               Render2D.strokeCircle(optionPosition[0], optionPosition[1], unequipIconRadius, (1.2F) * this.layoutScale, GuiVisuals.multiplyAlpha(GuiVisuals.white, optionAlpha));
               Render2D.drawLine(
                  optionPosition[0] - unequipIconRadius * (0.62F),
                  optionPosition[1] + unequipIconRadius * (0.62F),
                  optionPosition[0] + unequipIconRadius * (0.62F),
                  optionPosition[1] - unequipIconRadius * (0.62F),
                  (1.2F) * this.layoutScale,
                  GuiVisuals.multiplyAlpha(GuiVisuals.white, optionAlpha)
               );
            } else {
               CosmeticAssetCache.PreviewAtlasCell previewCell = CosmeticAssetCache.previewCell(optionEntry);
               if (previewCell != null) {
                  Render2D.drawImageRegion(
                     previewCell.atlasTexture(),
                     optionPosition[0] - previewSize / 2.0F,
                     optionPosition[1] - previewSize / 2.0F,
                     previewSize,
                     previewSize,
                     0.0F,
                     previewCell.minU(),
                     previewCell.minV(),
                     previewCell.maxU(),
                     previewCell.maxV(),
                     GuiVisuals.multiplyAlpha(GuiVisuals.white, optionAlpha)
                  );
               } else {
                  Render2D.strokeCircle(
                     optionPosition[0], optionPosition[1], (8.0F) * this.layoutScale, 1.0F * this.layoutScale, GuiVisuals.multiplyAlpha(GuiVisuals.white, optionAlpha * (0.6F))
                  );
               }
            }

            if (equipped) {
               float markerRadius = (185.0F) * this.layoutScale + (23.0F) * this.layoutScale;
               Render2D.fillCircle(
                  this.centerX + (float)Math.cos(optionPosition[2]) * markerRadius,
                  this.centerY + (float)Math.sin(optionPosition[2]) * markerRadius,
                  (1.8F) * this.layoutScale,
                  GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, alpha * staggeredReveal)
               );
            }
         }
      }

      if (categoryOptions.size() > 7) {
         float scrollMarkerRadius = (185.0F) * this.layoutScale;
         double previousMarkerAngle = this.optionCenter(0, visibleCount)[2] - Math.toRadians((8.0D));
         double nextMarkerAngle = this.optionCenter(visibleCount - 1, visibleCount)[2] + Math.toRadians((8.0D));
         if (scrollOffset > 0) {
            Render2D.fillCircle(
               this.centerX + (float)Math.cos(previousMarkerAngle) * scrollMarkerRadius,
               this.centerY + (float)Math.sin(previousMarkerAngle) * scrollMarkerRadius,
               (1.3F) * this.layoutScale,
               GuiVisuals.multiplyAlpha(GuiVisuals.white, alpha * (0.4F))
            );
         }

         if (scrollOffset + visibleCount < categoryOptions.size()) {
            Render2D.fillCircle(
               this.centerX + (float)Math.cos(nextMarkerAngle) * scrollMarkerRadius,
               this.centerY + (float)Math.sin(nextMarkerAngle) * scrollMarkerRadius,
               (1.3F) * this.layoutScale,
               GuiVisuals.multiplyAlpha(GuiVisuals.white, alpha * (0.4F))
            );
         }
      }
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public boolean keyPressed(KeyInput keyInput) {
      if (keyInput.key() != 256 && (!Globals.getInstance().cosmeticsMenuBind.isBound() || keyInput.key() != Globals.getInstance().cosmeticsMenuBind.getKeyCode())) {
         return super.keyPressed(keyInput);
      }

      this.closeWithAnimation();
      return true;
   }

   private void updateHover(float mouseX, float mouseY) {
      this.hoveredOptionIndex = -1;
      if (!this.closing) {
         float deltaX = mouseX - this.centerX;
         float deltaY = mouseY - this.centerY;
         float radius = (float)Math.hypot(deltaX, deltaY);
         if (radius > (60.0F) * this.layoutScale && radius < (162.0F) * this.layoutScale) {
            double categoryAngle = Math.toDegrees(Math.atan2(deltaY, deltaX)) + (90.0D) + (22.5D);

            while (categoryAngle < 0.0) {
               categoryAngle += (360.0D);
            }

            int categoryIndex = (int)(categoryAngle % (360.0D) / (45.0D));
            if (categoryIndex != this.selectedCategoryIndex) {
               this.selectedCategoryIndex = categoryIndex;
               this.optionRevealAnimation.setValue(0.0);
               this.optionRevealAnimation.animateTo(1.0, (0.28D), Easings.QUART_OUT);
            }
         }

         int visibleCount = this.optionCount(this.selectedCategoryIndex);

         for (int optionIndex = 0; optionIndex < visibleCount; optionIndex++) {
            float[] optionPosition = this.optionCenter(optionIndex, visibleCount);
            if (Math.hypot(mouseX - optionPosition[0], mouseY - optionPosition[1]) < (19.0F) * this.layoutScale) {
               this.hoveredOptionIndex = optionIndex;
            }
         }
      }
   }

   public static float modelAlpha() {
      return MinecraftClient.getInstance().currentScreen instanceof CosmeticWheelScreen wheelScreen ? wheelScreen.visibilityAnimation.floatValue() : 1.0F;
   }

   private float[] optionCenter(int optionIndex, int visibleOptionCount) {
      double angle = Math.toRadians((-90.0D) + this.selectedCategoryIndex * (45.0D) + (optionIndex - (visibleOptionCount - 1) / (2.0D)) * (12.5D));
      float radius = (185.0F) * this.layoutScale * ((0.94F) + (0.06F) * this.optionRevealAnimation.floatValue());
      return new float[]{this.centerX + (float)Math.cos(angle) * radius, this.centerY + (float)Math.sin(angle) * radius, (float)angle};
   }

   @Override
   public void close() {
      this.closeWithAnimation();
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null) {
         HudSettingsModule.getInstance().syncThemeColor();
         float guiCoordinateScale = coordinateScale();
         float screenWidth = this.width * guiCoordinateScale;
         float screenHeight = this.height * guiCoordinateScale;
         this.layoutScale = Math.min(screenWidth / (960.0F), screenHeight / (540.0F));
         this.centerX = screenWidth / 2.0F;
         this.centerY = screenHeight / 2.0F;
         float scaledMouseX = mouseX * guiCoordinateScale;
         float scaledMouseY = mouseY * guiCoordinateScale;
         this.visibilityAnimation.update();
         this.optionRevealAnimation.update();
         float visibility = this.visibilityAnimation.floatValue();
         if (this.closing && visibility <= (0.01F)) {
            client.execute(() -> {
               if (client.currentScreen == this) {
                  client.setScreen(null);
               }
            });
         } else {
            this.updateHover(scaledMouseX, scaledMouseY);
            Render2D.begin(context);
            HudRenderContext.setDrawContext(context);
            float openingScale = (0.92F) + (0.08F) * visibility;
            float innerRadius = (107.0F) * this.layoutScale * openingScale;
            float outerRadius = (144.0F) * this.layoutScale * openingScale;

            for (int categoryIndex = 0; categoryIndex < CATEGORIES.length; categoryIndex++) {
               TweenAnimation categoryAnimation = this.categoryAnimations[categoryIndex];
               categoryAnimation.update();
               categoryAnimation.animateTo(categoryIndex == this.selectedCategoryIndex ? 1.0 : 0.0, (0.16D), Easings.QUART_OUT);
               float selectedProgress = categoryAnimation.floatValue();
               float arcStart = (-90.0F) + categoryIndex * (45.0F) - (22.5F);
               GuiShapeRenderer.drawBlurredAnnularArc(
                  GuiShapeRenderer.BlurQuality.MIDDLE,
                  this.centerX,
                  this.centerY,
                  innerRadius,
                  outerRadius,
                  arcStart,
                  (45.0F),
                  (3.5F) * this.layoutScale,
                  (5.0F) * this.layoutScale,
                  GuiVisuals.lerpColor(HudPanelRenderer.panelTint(), GuiVisuals.white, (0.16F) * selectedProgress),
                  (0.8F) * (1.0F - (0.28F) * selectedProgress),
                  visibility
               );
               double categoryAngle = Math.toRadians((-90.0D) + categoryIndex * (45.0D));
               float iconX = this.centerX + (float)Math.cos(categoryAngle) * (innerRadius + outerRadius) / 2.0F;
               float iconY = this.centerY + (float)Math.sin(categoryAngle) * (innerRadius + outerRadius) / 2.0F;
               float iconSize = (16.0F) * this.layoutScale;
               Render2D.drawRoundedImage(
                  CATEGORIES[categoryIndex].iconTexture(),
                  iconX - iconSize / 2.0F,
                  iconY - iconSize / 2.0F,
                  iconSize,
                  iconSize,
                  0.0F,
                  GuiVisuals.multiplyAlpha(GuiVisuals.white, visibility * ((0.5F) + (0.5F) * selectedProgress))
               );
               if (EquippedCosmetics.equippedEntry(CATEGORIES[categoryIndex]) != null && selectedProgress < (0.99F)) {
                  Render2D.fillCircle(
                     this.centerX + (float)Math.cos(categoryAngle) * (outerRadius + (8.0F) * this.layoutScale),
                     this.centerY + (float)Math.sin(categoryAngle) * (outerRadius + (8.0F) * this.layoutScale),
                     (1.8F) * this.layoutScale,
                     GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, visibility * (1.0F - selectedProgress))
                  );
               }
            }

            this.drawOptions(visibility);
            this.drawCaption(screenWidth, screenHeight, visibility);
            Render2D.end();
            if (visibility > (0.01F)) {
               int previewHalfWidth = (int)((60.0F) * this.layoutScale / guiCoordinateScale);
               int previewHalfHeight = (int)((95.0F) * this.layoutScale / guiCoordinateScale);
               int previewCenterX = (int)(this.centerX / guiCoordinateScale);
               int previewCenterY = (int)(this.centerY / guiCoordinateScale);
               InventoryScreen.drawEntity(
                  context,
                  previewCenterX - previewHalfWidth,
                  previewCenterY - previewHalfHeight,
                  previewCenterX + previewHalfWidth,
                  previewCenterY + previewHalfHeight,
                  (int)((82.0F) * this.layoutScale / guiCoordinateScale),
                  (0.0625F),
                  mouseX,
                  mouseY,
                  client.player
               );
            }
         }
      }
   }

   @Override
   public boolean mouseClicked(Click click, boolean doubleClick) {
      if (this.closing) {
         return true;
      }

      CosmeticCategory category = CATEGORIES[this.selectedCategoryIndex];
      if (click.button() == 1) {
         EquippedCosmetics.unequip(category);
         return true;
      }

      if (click.button() == 0 && this.hoveredOptionIndex >= 0) {
         CosmeticEntry entry = this.options(category).get(this.categoryScrollOffsets[this.selectedCategoryIndex] + this.hoveredOptionIndex);
         if (entry == null) {
            EquippedCosmetics.unequip(category);
         } else {
            EquippedCosmetics.toggle(entry);
         }

         return true;
      } else {
         return super.mouseClicked(click, doubleClick);
      }
   }

   public CosmeticWheelScreen() {
      super(Text.literal(("маусклиент")));

      for (int categoryIndex = 0; categoryIndex < CATEGORIES.length; categoryIndex++) {
         this.categoryAnimations[categoryIndex] = new TweenAnimation();
      }

      for (int optionIndex = 0; optionIndex < 7; optionIndex++) {
         this.optionHoverAnimations[optionIndex] = new TweenAnimation();
      }
   }

   private static float coordinateScale() {
      return GuiScale.getWindowScale() / GuiScale.getBaseScale();
   }
}
