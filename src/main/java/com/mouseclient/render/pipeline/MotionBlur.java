package com.mouseclient.render.pipeline;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.network.ClientInitializer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import org.lwjgl.system.MemoryStack;

@ModuleInfo(name = "MotionBlur", description = "Смазывает картинку шлейфом из предыдущих кадров.", category = ModuleCategory.RENDER)
public final class MotionBlur extends Module {
   private static MotionBlur INSTANCE;
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/motion_blur")))
      .withVertexShader(ClientInitializer.id(("core/motion_blur")))
      .withFragmentShader(ClientInitializer.id(("core/motion_blur")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("MotionBlurParams"), UniformType.UNIFORM_BUFFER)
      .build();
   public SliderSetting strength;
   private SimpleFramebuffer historyFramebuffer;
   private GpuBuffer vertexBuffer;
   private GpuBuffer uniformBuffer;
   private boolean historyValid;
   private long lastFrameMillis;
   private ClientWorld historyWorld;

   @Override
   protected void onEnable() {
      this.historyValid = false;
   }

   private MotionBlur() {
      this.strength = this.addSetting(new SliderSetting(("Сила"), ("Насколько долго тянется шлейф за кадром.")).withRange(1.0F, (10.0F)).withStep(1.0F).setValue((5.0F)));
   }

   public static MotionBlur getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new MotionBlur();
      }

      return INSTANCE;
   }

   @Override
   protected void onDisable() {
      this.historyValid = false;
      if (this.historyFramebuffer != null) {
         this.historyFramebuffer.delete();
         this.historyFramebuffer = null;
      }

      if (this.vertexBuffer != null && !this.vertexBuffer.isClosed()) {
         this.vertexBuffer.close();
      }

      this.vertexBuffer = null;
      if (this.uniformBuffer != null && !this.uniformBuffer.isClosed()) {
         this.uniformBuffer.close();
      }

      this.uniformBuffer = null;
   }

   public static void renderIfEnabled() {
      if (INSTANCE != null && INSTANCE.isEnabled()) {
         INSTANCE.renderFrame();
      }
   }

   private void renderFrame() {
      if (minecraftClient.world != null
         && minecraftClient.getFramebuffer() != null
         && minecraftClient.getFramebuffer().getColorAttachmentView() != null
         && minecraftClient.getFramebuffer().textureWidth > 0
         && minecraftClient.getFramebuffer().textureHeight > 0) {
         if (this.historyWorld != minecraftClient.world) {
            this.historyWorld = minecraftClient.world;
            this.historyValid = false;
         }

         if (this.historyFramebuffer == null) {
            this.historyFramebuffer = new SimpleFramebuffer(
               ("mouseclient_motion_blur_history"), minecraftClient.getFramebuffer().textureWidth, minecraftClient.getFramebuffer().textureHeight, false
            );
            this.historyValid = false;
         }

         if (this.historyFramebuffer.textureWidth != minecraftClient.getFramebuffer().textureWidth
            || this.historyFramebuffer.textureHeight != minecraftClient.getFramebuffer().textureHeight) {
            this.historyFramebuffer.resize(minecraftClient.getFramebuffer().textureWidth, minecraftClient.getFramebuffer().textureHeight);
            this.historyValid = false;
         }

         if (this.historyValid) {
            if (this.vertexBuffer == null || this.vertexBuffer.isClosed()) {
               try (BufferAllocator allocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize())) {
                  BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION);
                  builder.vertex((-1.0F), (-1.0F), 0.0F);
                  builder.vertex((-1.0F), 1.0F, 0.0F);
                  builder.vertex(1.0F, 1.0F, 0.0F);
                  builder.vertex(1.0F, (-1.0F), 0.0F);

                  try (BuiltBuffer mesh = builder.end()) {
                     this.vertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_motion_blur_quad"), 32, mesh.getBuffer());
                  }
               }
            }

            if (this.uniformBuffer == null || this.uniformBuffer.isClosed()) {
               this.uniformBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_motion_blur_params"), 136, (16L));
            }

            MemoryStack memory = MemoryStack.stackPush();

            try {
               ByteBuffer uniformBytes = memory.calloc(16);
               uniformBytes.putFloat(
                  0,
                  (float)Math.pow(
                     this.strength.floatValue() / (100.0F) * (0.9F),
                     Math.max(1L, Math.min((100L), System.currentTimeMillis() - this.lastFrameMillis)) / (50.0D)
                  )
               );
               RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniformBuffer.slice(0L, (16L)), uniformBytes);
            } catch (Throwable drawFailure) {
               if (memory != null) {
                  try {
                     memory.close();
                  } catch (Throwable closeFailure) {
                     drawFailure.addSuppressed(closeFailure);
                  }
               }

               throw drawFailure;
            }

            if (memory != null) {
               memory.close();
            }

            try (RenderPass renderPass = RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(
                     () -> ("mouseclient_motion_blur"), minecraftClient.getFramebuffer().getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty()
                  )) {
               renderPass.setPipeline(PIPELINE);
               renderPass.bindTexture(("Sampler0"), this.historyFramebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
               renderPass.setUniform(("MotionBlurParams"), this.uniformBuffer);
               renderPass.setVertexBuffer(0, this.vertexBuffer);
               renderPass.setIndexBuffer(
                  RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
               );
               renderPass.drawIndexed(0, 0, 6, 1);
            }
         }

         RenderSystem.getDevice()
            .createCommandEncoder()
            .copyTextureToTexture(
               minecraftClient.getFramebuffer().getColorAttachment(),
               this.historyFramebuffer.getColorAttachment(),
               0,
               0,
               0,
               0,
               0,
               minecraftClient.getFramebuffer().textureWidth,
               minecraftClient.getFramebuffer().textureHeight
            );
         this.historyValid = true;
         this.lastFrameMillis = System.currentTimeMillis();
      }
   }
}
