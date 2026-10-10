package com.mouseclient.render.pipeline;

import com.mouseclient.gui.EffectIconQuad;
import com.mouseclient.gui.EffectIconBatchRenderState;
import com.mouseclient.gui.EffectIconAtlas;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.mixin.DrawContextAccessor;
import com.mouseclient.network.ClientInitializer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

public final class EffectIconRenderer implements AutoCloseable {
   private static volatile EffectIconRenderer INSTANCE;
   private static final VertexFormat ICON_VERTEX_FORMAT = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .build();
   public static final RenderPipeline ICON_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/effect_icon")))
      .withVertexShader(id(("core/effect_icon")))
      .withFragmentShader(id(("core/effect_icon")))
      .withVertexFormat(ICON_VERTEX_FORMAT, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .build();
   private final Map<Identifier, EffectIconRenderer.AtlasCacheEntry> atlasCache = new HashMap<>();
   private final Map<EffectIconRenderer.BatchKey, EffectIconBatchRenderState> batches = new LinkedHashMap<>(16);
   private DrawContext drawContext;

   public void begin(DrawContext drawContext) {
      if (this.drawContext != drawContext) {
         this.batches.clear();
      }

      this.drawContext = drawContext;
   }

   public void invalidateTextures() {
      this.atlasCache.clear();
   }

   public static EffectIconRenderer getInstance() {
      EffectIconRenderer renderer = INSTANCE;
      if (renderer == null) {
         synchronized (EffectIconRenderer.class) {
            renderer = INSTANCE;
            if (renderer == null) {
               renderer = new EffectIconRenderer();
               INSTANCE = renderer;
            }
         }
      }

      return renderer;
   }

   @Override
   public void close() {
      this.batches.clear();
      this.atlasCache.clear();
      this.drawContext = null;
   }

   public void endFrame() {
      this.drawContext = null;
      this.batches.clear();
   }

   private EffectIconRenderer() {
   }

   public void render(EffectIconSpec spec) {
      this.submitIcon(this.drawContext, spec);
   }

   private EffectIconAtlas effectAtlas(Identifier id) {
      if (id == null) {
         return null;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.getTextureManager() != null) {
         AbstractTexture atlasTexture = client.getTextureManager().getTexture(id);
         if (atlasTexture != null && atlasTexture.getGlTextureView() != null) {
            EffectIconRenderer.AtlasCacheEntry cacheEntry = this.atlasCache.get(id);
            if (cacheEntry != null && cacheEntry.texture() == atlasTexture) {
               return cacheEntry.value();
            }

            TextureSetup textureSetup = TextureSetup.of(atlasTexture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
            EffectIconAtlas resolvedAtlas = new EffectIconAtlas(id, textureSetup);
            this.atlasCache.put(id, new EffectIconRenderer.AtlasCacheEntry(atlasTexture, resolvedAtlas));
            return resolvedAtlas;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public void breakBatches() {
      this.batches.clear();
   }

   private static int preserveColor(int color) {
      return color;
   }

   public static void closeInstance() {
      EffectIconRenderer renderer = INSTANCE;
      if (renderer != null) {
         renderer.close();
         INSTANCE = null;
      }
   }

   private Sprite effectSprite(EffectIconSpec spec) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && spec.effect() != null) {
         SpriteAtlasTexture guiAtlas = client.getAtlasManager().getAtlasTexture(Atlases.GUI);
         Identifier id = InGameHud.getEffectTexture(spec.effect());
         return guiAtlas.getSprite(id);
      } else {
         return null;
      }
   }

   private void submitIcon(DrawContext context, EffectIconSpec spec) {
      if (context != null && spec != null && spec.visible()) {
         Sprite sprite = this.effectSprite(spec);
         if (sprite != null) {
            EffectIconAtlas atlas = this.effectAtlas(sprite.getAtlasId());
            if (atlas != null) {
               EffectIconQuad quad = new EffectIconQuad(
                  spec.x(), spec.y(), spec.size(), sprite.getMinU(), sprite.getMinV(), sprite.getMaxU(), sprite.getMaxV(), preserveColor(spec.color())
               );

               try {
                  GuiRenderState state = ((DrawContextAccessor)(Object)context).getGuiRenderState();
                  int unusedLayerSerial = ((GuiLayerTracker)state).getLayerSerial();
                  Matrix3x2f transform = GuiScale.getScaledMatrices(context);
                  EffectIconBatchRenderState iconBatch = new EffectIconBatchRenderState(transform, atlas);
                  iconBatch.add(quad);
                  state.addSimpleElement(iconBatch);
               } catch (RuntimeException error) {
                  ClientInitializer.logger().warn(("[EffectIconRenderer] Failed to submit effect icon"), error);
               }
            }
         }
      }
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private record AtlasCacheEntry(AbstractTexture texture, EffectIconAtlas value) {
   }

   private record TransformKey(float m00, float m01, float m10, float m11, float m20, float m21) {
      public static EffectIconRenderer.TransformKey of(Matrix3x2f transform) {
         return new EffectIconRenderer.TransformKey(transform.m00(), transform.m01(), transform.m10(), transform.m11(), transform.m20(), transform.m21());
      }
   }

   private record BatchKey(GuiRenderState state, int layerSerial, Identifier texture, EffectIconRenderer.TransformKey pose) {
   }
}
