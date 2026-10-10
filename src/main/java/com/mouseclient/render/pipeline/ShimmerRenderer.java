package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.ShimmerRenderState;
import com.mouseclient.gui.ShimmerSpec;
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

public final class ShimmerRenderer implements AutoCloseable {

   private static volatile ShimmerRenderer INSTANCE;
   private static final VertexFormat vertexFormat = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .add(("LineWidth"), VertexFormatElement.LINE_WIDTH)
      .build();
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/shimmer")))
      .withVertexShader(id(("core/shimmer")))
      .withFragmentShader(id(("core/shimmer")))
      .withVertexFormat(vertexFormat, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("ShimmerParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<ShimmerSpec> params = new ArrayList<>(32);
   private final Map<ShimmerRenderer.BatchKey, ShimmerRenderState> batches = new LinkedHashMap<>(16);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty;

   public int allocateParams(ShimmerRenderState state) {
      int paramsIndex = this.params.size();
      if (paramsIndex >= 256) {
         return -1;
      }

      this.params.add(state.shimmer());
      this.paramsDirty = true;
      return paramsIndex;
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

   public void render(ShimmerSpec spec) {
      if (this.drawContext != null && spec != null) {
         try {
            GuiRenderState guiRenderState = ((DrawContextAccessor)(Object)this.drawContext).getGuiRenderState();
            int layerSerial = ((GuiLayerTracker)guiRenderState).getLayerSerial();
            Matrix3x2f transform = GuiScale.getScaledMatrices(this.drawContext);
            ScreenRect bounds = ScissorStack.current();
            ShimmerRenderer.BatchKey batchKey = new ShimmerRenderer.BatchKey(guiRenderState, layerSerial, bounds);
            ShimmerRenderState batch = this.batches.get(batchKey);
            if (batch == null) {
               batch = new ShimmerRenderState(transform, spec, bounds);
               this.batches.put(batchKey, batch);
               guiRenderState.addSimpleElement(batch);
            }
         } catch (RuntimeException error) {
            ClientInitializer.logger().warn(("[ShimmerRenderer] submit failed"), error);
         }
      }
   }

   public void begin(DrawContext drawContext) {
      if (this.drawContext != drawContext) {
         this.batches.clear();
      }

      this.drawContext = drawContext;
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   public static ShimmerRenderer getInstance() {
      ShimmerRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (ShimmerRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new ShimmerRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
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

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("ShimmerParamsArray"), buffer);
         }
      }
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
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
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_shimmer_params"), 128, this.packParams(memory));
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

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   public static void closeInstance() {
      ShimmerRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   private ByteBuffer packParams(MemoryStack stack) {
      ByteBuffer packedParams = stack.calloc(4096);

      for (int specIndex = 0; specIndex < this.params.size(); specIndex++) {
         ShimmerSpec spec = this.params.get(specIndex);
         int byteOffset = specIndex * 4 * 4;
         packedParams.putFloat(byteOffset, spec.progress());
         packedParams.putFloat(byteOffset + 4, spec.halfWidth());
         packedParams.putFloat(byteOffset + 8, spec.intensity());
         packedParams.putFloat(byteOffset + 12, 0.0F);
      }

      packedParams.position(0);
      return packedParams;
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (4096L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_shimmer_params"), 136, (4096L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private ShimmerRenderer() {
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   private record BatchKey(GuiRenderState state, int layer, ScreenRect scissor) {
   }
}
