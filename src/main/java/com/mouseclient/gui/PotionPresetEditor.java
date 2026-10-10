package com.mouseclient.gui;

import com.mouseclient.internal.RoundedClipStack;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.util.time.PotionPresetStore;
import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.TextSetting;
import com.mouseclient.util.time.ClickGuiTheme;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.ScalarAnimation;
import com.mouseclient.util.time.GuiFont;
import java.util.ArrayList;
import net.minecraft.item.ItemStack;

public final class PotionPresetEditor {
   public static final String LOGO_TEXTURE = "assets/mouseclient/textures/gui/presets/logo.png";
   public static final String CHIP_TEXTURE = "assets/mouseclient/textures/gui/presets/chip.png";
   public static final String SEARCH_TEXTURE = "assets/mouseclient/textures/gui/presets/search.png";
   public static final String UNBOUND_KEY_TEXTURE = "assets/mouseclient/textures/gui/presets/keyboard_dim.png";
   public static final String BOUND_KEY_TEXTURE = "assets/mouseclient/textures/gui/presets/keyboard_on.png";
   public static final String TOGGLE_TEXTURE = "assets/mouseclient/textures/gui/presets/toggle.png";
   public static final String TRASH_TEXTURE = "assets/mouseclient/textures/gui/presets/trash.png";
   public static final String SMALL_CHIP_TEXTURE = "assets/mouseclient/textures/gui/presets/chip_small.png";
   public static final String ADD_POTION_TEXTURE = "assets/mouseclient/textures/gui/presets/potion_add.png";
   public static final String SMALL_SEARCH_TEXTURE = "assets/mouseclient/textures/gui/presets/search_small.png";
   private static PotionPresetEditor INSTANCE;
   private ScalarAnimation visibilityAnimation;
   private boolean open;
   private long openTime;

