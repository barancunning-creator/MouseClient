package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.BlobRenderState;
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

public final class BlobRenderer implements AutoCloseable {

   private static volatile BlobRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/blob")))
      .withVertexShader(id(("core/blob")))
      .withFragmentShader(id(("core/blob")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("BlobParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<BlobSpec> params = new ArrayList<>(16);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty;

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

   private static void putColor(ByteBuffer packedParams, int byteOffset, int color) {
      packedParams.putFloat(byteOffset, (color >>> 16 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 4, (color >>> 8 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 8, (color & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 12, (color >>> 24 & 0xFF) / (255.0F));
   }

   public void end() {
      this.drawContext = null;
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

   public void render(BlobSpec spec) {
      this.submit(this.drawContext, spec);
   }

   private BlobRenderer() {
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   public int allocateParams(BlobSpec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 128) {
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

   public static void closeInstance() {
      BlobRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("BlobParamsArray"), buffer);
         }
      }
   }

   @Override
   public void close() {
      this.params.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (8192L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_blob_params"), 136, (8192L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private ByteBuffer packParams(MemoryStack stack, List<BlobSpec> specs) {
      ByteBuffer packedParams = stack.calloc(Math.min(8192, specs.size() * 4 * 4 * 4));

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         BlobSpec spec = (BlobSpec)specs.get(specIndex);
         int byteOffset = specIndex * 4 * 4 * 4;
         packedParams.putFloat(byteOffset, spec.width());
         packedParams.putFloat(byteOffset + 4, spec.height());
         packedParams.putFloat(byteOffset + 8, spec.centerX());
         packedParams.putFloat(byteOffset + 12, spec.centerY());
         packedParams.putFloat(byteOffset + 16, spec.radiusX());
         packedParams.putFloat(byteOffset + 20, spec.radiusY());
         packedParams.putFloat(byteOffset + 24, spec.falloff());
         packedParams.putFloat(byteOffset + 28, Math.max(spec.feather(), (0.001F)));
         packedParams.putFloat(byteOffset + 32, spec.clipTL());
         packedParams.putFloat(byteOffset + 36, spec.clipTR());
         packedParams.putFloat(byteOffset + 40, spec.clipBR());
         packedParams.putFloat(byteOffset + 44, spec.clipBL());
         putColor(packedParams, byteOffset + 48, spec.color());
      }

      packedParams.position(0);
      return packedParams;
   }

   private void submit(DrawContext context, BlobSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new BlobRenderState(transform, spec, ScissorStack.current()));
         } catch (RuntimeException error) {
         }
      }
   }

   public static BlobRenderer getInstance() {
      BlobRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (BlobRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new BlobRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }
}
