package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.CircleRenderState;
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

public final class CircleRenderer implements AutoCloseable {

   private static volatile CircleRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/circle")))
      .withVertexShader(id(("core/circle")))
      .withFragmentShader(id(("core/circle")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("CircleParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<CircleSpec> params = new ArrayList<>(128);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty = true;

   private CircleSpec normalize(CircleSpec spec) {
      return new CircleSpec(
         spec.x(),
         spec.y(),
         Math.max(spec.radius(), 0.0F),
         clamp(spec.thickness(), 0.0F, spec.radius()),
         Math.max(spec.smoothness(), (0.5F)),
         spec.color()
      );
   }

   public int allocateParams(CircleSpec spec) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 512) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return paramsIndex;
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
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

   public void render(DrawContext context, CircleSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   @Override
   public void close() {
      this.params.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   public static CircleRenderer getInstance() {
      CircleRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (CircleRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new CircleRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (16384L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_circle_params"), 136, (16384L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   public void end() {
      this.drawContext = null;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private CircleRenderer() {
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("CircleParamsArray"), buffer);
         }
      }
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   private void submit(DrawContext context, CircleSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            CircleSpec normalizedSpec = this.normalize(spec);
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new CircleRenderState(transform, normalizedSpec, ScissorStack.current()));
         } catch (RuntimeException error) {
         }
      }
   }

   public static void closeInstance() {
      CircleRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   private ByteBuffer packParams(MemoryStack stack, List<CircleSpec> specs) {
      ByteBuffer packedParams = stack.calloc(Math.min(16384, specs.size() * 2 * 4 * 4));

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         CircleSpec spec = (CircleSpec)specs.get(specIndex);
         int byteOffset = specIndex * 2 * 4 * 4;
         packedParams.putFloat(byteOffset, spec.radius());
         packedParams.putFloat(byteOffset + 4, Math.max(spec.smoothness(), 0.0F));
         packedParams.putFloat(byteOffset + 8, spec.thickness());
         packedParams.putFloat(byteOffset + 12, 0.0F);
         putColor(packedParams, byteOffset + 16, spec.color());
      }

      packedParams.position(0);
      return packedParams;
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
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

   public void render(CircleSpec spec) {
      this.submit(this.drawContext, spec);
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   private static void putColor(ByteBuffer packedParams, int byteOffset, int color) {
      packedParams.putFloat(byteOffset, (color >>> 16 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 4, (color >>> 8 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 8, (color & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 12, (color >>> 24 & 0xFF) / (255.0F));
   }
}