   private void drawEditor(ClickGuiContext context, float screenWidth, float screenHeight, boolean interactive, float deltaSeconds, float verticalOffset) {
      PotionPresetStore presets = PotionPresetStore.getInstance();
      float editorX = (screenWidth - (263.0F) * context.scale) / 2.0F;
      float editorY = (screenHeight - (133.33F) * context.scale) / 2.0F + verticalOffset;
      long unusedCurrentTime = System.currentTimeMillis();
      drawPanel(editorX, editorY, (166.0F) * context.scale, (133.33F) * context.scale, (5.7F) * context.scale, context.scale);
      drawPanel(
         editorX + (172.0F) * context.scale,
         editorY,
         (91.0F) * context.scale,
         (133.33F) * context.scale,
         (5.7F) * context.scale,
         context.scale
      );
      if (interactive) {
         context.registerClick(editorX, editorY, (166.0F) * context.scale, (133.33F) * context.scale, null, () -> {});
      }

      if (interactive) {
         context.registerClick(editorX + (172.0F) * context.scale, editorY, (91.0F) * context.scale, (133.33F) * context.scale, null, () -> {});
      }

      drawPanelHeader(context, editorX, editorY, (166.0F) * context.scale, ("Создание пресета"), false, ("Potion Combiner"), ("Выбрасывает указанные зелья из инвентаря по нажатию указанной вами клавиши"), (136.0F), (151.33F));
      drawPanelHeader(
         context, editorX + (172.0F) * context.scale, editorY, (91.0F) * context.scale, ("Настройки пресетов"), true, ("Ваши пресеты"), ("Созданные вами ранее пресеты системы Potion Combiner"), (80.0F), (75.0F)
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI, ("Имя пресета"), editorX + (6.67F) * context.scale, editorY + (46.67F) * context.scale, (4.2F) * context.scale, GuiVisuals.white
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Уникальное имя данного пресета"),
         editorX + (6.67F) * context.scale,
         editorY + (52.33F) * context.scale,
         (4.2F) * context.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.35F))
      );
      drawTextInput(
         context,
         presets.draftName,
         editorX + (6.67F) * context.scale,
         editorY + (60.0F) * context.scale,
         (151.33F) * context.scale,
         (9.0F) * context.scale,
         interactive,
         ("pc:name")
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI, ("Горячая клавиша"), editorX + (6.67F) * context.scale, editorY + (74.33F) * context.scale, (4.2F) * context.scale, GuiVisuals.white
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Зелье выбрасываются при нажатии бинда"),
         editorX + (6.67F) * context.scale,
         editorY + (80.0F) * context.scale,
         (4.2F) * context.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.35F))
      );
      this.drawKeybind(context, presets.hotkey, editorX + (134.0F) * context.scale, editorY + (76.0F) * context.scale, (30.0F), interactive, ("pc:draftbind"));

      for (int slotIndex = 0; slotIndex < 7; slotIndex++) {
         float slotX = editorX + (6.67F) * context.scale + slotIndex * (22.0F) * context.scale;
         float slotY = editorY + (90.0F) * context.scale;
         Render2D.fillRoundedRectangle(
            slotX,
            slotY,
            (19.67F) * context.scale,
            (19.67F) * context.scale,
            (3.33F) * context.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.05F))
         );
         Render2D.drawOutline(
            slotX,
            slotY,
            (19.67F) * context.scale,
            (19.67F) * context.scale,
            (3.33F) * context.scale,
            (0.35F) * context.scale,
            GuiVisuals.multiplyAlpha(
               GuiVisuals.accentColor,
               slotIndex == 6
                  ? (0.19F)
                  : (0.06F)
                     + (0.13F)
                        * context.animateBoolean(
                           "pc:slot:" + slotIndex,
                           interactive
                              && GuiVisuals.containsPoint(
                                 context.mouseX, context.mouseY, slotX, slotY, (19.67F) * context.scale, (19.67F) * context.scale
                              )
                        )
            )
         );
         if (slotIndex < 6 && !presets.draftStacks[slotIndex].isEmpty()) {
            GuiItemRenderer.render(
               presets.draftStacks[slotIndex],
               slotX + (4.33F) * context.scale,
               slotY + (4.17F) * context.scale,
               (11.0F) * context.scale,
               ItemRenderOptions.noDecorations(currentAlpha())
            );
         }

         if (slotIndex < 6 && presets.draftPotionNames[slotIndex] != null) {
            context.offerTooltip(presets.draftPotionNames[slotIndex], slotX, slotY, (19.67F) * context.scale, (19.67F) * context.scale);
         }

         int clickedSlotIndex = slotIndex;
         if (interactive) {
            context.registerClick(slotX, slotY, (19.67F) * context.scale, (19.67F) * context.scale, null, () -> {
               if (clickedSlotIndex < 6 && !presets.draftStacks[clickedSlotIndex].isEmpty()) {
                  presets.draftStacks[clickedSlotIndex] = ItemStack.EMPTY;
                  presets.draftPotionNames[clickedSlotIndex] = null;
               } else {
                  presets.selectedDraftSlot = clickedSlotIndex < 6 ? clickedSlotIndex : presets.firstEmptyDraftSlot();
               }
            });
         }
      }

      float saveHoverProgress = context.animateBoolean(
         ("pc:save"),
         interactive
            && GuiVisuals.containsPoint(
               context.mouseX,
               context.mouseY,
               editorX + (6.67F) * context.scale,
               editorY + (116.33F) * context.scale,
               (151.33F) * context.scale,
               (11.0F) * context.scale
            )
      );
      Render2D.fillRoundedRectangle(
         editorX + (6.67F) * context.scale,
         editorY + (116.33F) * context.scale,
         (151.33F) * context.scale,
         (11.0F) * context.scale,
         2.0F * context.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.02F) + (0.03F) * saveHoverProgress)
      );
      Render2D.drawOutline(
         editorX + (6.67F) * context.scale,
         editorY + (116.33F) * context.scale,
         (151.33F) * context.scale,
         (11.0F) * context.scale,
         2.0F * context.scale,
         (0.35F) * context.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.06F) + (0.14F) * saveHoverProgress)
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI_BOLD,
         ("Сохранить пресет"),
         editorX
            + (6.67F) * context.scale
            + ((151.33F) * context.scale - GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, ("Сохранить пресет"), (4.2F) * context.scale)) / 2.0F,
         editorY + (119.33F) * context.scale,
         (4.2F) * context.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, 1.0F - (0.45F) * saveHoverProgress)
      );
      if (interactive) {
         context.registerClick(
            editorX + (6.67F) * context.scale,
            editorY + (116.33F) * context.scale,
            (151.33F) * context.scale,
            (11.0F) * context.scale,
            null,
            () -> presets.saveDraft()
         );
      }

      this.drawPresetList(context, presets, editorX + (172.0F) * context.scale, editorY, interactive, deltaSeconds);
      if (presets.selectedDraftSlot < 0 && !(context.animateObjectBoolean(("pc:dropdown"), presets.selectedDraftSlot >= 0) > (0.005F))) {
         presets.pickerBounds = null;
      } else {
         this.drawPotionPicker(
            context,
            presets,
            editorX - (80.0F) * context.scale,
            editorY + (27.33F) * context.scale,
            interactive && presets.selectedDraftSlot >= 0,
            deltaSeconds,
            context.animateObjectBoolean(("pc:dropdown"), presets.selectedDraftSlot >= 0)
         );
      }
   }

   public static PotionPresetEditor getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new PotionPresetEditor();
      }

      return INSTANCE;
   }

   public static void drawPanel(float x, float y, float width, float height, float radius, float scale) {
      GuiShapeRenderer.drawBlur(x, y, width, height, currentAlpha(), CornerRadii.uniform(radius), GuiShapeRenderer.BlurQuality.LOW, true);
      Render2D.fillRoundedRectangle(x, y, width, height, radius, ClickGuiTheme.panelBackground);
      Render2D.drawOutline(x, y, width, height, radius, (0.35F) * scale, GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.06F)));
   }

   public boolean mouseScrolled(float mouseX, float mouseY, double verticalScroll, float scale) {
      if (PotionPresetStore.getInstance().pickerBounds != null
         && PotionPresetStore.getInstance().selectedDraftSlot >= 0
         && GuiVisuals.containsPoint(
            mouseX,
            mouseY,
            PotionPresetStore.getInstance().pickerBounds[0],
            PotionPresetStore.getInstance().pickerBounds[1],
            PotionPresetStore.getInstance().pickerBounds[2],
            PotionPresetStore.getInstance().pickerBounds[3]
         )) {
         float pickerScrollAmount = (float)verticalScroll * (14.0F);
         PotionPresetStore.getInstance().pickerScroll.target = ClickGuiContext.clamp(
            PotionPresetStore.getInstance().pickerScroll.target - pickerScrollAmount * scale, 0.0F, PotionPresetStore.getInstance().pickerScroll.maxScroll
         );
      } else if (PotionPresetStore.getInstance().presetListBounds != null
         && GuiVisuals.containsPoint(
            mouseX,
            mouseY,
            PotionPresetStore.getInstance().presetListBounds[0],
            PotionPresetStore.getInstance().presetListBounds[1],
            PotionPresetStore.getInstance().presetListBounds[2],
            PotionPresetStore.getInstance().presetListBounds[3]
         )) {
         float presetScrollAmount = (float)verticalScroll * (14.0F);
         PotionPresetStore.getInstance().presetScroll.target = ClickGuiContext.clamp(
            PotionPresetStore.getInstance().presetScroll.target - presetScrollAmount * scale, 0.0F, PotionPresetStore.getInstance().presetScroll.maxScroll
         );
      }

      return true;
   }

   public static void drawPanelHeader(ClickGuiContext context, float x, float y, float width, String caption, boolean compact, String title, String description, float descriptionWidth, float dividerWidth) {
      GuiTextRenderer.draw(
         GuiFont.SEMI_BOLD,
         caption,
         compact ? x + (8.0F) * context.scale : x + (6.67F) * context.scale,
         y + (6.33F) * context.scale,
         (4.2F) * context.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.1F))
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         title,
         x + (compact ? (8.0F) : (6.67F)) * context.scale,
         y + (15.33F) * context.scale,
         (5.33F) * context.scale,
         GuiVisuals.white
      );
      float lineY = y + (23.33F) * context.scale;

      for (String line : GuiVisuals.wrapWords(GuiFont.SEMI, description, (4.2F) * context.scale, descriptionWidth * context.scale)) {
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            line,
            x + (compact ? (8.0F) : (6.67F)) * context.scale,
            lineY,
            (4.2F) * context.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.35F))
         );
         lineY += (5.0F) * context.scale;
      }

      Render2D.fillRoundedRectangle(
         x + (compact ? (8.0F) : (6.67F)) * context.scale,
         y + (40.0F) * context.scale,
         dividerWidth * context.scale,
         (0.33F) * context.scale,
         0.0F,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.03F))
      );
   }

   public void close() {
      this.open = false;
      PotionPresetStore.getInstance().selectedDraftSlot = -1;
      PotionPresetStore.getInstance().save();
   }

   private void drawKeybind(ClickGuiContext context, BindSetting setting, float x, float y, float width, boolean interactive, String animationKey) {
      boolean bindingActive = context.bindingSetting == setting;
      Render2D.drawRoundedImage(
         !setting.isBound() && !bindingActive ? ("assets/mouseclient/textures/gui/presets/keyboard_dim.png") : ("assets/mouseclient/textures/gui/presets/keyboard_on.png"),
         x,
         y,
         (3.33F) * context.scale,
         (3.33F) * context.scale,
         0.0F,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, 1.0F)
      );
      Render2D.pushScissor(context.drawContext, x, y - 2.0F * context.scale, width * context.scale, (8.0F) * context.scale);
      GuiTextRenderer.draw(
         GuiFont.SEMI_BOLD,
         bindingActive ? ("...") : (setting.isBound() ? setting.getBindingName().toUpperCase() : ("Press key")),
         x + (5.67F) * context.scale,
         y - (0.33F) * context.scale,
         (4.0F) * context.scale,
         !bindingActive && !setting.isBound()
            ? GuiVisuals.multiplyAlpha(
               GuiVisuals.white,
               (0.2F)
                  + (0.3F)
                     * context.animateBoolean(
                        animationKey,
                        interactive
                           && GuiVisuals.containsPoint(
                              context.mouseX,
                              context.mouseY,
                              x - 1.0F * context.scale,
                              y - 2.0F * context.scale,
                              width * context.scale,
                              (8.0F) * context.scale
                           )
                     )
            )
            : GuiVisuals.accentColor
      );
      Render2D.popScissor(context.drawContext);
      if (interactive) {
         context.registerClick(
            x - 1.0F * context.scale,
            y - 2.0F * context.scale,
            width * context.scale,
            (8.0F) * context.scale,
            null,
            () -> context.bindingSetting = setting,
            () -> setting.withKeyCode(-1)
         );
      }
   }

   public static void drawTextInput(ClickGuiContext context, TextSetting setting, float x, float y, float width, float height, boolean interactive, String animationKey) {
      Render2D.fillRoundedRectangle(x, y, width, height, 2.0F * context.scale, GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.05F)));
      Render2D.drawOutline(
         x,
         y,
         width,
         height,
         2.0F * context.scale,
         (0.35F) * context.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.06F) + (0.13F) * context.animateBoolean(animationKey, context.focusedText == setting))
      );
      Render2D.pushScissor(context.drawContext, x + (2.5F) * context.scale, y, width - (5.5F) * context.scale, height);
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         (setting.getValue().isEmpty() && context.focusedText != setting ? setting.getPlaceholder() : setting.getValue()) + (context.focusedText == setting ? ClickGuiContext.blinkingCursor() : ""),
         x + (3.33F) * context.scale,
         y + height / 2.0F - (1.67F) * context.scale,
         (3.33F) * context.scale,
         setting.getValue().isEmpty() && context.focusedText != setting ? GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.24F)) : GuiVisuals.white
      );
      Render2D.popScissor(context.drawContext);
      if (interactive) {
         context.registerClick(x, y, width, height, null, () -> {
            context.focusedText = setting;
            setting.moveToEnd(false);
         });
      }
   }

   public static void drawBranding(ClickGuiContext context, float screenWidth, float screenHeight) {
      Render2D.drawRoundedImage(
         ("assets/mouseclient/textures/gui/presets/logo.png"),
         screenWidth / 2.0F
            - ((8.0F) + GuiTextRenderer.getWidth(GuiFont.BOLD, ("маусклиент"), (3.43F) * context.scale) / context.scale) * context.scale / 2.0F,
         screenHeight - (23.0F) * context.scale,
         (5.33F) * context.scale,
         (4.33F) * context.scale,
         0.0F,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, 1.0F)
      );
      GuiTextRenderer.draw(
         GuiFont.BOLD,
         ("маусклиент"),
         screenWidth / 2.0F
            - ((8.0F) + GuiTextRenderer.getWidth(GuiFont.BOLD, ("маусклиент"), (3.43F) * context.scale) / context.scale) * context.scale / 2.0F
            + (8.0F) * context.scale,
         screenHeight - (22.7F) * context.scale,
         (3.43F) * context.scale,
         GuiVisuals.accentColor
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Beta 1.21.11"),
         screenWidth / 2.0F
            - ((8.0F) + GuiTextRenderer.getWidth(GuiFont.BOLD, ("маусклиент"), (3.43F) * context.scale) / context.scale) * context.scale / 2.0F
            + (9.0F) * context.scale,
         screenHeight - (17.3F) * context.scale,
         (3.43F) * context.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.4F))
      );
   }

   private PotionPresetEditor() {
      this.visibilityAnimation = new ScalarAnimation((300L)).reset(0.0);
   }

   public void open() {
      if (!this.open) {
         this.open = true;
         this.openTime = System.currentTimeMillis();
      }
   }

   public float visibilityProgress() {
      this.visibilityAnimation.setTarget(this.open ? 1.0 : 0.0);
      return (float)this.visibilityAnimation.value();
   }

   public void render(ClickGuiContext context, float screenWidth, float screenHeight, float progress, float deltaSeconds) {
      if (!(progress < (0.005F))) {
         context.modalClickStart = context.clickRegions.size();
         Render2D.fillRoundedRectangle(0.0F, 0.0F, screenWidth, screenHeight, 0.0F, GuiVisuals.multiplyAlpha(ClickGuiTheme.basePanelTint, progress));
         if (this.open && progress > (0.6F)) {
            context.registerClick(0.0F, 0.0F, screenWidth, screenHeight, null, () -> this.close());
         }

         Render2D.pushAlpha(progress);
         this.drawEditor(context, screenWidth, screenHeight, this.open && progress > (0.6F), deltaSeconds, (1.0F - progress) * (12.0F) * context.scale);
         Render2D.popAlpha();
      }
   }

   public static float currentAlpha() {
      return (Render2D.applyAlpha((-16777216)) >>> 24 & 0xFF) / (255.0F);
   }

   public boolean isOpen() {
      return this.open;
   }

   private void drawPotionPicker(ClickGuiContext context, PotionPresetStore presets, float x, float y, boolean interactive, float deltaSeconds, float progress) {
      interactive = interactive && progress > (0.6F);
      x -= (1.0F - progress) * (8.0F) * context.scale;
      Render2D.pushAlpha(progress);
      if (interactive) {
         context.registerClick(0.0F, 0.0F, (99999.0F), (99999.0F), null, () -> presets.selectedDraftSlot = -1);
      }

      float panelWidth = (74.0F) * context.scale;
      float panelHeight = (78.67F) * context.scale;
      drawPanel(x, y, panelWidth, panelHeight, (3.0F) * context.scale, context.scale);
      Render2D.fillRoundedRectangle(x, y, panelWidth, panelHeight, (3.0F) * context.scale, GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.05F)));
      if (interactive) {
         context.registerClick(x, y, panelWidth, panelHeight, null, () -> {});
      }

      GuiTextRenderer.draw(
         GuiFont.SEMI_BOLD,
         ("Ваши зелья"),
         x + (5.5F) * context.scale,
         y + (5.0F) * context.scale,
         (2.8F) * context.scale,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.1F))
      );
      GuiTextRenderer.draw(
         GuiFont.SEMI,
         ("Текущие зелья"),
         x + (5.5F) * context.scale,
         y + (10.0F) * context.scale,
         (4.2F) * context.scale,
         GuiVisuals.white
      );
      drawTextInput(
         context,
         presets.search,
         x + (5.5F) * context.scale,
         y + (18.33F) * context.scale,
         (63.0F) * context.scale,
         (9.0F) * context.scale,
         interactive,
         ("pc:psearch")
      );
      Render2D.fillRoundedRectangle(
         x + (5.5F) * context.scale,
         y + (32.33F) * context.scale,
         (63.0F) * context.scale,
         (0.33F) * context.scale,
         0.0F,
         GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.03F))
      );
      java.util.ArrayList<com.mouseclient.modules.impl.player.ServerHelper.ServerItemBind> matchingPotions = presets.getMatchingPotions();
      float listY = y + (36.33F) * context.scale;
      float listHeight = panelHeight - (40.0F) * context.scale;
      presets.pickerBounds = new float[]{x, y, panelWidth, panelHeight};
      presets.pickerScroll.maxScroll = Math.max(0.0F, matchingPotions.size() * (7.33F) * context.scale - listHeight);
      presets.pickerScroll.update(deltaSeconds);
      if (matchingPotions.isEmpty()) {
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            ("Зелий нет"),
            x + (12.0F) * context.scale,
            listY + 2.0F * context.scale,
            (3.67F) * context.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.2F))
         );
      }

      Render2D.pushScissor(context.drawContext, x + 2.0F * context.scale, listY, panelWidth - (4.0F) * context.scale, listHeight);
      float[] listClip = new float[]{x + 2.0F * context.scale, listY, panelWidth - (4.0F) * context.scale, listHeight};

      for (int potionIndex = 0; potionIndex < matchingPotions.size(); potionIndex++) {
         float rowY = listY + potionIndex * (7.33F) * context.scale - presets.pickerScroll.getScroll();
         if (!(rowY + (6.0F) * context.scale < listY - 2.0F) && !(rowY > listY + listHeight + 2.0F)) {
            ServerHelper.ServerItemBind binding = (ServerHelper.ServerItemBind)matchingPotions.get(potionIndex);
            float hoverProgress = context.animateBoolean(
               "pc:drow:" + potionIndex,
               interactive
                  && GuiVisuals.containsPoint(
                     context.mouseX,
                     context.mouseY,
                     x + (3.5F) * context.scale,
                     rowY - 1.0F * context.scale,
                     panelWidth - (9.5F) * context.scale,
                     (7.33F) * context.scale
                  )
                  && GuiVisuals.containsPoint(context.mouseX, context.mouseY, x, listY, panelWidth, listHeight)
            );
            if (hoverProgress > (0.005F)) {
               Render2D.fillRoundedRectangle(
                  x + (3.5F) * context.scale,
                  rowY - 1.0F * context.scale,
                  panelWidth - (9.5F) * context.scale,
                  (7.0F) * context.scale,
                  2.0F * context.scale,
                  GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.08F) * hoverProgress)
               );
            }

            GuiItemRenderer.render(
               PotionPresetStore.createPreviewStack(binding.binding().name),
               x + (5.5F) * context.scale,
               rowY,
               (4.67F) * context.scale,
               ItemRenderOptions.noDecorations(currentAlpha())
            );
            Render2D.pushScissor(
               context.drawContext,
               x + (12.5F) * context.scale,
               rowY - 1.0F * context.scale,
               panelWidth - (19.5F) * context.scale,
               (7.33F) * context.scale
            );
            float labelX = x + (12.5F) * context.scale;
            float labelY = rowY + (0.33F) * context.scale;
            float labelSize = (3.67F) * context.scale;
            GuiTextRenderer.draw(GuiFont.SEMI, binding.binding().name, labelX, labelY, labelSize, GuiVisuals.white);
            Render2D.popScissor(context.drawContext);
            if (interactive) {
               context.registerClick(
                  x + (3.5F) * context.scale,
                  rowY - 1.0F * context.scale,
                  panelWidth - (9.5F) * context.scale,
                  (7.33F) * context.scale,
                  listClip,
                  () -> presets.setDraftPotion(presets.selectedDraftSlot < 0 ? presets.firstEmptyDraftSlot() : presets.selectedDraftSlot, binding.binding().name)
               );
            }
         }
      }

      Render2D.popScissor(context.drawContext);
      if (presets.pickerScroll.maxScroll > 0.0F) {
         Render2D.fillRoundedRectangle(
            x + (67.67F) * context.scale,
            listY + (listHeight - (11.0F) * context.scale) * (presets.pickerScroll.getScroll() / presets.pickerScroll.maxScroll),
            (0.67F) * context.scale,
            (11.0F) * context.scale,
            (3.0F) * context.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.35F))
         );
      }

      Render2D.popAlpha();
   }

   public boolean handleEscape() {
      if (PotionPresetStore.getInstance().selectedDraftSlot >= 0) {
         PotionPresetStore.getInstance().selectedDraftSlot = -1;
         return true;
      }

      if (!this.open) {
         return false;
      }

      this.open = false;
      PotionPresetStore.getInstance().save();
      return true;
   }

   private void drawPresetList(ClickGuiContext context, PotionPresetStore presets, float x, float y, boolean interactive, float deltaSeconds) {
      float listY = y + (45.0F) * context.scale;
      float listHeight = (133.33F) * context.scale - (47.0F) * context.scale;
      presets.presetListBounds = new float[]{x, listY, (91.0F) * context.scale, listHeight};
      presets.presetScroll.maxScroll = Math.max(0.0F, presets.presets.size() * (16.0F) * context.scale - listHeight);
      presets.presetScroll.update(deltaSeconds);
      if (presets.presets.isEmpty()) {
         GuiTextRenderer.draw(
            GuiFont.SEMI,
            ("Пресетов пока нет"),
            x + ((91.0F) * context.scale - GuiTextRenderer.getWidth(GuiFont.SEMI, ("Пресетов пока нет"), (4.0F) * context.scale)) / 2.0F,
            listY + (8.0F) * context.scale,
            (4.0F) * context.scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.white, (0.2F))
         );
      }

      Render2D.pushScissor(context.drawContext, x + 2.0F * context.scale, listY, (89.0F) * context.scale, listHeight);
      RoundedClipStack.push(
         x + 2.0F * context.scale,
         listY,
         (89.0F) * context.scale,
         listHeight,
         2.0F * context.scale,
         2.0F * context.scale,
         2.0F * context.scale,
         2.0F * context.scale
      );
      float[] listClip = new float[]{x + 2.0F * context.scale, listY, (89.0F) * context.scale, listHeight};

      for (int presetIndex = 0; presetIndex < presets.presets.size(); presetIndex++) {
         PotionPresetStore.PotionPreset preset = presets.presets.get(presetIndex);
         float rowY = listY + presetIndex * (16.0F) * context.scale - presets.presetScroll.getScroll();
         if (!(rowY + (15.5F) * context.scale < listY - 2.0F) && !(rowY > listY + listHeight + 2.0F)) {
            GuiTextRenderer.draw(
               GuiFont.SEMI,
               preset.name,
               x + (8.0F) * context.scale,
               rowY,
               (3.8F) * context.scale,
               GuiVisuals.multiplyAlpha(GuiVisuals.white, preset.enabled ? 1.0F : (0.5F))
            );
            float controlsY = rowY + (5.5F) * context.scale;
            float potionSpacing = preset.potions.size() <= 1 ? (8.5F) : Math.min((8.5F), (29.0F) / (Math.min(5, preset.potions.size()) - 1));

            for (int potionIndex = 0; potionIndex < Math.min(5, preset.potions.size()); potionIndex++) {
               GuiItemRenderer.render(
                  preset.potions.get(potionIndex).previewStack(),
                  x + (8.0F) * context.scale + potionIndex * potionSpacing * context.scale,
                  controlsY + (0.83F) * context.scale,
                  (7.0F) * context.scale,
                  ItemRenderOptions.noDecorations(currentAlpha() * (preset.enabled ? 1.0F : (0.4F)))
               );
            }

            this.drawKeybind(
               context,
               preset.hotkey,
               x + ((18.0F) + (Math.min(5, preset.potions.size()) - 1) * potionSpacing) * context.scale,
               controlsY + 2.0F * context.scale,
               (46.0F) - (Math.min(5, preset.potions.size()) - 1) * potionSpacing,
               interactive,
               "pc:prb:" + presetIndex
            );
            float toggleX = x + (64.67F) * context.scale;
            float deleteX = x + (74.67F) * context.scale;
            Render2D.fillRoundedRectangle(
               toggleX,
               controlsY,
               (8.67F) * context.scale,
               (8.67F) * context.scale,
               2.0F * context.scale,
               GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.05F))
            );
            Render2D.drawOutline(
               toggleX,
               controlsY,
               (8.67F) * context.scale,
               (8.67F) * context.scale,
               2.0F * context.scale,
               (0.35F) * context.scale,
               GuiVisuals.multiplyAlpha(
                  GuiVisuals.accentColor,
                  (0.06F)
                     + (0.13F)
                        * context.animateBoolean(
                           "pc:tgh:" + presetIndex,
                           interactive
                              && GuiVisuals.containsPoint(
                                 context.mouseX, context.mouseY, toggleX, controlsY, (8.67F) * context.scale, (8.67F) * context.scale
                              )
                        )
               )
            );
            context.drawIcon(
               preset.enabled ? ("O") : ("N"),
               toggleX + (4.33F) * context.scale,
               controlsY + (4.33F) * context.scale,
               (4.5F) * context.scale,
               preset.enabled
                  ? GuiVisuals.accentColor
                  : GuiVisuals.lerpColor(
                     GuiVisuals.mutedTextColor,
                     GuiVisuals.white,
                     context.animateBoolean(
                        "pc:tgh:" + presetIndex,
                        interactive
                           && GuiVisuals.containsPoint(
                              context.mouseX, context.mouseY, toggleX, controlsY, (8.67F) * context.scale, (8.67F) * context.scale
                           )
                     )
                  )
            );
            Render2D.fillRoundedRectangle(
               deleteX,
               controlsY,
               (8.67F) * context.scale,
               (8.67F) * context.scale,
               2.0F * context.scale,
               GuiVisuals.multiplyAlpha(GuiVisuals.accentColor, (0.05F))
            );
            Render2D.drawOutline(
               deleteX,
               controlsY,
               (8.67F) * context.scale,
               (8.67F) * context.scale,
               2.0F * context.scale,
               (0.35F) * context.scale,
               GuiVisuals.multiplyAlpha(
                  GuiVisuals.accentColor,
                  (0.06F)
                     + (0.13F)
                        * context.animateBoolean(
                           "pc:trh:" + presetIndex,
                           interactive
                              && GuiVisuals.containsPoint(
                                 context.mouseX, context.mouseY, deleteX, controlsY, (8.67F) * context.scale, (8.67F) * context.scale
                              )
                        )
               )
            );
            context.drawIcon(
               ("P"),
               deleteX + (4.33F) * context.scale,
               controlsY + (4.33F) * context.scale,
               (4.5F) * context.scale,
               GuiVisuals.lerpColor(
                  GuiVisuals.mutedTextColor,
                  GuiVisuals.white,
                  context.animateBoolean(
                     "pc:trh:" + presetIndex,
                     interactive
                        && GuiVisuals.containsPoint(
                           context.mouseX, context.mouseY, deleteX, controlsY, (8.67F) * context.scale, (8.67F) * context.scale
                        )
                  )
               )
            );
            int clickedPresetIndex = presetIndex;
            if (interactive) {
               context.registerClick(toggleX, controlsY, (8.67F) * context.scale, (8.67F) * context.scale, listClip, () -> {
                  preset.enabled = !preset.enabled;
                  presets.save();
               });
            }

            if (interactive) {
               context.registerClick(deleteX, controlsY, (8.67F) * context.scale, (8.67F) * context.scale, listClip, () -> {
                  presets.presets.remove(clickedPresetIndex);
                  presets.save();
               });
            }
         }
      }

      RoundedClipStack.pop();
      Render2D.popScissor(context.drawContext);
   }
}
