package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.WarpTextRenderState;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.mixin.DrawContextAccessor;
import com.mouseclient.network.ClientInitializer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryStack;

public final class WarpTextRenderer implements AutoCloseable {

   private static volatile WarpTextRenderer INSTANCE;
   private static final VertexFormat vertexFormat = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .add(("LineWidth"), VertexFormatElement.LINE_WIDTH)
      .build();
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/warp_text")))
      .withVertexShader(id(("core/warp_text")))
      .withFragmentShader(id(("core/warp_text")))
      .withVertexFormat(vertexFormat, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("WarpTextParams"), UniformType.UNIFORM_BUFFER)
      .withSampler(("Sampler0"))
      .build();
   private final List<WarpTextSpec> params = new ArrayList<>(2);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty;

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("WarpTextParams"), buffer);
         }
      }
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   private WarpTextRenderer() {
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   @Override
   public void close() {
      this.params.clear();
      this.releaseBuffer();
      this.drawContext = null;
   }

   public void render(WarpTextSpec spec) {
      if (this.drawContext != null && spec != null && spec.glyphQuads().length != 0) {
         try {
            TextureSetup textureSetup = this.getTextureSetup(spec.atlasTexture());
            if (textureSetup == null) {
               return;
            }

            GuiRenderState guiRenderState = ((DrawContextAccessor)(Object)this.drawContext).getGuiRenderState();
            guiRenderState.addSimpleElement(new WarpTextRenderState(GuiScale.getScaledMatrices(this.drawContext), textureSetup, ScissorStack.current(), spec));
         } catch (RuntimeException error) {
            ClientInitializer.logger().warn(("[WarpTextRenderer] submit failed"), error);
         }
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
      MemoryStack memory = MemoryStack.stackPush();

      GpuBuffer buffer;
      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_warp_text_params"), 128, this.packParams(memory));
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

   public static void closeInstance() {
      WarpTextRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   private ByteBuffer packParams(MemoryStack stack) {
      ByteBuffer packedParams = stack.calloc(4096);
      float animationSeconds = (float)(System.nanoTime() % (1000000000000L)) / (1.0E9F);

      for (int specIndex = 0; specIndex < this.params.size(); specIndex++) {
         int byteOffset = specIndex * 64 * 4 * 4;
         int glyphCount = Math.min(30, this.params.get(specIndex).glyphQuads().length / 8);
         packedParams.putFloat(byteOffset, glyphCount);
         packedParams.putFloat(byteOffset + 4, this.params.get(specIndex).pxRange());
         packedParams.putFloat(byteOffset + 8, this.params.get(specIndex).influenceRadiusPx());
         packedParams.putFloat(byteOffset + 12, this.params.get(specIndex).cellPx());
         packedParams.putFloat(byteOffset + 16, this.params.get(specIndex).mouseLocalX());
         packedParams.putFloat(byteOffset + 20, this.params.get(specIndex).mouseLocalY());
         packedParams.putFloat(byteOffset + 24, this.params.get(specIndex).width());
         packedParams.putFloat(byteOffset + 28, this.params.get(specIndex).height());
         packedParams.putFloat(byteOffset + 32, animationSeconds);

         for (int glyphIndex = 0; glyphIndex < glyphCount; glyphIndex++) {
            int glyphOffset = byteOffset + (3 + glyphIndex * 2) * 4 * 4;

            for (int componentIndex = 0; componentIndex < 8; componentIndex++) {
               packedParams.putFloat(glyphOffset + componentIndex * 4, this.params.get(specIndex).glyphQuads()[glyphIndex * 8 + componentIndex]);
            }
         }
      }

      packedParams.position(0);
      return packedParams;
   }

   public int allocateParams(WarpTextSpec spec) {
      if (this.params.size() >= 4) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return this.params.size() - 1;
   }

   public static WarpTextRenderer getInstance() {
      WarpTextRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (WarpTextRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new WarpTextRenderer();
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
                  ByteBuffer packedParams = this.packParams(memory);
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

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (4096L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_warp_text_params"), 136, (4096L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private TextureSetup getTextureSetup(Identifier id) {
      if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getTextureManager() != null) {
         AbstractTexture atlasTexture = MinecraftClient.getInstance().getTextureManager().getTexture(id);
         return atlasTexture != null && atlasTexture.getGlTextureView() != null
            ? TextureSetup.of(atlasTexture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
            : null;
      } else {
         return null;
      }
   }
}
