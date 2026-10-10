package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.SoftGlowRenderState;
import com.mouseclient.internal.GuiScale;
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
import org.lwjgl.system.MemoryStack;

public final class SoftGlowRenderer implements AutoCloseable {

   private static volatile SoftGlowRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/soft_glow")))
      .withVertexShader(id(("core/soft_glow")))
      .withFragmentShader(id(("core/soft_glow")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("SoftGlowParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<SoftGlowSpec> params = new ArrayList<>(64);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty = true;

   public static void closeInstance() {
      SoftGlowRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   public static SoftGlowRenderer getInstance() {
      SoftGlowRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (SoftGlowRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new SoftGlowRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   private void submit(DrawContext context, SoftGlowSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new SoftGlowRenderState(transform, spec, ScissorStack.current()));
         } catch (RuntimeException error) {
         }
      }
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   private ByteBuffer packParams(MemoryStack stack, List<SoftGlowSpec> specs) {
      ByteBuffer packedParams = stack.calloc(Math.min(10240, specs.size() * 5 * 4 * 4));

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         SoftGlowSpec spec = (SoftGlowSpec)specs.get(specIndex);
         int byteOffset = specIndex * 5 * 4 * 4;
         float halfWidth = Math.max(spec.width() / 2.0F, 0.0F);
         float halfHeight = Math.max(spec.height() / 2.0F, 0.0F);
         float maximumRadius = Math.min(halfWidth, halfHeight);
         packedParams.putFloat(byteOffset, halfWidth);
         packedParams.putFloat(byteOffset + 4, halfHeight);
         packedParams.putFloat(byteOffset + 8, spec.pad());
         packedParams.putFloat(byteOffset + 12, Math.max(spec.softness(), (1.0E-4F)));
         packedParams.putFloat(byteOffset + 16, clamp(spec.radiusTopLeft(), 0.0F, maximumRadius));
         packedParams.putFloat(byteOffset + 20, clamp(spec.radiusTopRight(), 0.0F, maximumRadius));
         packedParams.putFloat(byteOffset + 24, clamp(spec.radiusBottomRight(), 0.0F, maximumRadius));
         packedParams.putFloat(byteOffset + 28, clamp(spec.radiusBottomLeft(), 0.0F, maximumRadius));
         int color = spec.color();
         float effectiveAlpha = (color >>> 24 & 0xFF) / (255.0F) * Math.max(spec.intensity(), 0.0F);
         packedParams.putFloat(byteOffset + 32, (color >>> 16 & 0xFF) / (255.0F));
         packedParams.putFloat(byteOffset + 36, (color >>> 8 & 0xFF) / (255.0F));
         packedParams.putFloat(byteOffset + 40, (color & 0xFF) / (255.0F));
         packedParams.putFloat(byteOffset + 44, effectiveAlpha);
         packedParams.putFloat(byteOffset + 48, spec.dirX());
         packedParams.putFloat(byteOffset + 52, spec.dirY());
         packedParams.putFloat(byteOffset + 56, clamp(spec.dirStrength(), 0.0F, 1.0F));
         packedParams.putFloat(byteOffset + 60, spec.mode());
         packedParams.putFloat(byteOffset + 64, Math.max(spec.falloff(), (0.25F)));
         packedParams.putFloat(byteOffset + 68, 0.0F);
         packedParams.putFloat(byteOffset + 72, 0.0F);
         packedParams.putFloat(byteOffset + 76, 0.0F);
      }

      packedParams.position(0);
      return packedParams;
   }

   private SoftGlowRenderer() {
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("SoftGlowParams"), buffer);
         }
      }
   }

   public void uploadParams() {
      if (!this.params.isEmpty() && this.paramsDirty) {
         GpuBuffer buffer = this.ensureBuffer();
         if (buffer != null) {
            try {
               MemoryStack memory = MemoryStack.stackPush();

               try {
                  ByteBuffer packedParams = this.packParams(memory, this.params);
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, packedParams.remaining()), packedParams);
                  this.paramsDirty = false;
               } catch (Throwable uploadFailure) {
                  if (memory != null) {
                     try {
                        memory.close();
                     } catch (Throwable closeFailure) {
                        uploadFailure.addSuppressed(closeFailure);
                     }
                  }

                  throw uploadFailure;
               }

               if (memory != null) {
                  memory.close();
               }
            } catch (RuntimeException error) {
               this.paramsDirty = true;
            }
         }
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

   public void end() {
      this.drawContext = null;
   }

   @Override
   public void close() {
      this.params.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (10240L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_soft_glow_params"), 136, (10240L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   public void render(SoftGlowSpec spec) {
      this.submit(this.drawContext, spec);
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   public int allocateParams(SoftGlowSpec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex >= 128) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return paramsIndex;
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   public void render(DrawContext context, SoftGlowSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }
}
