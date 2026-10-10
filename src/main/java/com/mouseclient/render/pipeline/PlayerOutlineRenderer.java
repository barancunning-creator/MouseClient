package com.mouseclient.render.pipeline;

import com.mouseclient.modules.impl.render.Tags;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.render.CosmeticMaskGeometry;
import com.mouseclient.util.entity.WorldProjection;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.BufferAllocator.CloseableBuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public final class PlayerOutlineRenderer {
   private static PlayerOutlineRenderer INSTANCE;
   private static final RenderPipeline OUTLINE_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/player_outline")))
      .withVertexShader(ClientInitializer.id(("core/player_outline")))
      .withFragmentShader(ClientInitializer.id(("core/player_outline")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withSampler(("Sampler1"))
      .withUniform(("PlayerOutlineParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline BLUR_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/player_outline_blur")))
      .withVertexShader(ClientInitializer.id(("core/player_outline_blur")))
      .withFragmentShader(ClientInitializer.id(("core/player_outline_blur")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("PlayerOutlineParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline HITBOX_MASK_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/hitbox_mask")))
      .withVertexShader(ClientInitializer.id(("core/hitbox_mask")))
      .withFragmentShader(ClientInitializer.id(("core/hitbox_mask")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withUniform(("HitboxMaskParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline COSMETIC_MASK_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/cosmetic_mask")))
      .withVertexShader(ClientInitializer.id(("core/cosmetic_mask")))
      .withFragmentShader(ClientInitializer.id(("core/cosmetic_mask")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("HitboxMaskParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final int[][] BOX_FACES = new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

   private SimpleFramebuffer blurFramebuffer;
   private GpuBuffer screenQuadBuffer;
   private GpuBuffer outlineParamsBuffer;
   private GpuBuffer maskParamsBuffer;
   private GpuBuffer hitboxVertexBuffer;
   private GpuBuffer cosmeticVertexBuffer;
   private BufferAllocator hitboxAllocator;
   private BufferAllocator cosmeticAllocator;
   private final ArrayList<Box> playerBoxes = new ArrayList<>();
   private final Matrix4f projectionViewMatrix = new Matrix4f();
   private final Vector4f projectedVertex = new Vector4f();
   private boolean hasCosmeticBounds;
   private boolean cosmeticCrossesNearPlane;
   private float cosmeticMinNdcX;
   private float cosmeticMinNdcY;
   private float cosmeticMaxNdcX;
   private float cosmeticMaxNdcY;
   private boolean useFullScreenScissor;
   private float outlineMinNdcX;
   private float outlineMinNdcY;
   private float outlineMaxNdcX;
   private float outlineMaxNdcY;

   public void close() {
      if (this.blurFramebuffer != null) {
         this.blurFramebuffer.delete();
         this.blurFramebuffer = null;
      }

      if (this.hitboxAllocator != null) {
         this.hitboxAllocator.close();
         this.hitboxAllocator = null;
      }

      if (this.cosmeticAllocator != null) {
         this.cosmeticAllocator.close();
         this.cosmeticAllocator = null;
      }

      this.playerBoxes.clear();
      this.hasCosmeticBounds = false;
      this.cosmeticCrossesNearPlane = false;
      if (this.screenQuadBuffer != null && !this.screenQuadBuffer.isClosed()) {
         this.screenQuadBuffer.close();
      }

      this.screenQuadBuffer = null;
      if (this.outlineParamsBuffer != null && !this.outlineParamsBuffer.isClosed()) {
         this.outlineParamsBuffer.close();
      }

      this.outlineParamsBuffer = null;
      if (this.maskParamsBuffer != null && !this.maskParamsBuffer.isClosed()) {
         this.maskParamsBuffer.close();
      }

      this.maskParamsBuffer = null;
      if (this.hitboxVertexBuffer != null && !this.hitboxVertexBuffer.isClosed()) {
         this.hitboxVertexBuffer.close();
      }

      this.hitboxVertexBuffer = null;
      if (this.cosmeticVertexBuffer != null && !this.cosmeticVertexBuffer.isClosed()) {
         this.cosmeticVertexBuffer.close();
      }

      this.cosmeticVertexBuffer = null;
      CosmeticMaskGeometry.clear();
   }

   private PlayerOutlineRenderer() {
   }

   public void renderCosmeticMask(Framebuffer framebuffer) {
      int quadCount = CosmeticMaskGeometry.totalQuadCount();
      this.hasCosmeticBounds = false;
      this.cosmeticCrossesNearPlane = false;
      if (quadCount != 0 && framebuffer != null && framebuffer.getColorAttachmentView() != null && Tags.getInstance().isOutlineShaderEnabled()) {
         if (this.maskParamsBuffer == null || this.maskParamsBuffer.isClosed()) {
            this.maskParamsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_hitbox_mask_params"), 136, (80L));
         }

         MemoryStack stack = MemoryStack.stackPush();

         try {
            ByteBuffer paramsData = stack.calloc(80);
            this.projectionViewMatrix.set(WorldProjection.projectionMatrix()).mul(WorldProjection.viewMatrix()).get(0, paramsData);
            paramsData.putFloat(64, 1.0F);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.maskParamsBuffer.slice(0L, (80L)), paramsData);
         } catch (Throwable uploadFailure) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable closeFailure) {
                  uploadFailure.addSuppressed(closeFailure);
               }
            }

            throw uploadFailure;
         }

         if (stack != null) {
            stack.close();
         }

         if (this.cosmeticAllocator == null) {
            this.cosmeticAllocator = new BufferAllocator(Math.max(256, quadCount) * 4 * VertexFormats.POSITION_TEXTURE.getVertexSize());
         }

         CloseableBuffer previousBuffer = this.cosmeticAllocator.getAllocated();
         if (previousBuffer != null) {
            previousBuffer.close();
         }

         BufferBuilder builder = new BufferBuilder(this.cosmeticAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

         for (int vertexIndex = 0; vertexIndex < quadCount * 4; vertexIndex++) {
            int componentOffset = vertexIndex * 5;
            builder.vertex(CosmeticMaskGeometry.vertexComponent(componentOffset), CosmeticMaskGeometry.vertexComponent(componentOffset + 1), CosmeticMaskGeometry.vertexComponent(componentOffset + 2))
               .texture(CosmeticMaskGeometry.vertexComponent(componentOffset + 3), CosmeticMaskGeometry.vertexComponent(componentOffset + 4));
            this.includeCosmeticVertex(CosmeticMaskGeometry.vertexComponent(componentOffset), CosmeticMaskGeometry.vertexComponent(componentOffset + 1), CosmeticMaskGeometry.vertexComponent(componentOffset + 2));
         }

         try (BuiltBuffer meshData = builder.end()) {
            if (this.cosmeticVertexBuffer != null && !this.cosmeticVertexBuffer.isClosed() && this.cosmeticVertexBuffer.size() >= meshData.getBuffer().remaining()) {
               RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.cosmeticVertexBuffer.slice(0L, meshData.getBuffer().remaining()), meshData.getBuffer());
            } else {
               if (this.cosmeticVertexBuffer != null && !this.cosmeticVertexBuffer.isClosed()) {
                  this.cosmeticVertexBuffer.close();
               }

               this.cosmeticVertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_cosmetic_mask_mesh"), 40, meshData.getBuffer());
            }

            for (int textureBatch = 0; textureBatch < CosmeticMaskGeometry.textureBatchCount(); textureBatch++) {
               if (CosmeticMaskGeometry.quadCount(textureBatch) != 0) {
                  GpuTextureView textureView = MinecraftClient.getInstance().getTextureManager().getTexture(CosmeticMaskGeometry.textureAt(textureBatch)).getGlTextureView();
                  if (textureView != null) {
                     try (RenderPass renderPass = RenderSystem.getDevice()
                           .createCommandEncoder()
                           .createRenderPass(() -> ("mouseclient_cosmetic_mask"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                        renderPass.setPipeline(COSMETIC_MASK_PIPELINE);
                        renderPass.bindTexture(("Sampler0"), textureView, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                        renderPass.setUniform(("HitboxMaskParams"), this.maskParamsBuffer);
                        renderPass.setVertexBuffer(0, this.cosmeticVertexBuffer);
                        renderPass.setIndexBuffer(
                           RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(quadCount * 6),
                           RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                        );
                        renderPass.drawIndexed(0, CosmeticMaskGeometry.firstQuad(textureBatch) * 6, CosmeticMaskGeometry.quadCount(textureBatch) * 6, 1);
                     }
                  }
               }
            }
         }

         CosmeticMaskGeometry.clear();
      } else {
         CosmeticMaskGeometry.clear();
      }
   }

   public void renderPlayerHitboxMask(Framebuffer framebuffer, boolean clearMask) {
      if (framebuffer != null
         && framebuffer.getColorAttachmentView() != null
         && MinecraftClient.getInstance().world != null
         && MinecraftClient.getInstance().gameRenderer != null) {
         java.util.ArrayList<net.minecraft.util.math.Box> boxes = this.playerBoxes;
         boxes.clear();

         for (Entity entity : MinecraftClient.getInstance().world.getEntities()) {
            if (entity instanceof AbstractClientPlayerEntity player
               && (player != MinecraftClient.getInstance().player || !MinecraftClient.getInstance().options.getPerspective().isFirstPerson())
               && Tags.getInstance().shouldDisplayPlayer(player)) {
               boxes.add(
                  player.getBoundingBox()
                     .offset(
                        player.getLerpedPos(MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false))
                           .subtract(player.getEntityPos())
                           .subtract(MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos())
                     )
               );
            }
         }

         if (!boxes.isEmpty()) {
            if (this.maskParamsBuffer == null || this.maskParamsBuffer.isClosed()) {
               this.maskParamsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_hitbox_mask_params"), 136, (80L));
            }

            MemoryStack stack = MemoryStack.stackPush();

            try {
               ByteBuffer paramsData = stack.calloc(80);
               this.projectionViewMatrix.set(WorldProjection.projectionMatrix()).mul(WorldProjection.viewMatrix()).get(0, paramsData);
               paramsData.putFloat(64, 1.0F);
               RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.maskParamsBuffer.slice(0L, (80L)), paramsData);
            } catch (Throwable uploadFailure) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable closeFailure) {
                     uploadFailure.addSuppressed(closeFailure);
                  }
               }

               throw uploadFailure;
            }

            if (stack != null) {
               stack.close();
            }

            if (this.hitboxAllocator == null) {
               this.hitboxAllocator = new BufferAllocator(384 * VertexFormats.POSITION.getVertexSize());
            }

            CloseableBuffer previousBuffer = this.hitboxAllocator.getAllocated();
            if (previousBuffer != null) {
               previousBuffer.close();
            }

            BufferBuilder builder = new BufferBuilder(this.hitboxAllocator, DrawMode.QUADS, VertexFormats.POSITION);

            for (Box box : boxes) {
               for (int[] faceCorners : BOX_FACES) {
                  for (int cornerIndex : faceCorners) {
                     builder.vertex(
                        (float)(cornerIndex >> 2 == 0 ? box.minX : box.maxX),
                        (float)((cornerIndex >> 1 & 1) == 0 ? box.minY : box.maxY),
                        (float)((cornerIndex & 1) == 0 ? box.minZ : box.maxZ)
                     );
                  }
               }
            }

            try (BuiltBuffer meshData = builder.end()) {
               if (this.hitboxVertexBuffer != null && !this.hitboxVertexBuffer.isClosed() && this.hitboxVertexBuffer.size() >= meshData.getBuffer().remaining()) {
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.hitboxVertexBuffer.slice(0L, meshData.getBuffer().remaining()), meshData.getBuffer());
               } else {
                  if (this.hitboxVertexBuffer != null && !this.hitboxVertexBuffer.isClosed()) {
                     this.hitboxVertexBuffer.close();
                  }

                  this.hitboxVertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_hitbox_mask_mesh"), 40, meshData.getBuffer());
               }

               try (RenderPass renderPass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(
                        () -> ("mouseclient_hitbox_mask"), framebuffer.getColorAttachmentView(), clearMask ? OptionalInt.of(0) : OptionalInt.empty(), null, OptionalDouble.empty()
                     )) {
                  renderPass.setPipeline(HITBOX_MASK_PIPELINE);
                  renderPass.setUniform(("HitboxMaskParams"), this.maskParamsBuffer);
                  renderPass.setVertexBuffer(0, this.hitboxVertexBuffer);
                  renderPass.setIndexBuffer(
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(boxes.size() * 36),
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                  );
                  renderPass.drawIndexed(0, 0, boxes.size() * 36, 1);
               }
            }
         }
      }
   }

   private void drawScreenQuad(RenderPass renderPass, int[] scissor) {
      if (!this.useFullScreenScissor) {
         renderPass.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
      }

      renderPass.setVertexBuffer(0, this.screenQuadBuffer);
      renderPass.setIndexBuffer(RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType());
      renderPass.drawIndexed(0, 0, 6, 1);
   }

   private void includeCosmeticVertex(float x, float y, float z) {
      if (!this.cosmeticCrossesNearPlane) {
         this.projectedVertex.set(x, y, z, 1.0F);
         this.projectionViewMatrix.transform(this.projectedVertex);
         if (this.projectedVertex.w <= (0.05F)) {
            this.cosmeticCrossesNearPlane = true;
         } else {
            float ndcX = this.projectedVertex.x / this.projectedVertex.w;
            float ndcY = this.projectedVertex.y / this.projectedVertex.w;
            if (!this.hasCosmeticBounds) {
               this.cosmeticMinNdcX = this.cosmeticMaxNdcX = ndcX;
               this.cosmeticMinNdcY = this.cosmeticMaxNdcY = ndcY;
               this.hasCosmeticBounds = true;
            } else {
               this.cosmeticMinNdcX = Math.min(this.cosmeticMinNdcX, ndcX);
               this.cosmeticMaxNdcX = Math.max(this.cosmeticMaxNdcX, ndcX);
               this.cosmeticMinNdcY = Math.min(this.cosmeticMinNdcY, ndcY);
               this.cosmeticMaxNdcY = Math.max(this.cosmeticMaxNdcY, ndcY);
            }
         }
      }
   }

   private int[] scissorForOutline(int padding, int width, int height) {
      if (this.useFullScreenScissor) {
         return new int[]{0, 0, width, height};
      }

      float minNdcX = Math.max(Math.min(this.outlineMinNdcX, (4.0F)), (-4.0F));
      float maxNdcX = Math.max(Math.min(this.outlineMaxNdcX, (4.0F)), (-4.0F));
      float minNdcY = Math.max(Math.min(this.outlineMinNdcY, (4.0F)), (-4.0F));
      float maxNdcY = Math.max(Math.min(this.outlineMaxNdcY, (4.0F)), (-4.0F));
      int minX = Math.max(0, (int)Math.floor((minNdcX * (0.5F) + (0.5F)) * width) - padding);
      int maxX = Math.min(width, (int)Math.ceil((maxNdcX * (0.5F) + (0.5F)) * width) + padding);
      int minY = Math.max(0, (int)Math.floor((minNdcY * (0.5F) + (0.5F)) * height) - padding);
      int maxY = Math.min(height, (int)Math.ceil((maxNdcY * (0.5F) + (0.5F)) * height) + padding);
      return maxX > minX && maxY > minY ? new int[]{minX, minY, maxX - minX, maxY - minY} : null;
   }

   public static PlayerOutlineRenderer getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new PlayerOutlineRenderer();
      }

      return INSTANCE;
   }

   private boolean collectOutlineBounds() {
      MinecraftClient client = MinecraftClient.getInstance();
      this.useFullScreenScissor = this.cosmeticCrossesNearPlane;
      if (this.useFullScreenScissor) {
         return true;
      }

      boolean hasBounds = this.hasCosmeticBounds;
      if (hasBounds) {
         this.outlineMinNdcX = this.cosmeticMinNdcX;
         this.outlineMaxNdcX = this.cosmeticMaxNdcX;
         this.outlineMinNdcY = this.cosmeticMinNdcY;
         this.outlineMaxNdcY = this.cosmeticMaxNdcY;
      }

      if (client.world != null && client.gameRenderer != null) {
         this.projectionViewMatrix.set(WorldProjection.projectionMatrix()).mul(WorldProjection.viewMatrix());
         float tickDelta = client.getRenderTickCounter().getTickProgress(false);
         double cameraX = client.gameRenderer.getCamera().getCameraPos().x;
         double cameraY = client.gameRenderer.getCamera().getCameraPos().y;
         double cameraZ = client.gameRenderer.getCamera().getCameraPos().z;

         for (Entity entity : client.world.getEntities()) {
            if (entity instanceof AbstractClientPlayerEntity player
               && (player != client.player || !client.options.getPerspective().isFirstPerson())
               && Tags.getInstance().shouldDisplayPlayer(player)) {
               Box box = player.getBoundingBox();
               double offsetX = player.getLerpedPos(tickDelta).x - player.getEntityPos().x - cameraX;
               double offsetY = player.getLerpedPos(tickDelta).y - player.getEntityPos().y - cameraY;
               double offsetZ = player.getLerpedPos(tickDelta).z - player.getEntityPos().z - cameraZ;
               float minX = (float)(box.minX + offsetX - (2.5D));
               float maxX = (float)(box.maxX + offsetX + (2.5D));
               float minY = (float)(box.minY + offsetY - (2.0D));
               float maxY = (float)(box.maxY + offsetY + (2.0D));
               float minZ = (float)(box.minZ + offsetZ - (2.5D));
               float maxZ = (float)(box.maxZ + offsetZ + (2.5D));

               for (int cornerIndex = 0; cornerIndex < 8; cornerIndex++) {
                  this.projectedVertex.set((cornerIndex & 4) == 0 ? minX : maxX, (cornerIndex & 2) == 0 ? minY : maxY, (cornerIndex & 1) == 0 ? minZ : maxZ, 1.0F);
                  this.projectionViewMatrix.transform(this.projectedVertex);
                  if (this.projectedVertex.w <= (0.05F)) {
                     this.useFullScreenScissor = true;
                     return true;
                  }

                  float ndcX = this.projectedVertex.x / this.projectedVertex.w;
                  float ndcY = this.projectedVertex.y / this.projectedVertex.w;
                  if (!hasBounds) {
                     this.outlineMinNdcX = this.outlineMaxNdcX = ndcX;
                     this.outlineMinNdcY = this.outlineMaxNdcY = ndcY;
                     hasBounds = true;
                  } else {
                     this.outlineMinNdcX = Math.min(this.outlineMinNdcX, ndcX);
                     this.outlineMaxNdcX = Math.max(this.outlineMaxNdcX, ndcX);
                     this.outlineMinNdcY = Math.min(this.outlineMinNdcY, ndcY);
                     this.outlineMaxNdcY = Math.max(this.outlineMaxNdcY, ndcY);
                  }
               }
            }
         }

         return hasBounds;
      } else {
         this.useFullScreenScissor = true;
         return true;
      }
   }

   public void renderOutline(Framebuffer maskFramebuffer) {
      if (Tags.getInstance().isOutlineShaderEnabled()) {
         Framebuffer mainFramebuffer = MinecraftClient.getInstance().getFramebuffer();
         if (mainFramebuffer != null && mainFramebuffer.getColorAttachmentView() != null && maskFramebuffer != null && maskFramebuffer.getColorAttachmentView() != null) {
            if (maskFramebuffer.textureWidth == mainFramebuffer.textureWidth && maskFramebuffer.textureHeight == mainFramebuffer.textureHeight) {
               this.useFullScreenScissor = true;
               this.hasCosmeticBounds = false;
               this.cosmeticCrossesNearPlane = false;
               int[] outlineScissor = this.scissorForOutline(50, mainFramebuffer.textureWidth, mainFramebuffer.textureHeight);
               if (outlineScissor != null) {
                  int[] blurScissor = this.scissorForOutline(100, mainFramebuffer.textureWidth, mainFramebuffer.textureHeight);
                  if (blurScissor != null) {
                     if (this.screenQuadBuffer == null || this.screenQuadBuffer.isClosed()) {
                        try (BufferAllocator quadAllocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize())) {
                           BufferBuilder builder = new BufferBuilder(quadAllocator, DrawMode.QUADS, VertexFormats.POSITION);
                           builder.vertex((-1.0F), (-1.0F), 0.0F);
                           builder.vertex((-1.0F), 1.0F, 0.0F);
                           builder.vertex(1.0F, 1.0F, 0.0F);
                           builder.vertex(1.0F, (-1.0F), 0.0F);

                           try (BuiltBuffer meshData = builder.end()) {
                              this.screenQuadBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_player_outline_quad"), 32, meshData.getBuffer());
                           }
                        }
                     }

                     if (this.outlineParamsBuffer == null || this.outlineParamsBuffer.isClosed()) {
                        this.outlineParamsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_player_outline_params"), 136, (48L));
                     }

                     MemoryStack stack = MemoryStack.stackPush();

                     try {
                        ByteBuffer paramsData = stack.calloc(48);
                        paramsData.putFloat(0, (Tags.getInstance().outlineShaderColor() >> 16 & 0xFF) / (255.0F));
                        paramsData.putFloat(4, (Tags.getInstance().outlineShaderColor() >> 8 & 0xFF) / (255.0F));
                        paramsData.putFloat(8, (Tags.getInstance().outlineShaderColor() & 0xFF) / (255.0F));
                        paramsData.putFloat(12, Tags.getInstance().fill.getValue());
                        paramsData.putFloat(24, Tags.getInstance().outline.getValue());
                        paramsData.putFloat(28, Tags.getInstance().highlight.getValue());
                        paramsData.putFloat(32, 1.0F / mainFramebuffer.textureWidth);
                        paramsData.putFloat(36, 1.0F / mainFramebuffer.textureHeight);
                        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.outlineParamsBuffer.slice(0L, (48L)), paramsData);
                     } catch (Throwable uploadFailure) {
                        if (stack != null) {
                           try {
                              stack.close();
                           } catch (Throwable closeFailure) {
                              uploadFailure.addSuppressed(closeFailure);
                           }
                        }

                        throw uploadFailure;
                     }

                     if (stack != null) {
                        stack.close();
                     }

                     if (this.blurFramebuffer == null) {
                        this.blurFramebuffer = new SimpleFramebuffer(("mouseclient_player_outline_blur"), mainFramebuffer.textureWidth, mainFramebuffer.textureHeight, false);
                     }

                     if (this.blurFramebuffer.textureWidth != mainFramebuffer.textureWidth || this.blurFramebuffer.textureHeight != mainFramebuffer.textureHeight) {
                        this.blurFramebuffer.resize(mainFramebuffer.textureWidth, mainFramebuffer.textureHeight);
                     }

                     try (RenderPass renderPass = RenderSystem.getDevice()
                           .createCommandEncoder()
                           .createRenderPass(() -> ("mouseclient_player_outline_blur"), this.blurFramebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                        renderPass.setPipeline(BLUR_PIPELINE);
                        renderPass.bindTexture(("Sampler0"), maskFramebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                        renderPass.setUniform(("PlayerOutlineParams"), this.outlineParamsBuffer);
                        this.drawScreenQuad(renderPass, blurScissor);
                     }

                     try (RenderPass outlinePass = RenderSystem.getDevice()
                           .createCommandEncoder()
                           .createRenderPass(() -> ("mouseclient_player_outline"), mainFramebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                        outlinePass.setPipeline(OUTLINE_PIPELINE);
                        outlinePass.bindTexture(("Sampler0"), this.blurFramebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                        outlinePass.bindTexture(("Sampler1"), maskFramebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                        outlinePass.setUniform(("PlayerOutlineParams"), this.outlineParamsBuffer);
                        this.drawScreenQuad(outlinePass, outlineScissor);
                     }
                  }
               }
            }
         }
      }
   }
}
