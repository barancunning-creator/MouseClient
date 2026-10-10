package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.RoundedRectangleRenderState;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.RoundedClipStack;
import com.mouseclient.mixin.DrawContextAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryUtil;

public final class RoundedRectangleRenderer implements AutoCloseable {

   private static volatile RoundedRectangleRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/rect_default")))
      .withVertexShader(id(("core/rect_default")))
      .withFragmentShader(id(("core/rect_default")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("RectangleParamsArray"), UniformType.UNIFORM_BUFFER)
      .withUniform(("RectangleParamsArray2"), UniformType.UNIFORM_BUFFER)
      .withUniform(("PaletteParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<RoundedRectangleSpec> params = new ArrayList<>(128);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private ByteBuffer stagingBuffer;
   private boolean paramsDirty = true;

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   public void end() {
      this.drawContext = null;
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   public static RoundedRectangleRenderer getInstance() {
      RoundedRectangleRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (RoundedRectangleRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new RoundedRectangleRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   private static void putColor(ByteBuffer packedParams, int byteOffset, int color) {
      packedParams.putFloat(byteOffset, (color >>> 16 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 4, (color >>> 8 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 8, (color & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 12, (color >>> 24 & 0xFF) / (255.0F));
   }

   public void render(DrawContext context, RoundedRectangleSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   public static void closeInstance() {
      RoundedRectangleRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   public int allocateParams(RoundedRectangleSpec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 1024) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return paramsIndex;
   }

   public void uploadParams() {
      if (!this.params.isEmpty() && this.paramsDirty) {
         GpuBuffer buffer = this.ensureBuffer();
         if (buffer != null) {
            ByteBuffer packedParams = this.packParams(this.params);
            if (packedParams != null && packedParams.remaining() > 0) {
               try {
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, packedParams.remaining()), packedParams);
                  this.paramsDirty = false;
               } catch (RuntimeException error) {
                  this.paramsDirty = true;
               }
            }
         }
      }
   }

   public void render(RoundedRectangleSpec spec) {
      this.submit(this.drawContext, spec);
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private RoundedRectangleRenderer() {
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (131072L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_rect_default_params"), 136, (131072L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private void submit(DrawContext context, RoundedRectangleSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            RoundedRectangleSpec normalizedSpec = this.normalize(spec);
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new RoundedRectangleRenderState(transform, normalizedSpec, ScissorStack.current()));
         } catch (RuntimeException error) {
         }
      }
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   private GpuBuffer getUploadedBuffer() {
      if (!this.paramsDirty && this.paramsBuffer != null) {
         return this.paramsBuffer;
      }

      this.uploadParams();
      if (!this.paramsDirty && this.paramsBuffer != null) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();
      this.uploadParams();
      return this.paramsBuffer;
   }

   private RoundedRectangleSpec normalize(RoundedRectangleSpec spec) {
      float maximumRadius = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      float scissorX = 0.0F;
      float scissorY = 0.0F;
      float scissorWidth = 0.0F;
      float scissorHeight = 0.0F;
      float scissorRadiusTopLeft = 0.0F;
      float scissorRadiusTopRight = 0.0F;
      float scissorRadiusBottomRight = 0.0F;
      float scissorRadiusBottomLeft = 0.0F;
      if (RoundedClipStack.isActive()) {
         scissorX = RoundedClipStack.getX();
         scissorY = RoundedClipStack.getY();
         scissorWidth = RoundedClipStack.getWidth();
         scissorHeight = RoundedClipStack.getHeight();
         scissorRadiusTopLeft = RoundedClipStack.getTopLeftRadius();
         scissorRadiusTopRight = RoundedClipStack.getTopRightRadius();
         scissorRadiusBottomRight = RoundedClipStack.getBottomRightRadius();
         scissorRadiusBottomLeft = RoundedClipStack.getBottomLeftRadius();
      } else {
         scissorX = spec.scissorX();
         scissorY = spec.scissorY();
         scissorWidth = spec.scissorWidth();
         scissorHeight = spec.scissorHeight();
         scissorRadiusTopLeft = spec.scissorRadiusTopLeft();
         scissorRadiusTopRight = spec.scissorRadiusTopRight();
         scissorRadiusBottomRight = spec.scissorRadiusBottomRight();
         scissorRadiusBottomLeft = spec.scissorRadiusBottomLeft();
      }

      return new RoundedRectangleSpec(
         Math.round(spec.x() * GuiScale.getBaseScale()) / GuiScale.getBaseScale(),
         Math.round(spec.y() * GuiScale.getBaseScale()) / GuiScale.getBaseScale(),
         Math.max(
            (Math.round((spec.x() + spec.width()) * GuiScale.getBaseScale()) - Math.round(spec.x() * GuiScale.getBaseScale()))
               / GuiScale.getBaseScale(),
            1.0F / GuiScale.getBaseScale()
         ),
         Math.max(
            (Math.round((spec.y() + spec.height()) * GuiScale.getBaseScale()) - Math.round(spec.y() * GuiScale.getBaseScale()))
               / GuiScale.getBaseScale(),
            1.0F / GuiScale.getBaseScale()
         ),
         clamp(spec.radiusTopLeft(), 0.0F, maximumRadius),
         clamp(spec.radiusTopRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomLeft(), 0.0F, maximumRadius),
         spec.colorTopLeft(),
         spec.colorTopRight(),
         spec.colorBottomRight(),
         spec.colorBottomLeft(),
         Math.max(spec.smoothness(), 0.0F),
         scissorX,
         scissorY,
         scissorWidth,
         scissorHeight,
         scissorRadiusTopLeft,
         scissorRadiusTopRight,
         scissorRadiusBottomRight,
         scissorRadiusBottomLeft,
         spec.paletteMode(),
         spec.paletteTint(),
         spec.paletteAlpha()
      );
   }

   private ByteBuffer packParams(List<RoundedRectangleSpec> specs) {
      int byteCount = Math.min((131072), specs.size() * 8 * 4 * 4);
      if (byteCount <= 0) {
         return null;
      }

      if (this.stagingBuffer == null) {
         this.stagingBuffer = MemoryUtil.memCalloc((131072));
      }

      ByteBuffer packedParams = this.stagingBuffer;
      packedParams.clear();
      MemoryUtil.memSet(MemoryUtil.memAddress(packedParams), 0, byteCount);

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         RoundedRectangleSpec spec = (RoundedRectangleSpec)specs.get(specIndex);
         int byteOffset = specIndex * 8 * 4 * 4;
         packedParams.putFloat(byteOffset, spec.radiusTopLeft());
         packedParams.putFloat(byteOffset + 4, spec.radiusTopRight());
         packedParams.putFloat(byteOffset + 8, spec.radiusBottomRight());
         packedParams.putFloat(byteOffset + 12, spec.radiusBottomLeft());
         packedParams.putFloat(byteOffset + 16, spec.width());
         packedParams.putFloat(byteOffset + 20, spec.height());
         packedParams.putFloat(byteOffset + 24, spec.smoothness());
         packedParams.putFloat(byteOffset + 28, spec.paletteMode());
         if (spec.paletteMode() != 0) {
            packedParams.putFloat(byteOffset + 32, spec.paletteTint());
            packedParams.putFloat(byteOffset + 36, spec.paletteAlpha());
         } else {
            putColor(packedParams, byteOffset + 32, spec.colorTopLeft());
            putColor(packedParams, byteOffset + 48, spec.colorTopRight());
            putColor(packedParams, byteOffset + 64, spec.colorBottomRight());
            putColor(packedParams, byteOffset + 80, spec.colorBottomLeft());
         }

         float baseScale = GuiScale.getBaseScale();
         float framebufferHeight = GuiScale.getFramebufferHeight();
         packedParams.putFloat(byteOffset + 96, spec.scissorX() * baseScale);
         packedParams.putFloat(byteOffset + 100, framebufferHeight - (spec.scissorY() + spec.scissorHeight()) * baseScale);
         packedParams.putFloat(byteOffset + 104, spec.scissorWidth() * baseScale);
         packedParams.putFloat(byteOffset + 108, spec.scissorHeight() * baseScale);
         packedParams.putFloat(byteOffset + 112, spec.scissorRadiusBottomLeft() * baseScale);
         packedParams.putFloat(byteOffset + 116, spec.scissorRadiusBottomRight() * baseScale);
         packedParams.putFloat(byteOffset + 120, spec.scissorRadiusTopRight() * baseScale);
         packedParams.putFloat(byteOffset + 124, spec.scissorRadiusTopLeft() * baseScale);
      }

      packedParams.limit(byteCount).position(0);
      return packedParams;
   }

   @Override
   public void close() {
      this.params.clear();
      this.drawContext = null;
      this.releaseBuffer();
      if (this.stagingBuffer != null) {
         MemoryUtil.memFree(this.stagingBuffer);
         this.stagingBuffer = null;
      }
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("RectangleParamsArray"), buffer.slice(0L, (65536L)));
            renderPass.setUniform(("RectangleParamsArray2"), buffer.slice((65536L), (65536L)));
         }

         GpuBuffer paletteBuffer = MouseClientClientPalette.buffer();
         if (paletteBuffer != null) {
            renderPass.setUniform(("PaletteParams"), paletteBuffer);
         }
      }
   }
}
