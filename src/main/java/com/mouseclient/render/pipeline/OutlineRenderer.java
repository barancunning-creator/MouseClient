package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.OutlineRenderState;
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

public final class OutlineRenderer implements AutoCloseable {

   private static volatile OutlineRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/outline_default")))
      .withVertexShader(id(("core/outline_default")))
      .withFragmentShader(id(("core/outline_default")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("OutlineParamsArray"), UniformType.UNIFORM_BUFFER)
      .withUniform(("OutlineParamsArray2"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<OutlineSpec> params = new ArrayList<>(128);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private ByteBuffer stagingBuffer;
   private boolean paramsDirty = true;

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("OutlineParamsArray"), buffer.slice(0L, (65536L)));
            renderPass.setUniform(("OutlineParamsArray2"), buffer.slice((65536L), (65536L)));
         }
      }
   }

   private static void putColor(ByteBuffer packedParams, int byteOffset, int color) {
      packedParams.putFloat(byteOffset, (color >>> 16 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 4, (color >>> 8 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 8, (color & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 12, (color >>> 24 & 0xFF) / (255.0F));
   }

   private void submit(DrawContext context, OutlineSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            OutlineSpec normalizedSpec = this.normalize(spec);
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new OutlineRenderState(transform, normalizedSpec, ScissorStack.current()));
         } catch (RuntimeException error) {
         }
      }
   }

   private OutlineRenderer() {
   }

   public void render(OutlineSpec spec) {
      this.submit(this.drawContext, spec);
   }

   private OutlineSpec normalize(OutlineSpec spec) {
      float maximumRadius = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      float maximumThickness = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      float[] roundedClip = null;
      if (RoundedClipStack.isActive()) {
         roundedClip = new float[]{
            RoundedClipStack.getX(),
            RoundedClipStack.getY(),
            RoundedClipStack.getWidth(),
            RoundedClipStack.getHeight(),
            RoundedClipStack.getTopLeftRadius(),
            RoundedClipStack.getTopRightRadius(),
            RoundedClipStack.getBottomRightRadius(),
            RoundedClipStack.getBottomLeftRadius()
         };
      }

      return new OutlineSpec(
         spec.x(),
         spec.y(),
         spec.width(),
         spec.height(),
         clamp(spec.radiusTopLeft(), 0.0F, maximumRadius),
         clamp(spec.radiusTopRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomLeft(), 0.0F, maximumRadius),
         clamp(spec.thickness(), 0.0F, maximumThickness),
         spec.colorTopLeft(),
         spec.colorTopRight(),
         spec.colorBottomRight(),
         spec.colorBottomLeft(),
         Math.max(spec.smoothness(), 0.0F),
         roundedClip
      );
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   public static void closeInstance() {
      OutlineRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   public void render(DrawContext context, OutlineSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   public int allocateParams(OutlineSpec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 1024) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return paramsIndex;
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

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
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

   public static OutlineRenderer getInstance() {
      OutlineRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (OutlineRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new OutlineRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (131072L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_outline_default_params"), 136, (131072L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private ByteBuffer packParams(List<OutlineSpec> specs) {
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
         OutlineSpec spec = (OutlineSpec)specs.get(specIndex);
         int byteOffset = specIndex * 8 * 4 * 4;
         packedParams.putFloat(byteOffset, spec.radiusTopLeft());
         packedParams.putFloat(byteOffset + 4, spec.radiusTopRight());
         packedParams.putFloat(byteOffset + 8, spec.radiusBottomRight());
         packedParams.putFloat(byteOffset + 12, spec.radiusBottomLeft());
         packedParams.putFloat(byteOffset + 16, spec.width());
         packedParams.putFloat(byteOffset + 20, spec.height());
         packedParams.putFloat(byteOffset + 24, spec.thickness());
         packedParams.putFloat(byteOffset + 28, spec.smoothness());
         putColor(packedParams, byteOffset + 32, spec.colorTopLeft());
         putColor(packedParams, byteOffset + 48, spec.colorTopRight());
         putColor(packedParams, byteOffset + 64, spec.colorBottomRight());
         putColor(packedParams, byteOffset + 80, spec.colorBottomLeft());
         float[] scissor = spec.scissor();
         if (scissor != null) {
            float baseScale = GuiScale.getBaseScale();
            float framebufferHeight = GuiScale.getFramebufferHeight();
            packedParams.putFloat(byteOffset + 96, scissor[0] * baseScale);
            packedParams.putFloat(byteOffset + 100, framebufferHeight - (scissor[1] + scissor[3]) * baseScale);
            packedParams.putFloat(byteOffset + 104, scissor[2] * baseScale);
            packedParams.putFloat(byteOffset + 108, scissor[3] * baseScale);
            packedParams.putFloat(byteOffset + 112, scissor[7] * baseScale);
            packedParams.putFloat(byteOffset + 116, scissor[6] * baseScale);
            packedParams.putFloat(byteOffset + 120, scissor[5] * baseScale);
            packedParams.putFloat(byteOffset + 124, scissor[4] * baseScale);
         }
      }

      packedParams.limit(byteCount).position(0);
      return packedParams;
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

   public void end() {
      this.drawContext = null;
   }
}
