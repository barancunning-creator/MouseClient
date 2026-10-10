package com.mouseclient.render.pipeline;

import com.mouseclient.modules.impl.render.JumpCircle;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.util.time.GuiVisuals;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

public final class MouseClientJumpCircleScene {
   private static MouseClientJumpCircleScene INSTANCE;
   private static final RenderPipeline JUMP_CIRCLE_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/jump_circle")))
      .withVertexShader(ClientInitializer.id(("core/jump_circle")))
      .withFragmentShader(ClientInitializer.id(("core/jump_circle")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withSampler(("Sampler1"))
      .withUniform(("JumpCircleParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private SimpleFramebuffer sceneColorCopy;
   private GpuBuffer quadBuffer;
   private GpuBuffer paramsBuffer;
   
   private long idleSinceMillis;

   public void close() {
      if (this.sceneColorCopy != null) {
         this.sceneColorCopy.delete();
         this.sceneColorCopy = null;
      }

      if (this.quadBuffer != null && !this.quadBuffer.isClosed()) {
         this.quadBuffer.close();
      }

      this.quadBuffer = null;
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed()) {
         this.paramsBuffer.close();
      }

      this.paramsBuffer = null;
   }

   private MouseClientJumpCircleScene() {
   }

   public void render(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
      JumpCircle jumpCircle = JumpCircle.getInstance();
      MinecraftClient client = MinecraftClient.getInstance();
      if (!jumpCircle.isEnabled() || client.world == null || client.player == null) {
         this.close();
      } else if (jumpCircle.getRings().isEmpty()) {
         if (this.idleSinceMillis == 0L) {
            this.idleSinceMillis = System.currentTimeMillis();
         } else if (System.currentTimeMillis() - this.idleSinceMillis > (5000L)) {
            this.close();
         }
      } else {
         this.idleSinceMillis = 0L;
         Framebuffer framebuffer = client.getFramebuffer();
         if (framebuffer != null
            && framebuffer.getColorAttachment() != null
            && framebuffer.getColorAttachmentView() != null
            && framebuffer.getDepthAttachmentView() != null
            && framebuffer.textureWidth > 0
            && framebuffer.textureHeight > 0) {
            if (this.sceneColorCopy == null) {
               this.sceneColorCopy = new SimpleFramebuffer(("mouseclient_jump_circle_scene"), framebuffer.textureWidth, framebuffer.textureHeight, false);
            }

            if (this.sceneColorCopy.textureWidth != framebuffer.textureWidth || this.sceneColorCopy.textureHeight != framebuffer.textureHeight) {
               this.sceneColorCopy.resize(framebuffer.textureWidth, framebuffer.textureHeight);
            }

            if (this.quadBuffer == null || this.quadBuffer.isClosed()) {
               try (BufferAllocator allocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize())) {
                  BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION);
                  builder.vertex((-1.0F), (-1.0F), 0.0F);
                  builder.vertex((-1.0F), 1.0F, 0.0F);
                  builder.vertex(1.0F, 1.0F, 0.0F);
                  builder.vertex(1.0F, (-1.0F), 0.0F);

                  try (BuiltBuffer quadData = builder.end()) {
                     this.quadBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_jump_circle_quad"), 32, quadData.getBuffer());
                  }
               }
            }

            if (this.paramsBuffer == null || this.paramsBuffer.isClosed()) {
               this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_jump_circle_params"), 136, (2416L));
            }

            Matrix4f viewProjection = new Matrix4f(projectionMatrix).mul(viewMatrix);
            Vec3d cameraPosition = client.gameRenderer.getCamera().getCameraPos();
            int color = jumpCircle.useCustomColor.getValue() ? jumpCircle.color.getArgb() : GuiVisuals.accentColor;
            float extentRadius = jumpCircle.radius.getValue() + jumpCircle.glow.getValue() * (3.0F) + (0.5F);
            Vector4f clipPoint = new Vector4f();
            float minClipX = 1.0F;
            float minClipY = 1.0F;
            float maxClipX = (-1.0F);
            float maxClipY = (-1.0F);
            boolean fullViewport = false;
            int visibleRingCount = 0;
            MemoryStack stack = MemoryStack.stackPush();

            label295: {
               try {
                  ByteBuffer paramsData = stack.calloc(2416);
                  new Matrix4f(viewProjection).invert().get(0, paramsData);
                  paramsData.putFloat(64, jumpCircle.radius.getValue());
                  paramsData.putFloat(68, jumpCircle.glow.getValue());
                  paramsData.putFloat(76, jumpCircle.distortion.getValue());
                  paramsData.putFloat(80, (color >> 16 & 0xFF) / (255.0F));
                  paramsData.putFloat(84, (color >> 8 & 0xFF) / (255.0F));
                  paramsData.putFloat(88, (color & 0xFF) / (255.0F));
                  paramsData.putFloat(92, 1.0F);
                  paramsData.putFloat(96, jumpCircle.radiusWave.getValue() ? 1.0F : 0.0F);
                  paramsData.putFloat(100, jumpCircle.effect.isSelected(("Вода")) ? 1.0F : 0.0F);
                  Iterator ringsIterator = jumpCircle.getRings().iterator();

                  label215:
                  while (true) {
                     JumpCircle.JumpCircleEntry ring;
                     float relativeX;
                     float relativeY;
                     float relativeZ;
                     while (true) {
                        if (!ringsIterator.hasNext()) {
                           break label215;
                        }

                        ring = (JumpCircle.JumpCircleEntry)ringsIterator.next();
                        if (visibleRingCount >= 16) {
                           break label215;
                        }

                        relativeX = (float)(ring.x() - cameraPosition.x);
                        relativeY = (float)(ring.y() - cameraPosition.y);
                        relativeZ = (float)(ring.z() - cameraPosition.z);
                        clipPoint.set(relativeX, relativeY, relativeZ, 1.0F);
                        viewProjection.transform(clipPoint);
                        if (!(clipPoint.w <= (0.05F))) {
                           boolean nearPlaneCrossed = false;
                           float ringMinClipX = 1.0F;
                           float ringMinClipY = 1.0F;
                           float ringMaxClipX = (-1.0F);
                           float ringMaxClipY = (-1.0F);

                           for (int cornerIndex = 0; cornerIndex < 8; cornerIndex++) {
                              clipPoint.set(
                                 (cornerIndex & 1) == 0 ? relativeX - extentRadius : relativeX + extentRadius,
                                 (cornerIndex & 2) == 0 ? relativeY - (1.35F) : relativeY + (1.35F),
                                 (cornerIndex & 4) == 0 ? relativeZ - extentRadius : relativeZ + extentRadius,
                                 1.0F
                              );
                              viewProjection.transform(clipPoint);
                              if (clipPoint.w <= (0.05F)) {
                                 nearPlaneCrossed = true;
                                 break;
                              }

                              ringMinClipX = Math.min(ringMinClipX, clipPoint.x / clipPoint.w);
                              ringMaxClipX = Math.max(ringMaxClipX, clipPoint.x / clipPoint.w);
                              ringMinClipY = Math.min(ringMinClipY, clipPoint.y / clipPoint.w);
                              ringMaxClipY = Math.max(ringMaxClipY, clipPoint.y / clipPoint.w);
                           }

                           if (nearPlaneCrossed) {
                              fullViewport = true;
                              break;
                           }

                           if (!(ringMaxClipX < (-1.0F)) && !(ringMinClipX > 1.0F) && !(ringMaxClipY < (-1.0F)) && !(ringMinClipY > 1.0F)) {
                              minClipX = Math.min(minClipX, ringMinClipX);
                              maxClipX = Math.max(maxClipX, ringMaxClipX);
                              minClipY = Math.min(minClipY, ringMinClipY);
                              maxClipY = Math.max(maxClipY, ringMaxClipY);
                              break;
                           }
                        }
                     }

                     paramsData.putFloat(112 + visibleRingCount * 16, relativeX);
                     paramsData.putFloat(116 + visibleRingCount * 16, relativeY);
                     paramsData.putFloat(120 + visibleRingCount * 16, relativeZ);
                     paramsData.putFloat(124 + visibleRingCount * 16, jumpCircle.lifeProgress(ring));

                     for (int wallSampleIndex = 0; wallSampleIndex < 32; wallSampleIndex++) {
                        paramsData.putFloat(368 + visibleRingCount * 128 + wallSampleIndex * 4, ring.wallDistBlocks()[wallSampleIndex]);
                     }

                     visibleRingCount++;
                  }

                  if (visibleRingCount == 0) {
                     break label295;
                  }

                  paramsData.putFloat(72, visibleRingCount);
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.paramsBuffer.slice(0L, (2416L)), paramsData);
               } catch (Throwable renderFailure) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable closeFailure) {
                        renderFailure.addSuppressed(closeFailure);
                     }
                  }

                  throw renderFailure;
               }

               if (stack != null) {
                  stack.close();
               }

               int scissorX = 0;
               int scissorY = 0;
               int scissorWidth = framebuffer.textureWidth;
               int scissorHeight = framebuffer.textureHeight;
               if (!fullViewport) {
                  scissorX = Math.max(0, (int)Math.floor((Math.max(minClipX, (-1.0F)) * (0.5F) + (0.5F)) * framebuffer.textureWidth) - 8);
                  scissorY = Math.max(0, (int)Math.floor((Math.max(minClipY, (-1.0F)) * (0.5F) + (0.5F)) * framebuffer.textureHeight) - 8);
                  scissorWidth = Math.min(framebuffer.textureWidth, (int)Math.ceil((Math.min(maxClipX, 1.0F) * (0.5F) + (0.5F)) * framebuffer.textureWidth) + 8) - scissorX;
                  scissorHeight = Math.min(framebuffer.textureHeight, (int)Math.ceil((Math.min(maxClipY, 1.0F) * (0.5F) + (0.5F)) * framebuffer.textureHeight) + 8) - scissorY;
                  if (scissorWidth <= 0 || scissorHeight <= 0) {
                     return;
                  }
               }

               RenderSystem.getDevice()
                  .createCommandEncoder()
                  .copyTextureToTexture(
                     framebuffer.getColorAttachment(), this.sceneColorCopy.getColorAttachment(), 0, 0, 0, 0, 0, framebuffer.textureWidth, framebuffer.textureHeight
                  );

               try (RenderPass renderPass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(() -> ("mouseclient_jump_circle"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                  renderPass.setPipeline(JUMP_CIRCLE_PIPELINE);
                  renderPass.enableScissor(scissorX, scissorY, scissorWidth, scissorHeight);
                  renderPass.bindTexture(("Sampler0"), framebuffer.getDepthAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                  renderPass.bindTexture(("Sampler1"), this.sceneColorCopy.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                  renderPass.setUniform(("JumpCircleParams"), this.paramsBuffer);
                  renderPass.setVertexBuffer(0, this.quadBuffer);
                  renderPass.setIndexBuffer(
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                  );
                  renderPass.drawIndexed(0, 0, 6, 1);
               }

               return;
            }

            if (stack != null) {
               stack.close();
            }
         }
      }
   }

   public static MouseClientJumpCircleScene getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new MouseClientJumpCircleScene();
      }

      return INSTANCE;
   }
}
