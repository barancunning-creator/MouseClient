package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.ColorPickerRenderState;
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

public final class ColorPickerRenderer implements AutoCloseable {

   private static volatile ColorPickerRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/picker")))
      .withVertexShader(id(("core/picker")))
      .withFragmentShader(id(("core/picker")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("PickerParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<ColorPickerSpec> params = new ArrayList<>(64);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty = true;

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   private static void putColor(ByteBuffer packedParams, int byteOffset, int color) {
      packedParams.putFloat(byteOffset, (color >>> 16 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 4, (color >>> 8 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 8, (color & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 12, (color >>> 24 & 0xFF) / (255.0F));
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("PickerParamsArray"), buffer);
         }
      }
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   public void render(ColorPickerSpec spec) {
      this.submit(this.drawContext, spec);
   }

   @Override
   public void close() {
      this.params.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   private void submit(DrawContext context, ColorPickerSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new ColorPickerRenderState(transform, spec, ScissorStack.current()));
         } catch (RuntimeException error) {
         }
      }
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (12288L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_picker_params"), 136, (12288L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
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

   public static void closeInstance() {
      ColorPickerRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   private ByteBuffer packParams(MemoryStack stack, List<ColorPickerSpec> specs) {
      ByteBuffer packedParams = stack.calloc(Math.min(12288, specs.size() * 3 * 4 * 4));

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         ColorPickerSpec spec = (ColorPickerSpec)specs.get(specIndex);
         int byteOffset = specIndex * 3 * 4 * 4;
         packedParams.putFloat(byteOffset, spec.mode());
         packedParams.putFloat(byteOffset + 4, Math.max(spec.radius(), 0.0F));
         packedParams.putFloat(byteOffset + 8, Math.max(spec.smoothness(), (0.5F)));
         packedParams.putFloat(byteOffset + 12, Math.max(0.0F, Math.min(1.0F, spec.alpha())));
         packedParams.putFloat(byteOffset + 16, spec.width());
         packedParams.putFloat(byteOffset + 20, spec.height());
         packedParams.putFloat(byteOffset + 24, Math.max(spec.checkerPx(), 1.0F));
         packedParams.putFloat(byteOffset + 28, 0.0F);
         putColor(packedParams, byteOffset + 32, spec.solidColor());
      }

      packedParams.position(0);
      return packedParams;
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   private ColorPickerRenderer() {
   }

   public static ColorPickerRenderer getInstance() {
      ColorPickerRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (ColorPickerRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new ColorPickerRenderer();
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

   public void end() {
      this.drawContext = null;
   }

   public void render(DrawContext context, ColorPickerSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   public int allocateParams(ColorPickerSpec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 256) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return paramsIndex;
   }
}
