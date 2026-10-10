package com.mouseclient.gui;

import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.render.pipeline.RoundedRectangleSpec;
import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.Setting;
import com.mouseclient.util.time.SettingRendererRegistry;
import com.mouseclient.util.time.ClickGuiTheme;
import com.mouseclient.util.time.SettingSection;
import java.util.List;

public final class ModuleRowRenderer {
   public static final float HEADER_HEIGHT = 19.6F;

   private static void drawToggle(ClickGuiContext context, Module module, float x, float y, float[] clip) {
      float scale = context.scale;
      float enabledProgress = context.animateBoolean("tgl:" + module.name, module.isEnabled());
      float toggleWidth = (10.74F) * scale;
      float toggleHeight = (6.64F) * scale;
      Render2D.fillRoundedRectangle(
         new RoundedRectangleSpec(
               x,
               y,
               toggleWidth,
               toggleHeight,
               toggleHeight / 2.0F,
               toggleHeight / 2.0F,
               toggleHeight / 2.0F,
               toggleHeight / 2.0F,
               Render2D.applyAlpha(GuiVisuals.lerpColor(ClickGuiTheme.disabledModuleCheckbox, ClickGuiTheme.enabledModuleCheckbox, enabledProgress))
            )
            .withSmoothness((0.5F))
      );
      float knobSize = (4.88F) * scale;
      float knobX = x + (0.93F) * scale + enabledProgress * (toggleWidth - knobSize - (1.86F) * scale);
      Render2D.fillCircle(
         knobX + knobSize / 2.0F,
         y + toggleHeight / 2.0F,
         knobSize / 2.0F,
         (0.5F),
         Render2D.applyAlpha(GuiVisuals.lerpColor(ClickGuiTheme.disabledModuleText, ClickGuiTheme.accent, enabledProgress))
      );
      context.registerClick(x - 1.0F * scale, y - (1.5F) * scale, toggleWidth + 2.0F * scale, toggleHeight + (3.0F) * scale, clip, module::toggle);
   }

   private static float renderSection(ClickGuiContext context, SettingSection section, float enabledProgress, float x, float y, float width, float[] clip) {
      float scale = context.scale;
      if (!section.hasHeader()) {
         return renderSettings(context, section.settings, enabledProgress, x, y, width, clip);
      }

      float expandedProgress = !section.settings.isEmpty() ? context.animateObjectBoolean(section.expansionKey, context.flagValue(section.expansionKey, false)) : 0.0F;
      context.drawMarqueeText(
         section.title,
         x,
         y,
         (5.5F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingIcon, ClickGuiTheme.enabledSettingIcon, enabledProgress),
         width - (6.0F) * scale
      );
      Render2D.drawRotatedImage(
         ("assets/mouseclient/textures/gui/clickgui/chev.png"),
         x + width - (4.0F) * scale,
         y + (0.6F) * scale,
         (4.0F) * scale,
         0.0F,
         expandedProgress * (180.0F),
         x + width - 2.0F * scale,
         y + (2.6F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledControlOutline, ClickGuiTheme.enabledControlOutline, enabledProgress)
      );
      context.registerClick(x, y - (1.5F) * scale, width, (8.0F) * scale, clip, () -> context.toggleFlag(section.expansionKey, false));
      float height = (6.0F) * scale;
      if (expandedProgress > (0.001F)) {
         float[] contentClip = ClickGuiContext.intersectClip(clip, x, y + (6.0F) * scale, width, expandedProgress * ((3.5F) * scale + sectionContentHeight(context, section, width)));
         Render2D.pushScissor(context.drawContext, contentClip[0], contentClip[1], contentClip[2], contentClip[3]);
         Render2D.pushAlpha(expandedProgress);
         renderSettings(context, section.settings, enabledProgress, x, y + (6.0F) * scale + (3.5F) * scale, width, contentClip);
         Render2D.popAlpha();
         Render2D.popScissor(context.drawContext);
         height = (6.0F) * scale + expandedProgress * ((3.5F) * scale + sectionContentHeight(context, section, width));
      }

      return height;
   }

