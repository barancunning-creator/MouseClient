package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.ItemQuadBatchRenderState;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.mixin.ItemLayerRenderStateAccessor;
import com.mouseclient.mixin.ItemRenderStateAccessor;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.KeyedItemRenderState;
import net.minecraft.client.render.item.ItemRenderState.Glint;
import net.minecraft.client.render.item.ItemRenderState.LayerRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;

public final class ItemRenderer implements AutoCloseable {

   private static volatile ItemRenderer INSTANCE;
   private static final VertexFormat vertexFormat = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .add(("LineWidth"), VertexFormatElement.LINE_WIDTH)
      .build();
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/item")))
      .withVertexShader(id(("core/item")))
      .withFragmentShader(id(("core/item")))
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("ItemParamsArray"), UniformType.UNIFORM_BUFFER)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withVertexFormat(vertexFormat, DrawMode.QUADS)
      .build();
   private final Map<Identifier, ItemRenderer.AtlasCacheEntry> atlasCache = new HashMap<>();
   private final Map<ItemRenderer.StackCacheKey, ItemQuadModel> stackModelCache;
   private final Map<ItemRenderer.ModelCacheKey, ItemQuadModel> identityModelCache;
   private ByteBuffer stagingBuffer;
   private final Map<ItemRenderer.BatchKey, ItemQuadBatchRenderState> quadBatches;
   private final Map<ItemGuiElementRenderState, Integer> fallbackColors;
   private Integer currentFallbackColor;
   private final float[] glintStrengths;
   private int paramCount;
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty;

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private ItemQuadModel getModel(MinecraftClient client, ItemRenderSpec spec) {
      ItemRenderer.StackCacheKey stackKey = ItemRenderer.StackCacheKey.of(spec.stack(), spec.seed());
      ItemQuadModel cachedModel = this.stackModelCache.get(stackKey);
      if (cachedModel != null) {
         return cachedModel;
      }

      KeyedItemRenderState itemState = new KeyedItemRenderState();
      client.getItemModelManager().clearAndUpdate(itemState, spec.stack(), ItemDisplayContext.GUI, client.world, client.player, spec.seed());
      ItemRenderer.ModelCacheKey modelKey = new ItemRenderer.ModelCacheKey(itemState.getModelKey(), spec.seed());
      ItemQuadModel model = this.identityModelCache.get(modelKey);
      if (model == null) {
         model = this.buildModel(itemState);
         this.identityModelCache.put(modelKey, model);
      }

      if (!itemState.isAnimated()) {
         this.stackModelCache.put(stackKey, model);
      }

      return model;
   }

   private boolean hasSpecialRenderer(LayerRenderState layer) {
      return layer != null && ((ItemLayerRenderStateAccessor)(Object)layer).getSpecialRenderer() != null;
   }

   public void render(ItemRenderSpec spec) {
      this.submitItem(this.drawContext, spec);
   }

   public void captureFallbackColor(ItemGuiElementRenderState state) {
      if (this.currentFallbackColor != null) {
         this.fallbackColors.put(state, this.currentFallbackColor);
      }
   }

   private static int shadeColor(int color, float brightness) {
      color = normalizeColor(color);
      int alpha = color >>> 24 & 0xFF;
      int red = clamp(Math.round((color >>> 16 & 0xFF) * brightness), 0, 255);
      int green = clamp(Math.round((color >>> 8 & 0xFF) * brightness), 0, 255);
      int blue = clamp(Math.round((color & 0xFF) * brightness), 0, 255);
      return alpha << 24 | red << 16 | green << 8 | blue;
   }

   private ByteBuffer packParams(boolean padToCapacity) {
      if (this.stagingBuffer == null) {
         this.stagingBuffer = MemoryUtil.memCalloc((65536));
      }

      ByteBuffer buffer = this.stagingBuffer;
      buffer.clear();
      float animationSeconds = (float)(System.nanoTime() % (30000000000L)) / (1.0E9F);

      for (int var4 = 0; var4 < this.paramCount; var4++) {
         int offset = var4 * 4 * 4;
         buffer.putFloat(offset, this.glintStrengths[var4]);
         buffer.putFloat(offset + 4, animationSeconds);
         buffer.putFloat(offset + 8, 0.0F);
         buffer.putFloat(offset + 12, 0.0F);
      }

      int usedBytes = this.paramCount * 4 * 4;
      if (padToCapacity) {
         if (usedBytes < (65536)) {
            MemoryUtil.memSet(MemoryUtil.memAddress(buffer) + usedBytes, 0, (65536) - usedBytes);
         }

         buffer.position(0).limit((65536));
      } else {
         buffer.position(0).limit(Math.max(16, usedBytes));
      }

      return buffer;
   }

   private GpuBuffer ensureParamsBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (65536L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_item_params"), 136, (65536L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   private void submitQuad(
      GuiRenderState state,
      int layerSerial,
      Matrix3x2f transform,
      ItemRenderer.TransformKey pose,
      ItemAtlasTexture atlas,
      ItemQuad quad,
      float x,
      float y,
      float size,
      int color,
      float glintStrength
   ) {
      ItemRenderer.BatchKey batchKey = new ItemRenderer.BatchKey(state, layerSerial, atlas.id(), pose, ScissorStack.current());
      ItemQuadBatchRenderState batch = this.quadBatches.get(batchKey);
      if (batch == null) {
         batch = new ItemQuadBatchRenderState(transform, atlas, ScissorStack.current());
         batch.add(quad, x, y, size, color, glintStrength);
         this.quadBatches.put(batchKey, batch);
         state.addSimpleElement(batch);
      } else {
         batch.add(quad, x, y, size, color, glintStrength);
      }
   }

   private static int quadTint(BakedQuad quad, int[] tints) {
      int color = -1;
      if (quad.hasTint()) {
         int tintIndex = quad.tintIndex();
         if (tintIndex >= 0 && tintIndex < tints.length) {
            color = normalizeColor(tints[tintIndex]);
         }
      }

      return color;
   }

   private static float quadArea(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
      return Math.abs(
            crossProduct(x0, y0, x1, y1) + crossProduct(x1, y1, x2, y2) + crossProduct(x2, y2, x3, y3) + crossProduct(x3, y3, x0, y0)
         )
         * (0.5F);
   }

   private void renderFallback(DrawContext context, ItemRenderSpec spec) {
      float scale = spec.size() / (16.0F);
      if (!(scale <= 0.0F)) {
         context.getMatrices().pushMatrix();

         try {
            GuiScale.applyScale(context.getMatrices());
            context.getMatrices().translate(spec.x(), spec.y());
            context.getMatrices().scale(scale);
            this.currentFallbackColor = premultiplyColor(spec.options().color(), spec.options().alpha());
            context.drawItem(spec.stack(), 0, 0, spec.seed());
         } finally {
            this.currentFallbackColor = null;
            context.getMatrices().popMatrix();
         }
      }
   }

   public void setDrawContext(DrawContext drawContext) {
      if (this.drawContext != drawContext) {
         this.quadBatches.clear();
      }

      this.drawContext = drawContext;
   }

   private void collectLayerQuads(List<ItemRenderer.RawItemQuad> output, ItemRenderer.ModelBounds bounds, LayerRenderState layer) {
      if (layer != null) {
         java.util.List<net.minecraft.client.render.model.BakedQuad> quads = layer.getQuads();
         if (!quads.isEmpty()) {
            ItemLayerRenderStateAccessor accessor = (ItemLayerRenderStateAccessor)(Object)layer;
            Glint glint = accessor.getFoilType();
            boolean foil = glint != Glint.NONE;
            int[] tints = accessor.getTintLayers();
            Matrix4f transform = this.layerTransform(accessor);
            boolean usesBlockLight = accessor.getUsesBlockLight();
            if (usesBlockLight) {
               bounds.markSpecialRenderer();
            }

            for (BakedQuad quad : quads) {
               ItemRenderer.RawItemQuad rawQuad = this.readQuad(quad, transform, quadTint(quad, tints), foil, usesBlockLight);
               if (!(rawQuad.area() < (2.0E-6F))) {
                  bounds.include(rawQuad.getX0(), rawQuad.getY0(), rawQuad.getZ0());
                  bounds.include(rawQuad.getX1(), rawQuad.getY1(), rawQuad.getZ1());
                  bounds.include(rawQuad.getX2(), rawQuad.getY2(), rawQuad.getZ2());
                  bounds.include(rawQuad.getX3(), rawQuad.getY3(), rawQuad.getZ3());
                  output.add(rawQuad);
               }
            }
         }
      }
   }

   @Override
   public void close() {
      this.quadBatches.clear();
      this.fallbackColors.clear();
      this.paramCount = 0;
      this.stackModelCache.clear();
      this.identityModelCache.clear();
      this.atlasCache.clear();
      this.drawContext = null;
      this.releaseBuffer();
      if (this.stagingBuffer != null) {
         MemoryUtil.memFree(this.stagingBuffer);
         this.stagingBuffer = null;
      }
   }

   private Matrix4f layerTransform(ItemLayerRenderStateAccessor layer) {
      Entry matrixEntry = new Entry();
      Transformation itemTransform = layer.getItemTransform();
      if (itemTransform == null) {
         itemTransform = Transformation.IDENTITY;
      }

      itemTransform.apply(false, matrixEntry);
      return new Matrix4f(matrixEntry.getPositionMatrix());
   }

   private ItemQuad normalizeQuad(ItemRenderer.RawItemQuad quad, ItemRenderer.ModelTransform transform) {
      return new ItemQuad(
         quad.atlas(),
         transform.x(quad.getX0()),
         transform.y(quad.getY0()),
         quad.getU0(),
         quad.getV0(),
         transform.x(quad.getX1()),
         transform.y(quad.getY1()),
         quad.getU1(),
         quad.getV1(),
         transform.x(quad.getX2()),
         transform.y(quad.getY2()),
         quad.getU2(),
         quad.getV2(),
         transform.x(quad.getX3()),
         transform.y(quad.getY3()),
         quad.getU3(),
         quad.getV3(),
         quad.tint(),
         quad.foil()
      );
   }

   private ItemRenderer.RawItemQuad readQuad(BakedQuad quad, Matrix4f transform, int tint, boolean foil, boolean shade) {
      Vector3fc position0 = quad.position0();
      Vector3fc position1 = quad.position1();
      Vector3fc position2 = quad.position2();
      Vector3fc position3 = quad.position3();
      Vector3f transformed0 = transformPosition(position0, transform);
      Vector3f transformed1 = transformPosition(position1, transform);
      Vector3f transformed2 = transformPosition(position2, transform);
      Vector3f transformed3 = transformPosition(position3, transform);
      long packedUv0 = quad.packedUV0();
      long packedUv1 = quad.packedUV1();
      long packedUv2 = quad.packedUV2();
      long packedUv3 = quad.packedUV3();
      float depth = (transformed0.z + transformed1.z + transformed2.z + transformed3.z) * (0.25F);
      int shadedTint = shade ? shadeColor(tint, faceBrightness(quad.face())) : tint;
      return new ItemRenderer.RawItemQuad(
         quad.sprite().getAtlasId(),
         transformed0.x,
         transformed0.y,
         Vector2f.getX(packedUv0),
         Vector2f.getY(packedUv0),
         transformed1.x,
         transformed1.y,
         Vector2f.getX(packedUv1),
         Vector2f.getY(packedUv1),
         transformed2.x,
         transformed2.y,
         Vector2f.getX(packedUv2),
         Vector2f.getY(packedUv2),
         transformed3.x,
         transformed3.y,
         Vector2f.getX(packedUv3),
         Vector2f.getY(packedUv3),
         shadedTint,
         foil,
         depth,
         quadArea(transformed0.x, transformed0.y, transformed1.x, transformed1.y, transformed2.x, transformed2.y, transformed3.x, transformed3.y),
         transformed0.z,
         transformed1.z,
         transformed2.z,
         transformed3.z
      );
   }

   public void clearFallbackColors() {
      this.fallbackColors.clear();
   }

   public static ItemRenderer getInstance() {
      ItemRenderer rendererInstance = INSTANCE;
      if (rendererInstance == null) {
         synchronized (ItemRenderer.class) {
            rendererInstance = INSTANCE;
            if (rendererInstance == null) {
               rendererInstance = new ItemRenderer();
               INSTANCE = rendererInstance;
            }
         }
      }

      return rendererInstance;
   }

   private static int clamp(int value, int minimum, int maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   public static void closeInstance() {
      ItemRenderer rendererInstance = INSTANCE;
      if (rendererInstance != null) {
         rendererInstance.close();
         INSTANCE = null;
      }
   }

   public void resetParams() {
      this.paramCount = 0;
      this.paramsDirty = false;
   }

   private static int combineColors(int color, int tint, float alpha) {
      color = normalizeColor(color);
      tint = normalizeColor(tint);
      int resultAlpha = Math.round((color >>> 24 & 0xFF) * ((tint >>> 24 & 0xFF) / (255.0F)) * alpha);
      int red = (color >>> 16 & 0xFF) * (tint >>> 16 & 0xFF) / 255;
      int green = (color >>> 8 & 0xFF) * (tint >>> 8 & 0xFF) / 255;
      int blue = (color & 0xFF) * (tint & 0xFF) / 255;
      return clamp(resultAlpha, 0, 255) << 24 | red << 16 | green << 8 | blue;
   }

   public void clearDrawContext() {
      this.drawContext = null;
      this.quadBatches.clear();
   }

   private static float faceBrightness(Direction face) {
      if (face == null) {
         return (0.84F);
      }

      return switch (face) {
         case UP -> (0.98F);
         case DOWN -> (0.72F);
         case NORTH -> (0.9F);
         case SOUTH -> (0.84F);
         case EAST, WEST -> (0.78F);
      };
   }

   private ItemQuadModel buildModel(ItemRenderState state) {
      ItemRenderStateAccessor accessor = (ItemRenderStateAccessor)(Object)state;
      LayerRenderState[] layers = accessor.getLayers();
      int layerCount = Math.min(accessor.getActiveLayerCount(), layers.length);

      for (int var5 = 0; var5 < layerCount; var5++) {
         if (this.hasSpecialRenderer(layers[var5])) {
            return new ItemQuadModel(List.of(), state.isAnimated(), true);
         }
      }

      ArrayList<ItemRenderer.RawItemQuad> rawQuads = new ArrayList(Math.max(8, layerCount * 8));
      ItemRenderer.ModelBounds bounds = new ItemRenderer.ModelBounds();

      for (int var7 = 0; var7 < layerCount; var7++) {
         this.collectLayerQuads(rawQuads, bounds, layers[var7]);
      }

      rawQuads.sort(Comparator.comparingDouble(ItemRenderer.RawItemQuad::depth));
      ItemRenderer.ModelTransform transform = ItemRenderer.ModelTransform.of(bounds);
      ArrayList quads = new ArrayList(rawQuads.size());

      for (ItemRenderer.RawItemQuad rawQuad : rawQuads) {
         quads.add(this.normalizeQuad(rawQuad, transform));
      }

      return new ItemQuadModel(List.copyOf(quads), state.isAnimated(), false);
   }

   private GpuBuffer paramsBuffer() {
      if (!this.paramsDirty && this.paramsBuffer != null) {
         return this.paramsBuffer;
      }

      this.uploadParams();
      if (!this.paramsDirty && this.paramsBuffer != null) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();
      ByteBuffer bufferData = this.packParams(true);
      this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_item_params"), 128, bufferData);
      this.paramsDirty = false;
      return this.paramsBuffer;
   }

   private static int premultiplyColor(int color, float alpha) {
      color = normalizeColor(color);
      float combinedAlpha = (color >>> 24 & 0xFF) / (255.0F) * alpha;
      return Math.round((255.0F) * combinedAlpha) << 24
         | Math.round((color >>> 16 & 0xFF) * combinedAlpha) << 16
         | Math.round((color >>> 8 & 0xFF) * combinedAlpha) << 8
         | Math.round((color & 0xFF) * combinedAlpha);
   }

   private static int normalizeColor(int color) {
      return (color & (-16777216)) == 0 && (color & (16777215)) != 0 ? color | (-16777216) : color;
   }

   public int addGlintParam(float strength) {
      int index = this.paramCount;
      if (index == 4096) {
         return -1;
      }

      this.glintStrengths[index] = strength;
      this.paramCount++;
      this.paramsDirty = true;
      return index;
   }

   public void uploadParams() {
      if (this.paramCount != 0 && this.paramsDirty) {
         GpuBuffer buffer = this.ensureParamsBuffer();
         if (buffer != null) {
            try {
               ByteBuffer bufferData = this.packParams(false);
               RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, bufferData.remaining()), bufferData);
               this.paramsDirty = false;
            } catch (RuntimeException error) {
               this.paramsDirty = true;
            }
         }
      }
   }

   private static float crossProduct(float x0, float y0, float x1, float y1) {
      return x0 * y1 - x1 * y0;
   }

   private void submitItem(DrawContext context, ItemRenderSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.getItemModelManager() != null) {
            try {
               GuiRenderState renderState = ((DrawContextAccessor)(Object)context).getGuiRenderState();
               int layerSerial = ((GuiLayerTracker)renderState).getLayerSerial();
               Matrix3x2f transform = GuiScale.getScaledMatrices(context);
               ItemRenderer.TransformKey pose = ItemRenderer.TransformKey.of(transform);
               ItemQuadModel model = this.getModel(client, spec);
               if (model.specialRenderer()) {
                  this.renderFallback(context, spec);
                  return;
               }

               for (ItemQuad quad : model.quads()) {
                  ItemAtlasTexture atlas = this.atlasTexture(quad.atlas());
                  if (atlas != null) {
                     float glintStrength = spec.options().glintMode().shouldRender(spec.stack(), quad.foil()) ? spec.options().glintStrength() : 0.0F;
                     int color = combineColors(spec.options().color(), quad.tint(), spec.options().alpha());
                     this.submitQuad(renderState, layerSerial, transform, pose, atlas, quad, spec.x(), spec.y(), spec.size(), color, glintStrength);
                  }
               }
            } catch (RuntimeException error) {
               ClientInitializer.logger().warn(("[RenderItem] Failed to submit item: {}"), spec.stack().getName().getString(), error);
            }
         }
      }
   }

   private static Vector3f transformPosition(Vector3fc position, Matrix4f transform) {
      return new Vector3f(position).mulPosition(transform);
   }

   private ItemRenderer() {
      this.stackModelCache = new LinkedHashMap<ItemRenderer.StackCacheKey, ItemQuadModel>(256, (0.75F), true) {
         @Override
         protected boolean removeEldestEntry(java.util.Map.Entry<ItemRenderer.StackCacheKey, ItemQuadModel> eldestEntry) {
            return this.size() > 512;
         }
      };
      this.identityModelCache = new LinkedHashMap<ItemRenderer.ModelCacheKey, ItemQuadModel>(256, (0.75F), true) {
         @Override
         protected boolean removeEldestEntry(java.util.Map.Entry<ItemRenderer.ModelCacheKey, ItemQuadModel> eldestEntry) {
            return this.size() > 512;
         }
      };
      this.quadBatches = new LinkedHashMap<>(32);
      this.fallbackColors = new IdentityHashMap<>();
      this.glintStrengths = new float[4096];
   }

   public int fallbackColor(ItemGuiElementRenderState state) {
      return this.fallbackColors.getOrDefault(state, -1);
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && this.paramCount != 0) {
         GpuBuffer buffer = this.paramsBuffer();
         if (buffer != null) {
            renderPass.setUniform(("ItemParamsArray"), buffer);
         }
      }
   }

   private ItemAtlasTexture atlasTexture(Identifier id) {
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

      AbstractTexture texture = textureManager.getTexture(id);
      if (texture != null && texture.getGlTextureView() != null) {
         ItemRenderer.AtlasCacheEntry cached = this.atlasCache.get(id);
         if (cached != null && cached.texture() == texture) {
            return cached.value();
         }

         TextureSetup textureSetup = TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
         ItemAtlasTexture atlas = new ItemAtlasTexture(id, textureSetup, TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR)));
         this.atlasCache.put(id, new ItemRenderer.AtlasCacheEntry(texture, atlas));
         return atlas;
      } else {
         return null;
      }
   }

   public void clearCaches() {
      this.stackModelCache.clear();
      this.identityModelCache.clear();
      this.atlasCache.clear();
   }

   private record BatchKey(GuiRenderState state, int layerSerial, Identifier texture, ItemRenderer.TransformKey pose, ScreenRect scissor) {
   }

   private record TransformKey(float m00, float m01, float m10, float m11, float m20, float m21) {
      public static ItemRenderer.TransformKey of(Matrix3x2f transform) {
         return new ItemRenderer.TransformKey(transform.m00(), transform.m01(), transform.m10(), transform.m11(), transform.m20(), transform.m21());
      }
   }

   private record RawItemQuad(
      Identifier atlas,
      float x0,
      float y0,
      float u0,
      float v0,
      float x1,
      float y1,
      float u1,
      float v1,
      float x2,
      float y2,
      float u2,
      float v2,
      float x3,
      float y3,
      float u3,
      float v3,
      int tint,
      boolean foil,
      float depth,
      float area,
      float z0,
      float z1,
      float z2,
      float z3
   ) {
      public float getV3() {
         return this.v3;
      }

      public float getZ3() {
         return this.z3;
      }

      public float getU0() {
         return this.u0;
      }

      public float getX2() {
         return this.x2;
      }

      public float getY0() {
         return this.y0;
      }

      public float getV2() {
         return this.v2;
      }

      public float getX1() {
         return this.x1;
      }

      public float getX3() {
         return this.x3;
      }

      public float getU1() {
         return this.u1;
      }

      public float getY3() {
         return this.y3;
      }

      public float getZ0() {
         return this.z0;
      }

      public float getV1() {
         return this.v1;
      }

      public float getY1() {
         return this.y1;
      }

      public float getX0() {
         return this.x0;
      }

      public float getU2() {
         return this.u2;
      }

      public float getY2() {
         return this.y2;
      }

      public float getZ1() {
         return this.z1;
      }

      public float getU3() {
         return this.u3;
      }

      public float getZ2() {
         return this.z2;
      }

      public float getV0() {
         return this.v0;
      }
   }

   private record StackCacheKey(Item item, ComponentMap components, int count, int seed) {
      public static ItemRenderer.StackCacheKey of(ItemStack stack, int seed) {
         return new ItemRenderer.StackCacheKey(stack.getItem(), stack.getImmutableComponents(), stack.getCount(), seed);
      }
   }

   private static final class ModelBounds {
      private float minX;
      private float minY;
      private float minZ;
      private float maxX;
      private float maxY;
      private float maxZ;
      private boolean specialRenderer;

      private void markSpecialRenderer() {
         this.specialRenderer = true;
      }

      private boolean isThreeDimensional() {
         return this.specialRenderer || this.maxZ - this.minZ > (0.02F);
      }

      private ModelBounds() {
         this.minX = (3.4028235E38F);
         this.minY = (3.4028235E38F);
         this.minZ = (3.4028235E38F);
         this.maxX = (-3.4028235E38F);
         this.maxY = (-3.4028235E38F);
         this.maxZ = (-3.4028235E38F);
      }

      private void include(float x, float y, float z) {
         this.minX = Math.min(this.minX, x);
         this.minY = Math.min(this.minY, y);
         this.minZ = Math.min(this.minZ, z);
         this.maxX = Math.max(this.maxX, x);
         this.maxY = Math.max(this.maxY, y);
         this.maxZ = Math.max(this.maxZ, z);
      }

      private boolean isValid() {
         return this.minX <= this.maxX && this.minY <= this.maxY;
      }
   }

   private record AtlasCacheEntry(AbstractTexture texture, ItemAtlasTexture value) {
   }

   private record ModelTransform(float minX, float maxY, float scale, float offsetX, float offsetY) {
      private float x(float x) {
         return this.offsetX + (x - this.minX) * this.scale;
      }

      private float y(float y) {
         return this.offsetY + (this.maxY - y) * this.scale;
      }

      private static ItemRenderer.ModelTransform of(ItemRenderer.ModelBounds bounds) {
         if (!bounds.isValid()) {
            return new ItemRenderer.ModelTransform(0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
         } else {
            float width = Math.max(1.0E-4F, bounds.maxX - bounds.minX);
            float height = Math.max(1.0E-4F, bounds.maxY - bounds.minY);
            float extent = Math.max(width, height);
            if (bounds.isThreeDimensional()) {
               float threeDimensionalScale = Math.min(1.0F / extent, 1.08F);
               return new ItemRenderer.ModelTransform(
                  (bounds.minX + bounds.maxX) * 0.5F, (bounds.minY + bounds.maxY) * 0.5F, threeDimensionalScale, 0.5F, 0.5F
               );
            } else {
               float flatScale = 1.0F / extent;
               return new ItemRenderer.ModelTransform(bounds.minX, bounds.maxY, flatScale, (1.0F - width * flatScale) * 0.5F, (1.0F - height * flatScale) * 0.5F);
            }
         }
      }
   }

   private record ModelCacheKey(Object identity, int seed) {
   }
}
