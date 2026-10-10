package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.GlassShapeRenderState;
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

public final class GlassShapeRenderer implements AutoCloseable {
   public static final int MAX_SHAPES = 16;
   public static final int MAX_SPANS = 64;
   public static final int PARAMETER_VEC4_COUNT = 73;

   private static volatile GlassShapeRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/shape")))
      .withVertexShader(id(("ui/shape/shape")))
      .withFragmentShader(id(("ui/shape/shape")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("ShapeParamsArray"), UniformType.UNIFORM_BUFFER)
      .withUniform(("PaletteParams"), UniformType.UNIFORM_BUFFER)
      .withUniform(("SplitParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<GlassShapeSpec> params = new ArrayList<>(32);
   private final List<BlurTextureRegion> blurRegions = new ArrayList<>(32);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty = true;

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (18688L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_shape_params"), 136, (18688L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   public void uploadParams() {
      if (!this.params.isEmpty() && this.paramsDirty) {
         GpuBuffer buffer = this.ensureBuffer();
         if (buffer != null) {
            try {
               MemoryStack memoryStack = MemoryStack.stackPush();

               try {
                  ByteBuffer packedParams = this.packParams(memoryStack, this.params);
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, packedParams.remaining()), packedParams);
                  this.paramsDirty = false;
               } catch (Throwable uploadFailure) {
                  if (memoryStack != null) {
                     try {
                        memoryStack.close();
                     } catch (Throwable closeFailure) {
                        uploadFailure.addSuppressed(closeFailure);
                     }
                  }

                  throw uploadFailure;
               }

               if (memoryStack != null) {
                  memoryStack.close();
               }
            } catch (RuntimeException error) {
               this.paramsDirty = true;
            }
         }
      }
   }

   public void render(DrawContext context, GlassShapeSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   private void submit(DrawContext context, GlassShapeSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         try {
            GlassShapeSpec normalizedSpec = this.normalize(spec);
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
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new GlassShapeRenderState(transform, normalizedSpec, ScissorStack.current(), blurRegion));
         } catch (RuntimeException error) {
         }
      }
   }

   public static GlassShapeRenderer getInstance() {
      GlassShapeRenderer renderer = INSTANCE;
      if (renderer == null) {
         synchronized (GlassShapeRenderer.class) {
            renderer = INSTANCE;
            if (renderer == null) {
               renderer = new GlassShapeRenderer();
               INSTANCE = renderer;
            }
         }
      }

      return renderer;
   }

   public void clearParams() {
      this.params.clear();
      this.blurRegions.clear();
      this.paramsDirty = false;
   }

   public static void closeInstance() {
      GlassShapeRenderer renderer = INSTANCE;
      if (renderer != null) {
         renderer.close();
         INSTANCE = null;
      }
   }

   private GlassShapeRenderer() {
   }

   private static void putColor(ByteBuffer buffer, int offset, int color) {
      buffer.putFloat(offset, (color >>> 16 & 0xFF) / (255.0F));
      buffer.putFloat(offset + 4, (color >>> 8 & 0xFF) / (255.0F));
      buffer.putFloat(offset + 8, (color & 0xFF) / (255.0F));
      buffer.putFloat(offset + 12, (color >>> 24 & 0xFF) / (255.0F));
   }

   private ByteBuffer packParams(MemoryStack memoryStack, List<GlassShapeSpec> specs) {
      int byteCount = Math.max(1, specs.size()) * 73 * 4 * 4;
      ByteBuffer packedParams = memoryStack.calloc(byteCount);

      for (int shapeIndex = 0; shapeIndex < specs.size(); shapeIndex++) {
         GlassShapeSpec spec = (GlassShapeSpec)specs.get(shapeIndex);
         int shapeOffset = shapeIndex * 73 * 4 * 4;
         packedParams.putFloat(shapeOffset, spec.radiusTopLeft());
         packedParams.putFloat(shapeOffset + 4, spec.radiusTopRight());
         packedParams.putFloat(shapeOffset + 8, spec.radiusBottomRight());
         packedParams.putFloat(shapeOffset + 12, spec.radiusBottomLeft());
         packedParams.putFloat(shapeOffset + 16, spec.width());
         packedParams.putFloat(shapeOffset + 20, spec.height());
         packedParams.putFloat(shapeOffset + 24, calculateInnerRadius(spec));
         packedParams.putFloat(shapeOffset + 28, Math.max(spec.squirt(), (0.001F)));
         packedParams.putFloat(shapeOffset + 32, spec.globalAlpha());
         packedParams.putFloat(shapeOffset + 36, spec.fresnelPower());
         packedParams.putFloat(shapeOffset + 40, spec.baseAlpha());
         packedParams.putFloat(shapeOffset + 44, spec.fresnelMix());
         putColor(packedParams, shapeOffset + 48, spec.fresnelColor());
         packedParams.putFloat(shapeOffset + 48, spec.splitIndex());
         packedParams.putFloat(shapeOffset + 64, spec.fresnelInvert() ? 1.0F : 0.0F);
         packedParams.putFloat(shapeOffset + 68, spec.distortStrength());
         packedParams.putFloat(shapeOffset + 72, spec.z());
         packedParams.putFloat(shapeOffset + 76, spec.colorOffset());
         putColor(packedParams, shapeOffset + 80, spec.color());
         putColor(packedParams, shapeOffset + 96, spec.secondColor());
         BlurTextureRegion blurRegion = shapeIndex < this.blurRegions.size() ? this.blurRegions.get(shapeIndex) : null;
         if (blurRegion != null) {
            packedParams.putFloat(shapeOffset + 112, blurRegion.x);
            packedParams.putFloat(shapeOffset + 116, blurRegion.y);
            packedParams.putFloat(shapeOffset + 120, blurRegion.width);
            packedParams.putFloat(shapeOffset + 124, blurRegion.height);
         }

         int spanCount = spec.spanCount();
         packedParams.putFloat(shapeOffset + 128, spanCount);
         packedParams.putFloat(shapeOffset + 132, spec.innerRadius());
         packedParams.putFloat(shapeOffset + 136, spec.leftAligned());
         packedParams.putFloat(shapeOffset + 140, spec.bottomAnchored());
         float[] spans = spec.spans();
         int uploadedSpanCount = Math.min(spanCount, 64);

         for (int spanIndex = 0; spanIndex < uploadedSpanCount; spanIndex++) {
            int spanOffset = spanIndex * 4;
            packedParams.putFloat(shapeOffset + 144 + spanIndex * 16, spans[spanOffset]);
            packedParams.putFloat(shapeOffset + 144 + spanIndex * 16 + 4, spans[spanOffset + 1]);
            packedParams.putFloat(shapeOffset + 144 + spanIndex * 16 + 8, spans[spanOffset + 2]);
            packedParams.putFloat(shapeOffset + 144 + spanIndex * 16 + 12, spans[spanOffset + 3]);
         }
      }

      packedParams.position(0);
      return packedParams;
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
      MemoryStack memoryStack = MemoryStack.stackPush();

      GpuBuffer buffer;
      try {
         ByteBuffer packedParams = this.packParams(memoryStack, this.params);
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_shape_params"), 128, packedParams);
         this.paramsDirty = false;
         buffer = this.paramsBuffer;
      } catch (Throwable allocationFailure) {
         if (memoryStack != null) {
            try {
               memoryStack.close();
            } catch (Throwable closeFailure) {
               allocationFailure.addSuppressed(closeFailure);
            }
         }

         throw allocationFailure;
      }

      if (memoryStack != null) {
         memoryStack.close();
      }

      return buffer;
   }

