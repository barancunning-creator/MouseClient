package com.mouseclient.render.pipeline;

import com.mouseclient.network.ClientInitializer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class TargetScanSurface extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
   private static final RenderPipeline SCAN_SURFACE_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/target_scan_surface")))
      .withVertexShader(ClientInitializer.id(("core/target_scan_surface")))
      .withFragmentShader(ClientInitializer.id(("core/target_scan_surface")))
      .withSampler(("Sampler0"))
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(true)
      .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, DrawMode.QUADS)
      .build();
   private static final Function<Identifier, RenderLayer> LAYER_BY_TEXTURE = Util.memoize(
      skinTexture -> RenderLayer.of(
         ("target_scan_surface"),
         RenderSetup.builder(SCAN_SURFACE_PIPELINE).texture(("Sampler0"), skinTexture).expectedBufferSize((32768)).build()
      )
   );

   private void emitModelMesh(MatrixStack matrices, VertexConsumer vertices) {
      this.getContextModel().head.render(matrices, vertices, (15728880), OverlayTexture.DEFAULT_UV, -1);
      this.getContextModel().body.render(matrices, vertices, (15728880), OverlayTexture.DEFAULT_UV, -1);
      this.getContextModel().rightArm.render(matrices, vertices, (15728880), OverlayTexture.DEFAULT_UV, -1);
      this.getContextModel().leftArm.render(matrices, vertices, (15728880), OverlayTexture.DEFAULT_UV, -1);
      this.getContextModel().rightLeg.render(matrices, vertices, (15728880), OverlayTexture.DEFAULT_UV, -1);
      this.getContextModel().leftLeg.render(matrices, vertices, (15728880), OverlayTexture.DEFAULT_UV, -1);
   }

   @Override
   public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float yaw, float pitch) {
      if (MinecraftClient.getInstance().world != null && state.skinTextures != null) {
         if (MinecraftClient.getInstance().world.getEntityById(state.id) instanceof PlayerEntity player) {
            TargetEsp.TargetScanState scanState = TargetEsp.getInstance().scanStateFor(player);
            if (scanState != null && !(scanState.alpha() <= (0.001F))) {
               queue.getBatchingQueue(20).submitCustom(matrices, LAYER_BY_TEXTURE.apply(state.skinTextures.body().texturePath()), (matrixEntry, vertices) -> {
                  MatrixStack scanMatrices = new MatrixStack();
                  scanMatrices.peek().copy(matrixEntry);
                  scanMatrices.scale((1.008F) - scanState.hit() * (0.018F), (1.008F) - scanState.hit() * (0.018F), (1.008F) - scanState.hit() * (0.018F));
                  this.getContextModel().setAngles(state);
                  TargetScanSurface.ScanBoundsConsumer bounds = new TargetScanSurface.ScanBoundsConsumer();
                  this.emitModelMesh(scanMatrices, bounds);
                  if (bounds.validSpan()) {
                     this.emitModelMesh(scanMatrices, new TargetScanSurface.ScanVertexConsumer(vertices, bounds, scanState));
                  }
               });
            }
         }
      }
   }

   public TargetScanSurface(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
      super(context);
   }

   private static final class ScanVertexConsumer implements VertexConsumer {
      private VertexConsumer delegate;
      private TargetScanSurface.ScanBoundsConsumer bounds;
      private int primaryRed;
      private int primaryGreen;
      private int primaryBlue;
      private int secondaryRedAndPhase;
      private int secondaryGreenAndAlpha;
      private int secondaryBlueAndGlow;
      private int hitBits;
      private float vertexX;
      private float vertexY;

      private static float normalizedSpan(float value, float min, float max) {
         return MathHelper.clamp((value - min) / Math.max((1.0E-4F), max - min), 0.0F, 1.0F);
      }

      private ScanVertexConsumer(VertexConsumer delegate, TargetScanSurface.ScanBoundsConsumer bounds, TargetEsp.TargetScanState scanState) {
         this.delegate = delegate;
         this.bounds = bounds;
         this.primaryRed = scanState.primaryColor() >>> 16 & 0xFF;
         this.primaryGreen = scanState.primaryColor() >>> 8 & 0xFF;
         this.primaryBlue = scanState.primaryColor() & 0xFF;
         this.secondaryRedAndPhase = (scanState.secondaryColor() >>> 16 & 0xFF) << 8 | Math.round(MathHelper.clamp(scanState.phase(), 0.0F, 1.0F) * (255.0F));
         this.secondaryGreenAndAlpha = (scanState.secondaryColor() >>> 8 & 0xFF) << 8 | Math.round(MathHelper.clamp(scanState.alpha(), 0.0F, 1.0F) * (255.0F));
         this.secondaryBlueAndGlow = (scanState.secondaryColor() & 0xFF) << 8 | Math.round(MathHelper.clamp(scanState.glow(), 0.0F, 2.0F) * (127.5F));
         this.hitBits = Math.round(MathHelper.clamp(scanState.hit(), 0.0F, 1.0F) * (255.0F)) << 8;
      }

      @Override
      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this.setScanColor();
      }

      @Override
      public VertexConsumer normal(float normalX, float normalY, float normalZ) {
         this.delegate.normal(normalX, normalY, normalZ);
         return this;
      }

      @Override
      public VertexConsumer color(int argb) {
         return this.setScanColor();
      }

      @Override
      public VertexConsumer light(int blockLight, int skyLight) {
         this.delegate
            .light(
               this.secondaryBlueAndGlow,
               this.hitBits
                  | Math.round(
                     normalizedSpan(this.vertexX, this.bounds.minX, this.bounds.maxX)
                        * (255.0F)
                  )
            );
         return this;
      }

      @Override
      public VertexConsumer overlay(int u, int v) {
         this.delegate.overlay(this.secondaryRedAndPhase, this.secondaryGreenAndAlpha);
         return this;
      }

      @Override
      public VertexConsumer vertex(float x, float y, float z) {
         this.vertexX = x;
         this.vertexY = y;
         this.delegate.vertex(x, y, z);
         return this;
      }

      @Override
      public VertexConsumer lineWidth(float width) {
         this.delegate.lineWidth(width);
         return this;
      }

      @Override
      public VertexConsumer texture(float u, float v) {
         this.delegate.texture(u, v);
         return this;
      }

      private VertexConsumer setScanColor() {
         this.delegate
            .color(
               this.primaryRed,
               this.primaryGreen,
               this.primaryBlue,
               Math.round(
                  normalizedSpan(this.vertexY, this.bounds.minY, this.bounds.maxY)
                     * (255.0F)
               )
            );
         return this;
      }
   }

   private static final class ScanBoundsConsumer implements VertexConsumer {
      private float minX = Float.POSITIVE_INFINITY;
      private float minY = Float.POSITIVE_INFINITY;
      private float maxX = Float.NEGATIVE_INFINITY;
      private float maxY = Float.NEGATIVE_INFINITY;

      @Override
      public VertexConsumer overlay(int u, int v) {
         return this;
      }

      @Override
      public VertexConsumer normal(float normalX, float normalY, float normalZ) {
         return this;
      }

      @Override
      public VertexConsumer vertex(float x, float y, float z) {
         this.minX = Math.min(this.minX, x);
         this.minY = Math.min(this.minY, y);
         this.maxX = Math.max(this.maxX, x);
         this.maxY = Math.max(this.maxY, y);
         return this;
      }

      @Override
      public VertexConsumer texture(float u, float v) {
         return this;
      }

      @Override
      public VertexConsumer light(int blockLight, int skyLight) {
         return this;
      }

      @Override
      public VertexConsumer color(int red, int green, int blue, int alpha) {
         return this;
      }

      private boolean validSpan() {
         return Float.isFinite(this.minX)
            && Float.isFinite(this.minY)
            && this.maxX - this.minX > (1.0E-4F)
            && this.maxY - this.minY > (1.0E-4F);
      }

      @Override
      public VertexConsumer lineWidth(float width) {
         return this;
      }

      @Override
      public VertexConsumer color(int argb) {
         return this;
      }
   }
}
