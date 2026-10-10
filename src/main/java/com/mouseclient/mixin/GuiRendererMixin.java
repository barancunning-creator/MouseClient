package com.mouseclient.mixin;

import com.mouseclient.internal.RenderPipelineWarmup;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.avatar.MsdfFontRegistry;
import com.mouseclient.render.pipeline.ArcRenderer;
import com.mouseclient.render.pipeline.BlobRenderer;
import com.mouseclient.render.pipeline.BracketRenderer;
import com.mouseclient.render.pipeline.CircleRenderer;
import com.mouseclient.render.pipeline.GuiBlurRenderer;
import com.mouseclient.render.pipeline.MouseClientClientPalette;
import com.mouseclient.render.pipeline.GuiShapeSplitBuffer;
import com.mouseclient.render.pipeline.GuiGlowRenderer;
import com.mouseclient.render.pipeline.GuiShapeBlurCache;
import com.mouseclient.render.pipeline.GlassOutlineRenderer;
import com.mouseclient.render.pipeline.GlassRenderer;
import com.mouseclient.render.pipeline.ImageRenderer;
import com.mouseclient.render.pipeline.LineRenderer;
import com.mouseclient.render.pipeline.MenuGridRenderer;
import com.mouseclient.render.pipeline.Outline360Renderer;
import com.mouseclient.render.pipeline.OutlineRenderer;
import com.mouseclient.render.pipeline.ColorPickerRenderer;
import com.mouseclient.render.pipeline.MsdfTextRenderer;
import com.mouseclient.render.pipeline.RectHalfIconRenderer;
import com.mouseclient.render.pipeline.RoundedRectangleRenderer;
import com.mouseclient.render.pipeline.RippleRenderer;
import com.mouseclient.render.pipeline.GuiShapePipelines;
import com.mouseclient.render.pipeline.GlassShapeRenderer;
import com.mouseclient.render.pipeline.ShimmerRenderer;
import com.mouseclient.render.pipeline.SoftGlowRenderer;
import com.mouseclient.render.pipeline.WarpTextRenderer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.texture.TextureSetup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
   @Shadow
   @Final
   private List<?> preparations;
   @Shadow
   @Final
   private List<?> draws;
   @Shadow
   private int blurLayer;
   private final IntArrayList shapeBlurMarkers = new IntArrayList();
   private final IntArrayList shapeBlurMarkerSlots = new IntArrayList();
   private RenderPass currentRenderPass;
   private boolean blurDrawActive;
   private boolean glassDrawActive;
   private boolean shapeDrawActive;
   private boolean glowDrawActive;
   private boolean softGlowDrawActive;
   private boolean glassOutlineDrawActive;
   private boolean circleDrawActive;
   private boolean bracketDrawActive;
   private boolean blobDrawActive;
   private boolean arcDrawActive;
   private boolean pickerDrawActive;
   private boolean rectangleDrawActive;
   private boolean halfIconRectangleDrawActive;
   private boolean outlineDrawActive;
   private boolean outline360DrawActive;
   private boolean imageDrawActive;
   private boolean lineDrawActive;
   private boolean itemDrawActive;
   private boolean rippleDrawActive;
   private boolean shimmerDrawActive;
   private boolean menuGridDrawActive;
   private boolean warpTextDrawActive;

   public @Invoker("render")
   abstract void mouseclient$executeDrawRange(
      Supplier<String> nameSupplier, Framebuffer framebuffer, GpuBufferSlice fogBuffer, GpuBufferSlice dynamicTransformsBuffer, GpuBuffer buffer, IndexType indexType, int from, int to
   );

   @Inject(method = "endBuffer(Lnet/minecraft/client/render/BufferBuilder;Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;Lnet/minecraft/client/gui/ScreenRect;)V", at = @At("HEAD"))
   private void trackShapeBlurMarker(BufferBuilder builder, RenderPipeline pipeline, TextureSetup textureSetup, ScreenRect bounds, CallbackInfo callback) {
      GuiBlurRenderer.getInstance().trackHeavyDraw(pipeline, textureSetup, this.preparations.size(), this.blurLayer);
      if (GuiShapePipelines.includesGuiBlur(pipeline)) {
         this.shapeBlurMarkers.add(this.preparations.size());
         this.shapeBlurMarkerSlots.add(textureSetup == null ? -1 : GuiShapeBlurCache.INSTANCE.findRegionForTexture(textureSetup.texure0()));
      }
   }

   @Redirect(
      method = "renderPreparedDraws(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Ljava/util/function/Supplier;Lnet/minecraft/client/gl/Framebuffer;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;II)V"
      )
   )
   private void splitShapeBlurRanges(
      GuiRenderer renderer, Supplier<String> nameSupplier, Framebuffer framebuffer, GpuBufferSlice fogBuffer, GpuBufferSlice dynamicTransformsBuffer, GpuBuffer buffer, IndexType indexType, int from, int to
   ) {
      if (!this.shapeBlurMarkers.isEmpty() && from < to) {
         int rangeStart = from;

         for (int markerIndex = 0; markerIndex < this.shapeBlurMarkers.size(); markerIndex++) {
            int markerDraw = this.shapeBlurMarkers.getInt(markerIndex);
            if (markerDraw >= from && markerDraw < to) {
               int regionIndex = this.shapeBlurMarkerSlots.getInt(markerIndex);
               if (regionIndex >= 0 && GuiShapeBlurCache.INSTANCE.isRegionDirty(regionIndex)) {
                  if (markerDraw > rangeStart) {
                     this.mouseclient$executeDrawRange(nameSupplier, framebuffer, fogBuffer, dynamicTransformsBuffer, buffer, indexType, rangeStart, markerDraw);
                  }

                  GuiShapeBlurCache.INSTANCE.updateRegion(regionIndex, framebuffer);
                  rangeStart = markerDraw;
               }
            }
         }

         if (rangeStart < to) {
            this.mouseclient$executeDrawRange(nameSupplier, framebuffer, fogBuffer, dynamicTransformsBuffer, buffer, indexType, rangeStart, to);
         }
      } else {
         this.mouseclient$executeDrawRange(nameSupplier, framebuffer, fogBuffer, dynamicTransformsBuffer, buffer, indexType, from, to);
      }
   }

   @Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"))
   private void beginBlurFrame(CallbackInfo callback) {
      int remainingWarmups = MinecraftClient.getInstance().getOverlay() == null ? 1 : 8;

      // ShaderLoader has not installed its sources while the reload overlay is active.
      while (MinecraftClient.getInstance().getOverlay() == null && remainingWarmups > 0 && this.warmNextUnit()) {
         remainingWarmups--;
      }

      GuiBlurRenderer.getInstance().beginFrame();
      GlassRenderer.getInstance().clearParams();
      GlassShapeRenderer.getInstance().clearParams();
      GuiGlowRenderer.getInstance().beginFrame();
      SoftGlowRenderer.getInstance().clearParams();
      GlassOutlineRenderer.getInstance().clearParams();
      CircleRenderer.getInstance().clearParams();
      BracketRenderer.getInstance().clearParams();
      BlobRenderer.getInstance().clearParams();
      ArcRenderer.getInstance().clearParams();
      ColorPickerRenderer.getInstance().clearParams();
      RoundedRectangleRenderer.getInstance().clearParams();
      RectHalfIconRenderer.getInstance().clearParams();
      OutlineRenderer.getInstance().clearParams();
      Outline360Renderer.getInstance().clearParams();
      ImageRenderer.getInstance().beginFrame();
      LineRenderer.getInstance().clearParams();
      RippleRenderer.getInstance().clearParams();
      ShimmerRenderer.getInstance().clearParams();
      MenuGridRenderer.getInstance().clearParams();
      WarpTextRenderer.getInstance().clearParams();
      MsdfTextRenderer.getInstance().beginFrame();
      GuiItemRenderer.resetParams();
   }

   private boolean warmNextUnit() {
      if (MsdfFontRegistry.warmNextFont()) {
         return true;
      } else {
         return MsdfTextRenderer.getInstance().warmupNextFont() ? true : RenderPipelineWarmup.precompileNext();
      }
   }

   @Inject(method = "prepare()V", at = @At("HEAD"))
   private void preparePendingBlurResources(CallbackInfo callback) {
      this.shapeBlurMarkers.clear();
      this.shapeBlurMarkerSlots.clear();
      GuiShapeSplitBuffer.upload();
      GuiBlurRenderer.getInstance().prepareRequests();
      GuiGlowRenderer.getInstance().prepareAtlas();
   }

   @Inject(method = "prepare()V", at = @At("RETURN"))
   private void prepareRenderUniforms(CallbackInfo callback) {
      MouseClientClientPalette.updateAndUpload();
      GuiBlurRenderer.getInstance().uploadParams();
      GlassRenderer.getInstance().uploadParams();
      GlassShapeRenderer.getInstance().uploadParams();
      GuiGlowRenderer.getInstance().uploadParams();
      SoftGlowRenderer.getInstance().uploadParams();
      GlassOutlineRenderer.getInstance().uploadParams();
      CircleRenderer.getInstance().uploadParams();
      BracketRenderer.getInstance().uploadParams();
      BlobRenderer.getInstance().uploadParams();
      ArcRenderer.getInstance().uploadParams();
      ColorPickerRenderer.getInstance().uploadParams();
      RoundedRectangleRenderer.getInstance().uploadParams();
      RectHalfIconRenderer.getInstance().uploadParams();
      OutlineRenderer.getInstance().uploadParams();
      Outline360Renderer.getInstance().uploadParams();
      ImageRenderer.getInstance().uploadParams();
      LineRenderer.getInstance().uploadParams();
      RippleRenderer.getInstance().uploadParams();
      ShimmerRenderer.getInstance().uploadParams();
      MenuGridRenderer.getInstance().uploadParams();
      WarpTextRenderer.getInstance().uploadParams();
      GuiItemRenderer.uploadParams();
   }

   @Inject(method = "renderPreparedDraws(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"))
   private void prepareBlurCapture(CallbackInfo callback) {
      GuiBlurRenderer.getInstance().processRequests(this.blurLayer, this.draws.size());
      GuiShapeBlurCache.INSTANCE.updateSharedBlur();
   }

   @Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("RETURN"))
   private void clearSpecialItemTints(CallbackInfo callback) {
      GuiItemRenderer.clearFallbackColors();
      GuiShapeBlurCache.INSTANCE.endFrame();
   }

   @Redirect(method = "prepareItem(Lnet/minecraft/client/gui/render/state/ItemGuiElementRenderState;FFII)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;addSimpleElementToCurrentLayer(Lnet/minecraft/client/gui/render/state/TexturedQuadGuiElementRenderState;)V"))
   private void tintItemAtlasBlit(
      GuiRenderState renderState, TexturedQuadGuiElementRenderState quad, ItemGuiElementRenderState state, float u, float v, int pixelsPerItem, int itemAtlasSideLength
   ) {
      renderState.addSimpleElementToCurrentLayer(
         GuiItemRenderer.fallbackColor(state) == -1
            ? quad
            : new TexturedQuadGuiElementRenderState(
               quad.pipeline(),
               quad.textureSetup(),
               quad.pose(),
               quad.x1(),
               quad.y1(),
               quad.x2(),
               quad.y2(),
               quad.u1(),
               quad.u2(),
               quad.v1(),
               quad.v2(),
               GuiItemRenderer.fallbackColor(state),
               quad.scissorArea(),
               quad.bounds()
            )
      );
   }

   @Redirect(
      method = "render(Lnet/minecraft/client/gui/render/GuiRenderer$Draw;Lcom/mojang/blaze3d/systems/RenderPass;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V")
   )
   private void trackPipeline(RenderPass currentRenderPass, RenderPipeline pipeline) {
      if (!pipeline.getLocation().getNamespace().equals(("mouseclient"))) {
         this.currentRenderPass = null;
         currentRenderPass.setPipeline(pipeline);
      } else {
         this.currentRenderPass = currentRenderPass;
         this.blurDrawActive = GuiBlurRenderer.getInstance().usesPipeline(pipeline);
         this.glassDrawActive = GlassRenderer.getInstance().usesPipeline(pipeline);
         this.shapeDrawActive = GlassShapeRenderer.getInstance().usesPipeline(pipeline);
         this.glowDrawActive = GuiGlowRenderer.getInstance().usesPipeline(pipeline);
         this.softGlowDrawActive = SoftGlowRenderer.getInstance().usesPipeline(pipeline);
         this.glassOutlineDrawActive = GlassOutlineRenderer.getInstance().usesPipeline(pipeline);
         this.circleDrawActive = CircleRenderer.getInstance().usesPipeline(pipeline);
         this.bracketDrawActive = BracketRenderer.getInstance().usesPipeline(pipeline);
         this.blobDrawActive = BlobRenderer.getInstance().usesPipeline(pipeline);
         this.arcDrawActive = ArcRenderer.getInstance().usesPipeline(pipeline);
         this.pickerDrawActive = ColorPickerRenderer.getInstance().usesPipeline(pipeline);
         this.rectangleDrawActive = RoundedRectangleRenderer.getInstance().usesPipeline(pipeline);
         this.halfIconRectangleDrawActive = RectHalfIconRenderer.getInstance().usesPipeline(pipeline);
         this.outlineDrawActive = OutlineRenderer.getInstance().usesPipeline(pipeline);
         this.outline360DrawActive = Outline360Renderer.getInstance().usesPipeline(pipeline);
         this.imageDrawActive = ImageRenderer.getInstance().usesPipeline(pipeline);
         this.lineDrawActive = LineRenderer.getInstance().usesPipeline(pipeline);
         this.rippleDrawActive = RippleRenderer.getInstance().usesPipeline(pipeline);
         this.shimmerDrawActive = ShimmerRenderer.getInstance().usesPipeline(pipeline);
         this.menuGridDrawActive = MenuGridRenderer.getInstance().usesPipeline(pipeline);
         this.warpTextDrawActive = WarpTextRenderer.getInstance().usesPipeline(pipeline);
         this.itemDrawActive = GuiItemRenderer.usesPipeline(pipeline);
         currentRenderPass.setPipeline(pipeline);
      }
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/render/GuiRenderer$Draw;Lcom/mojang/blaze3d/systems/RenderPass;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V", shift = Shift.BEFORE))
   private void bindBlurParams(CallbackInfo callback) {
      if (this.blurDrawActive && this.currentRenderPass != null) {
         GuiBlurRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.glassDrawActive && this.currentRenderPass != null) {
         GlassRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.shapeDrawActive && this.currentRenderPass != null) {
         GlassShapeRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.glowDrawActive && this.currentRenderPass != null) {
         GuiGlowRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.softGlowDrawActive && this.currentRenderPass != null) {
         SoftGlowRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.glassOutlineDrawActive && this.currentRenderPass != null) {
         GlassOutlineRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.circleDrawActive && this.currentRenderPass != null) {
         CircleRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.bracketDrawActive && this.currentRenderPass != null) {
         BracketRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.blobDrawActive && this.currentRenderPass != null) {
         BlobRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.arcDrawActive && this.currentRenderPass != null) {
         ArcRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.pickerDrawActive && this.currentRenderPass != null) {
         ColorPickerRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.rectangleDrawActive && this.currentRenderPass != null) {
         RoundedRectangleRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.halfIconRectangleDrawActive && this.currentRenderPass != null) {
         RectHalfIconRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.outlineDrawActive && this.currentRenderPass != null) {
         OutlineRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.outline360DrawActive && this.currentRenderPass != null) {
         Outline360Renderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.imageDrawActive && this.currentRenderPass != null) {
         ImageRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.lineDrawActive && this.currentRenderPass != null) {
         LineRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.rippleDrawActive && this.currentRenderPass != null) {
         RippleRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.shimmerDrawActive && this.currentRenderPass != null) {
         ShimmerRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.menuGridDrawActive && this.currentRenderPass != null) {
         MenuGridRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.warpTextDrawActive && this.currentRenderPass != null) {
         WarpTextRenderer.getInstance().bindUniforms(this.currentRenderPass);
      }

      if (this.itemDrawActive && this.currentRenderPass != null) {
         GuiItemRenderer.bindUniforms(this.currentRenderPass);
      }
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/render/GuiRenderer$Draw;Lcom/mojang/blaze3d/systems/RenderPass;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V", at = @At("RETURN"))
   private void clearTrackedPipeline(CallbackInfo callback) {
      this.currentRenderPass = null;
      this.blurDrawActive = false;
      this.glassDrawActive = false;
      this.shapeDrawActive = false;
      this.glowDrawActive = false;
      this.softGlowDrawActive = false;
      this.glassOutlineDrawActive = false;
      this.circleDrawActive = false;
      this.bracketDrawActive = false;
      this.blobDrawActive = false;
      this.arcDrawActive = false;
      this.pickerDrawActive = false;
      this.rectangleDrawActive = false;
      this.halfIconRectangleDrawActive = false;
      this.outlineDrawActive = false;
      this.outline360DrawActive = false;
      this.imageDrawActive = false;
      this.lineDrawActive = false;
      this.itemDrawActive = false;
      this.rippleDrawActive = false;
      this.shimmerDrawActive = false;
      this.menuGridDrawActive = false;
      this.warpTextDrawActive = false;
   }

   static {
      VMBridge.identifyClass(GuiRendererMixin.class, "ukilqNNd");
   }
}
