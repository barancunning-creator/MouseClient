package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.DitherBatchRenderState;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.mixin.DrawContextAccessor;
import com.mouseclient.network.ClientInitializer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;

public final class DitherRenderer implements AutoCloseable {

   private static volatile DitherRenderer INSTANCE;
   private static final VertexFormat vertexFormat = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .add(("LineWidth"), VertexFormatElement.LINE_WIDTH)
      .build();
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/dither")))
      .withVertexShader(id(("core/dither")))
      .withFragmentShader(id(("core/dither")))
      .withVertexFormat(vertexFormat, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("DitherParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<DitherSpec> params = new ArrayList<>(16);
   private final Map<DitherRenderer.BatchKey, DitherBatchRenderState> batches = new LinkedHashMap<>(8);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty;

   public static void closeInstance() {
      DitherRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   public void begin(DrawContext drawContext) {
      if (this.drawContext != drawContext) {
         this.batches.clear();
      }

      this.drawContext = drawContext;
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   private ByteBuffer packParams(MemoryStack stack) {
      ByteBuffer packedParams = stack.calloc(8192);
      float animationSeconds = (float)(System.nanoTime() % (1000000000000L)) / (1.0E9F);

      for (int specIndex = 0; specIndex < this.params.size(); specIndex++) {
         DitherSpec spec = this.params.get(specIndex);
         int byteOffset = specIndex * 8 * 4;
         packedParams.putFloat(byteOffset, spec.cellSize());
         packedParams.putFloat(byteOffset + 4, spec.width());
         packedParams.putFloat(byteOffset + 8, spec.height());
         packedParams.putFloat(byteOffset + 12, spec.radius());
         packedParams.putFloat(byteOffset + 16, animationSeconds);
         packedParams.putFloat(byteOffset + 20, (7.31F));
         packedParams.putFloat(byteOffset + 24, spec.spread());
         packedParams.putFloat(byteOffset + 28, 0.0F);
      }

      packedParams.position(0);
      return packedParams;
   }

   public void uploadParams() {
      if (!this.params.isEmpty() && this.paramsDirty) {
         GpuBuffer buffer = this.ensureBuffer();
         if (buffer != null) {
            try {
               MemoryStack memory = MemoryStack.stackPush();

               try {
                  ByteBuffer packedParams = this.packParams(memory);
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

   public int allocateParams(DitherSpec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex >= 256) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return paramsIndex;
   }

   public void render(DitherSpec spec) {
      if (this.drawContext != null && spec != null) {
         try {
            GuiRenderState guiRenderState = ((DrawContextAccessor)(Object)this.drawContext).getGuiRenderState();
            int layerSerial = ((GuiLayerTracker)guiRenderState).getLayerSerial();
            Matrix3x2f transform = GuiScale.getScaledMatrices(this.drawContext);
            ScreenRect bounds = ScissorStack.current();
            DitherRenderer.BatchKey batchKey = new DitherRenderer.BatchKey(guiRenderState, layerSerial, bounds);
            DitherBatchRenderState batch = this.batches.get(batchKey);
            if (batch == null) {
               batch = new DitherBatchRenderState(transform, bounds);
               batch.add(spec);
               this.batches.put(batchKey, batch);
               guiRenderState.addSimpleElement(batch);
            } else {
               batch.add(spec);
            }
         } catch (RuntimeException error) {
            ClientInitializer.logger().warn(("[DitherRenderer] submit failed"), error);
         }
      }
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
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
      MemoryStack memory = MemoryStack.stackPush();

      GpuBuffer buffer;
      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_dither_params"), 128, this.packParams(memory));
         this.paramsDirty = false;
         buffer = this.paramsBuffer;
      } catch (Throwable allocationFailure) {
         if (memory != null) {
            try {
               memory.close();
            } catch (Throwable closeFailure) {
               allocationFailure.addSuppressed(closeFailure);
            }
         }

         throw allocationFailure;
      }

      if (memory != null) {
         memory.close();
      }

      return buffer;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private DitherRenderer() {
   }

   @Override
   public void close() {
      this.params.clear();
      this.batches.clear();
      this.releaseBuffer();
      this.drawContext = null;
   }

   public void end() {
      this.drawContext = null;
      this.batches.clear();
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (8192L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_dither_params"), 136, (8192L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("DitherParamsArray"), buffer);
         }
      }
   }

   public static DitherRenderer getInstance() {
      DitherRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (DitherRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new DitherRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   private record BatchKey(GuiRenderState state, int layer, ScreenRect scissor) {
   }
}
