package com.mouseclient.render.pipeline;

import com.mouseclient.gui.OutlineColorRange;
import com.mouseclient.gui.Outline360RenderState;
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

public final class Outline360Renderer implements AutoCloseable {

   private static volatile Outline360Renderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/outline_360")))
      .withVertexShader(id(("core/outline_360")))
      .withFragmentShader(id(("core/outline_360")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Outline360ParamsArray"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Outline360RangesArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<Outline360Spec> params = new ArrayList<>(64);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private GpuBuffer rangesBuffer;
   private boolean paramsDirty = true;

   public static void closeInstance() {
      Outline360Renderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   private static void putColor(ByteBuffer packedParams, int byteOffset, int color) {
      packedParams.putFloat(byteOffset, (color >>> 16 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 4, (color >>> 8 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 8, (color & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 12, (color >>> 24 & 0xFF) / (255.0F));
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private void ensureUploadedBuffers() {
      if (this.paramsDirty || this.paramsBuffer == null || this.rangesBuffer == null) {
         this.uploadParams();
         if (this.paramsDirty || this.paramsBuffer == null || this.rangesBuffer == null) {
            this.releaseBuffers();
            MemoryStack memoryStack = MemoryStack.stackPush();

            try {
               ByteBuffer paramsData = memoryStack.calloc(16384);
               ByteBuffer rangesData = memoryStack.calloc((32768));
               this.packParams(paramsData, rangesData, this.params);
               this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_outline_360_params"), 128, paramsData);
               this.rangesBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_outline_360_ranges"), 128, rangesData);
               this.paramsDirty = false;
            } catch (Throwable uploadFailure) {
               if (memoryStack != null) {
                  try {
                     memoryStack.close();
                  } catch (Throwable closeFailure) {
                     uploadFailure.addSuppressed(closeFailure);
                  }
               }

               throw uploadFailure;
            }

            if (memoryStack != null) {
               memoryStack.close();
            }
         }
      }
   }

   private boolean ensureBuffers() {
      boolean paramsBufferReady = this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (16384L);
      boolean rangesBufferReady = this.rangesBuffer != null && !this.rangesBuffer.isClosed() && this.rangesBuffer.size() >= (32768L);
      if (paramsBufferReady && rangesBufferReady) {
         return true;
      }

      this.releaseBuffers();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_outline_360_params"), 136, (16384L));
         this.rangesBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_outline_360_ranges"), 136, (32768L));
         return true;
      } catch (RuntimeException error) {
         this.releaseBuffers();
         return false;
      }
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         this.ensureUploadedBuffers();
         if (this.paramsBuffer != null) {
            renderPass.setUniform(("Outline360ParamsArray"), this.paramsBuffer);
         }

         if (this.rangesBuffer != null) {
            renderPass.setUniform(("Outline360RangesArray"), this.rangesBuffer);
         }
      }
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   private Outline360Spec normalize(Outline360Spec spec) {
      float maximumRadius = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      float maximumThickness = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      return new Outline360Spec(
         spec.x(),
         spec.y(),
         spec.width(),
         spec.height(),
         clamp(spec.radiusTopLeft(), 0.0F, maximumRadius),
         clamp(spec.radiusTopRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomLeft(), 0.0F, maximumRadius),
         clamp(spec.thickness(), 0.0F, maximumThickness),
         spec.defaultColor(),
         Math.max(spec.smoothness(), 0.0F),
         Math.max(spec.blendDegrees(), 0.0F),
         spec.angleOffsetDegrees(),
         spec.ranges()
      );
   }

   @Override
   public void close() {
      this.params.clear();
      this.drawContext = null;
      this.releaseBuffers();
   }

   private void submit(DrawContext context, Outline360Spec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            Outline360Spec normalizedSpec = this.normalize(spec);
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new Outline360RenderState(transform, normalizedSpec));
         } catch (RuntimeException error) {
         }
      }
   }

   public void render(DrawContext context, Outline360Spec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   public int allocateParams(Outline360Spec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 256) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return paramsIndex;
   }

   public static Outline360Renderer getInstance() {
      Outline360Renderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (Outline360Renderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new Outline360Renderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   private Outline360Renderer() {
   }

   private void releaseBuffers() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }

      if (this.rangesBuffer != null) {
         this.rangesBuffer.close();
         this.rangesBuffer = null;
      }
   }

   private void packParams(ByteBuffer shapeParams, ByteBuffer rangeParams, List<Outline360Spec> specs) {
      int rangeCount = 0;

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         Outline360Spec spec = (Outline360Spec)specs.get(specIndex);
         int shapeOffset = specIndex * 4 * 4 * 4;
         int uploadedRangeCount = Math.min(spec.ranges().size(), 1024 - rangeCount);
         shapeParams.putFloat(shapeOffset, spec.radiusTopLeft());
         shapeParams.putFloat(shapeOffset + 4, spec.radiusTopRight());
         shapeParams.putFloat(shapeOffset + 8, spec.radiusBottomRight());
         shapeParams.putFloat(shapeOffset + 12, spec.radiusBottomLeft());
         shapeParams.putFloat(shapeOffset + 16, spec.width());
         shapeParams.putFloat(shapeOffset + 20, spec.height());
         shapeParams.putFloat(shapeOffset + 24, spec.thickness());
         shapeParams.putFloat(shapeOffset + 28, spec.smoothness());
         putColor(shapeParams, shapeOffset + 32, spec.defaultColor());
         shapeParams.putFloat(shapeOffset + 48, rangeCount);
         shapeParams.putFloat(shapeOffset + 52, uploadedRangeCount);
         shapeParams.putFloat(shapeOffset + 56, spec.blendDegrees());
         shapeParams.putFloat(shapeOffset + 60, spec.angleOffsetDegrees());

         for (int rangeIndex = 0; rangeIndex < uploadedRangeCount; rangeIndex++) {
            OutlineColorRange range = spec.ranges().get(rangeIndex);
            int rangeOffset = (rangeCount + rangeIndex) * 2 * 4 * 4;
            rangeParams.putFloat(rangeOffset, range.startDegrees());
            rangeParams.putFloat(rangeOffset + 4, range.endDegrees());
            rangeParams.putFloat(rangeOffset + 8, 0.0F);
            rangeParams.putFloat(rangeOffset + 12, 0.0F);
            putColor(rangeParams, rangeOffset + 16, range.color());
         }

         rangeCount += uploadedRangeCount;
      }

      shapeParams.position(0);
      rangeParams.position(0);
   }

   public void render(Outline360Spec spec) {
      this.submit(this.drawContext, spec);
   }

   public void uploadParams() {
      if (!this.params.isEmpty() && this.paramsDirty) {
         if (this.ensureBuffers()) {
            try {
               MemoryStack memory = MemoryStack.stackPush();

               try {
                  ByteBuffer packedShapeParams = memory.calloc(16384);
                  ByteBuffer packedRangeParams = memory.calloc((32768));
                  this.packParams(packedShapeParams, packedRangeParams, this.params);
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.paramsBuffer.slice(0L, packedShapeParams.remaining()), packedShapeParams);
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.rangesBuffer.slice(0L, packedRangeParams.remaining()), packedRangeParams);
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

   public void end() {
      this.drawContext = null;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }
}