   private static float calculateInnerRadius(GlassShapeSpec spec) {
      int spanCount = Math.min(spec.spanCount(), 64);
      float[] spans = spec.spans();
      if (spanCount > 0 && spans != null) {
         float minimumSpanHeight = (3.4028235E38F);

         for (int spanIndex = 0; spanIndex < spanCount; spanIndex++) {
            float spanTop = spans[spanIndex * 4 + 2];
            float spanBottom = spans[spanIndex * 4 + 3];
            float spanHeight = spanBottom - spanTop;
            if (spanHeight > (0.001F)) {
               minimumSpanHeight = Math.min(minimumSpanHeight, spanHeight);
            }
         }

         return minimumSpanHeight == (3.4028235E38F) ? 0.0F : Math.max(minimumSpanHeight * (0.5F), (1.5F));
      } else {
         return 0.0F;
      }
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("ShapeParamsArray"), buffer);
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

   public int allocateParams(GlassShapeSpec spec, BlurTextureRegion blurRegion) {
      int parameterIndex = this.params.size();
      if (parameterIndex == 16) {
         return -1;
      }

      this.params.add(spec);
      this.blurRegions.add(blurRegion);
      this.paramsDirty = true;
      return parameterIndex;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private GlassShapeSpec normalize(GlassShapeSpec spec) {
      float maximumRadius = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      float topLeftRadius = clamp(spec.radiusTopLeft(), 0.0F, maximumRadius);
      float topRightRadius = clamp(spec.radiusTopRight(), 0.0F, maximumRadius);
      float bottomRightRadius = clamp(spec.radiusBottomRight(), 0.0F, maximumRadius);
      float bottomLeftRadius = clamp(spec.radiusBottomLeft(), 0.0F, maximumRadius);
      float globalAlpha = clamp(spec.globalAlpha(), 0.0F, 1.0F);
      float fresnelPower = Math.max(spec.fresnelPower(), (0.001F));
      float baseAlpha = clamp(spec.baseAlpha(), 0.0F, 1.0F);
      float fresnelMix = clamp(spec.fresnelMix(), 0.0F, 1.0F);
      float squirt = Math.max(spec.squirt(), (0.001F));
      float blurRadius = Float.isFinite(spec.blurRadius()) ? clamp(spec.blurRadius(), (0.1F), (64.0F)) : (0.1F);
      float colorOffset = Float.isFinite(spec.colorOffset()) ? spec.colorOffset() : 0.0F;
      colorOffset -= (float)Math.floor(colorOffset);
      return topLeftRadius == spec.radiusTopLeft()
            && topRightRadius == spec.radiusTopRight()
            && bottomRightRadius == spec.radiusBottomRight()
            && bottomLeftRadius == spec.radiusBottomLeft()
            && globalAlpha == spec.globalAlpha()
            && fresnelPower == spec.fresnelPower()
            && baseAlpha == spec.baseAlpha()
            && fresnelMix == spec.fresnelMix()
            && squirt == spec.squirt()
            && blurRadius == spec.blurRadius()
            && colorOffset == spec.colorOffset()
         ? spec
         : new GlassShapeSpec(
            spec.x(),
            spec.y(),
            spec.width(),
            spec.height(),
            spec.spans(),
            spec.spanCount(),
            spec.innerRadius(),
            topLeftRadius,
            topRightRadius,
            bottomRightRadius,
            bottomLeftRadius,
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
            colorOffset,
            spec.leftAligned(),
            spec.bottomAnchored(),
            spec.splitIndex()
         );
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   public void end() {
      this.drawContext = null;
   }

   public void render(GlassShapeSpec spec) {
      this.submit(this.drawContext, spec);
   }
}
