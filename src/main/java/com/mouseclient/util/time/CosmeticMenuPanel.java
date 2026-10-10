package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import com.mouseclient.render.CosmeticEntry;
import com.mouseclient.render.EquippedCosmetics;
import com.mouseclient.render.CosmeticCatalog;
import com.mouseclient.render.avatar.CosmeticAssetCache;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public final class CosmeticMenuPanel {

   private final HashMap<String, ScalarAnimation> hoverAnimations = new HashMap<>();
   private final HashMap<Integer, ScalarAnimation> itemHoverAnimations = new HashMap<>();
   private final HashMap<Integer, ScalarAnimation> itemEquippedAnimations = new HashMap<>();
   private final EnumMap<CosmeticCategory, ScalarAnimation> categoryHoverAnimations = new EnumMap<>(CosmeticCategory.class);
   private final EnumMap<CosmeticCategory, ScalarAnimation> categorySelectionAnimations = new EnumMap<>(CosmeticCategory.class);
   private final EnumMap<CosmeticCategory, List<CosmeticEntry>> filteredCategoryEntries = new EnumMap<>(CosmeticCategory.class);
   private final EnumMap<CosmeticCategory, String> categoryCounts = new EnumMap<>(CosmeticCategory.class);
   private final HashMap<Integer, CosmeticMenuPanel.CosmeticLabel> labelCache = new HashMap<>();
   private final ArrayList<CosmeticMenuPanel.CosmeticClickRegion> clickRegions = new ArrayList<>();
   private final CosmeticMenuPanel.AnimatedCosmeticText categoryTitle = new CosmeticMenuPanel.AnimatedCosmeticText();
   private final CosmeticMenuPanel.AnimatedCosmeticText catalogSubtitle = new CosmeticMenuPanel.AnimatedCosmeticText();
   private final CosmeticMenuPanel.AnimatedCosmeticText equippedTitle = new CosmeticMenuPanel.AnimatedCosmeticText();
   private String cachedSearchText;
   private int cachedCatalogRevision = -1;
   private float contentHeight;
   private boolean searchFocused;
   private String searchText = "";
   private float targetScroll;
   private float displayedScroll;
   private float maxScroll;
   private long lastFrameMillis;
   private long openedAt;
   private Runnable backAction;
   private float viewportTop;
   private float viewportBottom;
   private float catalogX;
   private float catalogY;
   private float scrollbarX;
   private float scale;
   private float cachedLabelScale;
   private float equipLabelWidth;
   private float unequipLabelWidth;
   private CosmeticCategory selectedCategory;
   private CosmeticCategory previousCategory;
   private long categoryChangedAt;
   private CosmeticEntry hoveredEntry;
   private CosmeticEntry previousHoveredEntry;
   private boolean draggingScrollbar;
   private float scrollbarGrabOffset;

   private float hoverProgress(String key, boolean hovered) {
      ScalarAnimation animation = this.hoverAnimations.computeIfAbsent(key, cacheKey -> new ScalarAnimation((140L)));
      animation.setTarget(hovered ? 1.0 : 0.0);
      return (float)animation.value();
   }

   public boolean keyPressed(int keyCode) {
      if (!this.searchFocused) {
         return false;
      }

      if (GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), 341) == 1 && keyCode == 86) {
         this.searchText = this.searchText + MinecraftClient.getInstance().keyboard.getClipboard().replaceAll(("[\\r\\n\\t]"), "");
         if (this.searchText.length() > 24) {
            this.searchText = this.searchText.substring(0, 24);
         }

         return true;
      } else if (keyCode == 259) {
         if (!this.searchText.isEmpty()) {
            this.searchText = this.searchText.substring(0, this.searchText.length() - 1);
         }

         this.targetScroll = 0.0F;
         return true;
      } else {
         if (keyCode != 257 && keyCode != 335 && keyCode != 256) {
            return false;
         }

         this.searchFocused = false;
         return true;
      }
   }

   public void open(Runnable backAction) {
      this.openedAt = System.currentTimeMillis();
      this.categoryChangedAt = this.openedAt;
      this.backAction = backAction;
      this.searchFocused = false;
      this.draggingScrollbar = false;
   }

   private void refreshFilteredEntries() {
      if (!this.searchText.equals(this.cachedSearchText) || this.cachedCatalogRevision != CosmeticCatalog.revision()) {
         boolean queryChanged = !this.searchText.equals(this.cachedSearchText);
         this.cachedSearchText = this.searchText;
         this.cachedCatalogRevision = CosmeticCatalog.revision();
         this.filteredCategoryEntries.clear();

         for (CosmeticCategory category : CosmeticCategory.values()) {
            java.util.List<com.mouseclient.render.CosmeticEntry> entries = this.filteredEntries(category);
            this.filteredCategoryEntries.put(category, entries);
            this.categoryCounts.put(category, String.valueOf(entries.size()));
         }

         if (queryChanged && !this.searchText.isBlank() && this.filteredCategoryEntries.get(this.selectedCategory).isEmpty()) {
            for (CosmeticCategory matchingCategory : CosmeticCategory.values()) {
               if (!this.filteredCategoryEntries.get(matchingCategory).isEmpty()) {
                  this.selectCategory(matchingCategory);
                  break;
               }
            }
         }
      }
   }

   private void drawEquippedSummary(CosmeticEntry entry, float x, float y, float mouseX, float mouseY) {
      float summaryWidth = (158.0F) * this.scale;
      float summaryHeight = (29.0F) * this.scale;
      float textProgress = this.equippedTitle
         .draw(
            GuiFont.SEMI_BOLD,
            entry == null ? ("Ничего не надето") : this.equippedLabel(entry),
            x + (30.0F) * this.scale,
            y + (14.5F) * this.scale,
            (6.5F) * this.scale,
            entry == null ? GuiVisuals.placeholderColor : GuiVisuals.white,
            (4.0F) * this.scale,
            (240L)
         );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         entry == null ? ("Выберите предмет") : ("Надето"),
         x + (30.0F) * this.scale,
         y + (5.5F) * this.scale,
         (5.5F) * this.scale,
         GuiVisuals.placeholderColor
      );
      float previewProgress = entry == null ? 0.0F : GuiVisuals.easeOutBack(textProgress);
      if (entry == null) {
         GuiVisuals.drawCenteredImage(
            this.selectedCategory.iconTexture(),
            x + (14.5F) * this.scale,
            y + (14.5F) * this.scale,
            (9.0F) * this.scale,
            (9.0F) * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.2F))
         );
      } else {
         CosmeticAssetCache.PreviewAtlasCell previewCell = CosmeticAssetCache.previewCell(entry);
         float previewSize = (18.0F) * this.scale * Math.max(0.0F, previewProgress);
         if (previewCell != null && previewSize > (0.5F)) {
            Render2D.drawImageRegion(
               previewCell.atlasTexture(),
               x + (14.5F) * this.scale - previewSize / 2.0F,
               y + (14.5F) * this.scale - previewSize / 2.0F,
               previewSize,
               previewSize,
               0.0F,
               previewCell.minU(),
               previewCell.minV(),
               previewCell.maxU(),
               previewCell.maxV(),
               GuiVisuals.white
            );
         }

         float removeHover = this.hoverProgress(
            ("cosTakeOff"),
            GuiVisuals.containsPoint(
               mouseX,
               mouseY,
               x + summaryWidth - (22.0F) * this.scale,
               y + (4.5F) * this.scale,
               (18.0F) * this.scale,
               (20.0F) * this.scale
            )
         );
         GuiVisuals.drawCenteredIcon(
            ("c"),
            x + summaryWidth - (13.0F) * this.scale,
            y + (14.5F) * this.scale,
            (5.5F) * this.scale,
            GuiVisuals.lerpColor(GuiVisuals.menuDescriptionColor, GuiVisuals.clearCosmeticHoverColor, removeHover)
         );
         this.clickRegions
            .add(
               new CosmeticMenuPanel.CosmeticClickRegion(
                  x + summaryWidth - (22.0F) * this.scale,
                  y + (4.5F) * this.scale,
                  (18.0F) * this.scale,
                  (20.0F) * this.scale,
                  false,
                  () -> EquippedCosmetics.unequip(this.selectedCategory)
               )
            );
      }
   }

   private void drawHeading(float x, float y, float mouseX, float mouseY) {
      float headingProgress = this.entranceProgress(0);
      Render2D.pushAlpha(headingProgress);
      float animatedX = x - (1.0F - headingProgress) * (14.0F) * this.scale;
      float backHover = this.hoverProgress(
         ("cosBack"),
         GuiVisuals.containsPoint(
            mouseX,
            mouseY,
            animatedX - (4.0F) * this.scale,
            y + (105.0F) * this.scale,
            (18.0F) * this.scale,
            (18.0F) * this.scale
         )
      );
      GuiVisuals.drawCenteredImage(
         ("assets/mouseclient/textures/gui/mainmenu/icons/back.png"),
         animatedX + (4.75F) * this.scale - backHover * (1.5F) * this.scale,
         y + (113.75F) * this.scale,
         (9.5F) * this.scale,
         (9.5F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.menuDescriptionColor, GuiVisuals.white, backHover)
      );
      this.clickRegions
         .add(
            new CosmeticMenuPanel.CosmeticClickRegion(
               animatedX - (4.0F) * this.scale,
               y + (105.0F) * this.scale,
               (18.0F) * this.scale,
               (18.0F) * this.scale,
               false,
               this.backAction
            )
         );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("маусклиент  /  Главная  /  Косметика"),
         animatedX + (15.5F) * this.scale,
         y + (110.5F) * this.scale,
         (6.5F) * this.scale,
         GuiVisuals.menuDescriptionColor
      );
      GuiTextRenderer.draw(GuiFont.SEMI, ("Косметика."), animatedX, y + (124.5F) * this.scale, (20.0F) * this.scale, GuiVisuals.white);
      GuiTextRenderer.draw(GuiFont.SEMI, ("Собирайте образ - предметы применяются сразу"), animatedX, y + (159.5F) * this.scale, (7.5F) * this.scale, GuiVisuals.menuDescriptionColor);
      Render2D.popAlpha();
   }

   private float entranceProgress(int sectionIndex) {
      return GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.openedAt, (40L) + sectionIndex * (55L), (420L)));
   }

   private void drawSectionHeading(String label, float x, float y, float mouseX, float mouseY, boolean showClearButton) {
      GuiTextRenderer.draw(GuiFont.SEMI, label, x, y, (6.0F) * this.scale, GuiVisuals.placeholderColor);
      float dividerEnd = x + (226.5F) * this.scale;
      if (showClearButton) {
         String clearLabel = ("Снять всё");
         float clearLabelWidth = GuiTextRenderer.getWidth(GuiFont.SEMI, clearLabel, (6.0F) * this.scale);
         float clearHover = this.hoverProgress(
            ("cosClear"),
            GuiVisuals.containsPoint(
               mouseX,
               mouseY,
               x + (226.5F) * this.scale - clearLabelWidth - (9.0F) * this.scale,
               y - (4.0F) * this.scale,
               clearLabelWidth + (9.0F) * this.scale,
               (14.0F) * this.scale
            )
         );
         GuiVisuals.drawCenteredIcon(
            ("c"),
            x + (226.5F) * this.scale - clearLabelWidth - (5.5F) * this.scale,
            y + (3.0F) * this.scale,
            (5.0F) * this.scale,
            GuiVisuals.lerpColor(GuiVisuals.menuDescriptionColor, GuiVisuals.white, clearHover)
         );
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            clearLabel,
            x + (226.5F) * this.scale - clearLabelWidth,
            y,
            (6.0F) * this.scale,
            GuiVisuals.lerpColor(GuiVisuals.menuDescriptionColor, GuiVisuals.white, clearHover)
         );
         this.clickRegions
            .add(
               new CosmeticMenuPanel.CosmeticClickRegion(
                  x + (226.5F) * this.scale - clearLabelWidth - (9.0F) * this.scale,
                  y - (4.0F) * this.scale,
                  clearLabelWidth + (9.0F) * this.scale,
                  (14.0F) * this.scale,
                  false,
                  EquippedCosmetics::unequipAll
               )
            );
         dividerEnd -= clearLabelWidth + (11.0F) * this.scale;
      }

      float dividerStart = x + GuiTextRenderer.getWidth(GuiFont.SEMI, label, (6.0F) * this.scale) + (6.0F) * this.scale;
      if (dividerEnd - dividerStart > 2.0F * this.scale) {
         Render2D.fillRoundedRectangle(
            dividerStart,
            y + (3.0F) * this.scale,
            dividerEnd - dividerStart,
            (0.5F) * this.scale,
            (0.25F) * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.12F))
         );
      }
   }

   public boolean mouseDragged(float mouseX, float mouseY) {
      if (!this.draggingScrollbar) {
         return false;
      }

      float viewportHeight = this.viewportBottom - this.viewportTop;
      float unscaledViewportHeight = viewportHeight / this.scale;
      float thumbHeight = Math.max((24.0F) * this.scale, viewportHeight * (unscaledViewportHeight / Math.max(unscaledViewportHeight, this.contentHeight)));
      this.targetScroll = this.displayedScroll = clamp((mouseY - this.scrollbarGrabOffset - this.viewportTop) / Math.max(1.0F, viewportHeight - thumbHeight), 0.0F, 1.0F)
         * this.maxScroll;
      return true;
   }

   private void selectCategory(CosmeticCategory category) {
      if (category != this.selectedCategory) {
         this.previousCategory = this.selectedCategory;
         this.selectedCategory = category;
         this.targetScroll = this.displayedScroll = 0.0F;
         this.categoryChangedAt = System.currentTimeMillis();
      }
   }

   private static float smoothScroll(float current, float target, float deltaSeconds) {
      current += (target - current) * (1.0F - (float)Math.exp(-deltaSeconds * (22.0F)));
      return Math.abs(target - current) < (0.1F) ? target : current;
   }

   private void drawCosmeticCard(CosmeticEntry entry, float x, float y, float mouseX, float mouseY, boolean cosmeticsEnabled, int entranceIndex) {
      float edgeAlpha = Math.min(
         clamp((y + (80.0F) * this.scale - this.viewportTop) / ((14.0F) * this.scale), 0.0F, 1.0F),
         clamp((this.viewportBottom - y) / ((14.0F) * this.scale), 0.0F, 1.0F)
      );
      if (!(edgeAlpha <= (0.01F))) {
         boolean betaUnavailable = entry.beta() && !cosmeticsEnabled;
         boolean equipped = EquippedCosmetics.isEquipped(entry);
         boolean hovered = !this.draggingScrollbar
            && GuiVisuals.containsPoint(mouseX, mouseY, x, y, (56.0F) * this.scale, (80.0F) * this.scale)
            && mouseY >= this.viewportTop
            && mouseY <= this.viewportBottom;
         if (hovered) {
            this.hoveredEntry = entry;
         }

         float hoverAlpha = this.itemProgress(this.itemHoverAnimations, entry.id(), hovered && !betaUnavailable);
         float equippedAlpha = this.itemProgress(this.itemEquippedAnimations, entry.id(), equipped);
         float cardAlpha = GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.categoryChangedAt, Math.min(Math.max(entranceIndex, 0), 27) * (9L), (240L))) * edgeAlpha;
         boolean partialAlpha = cardAlpha < (0.999F);
         if (partialAlpha) {
            Render2D.pushAlpha(cardAlpha);
         }

         float previewSize = (38.0F) * this.scale;
         float previewX = x + ((56.0F) * this.scale - previewSize) / 2.0F;
         float previewY = y + (5.0F) * this.scale;
         CosmeticAssetCache.PreviewAtlasCell previewCell = CosmeticAssetCache.previewCell(entry);
         if (previewCell != null) {
            Render2D.drawImageRegion(
               previewCell.atlasTexture(),
               previewX,
               previewY,
               previewSize,
               previewSize,
               0.0F,
               previewCell.minU(),
               previewCell.minV(),
               previewCell.maxU(),
               previewCell.maxV(),
               GuiVisuals.multiplyAlpha(GuiVisuals.white, betaUnavailable ? (0.5F) : 1.0F)
            );
         } else {
            Render2D.fillRoundedRectangle(
               previewX + (8.0F) * this.scale,
               previewY + (8.0F) * this.scale,
               previewSize - (16.0F) * this.scale,
               previewSize - (16.0F) * this.scale,
               (5.0F) * this.scale,
               GuiVisuals.multiplyAlpha(
                  GuiVisuals.white,
                  (0.03F)
                     + (0.025F) * (float)Math.sin((float)(System.currentTimeMillis() % (1400L)) / (1400.0F) * (3.141592653589793D) * (2.0D))
               )
            );
         }

         CosmeticMenuPanel.CosmeticLabel label = this.cardLabel(entry);
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            label.text(),
            x + ((56.0F) * this.scale - label.width()) / 2.0F,
            y + (47.0F) * this.scale,
            (5.5F) * this.scale,
            betaUnavailable ? GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.42F)) : GuiVisuals.white
         );
         float buttonX = x + (4.0F) * this.scale;
         float buttonY = y + (62.0F) * this.scale;
         float buttonWidth = (48.0F) * this.scale;
         float buttonHeight = (14.0F) * this.scale;
         if (betaUnavailable) {
            GuiVisuals.drawOutlinedPanel(
               buttonX,
               buttonY,
               buttonWidth,
               buttonHeight,
               (5.0F) * this.scale,
               GuiVisuals.multiplyAlpha(GuiVisuals.unavailableCosmeticColor, (0.07F)),
               GuiVisuals.multiplyAlpha(GuiVisuals.unavailableCosmeticColor, (0.2F))
            );
            float betaLabelWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("Бета"), (5.5F) * this.scale);
            float betaIconX = x + ((56.0F) * this.scale - betaLabelWidth - (8.0F) * this.scale) / 2.0F;
            GuiVisuals.drawCenteredIcon(
               ("Z"),
               betaIconX + (2.5F) * this.scale,
               buttonY + (7.0F) * this.scale,
               (5.0F) * this.scale,
               GuiVisuals.unavailableCosmeticColor
            );
            GuiTextRenderer.draw(
               GuiFont.SEMI_BOLD,
               ("Бета"),
               betaIconX + (8.0F) * this.scale,
               buttonY + (4.5F) * this.scale,
               (5.5F) * this.scale,
               GuiVisuals.unavailableCosmeticColor
            );
         } else {
            GuiVisuals.drawOutlinedPanel(
               buttonX,
               buttonY,
               buttonWidth,
               buttonHeight,
               (5.0F) * this.scale,
               GuiVisuals.lerpColor(GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.031F) + (0.05F) * hoverAlpha), GuiVisuals.accentColor, equippedAlpha),
               GuiVisuals.lerpColor(GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.078F) + (0.09F) * hoverAlpha), GuiVisuals.accentOutlineColor, equippedAlpha)
            );
            GuiTextRenderer.draw(
               GuiFont.SEMI_BOLD,
               equipped ? ("Снять") : ("Надеть"),
               x + ((56.0F) * this.scale - (equipped ? this.unequipLabelWidth : this.equipLabelWidth)) / 2.0F,
               buttonY + (4.5F) * this.scale,
               (5.5F) * this.scale,
               GuiVisuals.lerpColor(GuiVisuals.lerpColor(GuiVisuals.menuRowTextColor, GuiVisuals.white, hoverAlpha), GuiVisuals.buttonTextColor, equippedAlpha)
            );
            this.clickRegions
               .add(
                  new CosmeticMenuPanel.CosmeticClickRegion(
                     x, y, (56.0F) * this.scale, (80.0F) * this.scale, true, () -> EquippedCosmetics.toggle(entry)
                  )
               );
         }

         if (partialAlpha) {
            Render2D.popAlpha();
         }
      }
   }

   private void drawCategoryRow(CosmeticCategory category, float x, float y, float mouseX, float mouseY) {
      float hoverAlpha = this.categoryProgress(
         this.categoryHoverAnimations, category, GuiVisuals.containsPoint(mouseX, mouseY, x, y, (226.5F) * this.scale, (19.5F) * this.scale)
      );
      float selectedAlpha = this.categoryProgress(this.categorySelectionAnimations, category, category == this.selectedCategory);
      GuiVisuals.drawOutlinedPanel(
         x,
         y,
         (226.5F) * this.scale,
         (19.5F) * this.scale,
         (6.0F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.031F) + (0.04F) * hoverAlpha), GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.16F)), selectedAlpha),
         GuiVisuals.lerpColor(GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.078F) + (0.08F) * hoverAlpha), GuiVisuals.multiplyAlpha(GuiVisuals.accentOutlineColor, (0.6F)), selectedAlpha)
      );
      GuiVisuals.drawCenteredImage(
         category.iconTexture(),
         x + (11.0F) * this.scale,
         y + (9.75F) * this.scale,
         (7.0F) * this.scale,
         (7.0F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.lerpColor(GuiVisuals.menuDescriptionColor, GuiVisuals.white, hoverAlpha), GuiVisuals.accentLightColor, selectedAlpha)
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         category.displayName(),
         x + (20.0F) * this.scale,
         y + (6.25F) * this.scale,
         (7.0F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.menuRowTextColor, GuiVisuals.white, Math.max(hoverAlpha, selectedAlpha))
      );
      String countText = this.categoryCounts.getOrDefault(category, ("0"));
      float countWidth = GuiTextRenderer.getWidth(GuiFont.SEMI, countText, (6.0F) * this.scale);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         countText,
         x + (218.5F) * this.scale - countWidth,
         y + (6.75F) * this.scale,
         (6.0F) * this.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.34F) + (0.3F) * selectedAlpha)
      );
      CosmeticEntry entry = EquippedCosmetics.equippedEntry(category);
      if (entry != null) {
         float equippedPreviewX = x + (218.5F) * this.scale - countWidth - (19.0F) * this.scale;
         CosmeticAssetCache.PreviewAtlasCell previewCell = CosmeticAssetCache.previewCell(entry);
         if (previewCell != null) {
            Render2D.drawImageRegion(
               previewCell.atlasTexture(),
               equippedPreviewX + 1.0F * this.scale,
               y + (4.0F) * this.scale,
               (11.5F) * this.scale,
               (11.5F) * this.scale,
               0.0F,
               previewCell.minU(),
               previewCell.minV(),
               previewCell.maxU(),
               previewCell.maxV(),
               GuiVisuals.white
            );
         }
      }

      this.clickRegions
         .add(
            new CosmeticMenuPanel.CosmeticClickRegion(x, y, (226.5F) * this.scale, (19.5F) * this.scale, false, () -> this.selectCategory(category))
         );
   }

   private float categoryProgress(EnumMap<CosmeticCategory, ScalarAnimation> animations, CosmeticCategory category, boolean active) {
      ScalarAnimation animation = animations.computeIfAbsent(category, categoryKey -> new ScalarAnimation((160L)));
      animation.setTarget(active ? 1.0 : 0.0);
      return (float)animation.value();
   }

   private String equippedLabel(CosmeticEntry entry) {
      CosmeticMenuPanel.CosmeticLabel cachedLabel = this.labelCache.get(-entry.id() - 1);
      if (cachedLabel != null) {
         return cachedLabel.text();
      }

      String labelText = entry.name();
      float availableWidth = (100.0F) * this.scale;
      float fontSize = (6.5F) * this.scale;

      while (GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, labelText, fontSize) > availableWidth && labelText.length() > 3) {
         labelText = labelText.substring(0, labelText.length() - 2) + "…";
      }

      this.labelCache.put(-entry.id() - 1, new CosmeticMenuPanel.CosmeticLabel(labelText, 0.0F));
      return labelText;
   }

   private CosmeticMenuPanel.CosmeticLabel cardLabel(CosmeticEntry entry) {
      CosmeticMenuPanel.CosmeticLabel cachedLabel = this.labelCache.get(entry.id());
      if (cachedLabel != null) {
         return cachedLabel;
      }

      String labelText = entry.name().replaceFirst(("^[^—]+ — "), "");
      float availableWidth = (51.0F) * this.scale;
      float fontSize = (5.5F) * this.scale;

      while (GuiTextRenderer.getWidth(GuiFont.SEMI, labelText, fontSize) > availableWidth && labelText.length() > 3) {
         labelText = labelText.substring(0, labelText.length() - 2) + "…";
      }

      CosmeticMenuPanel.CosmeticLabel measuredLabel = new CosmeticMenuPanel.CosmeticLabel(labelText, GuiTextRenderer.getWidth(GuiFont.SEMI, labelText, fontSize));
      this.labelCache.put(entry.id(), measuredLabel);
      return measuredLabel;
   }

   public boolean mouseScrolled(float mouseX, float mouseY, float scrollAmount) {
      if (!GuiVisuals.containsPoint(mouseX, mouseY, this.catalogX, this.catalogY, (592.0F) * this.scale, (396.0F) * this.scale)) {
         return false;
      }

      this.targetScroll = clamp(this.targetScroll - scrollAmount * (87.0F), 0.0F, this.maxScroll);
      return true;
   }

   private void drawCategoryList(float x, float y, float mouseX, float mouseY) {
      float listProgress = this.entranceProgress(2);
      Render2D.pushAlpha(listProgress);
      float animatedX = x - (1.0F - listProgress) * (14.0F) * this.scale;
      float rowY = y + (214.0F) * this.scale;
      this.drawSectionHeading(("Образ"), animatedX, rowY, mouseX, mouseY, true);
      rowY += (13.0F) * this.scale;
      boolean itemSectionStarted = false;

      for (CosmeticCategory category : CosmeticCategory.values()) {
         if (category.isItemCategory() && !itemSectionStarted) {
            itemSectionStarted = true;
            rowY += (5.0F) * this.scale;
            this.drawSectionHeading(("Оружие"), animatedX, rowY, mouseX, mouseY, false);
            rowY += (13.0F) * this.scale;
         }

         this.drawCategoryRow(category, animatedX, rowY, mouseX, mouseY);
         rowY += (22.5F) * this.scale;
      }

      Render2D.popAlpha();
   }

   public boolean charTyped(int codepoint) {
      if (this.searchFocused && codepoint >= 32 && codepoint <= (65535)) {
         if (this.searchText.length() < 24) {
            this.searchText = this.searchText + (char)codepoint;
         }

         this.targetScroll = 0.0F;
         return true;
      } else {
         return false;
      }
   }

   public void render(DrawContext context, float panelOffsetX, float guiScale, float panelOffsetY, float windowWidth, float windowHeight, float mouseX, float mouseY) {
      this.scale = guiScale;
      float deltaSeconds = Math.min((0.05F), (float)(System.nanoTime() / (1000000L) - this.lastFrameMillis) / (1000.0F));
      this.lastFrameMillis = System.nanoTime() / (1000000L);
      this.clickRegions.clear();
      this.previousHoveredEntry = this.hoveredEntry;
      this.hoveredEntry = null;
      if (this.cachedLabelScale != guiScale) {
         this.labelCache.clear();
         this.cachedLabelScale = guiScale;
         this.equipLabelWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("Надеть"), (5.5F) * guiScale);
         this.unequipLabelWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("Снять"), (5.5F) * guiScale);
      }

      float sidebarX = panelOffsetX + (51.5F) * guiScale;
      this.refreshFilteredEntries();
      this.drawHeading(sidebarX, panelOffsetY, mouseX, mouseY);
      this.drawSearchField(sidebarX, panelOffsetY, mouseX, mouseY);
      this.drawCategoryList(sidebarX, panelOffsetY, mouseX, mouseY);
      this.drawCatalog(context, panelOffsetX, panelOffsetY, windowWidth, windowHeight, mouseX, mouseY, deltaSeconds);
   }

   public void clearInputState() {
      this.searchFocused = false;
      this.draggingScrollbar = false;
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private float itemProgress(HashMap<Integer, ScalarAnimation> animations, int itemId, boolean active) {
      ScalarAnimation animation = animations.computeIfAbsent(itemId, cosmeticId -> new ScalarAnimation((140L)));
      animation.setTarget(active ? 1.0 : 0.0);
      return (float)animation.value();
   }

   public boolean mouseClicked(float mouseX, float mouseY) {
      boolean wasSearchFocused = this.searchFocused;
      this.searchFocused = false;
      if (this.maxScroll > (0.5F)
         && GuiVisuals.containsPoint(
            mouseX,
            mouseY,
            this.scrollbarX - (4.0F) * this.scale,
            this.viewportTop,
            (10.0F) * this.scale,
            this.viewportBottom - this.viewportTop
         )) {
         float viewportHeight = this.viewportBottom - this.viewportTop;
         float unscaledViewportHeight = viewportHeight / this.scale;
         float thumbHeight = Math.max((24.0F) * this.scale, viewportHeight * (unscaledViewportHeight / Math.max(unscaledViewportHeight, this.contentHeight)));
         float thumbY = this.viewportTop + this.displayedScroll / this.maxScroll * (viewportHeight - thumbHeight);
         this.scrollbarGrabOffset = mouseY >= thumbY && mouseY <= thumbY + thumbHeight ? mouseY - thumbY : thumbHeight / 2.0F;
         this.draggingScrollbar = true;
         this.mouseDragged(mouseX, mouseY);
         return true;
      }

      for (int var4 = this.clickRegions.size() - 1; var4 >= 0; var4--) {
         CosmeticMenuPanel.CosmeticClickRegion region = this.clickRegions.get(var4);
         if ((!region.clipped() || !(mouseY < this.viewportTop) && !(mouseY > this.viewportBottom))
            && GuiVisuals.containsPoint(mouseX, mouseY, region.x(), region.y(), region.width(), region.height())) {
            region.action().run();
            return true;
         }
      }

      return wasSearchFocused;
   }

   private static String itemCountWord(int count) {
      int lastTwoDigits = count % 100;
      int lastDigit = count % 10;
      return lastTwoDigits >= 11 && lastTwoDigits <= 14 ? ("предметов") : (lastDigit == 1 ? ("предмет") : (lastDigit >= 2 && lastDigit <= 4 ? ("предмета") : ("предметов")));
   }

   private void drawCatalog(DrawContext context, float panelOffsetX, float panelOffsetY, float windowWidth, float windowHeight, float mouseX, float mouseY, float deltaSeconds) {
      float catalogProgress = this.entranceProgress(3);
      Render2D.pushAlpha(catalogProgress);
      this.catalogX = panelOffsetX + (344.0F) * this.scale + (1.0F - catalogProgress) * (14.0F) * this.scale;
      this.catalogY = panelOffsetY + (92.0F) * this.scale;
      float panelWidth = (592.0F) * this.scale;
      float panelHeight = (396.0F) * this.scale;
      float headerBottom = this.catalogY + (54.0F) * this.scale;
      float contentX = this.catalogX + (16.0F) * this.scale;
      float headerY = this.catalogY + (15.0F) * this.scale;
      java.util.List<com.mouseclient.render.CosmeticEntry> entries = this.filteredCategoryEntries.getOrDefault(this.selectedCategory, List.of());
      CosmeticEntry entry = EquippedCosmetics.equippedEntry(this.selectedCategory);
      float categoryTransition = GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.categoryChangedAt, 0L, (280L)));
      if (categoryTransition < (0.999F) && this.previousCategory != this.selectedCategory) {
         GuiVisuals.drawCenteredImage(
            this.previousCategory.iconTexture(),
            contentX + (13.5F) * this.scale,
            headerY + (13.5F) * this.scale,
            (15.0F) * this.scale * (1.0F - categoryTransition * (0.4F)),
            (15.0F) * this.scale * (1.0F - categoryTransition * (0.4F)),
            GuiVisuals.multiplyAlpha(GuiVisuals.accentLightColor, 1.0F - categoryTransition)
         );
      }

      GuiVisuals.drawCenteredImage(
         this.selectedCategory.iconTexture(),
         contentX + (13.5F) * this.scale,
         headerY + (13.5F) * this.scale,
         (15.0F) * this.scale * ((0.6F) + (0.4F) * categoryTransition),
         (15.0F) * this.scale * ((0.6F) + (0.4F) * categoryTransition),
         GuiVisuals.multiplyAlpha(GuiVisuals.accentLightColor, categoryTransition)
      );
      this.categoryTitle
         .draw(
            GuiFont.SEMI,
            this.selectedCategory.displayName(),
            contentX + (36.0F) * this.scale,
            headerY + (0.5F) * this.scale,
            (13.0F) * this.scale,
            GuiVisuals.white,
            (7.0F) * this.scale,
            (280L)
         );
      String subtitle = this.previousHoveredEntry != null
         ? this.previousHoveredEntry.name()
         : this.categoryCounts.getOrDefault(this.selectedCategory, ("0")) + " " + itemCountWord(entries.size()) + (this.searchText.isBlank() ? "" : (" по запросу"));
      this.catalogSubtitle
         .draw(
            GuiFont.SEMI,
            subtitle,
            contentX + (36.0F) * this.scale,
            headerY + (17.0F) * this.scale,
            (6.5F) * this.scale,
            this.previousHoveredEntry != null ? GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.85F)) : GuiVisuals.menuDescriptionColor,
            (3.0F) * this.scale,
            (170L)
         );
      this.drawEquippedSummary(
         entry, this.catalogX + panelWidth - (16.0F) * this.scale - (158.0F) * this.scale, headerY - 1.0F * this.scale, mouseX, mouseY
      );
      this.viewportTop = headerBottom + (8.0F) * this.scale;
      this.viewportBottom = this.catalogY + panelHeight - (10.0F) * this.scale;
      float unscaledViewportHeight = (this.viewportBottom - this.viewportTop) / this.scale;
      float rowHeight = (87.0F);
      int rowCount = (entries.size() + 9 - 1) / 9;
      this.contentHeight = Math.max(0.0F, rowCount * rowHeight - (7.0F) + (8.0F));
      this.maxScroll = Math.max(0.0F, this.contentHeight - unscaledViewportHeight);
      this.targetScroll = clamp(this.targetScroll, 0.0F, this.maxScroll);
      this.displayedScroll = this.draggingScrollbar ? this.targetScroll : smoothScroll(this.displayedScroll, this.targetScroll, deltaSeconds);
      Render2D.pushScissor(
         context, contentX - (4.0F) * this.scale, this.viewportTop, (568.0F) * this.scale, this.viewportBottom - this.viewportTop
      );
      int firstVisibleRow = Math.max(0, (int)Math.floor((this.displayedScroll - (8.0F)) / rowHeight));
      int lastVisibleRow = Math.min(rowCount - 1, (int)Math.ceil((this.displayedScroll + unscaledViewportHeight) / rowHeight));
      boolean cosmeticsEnabled = CosmeticAvailability.isEnabled();

      for (int row = firstVisibleRow; row <= lastVisibleRow; row++) {
         for (int column = 0; column < 9; column++) {
            int entryIndex = row * 9 + column;
            if (entryIndex >= entries.size()) {
               break;
            }

            this.drawCosmeticCard(
               (CosmeticEntry)entries.get(entryIndex),
               contentX + column * (63.0F) * this.scale,
               this.viewportTop + ((4.0F) + row * rowHeight - this.displayedScroll) * this.scale,
               mouseX,
               mouseY,
               cosmeticsEnabled,
               entryIndex - firstVisibleRow * 9
            );
         }
      }

      if (entries.isEmpty()) {
         String emptyMessage = this.searchText.isBlank() ? ("Здесь пока пусто") : ("Ничего не найдено");
         float centerX = this.catalogX + panelWidth / 2.0F;
         GuiVisuals.drawCenteredImage(
            ("assets/mouseclient/textures/gui/mainmenu/icons/search.png"),
            centerX,
            this.viewportTop + (120.0F) * this.scale,
            (14.0F) * this.scale,
            (14.0F) * this.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.22F))
         );
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            emptyMessage,
            centerX - GuiTextRenderer.getWidth(GuiFont.SEMI, emptyMessage, (7.5F) * this.scale) / 2.0F,
            this.viewportTop + (137.0F) * this.scale,
            (7.5F) * this.scale,
            GuiVisuals.placeholderColor
         );
      }

      Render2D.popScissor(context);
      this.scrollbarX = this.catalogX + panelWidth - (9.0F) * this.scale;
      if (this.maxScroll > (0.5F)) {
         float viewportHeight = this.viewportBottom - this.viewportTop;
         float thumbHeight = Math.max((24.0F) * this.scale, viewportHeight * (unscaledViewportHeight / Math.max(unscaledViewportHeight, this.contentHeight)));
         float thumbY = this.viewportTop + this.displayedScroll / this.maxScroll * (viewportHeight - thumbHeight);
         float scrollbarHover = this.hoverProgress(
            ("cosThumb"),
            this.draggingScrollbar
               || GuiVisuals.containsPoint(
                  mouseX, mouseY, this.scrollbarX - (3.0F) * this.scale, this.viewportTop, (8.0F) * this.scale, viewportHeight
               )
         );
         Render2D.fillRoundedRectangle(
            this.scrollbarX, this.viewportTop, 2.0F * this.scale, viewportHeight, 1.0F * this.scale, GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.05F))
         );
         Render2D.fillRoundedRectangle(
            this.scrollbarX - scrollbarHover * (0.5F) * this.scale,
            thumbY,
            (2.0F + scrollbarHover) * this.scale,
            thumbHeight,
            (1.5F) * this.scale,
            GuiVisuals.lerpColor(GuiVisuals.listDividerColor, GuiVisuals.multiplyAlpha(GuiVisuals.accentOutlineColor, (0.9F)), scrollbarHover)
         );
      }

      Render2D.popAlpha();
   }

   private List<CosmeticEntry> filteredEntries(CosmeticCategory category) {
      java.util.List<com.mouseclient.render.CosmeticEntry> categoryEntries = CosmeticCatalog.inCategory(category);
      if (this.searchText.isBlank()) {
         return categoryEntries;
      }

      String lowercaseQuery = this.searchText.toLowerCase(Locale.ROOT);
      ArrayList matchingEntries = new ArrayList();

      for (CosmeticEntry entry : categoryEntries) {
         if (entry.name().toLowerCase(Locale.ROOT).contains(lowercaseQuery)) {
            matchingEntries.add(entry);
         }
      }

      return matchingEntries;
   }

   public boolean mouseReleased() {
      boolean wasDragging = this.draggingScrollbar;
      this.draggingScrollbar = false;
      return wasDragging;
   }

   private void drawSearchField(float x, float y, float mouseX, float mouseY) {
      float fieldProgress = this.entranceProgress(1);
      Render2D.pushAlpha(fieldProgress);
      float animatedX = x - (1.0F - fieldProgress) * (14.0F) * this.scale;
      float fieldY = y + (184.5F) * this.scale;
      float focusAlpha = this.hoverProgress(("cosFieldFocus"), this.searchFocused);
      Render2D.fillRoundedRectangle(
         animatedX,
         fieldY,
         (226.5F) * this.scale,
         (19.5F) * this.scale,
         (6.0F) * this.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.031F))
      );
      Render2D.drawOutline(
         animatedX,
         fieldY,
         (226.5F) * this.scale,
         (19.5F) * this.scale,
         (6.0F) * this.scale,
         (0.5F),
         GuiVisuals.lerpColor(GuiVisuals.menuFieldOutlineColor, GuiVisuals.multiplyAlpha(GuiVisuals.accentOutlineColor, (0.8F)), focusAlpha)
      );
      GuiVisuals.drawCenteredImage(
         ("assets/mouseclient/textures/gui/mainmenu/icons/search.png"),
         animatedX + (8.5F) * this.scale,
         fieldY + (9.75F) * this.scale,
         (6.5F) * this.scale,
         (6.5F) * this.scale,
         GuiVisuals.lerpColor(GuiVisuals.placeholderColor, GuiVisuals.multiplyAlpha(GuiVisuals.accentOutlineColor, (0.95F)), focusAlpha)
      );
      String query = this.searchText;
      boolean showPlaceholder = query.isEmpty() && !this.searchFocused;
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         showPlaceholder ? ("Найти предмет во всех категориях...") : query + (this.searchFocused && System.currentTimeMillis() % (1000L) < (500L) ? ("|") : ""),
         animatedX + (19.0F) * this.scale,
         fieldY + (6.25F) * this.scale,
         (7.0F) * this.scale,
         query.isEmpty() ? GuiVisuals.placeholderColor : GuiVisuals.white
      );
      this.clickRegions
         .add(
            new CosmeticMenuPanel.CosmeticClickRegion(animatedX, fieldY, (226.5F) * this.scale, (19.5F) * this.scale, false, () -> this.searchFocused = true)
         );
      float clearVisibility = this.hoverProgress(("cosSearchClearShown"), !query.isEmpty());
      if (clearVisibility > (0.01F)) {
         float clearHover = this.hoverProgress(
            ("cosSearchClear"),
            GuiVisuals.containsPoint(mouseX, mouseY, animatedX + (207.0F) * this.scale, fieldY, (19.5F) * this.scale, (19.5F) * this.scale)
         );
         GuiVisuals.drawCenteredIcon(
            ("c"),
            animatedX + (216.75F) * this.scale,
            fieldY + (9.75F) * this.scale,
            (5.5F) * this.scale * clearVisibility,
            GuiVisuals.multiplyAlpha(GuiVisuals.lerpColor(GuiVisuals.menuDescriptionColor, GuiVisuals.white, clearHover), clearVisibility)
         );
         if (!query.isEmpty()) {
            this.clickRegions
               .add(
                  new CosmeticMenuPanel.CosmeticClickRegion(
                     animatedX + (207.0F) * this.scale, fieldY, (19.5F) * this.scale, (19.5F) * this.scale, false, () -> {
                        this.searchText = "";
                        this.targetScroll = 0.0F;
                     }
                  )
               );
         }
      }

      Render2D.popAlpha();
   }

   public CosmeticMenuPanel() {
      this.lastFrameMillis = System.nanoTime() / (1000000L);
      this.backAction = () -> {};
      this.scale = 1.0F;
      this.cachedLabelScale = (-1.0F);
      this.selectedCategory = CosmeticCategory.HAT;
      this.previousCategory = CosmeticCategory.HAT;
   }

   private record CosmeticLabel(String text, float width) {
   }

   private record CosmeticClickRegion(float x, float y, float width, float height, boolean clipped, Runnable action) {
      public float width() {
         return this.width;
      }

      public float height() {
         return this.height;
      }
   }

   private static final class AnimatedCosmeticText {
      private String currentText;
      private String previousText;
      private long changedAt;

      private float draw(GuiFont font, String text, float x, float y, float size, int color, float slideDistance, long durationMillis) {
         if (!text.equals(this.currentText)) {
            this.previousText = this.currentText;
            this.currentText = text;
            this.changedAt = System.currentTimeMillis();
         }

         float transitionProgress = GuiVisuals.easeOutQuad(GuiVisuals.elapsedProgress(this.changedAt, 0L, durationMillis));
         if (transitionProgress < (0.999F) && this.previousText != null) {
            Render2D.pushAlpha(1.0F - transitionProgress);
            GuiTextRenderer.draw(font, this.previousText, x, y - slideDistance * transitionProgress, size, color);
            Render2D.popAlpha();
         }

         Render2D.pushAlpha(transitionProgress);
         GuiTextRenderer.draw(font, this.currentText, x, y + slideDistance * (1.0F - transitionProgress), size, color);
         Render2D.popAlpha();
         return transitionProgress;
      }
   }
}
