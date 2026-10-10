package com.mouseclient.modules.impl.misc;

import com.mouseclient.gui.ModuleRowRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.SmoothScrollState;
import com.mouseclient.internal.RoundedClipStack;
import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.ClickGuiTheme;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

public final class ModuleCategoryRenderer {
   private static final Map<Module, ModuleCategoryRenderer.SearchEntry> searchEntries = new IdentityHashMap<>();

   public static void render(ClickGuiContext context, ModuleCategory category, SmoothScrollState scroll, float x, float y, float width, float height, float deltaSeconds) {
      float scale = context.scale;
      float viewportY = y + (21.0F) * scale;
      Render2D.drawSharedBlur(
         x + (0.5F) * scale, y + (0.5F) * scale, width - 1.0F * scale, height - 1.0F * scale, (8.0F) * scale, (30.0F), 1.0F, -1
      );
      Render2D.fillRoundedRectangle(x, y, width, height, (8.5F) * scale, ClickGuiTheme.panelBackground);
      Render2D.drawOutline(x, y, width, height, (8.5F) * scale, (0.5F), ClickGuiTheme.panelOutline);
      GuiTextRenderer.draw(
         GuiFont.INSTANCE,
         category.displayName().toUpperCase(Locale.ROOT),
         x + (11.0F) * scale,
         y + (9.0F) * scale,
         (6.3F) * scale,
         ClickGuiTheme.headerText
      );
      Render2D.drawRoundedImage(
         ClickGuiTheme.categoryIcon(category),
         x + width - (17.3F) * scale,
         y + (9.4F) * scale,
         (6.4F) * scale,
         (6.4F) * scale,
         0.0F,
         ClickGuiTheme.headerText
      );
      scroll.update(deltaSeconds);
      float rowY = y + (26.5F) * scale - scroll.getScroll();
      Render2D.pushScissor(context.drawContext, x, viewportY, width, height - (21.0F) * scale);
      RoundedClipStack.push(x, viewportY, width, height - (21.0F) * scale, 0.0F, 0.0F, (8.5F) * scale, (8.5F) * scale);
      float[] viewportClip = null;

      for (Module module : ModuleRegistry.byCategory(category)) {
         ModuleCategoryRenderer.SearchEntry searchMetadata = searchEntry(module);
         float searchProgress = context.animateValue(
            searchMetadata.searchKey, !context.searchText.isEmpty() && !searchMetadata.lowerName.contains(context.searchText) && !searchMetadata.lowerDescription.contains(context.searchText) ? 0.0F : 1.0F
         );
         if (!(searchProgress < (0.01F))) {
            if (searchProgress < (0.999F)) {
               Render2D.pushAlpha(searchProgress * searchProgress);
            }

            float rowHeight = ModuleRowRenderer.getHeight(context, module, width - (9.2F) * scale);
            if (rowY + rowHeight >= viewportY - 2.0F && rowY <= viewportY + (height - (21.0F) * scale) + 2.0F) {
               if (viewportClip == null) {
                  viewportClip = new float[]{x, viewportY, width, height - (21.0F) * scale};
               }

               ModuleRowRenderer.render(context, module, x + (4.6F) * scale, rowY, width - (9.2F) * scale, viewportClip);
            }

            if (searchProgress < (0.999F)) {
               Render2D.popAlpha();
            }

            rowY += (rowHeight + (1.5F) * scale) * searchProgress;
         }
      }

      RoundedClipStack.pop();
      Render2D.popScissor(context.drawContext);
      scroll.maxScroll = Math.max(0.0F, rowY - y + scroll.getScroll() - height + (4.5F) * scale);
   }

   private ModuleCategoryRenderer() {
   }

   private static ModuleCategoryRenderer.SearchEntry searchEntry(Module module) {
      ModuleCategoryRenderer.SearchEntry cachedEntry = searchEntries.get(module);
      if (cachedEntry == null || cachedEntry.name != module.name || cachedEntry.description != module.description) {
         String searchDescription = module.description == null ? "" : module.description;
         cachedEntry = new ModuleCategoryRenderer.SearchEntry(module.name, module.description, module.name.toLowerCase(), searchDescription.toLowerCase(), "search." + module.name);
         searchEntries.put(module, cachedEntry);
      }

      return cachedEntry;
   }

   private record SearchEntry(String name, String description, String lowerName, String lowerDescription, String searchKey) {
   }
}
