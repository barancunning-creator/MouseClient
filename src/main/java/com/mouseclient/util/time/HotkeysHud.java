package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.hud.HudIcon;
import com.mouseclient.internal.HudSpriteSheet;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleRegistry;
import com.mouseclient.network.HudStyle;
import com.mouseclient.render.avatar.Easings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class HotkeysHud {
   private static final String[] PREVIEW_KEYS = new String[]{("A"), ("B"), ("C"), ("D"), ("E")};
   private static final HotkeysHud.KeybindRow previewRow = new HotkeysHud.KeybindRow(null, ("Example Module"));
   private static final Map<Module, HotkeysHud.KeybindRow> rowsByModule = new IdentityHashMap<>();
   private static final List<HotkeysHud.KeybindRow> visibleRows = new ArrayList<>();
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();
   private static final TweenAnimation widthAnimation = new TweenAnimation();
   private static final TweenAnimation headerWidthAnimation = new TweenAnimation();
   private static final TweenAnimation bindingColumnWidthAnimation = new TweenAnimation();
   private static float width;
   private static float height;

   private HotkeysHud() {
   }

   public static float render(float x, float bottomY) {
      updateRows();
      float headerWidth = HudPanelRenderer.headerWidth(("Hotkeys"));
      float bindingColumnWidth = 0.0F;
      float moduleColumnWidth = 0.0F;
      float rowsHeight = (13.0F);

      for (HotkeysHud.KeybindRow measuredRow : visibleRows) {
         bindingColumnWidth = Math.max(bindingColumnWidth, HudPanelRenderer.combinedRowWidth((10.0F), bindingTextWidth(measuredRow)));
         moduleColumnWidth = Math.max(moduleColumnWidth, HudPanelRenderer.textChipWidth(moduleNameWidth(measuredRow)));
         rowsHeight += (14.0F) * measuredRow.visibilityAnimation.floatValue();
      }

      float combinedWidth = bindingColumnWidth + (bindingColumnWidth > 0.0F && moduleColumnWidth > 0.0F ? 2.0F : 0.0F) + moduleColumnWidth;
      float targetWidth = Math.max(headerWidth, Math.max(combinedWidth, 1.0F));
      boolean visible = !visibleRows.isEmpty() || HudRenderContext.isChatVisible();
      visibilityAnimation.update();
      visibilityAnimation.animateTo(visible ? 1.0 : 0.0, (0.4D), Easings.QUART_OUT);
      widthAnimation.update();
      if (!visibleRows.isEmpty()) {
         widthAnimation.animateTo(Math.max(targetWidth, 1.0F), (0.6D), Easings.QUART_OUT);
      }

      headerWidthAnimation.update();
      if (!visibleRows.isEmpty()) {
         headerWidthAnimation.animateTo(Math.max(targetWidth, 1.0F), (0.6D), Easings.QUART_OUT);
      }

      bindingColumnWidthAnimation.update();
      if (!visibleRows.isEmpty()) {
         bindingColumnWidthAnimation.animateTo(Math.max(bindingColumnWidth, 0.0F), (0.6D), Easings.QUART_OUT);
      }

      width = widthAnimation.floatValue();
      height = Math.max(rowsHeight, 1.0F);
      float alpha = visibilityAnimation.floatValue();
      if (alpha <= (0.001F)) {
         return 0.0F;
      }

      float[] anchor = HudLayout.resolveAnchor(("keybinds"), x, bottomY - height);
      float left = anchor[0];
      float top = anchor[1];
      HudLayout.registerBounds(("keybinds"), left, top, width, height);
      HudRenderContext.pushIdentityScale(left + width / 2.0F, top + height / 2.0F, alpha);
      float cardWidth = Math.max(width, 1.0F);
      HudPanelRenderer.drawStackCard(left, top, cardWidth, height, alpha);
      HudPanelRenderer.drawStackTitle(left, top, cardWidth, alpha, HudIcon.KEYBINDS, ("Hotkeys"));
      float rowY = top + (15.0F);

      for (HotkeysHud.KeybindRow row : visibleRows) {
         renderRow(left, cardWidth, rowY, alpha, row);
         rowY += (13.0F) * row.visibilityAnimation.floatValue();
      }

      HudRenderContext.popTransform();
      return height * alpha;
   }

   static {
      widthAnimation.setValue(1.0);
   }

   private static void updateRows() {
      visibleRows.clear();

      for (Module module : ModuleRegistry.modules()) {
         boolean shouldShowRow = module.isBound() && module.isEnabled();
         HotkeysHud.KeybindRow moduleRow = rowsByModule.get(module);
         if (moduleRow == null) {
            if (!shouldShowRow) {
               continue;
            }

            moduleRow = new HotkeysHud.KeybindRow(module, module.name);
            rowsByModule.put(module, moduleRow);
         }

         moduleRow.bindingName = module.isBound() ? module.getBindingName() : moduleRow.bindingName;
         moduleRow.visible = shouldShowRow;
         if (shouldShowRow || moduleRow.visibilityAnimation.floatValue() > (0.001D)) {
            visibleRows.add(moduleRow);
         }
      }

      rowsByModule.entrySet().removeIf(rowEntry -> !rowEntry.getValue().visible && rowEntry.getValue().visibilityAnimation.floatValue() <= (0.001D) && rowEntry.getValue().visibilityAnimation.isFinished());
      previewRow.visible = visibleRows.isEmpty() && HudRenderContext.isChatVisible();
      if (previewRow.visible) {
         previewRow.bindingName = PREVIEW_KEYS[HudRenderContext.elapsedSeconds() % PREVIEW_KEYS.length];
      }

      if (previewRow.visible || previewRow.visibilityAnimation.floatValue() > (0.001D)) {
         visibleRows.add(previewRow);
      }

      visibleRows.sort(Comparator.<HotkeysHud.KeybindRow>comparingDouble(candidateRow -> moduleNameWidth(candidateRow)).reversed());

      for (HotkeysHud.KeybindRow animatedRow : visibleRows) {
         animatedRow.visibilityAnimation.update();
         animatedRow.visibilityAnimation.animateTo(animatedRow.visible ? 1.0 : 0.0, (0.6D), Easings.QUART_OUT);
         boolean enabled = animatedRow.module == null || animatedRow.module.isEnabled();
         animatedRow.enabledAnimation.update();
         if (animatedRow.enabledAnimationInitialized) {
            animatedRow.enabledAnimation.animateTo(enabled ? 1.0 : 0.0, (0.4D), Easings.QUART_OUT);
         } else {
            animatedRow.enabledAnimationInitialized = true;
            animatedRow.enabledAnimation.setValue(enabled ? 1.0 : 0.0);
         }
      }
   }

   private static float bindingTextWidth(HotkeysHud.KeybindRow row) {
      return GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, row.bindingName, (6.0F));
   }

   private static void renderRow(float x, float cardWidth, float y, float alpha, HotkeysHud.KeybindRow row) {
      float rowAlpha = row.visibilityAnimation.floatValue() * alpha;
      if (!(rowAlpha <= (0.001F))) {
         float textY = y + 1.2F;
         int nameColor = GuiVisuals.multiplyAlpha(0xFFE7EAF0, rowAlpha);
         int valueColor = GuiVisuals.multiplyAlpha(0xFFF4F6FA, rowAlpha);
         GuiTextRenderer.draw(GuiFont.SEMI, row.moduleName, x + 6.0F, textY, 6.0F, nameColor);
         float valueWidth = bindingTextWidth(row);
         GuiTextRenderer.draw(GuiFont.SEMI, row.bindingName, x + cardWidth - 6.0F - valueWidth, textY, 6.0F, valueColor);
      }
   }

   private static float moduleNameWidth(HotkeysHud.KeybindRow row) {
      return GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, row.moduleName, (6.0F));
   }

   private static final class KeybindRow extends AnimatedHudRow {
      public final Module module;
      public final String moduleName;
      public String bindingName = "";
      public final TweenAnimation enabledAnimation = new TweenAnimation();
      public boolean enabledAnimationInitialized;

      public KeybindRow(Module module, String moduleName) {
         this.module = module;
         this.moduleName = moduleName;
      }
   }
}
