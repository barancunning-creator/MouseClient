package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.GlassRenderState;
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

public final class GlassRenderer implements AutoCloseable {

   private static volatile GlassRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/glass")))
      .withVertexShader(id(("ui/glass/glass")))
      .withFragmentShader(id(("ui/glass/glass")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("GlassParamsArray"), UniformType.UNIFORM_BUFFER)
      .withUniform(("PaletteParams"), UniformType.UNIFORM_BUFFER)
      .withUniform(("SplitParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<GlassSpec> params = new ArrayList<>(64);
   private final List<BlurTextureRegion> blurRegions = new ArrayList<>(64);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty = true;

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
         ByteBuffer packedParams = this.packParams(memory, this.params);
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_glass_params"), 128, packedParams);
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

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (57344L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_glass_params"), 136, (57344L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   public int allocateParams(GlassSpec spec, BlurTextureRegion blurRegion) {
      int paramsIndex = this.params.size();
      if (paramsIndex == 448) {
         return -1;
      }

      this.params.add(spec);
      this.blurRegions.add(blurRegion);
      this.paramsDirty = true;
      return paramsIndex;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   @Override
   public void close() {
      this.params.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   private ByteBuffer packParams(MemoryStack stack, List<GlassSpec> specs) {
      int byteCount = Math.max(1, specs.size()) * 8 * 4 * 4;
      ByteBuffer packedParams = stack.calloc(byteCount);

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         GlassSpec spec = (GlassSpec)specs.get(specIndex);
         int byteOffset = specIndex * 8 * 4 * 4;
         packedParams.putFloat(byteOffset, spec.radiusTopLeft());
         packedParams.putFloat(byteOffset + 4, spec.radiusTopRight());
         packedParams.putFloat(byteOffset + 8, spec.radiusBottomRight());
         packedParams.putFloat(byteOffset + 12, spec.radiusBottomLeft());
         packedParams.putFloat(byteOffset + 16, spec.width());
         packedParams.putFloat(byteOffset + 20, spec.height());
         packedParams.putFloat(byteOffset + 24, spec.splitIndex());
         packedParams.putFloat(byteOffset + 28, Math.max(spec.squirt(), (0.001F)));
         packedParams.putFloat(byteOffset + 32, spec.globalAlpha());
         packedParams.putFloat(byteOffset + 36, spec.fresnelPower());
         packedParams.putFloat(byteOffset + 40, spec.baseAlpha());
         packedParams.putFloat(byteOffset + 44, spec.fresnelMix());
         putColor(packedParams, byteOffset + 48, spec.fresnelColor());
         packedParams.putFloat(byteOffset + 64, spec.fresnelInvert() ? 1.0F : 0.0F);
         packedParams.putFloat(byteOffset + 68, spec.distortStrength());
         packedParams.putFloat(byteOffset + 72, spec.z());
         packedParams.putFloat(byteOffset + 76, spec.colorOffset());
         putColor(packedParams, byteOffset + 80, spec.color());
         putColor(packedParams, byteOffset + 96, spec.secondColor());
         BlurTextureRegion blurRegion = specIndex < this.blurRegions.size() ? this.blurRegions.get(specIndex) : null;
         if (blurRegion != null) {
            packedParams.putFloat(byteOffset + 112, blurRegion.x);
            packedParams.putFloat(byteOffset + 116, blurRegion.y);
            packedParams.putFloat(byteOffset + 120, blurRegion.width);
            packedParams.putFloat(byteOffset + 124, blurRegion.height);
         }
      }

      packedParams.position(0);
      return packedParams;
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("GlassParamsArray"), buffer);
         }

         GpuBuffer paletteBuffer = MouseClientClientPalette.buffer();
         if (paletteBuffer != null) {
            renderPass.setUniform(("PaletteParams"), paletteBuffer);
         }

         GpuBuffer splitBuffer = GuiShapeSplitBuffer.buffer();
         if (splitBuffer != null) {
            renderPass.setUniform(("SplitParams"), splitBuffer);
         }
      }
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   public void clearParams() {
      this.params.clear();
      this.blurRegions.clear();
      this.paramsDirty = false;
   }

   public static GlassRenderer getInstance() {
      GlassRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (GlassRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new GlassRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   public void render(GlassSpec spec) {
      this.submit(this.drawContext, spec);
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

   public void render(DrawContext context, GlassSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   private GlassSpec normalize(GlassSpec spec) {
      float maximumRadius = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      float radiusTopLeft = clamp(spec.radiusTopLeft(), 0.0F, maximumRadius);
      float radiusTopRight = clamp(spec.radiusTopRight(), 0.0F, maximumRadius);
      float radiusBottomRight = clamp(spec.radiusBottomRight(), 0.0F, maximumRadius);
      float radiusBottomLeft = clamp(spec.radiusBottomLeft(), 0.0F, maximumRadius);
      float globalAlpha = clamp(spec.globalAlpha(), 0.0F, 1.0F);
      float fresnelPower = Math.max(spec.fresnelPower(), (0.001F));
      float baseAlpha = clamp(spec.baseAlpha(), 0.0F, 1.0F);
      float fresnelMix = clamp(spec.fresnelMix(), 0.0F, 1.0F);
      float squirt = Math.max(spec.squirt(), (0.001F));
      float blurRadius = Float.isFinite(spec.blurRadius()) ? clamp(spec.blurRadius(), (0.1F), (64.0F)) : (0.1F);
      float colorOffset = Float.isFinite(spec.colorOffset()) ? spec.colorOffset() : 0.0F;
      colorOffset -= (float)Math.floor(colorOffset);
      return radiusTopLeft == spec.radiusTopLeft()
            && radiusTopRight == spec.radiusTopRight()
            && radiusBottomRight == spec.radiusBottomRight()
            && radiusBottomLeft == spec.radiusBottomLeft()
            && globalAlpha == spec.globalAlpha()
            && fresnelPower == spec.fresnelPower()
            && baseAlpha == spec.baseAlpha()
            && fresnelMix == spec.fresnelMix()
            && squirt == spec.squirt()
            && blurRadius == spec.blurRadius()
            && colorOffset == spec.colorOffset()
         ? spec
         : new GlassSpec(
               spec.x(),
               spec.y(),
               spec.width(),
               spec.height(),
               radiusTopLeft,
               radiusTopRight,
               radiusBottomRight,
               radiusBottomLeft,
               spec.color(),
               globalAlpha,
               fresnelPower,
               spec.fresnelColor(),
               baseAlpha,
               spec.fresnelInvert(),
               fresnelMix,
               spec.distortStrength(),
               squirt,
               spec.z(),
               blurRadius,
               spec.secondColor(),
               colorOffset
            )
            .withSplitIndex(spec.splitIndex());
   }

   public static void closeInstance() {
      GlassRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   private GlassRenderer() {
   }

   private void submit(DrawContext context, GlassSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            GlassSpec normalizedSpec = this.normalize(spec);
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
                     normalizedSpec.blurRadius(),
                     -1
                  ),
                  blurRegion
               );
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new GlassRenderState(transform, normalizedSpec, ScissorStack.current(), blurRegion));
         } catch (RuntimeException error) {
         }
      }
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   public void end() {
      this.drawContext = null;
   }
}
