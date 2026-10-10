package com.mouseclient.render.pipeline;

import com.mouseclient.render.GuiVertexFormats;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;

public final class GuiShapePipelines {
   public static final RenderPipeline ROUNDED = createShapePipeline(("rounded_gui"), ("ui/shape2/rounded"), ("ui/shape2/rounded"), 0);
   public static final RenderPipeline TEXTURE = createShapePipeline(("shape_texture_gui"), ("ui/shape2/shape"), ("ui/shape2/shape"), 1);
   public static final RenderPipeline TEXTURE_GUI_BLUR = createShapePipeline(("shape_texture_gui_2d_blur"), ("ui/shape2/shape"), ("ui/shape2/shape"), 1);
   public static final RenderPipeline SOFT_BLUR = createShapePipeline(("glass2"), ("ui/shape2/glass"), ("ui/shape2/glass"), 2);
   public static final RenderPipeline SOFT_BLUR_GUI_BLUR = createShapePipeline(GuiShapePipelines.SOFT_BLUR_GUI_LOCATION, ("ui/shape2/glass"), ("ui/shape2/glass"), 2);
   public static final RenderPipeline ARC = createShapePipeline(("pie_gui"), GuiShapePipelines.ARC_SHADER, ("ui/shape2/pie"), 0);
   public static final RenderPipeline ARC_BLUR = createShapePipeline(("pie_blur_gui"), ("ui/shape2/pie"), ("ui/shape2/pie_blur"), 1);
   public static final RenderPipeline BLUR_DOWN = createPostProcessPipeline(("shape_blur_down"), ("ui/shape2/blur_down"), false);
   public static final RenderPipeline BLUR_UP = createPostProcessPipeline(("shape_blur_up"), GuiShapePipelines.BLUR_UP_SHADER, false);
   public static final RenderPipeline CLAMP_COPY = createPostProcessPipeline(("shape_clamp_copy"), ("ui/shape2/clamp_copy"), true);

   private static final String PRIMARY_SAMPLER = "Sampler0";
   
   private static final String NAMESPACE = "mouseclient";

   private static final String SOFT_BLUR_GUI_LOCATION = "glass2_2d_blur";

   private static final String ARC_SHADER = "ui/shape2/pie";

   private static final String BLUR_UP_SHADER = "ui/shape2/blur_up";

   public static boolean includesGuiBlur(RenderPipeline pipeline) {
      return pipeline == TEXTURE_GUI_BLUR || pipeline == SOFT_BLUR_GUI_BLUR;
   }

   private static RenderPipeline createShapePipeline(String location, String vertexShader, String fragmentShader, int samplerCount) {
      Builder pipelineBuilder = RenderPipeline.builder()
         .withLocation(identifier("pipeline/" + location))
         .withVertexShader(identifier(vertexShader))
         .withFragmentShader(identifier(fragmentShader))
         .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
         .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withCull(false)
         .withVertexFormat(GuiVertexFormats.SHAPE, DrawMode.QUADS);
      if (samplerCount >= 1) {
         pipelineBuilder.withSampler(("Sampler0"));
      }

      if (samplerCount >= 2) {
         pipelineBuilder.withSampler(("Sampler1"));
      }

      return pipelineBuilder.build();
   }

   private GuiShapePipelines() {
   }

   private static RenderPipeline createPostProcessPipeline(String location, String fragmentShader, boolean clampUniform) {
      Builder pipelineBuilder = RenderPipeline.builder()
         .withLocation(identifier("pipeline/" + location))
         .withVertexShader(identifier(("ui/shape2/pass")))
         .withFragmentShader(identifier(fragmentShader))
         .withSampler(PRIMARY_SAMPLER)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withCull(false)
         .withVertexFormat(GuiVertexFormats.POST_PROCESS, DrawMode.QUADS);
      if (clampUniform) {
         pipelineBuilder.withUniform(("MouseClientBlurRect"), UniformType.UNIFORM_BUFFER);
      }

      return pipelineBuilder.build();
   }

   private static Identifier identifier(String path) {
      return Identifier.of(NAMESPACE, path);
   }
}
