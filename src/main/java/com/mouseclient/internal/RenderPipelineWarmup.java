package com.mouseclient.internal;

import com.mouseclient.render.pipeline.WorldRainRenderer;
import com.mouseclient.render.pipeline.ArcRenderer;
import com.mouseclient.render.pipeline.BlobRenderer;
import com.mouseclient.render.pipeline.BlockEsp;
import com.mouseclient.render.pipeline.BracketRenderer;
import com.mouseclient.render.pipeline.CircleRenderer;
import com.mouseclient.render.pipeline.CustomWorld;
import com.mouseclient.render.pipeline.AtlasTextRenderer;
import com.mouseclient.render.pipeline.GuiBlurRenderer;
import com.mouseclient.render.pipeline.GuiGlowRenderer;
import com.mouseclient.render.pipeline.MouseClientHandOutlineBlur;
import com.mouseclient.render.pipeline.MouseClientJumpCircleScene;
import com.mouseclient.render.pipeline.RainScreenRenderer;
import com.mouseclient.render.pipeline.DitherRenderer;
import com.mouseclient.render.pipeline.GlassOutlineRenderer;
import com.mouseclient.render.pipeline.GlassRenderer;
import com.mouseclient.render.pipeline.PlayerOutlineRenderer;
import com.mouseclient.render.pipeline.ImageRenderer;
import com.mouseclient.render.pipeline.ItemRenderer;
import com.mouseclient.render.pipeline.LineRenderer;
import com.mouseclient.render.pipeline.MenuGridRenderer;
import com.mouseclient.render.pipeline.MotionBlur;
import com.mouseclient.render.pipeline.Outline360Renderer;
import com.mouseclient.render.pipeline.OutlineRenderer;
import com.mouseclient.render.pipeline.Particles;
import com.mouseclient.render.pipeline.ColorPickerRenderer;
import com.mouseclient.render.pipeline.EffectIconRenderer;
import com.mouseclient.render.pipeline.MsdfTextRenderer;
import com.mouseclient.render.pipeline.RectHalfIconRenderer;
import com.mouseclient.render.pipeline.RoundedRectangleRenderer;
import com.mouseclient.render.pipeline.RippleRenderer;
import com.mouseclient.render.pipeline.GuiShapePipelines;
import com.mouseclient.render.pipeline.SaturationRenderer;
import com.mouseclient.render.pipeline.GlassShapeRenderer;
import com.mouseclient.render.pipeline.ShimmerRenderer;
import com.mouseclient.render.pipeline.SkeletonEsp;
import com.mouseclient.render.pipeline.SoftGlowRenderer;
import com.mouseclient.render.pipeline.TargetEsp;
import com.mouseclient.render.pipeline.TargetScanSurface;
import com.mouseclient.render.pipeline.Torus;
import com.mouseclient.render.pipeline.TotemGhost;
import com.mouseclient.render.pipeline.WarpTextRenderer;
import com.mouseclient.render.pipeline.WetSurfaceRenderer;
import com.mouseclient.util.entity.ProjectilePrediction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RenderPipelineWarmup {
   private static final Class<?>[] PIPELINE_OWNERS = new Class[]{
      GuiBlurRenderer.class,
      GlassRenderer.class,
      GlassShapeRenderer.class,
      GuiGlowRenderer.class,
      SoftGlowRenderer.class,
      GlassOutlineRenderer.class,
      CircleRenderer.class,
      BracketRenderer.class,
      BlobRenderer.class,
      ArcRenderer.class,
      ColorPickerRenderer.class,
      RoundedRectangleRenderer.class,
      RectHalfIconRenderer.class,
      OutlineRenderer.class,
      Outline360Renderer.class,
      ImageRenderer.class,
      LineRenderer.class,
      RippleRenderer.class,
      ShimmerRenderer.class,
      MenuGridRenderer.class,
      WarpTextRenderer.class,
      DitherRenderer.class,
      EffectIconRenderer.class,
      MsdfTextRenderer.class,
      AtlasTextRenderer.class,
      ItemRenderer.class,
      GuiShapePipelines.class,
      PlayerOutlineRenderer.class,
      MouseClientHandOutlineBlur.class,
      MouseClientJumpCircleScene.class,
      SaturationRenderer.class,
      MotionBlur.class,
      Particles.class,
      SkeletonEsp.class,
      BlockEsp.class,
      TargetEsp.class,
      Torus.class,
      ProjectilePrediction.class,
      CustomWorld.class,
      TotemGhost.class,
      TargetScanSurface.class,
      WetSurfaceRenderer.class,
      RainScreenRenderer.class,
      WorldRainRenderer.class
   };
   private static final Logger LOGGER = LoggerFactory.getLogger(("mouseclient-pipelines"));
   private static ArrayList<RenderPipeline> pipelines;
   private static int nextPipelineIndex;

   public static boolean precompileNext() {
      if (pipelines == null) {
         pipelines = collectPipelines();
      }

      if (nextPipelineIndex >= pipelines.size()) {
         return false;
      }

      RenderPipeline pipeline = pipelines.get(nextPipelineIndex++);

      try {
         RenderSystem.getDevice().precompilePipeline(pipeline);
      } catch (Throwable compileFailure) {
         LOGGER.error(("pipeline {} failed to compile"), pipeline.getLocation(), compileFailure);
      }

      return true;
   }

   public static void reset() {
      pipelines = null;
      nextPipelineIndex = 0;
   }

   private RenderPipelineWarmup() {
   }

   private static ArrayList<RenderPipeline> collectPipelines() {
      ArrayList discoveredPipelines = new ArrayList(64);

      for (Class owner : PIPELINE_OWNERS) {
         try {
            for (Field field : owner.getDeclaredFields()) {
               if (Modifier.isStatic(field.getModifiers()) && RenderPipeline.class.isAssignableFrom(field.getType())) {
                  field.setAccessible(true);
                  RenderPipeline pipeline = (RenderPipeline)field.get(null);
                  if (pipeline != null && !discoveredPipelines.contains(pipeline)) {
                     discoveredPipelines.add(pipeline);
                  }
               }
            }
         } catch (Throwable inspectionFailure) {
         }
      }

      return discoveredPipelines;
   }
}
