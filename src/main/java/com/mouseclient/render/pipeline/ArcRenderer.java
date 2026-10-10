package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.ArcRenderState;
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

public final class ArcRenderer implements AutoCloseable {

   private static volatile ArcRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/arc_divider")))
      .withVertexShader(id(("core/arc_divider")))
      .withFragmentShader(id(("core/arc_divider")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("ArcParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<ArcSpec> params = new ArrayList<>(64);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty = true;

   public static ArcRenderer getInstance() {
      ArcRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (ArcRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new ArcRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   private ByteBuffer packParams(MemoryStack stack, List<ArcSpec> specs) {
      ByteBuffer packedParams = stack.calloc(Math.min(8192, specs.size() * 2 * 4 * 4));

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         ArcSpec spec = (ArcSpec)specs.get(specIndex);
         int byteOffset = specIndex * 2 * 4 * 4;
         packedParams.putFloat(byteOffset, spec.halfWidth());
         packedParams.putFloat(byteOffset + 4, Math.max(spec.lift(), 0.0F));
         packedParams.putFloat(byteOffset + 8, Math.max(spec.thickness(), (0.5F)));
         packedParams.putFloat(byteOffset + 12, Math.max(spec.feather(), (0.5F)));
         putColor(packedParams, byteOffset + 16, spec.color());
      }

      packedParams.position(0);
      return packedParams;
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (8192L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_arc_params"), 136, (8192L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   public void render(ArcSpec spec) {
      this.submit(this.drawContext, spec);
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("ArcParamsArray"), buffer);
         }
      }
   }

   public int allocateParams(ArcSpec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 256) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return paramsIndex;
   }

   private ArcRenderer() {
   }

   @Override
   public void close() {
      this.params.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   private static void putColor(ByteBuffer packedParams, int byteOffset, int color) {
      packedParams.putFloat(byteOffset, (color >>> 16 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 4, (color >>> 8 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 8, (color & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 12, (color >>> 24 & 0xFF) / (255.0F));
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

   private void submit(DrawContext context, ArcSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new ArcRenderState(transform, spec, ScissorStack.current()));
         } catch (RuntimeException error) {
         }
      }
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

   public void end() {
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
      this.uploadParams();
      return this.paramsBuffer;
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   public static void closeInstance() {
      ArcRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }
}