   private static float renderSettings(ClickGuiContext context, List<Setting<?>> settings, float enabledProgress, float x, float y, float width, float[] clip) {
      float scale = context.scale;
      float cursorY = y;
      boolean firstVisible = true;

      for (Setting setting : settings) {
         float visibleProgress = context.animateObjectBoolean(setting, setting.isVisible());
         if (!(visibleProgress < (0.001F))) {
            if (!firstVisible) {
               cursorY += (3.5F) * scale * visibleProgress;
            }

            firstVisible = false;
            if (visibleProgress > (0.999F)) {
               cursorY += SettingRendererRegistry.renderSetting(context, setting, enabledProgress, x, cursorY, width, clip);
            } else {
               float settingHeight = SettingRendererRegistry.getSettingHeight(context, setting, width);
               float[] settingClip = ClickGuiContext.intersectClip(clip, x - (3.0F) * scale, cursorY, width + (6.0F) * scale, settingHeight * visibleProgress);
               Render2D.pushScissor(context.drawContext, settingClip[0], settingClip[1], settingClip[2], settingClip[3]);
               Render2D.pushAlpha(visibleProgress);
               SettingRendererRegistry.renderSetting(context, setting, enabledProgress, x, cursorY - (1.0F - visibleProgress) * (4.0F) * scale, width, settingClip);
               Render2D.popAlpha();
               Render2D.popScissor(context.drawContext);
               cursorY += settingHeight * visibleProgress;
            }
         }
      }

      return cursorY - y;
   }

   private static float sectionHeight(ClickGuiContext context, SettingSection section, float width) {
      float scale = context.scale;
      if (!section.hasHeader()) {
         return sectionContentHeight(context, section, width);
      }

      float headerHeight = (6.0F) * scale;
      if (section.settings.isEmpty()) {
         return headerHeight;
      }

      float expandedProgress = context.animateObjectBoolean(section.expansionKey, context.flagValue(section.expansionKey, false));
      return expandedProgress < (0.001F) ? headerHeight : headerHeight + expandedProgress * ((3.5F) * scale + sectionContentHeight(context, section, width));
   }

   public static float getHeight(ClickGuiContext context, Module module, float width) {
      float scale = context.scale;
      float headerHeight = (19.6F) * scale;
      if (!SettingSection.hasVisibleSettings(SettingSection.getSections(module))) {
         return headerHeight;
      }

      float expandedProgress = context.animateObjectBoolean(module, module.expanded);
      return expandedProgress < (0.001F) ? headerHeight : headerHeight + expandedProgress * (sectionsHeight(context, SettingSection.getSections(module), width - (12.5F) * scale) + (5.1F) * scale);
   }

   private ModuleRowRenderer() {
   }

   public static void render(ClickGuiContext context, Module module, float x, float y, float width, float[] clip) {
      float scale = context.scale;
      float enabledProgress = context.animateBoolean("tgl:" + module.name, module.isEnabled());
      java.util.List<com.mouseclient.util.time.SettingSection> sections = SettingSection.getSections(module);
      boolean hasVisibleSettings = SettingSection.hasVisibleSettings(sections);
      float expandedProgress = hasVisibleSettings ? context.animateObjectBoolean(module, module.expanded) : 0.0F;

      for (SettingSection section : sections) {
         for (Setting setting : section.settings) {
            if (context.popupOwner == setting && (!module.expanded || !setting.isVisible() || section.hasHeader() && !context.flagValue(section.expansionKey, false))) {
               context.popupOwner = null;
            }
         }
      }

      float rowHeight = getHeight(context, module, width);
      float backgroundProgress = Math.max(enabledProgress, expandedProgress);
      backgroundProgress = Math.max(
         backgroundProgress,
         (0.35F)
            * context.animateBoolean(
               "row:" + module.name,
               GuiVisuals.containsPoint(context.mouseX, context.mouseY, x, y, width, (19.6F) * scale)
                  && GuiVisuals.containsPoint(context.mouseX, context.mouseY, clip[0], clip[1], clip[2], clip[3])
            )
      );
      if (backgroundProgress > (0.005F)) {
         Render2D.fillRoundedRectangle(
            x,
            y,
            width,
            rowHeight,
            (4.5F) * scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.lerpColor(ClickGuiTheme.disabledModuleBackground, ClickGuiTheme.enabledModuleBackground, enabledProgress), backgroundProgress)
         );
         Render2D.drawOutline(x, y, width, rowHeight, (4.5F) * scale, (0.5F), GuiVisuals.multiplyAlpha(ClickGuiTheme.panelOutline, backgroundProgress));
      }

