package com.mouseclient.render;

import com.mouseclient.gui.Render2D;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.render.pipeline.ItemRenderer;
import com.mouseclient.render.pipeline.ItemRenderSpec;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.util.Identifier;

public final class GuiItemRenderer {
   private static final GuiFont COUNT_FONT = GuiFont.SEMI_BOLD;
   private static boolean reloaderRegistered;

   public static void initialize() {
      registerResourceReloader();
      ClientInitializer.logger().info(("[RenderItem] RenderItem initialized"));
   }

   public static void render(ItemStack item, float x, float y, float size, ItemRenderOptions appearance) {
      ItemRenderSpec spec = new ItemRenderSpec(item, x, y, size, appearance, 0);
      renderer().render(spec);
      drawDecorations(spec);
   }

   private static ItemStack resolveStack(String itemId) {
      if (itemId != null && !itemId.isBlank()) {
         Identifier id = itemId.indexOf(58) >= 0 ? Identifier.tryParse(itemId.trim()) : Identifier.ofVanilla(itemId.trim());
         if (id == null) {
            return ItemStack.EMPTY;
         }

         Item item = Registries.ITEM.getOptionalValue(id).orElse(null);
         return item == null ? ItemStack.EMPTY : item.getDefaultStack();
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static void setDrawContext(DrawContext context) {
      renderer().setDrawContext(context);
   }

   public static void clearFallbackColors() {
      renderer().clearFallbackColors();
   }

   private static void drawCount(int count, float x, float y, float size, float alpha) {
      String countText = Integer.toString(count);
      float fontSize = Math.max((3.0F), Math.round(size * (0.6F) * 2.0F) / 2.0F);
      float textWidth = GuiTextRenderer.getWidth(COUNT_FONT, countText, fontSize);
      float textX = Math.round((x + size - textWidth) * 2.0F) / 2.0F;
      float textY = Math.round((y + size - fontSize) * 2.0F) / 2.0F;
      int textAlpha = Math.round((255.0F) * alpha);
      GuiTextRenderer.draw(COUNT_FONT, countText, textX, textY, fontSize, ArgbColor.argb(255, 255, 255, textAlpha));
   }

   public static void render(ItemStack item, float x, float y, float size) {
      render(item, x, y, size, ItemRenderOptions.defaults());
   }

   private static void drawDurability(ItemStack stack, float x, float y, float size, float alpha) {
      float barWidth = Math.max((8.0F), size * (0.78F));
      float barHeight = Math.max((0.7F), size * (0.04F));
      float barX = x + (size - barWidth) * (0.5F);
      float barY = y + size - barHeight - Math.max((0.35F), size * (0.03F));
      float durabilityFraction = Math.max(0.0F, Math.min(1.0F, stack.getItemBarStep() / (13.0F)));
      int barColor = stack.getItemBarColor();
      Render2D.fillRoundedRectangle(barX, barY, barWidth, barHeight, barHeight * (0.5F), ArgbColor.argb(0, 0, 0, Math.round((150.0F) * alpha)));
      Render2D.fillRoundedRectangle(
         barX,
         barY,
         Math.max(1.0F, barWidth * durabilityFraction),
         barHeight,
         barHeight * (0.5F),
         ArgbColor.argb(barColor >>> 16 & 0xFF, barColor >>> 8 & 0xFF, barColor & 0xFF, Math.round((240.0F) * alpha))
      );
   }

   public static void render(String item, float x, float y, float size) {
      render(resolveStack(item), x, y, size, ItemRenderOptions.defaults());
   }

   public static void render(String item, float x, float y, float size, ItemRenderOptions appearance) {
      render(resolveStack(item), x, y, size, appearance);
   }

   public static void clearCaches() {
      renderer().clearCaches();
   }

   private static ItemRenderer renderer() {
      return ItemRenderer.getInstance();
   }

   public static void clearDrawContext() {
      renderer().clearDrawContext();
   }

   private GuiItemRenderer() {
   }

   private static void registerResourceReloader() {
      if (!reloaderRegistered) {
         Identifier id = Identifier.of(("mouseclient"), ("render_item_cache"));
         ResourceLoader.get(ResourceType.CLIENT_RESOURCES).registerReloader(id, (SynchronousResourceReloader)resourceManager -> clearCaches());
         reloaderRegistered = true;
      }
   }

   public static void close() {
      ItemRenderer.closeInstance();
      ClientInitializer.logger().info(("[RenderItem] RenderItem closed"));
   }

   public static void resetParams() {
      renderer().resetParams();
   }

   public static boolean usesPipeline(RenderPipeline pipeline) {
      return renderer().usesPipeline(pipeline);
   }

   public static void render(ItemStack item, float x, float y, float size, float appearance) {
      render(item, x, y, size, ItemRenderOptions.defaults().alpha(appearance));
   }

   public static int fallbackColor(ItemGuiElementRenderState state) {
      return renderer().fallbackColor(state);
   }

   public static void uploadParams() {
      renderer().uploadParams();
   }

   private static void drawDecorations(ItemRenderSpec spec) {
      if (spec != null && spec.visible()) {
         ItemStack stack = spec.stack();
         ItemRenderOptions options = spec.options();
         if (options.showDurability() && stack.isItemBarVisible()) {
            drawDurability(stack, spec.x(), spec.y(), spec.size(), options.alpha());
         }

         if (options.showCount() && stack.getCount() > 1) {
            drawCount(stack.getCount(), spec.x(), spec.y(), spec.size(), options.alpha());
         }
      }
   }

   public static void captureFallbackColor(ItemGuiElementRenderState state) {
      renderer().captureFallbackColor(state);
   }

   public static void bindUniforms(RenderPass renderPass) {
      renderer().bindUniforms(renderPass);
   }
}
