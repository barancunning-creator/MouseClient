package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.MenuGridBatchRenderState;
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

public final class MenuGridRenderer implements AutoCloseable {

   private static volatile MenuGridRenderer INSTANCE;
   private static final VertexFormat vertexFormat = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .add(("LineWidth"), VertexFormatElement.LINE_WIDTH)
      .build();
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/menu_grid")))
      .withVertexShader(id(("core/menu_grid")))
      .withFragmentShader(id(("core/menu_grid")))
      .withVertexFormat(vertexFormat, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("MenuGridParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<MenuGridSpec> params = new ArrayList<>(4);
   private final Map<MenuGridRenderer.BatchKey, MenuGridBatchRenderState> batches = new LinkedHashMap<>(4);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty;

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (2048L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_menu_grid_params"), 136, (2048L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   @Override
   public void close() {
      this.params.clear();
      this.batches.clear();
      this.releaseBuffer();
      this.drawContext = null;
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
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_menu_grid_params"), 128, this.packParams(memory));
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

   public void end() {
      this.drawContext = null;
      this.batches.clear();
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("MenuGridParamsArray"), buffer);
         }
      }
   }

   public void begin(DrawContext drawContext) {
      if (this.drawContext != drawContext) {
         this.batches.clear();
      }

      this.drawContext = drawContext;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   public void render(MenuGridSpec spec) {
      if (this.drawContext != null && spec != null) {
         try {
            GuiRenderState guiRenderState = ((DrawContextAccessor)(Object)this.drawContext).getGuiRenderState();
            Matrix3x2f transform = GuiScale.getScaledMatrices(this.drawContext);
            ScreenRect bounds = ScissorStack.current();
            MenuGridRenderer.BatchKey batchKey = new MenuGridRenderer.BatchKey(guiRenderState, ((GuiLayerTracker)guiRenderState).getLayerSerial(), bounds);
            MenuGridBatchRenderState batch = this.batches.get(batchKey);
            if (batch == null) {
               batch = new MenuGridBatchRenderState(transform, bounds);
               batch.add(spec);
               this.batches.put(batchKey, batch);
               guiRenderState.addSimpleElement(batch);
            } else {
               batch.add(spec);
            }
         } catch (RuntimeException error) {
            ClientInitializer.logger().warn(("[MenuGridRenderer] submit failed"), error);
         }
      }
   }

   public static void closeInstance() {
      MenuGridRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
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

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   public int allocateParams(MenuGridSpec spec) {
      if (this.params.size() >= 64) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return this.params.size() - 1;
   }

   private MenuGridRenderer() {
   }

   private ByteBuffer packParams(MemoryStack stack) {
      ByteBuffer packedParams = stack.calloc(2048);
      float animationSeconds = (float)(System.nanoTime() % (1000000000000L)) / (1.0E9F);

      for (int specIndex = 0; specIndex < this.params.size(); specIndex++) {
         int byteOffset = specIndex * 8 * 4;
         packedParams.putFloat(byteOffset, this.params.get(specIndex).width());
         packedParams.putFloat(byteOffset + 4, this.params.get(specIndex).height());
         packedParams.putFloat(byteOffset + 8, this.params.get(specIndex).cellPx());
         packedParams.putFloat(byteOffset + 12, this.params.get(specIndex).lineWidthPx());
         packedParams.putFloat(byteOffset + 16, this.params.get(specIndex).mouseX() - this.params.get(specIndex).x());
         packedParams.putFloat(byteOffset + 20, this.params.get(specIndex).mouseY() - this.params.get(specIndex).y());
         packedParams.putFloat(byteOffset + 24, this.params.get(specIndex).influenceRadiusPx());
         packedParams.putFloat(byteOffset + 28, animationSeconds);
      }

      packedParams.position(0);
      return packedParams;
   }

   public static MenuGridRenderer getInstance() {
      MenuGridRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (MenuGridRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new MenuGridRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
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

   private record BatchKey(GuiRenderState state, int layer, ScreenRect scissor) {
   }
}
