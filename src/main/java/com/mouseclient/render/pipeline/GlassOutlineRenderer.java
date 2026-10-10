package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.GlassOutlineRenderState;
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

public final class GlassOutlineRenderer implements AutoCloseable {

   private static volatile GlassOutlineRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/outline_glass")))
      .withVertexShader(id(("core/outline_glass")))
      .withFragmentShader(id(("core/outline_glass")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("GlassOutlineParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<GlassOutlineSpec> params = new ArrayList<>(128);
   private final List<BlurTextureRegion> blurRegions = new ArrayList<>(128);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty = true;

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("GlassOutlineParamsArray"), buffer);
         }
      }
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
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

   @Override
   public void close() {
      this.params.clear();
      this.blurRegions.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   public void end() {
      this.drawContext = null;
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

   private void submit(DrawContext context, GlassOutlineSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            GlassOutlineSpec normalizedSpec = this.normalize(spec);
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            BlurTextureRegion blurRegion = new BlurTextureRegion();
            GuiBlurRenderer.getInstance()
               .submitGlobalCapture(
                  context,
                  new BlurSpec(
                     normalizedSpec.x(),
                     normalizedSpec.y(),
                     normalizedSpec.width(),
                     normalizedSpec.height(),
                     normalizedSpec.radiusTopLeft(),
                     normalizedSpec.radiusTopRight(),
                     normalizedSpec.radiusBottomRight(),
                     normalizedSpec.radiusBottomLeft(),
                     1.0F,
                     (30.0F),
                     -1
                  ),
                  blurRegion
               );
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new GlassOutlineRenderState(transform, normalizedSpec, ScissorStack.current(), blurRegion));
         } catch (RuntimeException error) {
         }
      }
   }

   private GlassOutlineRenderer() {
   }

   private static void putColor(ByteBuffer packedParams, int byteOffset, int color) {
      packedParams.putFloat(byteOffset, (color >>> 16 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 4, (color >>> 8 & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 8, (color & 0xFF) / (255.0F));
      packedParams.putFloat(byteOffset + 12, (color >>> 24 & 0xFF) / (255.0F));
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (49056L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_outline_glass_params"), 136, (49056L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   public int allocateParams(GlassOutlineSpec spec, BlurTextureRegion blurRegion) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 438) {
         return -1;
      }

      this.params.add(spec);
      this.blurRegions.add(blurRegion);
      this.paramsDirty = true;
      return paramsIndex;
   }

   public void clearParams() {
      this.params.clear();
      this.blurRegions.clear();
      this.paramsDirty = false;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   public void render(GlassOutlineSpec spec) {
      this.submit(this.drawContext, spec);
   }

   private ByteBuffer packParams(MemoryStack stack, List<GlassOutlineSpec> specs) {
      ByteBuffer packedParams = stack.calloc(Math.min((49056), specs.size() * 7 * 4 * 4));

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         GlassOutlineSpec spec = (GlassOutlineSpec)specs.get(specIndex);
         int byteOffset = specIndex * 7 * 4 * 4;
         packedParams.putFloat(byteOffset, spec.radiusTopLeft());
         packedParams.putFloat(byteOffset + 4, spec.radiusTopRight());
         packedParams.putFloat(byteOffset + 8, spec.radiusBottomRight());
         packedParams.putFloat(byteOffset + 12, spec.radiusBottomLeft());
         packedParams.putFloat(byteOffset + 16, spec.width());
         packedParams.putFloat(byteOffset + 20, spec.height());
         packedParams.putFloat(byteOffset + 24, spec.thickness());
         packedParams.putFloat(byteOffset + 28, spec.smoothness());
         packedParams.putFloat(byteOffset + 32, spec.globalAlpha());
         packedParams.putFloat(byteOffset + 36, spec.fresnelPower());
         packedParams.putFloat(byteOffset + 40, spec.baseAlpha());
         packedParams.putFloat(byteOffset + 44, spec.fresnelMix());
         putColor(packedParams, byteOffset + 48, spec.fresnelColor());
         packedParams.putFloat(byteOffset + 64, spec.fresnelInvert() ? 1.0F : 0.0F);
         packedParams.putFloat(byteOffset + 68, spec.distortStrength());
         packedParams.putFloat(byteOffset + 72, spec.z());
         packedParams.putFloat(byteOffset + 76, Math.max(spec.squirt(), (0.001F)));
         putColor(packedParams, byteOffset + 80, spec.color());
         BlurTextureRegion blurRegion = specIndex < this.blurRegions.size() ? this.blurRegions.get(specIndex) : null;
         if (blurRegion != null) {
            packedParams.putFloat(byteOffset + 96, blurRegion.x);
            packedParams.putFloat(byteOffset + 100, blurRegion.y);
            packedParams.putFloat(byteOffset + 104, blurRegion.width);
            packedParams.putFloat(byteOffset + 108, blurRegion.height);
         }
      }

      packedParams.position(0);
      return packedParams;
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   public void render(DrawContext context, GlassOutlineSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   public static GlassOutlineRenderer getInstance() {
      GlassOutlineRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (GlassOutlineRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new GlassOutlineRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   public static void closeInstance() {
      GlassOutlineRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   private GlassOutlineSpec normalize(GlassOutlineSpec spec) {
      float maximumRadius = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      float maximumThickness = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      return new GlassOutlineSpec(
         spec.x(),
         spec.y(),
         spec.width(),
         spec.height(),
         clamp(spec.radiusTopLeft(), 0.0F, maximumRadius),
         clamp(spec.radiusTopRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomLeft(), 0.0F, maximumRadius),
         clamp(spec.thickness(), 0.0F, maximumThickness),
         spec.color(),
         clamp(spec.globalAlpha(), 0.0F, 1.0F),
         Math.max(spec.fresnelPower(), (0.001F)),
         spec.fresnelColor(),
         clamp(spec.baseAlpha(), 0.0F, 1.0F),
         spec.fresnelInvert(),
         clamp(spec.fresnelMix(), 0.0F, 1.0F),
         spec.distortStrength(),
         Math.max(spec.squirt(), (0.001F)),
         Math.max(spec.smoothness(), 0.0F),
         spec.z()
      );
   }
}
