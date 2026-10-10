package com.mouseclient.render.pipeline;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.ColorSetting;
import com.mouseclient.modules.impl.render.SettingSeparator;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.util.entity.WorldProjection;
import com.mouseclient.util.time.GuiVisuals;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

@ModuleInfo(name = "CustomWorld", description = "Насыщенность, освещение, время, погода и шейдерное небо.", category = ModuleCategory.RENDER)
public final class CustomWorld extends Module {
   private static CustomWorld INSTANCE;
   private static final RenderPipeline PLASMA_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/sky_plasma")))
      .withVertexShader(ClientInitializer.id(("core/sky")))
      .withFragmentShader(ClientInitializer.id(("core/plasma")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withUniform(("SkyParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline SPACE_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/sky_space")))
      .withVertexShader(ClientInitializer.id(("core/sky")))
      .withFragmentShader(ClientInitializer.id(("core/space")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withUniform(("SkyParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline GALAXY_PIPELINE = createSkyPostPipeline(("sky_galaxy"));
   private static final RenderPipeline OVERCAST_PIPELINE = createSkyPostPipeline(("sky_overcast"));
   private static final RenderPipeline PURPLE_PIPELINE = createSkyPostPipeline(("sky_purple"));
   private static final RenderPipeline RAYS_PIPELINE = createSkyPostPipeline(("sky_rays"));
   private static final RenderPipeline NORD_PIPELINE = createSkyPostPipeline(("sky_nord"));
   private static final RenderPipeline AURORA_LAYER_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/sky_aurora_layer")))
      .withVertexShader(ClientInitializer.id(("core/sky_post")))
      .withFragmentShader(ClientInitializer.id(("core/sky_aurora_layer")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withUniform(("SkyPostParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline AURORA_COMPOSITE_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/sky_aurora")))
      .withVertexShader(ClientInitializer.id(("core/sky_post")))
      .withFragmentShader(ClientInitializer.id(("core/sky_aurora")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("SkyPostParams"), UniformType.UNIFORM_BUFFER)
      .build();
   public DecimalSetting saturation;
   public DecimalSetting lighting;
   public DecimalSetting skyStrength;
   public BooleanSetting fixedTime;
   public DecimalSetting timeOfDay;
   public ModeSetting weather;
   public BooleanSetting customPrecipitationColor;
   public ColorSetting precipitationColor;
   public ModeSetting shaderSky;
   public BooleanSetting shaderRain;
   public BooleanSetting wetSurfaces;
   public BooleanSetting wetSurfacesOnlyInRain;
   public DecimalSetting wetness;
   public DecimalSetting reflections;
   public DecimalSetting specularHighlight;
   public DecimalSetting ripples;
   public BooleanSetting floorOnly;
   public DecimalSetting reflectSky;
   public BooleanSetting customReflectionColor;
   public ColorSetting reflectionColor;
   public DecimalSetting hitThickness;
   public DecimalSetting reflectionDistance;
   public BooleanSetting worldRain;
   public BooleanSetting worldRainOnlyInRain;
   public DecimalSetting rainStreaks;
   public DecimalSetting rainDensity;
   public DecimalSetting rainSpeed;
   public DecimalSetting streakLength;
   public BooleanSetting hideVanillaRain;
   public DecimalSetting screenDroplets;
   public DecimalSetting groundSplashes;
   public BooleanSetting checkRoof;
   public BooleanSetting customRainColor;
   public ColorSetting rainColor;
   public BooleanSetting fog;
   public ModeSetting fogMode;
   public DecimalSetting fogDistance;
   public ColorSetting fogColor;
   public DecimalSetting fogVolume;
   
   private static final RenderPipeline FOG_CLOUD_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/custom_world_fog")))
      .withVertexShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .build();
   private static final RenderLayer FOG_CLOUD_LAYER = RenderLayer.of(
      ("custom_world_fog"),
      RenderSetup.builder(FOG_CLOUD_PIPELINE)
         .texture(("Sampler0"), ClientInitializer.id(("textures/glow.png")))
         .expectedBufferSize(320 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())
         .build()
   );
   private final Immediate fogVertices = VertexConsumerProvider.immediate(
      new BufferAllocator(320 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())
   );
   private final float[] fogCloudData = new float[400];
   private final Quaternionf fogBillboardRotation = new Quaternionf();
   private final Vector3f fogVertexOffset = new Vector3f();
   private float generatedFogDistance;
   private static final RenderPipeline HAZE_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/haze")))
      .withVertexShader(ClientInitializer.id(("core/sky_post")))
      .withFragmentShader(ClientInitializer.id(("core/haze")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler1"))
      .withUniform(("SkyPostParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline HAZE_COMPOSITE_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/haze_blit")))
      .withVertexShader(ClientInitializer.id(("core/sky_post")))
      .withFragmentShader(ClientInitializer.id(("core/haze_blit")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withSampler(("Sampler1"))
      .withUniform(("SkyPostParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private final Matrix4f inverseViewProjection;
   private SimpleFramebuffer hazeFramebuffer;
   private GpuBuffer hazeQuadBuffer;
   private GpuBuffer hazeParamsBuffer;
   public static final float[] horizonRgb = new float[]{(0.45F), (0.6F), (0.9F)};
   private GpuBuffer skyQuadBuffer;
   private GpuBuffer skyParamsBuffer;
   private GpuBuffer skyPostParamsBuffer;
   private SimpleFramebuffer auroraFramebuffer;

   private boolean skyStrengthApplies() {
      return this.shaderSky.isSelected(("Галактика"))
         || this.shaderSky.isSelected(("Пасмурно"))
         || this.shaderSky.isSelected(("Фиолетовый"))
         || this.shaderSky.isSelected(("Аврора"))
         || this.shaderSky.isSelected(("Сияние"))
         || this.shaderSky.isSelected(("Норд"));
   }

   public void renderSky(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
      if (!this.isShaderSkyEnabled()) {
         this.closeSkyResources();
      } else {
         Framebuffer framebuffer = minecraftClient.getFramebuffer();
         if (framebuffer != null && framebuffer.getColorAttachmentView() != null && framebuffer.getDepthAttachmentView() != null && framebuffer.textureWidth > 0 && framebuffer.textureHeight > 0) {
            if (this.skyQuadBuffer == null || this.skyQuadBuffer.isClosed()) {
               try (BufferAllocator quadAllocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize())) {
                  BufferBuilder builder = new BufferBuilder(quadAllocator, DrawMode.QUADS, VertexFormats.POSITION);
                  builder.vertex((-1.0F), (-1.0F), 0.0F);
                  builder.vertex((-1.0F), 1.0F, 0.0F);
                  builder.vertex(1.0F, 1.0F, 0.0F);
                  builder.vertex(1.0F, (-1.0F), 0.0F);

                  try (BuiltBuffer meshData = builder.end()) {
                     this.skyQuadBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_sky_quad"), 32, meshData.getBuffer());
                  }
               }
            }

            if (this.auroraFramebuffer != null && !this.shaderSky.isSelected(("Аврора"))) {
               this.auroraFramebuffer.delete();
               this.auroraFramebuffer = null;
            }

            if (!this.shaderSky.isSelected(("Плазма")) && !this.shaderSky.isSelected(("Космос"))) {
               if (this.skyPostParamsBuffer == null || this.skyPostParamsBuffer.isClosed()) {
                  this.skyPostParamsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_sky_post_params"), 136, (128L));
               }

               MemoryStack postStack = MemoryStack.stackPush();

               try {
                  ByteBuffer postParams = postStack.calloc(128);
                  new Matrix4f(projectionMatrix).mul(viewMatrix).invert().get(0, postParams);
                  postParams.putFloat(64, (GuiVisuals.accentColor >> 16 & 0xFF) / (255.0F));
                  postParams.putFloat(68, (GuiVisuals.accentColor >> 8 & 0xFF) / (255.0F));
                  postParams.putFloat(72, (GuiVisuals.accentColor & 0xFF) / (255.0F));
                  postParams.putFloat(76, this.shaderSky.isSelected(("Фиолетовый")) ? 1.0F : (0.5F));
                  postParams.putFloat(80, (GuiVisuals.accentGradientColor >> 16 & 0xFF) / (255.0F));
                  postParams.putFloat(84, (GuiVisuals.accentGradientColor >> 8 & 0xFF) / (255.0F));
                  postParams.putFloat(88, (GuiVisuals.accentGradientColor & 0xFF) / (255.0F));
                  postParams.putFloat(92, (0.46F));
                  postParams.putFloat(96, (float)(System.currentTimeMillis() % (100000L)) / (1000.0F));
                  postParams.putFloat(100, this.skyStrength.getValue());
                  postParams.putFloat(104, (float)framebuffer.textureWidth / framebuffer.textureHeight);
                  postParams.putFloat(108, framebuffer.textureHeight);
                  postParams.putFloat(112, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().x);
                  postParams.putFloat(116, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().y);
                  postParams.putFloat(120, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().z);
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.skyPostParamsBuffer.slice(0L, (128L)), postParams);
               } catch (Throwable postUploadFailure) {
                  if (postStack != null) {
                     try {
                        postStack.close();
                     } catch (Throwable postCloseFailure) {
                        postUploadFailure.addSuppressed(postCloseFailure);
                     }
                  }

                  throw postUploadFailure;
               }

               if (postStack != null) {
                  postStack.close();
               }

               if (this.shaderSky.isSelected(("Аврора"))) {
                  int halfWidth = Math.max(1, framebuffer.textureWidth / 2);
                  int halfHeight = Math.max(1, framebuffer.textureHeight / 2);
                  if (this.auroraFramebuffer == null) {
                     this.auroraFramebuffer = new SimpleFramebuffer(("mouseclient_sky_aurora_layer"), halfWidth, halfHeight, false);
                  }

                  if (this.auroraFramebuffer.textureWidth != halfWidth || this.auroraFramebuffer.textureHeight != halfHeight) {
                     this.auroraFramebuffer.resize(halfWidth, halfHeight);
                  }

                  try (RenderPass renderPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(() -> ("mouseclient_sky_aurora_layer"), this.auroraFramebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                     renderPass.setPipeline(AURORA_LAYER_PIPELINE);
                     renderPass.setUniform(("SkyPostParams"), this.skyPostParamsBuffer);
                     renderPass.setVertexBuffer(0, this.skyQuadBuffer);
                     renderPass.setIndexBuffer(
                        RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                     );
                     renderPass.drawIndexed(0, 0, 6, 1);
                  }

                  try (RenderPass auroraCompositePass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                           () -> ("mouseclient_sky_aurora"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
                        )) {
                     auroraCompositePass.setPipeline(AURORA_COMPOSITE_PIPELINE);
                     auroraCompositePass.bindTexture(("Sampler0"), this.auroraFramebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                     auroraCompositePass.setUniform(("SkyPostParams"), this.skyPostParamsBuffer);
                     auroraCompositePass.setVertexBuffer(0, this.skyQuadBuffer);
                     auroraCompositePass.setIndexBuffer(
                        RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                     );
                     auroraCompositePass.drawIndexed(0, 0, 6, 1);
                  }
               } else {
                  try (RenderPass skyPostPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(
                           () -> ("mouseclient_sky_post"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
                        )) {
                     skyPostPass.setPipeline(
                        this.shaderSky.isSelected(("Галактика"))
                           ? GALAXY_PIPELINE
                           : (
                              this.shaderSky.isSelected(("Пасмурно"))
                                 ? OVERCAST_PIPELINE
                                 : (
                                    this.shaderSky.isSelected(("Сияние"))
                                       ? RAYS_PIPELINE
                                       : (this.shaderSky.isSelected(("Норд")) ? NORD_PIPELINE : PURPLE_PIPELINE)
                                 )
                           )
                     );
                     skyPostPass.setUniform(("SkyPostParams"), this.skyPostParamsBuffer);
                     skyPostPass.setVertexBuffer(0, this.skyQuadBuffer);
                     skyPostPass.setIndexBuffer(
                        RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                     );
                     skyPostPass.drawIndexed(0, 0, 6, 1);
                  }
               }
            } else {
               if (this.skyParamsBuffer == null || this.skyParamsBuffer.isClosed()) {
                  this.skyParamsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_sky_params"), 136, (96L));
               }

               MemoryStack skyStack = MemoryStack.stackPush();

               try {
                  ByteBuffer skyParams = skyStack.calloc(96);
                  new Matrix4f(projectionMatrix).mul(viewMatrix).invert().get(0, skyParams);
                  skyParams.putFloat(64, (GuiVisuals.accentColor >> 16 & 0xFF) / (255.0F));
                  skyParams.putFloat(68, (GuiVisuals.accentColor >> 8 & 0xFF) / (255.0F));
                  skyParams.putFloat(72, (GuiVisuals.accentColor & 0xFF) / (255.0F));
                  skyParams.putFloat(76, 1.0F);
                  skyParams.putFloat(80, (float)(System.currentTimeMillis() % (1000000L)) / (1000.0F));
                  skyParams.putFloat(84, 1.0F);
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.skyParamsBuffer.slice(0L, (96L)), skyParams);
               } catch (Throwable skyUploadFailure) {
                  if (skyStack != null) {
                     try {
                        skyStack.close();
                     } catch (Throwable skyCloseFailure) {
                        skyUploadFailure.addSuppressed(skyCloseFailure);
                     }
                  }

                  throw skyUploadFailure;
               }

               if (skyStack != null) {
                  skyStack.close();
               }

               try (RenderPass skyPass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(
                        () -> ("mouseclient_sky"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
                     )) {
                  skyPass.setPipeline(this.shaderSky.isSelected(("Космос")) ? SPACE_PIPELINE : PLASMA_PIPELINE);
                  skyPass.setUniform(("SkyParams"), this.skyParamsBuffer);
                  skyPass.setVertexBuffer(0, this.skyQuadBuffer);
                  skyPass.setIndexBuffer(
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                  );
                  skyPass.drawIndexed(0, 0, 6, 1);
               }
            }
         }
      }
   }

   public boolean isShaderSkyEnabled() {
      return this.isEnabled() && !this.shaderSky.isSelected(("Выкл"));
   }

   private void generateFogClouds(float distance) {
      Random random = new Random((42L));
      float minRadius = distance * (0.2F);
      float maxRadius = distance * (0.95F);

      for (int cloudIndex = 0; cloudIndex < 80; cloudIndex++) {
         float radius = minRadius + random.nextFloat() * (maxRadius - minRadius);
         float angle = random.nextFloat() * (6.2831855F);
         int cloudOffset = cloudIndex * 5;
         this.fogCloudData[cloudOffset] = MathHelper.sin(angle) * radius;
         this.fogCloudData[cloudOffset + 1] = (random.nextFloat() - (0.4F)) * distance * (0.4F);
         this.fogCloudData[cloudOffset + 2] = MathHelper.cos(angle) * radius;
         this.fogCloudData[cloudOffset + 3] = distance * ((0.3F) + random.nextFloat() * (0.5F));
         this.fogCloudData[cloudOffset + 4] = (radius - minRadius) / (maxRadius - minRadius);
      }

      this.generatedFogDistance = distance;
   }

   private void emitFogVertex(VertexConsumer vertices, Matrix4f transform, int cloudOffset, float x, float y, float u, float v, int color) {
      this.fogBillboardRotation.transform(this.fogVertexOffset.set(x, y, 0.0F));
      vertices.vertex(
            transform,
            this.fogCloudData[cloudOffset] + this.fogVertexOffset.x,
            this.fogCloudData[cloudOffset + 1] + this.fogVertexOffset.y,
            this.fogCloudData[cloudOffset + 2] + this.fogVertexOffset.z
         )
         .texture(u, v)
         .color(color);
   }

   private CustomWorld() {
      this.generatedFogDistance = (-1.0F);
      this.inverseViewProjection = new Matrix4f();
      this.saturation = this.addSetting(new DecimalSetting(("Насыщенность"), ("Насыщенность цветов мира."), 1.0, 0.0, (2.0D), (0.05D)));
      this.lighting = this.addSetting(new DecimalSetting(("Освещение"), ("Яркость освещения мира."), 1.0, 0.0, (2.0D), (0.05D)));
      this.addSetting(new SettingSeparator(("Туман")));
      this.fog = this.addSetting(new BooleanSetting(("Туман"), ("Свой туман вокруг игрока."), false));
      this.fogMode = this.addSetting(new ModeSetting(("Тип тумана"), ("Облака — объёмные клубы вокруг, Дымка — плотная пелена, съедающая даль."), ("Облака"), ("Облака"), ("Дымка")));
      this.fogMode.setVisibility(() -> this.fog.getValue());
      this.fogDistance = this.addSetting(new DecimalSetting(("Дальность тумана"), ("Дальность отрисовки тумана в блоках."), (80.0D), (10.0D), (255.0D), (5.0D)));
      this.fogColor = this.addSetting(new ColorSetting(("Цвет тумана"), ("Цвет тумана."), new Color(214, 216, 222)));
      this.fogVolume = this.addSetting(new DecimalSetting(("Объём тумана"), ("Плотность тумана."), (0.3D), (0.05D), 1.0, (0.05D)));
      this.fogDistance.setVisibility(() -> this.fog.getValue());
      this.fogColor.setVisibility(() -> this.fog.getValue());
      this.fogVolume.setVisibility(() -> this.fog.getValue());
      this.addSetting(new SettingSeparator(("Время и погода")));
      this.timeOfDay = this.addSetting(new DecimalSetting(("Время суток"), ("Время суток в тиках: 0 рассвет, 6000 полдень, 12000 закат, 18000 полночь. Сдвинь — время зафиксируется само."), (6000.0D), 0.0, (24000.0D), (100.0D)));
      this.fixedTime = this.addSetting(new BooleanSetting(("Фиксировать время"), ("Зафиксировать время суток слайдером."), false));
      this.weather = this.addSetting(new ModeSetting(("Погода"), ("Зафиксировать погоду."), ("Выкл"), ("Выкл"), ("Ясно"), ("Дождь"), ("Гроза"), ("Снег")));
      this.customPrecipitationColor = this.addSetting(new BooleanSetting(("Свой цвет осадков"), ("Красить дождь и снег своим цветом."), false));
      this.precipitationColor = this.addSetting(new ColorSetting(("Цвет осадков"), ("Цвет дождя и снега.")));
      this.addSetting(new SettingSeparator(("Небо и осадки")));
      this.shaderSky = this.addSetting(
         new ModeSetting(("Шейдерное небо"), ("Заменить небо шейдером."), ("Выкл"), ("Выкл"), ("Плазма"), ("Космос"), ("Галактика"), ("Пасмурно"), ("Фиолетовый"), ("Аврора"), ("Сияние"), ("Норд"))
      );
      this.skyStrength = this.addSetting(new DecimalSetting(("Сила неба"), ("Насколько плотно шейдер неба закрашивает мир."), (0.65D), (0.05D), 1.0, (0.05D)));
      this.shaderRain = new BooleanSetting(("Шейдерный дождь"), ("Заменить дождь шейдером."), false);
      this.addSetting(new SettingSeparator(("Дождь")));
      this.worldRain = this.addSetting(new BooleanSetting(("Дождь в мире"), ("Капли на камере и брызги по земле."), false));
      this.worldRainOnlyInRain = this.addSetting(new BooleanSetting(("Только в дождь"), ("Показывать только когда идёт дождь и над головой открытое небо."), true));
      this.rainStreaks = this.addSetting(new DecimalSetting(("Струи дождя"), ("Сила падающих струй дождя в воздухе."), (0.7D), 0.0, 1.0, (0.05D)));
      this.rainDensity = this.addSetting(new DecimalSetting(("Плотность дождя"), ("Сколько струй в воздухе."), 1.0, (0.1D), (2.0D), (0.05D)));
      this.rainSpeed = this.addSetting(new DecimalSetting(("Скорость дождя"), ("Скорость падения и наклон струй."), 1.0, (0.3D), (2.0D), (0.05D)));
      this.streakLength = this.addSetting(new DecimalSetting(("Длина струй"), ("Во сколько раз струя вытягивается вдоль падения."), (10.0D), 1.0, (24.0D), (0.5D)));
      this.hideVanillaRain = this.addSetting(new BooleanSetting(("Скрыть ванильный дождь"), ("Убрать ванильные полосы дождя, оставив только шейдерные."), true));
      this.screenDroplets = this.addSetting(new DecimalSetting(("Капли на экране"), ("Сила капель на камере."), (0.6D), 0.0, 1.0, (0.05D)));
      this.groundSplashes = this.addSetting(new DecimalSetting(("Брызги на земле"), ("Сила брызг по горизонтальным поверхностям."), (0.6D), 0.0, 1.0, (0.05D)));
      this.checkRoof = this.addSetting(new BooleanSetting(("Учитывать крышу"), ("Не брызгать под крышей и в помещениях."), true));
      this.customRainColor = this.addSetting(new BooleanSetting(("Свой цвет дождя"), ("Использовать свой цвет вместо цвета горизонта."), false));
      this.rainColor = this.addSetting(new ColorSetting(("Цвет дождя"), ("Цвет капель и брызг."), new Color(179, 199, 217)));
      this.addSetting(new SettingSeparator(("Мокрый мир")));
      this.wetSurfaces = this.addSetting(new BooleanSetting(("Мокрые поверхности"), ("Мокрые блики и экранные отражения на поверхностях."), false));
      this.wetSurfacesOnlyInRain = this.addSetting(new BooleanSetting(("Только под дождём"), ("Сила эффекта следует за уровнем дождя."), false));
      this.wetness = this.addSetting(new DecimalSetting(("Влажность"), ("Насколько поверхности выглядят мокрыми."), (0.6D), 0.0, 1.0, (0.05D)));
      this.reflections = this.addSetting(new DecimalSetting(("Отражения"), ("Сила экранных отражений."), (0.7D), 0.0, 1.0, (0.05D)));
      this.specularHighlight = this.addSetting(new DecimalSetting(("Блик"), ("Сила солнечного блика на мокрой поверхности."), (0.5D), 0.0, (2.0D), (0.05D)));
      this.ripples = this.addSetting(new DecimalSetting(("Рябь"), ("Рябь по лужам."), (0.35D), 0.0, 1.0, (0.05D)));
      this.floorOnly = this.addSetting(new BooleanSetting(("Только пол"), ("Мочить только горизонтальные поверхности."), true));
      this.reflectSky = this.addSetting(new DecimalSetting(("Отражать небо"), ("Насколько промахнувшийся луч берёт цвет неба с экрана."), (0.8D), 0.0, 1.0, (0.05D)));
      this.customReflectionColor = this.addSetting(new BooleanSetting(("Свой цвет отражений"), ("Использовать свой цвет вместо цвета горизонта."), false));
      this.reflectionColor = this.addSetting(new ColorSetting(("Цвет отражений"), ("Цвет отражений там, где луч не попал в геометрию.")));
      this.hitThickness = this.addSetting(new DecimalSetting(("Толщина попадания"), ("Допуск толщины геометрии при попадании луча."), (0.6D), (0.1D), (3.0D), (0.1D)));
      this.reflectionDistance = this.addSetting(new DecimalSetting(("Дальность отражений"), ("Максимальная длина луча отражения в блоках."), (24.0D), (4.0D), (96.0D), 1.0));
      this.skyStrength.setVisibility(() -> this.skyStrengthApplies());
      this.timeOfDay.onChange(() -> this.fixedTime.setValue(true));
      this.precipitationColor.setVisibility(() -> this.customPrecipitationColor.getValue());
      this.wetSurfacesOnlyInRain.setVisibility(() -> this.wetSurfaces.getValue());
      this.wetness.setVisibility(() -> this.wetSurfaces.getValue());
      this.reflections.setVisibility(() -> this.wetSurfaces.getValue());
      this.specularHighlight.setVisibility(() -> this.wetSurfaces.getValue());
      this.ripples.setVisibility(() -> this.wetSurfaces.getValue());
      this.floorOnly.setVisibility(() -> this.wetSurfaces.getValue());
      this.reflectSky.setVisibility(() -> this.wetSurfaces.getValue());
      this.customReflectionColor.setVisibility(() -> this.wetSurfaces.getValue());
      this.reflectionColor.setVisibility(() -> this.wetSurfaces.getValue() && this.customReflectionColor.getValue());
      this.hitThickness.setVisibility(() -> this.wetSurfaces.getValue());
      this.reflectionDistance.setVisibility(() -> this.wetSurfaces.getValue());
      this.worldRainOnlyInRain.setVisibility(() -> this.worldRain.getValue());
      this.rainStreaks.setVisibility(() -> this.worldRain.getValue());
      this.rainDensity.setVisibility(() -> this.worldRain.getValue() && this.rainStreaks.getValue() > 0.0F);
      this.rainSpeed.setVisibility(() -> this.worldRain.getValue() && this.rainStreaks.getValue() > 0.0F);
      this.streakLength.setVisibility(() -> this.worldRain.getValue() && this.rainStreaks.getValue() > 0.0F);
      this.hideVanillaRain.setVisibility(() -> this.worldRain.getValue());
      this.screenDroplets.setVisibility(() -> this.worldRain.getValue());
      this.groundSplashes.setVisibility(() -> this.worldRain.getValue());
      this.checkRoof.setVisibility(() -> this.worldRain.getValue());
      this.customRainColor.setVisibility(() -> this.worldRain.getValue());
      this.rainColor.setVisibility(() -> this.worldRain.getValue() && this.customRainColor.getValue());
   }

   public static CustomWorld getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new CustomWorld();
      }

      return INSTANCE;
   }

   private void closeHazeResources() {
      if (this.hazeQuadBuffer != null && !this.hazeQuadBuffer.isClosed()) {
         this.hazeQuadBuffer.close();
      }

      this.hazeQuadBuffer = null;
      if (this.hazeParamsBuffer != null && !this.hazeParamsBuffer.isClosed()) {
         this.hazeParamsBuffer.close();
      }

      this.hazeParamsBuffer = null;
      if (this.hazeFramebuffer != null) {
         this.hazeFramebuffer.delete();
         this.hazeFramebuffer = null;
      }
   }

   private void renderHaze(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
      Framebuffer framebuffer = minecraftClient.getFramebuffer();
      if (framebuffer != null && framebuffer.getColorAttachmentView() != null && framebuffer.getDepthAttachmentView() != null && framebuffer.textureWidth > 0 && framebuffer.textureHeight > 0) {
         if (this.hazeQuadBuffer == null || this.hazeQuadBuffer.isClosed()) {
            try (BufferAllocator quadAllocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize())) {
               BufferBuilder builder = new BufferBuilder(quadAllocator, DrawMode.QUADS, VertexFormats.POSITION);
               builder.vertex((-1.0F), (-1.0F), 0.0F);
               builder.vertex((-1.0F), 1.0F, 0.0F);
               builder.vertex(1.0F, 1.0F, 0.0F);
               builder.vertex(1.0F, (-1.0F), 0.0F);

               try (BuiltBuffer meshData = builder.end()) {
                  this.hazeQuadBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_haze_quad"), 32, meshData.getBuffer());
               }
            }
         }

         if (this.hazeParamsBuffer == null || this.hazeParamsBuffer.isClosed()) {
            this.hazeParamsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_haze_params"), 136, (128L));
         }

         int color = this.fogColor.getArgb();
         MemoryStack stack = MemoryStack.stackPush();

         try {
            ByteBuffer paramsData = stack.calloc(128);
            this.inverseViewProjection.set(projectionMatrix).mul(viewMatrix).invert().get(0, paramsData);
            paramsData.putFloat(64, (color >> 16 & 0xFF) / (255.0F));
            paramsData.putFloat(68, (color >> 8 & 0xFF) / (255.0F));
            paramsData.putFloat(72, (color & 0xFF) / (255.0F));
            paramsData.putFloat(76, (color >>> 24 & 0xFF) / (255.0F));
            paramsData.putFloat(80, this.fogDistance.getValue());
            paramsData.putFloat(84, this.fogDistance.getValue() * (0.3F));
            paramsData.putFloat(88, this.fogVolume.getValue());
            paramsData.putFloat(92, (float)(System.currentTimeMillis() % (1000000L)) / (1000.0F));
            paramsData.putFloat(112, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().x);
            paramsData.putFloat(116, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().y);
            paramsData.putFloat(120, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().z);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.hazeParamsBuffer.slice(0L, (128L)), paramsData);
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

         int halfWidth = Math.max(1, framebuffer.textureWidth / 2);
         int halfHeight = Math.max(1, framebuffer.textureHeight / 2);
         if (this.hazeFramebuffer == null) {
            this.hazeFramebuffer = new SimpleFramebuffer(("mouseclient_haze_layer"), halfWidth, halfHeight, false);
         }

         if (this.hazeFramebuffer.textureWidth != halfWidth || this.hazeFramebuffer.textureHeight != halfHeight) {
            this.hazeFramebuffer.resize(halfWidth, halfHeight);
         }

         try (RenderPass renderPass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> ("mouseclient_haze"), this.hazeFramebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
            renderPass.setPipeline(HAZE_PIPELINE);
            renderPass.bindTexture(("Sampler1"), framebuffer.getDepthAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
            renderPass.setUniform(("SkyPostParams"), this.hazeParamsBuffer);
            renderPass.setVertexBuffer(0, this.hazeQuadBuffer);
            renderPass.setIndexBuffer(
               RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
            );
            renderPass.drawIndexed(0, 0, 6, 1);
         }

         try (RenderPass compositePass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> ("mouseclient_haze_blit"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
            compositePass.setPipeline(HAZE_COMPOSITE_PIPELINE);
            compositePass.bindTexture(("Sampler0"), this.hazeFramebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
            compositePass.bindTexture(("Sampler1"), framebuffer.getDepthAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
            compositePass.setUniform(("SkyPostParams"), this.hazeParamsBuffer);
            compositePass.setVertexBuffer(0, this.hazeQuadBuffer);
            compositePass.setIndexBuffer(
               RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
            );
            compositePass.drawIndexed(0, 0, 6, 1);
         }
      }
   }

   public void renderFog(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
      if (this.isCustomFogEnabled() && minecraftClient.world != null) {
         if (this.isHazeEnabled()) {
            this.renderHaze(viewMatrix, projectionMatrix);
         } else {
            this.closeHazeResources();
            float distance = this.fogDistance.getValue();
            if (this.generatedFogDistance != distance) {
               this.generateFogClouds(distance);
            }

            int rgb = this.fogColor.getArgb() & (16777215);
            float density = this.fogVolume.getValue();
            this.fogBillboardRotation.set(minecraftClient.gameRenderer.getCamera().getRotation()).rotateY((3.1415927F));
            Vec3d cameraPosition = minecraftClient.gameRenderer.getCamera().getCameraPos();
            VertexConsumer vertices = this.fogVertices.getBuffer(FOG_CLOUD_LAYER);

            for (int cloudIndex = 0; cloudIndex < 80; cloudIndex++) {
               int cloudOffset = cloudIndex * 5;
               float radialProgress = this.fogCloudData[cloudOffset + 4];
               int alpha = MathHelper.clamp((int)(density * ((0.03F) + radialProgress * radialProgress * (0.12F)) * (255.0F)), 0, 255);
               if (alpha > 1) {
                  float halfSize = this.fogCloudData[cloudOffset + 3] * (0.5F);
                  if (WorldProjection.isSphereVisible(
                     cameraPosition.x + this.fogCloudData[cloudOffset], cameraPosition.y + this.fogCloudData[cloudOffset + 1], cameraPosition.z + this.fogCloudData[cloudOffset + 2], halfSize * (1.42F)
                  )) {
                     int color = alpha << 24 | rgb;
                     this.emitFogVertex(vertices, viewMatrix, cloudOffset, -halfSize, -halfSize, 0.0F, 0.0F, color);
                     this.emitFogVertex(vertices, viewMatrix, cloudOffset, -halfSize, halfSize, 0.0F, 1.0F, color);
                     this.emitFogVertex(vertices, viewMatrix, cloudOffset, halfSize, halfSize, 1.0F, 1.0F, color);
                     this.emitFogVertex(vertices, viewMatrix, cloudOffset, halfSize, -halfSize, 1.0F, 0.0F, color);
                  }
               }
            }

            this.fogVertices.draw();
         }
      } else {
         this.closeHazeResources();
      }
   }

   public boolean isHazeEnabled() {
      return this.isCustomFogEnabled() && this.fogMode.isSelected(("Дымка"));
   }

   public void closeSkyResources() {
      if (this.skyQuadBuffer != null && !this.skyQuadBuffer.isClosed()) {
         this.skyQuadBuffer.close();
      }

      this.skyQuadBuffer = null;
      if (this.skyParamsBuffer != null && !this.skyParamsBuffer.isClosed()) {
         this.skyParamsBuffer.close();
      }

      this.skyParamsBuffer = null;
      if (this.skyPostParamsBuffer != null && !this.skyPostParamsBuffer.isClosed()) {
         this.skyPostParamsBuffer.close();
      }

      this.skyPostParamsBuffer = null;
      if (this.auroraFramebuffer != null) {
         this.auroraFramebuffer.delete();
         this.auroraFramebuffer = null;
      }
   }

   public static int horizonColor() {
      int red = Math.round(Math.min(Math.max(horizonRgb[0], 0.0F), 1.0F) * (255.0F));
      int green = Math.round(Math.min(Math.max(horizonRgb[1], 0.0F), 1.0F) * (255.0F));
      int blue = Math.round(Math.min(Math.max(horizonRgb[2], 0.0F), 1.0F) * (255.0F));
      return (-16777216) | red << 16 | green << 8 | blue;
   }

   private static RenderPipeline createSkyPostPipeline(String shaderName) {
      return RenderPipeline.builder()
         .withLocation(ClientInitializer.id("pipeline/" + shaderName))
         .withVertexShader(ClientInitializer.id(("core/sky_post")))
         .withFragmentShader(ClientInitializer.id("core/" + shaderName))
         .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
         .withBlend(BlendFunction.TRANSLUCENT)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withCull(false)
         .withUniform(("SkyPostParams"), UniformType.UNIFORM_BUFFER)
         .build();
   }

   public boolean isCustomFogEnabled() {
      return this.isEnabled() && this.fog.getValue();
   }
}
