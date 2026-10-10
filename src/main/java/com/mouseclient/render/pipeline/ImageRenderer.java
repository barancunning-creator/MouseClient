package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ImageQuadSpec;
import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.ImageBatchRenderState;
import com.mouseclient.gui.ImageTexture;
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
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

public final class ImageRenderer implements AutoCloseable {

   private static volatile ImageRenderer INSTANCE;
   private static final VertexFormat IMAGE_VERTEX_FORMAT = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .add(("LineWidth"), VertexFormatElement.LINE_WIDTH)
      .build();
   public static final RenderPipeline IMAGE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/image")))
      .withVertexShader(id(("core/image")))
      .withFragmentShader(id(("core/image")))
      .withVertexFormat(IMAGE_VERTEX_FORMAT, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("ImageParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   public static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/image_glow")))
      .withVertexShader(id(("core/image")))
      .withFragmentShader(id(("core/image")))
      .withVertexFormat(IMAGE_VERTEX_FORMAT, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("ImageParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   
   private final Map<Identifier, ImageRenderer.TextureCacheEntry> textureCache = new HashMap<>();
   private int textureLoadsRemaining = 4;
   private final Map<ImageRenderer.BatchKey, ImageBatchRenderState> batches = new LinkedHashMap<>(32);
   private final List<ImageQuadSpec> params = new ArrayList<>(128);
   private final ByteBuffer stagingBuffer;
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty;

   private static int preserveColor(int color) {
      return color;
   }

   private Identifier resolveTextureId(String path) {
      if (path != null && !path.isBlank()) {
         String normalizedPath = path.trim().replace('\\', '/');
         if (normalizedPath.startsWith(("/"))) {
            normalizedPath = normalizedPath.substring(1);
         }

         if (normalizedPath.startsWith(("assets/"))) {
            normalizedPath = normalizedPath.substring(("assets/").length());
            int namespaceSeparator = normalizedPath.indexOf(47);
            if (namespaceSeparator >= 0) {
               String namespace = normalizedPath.substring(0, namespaceSeparator);
               String texturePath = normalizedPath.substring(namespaceSeparator + 1);
               return Identifier.of(namespace, texturePath);
            }
         }

         if (normalizedPath.indexOf(58) >= 0) {
            return Identifier.tryParse(normalizedPath);
         }

         if (!normalizedPath.startsWith(("images/"))) {
            normalizedPath = "images/" + normalizedPath;
         }

         if (!normalizedPath.contains(("."))) {
            normalizedPath = normalizedPath + ".png";
         }

         return Identifier.of(("mouseclient"), normalizedPath);
      } else {
         return null;
      }
   }

   private int paramsByteCount() {
      return this.params.size() * 6 * 4 * 4;
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == IMAGE_PIPELINE || pipeline == GLOW_PIPELINE;
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (49152L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_image_params"), 136, (49152L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private static float finiteSmoothness(float smoothness) {
      return !Float.isFinite(smoothness) ? 0.0F : Math.max((0.001F), smoothness);
   }

   private static void putColor(ByteBuffer buffer, int offset, int color) {
      buffer.putFloat(offset, (color >>> 16 & 0xFF) / (255.0F));
      buffer.putFloat(offset + 4, (color >>> 8 & 0xFF) / (255.0F));
      buffer.putFloat(offset + 8, (color & 0xFF) / (255.0F));
      buffer.putFloat(offset + 12, (color >>> 24 & 0xFF) / (255.0F));
   }

   public static void closeInstance() {
      ImageRenderer renderer = INSTANCE;
      if (renderer != null) {
         renderer.close();
         INSTANCE = null;
      }
   }

   public void uploadParams() {
      if (!this.params.isEmpty() && this.paramsDirty) {
         GpuBuffer buffer = this.ensureBuffer();
         if (buffer != null) {
            try {
               ByteBuffer encodedParams = this.encodeParams();
               RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, encodedParams.remaining()), encodedParams);
               this.paramsDirty = false;
            } catch (RuntimeException error) {
               this.paramsDirty = true;
            }
         }
      }
   }

   private ImageRenderer() {
      this.stagingBuffer = ByteBuffer.allocateDirect((49152)).order(ByteOrder.nativeOrder());
   }

   public boolean hasTexture(String path) {
      return this.imageTexture(path) != null;
   }

   @Override
   public void close() {
      this.batches.clear();
      this.params.clear();
      this.textureCache.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   public void beginFrame() {
      this.textureLoadsRemaining = 4;
      this.params.clear();
      this.paramsDirty = false;
   }

   private GpuBuffer getUploadedBuffer() {
      if (!this.paramsDirty && this.paramsBuffer != null) {
         return this.paramsBuffer;
      }

      this.uploadParams();
      return !this.paramsDirty && this.paramsBuffer != null ? this.paramsBuffer : null;
   }

   private ByteBuffer encodeParams() {
      ByteBuffer encodedParams = this.stagingBuffer;
      encodedParams.clear();

      for (int parameterIndex = 0; parameterIndex < this.params.size(); parameterIndex++) {
         ImageQuadSpec spec = this.params.get(parameterIndex);
         int byteOffset = parameterIndex * 6 * 4 * 4;
         encodedParams.putFloat(byteOffset, spec.radiusTL());
         encodedParams.putFloat(byteOffset + 4, spec.radiusTR());
         encodedParams.putFloat(byteOffset + 8, spec.radiusBR());
         encodedParams.putFloat(byteOffset + 12, spec.radiusBL());
         encodedParams.putFloat(byteOffset + 16, spec.width());
         encodedParams.putFloat(byteOffset + 20, spec.height());
         encodedParams.putFloat(byteOffset + 24, spec.smoothness());
         encodedParams.putFloat(byteOffset + 28, 0.0F);
         putColor(encodedParams, byteOffset + 32, spec.colorTopLeft());
         putColor(encodedParams, byteOffset + 48, spec.colorTopRight());
         putColor(encodedParams, byteOffset + 64, spec.colorBottomRight());
         putColor(encodedParams, byteOffset + 80, spec.colorBottomLeft());
      }

      encodedParams.limit(this.paramsByteCount());
      encodedParams.position(0);
      return encodedParams;
   }

   public static ImageRenderer getInstance() {
      ImageRenderer renderer = INSTANCE;
      if (renderer == null) {
         synchronized (ImageRenderer.class) {
            renderer = INSTANCE;
            if (renderer == null) {
               renderer = new ImageRenderer();
               INSTANCE = renderer;
            }
         }
      }

      return renderer;
   }

   public int allocateParams(ImageQuadSpec spec) {
      int parameterIndex = this.params.size();
      if (parameterIndex == 512) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return parameterIndex;
   }

   private void submitImage(DrawContext context, ImageSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         ImageTexture imageTexture = this.imageTexture(spec.texture());
         if (imageTexture != null) {
            float drawWidth = spec.explicitWidth() > 0.0F ? spec.explicitWidth() : imageTexture.drawWidth(spec.size());
            float drawHeight = spec.explicitHeight() > 0.0F ? spec.explicitHeight() : imageTexture.drawHeight(spec.size());
            if (!(drawWidth <= 0.0F) && !(drawHeight <= 0.0F)) {
               float maximumRadius = Math.max(0.0F, Math.min(drawWidth, drawHeight) * (0.5F));
               ImageQuadSpec quad = new ImageQuadSpec(
                  spec.x(),
                  spec.y(),
                  drawWidth,
                  drawHeight,
                  clamp(spec.radiusTL(), 0.0F, maximumRadius),
                  clamp(spec.radiusTR(), 0.0F, maximumRadius),
                  clamp(spec.radiusBR(), 0.0F, maximumRadius),
                  clamp(spec.radiusBL(), 0.0F, maximumRadius),
                  finiteSmoothness(spec.smoothness()),
                  preserveColor(spec.colorTopLeft()),
                  preserveColor(spec.colorTopRight()),
                  preserveColor(spec.colorBottomRight()),
                  preserveColor(spec.colorBottomLeft()),
                  clamp(spec.uMin(), 0.0F, 1.0F),
                  clamp(spec.vMin(), 0.0F, 1.0F),
                  clamp(spec.uMax(), 0.0F, 1.0F),
                  clamp(spec.vMax(), 0.0F, 1.0F),
                  Float.isFinite(spec.rotationDegrees()) ? spec.rotationDegrees() : 0.0F,
                  Float.isFinite(spec.rotationOriginX()) ? spec.rotationOriginX() : 0.0F,
                  Float.isFinite(spec.rotationOriginY()) ? spec.rotationOriginY() : 0.0F
               );

               try {
                  GuiRenderState state = ((DrawContextAccessor)(Object)context).getGuiRenderState();
                  int layerSerial = ((GuiLayerTracker)state).getLayerSerial();
                  Matrix3x2f transform = GuiScale.getScaledMatrices(context);
                  ScreenRect bounds = ScissorStack.current();
                  ImageRenderer.BatchKey batchKey = new ImageRenderer.BatchKey(
                     state,
                     layerSerial,
                     imageTexture.id(),
                     spec.nearest()
                        && Math.abs(quad.uMax() - quad.uMin()) * imageTexture.width() <= drawWidth * GuiScale.getBaseScale() * transform.m00()
                        && Math.abs(quad.vMax() - quad.vMin()) * imageTexture.height() <= drawHeight * GuiScale.getBaseScale() * transform.m00(),
                     ImageRenderer.TransformKey.of(transform),
                     bounds,
                     spec.glow()
                  );
                  ImageBatchRenderState imageBatch = this.batches.get(batchKey);
                  if (imageBatch == null) {
                     imageBatch = new ImageBatchRenderState(transform, imageTexture, bounds, batchKey.nearest(), spec.glow());
                     imageBatch.add(quad);
                     this.batches.put(batchKey, imageBatch);
                     state.addSimpleElement(imageBatch);
                  } else {
                     imageBatch.add(quad);
                  }
               } catch (RuntimeException error) {
                  ClientInitializer.logger().warn(("[ImageRenderer] Failed to submit image: {}"), spec.texture(), error);
               }
            }
         }
      }
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   public void invalidateTextures() {
      this.textureCache.clear();
   }

   private ImageTexture imageTexture(String path) {
      Identifier id = this.resolveTextureId(path);
      if (id == null) {
         return null;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return null;
      }

      TextureManager textureManager = client.getTextureManager();
      if (textureManager == null) {
         return null;
      }

      if (this.textureLoadsRemaining <= 0 && !this.textureCache.containsKey(id)) {
         return null;
      }

      AbstractTexture texture = textureManager.getTexture(id);
      if (texture != null && texture.getGlTextureView() != null && texture.getGlTexture() != null) {
         ImageRenderer.TextureCacheEntry cacheEntry = this.textureCache.get(id);
         if (cacheEntry != null && cacheEntry.texture() == texture) {
            return cacheEntry.value();
         }

         this.textureLoadsRemaining--;
         int textureWidth = Math.max(1, texture.getGlTexture().getWidth(0));
         int textureHeight = Math.max(1, texture.getGlTexture().getHeight(0));
         TextureSetup textureSetup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
         TextureSetup nearestSetup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
         ImageTexture resolvedImage = new ImageTexture(id, textureSetup, nearestSetup, textureWidth, textureHeight);
         this.textureCache.put(id, new ImageRenderer.TextureCacheEntry(texture, resolvedImage));
         return resolvedImage;
      } else {
         return null;
      }
   }

   public void breakBatches() {
      this.batches.clear();
   }

   public void begin(DrawContext drawContext) {
      if (this.drawContext != drawContext) {
         this.batches.clear();
      }

      this.drawContext = drawContext;
   }

   public void endFrame() {
      this.drawContext = null;
      this.batches.clear();
   }

   public void render(ImageSpec spec) {
      this.submitImage(this.drawContext, spec);
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("ImageParamsArray"), buffer);
         }
      }
   }

   private record TextureCacheEntry(AbstractTexture texture, ImageTexture value) {
   }

   private record BatchKey(
      GuiRenderState state, int layerSerial, Identifier texture, boolean nearest, ImageRenderer.TransformKey pose, ScreenRect scissorArea, boolean glow
   ) {
   }

   private record TransformKey(float m00, float m01, float m10, float m11, float m20, float m21) {
      public static ImageRenderer.TransformKey of(Matrix3x2f transform) {
         return new ImageRenderer.TransformKey(transform.m00(), transform.m01(), transform.m10(), transform.m11(), transform.m20(), transform.m21());
      }
   }
}