      context.registerClick(
         x,
         y,
         width,
         (19.6F) * scale,
         clip,
         module::toggle,
         hasVisibleSettings ? () -> module.expanded = !module.expanded : null,
         () -> context.bindingModule = context.bindingModule == module ? null : module
      );
      float toggleX = x + width - (16.1F) * scale;
      float headerCenterY = y + (9.8F) * scale;
      if (module.description != null && !module.description.isEmpty() && GuiVisuals.containsPoint(context.mouseX, context.mouseY, clip[0], clip[1], clip[2], clip[3])) {
         context.offerTooltip(module.description, x + (3.0F) * scale, y + 2.0F * scale, width * (0.6F), (15.6F) * scale);
      }

      float labelWidth = (hasVisibleSettings ? toggleX - (10.5F) * scale : toggleX - 2.0F * scale) - (x + (6.3F) * scale);
      context.drawMarqueeText(
         context.bindingModule == module ? ("Нажми клавишу") : module.name,
         x + (6.3F) * scale,
         y + (5.8F) * scale,
         (7.0F) * scale,
         context.bindingModule == module ? ClickGuiTheme.accent : GuiVisuals.lerpColor(ClickGuiTheme.mutedText, ClickGuiTheme.accent, enabledProgress),
         labelWidth
      );
      if (hasVisibleSettings) {
         float chevronCenterX = toggleX - (6.7F) * scale;
         Render2D.drawRotatedImage(
            ("assets/mouseclient/textures/gui/clickgui/chev.png"),
            chevronCenterX - (2.25F) * scale,
            headerCenterY - (2.25F) * scale,
            (4.5F) * scale,
            0.0F,
            expandedProgress * (180.0F),
            chevronCenterX,
            headerCenterY,
            GuiVisuals.lerpColor(ClickGuiTheme.disabledControlOutline, ClickGuiTheme.enabledControlOutline, Math.max(enabledProgress, expandedProgress))
         );
         context.registerClick(
            chevronCenterX - (3.5F) * scale,
            headerCenterY - (3.5F) * scale,
            (7.0F) * scale,
            (7.0F) * scale,
            clip,
            () -> module.expanded = !module.expanded
         );
      }

      drawToggle(context, module, toggleX, headerCenterY - (3.32F) * scale, clip);
      if (expandedProgress > (0.001F)) {
         float contentY = y + (19.0F) * scale;
         float[] contentClip = ClickGuiContext.intersectClip(clip, x, contentY, width, rowHeight - (19.0F) * scale);
         Render2D.pushScissor(context.drawContext, contentClip[0], contentClip[1], contentClip[2], contentClip[3]);
         Render2D.pushAlpha(expandedProgress);
         float sectionY = contentY;

         for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            sectionY += renderSection(context, (SettingSection)sections.get(sectionIndex), enabledProgress, x + (6.3F) * scale, sectionY, width - (12.5F) * scale, contentClip);
            if (sectionIndex < sections.size() - 1) {
               sectionY += (3.5F) * scale;
            }
         }

         Render2D.popAlpha();
         Render2D.popScissor(context.drawContext);
      }
   }

   private static float sectionContentHeight(ClickGuiContext context, SettingSection section, float width) {
      float scale = context.scale;
      float height = 0.0F;
      boolean firstVisible = true;

      for (Setting setting : section.settings) {
         float visibleProgress = context.animateObjectBoolean(setting, setting.isVisible());
         if (!(visibleProgress < (0.001F))) {
            if (!firstVisible) {
               height += (3.5F) * scale * visibleProgress;
            }

            height += SettingRendererRegistry.getSettingHeight(context, setting, width) * visibleProgress;
            firstVisible = false;
         }
      }

      return height;
   }

   private static float sectionsHeight(ClickGuiContext context, List<SettingSection> sections, float width) {
      float scale = context.scale;
      float height = 0.0F;

      for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
         height += sectionHeight(context, (SettingSection)sections.get(sectionIndex), width);
         if (sectionIndex < sections.size() - 1) {
            height += (3.5F) * scale;
         }
      }

      return height;
   }
}
