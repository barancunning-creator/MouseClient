package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.LineRenderState;
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

public final class LineRenderer implements AutoCloseable {

   private static volatile LineRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/line")))
      .withVertexShader(id(("core/line")))
      .withFragmentShader(id(("core/line")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("LineParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<LineSpec> params = new ArrayList<>(128);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty = true;

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   @Override
   public void close() {
      this.params.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("LineParamsArray"), buffer);
         }
      }
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   public void end() {
      this.drawContext = null;
   }

   private LineRenderer() {
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   public int allocateParams(LineSpec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 2048) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return paramsIndex;
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   public void render(LineSpec spec) {
      this.submit(this.drawContext, spec);
   }

   private void submit(DrawContext context, LineSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new LineRenderState(transform, spec, ScissorStack.current()));
         } catch (RuntimeException error) {
         }
      }
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (65536L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_line_params"), 136, (65536L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   public static void closeInstance() {
      LineRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   private ByteBuffer packParams(MemoryStack stack, List<LineSpec> specs) {
      ByteBuffer packedParams = stack.calloc(Math.min((65536), specs.size() * 2 * 4 * 4));

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         LineSpec spec = (LineSpec)specs.get(specIndex);
         int color = spec.color();
         int byteOffset = specIndex * 2 * 4 * 4;
         packedParams.putFloat(byteOffset, (color >>> 16 & 0xFF) / (255.0F));
         packedParams.putFloat(byteOffset + 4, (color >>> 8 & 0xFF) / (255.0F));
         packedParams.putFloat(byteOffset + 8, (color & 0xFF) / (255.0F));
         packedParams.putFloat(byteOffset + 12, (color >>> 24 & 0xFF) / (255.0F));
         packedParams.putFloat(byteOffset + 16, spec.length());
         packedParams.putFloat(byteOffset + 20, spec.thickness());
         packedParams.putFloat(byteOffset + 24, spec.fadeStart());
         packedParams.putFloat(byteOffset + 28, spec.fadeEnd());
      }

      packedParams.position(0);
      return packedParams;
   }

   public static LineRenderer getInstance() {
      LineRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (LineRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new LineRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
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

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
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
}
