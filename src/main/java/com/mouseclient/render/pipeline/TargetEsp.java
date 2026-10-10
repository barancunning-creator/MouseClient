package com.mouseclient.render.pipeline;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.modules.impl.combat.AimAssist;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.combat.TriggerBot;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.Tags;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.network.HudStyle;
import com.mouseclient.render.DepthSnapshot;
import com.mouseclient.render.avatar.Easings;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.WorldProjection;
import com.mouseclient.util.entity.EntityRaycast;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.ScalarAnimation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.BufferAllocator.CloseableBuffer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

@ModuleInfo(
   name = "TargetEsp",
   description = "Подсвечивает цель AttackAura, AimAssist или TrigerBot призраками и кольцом.",
   category = ModuleCategory.RENDER
)
public final class TargetEsp extends Module {
   private static TargetEsp INSTANCE;
   private static final RenderPipeline GHOST_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/target_ghosts")))
      .withVertexShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .build();
   private static final RenderPipeline DEPTH_GHOST_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/target_ghosts_depth")))
      .withVertexShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .build();
   private static final RenderPipeline CRYSTAL_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/target_crystals")))
      .withVertexShader(Identifier.ofVanilla(("core/position_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_color")))
      .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   private static final RenderPipeline DEPTH_CRYSTAL_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/target_crystals_depth")))
      .withVertexShader(Identifier.ofVanilla(("core/position_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_color")))
      .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   private static final RenderPipeline SCAN_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/target_scan")))
      .withVertexShader(ClientInitializer.id(("core/scan")))
      .withFragmentShader(ClientInitializer.id(("core/scan")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("ScanParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final Vector4f WHITE_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
   private static final Vector3f ZERO_OFFSET = new Vector3f();
   private static final Matrix4f IDENTITY_TEXTURE_MATRIX = new Matrix4f();
   private static final Vector3f[] CRYSTAL_VERTICES = new Vector3f[]{
      new Vector3f(0.0F, (1.5F), 0.0F),
      new Vector3f(0.0F, (-1.5F), 0.0F),
      new Vector3f(1.0F, 0.0F, 0.0F),
      new Vector3f((-1.0F), 0.0F, 0.0F),
      new Vector3f(0.0F, 0.0F, 1.0F),
      new Vector3f(0.0F, 0.0F, (-1.0F))
   };
   private static final int[][] CRYSTAL_FACES = new int[][]{{0, 2, 4}, {0, 4, 3}, {0, 3, 5}, {0, 5, 2}, {1, 4, 2}, {1, 3, 4}, {1, 5, 3}, {1, 2, 5}};
   private static final float[] CRYSTAL_FACE_BRIGHTNESS = new float[]{
      1.0F,
      (0.8F),
      (0.6F),
      (0.9F),
      (0.7F),
      (0.5F),
      (0.4F),
      (0.6F)
   };
   public ModeSetting mode;
   public ModeSetting ghostStyle;
   public ModeSetting circleStyle;
   public BooleanSetting trident;
   public BooleanSetting projectiles;
   private final ScalarAnimation targetFade;
   private LivingEntity target;
   private float lightningElapsedSeconds;
   private long lastLightningFrameAt;
   private float lightningHitFade;
   private float ringElapsedSeconds;
   private long lastRingFrameAt;
   private float ringHitFade;
   private LivingEntity scanTarget;
   private long scanStartedAt;
   private long lastScanQueryAt;
   private float[] lightningPoints;
   private float[] ribbonNormals;
   private float[] ribbonMiterScales;
   private int lightningPointCount;
   private ArrayList<float[]> pendingLightningBranches;
   private BufferBuilder lightningBuilder;
   private BufferBuilder glowBuilder;
   private Quaternionf billboardRotation;
   private Vector3f billboardScratch;
   private float ribbonHalfWidth;
   private float floatValue6;
   private float glowHalfSize;
   private int lightningColor;
   private int glowColor;
   private final HashMap<Integer, float[]> projectileAlphas;
   private float diamondUnhurtScale;
   private float ghostOrbitPhase;
   private long lastGhostFrameAt;
   private final HashMap<Integer, Vec3d> hashMap2;
   private final ScalarAnimation chainFade;
   private float chainRotationDegrees;
   private long lastChainFrameAt;
   private final float[] skullAlphas;
   private long lastSkullFrameAt;
   private GpuBuffer scanVertices;
   private final GpuBuffer[] quadVertexBuffers;
   private int quadBufferCursor;
   private final BufferAllocator[] geometryAllocators;
   private final HashMap<String, Identifier> textureIds;
   private GpuBuffer scanUniforms;
   private boolean deferredGhostsPending;
   private float deferredOpacity;
   private float deferredHit;
   private float deferredTickDelta;
   private final Matrix4f deferredModelView;
   private GpuBufferSlice deferredProjection;
   private ProjectionType deferredProjectionType;
   private final EventListener<WorldRenderEvent> worldRenderListener;

   private BufferAllocator geometryAllocator(int allocatorIndex, int capacity) {
      BufferAllocator allocator = this.geometryAllocators[allocatorIndex];
      if (allocator == null) {
         allocator = this.geometryAllocators[allocatorIndex] = new BufferAllocator(capacity);
      }

      CloseableBuffer allocatedBuffer = allocator.getAllocated();
      if (allocatedBuffer != null) {
         allocatedBuffer.close();
      }

      return allocator;
   }

   private void drawBloomGhosts(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      if (minecraftClient.getTextureManager().getTexture(Identifier.of(("mouseclient"), ("textures/particles/bloom.png"))).getGlTextureView() != null
         && minecraftClient.getFramebuffer() != null
         && minecraftClient.getFramebuffer().getColorAttachmentView() != null) {
         Quaternionf cameraRotation = minecraftClient.gameRenderer.getCamera().getRotation();
         double relativeX = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x;
         double relativeY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - minecraftClient.gameRenderer.getCamera().getCameraPos().y;
         double relativeZ = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z;
         this.ghostOrbitPhase = this.ghostOrbitPhase + (3.5F) * (float)(System.currentTimeMillis() - this.lastGhostFrameAt) / (1000.0F);
         this.lastGhostFrameAt = System.currentTimeMillis();
         byte orbitCount = 4;
         byte trailSteps = 15;
         float orbitRadius = (0.68F);
         float baseHeight = (1.05F);
         float verticalAmplitude = (0.24F);
         float verticalTrailPhaseStep = (0.12F);
         float pairedHeightOffset = (0.45F);
         float pairedHorizontalOffset = (-0.02F);
         float horizontalPhaseOffset = (0.12F);
         float minimumBillboardSize = (0.02F);
         float maximumBillboardSize = (0.05F);
         float trailPhaseStep = (float)((0.5D) * Math.asin(maximumBillboardSize / (0.5F)));
         int quadCount = 0;
         BufferAllocator allocator = this.geometryAllocator(1, orbitCount * trailSteps * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
         BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
         Vector3f scratch = new Vector3f();

         for (int orbitIndex = 0; orbitIndex < orbitCount; orbitIndex++) {
            float orbitDirection = orbitIndex < 2 ? 1.0F : (-1.0F);
            float pairedOrbitDirection = orbitIndex % 2 == 0 ? (-1.0F) : 1.0F;
            float horizontalOffset = orbitIndex % 2 == 0 ? pairedHorizontalOffset : -pairedHorizontalOffset;
            float heightOffset = orbitIndex % 2 == 0 ? pairedHeightOffset : -pairedHeightOffset;

            for (int trailIndex = 0; trailIndex < trailSteps; trailIndex++) {
               int sampleIndex = orbitIndex % 2 == 0 ? trailIndex : trailSteps - 1 - trailIndex;
               float orbitPhase = this.ghostOrbitPhase + sampleIndex * trailPhaseStep;
               float billboardSize = (5.0F) * MathHelper.lerp((float)sampleIndex / trailSteps, minimumBillboardSize, maximumBillboardSize);
               this.emitBillboard(
                  builder,
                  cameraRotation,
                  scratch,
                  relativeX + orbitDirection * (orbitRadius * MathHelper.sin(orbitPhase + horizontalPhaseOffset) + horizontalOffset),
                  relativeY + baseHeight + verticalAmplitude * MathHelper.sin(this.ghostOrbitPhase + sampleIndex * verticalTrailPhaseStep) + heightOffset,
                  relativeZ + orbitDirection * pairedOrbitDirection * orbitRadius * MathHelper.cos(orbitPhase - horizontalPhaseOffset),
                  billboardSize,
                  0.0F,
                  ArgbColor.multiplyAlpha(this.effectColor(orbitIndex * 90, hit), opacity)
               );
               quadCount++;
            }
         }

         try (BuiltBuffer builtBuffer = builder.end()) {
            this.drawQuads(matrices, ("textures/particles/bloom.png"), builtBuffer, quadCount, EntityRaycast.hasLineOfSight(minecraftClient.player, entity), false);
         }
      }
   }

   private float smoothstep(float minimum, float maximum, float value) {
      return MathHelper.clamp((value - minimum) / (maximum - minimum), 0.0F, 1.0F)
         * MathHelper.clamp((value - minimum) / (maximum - minimum), 0.0F, 1.0F)
         * ((3.0F) - 2.0F * MathHelper.clamp((value - minimum) / (maximum - minimum), 0.0F, 1.0F));
   }

   private void drawTargetChains(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      this.chainRotationDegrees = (this.chainRotationDegrees + (75.0F) * (float)(System.currentTimeMillis() - this.lastChainFrameAt) / (1000.0F)) % (720.0F);
      this.lastChainFrameAt = System.currentTimeMillis();
      this.chainFade.setTarget(opacity >= (0.95F) ? 1.0 : 0.0);
      float tiltZDegrees = (20.0F) * Math.min(1.0F + MathHelper.sin((float)Math.toRadians(this.chainRotationDegrees)), 1.0F);
      float tiltXDegrees = (20.0F) * MathHelper.sin((float)Math.toRadians(this.chainRotationDegrees));
      float baseRadius = entity.getWidth() * (1.5F);
      float bandHeight = 1.0F;
      float segmentAngle = (22.5F);
      double relativeX = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x;
      double relativeY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY())
         + entity.getHeight() * (0.5F)
         - bandHeight / 2.0F
         - minecraftClient.gameRenderer.getCamera().getCameraPos().y;
      double relativeZ = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z;
      int quadCount = 0;
      Vector3f scratch = new Vector3f();
      BufferAllocator chainAllocator = this.geometryAllocator(0, 256 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      BufferAllocator glowAllocator = this.geometryAllocator(1, 256 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      BufferBuilder builder = new BufferBuilder(chainAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      BufferBuilder chainGlowBuilder = new BufferBuilder(glowAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

      for (int layerIndex = 0; layerIndex < 2; layerIndex++) {
         float layerOpacity = layerIndex == 0 ? opacity : (float)this.chainFade.value();
         if (!(layerOpacity < (0.003921569F))) {
            float radius = baseRadius * ((1.2F) - (0.5F) * layerOpacity);
            float layerOffsetX = (layerIndex == 0 ? tiltZDegrees : -tiltZDegrees) / (100.0F);
            float layerOffsetZ = (layerIndex == 0 ? -tiltXDegrees : tiltXDegrees) / (100.0F);
            Quaternionf layerRotation = new Quaternionf()
               .rotateZ((float)Math.toRadians(layerIndex == 0 ? tiltZDegrees : -tiltZDegrees))
               .rotateX((float)Math.toRadians(layerIndex == 0 ? tiltXDegrees : -tiltXDegrees));

            for (float angleDegrees = segmentAngle; angleDegrees <= (720.0F); angleDegrees += segmentAngle) {
               int segmentColor = ArgbColor.multiplyAlpha(this.effectColor(angleDegrees * (3.0F), hit), layerOpacity);
               float segmentStartX = layerOffsetX + MathHelper.sin((float)Math.toRadians(angleDegrees - segmentAngle + this.chainRotationDegrees * (0.5F))) * radius;
               float segmentStartZ = layerOffsetZ + MathHelper.cos((float)Math.toRadians(angleDegrees - segmentAngle + this.chainRotationDegrees * (0.5F))) * radius;
               float segmentEndX = layerOffsetX + MathHelper.sin((float)Math.toRadians(angleDegrees + this.chainRotationDegrees * (0.5F))) * radius;
               float segmentEndZ = layerOffsetZ + MathHelper.cos((float)Math.toRadians(angleDegrees + this.chainRotationDegrees * (0.5F))) * radius;
               layerRotation.transform(scratch.set(segmentStartX, 0.0F, segmentStartZ));
               builder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z))
                  .texture((angleDegrees - segmentAngle) / (360.0F) * (3.0F), 0.0F)
                  .color(segmentColor);
               layerRotation.transform(scratch.set(segmentEndX, 0.0F, segmentEndZ));
               builder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z))
                  .texture(angleDegrees / (360.0F) * (3.0F), 0.0F)
                  .color(segmentColor);
               layerRotation.transform(scratch.set(segmentEndX, bandHeight, segmentEndZ));
               builder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z))
                  .texture(angleDegrees / (360.0F) * (3.0F), 1.0F)
                  .color(segmentColor);
               layerRotation.transform(scratch.set(segmentStartX, bandHeight, segmentStartZ));
               builder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z))
                  .texture((angleDegrees - segmentAngle) / (360.0F) * (3.0F), 1.0F)
                  .color(segmentColor);
               layerRotation.transform(
                  scratch.set(
                     MathHelper.lerp((0.87F), (segmentStartX + segmentEndX) / 2.0F, segmentStartX),
                     MathHelper.lerp((0.87F), bandHeight / 2.0F, 0.0F),
                     MathHelper.lerp((0.87F), (segmentStartZ + segmentEndZ) / 2.0F, segmentStartZ)
                  )
               );
               chainGlowBuilder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z))
                  .texture(0.0F, 0.0F)
                  .color(ArgbColor.multiplyAlpha(segmentColor, (0.05F)));
               layerRotation.transform(
                  scratch.set(
                     MathHelper.lerp((0.87F), (segmentStartX + segmentEndX) / 2.0F, segmentEndX),
                     MathHelper.lerp((0.87F), bandHeight / 2.0F, 0.0F),
                     MathHelper.lerp((0.87F), (segmentStartZ + segmentEndZ) / 2.0F, segmentEndZ)
                  )
               );
               chainGlowBuilder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z))
                  .texture(1.0F, 0.0F)
                  .color(ArgbColor.multiplyAlpha(segmentColor, (0.05F)));
               layerRotation.transform(
                  scratch.set(
                     MathHelper.lerp((0.87F), (segmentStartX + segmentEndX) / 2.0F, segmentEndX),
                     MathHelper.lerp((0.87F), bandHeight / 2.0F, bandHeight),
                     MathHelper.lerp((0.87F), (segmentStartZ + segmentEndZ) / 2.0F, segmentEndZ)
                  )
               );
               chainGlowBuilder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z))
                  .texture(1.0F, 1.0F)
                  .color(ArgbColor.multiplyAlpha(segmentColor, (0.05F)));
               layerRotation.transform(
                  scratch.set(
                     MathHelper.lerp((0.87F), (segmentStartX + segmentEndX) / 2.0F, segmentStartX),
                     MathHelper.lerp((0.87F), bandHeight / 2.0F, bandHeight),
                     MathHelper.lerp((0.87F), (segmentStartZ + segmentEndZ) / 2.0F, segmentStartZ)
                  )
               );
               chainGlowBuilder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z))
                  .texture(0.0F, 1.0F)
                  .color(ArgbColor.multiplyAlpha(segmentColor, (0.05F)));
               quadCount++;
            }
         }
      }

      try (BuiltBuffer glowBuffer = chainGlowBuilder.end()) {
         this.drawQuads(matrices, ("textures/particles/glow.png"), glowBuffer, quadCount, EntityRaycast.hasLineOfSight(minecraftClient.player, entity), false);
      }

      try (BuiltBuffer chainBuffer = builder.end()) {
         this.drawQuads(matrices, ("textures/particles/chain.png"), chainBuffer, quadCount, EntityRaycast.hasLineOfSight(minecraftClient.player, entity), true);
      }
   }

   private int emitRingBand(
      BufferBuilder builder,
      float centerX,
      float centerY,
      float centerZ,
      LivingEntity entity,
      float height,
      float opacity,
      float lowerY,
      float upperY,
      float lowerRadiusOffset,
      float upperRadiusOffset,
      int lowerColor,
      int upperColor
   ) {
      int quadCount = 0;

      for (int angleDegrees = 0; angleDegrees < 360; angleDegrees += 2) {
         float startCos = MathHelper.cos((float)Math.toRadians(angleDegrees));
         float startSin = MathHelper.sin((float)Math.toRadians(angleDegrees));
         float endCos = MathHelper.cos((float)Math.toRadians(angleDegrees + 2));
         float endSin = MathHelper.sin((float)Math.toRadians(angleDegrees + 2));
         builder.vertex(
               centerX + startCos * (this.ringRadiusAtHeight(entity, lowerY / height, opacity) + lowerRadiusOffset),
               centerY + lowerY,
               centerZ + startSin * (this.ringRadiusAtHeight(entity, lowerY / height, opacity) + lowerRadiusOffset)
            )
            .color(lowerColor);
         builder.vertex(
               centerX + endCos * (this.ringRadiusAtHeight(entity, lowerY / height, opacity) + lowerRadiusOffset),
               centerY + lowerY,
               centerZ + endSin * (this.ringRadiusAtHeight(entity, lowerY / height, opacity) + lowerRadiusOffset)
            )
            .color(lowerColor);
         builder.vertex(
               centerX + endCos * (this.ringRadiusAtHeight(entity, upperY / height, opacity) + upperRadiusOffset),
               centerY + upperY,
               centerZ + endSin * (this.ringRadiusAtHeight(entity, upperY / height, opacity) + upperRadiusOffset)
            )
            .color(upperColor);
         builder.vertex(
               centerX + startCos * (this.ringRadiusAtHeight(entity, upperY / height, opacity) + upperRadiusOffset),
               centerY + upperY,
               centerZ + startSin * (this.ringRadiusAtHeight(entity, upperY / height, opacity) + upperRadiusOffset)
            )
            .color(upperColor);
         quadCount++;
      }

      return quadCount;
   }

   private int emitLightningRibbon(float widthScale, boolean taperEnd) {
      if (this.lightningPointCount < 2) {
         return 0;
      }

      for (int pointIndex = 0; pointIndex < this.lightningPointCount; pointIndex++) {
         float pointX = this.lightningPoints[pointIndex * 3];
         float pointY = this.lightningPoints[pointIndex * 3 + 1];
         float pointZ = this.lightningPoints[pointIndex * 3 + 2];
         float previousNormalX = 0.0F;
         float previousNormalY = 0.0F;
         float previousNormalZ = 0.0F;
         float nextNormalX = 0.0F;
         float nextNormalY = 0.0F;
         float nextNormalZ = 0.0F;
         boolean hasPreviousNormal = pointIndex > 0;
         boolean hasNextNormal = pointIndex < this.lightningPointCount - 1;
         if (hasPreviousNormal) {
            float previousTangentX = pointX - this.lightningPoints[pointIndex * 3 - 3];
            float previousTangentY = pointY - this.lightningPoints[pointIndex * 3 - 2];
            float previousTangentZ = pointZ - this.lightningPoints[pointIndex * 3 - 1];
            previousNormalX = previousTangentY * pointZ - previousTangentZ * pointY;
            previousNormalY = previousTangentZ * pointX - previousTangentX * pointZ;
            previousNormalZ = previousTangentX * pointY - previousTangentY * pointX;
            float previousNormalLength = MathHelper.sqrt(previousNormalX * previousNormalX + previousNormalY * previousNormalY + previousNormalZ * previousNormalZ);
            if (previousNormalLength < (1.0E-6F)) {
               hasPreviousNormal = false;
            } else {
               previousNormalX /= previousNormalLength;
               previousNormalY /= previousNormalLength;
               previousNormalZ /= previousNormalLength;
            }
         }

         if (hasNextNormal) {
            float nextTangentX = this.lightningPoints[pointIndex * 3 + 3] - pointX;
            float nextTangentY = this.lightningPoints[pointIndex * 3 + 4] - pointY;
            float nextTangentZ = this.lightningPoints[pointIndex * 3 + 5] - pointZ;
            nextNormalX = nextTangentY * pointZ - nextTangentZ * pointY;
            nextNormalY = nextTangentZ * pointX - nextTangentX * pointZ;
            nextNormalZ = nextTangentX * pointY - nextTangentY * pointX;
            float nextNormalLength = MathHelper.sqrt(nextNormalX * nextNormalX + nextNormalY * nextNormalY + nextNormalZ * nextNormalZ);
            if (nextNormalLength < (1.0E-6F)) {
               hasNextNormal = false;
            } else {
               nextNormalX /= nextNormalLength;
               nextNormalY /= nextNormalLength;
               nextNormalZ /= nextNormalLength;
            }
         }

         float miterScale = 1.0F;
         float normalX;
         float normalY;
         float normalZ;
         if (hasPreviousNormal && hasNextNormal) {
            if (previousNormalX * nextNormalX + previousNormalY * nextNormalY + previousNormalZ * nextNormalZ < 0.0F) {
               nextNormalX = -nextNormalX;
               nextNormalY = -nextNormalY;
               nextNormalZ = -nextNormalZ;
            }

            normalX = previousNormalX + nextNormalX;
            normalY = previousNormalY + nextNormalY;
            normalZ = previousNormalZ + nextNormalZ;
            float normalLength = MathHelper.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            if (normalLength < (1.0E-6F)) {
               normalX = previousNormalX;
               normalY = previousNormalY;
               normalZ = previousNormalZ;
            } else {
               normalX /= normalLength;
               normalY /= normalLength;
               normalZ /= normalLength;
               miterScale = 1.0F / Math.max(normalX * previousNormalX + normalY * previousNormalY + normalZ * previousNormalZ, (0.35F));
            }
         } else if (hasPreviousNormal) {
            normalX = previousNormalX;
            normalY = previousNormalY;
            normalZ = previousNormalZ;
         } else if (hasNextNormal) {
            normalX = nextNormalX;
            normalY = nextNormalY;
            normalZ = nextNormalZ;
         } else {
            normalX = 0.0F;
            normalY = 0.0F;
            normalZ = 0.0F;
         }

         this.ribbonNormals[pointIndex * 3] = normalX;
         this.ribbonNormals[pointIndex * 3 + 1] = normalY;
         this.ribbonNormals[pointIndex * 3 + 2] = normalZ;
         this.ribbonMiterScales[pointIndex] = miterScale;
      }

      float scaledRibbonWidth = this.ribbonHalfWidth * widthScale;
      float scaledGlowSize = this.glowHalfSize * widthScale;

      for (int segmentIndex = 0; segmentIndex < this.lightningPointCount - 1; segmentIndex++) {
         float startTaper = 1.0F;
         float endTaper = 1.0F;
         if (taperEnd) {
            float taperProgress = MathHelper.clamp(((float)segmentIndex / (this.lightningPointCount - 1) - (0.35F)) / (0.65F), 0.0F, 1.0F);
            startTaper = 1.0F - (0.75F) * taperProgress * taperProgress * ((3.0F) - 2.0F * taperProgress);
            taperProgress = MathHelper.clamp(((float)(segmentIndex + 1) / (this.lightningPointCount - 1) - (0.35F)) / (0.65F), 0.0F, 1.0F);
            endTaper = 1.0F - (0.75F) * taperProgress * taperProgress * ((3.0F) - 2.0F * taperProgress);
         }

         float startOffsetX = this.ribbonNormals[segmentIndex * 3] * scaledRibbonWidth * this.ribbonMiterScales[segmentIndex] * startTaper;
         float startOffsetY = this.ribbonNormals[segmentIndex * 3 + 1] * scaledRibbonWidth * this.ribbonMiterScales[segmentIndex] * startTaper;
         float startOffsetZ = this.ribbonNormals[segmentIndex * 3 + 2] * scaledRibbonWidth * this.ribbonMiterScales[segmentIndex] * startTaper;
         float endOffsetX = this.ribbonNormals[segmentIndex * 3 + 3] * scaledRibbonWidth * this.ribbonMiterScales[segmentIndex + 1] * endTaper;
         float endOffsetY = this.ribbonNormals[segmentIndex * 3 + 4] * scaledRibbonWidth * this.ribbonMiterScales[segmentIndex + 1] * endTaper;
         float endOffsetZ = this.ribbonNormals[segmentIndex * 3 + 5] * scaledRibbonWidth * this.ribbonMiterScales[segmentIndex + 1] * endTaper;
         this.lightningBuilder
            .vertex(this.lightningPoints[segmentIndex * 3] - startOffsetX, this.lightningPoints[segmentIndex * 3 + 1] - startOffsetY, this.lightningPoints[segmentIndex * 3 + 2] - startOffsetZ)
            .color(this.lightningColor);
         this.lightningBuilder
            .vertex(this.lightningPoints[segmentIndex * 3 + 3] - endOffsetX, this.lightningPoints[segmentIndex * 3 + 4] - endOffsetY, this.lightningPoints[segmentIndex * 3 + 5] - endOffsetZ)
            .color(this.lightningColor);
         this.lightningBuilder
            .vertex(this.lightningPoints[segmentIndex * 3 + 3] + endOffsetX, this.lightningPoints[segmentIndex * 3 + 4] + endOffsetY, this.lightningPoints[segmentIndex * 3 + 5] + endOffsetZ)
            .color(this.lightningColor);
         this.lightningBuilder
            .vertex(this.lightningPoints[segmentIndex * 3] + startOffsetX, this.lightningPoints[segmentIndex * 3 + 1] + startOffsetY, this.lightningPoints[segmentIndex * 3 + 2] + startOffsetZ)
            .color(this.lightningColor);
         this.emitBillboard(
            this.glowBuilder,
            this.billboardRotation,
            this.billboardScratch,
            (this.lightningPoints[segmentIndex * 3] + this.lightningPoints[segmentIndex * 3 + 3]) * (0.5F),
            (this.lightningPoints[segmentIndex * 3 + 1] + this.lightningPoints[segmentIndex * 3 + 4]) * (0.5F),
            (this.lightningPoints[segmentIndex * 3 + 2] + this.lightningPoints[segmentIndex * 3 + 5]) * (0.5F),
            scaledGlowSize * (startTaper + endTaper) * (0.5F),
            0.0F,
            this.glowColor
         );
      }

      return this.lightningPointCount - 1;
   }

   public void renderDeferredGhosts() {
      if (this.deferredGhostsPending) {
         this.deferredGhostsPending = false;
         if (this.target != null && !this.target.isRemoved()) {
            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(this.deferredProjection, this.deferredProjectionType);
            MatrixStack matrices = new MatrixStack();
            matrices.multiplyPositionMatrix(this.deferredModelView);
            if (this.ghostStyle.isSelected(("Второй"))) {
               this.drawOrbitGhosts(matrices, this.target, this.deferredTickDelta, this.deferredOpacity, this.deferredHit);
            } else {
               this.drawBloomGhosts(matrices, this.target, this.deferredTickDelta, this.deferredOpacity, this.deferredHit);
            }

            RenderSystem.restoreProjectionMatrix();
         }
      }
   }

   private void drawProjectileChains(MatrixStack matrices, float tickDelta) {
      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof PersistentProjectileEntity projectile
            && projectile.getOwner() == minecraftClient.player
            && !(projectile.getVelocity().lengthSquared() < (1.0E-5D))) {
            double handX = MathHelper.lerp(tickDelta, minecraftClient.player.lastRenderX, minecraftClient.player.getX())
               - minecraftClient.gameRenderer.getCamera().getCameraPos().x;
            double handY = MathHelper.lerp(tickDelta, minecraftClient.player.lastRenderY, minecraftClient.player.getY())
               + minecraftClient.player.getStandingEyeHeight() * (0.75F)
               - minecraftClient.gameRenderer.getCamera().getCameraPos().y;
            double handZ = MathHelper.lerp(tickDelta, minecraftClient.player.lastRenderZ, minecraftClient.player.getZ())
               - minecraftClient.gameRenderer.getCamera().getCameraPos().z;
            double projectileX = MathHelper.lerp(tickDelta, projectile.lastRenderX, projectile.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x;
            double projectileY = MathHelper.lerp(tickDelta, projectile.lastRenderY, projectile.getY()) - minecraftClient.gameRenderer.getCamera().getCameraPos().y;
            double projectileZ = MathHelper.lerp(tickDelta, projectile.lastRenderZ, projectile.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z;
            float chainLength = (float)Math.sqrt((projectileX - handX) * (projectileX - handX) + (projectileY - handY) * (projectileY - handY) + (projectileZ - handZ) * (projectileZ - handZ));
            float chainWidth = 1.0F;
            float sag = chainLength * (0.06F);
            byte segmentCount = 16;
            float[] pointX = new float[segmentCount + 1];
            float[] pointY = new float[segmentCount + 1];
            float[] pointZ = new float[segmentCount + 1];
            float[] normalX = new float[segmentCount + 1];
            float[] normalY = new float[segmentCount + 1];
            float[] normalZ = new float[segmentCount + 1];
            BufferAllocator allocator = this.geometryAllocator(1, segmentCount * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
            BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            for (int pointIndex = 0; pointIndex <= segmentCount; pointIndex++) {
               pointX[pointIndex] = (float)MathHelper.lerp((double)pointIndex / segmentCount, handX, projectileX);
               pointY[pointIndex] = (float)(
                  MathHelper.lerp((double)pointIndex / segmentCount, handY, projectileY) - sag * (4.0F) * ((float)pointIndex / segmentCount) * (1.0F - (float)pointIndex / segmentCount)
               );
               pointZ[pointIndex] = (float)MathHelper.lerp((double)pointIndex / segmentCount, handZ, projectileZ);
            }

            for (int normalIndex = 0; normalIndex <= segmentCount; normalIndex++) {
               float tangentX = pointX[Math.min(normalIndex + 1, segmentCount)] - pointX[Math.max(normalIndex - 1, 0)];
               float tangentY = pointY[Math.min(normalIndex + 1, segmentCount)] - pointY[Math.max(normalIndex - 1, 0)];
               float tangentZ = pointZ[Math.min(normalIndex + 1, segmentCount)] - pointZ[Math.max(normalIndex - 1, 0)];
               normalX[normalIndex] = tangentY * pointZ[normalIndex] - tangentZ * pointY[normalIndex];
               normalY[normalIndex] = tangentZ * pointX[normalIndex] - tangentX * pointZ[normalIndex];
               normalZ[normalIndex] = tangentX * pointY[normalIndex] - tangentY * pointX[normalIndex];
               float normalLength = Math.max((1.0E-5F), (float)Math.sqrt(normalX[normalIndex] * normalX[normalIndex] + normalY[normalIndex] * normalY[normalIndex] + normalZ[normalIndex] * normalZ[normalIndex]));
               normalX[normalIndex] *= chainWidth / 2.0F / normalLength;
               normalY[normalIndex] *= chainWidth / 2.0F / normalLength;
               normalZ[normalIndex] *= chainWidth / 2.0F / normalLength;
            }

            for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
               builder.vertex(pointX[segmentIndex] - normalX[segmentIndex], pointY[segmentIndex] - normalY[segmentIndex], pointZ[segmentIndex] - normalZ[segmentIndex])
                  .texture((float)segmentIndex / segmentCount * chainLength / (chainWidth * (1.3333F)), 0.0F)
                  .color(this.effectColor(segmentIndex * (24.0F), 0.0F));
               builder.vertex(pointX[segmentIndex + 1] - normalX[segmentIndex + 1], pointY[segmentIndex + 1] - normalY[segmentIndex + 1], pointZ[segmentIndex + 1] - normalZ[segmentIndex + 1])
                  .texture((float)(segmentIndex + 1) / segmentCount * chainLength / (chainWidth * (1.3333F)), 0.0F)
                  .color(this.effectColor((segmentIndex + 1) * (24.0F), 0.0F));
               builder.vertex(pointX[segmentIndex + 1] + normalX[segmentIndex + 1], pointY[segmentIndex + 1] + normalY[segmentIndex + 1], pointZ[segmentIndex + 1] + normalZ[segmentIndex + 1])
                  .texture((float)(segmentIndex + 1) / segmentCount * chainLength / (chainWidth * (1.3333F)), 1.0F)
                  .color(this.effectColor((segmentIndex + 1) * (24.0F), 0.0F));
               builder.vertex(pointX[segmentIndex] + normalX[segmentIndex], pointY[segmentIndex] + normalY[segmentIndex], pointZ[segmentIndex] + normalZ[segmentIndex])
                  .texture((float)segmentIndex / segmentCount * chainLength / (chainWidth * (1.3333F)), 1.0F)
                  .color(this.effectColor(segmentIndex * (24.0F), 0.0F));
            }

            try (BuiltBuffer builtBuffer = builder.end()) {
               this.drawQuads(matrices, ("textures/particles/chain.png"), builtBuffer, segmentCount, EntityRaycast.hasLineOfSight(minecraftClient.player, projectile), true);
            }
         }
      }
   }

   public TargetEsp.TargetScanState scanStateFor(PlayerEntity player) {
      if (this.isEnabled()
         && this.mode.isSelected(("Круг"))
         && this.circleStyle.isSelected(("Четвёртый"))
         && this.target == player
         && !(this.targetFade.value() <= (0.0010000000474974513D))) {
         if (this.scanTarget != player || System.currentTimeMillis() - this.lastScanQueryAt > (250L)) {
            this.scanTarget = player;
            this.scanStartedAt = System.currentTimeMillis();
         }

         this.lastScanQueryAt = System.currentTimeMillis();
         float scanProgress = (float)((System.currentTimeMillis() - this.scanStartedAt) % (1800L)) / (1800.0F);
         return new TargetEsp.TargetScanState(
            this.effectColor(0.0F, this.ringHitFade),
            (-49088),
            scanProgress,
            this.smoothstep(0.0F, 1.0F, MathHelper.clamp((float)this.targetFade.value(), 0.0F, 1.0F))
               * this.smoothstep(0.0F, (0.08F), scanProgress)
               * (1.0F - this.smoothstep((0.88F), 1.0F, scanProgress)),
            MathHelper.clamp(this.ringHitFade, 0.0F, 1.0F),
            1.0F
         );
      } else {
         return null;
      }
   }

   private void drawTargetLightning(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      long nowMillis = System.currentTimeMillis();
      this.lightningElapsedSeconds = this.lightningElapsedSeconds + (float)Math.min(nowMillis - this.lastLightningFrameAt, (100L)) / (1000.0F);
      this.lastLightningFrameAt = nowMillis;
      this.lightningHitFade = entity.hurtTime > 0 ? entity.hurtTime / (10.0F) : Math.max(0.0F, this.lightningHitFade - (0.1F));
      int noiseFrame = (int)this.lightningElapsedSeconds;
      float noiseProgress = this.lightningElapsedSeconds - noiseFrame;
      float entityHeight = entity.getHeight();
      float orbitRadius = entity.getWidth() * (0.75F) + (0.16F);
      float relativeX = (float)(MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x);
      float relativeY = (float)(MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - minecraftClient.gameRenderer.getCamera().getCameraPos().y);
      float relativeZ = (float)(MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z);
      boolean visible = EntityRaycast.hasLineOfSight(minecraftClient.player, entity);
      this.ribbonHalfWidth = entityHeight * (0.005F);
      this.glowHalfSize = entityHeight * (0.09F);
      this.billboardRotation = minecraftClient.gameRenderer.getCamera().getRotation();
      int glowQuadCount = 0;
      int lightningQuadCount = 0;
      BufferAllocator lightningAllocator = this.geometryAllocator(0, 2048 * VertexFormats.POSITION_COLOR.getVertexSize());
      BufferAllocator glowAllocator = this.geometryAllocator(1, 2048 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      this.lightningBuilder = new BufferBuilder(lightningAllocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      this.glowBuilder = new BufferBuilder(glowAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

      for (int strandIndex = 0; strandIndex < 3; strandIndex++) {
         float strandTime = this.lightningElapsedSeconds * (1.3F) + strandIndex / (3.0F);
         int strandFrame = (int)Math.floor(strandTime);
         float strandProgress = strandTime - strandFrame;
         float strandAlpha = MathHelper.clamp(strandProgress / (0.08F), 0.0F, 1.0F)
            * (1.0F - MathHelper.clamp((strandProgress - (0.42F)) / (0.3F), 0.0F, 1.0F))
            * MathHelper.clamp((0.85F) + (0.15F) * this.interpolatedNoise(strandIndex, 900, noiseFrame, noiseProgress), 0.0F, 1.0F)
            * opacity;
         if (!(strandAlpha < (0.003921569F))) {
            this.lightningColor = ArgbColor.multiplyAlpha(
               ArgbColor.withAlpha(
                  ArgbColor.lerp(
                     ArgbColor.lerp(
                        -1, -1, (float)(Math.sin(System.currentTimeMillis() / (700.0D) + Math.toRadians((44.0D))) + 1.0) / 2.0F
                     ),
                     (-49088),
                     hit
                  ),
                  255
               ),
               strandAlpha
            );
            this.glowColor = ArgbColor.multiplyAlpha(this.lightningColor, (0.13F));
            float initialOrbitAngle = this.seededNoise(strandIndex, 11, strandFrame) * (3.1415927F);
            float orbitTurns = (0.6F) + (0.5F) * Math.abs(this.seededNoise(strandIndex, 12, strandFrame));
            float orbitDirection = this.seededNoise(strandIndex, 13, strandFrame) >= 0.0F ? 1.0F : (-1.0F);
            float strandStartHeight = entityHeight * ((0.85F) + (0.05F) * this.seededNoise(strandIndex, 14, strandFrame));
            float strandHeightSpan = entityHeight * (0.67F);
            float strandRadius = orbitRadius * (1.0F + (0.12F) * this.seededNoise(strandIndex, 15, strandFrame));
            float verticalOscillation = entityHeight * (0.16F) * MathHelper.sin(this.lightningElapsedSeconds * (1.7F) + strandIndex * (2.1F));
            float previousX = 0.0F;
            float previousY = 0.0F;
            float previousZ = 0.0F;

            for (int sectionIndex = 0; sectionIndex <= 3; sectionIndex++) {
               float sectionProgress = sectionIndex / (3.0F);
               float orbitAngle = initialOrbitAngle + this.lightningElapsedSeconds * (0.9F) + orbitDirection * orbitTurns * (6.2831855F) * sectionProgress;
               float pointX = relativeX + MathHelper.cos(orbitAngle) * strandRadius;
               float pointY = relativeY + strandStartHeight - strandHeightSpan * sectionProgress + verticalOscillation;
               float pointZ = relativeZ + MathHelper.sin(orbitAngle) * strandRadius;
               if (sectionIndex > 0) {
                  lightningQuadCount += this.emitLightningBranches(previousX, previousY, previousZ, pointX, pointY, pointZ, (strandIndex * 131 + sectionIndex) * 17 + strandFrame * 101 | 1, noiseFrame, noiseProgress, sectionIndex == 3);
               }

               previousX = pointX;
               previousY = pointY;
               previousZ = pointZ;
            }
         }
      }

      glowQuadCount = lightningQuadCount;
      if (glowQuadCount > 0) {
         try (BuiltBuffer glowBuffer = this.glowBuilder.end()) {
            this.drawQuads(matrices, ("textures/particles/glow.png"), glowBuffer, glowQuadCount, visible, false);
         }
      }

      if (lightningQuadCount > 0) {
         try (BuiltBuffer lightningBuffer = this.lightningBuilder.end()) {
            this.drawQuads(matrices, null, lightningBuffer, lightningQuadCount, visible, false);
         }
      }
   }

   private int effectColor(float phaseDegrees, float hit) {
      return ArgbColor.lerp(
         ArgbColor.lerp(
            GuiVisuals.accentColor, GuiVisuals.accentLightColor, (float)(Math.sin(System.currentTimeMillis() / (700.0D) + Math.toRadians(phaseDegrees)) + 1.0) / 2.0F
         ),
         (-49088),
         hit
      );
   }

   private void drawOrbitGhosts(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      Quaternionf cameraRotation = minecraftClient.gameRenderer.getCamera().getRotation();
      double relativeX = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x;
      double relativeY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - minecraftClient.gameRenderer.getCamera().getCameraPos().y;
      double relativeZ = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z;
      byte orbitCount = 4;
      byte trailSteps = 15;
      float orbitRadius = entity.getWidth();
      float verticalCenter = entity.getHeight() / 2.0F + (0.2F);
      float verticalAmplitude = (0.7F);
      float minimumSize = (0.2F);
      float maximumSize = (0.6F);
      double timeSeconds = System.currentTimeMillis() / (1000.0D);
      double orbitPhase = timeSeconds * (3.0D) % (6.283185307179586D);
      double trailAngleStep = Math.toRadians((60.0D)) / (trailSteps - 1);
      int quadCount = 0;
      BufferAllocator allocator = this.geometryAllocator(1, orbitCount * trailSteps * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      Vector3f scratch = new Vector3f();

      for (int orbitIndex = 0; orbitIndex < orbitCount; orbitIndex++) {
         double orbitTime = timeSeconds + orbitIndex * (15.0D);
         double orbitOffset = orbitIndex * (1.5707963267948966D);

         for (int trailIndex = 0; trailIndex < trailSteps; trailIndex++) {
            double trailAngle = trailIndex * trailAngleStep;
            double sampleAngle = trailAngle + orbitPhase + orbitOffset;
            this.emitBillboard(
               builder,
               cameraRotation,
               scratch,
               relativeX + orbitRadius * Math.cos(sampleAngle),
               relativeY + verticalCenter + Math.sin(orbitTime + trailAngle + orbitIndex) * verticalAmplitude,
               relativeZ + orbitRadius * Math.sin(sampleAngle),
               MathHelper.lerp((float)trailIndex / (trailSteps - 1), minimumSize, maximumSize) / 2.0F,
               0.0F,
               ArgbColor.multiplyAlpha(this.effectColor(trailIndex * 14, hit), opacity)
            );
            quadCount++;
         }
      }

      try (BuiltBuffer builtBuffer = builder.end()) {
         this.drawQuads(matrices, ("textures/particles/glow.png"), builtBuffer, quadCount, false, false);
      }
   }

   private void subdivideLightning(float startX, float startY, float startZ, float endX, float endY, float endZ, int remainingDepth, int seed, int branchLevel, int noiseFrame, float noiseProgress) {
      float deltaX = endX - startX;
      float deltaY = endY - startY;
      float deltaZ = endZ - startZ;
      float segmentLength = MathHelper.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
      if (remainingDepth > 0 && !(segmentLength < (1.0E-4F))) {
         float directionX = deltaX / segmentLength;
         float directionY = deltaY / segmentLength;
         float directionZ = deltaZ / segmentLength;
         float referenceX = Math.abs(directionY) < (0.9F) ? 0.0F : 1.0F;
         float referenceY = Math.abs(directionY) < (0.9F) ? 1.0F : 0.0F;
         float normalX = -directionZ * referenceY;
         float normalY = directionZ * referenceX;
         float normalZ = directionX * referenceY - directionY * referenceX;
         float normalLength = MathHelper.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
         if (normalLength < (1.0E-5F)) {
            normalLength = 1.0F;
         }

         normalX /= normalLength;
         normalY /= normalLength;
         normalZ /= normalLength;
         float binormalX = directionY * normalZ - directionZ * normalY;
         float binormalY = directionZ * normalX - directionX * normalZ;
         float binormalZ = directionX * normalY - directionY * normalX;
         float noiseA = this.interpolatedNoise(seed, 0, noiseFrame, noiseProgress);
         float noiseB = this.interpolatedNoise(seed, 1, noiseFrame, noiseProgress);
         float displacement = segmentLength * (0.3F);
         float midpointX = (startX + endX) * (0.5F) + (normalX * noiseA + binormalX * noiseB) * displacement;
         float midpointY = (startY + endY) * (0.5F) + (normalY * noiseA + binormalY * noiseB) * displacement;
         float midpointZ = (startZ + endZ) * (0.5F) + (normalZ * noiseA + binormalZ * noiseB) * displacement;
         this.subdivideLightning(startX, startY, startZ, midpointX, midpointY, midpointZ, remainingDepth - 1, seed * 2 + 7, branchLevel, noiseFrame, noiseProgress);
         this.subdivideLightning(midpointX, midpointY, midpointZ, endX, endY, endZ, remainingDepth - 1, seed * 2 + 9, branchLevel, noiseFrame, noiseProgress);
         if (branchLevel < 2 && remainingDepth >= 2 && this.interpolatedNoise(seed, 2, noiseFrame, noiseProgress) > (0.45F)) {
            float branchNoiseA = this.interpolatedNoise(seed, 3, noiseFrame, noiseProgress);
            float branchNoiseB = this.interpolatedNoise(seed, 4, noiseFrame, noiseProgress);
            float branchDirectionX = directionX * (0.4F) + normalX * branchNoiseA + binormalX * branchNoiseB;
            float branchDirectionY = directionY * (0.4F) + normalY * branchNoiseA + binormalY * branchNoiseB;
            float branchDirectionZ = directionZ * (0.4F) + normalZ * branchNoiseA + binormalZ * branchNoiseB;
            float branchDirectionLength = MathHelper.sqrt(branchDirectionX * branchDirectionX + branchDirectionY * branchDirectionY + branchDirectionZ * branchDirectionZ);
            if (branchDirectionLength < (1.0E-5F)) {
               branchDirectionLength = 1.0F;
            }

            float branchLength = segmentLength * (0.75F);
            this.pendingLightningBranches
               .add(
                  new float[]{
                     midpointX,
                     midpointY,
                     midpointZ,
                     midpointX + branchDirectionX / branchDirectionLength * branchLength,
                     midpointY + branchDirectionY / branchDirectionLength * branchLength,
                     midpointZ + branchDirectionZ / branchDirectionLength * branchLength,
                     Math.min(remainingDepth - 1, 3),
                     seed * 3 + 13,
                     branchLevel + 1,
                     (0.55F)
                  }
               );
         }
      } else {
         this.appendLightningPoint(endX, endY, endZ);
      }
   }

   private void drawDepthScan(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      if (minecraftClient.getFramebuffer() != null
         && minecraftClient.getFramebuffer().getColorAttachmentView() != null
         && DepthSnapshot.depthView() != null) {
         float relativeX = (float)(MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x);
         float relativeY = (float)(MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - minecraftClient.gameRenderer.getCamera().getCameraPos().y);
         float relativeZ = (float)(MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z);
         float boundsHeight = entity.getHeight() + (0.3F);
         float radius = entity.getWidth() * (0.9F) + (0.3F);
         float scanHeight = entity.getHeight() - (0.25F);
         float scanAngle = (float)(System.currentTimeMillis() % (2200L)) / (2200.0F) * (6.2831855F);
         float verticalProgress = (0.5F) + (0.5F) * MathHelper.sin(scanAngle);
         float directionSign = MathHelper.cos(scanAngle) >= 0.0F ? 1.0F : (-1.0F);
         float speedFactor = Math.abs(MathHelper.cos(scanAngle));
         float bandY = relativeY + (0.12F) + scanHeight * verticalProgress;
         int scanColor = this.effectColor(0.0F, hit);
         if (this.scanUniforms == null || this.scanUniforms.isClosed()) {
            this.scanUniforms = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_target_scan_params"), 136, (112L));
         }

         MemoryStack memory = MemoryStack.stackPush();

         try {
            ByteBuffer uniformData = memory.calloc(112);
            new Matrix4f(WorldProjection.projectionMatrix()).mul(matrices.peek().getPositionMatrix()).invert().get(0, uniformData);
            uniformData.putFloat(64, relativeX);
            uniformData.putFloat(68, relativeY);
            uniformData.putFloat(72, relativeZ);
            uniformData.putFloat(76, radius);
            uniformData.putFloat(80, bandY);
            uniformData.putFloat(84, directionSign);
            uniformData.putFloat(88, speedFactor);
            uniformData.putFloat(92, (float)(System.currentTimeMillis() % (100000L)) / (1000.0F));
            uniformData.putFloat(96, relativeY + (0.06F));
            uniformData.putFloat(100, relativeY - (0.1F) + boundsHeight);
            uniformData.putFloat(104, opacity);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.scanUniforms.slice(0L, (112L)), uniformData);
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

         try (BufferAllocator allocator = new BufferAllocator(4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            builder.vertex((-1.0F), (-1.0F), 0.0F).texture(0.0F, 0.0F).color(scanColor);
            builder.vertex((-1.0F), 1.0F, 0.0F).texture(0.0F, 1.0F).color(scanColor);
            builder.vertex(1.0F, 1.0F, 0.0F).texture(1.0F, 1.0F).color(scanColor);
            builder.vertex(1.0F, (-1.0F), 0.0F).texture(1.0F, 0.0F).color(scanColor);

            try (BuiltBuffer builtBuffer = builder.end()) {
               if (this.scanVertices != null && !this.scanVertices.isClosed() && this.scanVertices.size() >= builtBuffer.getBuffer().remaining()) {
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.scanVertices.slice(0L, builtBuffer.getBuffer().remaining()), builtBuffer.getBuffer());
               } else {
                  if (this.scanVertices != null && !this.scanVertices.isClosed()) {
                     this.scanVertices.close();
                  }

                  this.scanVertices = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_target_ghosts_vertices"), 40, builtBuffer.getBuffer());
               }

               try (RenderPass renderPass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(
                        () -> ("mouseclient_target_scan"), minecraftClient.getFramebuffer().getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty()
                     )) {
                  renderPass.setPipeline(SCAN_PIPELINE);
                  renderPass.bindTexture(("Sampler0"), DepthSnapshot.depthView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                  renderPass.setUniform(("ScanParams"), this.scanUniforms);
                  renderPass.setVertexBuffer(0, this.scanVertices);
                  renderPass.setIndexBuffer(
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                  );
                  renderPass.drawIndexed(0, 0, 6, 1);
               }
            }
         }
      }
   }

   private float interpolatedNoise(int seed, int channel, int noiseFrame, float noiseProgress) {
      return MathHelper.lerp(noiseProgress * noiseProgress * ((3.0F) - 2.0F * noiseProgress), this.seededNoise(seed, channel, noiseFrame), this.seededNoise(seed, channel, noiseFrame + 1));
   }

   private void drawTargetCrystals(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      long nowMillis = System.currentTimeMillis();
      float orbitRadius = ((1.2F) - (0.5F) * opacity) * entity.getWidth() * (1.5F);
      float crystalScale = (0.1F);
      float glowSize = 1.0F;
      int crystalColor = ArgbColor.multiplyAlpha(this.effectColor(0.0F, hit), opacity);
      matrices.push();
      matrices.translate(
         entity.getLerpedPos(tickDelta).x - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
         entity.getLerpedPos(tickDelta).y - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
         entity.getLerpedPos(tickDelta).z - minecraftClient.gameRenderer.getCamera().getCameraPos().z
      );
      BufferAllocator allocator = this.geometryAllocator(0, 18 * CRYSTAL_FACES.length * 4 * VertexFormats.POSITION_COLOR.getVertexSize());
      BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      MatrixStack crystalMatrices = new MatrixStack();
      int quadCount = 0;

      for (int angleDegrees = 0; angleDegrees < 360; angleDegrees += 20) {
         float orbitAngle = (float)Math.toRadians(
            angleDegrees + (float)((nowMillis + (long)((7333.0F) * (angleDegrees / (360.0F)))) % (7333L)) / (7333.0F) * (360.0F)
         );
         float offsetX = MathHelper.sin(orbitAngle) * orbitRadius;
         float offsetZ = MathHelper.cos(orbitAngle) * orbitRadius;
         float offsetY = (0.1F) + entity.getHeight() * (float)Easings.QUAD_IN_OUT.apply(Math.abs(MathHelper.sin(angleDegrees)));
         crystalMatrices.push();
         crystalMatrices.translate(offsetX, offsetY, offsetZ);
         crystalMatrices.multiply(
            new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(-offsetX, entity.getHeight() * (0.5F) - offsetY, -offsetZ).normalize())
         );
         crystalMatrices.multiply(
            RotationAxis.POSITIVE_Y.rotationDegrees(((float)(nowMillis % (1200L)) / (1200.0F) * (360.0F) + angleDegrees * (13.7F)) % (360.0F))
         );
         crystalMatrices.scale(crystalScale, crystalScale, crystalScale);

         for (int faceIndex = 0; faceIndex < CRYSTAL_FACES.length; faceIndex++) {
            for (int cornerIndex = 0; cornerIndex < 4; cornerIndex++) {
               Vector3f corner = CRYSTAL_VERTICES[CRYSTAL_FACES[faceIndex][Math.min(cornerIndex, 2)]];
               builder.vertex(crystalMatrices.peek().getPositionMatrix(), corner.x, corner.y, corner.z)
                  .color(ArgbColor.lerp(crystalColor, ArgbColor.withAlpha(0, ArgbColor.alpha(crystalColor)), 1.0F - CRYSTAL_FACE_BRIGHTNESS[faceIndex]));
            }

            quadCount++;
         }

         crystalMatrices.pop();
      }

      try (BuiltBuffer crystalBuffer = builder.end()) {
         this.drawQuads(matrices, null, crystalBuffer, quadCount, EntityRaycast.hasLineOfSight(minecraftClient.player, entity), false);
      }

      allocator = this.geometryAllocator(1, 72 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      Vector3f scratch = new Vector3f();

      for (int glowAngleDegrees = 0; glowAngleDegrees < 360; glowAngleDegrees += 20) {
         float glowOrbitAngle = (float)Math.toRadians(
            glowAngleDegrees + (float)((nowMillis + (long)((7333.0F) * (glowAngleDegrees / (360.0F)))) % (7333L)) / (7333.0F) * (360.0F)
         );
         this.emitBillboard(
            builder,
            minecraftClient.gameRenderer.getCamera().getRotation(),
            scratch,
            MathHelper.sin(glowOrbitAngle) * orbitRadius,
            (0.1F) + entity.getHeight() * (float)Easings.QUAD_IN_OUT.apply(Math.abs(MathHelper.sin(glowAngleDegrees))),
            MathHelper.cos(glowOrbitAngle) * orbitRadius,
            glowSize / 2.0F,
            0.0F,
            ArgbColor.multiplyAlpha(crystalColor, (0.2F))
         );
      }

      try (BuiltBuffer glowBuffer = builder.end()) {
         this.drawQuads(matrices, ("textures/particles/glow.png"), glowBuffer, 18, EntityRaycast.hasLineOfSight(minecraftClient.player, entity), false);
      }

      matrices.pop();
   }

   private void drawTargetRing(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      long lastRingFrameAt = System.currentTimeMillis();
      this.ringElapsedSeconds = this.ringElapsedSeconds + (float)Math.min(lastRingFrameAt - this.lastRingFrameAt, (100L)) / (1000.0F);
      this.lastRingFrameAt = lastRingFrameAt;
      this.ringHitFade = entity.hurtTime > 0 ? entity.hurtTime / (10.0F) : Math.max(0.0F, this.ringHitFade - (0.1F));
      if (!this.circleStyle.isSelected(("Четвёртый"))) {
         if (this.circleStyle.isSelected(("Третий"))) {
            float thirdEntityHeight = entity.getHeight();
            float thirdPhase = (float)(System.currentTimeMillis() % (3600L)) / (3600.0F) * (6.2831855F);
            float thirdVerticalProgress = (0.5F) + (0.5F) * MathHelper.cos(thirdPhase);
            float thirdOscillation = MathHelper.sin(thirdPhase);
            float thirdReveal = this.smoothstep(0.0F, 1.0F, this.smoothstep(0.0F, 1.0F, MathHelper.clamp(opacity, 0.0F, 1.0F)));
            float thirdBandHeight = MathHelper.lerp(thirdVerticalProgress, (0.035F), thirdEntityHeight - (0.035F));
            float thirdTrailHeight = MathHelper.clamp(
               thirdBandHeight + thirdOscillation * Math.min((0.55F), thirdEntityHeight * (0.55F)) * this.smoothstep(0.0F, 1.0F, MathHelper.clamp(opacity, 0.0F, 1.0F)),
               (0.02F),
               thirdEntityHeight - (0.02F)
            );
            float thirdRelativeX = (float)(MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x);
            float thirdRelativeZ = (float)(MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z);
            float thirdRelativeY = (float)(MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - minecraftClient.gameRenderer.getCamera().getCameraPos().y);
            int thirdBaseColor = this.effectColor(0.0F, this.ringHitFade);
            int thirdHighlightColor = ArgbColor.lerp(thirdBaseColor, -1, (0.72F) * (1.0F - this.ringHitFade));
            float thirdTrailOpacity = thirdReveal * (float)Math.sqrt(Math.abs(thirdOscillation));
            float thirdHalfThickness = thirdEntityHeight * (0.019F);
            boolean thirdUseDepth = EntityRaycast.hasLineOfSight(minecraftClient.player, entity);
            float[] trailFractions = new float[]{0.0F, (0.18F), (0.52F), 1.0F};
            int[] trailAlphas = new int[]{125, 72, 24, 0};
            int thirdQuadCount = 0;
            BufferAllocator thirdAllocator = this.geometryAllocator(0, 16384 * VertexFormats.POSITION_COLOR.getVertexSize());
            BufferBuilder builder = new BufferBuilder(thirdAllocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (int trailBandIndex = 0; trailBandIndex < trailFractions.length - 1; trailBandIndex++) {
               thirdQuadCount += this.emitRingBand(
                  builder,
                  thirdRelativeX,
                  thirdRelativeY,
                  thirdRelativeZ,
                  entity,
                  thirdEntityHeight,
                  thirdReveal,
                  MathHelper.lerp(trailFractions[trailBandIndex], thirdBandHeight, thirdTrailHeight),
                  MathHelper.lerp(trailFractions[trailBandIndex + 1], thirdBandHeight, thirdTrailHeight),
                  0.0F,
                  0.0F,
                  ArgbColor.withAlpha(thirdBaseColor, (int)(trailAlphas[trailBandIndex] * thirdTrailOpacity)),
                  ArgbColor.withAlpha(thirdBaseColor, (int)(trailAlphas[trailBandIndex + 1] * thirdTrailOpacity))
               );
            }

            thirdQuadCount += this.emitRingBand(
               builder,
               thirdRelativeX,
               thirdRelativeY,
               thirdRelativeZ,
               entity,
               thirdEntityHeight,
               thirdReveal,
               Math.max(0.0F, thirdBandHeight - (0.075F)),
               thirdBandHeight,
               (0.025F),
               (0.035F),
               ArgbColor.withAlpha(thirdBaseColor, 0),
               ArgbColor.withAlpha(thirdBaseColor, (int)((72.0F) * thirdReveal))
            );
            thirdQuadCount += this.emitRingBand(
               builder,
               thirdRelativeX,
               thirdRelativeY,
               thirdRelativeZ,
               entity,
               thirdEntityHeight,
               thirdReveal,
               thirdBandHeight,
               Math.min(thirdEntityHeight, thirdBandHeight + (0.075F)),
               (0.035F),
               (0.025F),
               ArgbColor.withAlpha(thirdBaseColor, (int)((72.0F) * thirdReveal)),
               ArgbColor.withAlpha(thirdBaseColor, 0)
            );
            thirdQuadCount += this.emitRingBand(
               builder,
               thirdRelativeX,
               thirdRelativeY,
               thirdRelativeZ,
               entity,
               thirdEntityHeight,
               thirdReveal,
               thirdBandHeight - thirdHalfThickness,
               thirdBandHeight + thirdHalfThickness,
               (0.045F),
               (0.045F),
               ArgbColor.withAlpha(thirdHighlightColor, (int)((245.0F) * thirdReveal)),
               ArgbColor.withAlpha(thirdHighlightColor, (int)((245.0F) * thirdReveal))
            );

            for (int trailHighlightIndex = 1; trailHighlightIndex <= 5; trailHighlightIndex++) {
               thirdQuadCount += this.emitRingBand(
                  builder,
                  thirdRelativeX,
                  thirdRelativeY,
                  thirdRelativeZ,
                  entity,
                  thirdEntityHeight,
                  thirdReveal,
                  MathHelper.lerp(trailHighlightIndex / (6.0F), thirdBandHeight, thirdTrailHeight) - thirdHalfThickness,
                  MathHelper.lerp(trailHighlightIndex / (6.0F), thirdBandHeight, thirdTrailHeight) + thirdHalfThickness,
                  (0.012F),
                  (0.012F),
                  ArgbColor.withAlpha(thirdBaseColor, (int)((1.0F - trailHighlightIndex / (6.0F)) * (34.0F) * thirdTrailOpacity)),
                  ArgbColor.withAlpha(thirdBaseColor, (int)((1.0F - trailHighlightIndex / (6.0F)) * (34.0F) * thirdTrailOpacity))
               );
            }

            if (thirdQuadCount > 0) {
               try (BuiltBuffer thirdBuffer = builder.end()) {
                  this.drawQuads(matrices, null, thirdBuffer, thirdQuadCount, thirdUseDepth, false);
               }
            }
         } else if (this.circleStyle.isSelected(("Второй"))) {
            float secondPhaseMillis = (float)(System.currentTimeMillis() % (1750L));
            float secondDirection = secondPhaseMillis > (875.0F) ? (-1.0F) : 1.0F;
            float secondVerticalProgress = secondDirection < 0.0F ? secondPhaseMillis / (875.0F) - 1.0F : 1.0F - secondPhaseMillis / (875.0F);
            float secondEasedProgress = secondVerticalProgress < (0.5F) ? 2.0F * secondVerticalProgress * secondVerticalProgress : 1.0F - (float)Math.pow((-2.0F) * secondVerticalProgress + 2.0F, (2.0D)) / 2.0F;
            float secondTravelHeight = entity.getHeight() + (0.15F);
            float secondRadius = entity.getWidth() * (0.8F);
            float secondHalfThickness = entity.getHeight() * (0.006F);
            float secondRelativeX = (float)(MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x);
            float secondRelativeZ = (float)(MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z);
            float secondBandY = (float)(MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - minecraftClient.gameRenderer.getCamera().getCameraPos().y)
               + secondTravelHeight * secondEasedProgress;
            float secondTrailOffset = secondTravelHeight * (0.8F) * Math.min(secondEasedProgress, 1.0F - secondEasedProgress) * secondDirection;
            int secondBaseColor = ArgbColor.multiplyAlpha(this.effectColor(0.0F, this.ringHitFade), opacity);
            int secondTrailColor = ArgbColor.multiplyAlpha(secondBaseColor, (0.55F));
            int secondTransparentColor = ArgbColor.withAlpha(secondBaseColor, 0);
            boolean secondUseDepth = EntityRaycast.hasLineOfSight(minecraftClient.player, entity);
            int secondQuadCount = 0;
            BufferAllocator secondAllocator = this.geometryAllocator(0, 8192 * VertexFormats.POSITION_COLOR.getVertexSize());
            BufferBuilder secondBuilder = new BufferBuilder(secondAllocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (int secondAngleDegrees = 0; secondAngleDegrees < 360; secondAngleDegrees += 3) {
               float secondStartCos = MathHelper.cos((float)Math.toRadians(secondAngleDegrees));
               float secondStartSin = MathHelper.sin((float)Math.toRadians(secondAngleDegrees));
               float secondEndCos = MathHelper.cos((float)Math.toRadians(secondAngleDegrees + 3));
               float secondEndSin = MathHelper.sin((float)Math.toRadians(secondAngleDegrees + 3));
               secondBuilder.vertex(secondRelativeX + secondStartCos * (secondRadius - secondHalfThickness), secondBandY, secondRelativeZ + secondStartSin * (secondRadius - secondHalfThickness)).color(secondBaseColor);
               secondBuilder.vertex(secondRelativeX + secondEndCos * (secondRadius - secondHalfThickness), secondBandY, secondRelativeZ + secondEndSin * (secondRadius - secondHalfThickness)).color(secondBaseColor);
               secondBuilder.vertex(secondRelativeX + secondEndCos * (secondRadius + secondHalfThickness), secondBandY, secondRelativeZ + secondEndSin * (secondRadius + secondHalfThickness)).color(secondBaseColor);
               secondBuilder.vertex(secondRelativeX + secondStartCos * (secondRadius + secondHalfThickness), secondBandY, secondRelativeZ + secondStartSin * (secondRadius + secondHalfThickness)).color(secondBaseColor);
               secondQuadCount++;
               secondBuilder.vertex(secondRelativeX + secondStartCos * secondRadius, secondBandY, secondRelativeZ + secondStartSin * secondRadius).color(secondTrailColor);
               secondBuilder.vertex(secondRelativeX + secondEndCos * secondRadius, secondBandY, secondRelativeZ + secondEndSin * secondRadius).color(secondTrailColor);
               secondBuilder.vertex(secondRelativeX + secondEndCos * secondRadius, secondBandY + secondTrailOffset, secondRelativeZ + secondEndSin * secondRadius).color(secondTransparentColor);
               secondBuilder.vertex(secondRelativeX + secondStartCos * secondRadius, secondBandY + secondTrailOffset, secondRelativeZ + secondStartSin * secondRadius).color(secondTransparentColor);
               secondQuadCount++;
            }

            if (secondQuadCount > 0) {
               try (BuiltBuffer secondBuffer = secondBuilder.end()) {
                  this.drawQuads(matrices, null, secondBuffer, secondQuadCount, secondUseDepth, false);
               }
            }
         } else {
            float firstVerticalPhase = this.ringElapsedSeconds * (2.4F);
            float firstVerticalProgress = (0.5F) + (0.5F) * MathHelper.sin(firstVerticalPhase);
            float firstDirection = -MathHelper.cos(firstVerticalPhase);
            float firstWavePhase = this.ringElapsedSeconds * (1.6F);
            float firstRelativeX = (float)(MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x);
            float firstRelativeZ = (float)(MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z);
            float firstBandY = (float)(
               MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY())
                  + (entity.getHeight() + (0.1F)) * firstVerticalProgress
                  - minecraftClient.gameRenderer.getCamera().getCameraPos().y
            );
            float firstRadius = entity.getWidth() * (0.9F) + 1.0F * (1.0F - opacity);
            float firstHalfThickness = entity.getHeight() * (0.006F);
            int firstBaseColor = ArgbColor.multiplyAlpha(this.effectColor(0.0F, this.ringHitFade), opacity);
            int firstTrailColor = ArgbColor.multiplyAlpha(firstBaseColor, (0.28F));
            int firstTrailEndColor = ArgbColor.multiplyAlpha(firstBaseColor, (0.03F));
            int firstGlowColor = ArgbColor.multiplyAlpha(firstBaseColor, (0.12F));
            boolean firstUseDepth = EntityRaycast.hasLineOfSight(minecraftClient.player, entity);
            int firstBandQuadCount = 0;
            int firstGlowQuadCount = 0;
            BufferAllocator firstBandAllocator = this.geometryAllocator(0, 8192 * VertexFormats.POSITION_COLOR.getVertexSize());
            BufferAllocator firstGlowAllocator = this.geometryAllocator(1, 48 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
            BufferBuilder firstBandBuilder = new BufferBuilder(firstBandAllocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            BufferBuilder firstGlowBuilder = new BufferBuilder(firstGlowAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            Vector3f billboardScratch = new Vector3f();

            for (int firstAngleDegrees = 0; firstAngleDegrees < 360; firstAngleDegrees += 3) {
               float firstStartCos = MathHelper.cos((float)Math.toRadians(firstAngleDegrees));
               float firstStartSin = MathHelper.sin((float)Math.toRadians(firstAngleDegrees));
               float firstEndCos = MathHelper.cos((float)Math.toRadians(firstAngleDegrees + 3));
               float firstEndSin = MathHelper.sin((float)Math.toRadians(firstAngleDegrees + 3));
               firstBandBuilder.vertex(firstRelativeX + firstStartCos * (firstRadius - firstHalfThickness), firstBandY, firstRelativeZ + firstStartSin * (firstRadius - firstHalfThickness)).color(firstBaseColor);
               firstBandBuilder.vertex(firstRelativeX + firstEndCos * (firstRadius - firstHalfThickness), firstBandY, firstRelativeZ + firstEndSin * (firstRadius - firstHalfThickness)).color(firstBaseColor);
               firstBandBuilder.vertex(firstRelativeX + firstEndCos * (firstRadius + firstHalfThickness), firstBandY, firstRelativeZ + firstEndSin * (firstRadius + firstHalfThickness)).color(firstBaseColor);
               firstBandBuilder.vertex(firstRelativeX + firstStartCos * (firstRadius + firstHalfThickness), firstBandY, firstRelativeZ + firstStartSin * (firstRadius + firstHalfThickness)).color(firstBaseColor);
               firstBandQuadCount++;
               float firstTrailStartOffset = ((0.45F) + (0.12F) * MathHelper.sin((float)Math.toRadians(firstAngleDegrees) * (3.0F) - firstWavePhase)) * firstDirection;
               float firstTrailEndOffset = ((0.45F) + (0.12F) * MathHelper.sin((float)Math.toRadians(firstAngleDegrees + 3) * (3.0F) - firstWavePhase)) * firstDirection;
               firstBandBuilder.vertex(firstRelativeX + firstStartCos * firstRadius, firstBandY, firstRelativeZ + firstStartSin * firstRadius).color(firstTrailColor);
               firstBandBuilder.vertex(firstRelativeX + firstEndCos * firstRadius, firstBandY, firstRelativeZ + firstEndSin * firstRadius).color(firstTrailColor);
               firstBandBuilder.vertex(firstRelativeX + firstEndCos * firstRadius, firstBandY + firstTrailEndOffset, firstRelativeZ + firstEndSin * firstRadius).color(firstTrailEndColor);
               firstBandBuilder.vertex(firstRelativeX + firstStartCos * firstRadius, firstBandY + firstTrailStartOffset, firstRelativeZ + firstStartSin * firstRadius).color(firstTrailEndColor);
               firstBandQuadCount++;
            }

            for (int glowAngleDegrees = 0; glowAngleDegrees < 360; glowAngleDegrees += 7) {
               this.emitBillboard(
                  firstGlowBuilder,
                  minecraftClient.gameRenderer.getCamera().getRotation(),
                  billboardScratch,
                  firstRelativeX + MathHelper.cos((float)Math.toRadians(glowAngleDegrees)) * firstRadius,
                  firstBandY,
                  firstRelativeZ + MathHelper.sin((float)Math.toRadians(glowAngleDegrees)) * firstRadius,
                  firstRadius * (0.2F),
                  0.0F,
                  firstGlowColor
               );
               firstGlowQuadCount++;
            }

            if (firstGlowQuadCount > 0) {
               try (BuiltBuffer firstGlowBuffer = firstGlowBuilder.end()) {
                  this.drawQuads(matrices, ("textures/particles/glow.png"), firstGlowBuffer, firstGlowQuadCount, firstUseDepth, false);
               }
            }

            if (firstBandQuadCount > 0) {
               try (BuiltBuffer firstBandBuffer = firstBandBuilder.end()) {
                  this.drawQuads(matrices, null, firstBandBuffer, firstBandQuadCount, firstUseDepth, false);
               }
            }
         }
      }
   }

   private int emitLightningBranches(float startX, float startY, float startZ, float endX, float endY, float endZ, int seed, int noiseFrame, float noiseProgress, boolean taperEnd) {
      this.pendingLightningBranches.clear();
      this.lightningPointCount = 0;
      this.appendLightningPoint(startX, startY, startZ);
      this.subdivideLightning(startX, startY, startZ, endX, endY, endZ, 4, seed, 0, noiseFrame, noiseProgress);
      int quadCount = this.emitLightningRibbon(1.0F, taperEnd);

      for (int branchIndex = 0; branchIndex < this.pendingLightningBranches.size(); branchIndex++) {
         float[] branch = this.pendingLightningBranches.get(branchIndex);
         this.lightningPointCount = 0;
         this.appendLightningPoint(branch[0], branch[1], branch[2]);
         this.subdivideLightning(branch[0], branch[1], branch[2], branch[3], branch[4], branch[5], (int)branch[6], (int)branch[7], (int)branch[8], noiseFrame, noiseProgress);
         quadCount += this.emitLightningRibbon(branch[9], true);
      }

      this.pendingLightningBranches.clear();
      return quadCount;
   }

   private float ringRadiusAtHeight(LivingEntity entity, float normalizedHeight, float opacity) {
      return (
            MathHelper.clamp(normalizedHeight, 0.0F, 1.0F) < (0.48F)
               ? MathHelper.lerp(
                  this.smoothstep((0.24F), (0.46F), MathHelper.clamp(normalizedHeight, 0.0F, 1.0F)),
                  (entity.getWidth() * (0.5F) + (0.1F)) * (1.02F),
                  (entity.getWidth() * (0.5F) + (0.1F)) * (1.46F)
               )
               : (
                  MathHelper.clamp(normalizedHeight, 0.0F, 1.0F) > (0.68F)
                     ? MathHelper.lerp(
                        this.smoothstep((0.68F), (0.84F), MathHelper.clamp(normalizedHeight, 0.0F, 1.0F)),
                        (entity.getWidth() * (0.5F) + (0.1F)) * (1.46F),
                        (entity.getWidth() * (0.5F) + (0.1F)) * (1.16F)
                     )
                     : (entity.getWidth() * (0.5F) + (0.1F)) * (1.46F)
               )
         )
         * ((0.86F) + (0.14F) * this.smoothstep(0.0F, 1.0F, opacity));
   }

   private void appendLightningPoint(float x, float y, float z) {
      if (this.lightningPointCount * 3 + 3 > this.lightningPoints.length) {
         this.lightningPoints = Arrays.copyOf(this.lightningPoints, this.lightningPoints.length * 2);
         this.ribbonNormals = Arrays.copyOf(this.ribbonNormals, this.ribbonNormals.length * 2);
         this.ribbonMiterScales = Arrays.copyOf(this.ribbonMiterScales, this.ribbonMiterScales.length * 2);
      }

      this.lightningPoints[this.lightningPointCount * 3] = x;
      this.lightningPoints[this.lightningPointCount * 3 + 1] = y;
      this.lightningPoints[this.lightningPointCount * 3 + 2] = z;
      this.lightningPointCount++;
   }

   private void emitBillboard(BufferBuilder builder, Quaternionf rotation, Vector3f scratch, double x, double y, double z, float halfSize, float depthOffset, int color) {
      rotation.transform(scratch.set(-halfSize, -halfSize, -depthOffset));
      builder.vertex((float)(x + scratch.x), (float)(y + scratch.y), (float)(z + scratch.z)).texture(0.0F, 0.0F).color(color);
      rotation.transform(scratch.set(-halfSize, halfSize, -depthOffset));
      builder.vertex((float)(x + scratch.x), (float)(y + scratch.y), (float)(z + scratch.z)).texture(0.0F, 1.0F).color(color);
      rotation.transform(scratch.set(halfSize, halfSize, -depthOffset));
      builder.vertex((float)(x + scratch.x), (float)(y + scratch.y), (float)(z + scratch.z)).texture(1.0F, 1.0F).color(color);
      rotation.transform(scratch.set(halfSize, -halfSize, -depthOffset));
      builder.vertex((float)(x + scratch.x), (float)(y + scratch.y), (float)(z + scratch.z)).texture(1.0F, 0.0F).color(color);
   }

   private void drawQuads(MatrixStack matrices, String texturePath, BuiltBuffer builtBuffer, int quadCount, boolean useDepth, boolean repeatTexture) {
      if (minecraftClient.getFramebuffer() != null && minecraftClient.getFramebuffer().getColorAttachmentView() != null) {
         Identifier id = texturePath == null ? null : this.textureIds.computeIfAbsent(texturePath, textureResourcePath -> Identifier.of(("mouseclient"), textureResourcePath));
         if (id == null || minecraftClient.getTextureManager().getTexture(id).getGlTextureView() != null) {
            int bufferIndex = this.quadBufferCursor++ % this.quadVertexBuffers.length;
            GpuBuffer buffer = this.quadVertexBuffers[bufferIndex];
            if (buffer != null && !buffer.isClosed() && buffer.size() >= builtBuffer.getBuffer().remaining()) {
               RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, builtBuffer.getBuffer().remaining()), builtBuffer.getBuffer());
            } else {
               if (buffer != null && !buffer.isClosed()) {
                  buffer.close();
               }

               buffer = this.quadVertexBuffers[bufferIndex] = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_target_quads_vertices"), 40, builtBuffer.getBuffer());
            }

            GpuBufferSlice bufferSlice = RenderSystem.getDynamicUniforms().write(matrices.peek().getPositionMatrix(), WHITE_MODULATOR, ZERO_OFFSET, IDENTITY_TEXTURE_MATRIX);

            try (RenderPass renderPass = RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(
                     () -> ("mouseclient_target_quads"),
                     minecraftClient.getFramebuffer().getColorAttachmentView(),
                     OptionalInt.empty(),
                     useDepth ? DepthSnapshot.depthView() : null,
                     OptionalDouble.empty()
                  )) {
               renderPass.setPipeline(texturePath == null ? (useDepth ? DEPTH_CRYSTAL_PIPELINE : CRYSTAL_PIPELINE) : (useDepth ? DEPTH_GHOST_PIPELINE : GHOST_PIPELINE));
               if (id != null) {
                  renderPass.bindTexture(
                     ("Sampler0"),
                     minecraftClient.getTextureManager().getTexture(id).getGlTextureView(),
                     repeatTexture ? RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR) : RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
                  );
               }

               renderPass.setUniform(("DynamicTransforms"), bufferSlice);
               renderPass.setUniform(("Projection"), RenderSystem.getProjectionMatrixBuffer());
               renderPass.setVertexBuffer(0, buffer);
               renderPass.setIndexBuffer(
                  RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(quadCount * 6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
               );
               renderPass.drawIndexed(0, 0, quadCount * 6, 1);
            }
         }
      }
   }

   private void drawTargetDiamond(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      this.diamondUnhurtScale = MathHelper.lerp((0.22F), this.diamondUnhurtScale, entity.hurtTime > 0 ? 0.0F : 1.0F);
      float halfSize = (2.0F - opacity + (0.3F) * this.diamondUnhurtScale) / 2.0F;
      Quaternionf rotation = new Quaternionf(minecraftClient.gameRenderer.getCamera().getRotation())
         .rotateZ((float)Math.toRadians((float)(System.currentTimeMillis() % (3000L)) / (3000.0F) * (360.0F)));
      matrices.push();
      matrices.translate(
         entity.getLerpedPos(tickDelta).x - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
         entity.getLerpedPos(tickDelta).y + entity.getHeight() * (0.5F) - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
         entity.getLerpedPos(tickDelta).z - minecraftClient.gameRenderer.getCamera().getCameraPos().z
      );
      BufferAllocator allocator = this.geometryAllocator(1, 8 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      Vector3f scratch = new Vector3f();

      for (float layerAlpha : new float[]{1.0F, (0.5F)}) {
         rotation.transform(scratch.set(-halfSize, -halfSize, 0.0F));
         builder.vertex(scratch.x, scratch.y, scratch.z).texture(0.0F, 0.0F).color(ArgbColor.multiplyAlpha(this.effectColor((270.0F), hit), opacity * layerAlpha));
         rotation.transform(scratch.set(-halfSize, halfSize, 0.0F));
         builder.vertex(scratch.x, scratch.y, scratch.z).texture(0.0F, 1.0F).color(ArgbColor.multiplyAlpha(this.effectColor(0.0F, hit), opacity * layerAlpha));
         rotation.transform(scratch.set(halfSize, halfSize, 0.0F));
         builder.vertex(scratch.x, scratch.y, scratch.z).texture(1.0F, 1.0F).color(ArgbColor.multiplyAlpha(this.effectColor((180.0F), hit), opacity * layerAlpha));
         rotation.transform(scratch.set(halfSize, -halfSize, 0.0F));
         builder.vertex(scratch.x, scratch.y, scratch.z).texture(1.0F, 0.0F).color(ArgbColor.multiplyAlpha(this.effectColor((90.0F), hit), opacity * layerAlpha));
      }

      try (BuiltBuffer builtBuffer = builder.end()) {
         this.drawQuads(matrices, ("textures/particles/target.png"), builtBuffer, 2, false, false);
      }

      matrices.pop();
   }

   private void drawTargetCrown(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      long now = System.currentTimeMillis();
      float spin = (now % 5200L) / 5200.0F * 360.0F;
      float bob = (float)Math.sin(now / 420.0) * 0.035F;
      float flare = 1.0F + hit * 0.22F;
      int shards = 6;
      int color = ArgbColor.multiplyAlpha(this.effectColor(24.0F, hit), opacity);
      matrices.push();
      matrices.translate(
         entity.getLerpedPos(tickDelta).x - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
         entity.getLerpedPos(tickDelta).y - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
         entity.getLerpedPos(tickDelta).z - minecraftClient.gameRenderer.getCamera().getCameraPos().z
      );
      BufferAllocator allocator = this.geometryAllocator(0, shards * CRYSTAL_FACES.length * 4 * VertexFormats.POSITION_COLOR.getVertexSize());
      BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      MatrixStack shardMatrices = new MatrixStack();
      int quadCount = 0;
      float radius = entity.getWidth() * 0.42F * flare;

      for (int index = 0; index < shards; index++) {
         float angle = (float)Math.toRadians(spin + index * (360.0F / shards));
         float x = MathHelper.sin(angle) * radius;
         float z = MathHelper.cos(angle) * radius;
         float y = entity.getHeight() + 0.18F + bob;
         shardMatrices.push();
         shardMatrices.translate(x, y, z);
         shardMatrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-spin - index * 18.0F));
         shardMatrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(28.0F));
         shardMatrices.scale(0.11F * flare, 0.2F * flare, 0.11F * flare);

         for (int faceIndex = 0; faceIndex < CRYSTAL_FACES.length; faceIndex++) {
            for (int cornerIndex = 0; cornerIndex < 4; cornerIndex++) {
               Vector3f corner = CRYSTAL_VERTICES[CRYSTAL_FACES[faceIndex][Math.min(cornerIndex, 2)]];
               builder.vertex(shardMatrices.peek().getPositionMatrix(), corner.x, corner.y, corner.z)
                  .color(ArgbColor.lerp(color, ArgbColor.withAlpha(color, 40), 1.0F - CRYSTAL_FACE_BRIGHTNESS[faceIndex]));
            }

            quadCount++;
         }

         shardMatrices.pop();
      }

      try (BuiltBuffer crystalBuffer = builder.end()) {
         this.drawQuads(matrices, null, crystalBuffer, quadCount, false, false);
      }

      allocator = this.geometryAllocator(1, (shards * 2 + 16) * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      Vector3f scratch = new Vector3f();
      Quaternionf camera = minecraftClient.gameRenderer.getCamera().getRotation();

      for (int index = 0; index < shards; index++) {
         float angle = (float)Math.toRadians(spin + index * (360.0F / shards));
         this.emitBillboard(builder, camera, scratch, MathHelper.sin(angle) * radius, entity.getHeight() + 0.08F + bob, MathHelper.cos(angle) * radius, 0.16F, 0.0F, ArgbColor.multiplyAlpha(color, 0.45F));
         this.emitBillboard(builder, camera, scratch, MathHelper.sin(angle) * radius, entity.getHeight() + 0.34F + bob, MathHelper.cos(angle) * radius, 0.1F, 0.0F, ArgbColor.multiplyAlpha(color, 0.7F));
      }

      for (int point = 0; point < 16; point++) {
         float angle = (float)Math.toRadians(spin * 0.35F + point * 22.5F);
         this.emitBillboard(builder, camera, scratch, MathHelper.sin(angle) * radius * 0.92F, entity.getHeight() + 0.02F, MathHelper.cos(angle) * radius * 0.92F, 0.045F, 0.0F, ArgbColor.multiplyAlpha(this.effectColor(point * 18.0F, hit), opacity * 0.55F));
      }

      try (BuiltBuffer glowBuffer = builder.end()) {
         this.drawQuads(matrices, ("textures/particles/glow.png"), glowBuffer, shards * 2 + 16, false, false);
      }

      matrices.pop();
   }

   private void drawTargetCage(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      float spin = (System.currentTimeMillis() % 7000L) / 7000.0F * ((float)Math.PI * 2.0F);
      float squeeze = 1.0F - hit * 0.12F;
      float rx = entity.getWidth() * 0.78F * squeeze;
      float ry = entity.getHeight() * 0.52F;
      float rz = entity.getWidth() * 0.78F * squeeze;
      float centerY = entity.getHeight() * 0.5F;
      int meridians = 6;
      int steps = 18;
      int latitudes = 2;
      int latSteps = 28;
      int quadCount = meridians * steps + latitudes * latSteps;
      Vec3d toCamera = minecraftClient.gameRenderer.getCamera().getCameraPos().subtract(entity.getLerpedPos(tickDelta).add(0.0, centerY, 0.0));
      matrices.push();
      matrices.translate(
         entity.getLerpedPos(tickDelta).x - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
         entity.getLerpedPos(tickDelta).y - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
         entity.getLerpedPos(tickDelta).z - minecraftClient.gameRenderer.getCamera().getCameraPos().z
      );
      BufferAllocator allocator = this.geometryAllocator(0, quadCount * 4 * VertexFormats.POSITION_COLOR.getVertexSize());
      BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
      Matrix4f matrix = matrices.peek().getPositionMatrix();

      for (int meridian = 0; meridian < meridians; meridian++) {
         float bearing = spin + meridian / (float)meridians * ((float)Math.PI * 2.0F);

         for (int step = 0; step < steps; step++) {
            float a0 = step / (float)steps * (float)Math.PI;
            float a1 = (step + 1) / (float)steps * (float)Math.PI;
            int color = ArgbColor.multiplyAlpha(this.effectColor(step * 12.0F + meridian * 30.0F, hit), opacity * (0.35F + 0.65F * (float)Math.sin(a0)));
            this.emitLocalRibbon(
               builder, matrix,
               MathHelper.sin(a0) * MathHelper.cos(bearing) * rx, centerY + MathHelper.cos(a0) * ry, MathHelper.sin(a0) * MathHelper.sin(bearing) * rz,
               MathHelper.sin(a1) * MathHelper.cos(bearing) * rx, centerY + MathHelper.cos(a1) * ry, MathHelper.sin(a1) * MathHelper.sin(bearing) * rz,
               0.018F, color, toCamera
            );
         }
      }

      for (int ring = 0; ring < latitudes; ring++) {
         float height = ring == 0 ? 0.34F : 0.68F;
         float ringAngle = (float)Math.acos(1.0F - height * 2.0F);
         float ringRadiusX = MathHelper.sin(ringAngle) * rx;
         float ringRadiusZ = MathHelper.sin(ringAngle) * rz;
         float y = entity.getHeight() * height;

         for (int step = 0; step < latSteps; step++) {
            float a0 = spin * 0.6F + step / (float)latSteps * ((float)Math.PI * 2.0F);
            float a1 = spin * 0.6F + (step + 1) / (float)latSteps * ((float)Math.PI * 2.0F);
            this.emitLocalRibbon(
               builder, matrix,
               MathHelper.cos(a0) * ringRadiusX, y, MathHelper.sin(a0) * ringRadiusZ,
               MathHelper.cos(a1) * ringRadiusX, y, MathHelper.sin(a1) * ringRadiusZ,
               0.016F, ArgbColor.multiplyAlpha(this.effectColor(step * 10.0F, hit), opacity * 0.7F), toCamera
            );
         }
      }

      try (BuiltBuffer cageBuffer = builder.end()) {
         this.drawQuads(matrices, null, cageBuffer, quadCount, false, false);
      }

      allocator = this.geometryAllocator(1, 8 * 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
      builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
      Vector3f scratch = new Vector3f();
      Quaternionf camera = minecraftClient.gameRenderer.getCamera().getRotation();
      int glow = ArgbColor.multiplyAlpha(this.effectColor(0.0F, hit), opacity * 0.55F);
      this.emitBillboard(builder, camera, scratch, 0.0, entity.getHeight() + ry * 0.15, 0.0, 0.14F, 0.0F, glow);
      this.emitBillboard(builder, camera, scratch, 0.0, -0.02, 0.0, 0.12F, 0.0F, glow);

      try (BuiltBuffer glowBuffer = builder.end()) {
         this.drawQuads(matrices, ("textures/particles/glow.png"), glowBuffer, 2, false, false);
      }

      matrices.pop();
   }

   private void emitLocalRibbon(BufferBuilder builder, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float width, int color, Vec3d toCamera) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float dz = z2 - z1;
      float sx = dy * (float)toCamera.z - dz * (float)toCamera.y;
      float sy = dz * (float)toCamera.x - dx * (float)toCamera.z;
      float sz = dx * (float)toCamera.y - dy * (float)toCamera.x;
      float length = MathHelper.sqrt(sx * sx + sy * sy + sz * sz);
      if (length < 1.0E-4F) {
         sx = width;
         sy = 0.0F;
         sz = 0.0F;
      } else {
         sx = sx / length * width;
         sy = sy / length * width;
         sz = sz / length * width;
      }

      builder.vertex(matrix, x1 + sx, y1 + sy, z1 + sz).color(color);
      builder.vertex(matrix, x1 - sx, y1 - sy, z1 - sz).color(color);
      builder.vertex(matrix, x2 - sx, y2 - sy, z2 - sz).color(color);
      builder.vertex(matrix, x2 + sx, y2 + sy, z2 + sz).color(color);
   }

   @Override
   protected void onDisable() {
      this.target = null;
      this.ghostOrbitPhase = 0.0F;
      this.chainRotationDegrees = 0.0F;
      this.chainFade.reset(0.0);
      this.lightningElapsedSeconds = 0.0F;
      this.lastLightningFrameAt = System.currentTimeMillis();
      this.lightningHitFade = 0.0F;
      this.ringElapsedSeconds = 0.0F;
      this.lastRingFrameAt = System.currentTimeMillis();
      this.ringHitFade = 0.0F;
      this.scanTarget = null;
      this.scanStartedAt = 0L;
      this.lastScanQueryAt = 0L;
      this.diamondUnhurtScale = 0.0F;
      Arrays.fill(this.skullAlphas, 0.0F);
      this.lastSkullFrameAt = System.currentTimeMillis();
      if (this.scanVertices != null && !this.scanVertices.isClosed()) {
         this.scanVertices.close();
      }

      this.scanVertices = null;

      for (int bufferIndex = 0; bufferIndex < this.quadVertexBuffers.length; bufferIndex++) {
         if (this.quadVertexBuffers[bufferIndex] != null && !this.quadVertexBuffers[bufferIndex].isClosed()) {
            this.quadVertexBuffers[bufferIndex].close();
         }

         this.quadVertexBuffers[bufferIndex] = null;
      }

      this.quadBufferCursor = 0;

      for (int allocatorIndex = 0; allocatorIndex < this.geometryAllocators.length; allocatorIndex++) {
         if (this.geometryAllocators[allocatorIndex] != null) {
            this.geometryAllocators[allocatorIndex].close();
         }

         this.geometryAllocators[allocatorIndex] = null;
      }
   }

   public static TargetEsp getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new TargetEsp();
      }

      return INSTANCE;
   }

   private void drawHealthSkull(MatrixStack matrices, LivingEntity entity, float tickDelta, float opacity, float hit) {
      long nowMillis = System.currentTimeMillis();
      float deltaSeconds = (float)Math.min(nowMillis - this.lastSkullFrameAt, (100L)) / (1000.0F);
      this.lastSkullFrameAt = nowMillis;
      float health = entity instanceof PlayerEntity ? CombatTargeting.displayedHealth(entity) : entity.getHealth();
      int healthLevel = health < (8.0F) ? 2 : (health < (13.0F) ? 1 : 0);

      for (int fadeIndex = 0; fadeIndex < this.skullAlphas.length; fadeIndex++) {
         this.skullAlphas[fadeIndex] = MathHelper.clamp(this.skullAlphas[fadeIndex] + (fadeIndex == healthLevel ? deltaSeconds : -deltaSeconds) / (0.25F), 0.0F, 1.0F);
      }

      float halfSize = (0.42F);
      double relativeX = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x;
      double relativeY = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY())
         + entity.getHeight() * (0.55F)
         - minecraftClient.gameRenderer.getCamera().getCameraPos().y;
      double relativeZ = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z;
      Quaternionf cameraRotation = minecraftClient.gameRenderer.getCamera().getRotation();
      int skullColor = ArgbColor.multiplyAlpha(ArgbColor.lerp(HudStyle.accentColor, (-49088), hit), opacity);
      Vector3f scratch = new Vector3f();
      BufferBuilder builder = new BufferBuilder(
         this.geometryAllocator(1, 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize()), DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR
      );
      this.emitBillboard(builder, cameraRotation, scratch, relativeX, relativeY, relativeZ, halfSize * (1.6F), 0.0F, ArgbColor.multiplyAlpha(skullColor, (0.22F)));

      try (BuiltBuffer glowBuffer = builder.end()) {
         this.drawQuads(matrices, ("textures/particles/glow.png"), glowBuffer, 1, false, false);
      }

      String[] skullTextures = new String[]{("textures/img.png"), ("textures/img_1.png"), ("textures/img_2.png")};

      for (int textureIndex = 0; textureIndex < skullTextures.length; textureIndex++) {
         if (!(this.skullAlphas[textureIndex] < (0.003921569F))) {
            int layerColor = ArgbColor.multiplyAlpha(skullColor, this.skullAlphas[textureIndex]);
            BufferBuilder skullBuilder = new BufferBuilder(
               this.geometryAllocator(1, 4 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize()), DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR
            );
            cameraRotation.transform(scratch.set(-halfSize, -halfSize, 0.0F));
            skullBuilder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z)).texture(0.0F, 1.0F).color(layerColor);
            cameraRotation.transform(scratch.set(-halfSize, halfSize, 0.0F));
            skullBuilder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z)).texture(0.0F, 0.0F).color(layerColor);
            cameraRotation.transform(scratch.set(halfSize, halfSize, 0.0F));
            skullBuilder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z)).texture(1.0F, 0.0F).color(layerColor);
            cameraRotation.transform(scratch.set(halfSize, -halfSize, 0.0F));
            skullBuilder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z)).texture(1.0F, 1.0F).color(layerColor);

            try (BuiltBuffer skullBuffer = skullBuilder.end()) {
               this.drawQuads(matrices, skullTextures[textureIndex], skullBuffer, 1, false, false);
            }
         }
      }
   }

   private float seededNoise(int seed, int channel, int noiseFrame) {
      int hash = seed * (668265261) + channel * (374761393) + noiseFrame * (-1640531535);
      hash ^= hash >>> 15;
      hash *= (-2048144789);
      hash ^= hash >>> 13;
      return (hash >>> 8) / (8388608.0F) - 1.0F;
   }

   private TargetEsp() {
      this.targetFade = new ScalarAnimation((350L));
      this.lastLightningFrameAt = System.currentTimeMillis();
      this.lastRingFrameAt = System.currentTimeMillis();
      this.lightningPoints = new float[192];
      this.ribbonNormals = new float[192];
      this.ribbonMiterScales = new float[64];
      this.pendingLightningBranches = new ArrayList<>();
      this.billboardScratch = new Vector3f();
      this.projectileAlphas = new HashMap<>();
      this.hashMap2 = new HashMap<>();
      this.chainFade = new ScalarAnimation((400L));
      this.skullAlphas = new float[3];
      this.lastSkullFrameAt = System.currentTimeMillis();
      this.quadVertexBuffers = new GpuBuffer[8];
      this.geometryAllocators = new BufferAllocator[2];
      this.textureIds = new HashMap<>();
      this.deferredModelView = new Matrix4f();
      this.worldRenderListener = event -> {
         if (minecraftClient.player != null && minecraftClient.world != null) {
            this.quadBufferCursor = 0;
            if (this.mode.isSelected(("Цепи")) && this.trident.getValue()) {
               this.drawProjectileChains(event.getMatrices(), event.getTickDelta());
            }

            if (this.mode.isSelected(("Молнии")) && this.projectiles.getValue()) {
               this.drawProjectileLightning(event.getMatrices(), event.getTickDelta());
            }

            LivingEntity selectedTarget = AttackAura.getInstance().isEnabled() && AttackAura.getInstance().target() != null
               ? AttackAura.getInstance().target()
               : (
                  AimAssist.getInstance().isEnabled() && AimAssist.getInstance().target() != null
                     ? AimAssist.getInstance().target()
                     : (TriggerBot.getInstance().isEnabled() ? TriggerBot.getInstance().target() : null)
               );
            if (selectedTarget != null && selectedTarget != this.target) {
               this.target = selectedTarget;
               this.targetFade.reset(0.0);
               this.ghostOrbitPhase = 0.0F;
               this.lastGhostFrameAt = System.currentTimeMillis();
               this.chainFade.reset(0.0);
               this.lastChainFrameAt = System.currentTimeMillis();
            }

            this.targetFade.setTarget(selectedTarget != null ? 1.0 : 0.0);
            if (this.target != null) {
               if (this.target.isRemoved()) {
                  this.target = null;
               } else {
                  float opacity = (float)this.targetFade.value();
                  if (opacity < (0.003921569F)) {
                     if (selectedTarget == null) {
                        this.target = null;
                     }

                     this.lightningElapsedSeconds = 0.0F;
                  } else {
                     float hit = this.target.hurtTime / (10.0F);
                     if (this.mode.isSelected(("Призраки"))) {
                        if (Tags.getInstance().isOutlineShaderEnabled()) {
                           this.deferredGhostsPending = true;
                           this.deferredOpacity = opacity;
                           this.deferredHit = hit;
                           this.deferredTickDelta = event.getTickDelta();
                           this.deferredModelView.set(event.getMatrices().peek().getPositionMatrix());
                           this.deferredProjection = RenderSystem.getProjectionMatrixBuffer();
                           this.deferredProjectionType = RenderSystem.getProjectionType();
                        } else if (this.ghostStyle.isSelected(("Второй"))) {
                           this.drawOrbitGhosts(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                        } else {
                           this.drawBloomGhosts(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                        }
                     }

                     if (this.mode.isSelected(("Молнии"))) {
                        this.drawTargetLightning(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                     }

                     if (this.mode.isSelected(("Круг"))) {
                        this.drawTargetRing(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                     }

                     if (this.mode.isSelected(("Кристаллы"))) {
                        this.drawTargetCrystals(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                     }

                     if (this.mode.isSelected(("Ромб"))) {
                        this.drawTargetDiamond(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                     }

                     if (this.mode.isSelected(("Цепи"))) {
                        this.drawTargetChains(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                     }

                     if (this.mode.isSelected(("Череп"))) {
                        this.drawHealthSkull(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                     }
                     if (this.mode.isSelected(("Корона"))) {
                        this.drawTargetCrown(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                     }
                     if (this.mode.isSelected(("тест"))) {
                        this.drawTargetCage(event.getMatrices(), this.target, event.getTickDelta(), opacity, hit);
                     }
                  }
               }
            }
         }
      };
      this.mode = this.addSetting(new ModeSetting(("Мод"), ("Какой эффект рисовать на цели."), ("Призраки"), ("Призраки"), ("Молнии"), ("Круг"), ("Кристаллы"), ("Ромб"), ("Цепи"), ("Череп"), ("Корона"), ("тест")));
      this.ghostStyle = this.addSetting(new ModeSetting(("Стиль призраков"), ("Какими рисовать призраков на цели."), ("Второй"), ("Второй")));
      this.ghostStyle.setVisibility(() -> false);
      this.circleStyle = this.addSetting(new ModeSetting(("Стиль круга"), ("Каким рисовать круг на цели."), ("Второй"), ("Второй")));
      this.circleStyle.setVisibility(() -> false);
      this.trident = this.addSetting(new BooleanSetting(("Трезубец"), ("Тянет цепь от руки к летящим трезубцу и стрелам."), true).setVisibility(() -> this.mode.isSelected(("Цепи"))));
      this.projectiles = this.addSetting(new BooleanSetting(("Снаряды"), ("Тянет молнию от руки к летящим трезубцу и стрелам."), true).setVisibility(() -> this.mode.isSelected(("Молнии"))));
   }

   private void drawProjectileLightning(MatrixStack matrices, float tickDelta) {
      long nowMillis = System.currentTimeMillis();
      float deltaSeconds = (float)Math.min(nowMillis - this.lastLightningFrameAt, (100L)) / (1000.0F);
      this.lightningElapsedSeconds += deltaSeconds;
      this.lastLightningFrameAt = nowMillis;
      this.projectileAlphas.keySet().removeIf(entityId -> minecraftClient.world.getEntityById(entityId) == null);
      int noiseFrame = (int)this.lightningElapsedSeconds;
      float noiseProgress = this.lightningElapsedSeconds - noiseFrame;
      float pulseProgress = this.lightningElapsedSeconds * (1.7F) - (int)(this.lightningElapsedSeconds * (1.7F));
      float handX = (float)(
         MathHelper.lerp(tickDelta, minecraftClient.player.lastRenderX, minecraftClient.player.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x
      );
      float handY = (float)(
         MathHelper.lerp(tickDelta, minecraftClient.player.lastRenderY, minecraftClient.player.getY())
            + minecraftClient.player.getStandingEyeHeight() * (0.75F)
            - minecraftClient.gameRenderer.getCamera().getCameraPos().y
      );
      float handZ = (float)(
         MathHelper.lerp(tickDelta, minecraftClient.player.lastRenderZ, minecraftClient.player.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z
      );
      this.ribbonHalfWidth = (0.0037F);
      this.glowHalfSize = (0.047F);
      this.billboardRotation = minecraftClient.gameRenderer.getCamera().getRotation();

      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof PersistentProjectileEntity projectile && projectile.getOwner() == minecraftClient.player) {
            boolean flying = !projectile.isOnGround() && projectile.getVelocity().lengthSquared() > (1.0E-5D);
            float[] alphaState = this.projectileAlphas.computeIfAbsent(projectile.getId(), entityId -> new float[1]);
            alphaState[0] = MathHelper.clamp(alphaState[0] + (flying ? deltaSeconds / (0.15F) : -deltaSeconds / (0.3F)), 0.0F, 1.0F);
            if (!(alphaState[0] < (0.003921569F))) {
               float projectileX = (float)(MathHelper.lerp(tickDelta, projectile.lastRenderX, projectile.getX()) - minecraftClient.gameRenderer.getCamera().getCameraPos().x);
               float projectileY = (float)(MathHelper.lerp(tickDelta, projectile.lastRenderY, projectile.getY()) - minecraftClient.gameRenderer.getCamera().getCameraPos().y);
               float projectileZ = (float)(MathHelper.lerp(tickDelta, projectile.lastRenderZ, projectile.getZ()) - minecraftClient.gameRenderer.getCamera().getCameraPos().z);
               float deltaX = projectileX - handX;
               float deltaY = projectileY - handY;
               float deltaZ = projectileZ - handZ;
               float distance = MathHelper.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
               if (!(distance < 1.0F)) {
                  float directionX = deltaX / distance;
                  float directionY = deltaY / distance;
                  float directionZ = deltaZ / distance;
                  float referenceX = Math.abs(directionY) < (0.9F) ? 0.0F : 1.0F;
                  float referenceY = Math.abs(directionY) < (0.9F) ? 1.0F : 0.0F;
                  float normalX = -directionZ * referenceY;
                  float normalY = directionZ * referenceX;
                  float normalZ = directionX * referenceY - directionY * referenceX;
                  float normalLength = MathHelper.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
                  if (normalLength < (1.0E-5F)) {
                     normalLength = 1.0F;
                  }

                  normalX /= normalLength;
                  normalY /= normalLength;
                  normalZ /= normalLength;
                  float binormalX = directionY * normalZ - directionZ * normalY;
                  float binormalY = directionZ * normalX - directionX * normalZ;
                  float binormalZ = directionX * normalY - directionY * normalX;
                  float brightness = MathHelper.clamp((0.8F) + (0.2F) * this.interpolatedNoise(projectile.getId(), 900, noiseFrame, noiseProgress), 0.0F, 1.0F);
                  int segmentCount = MathHelper.clamp((int)Math.ceil(distance / (2.2F)), 2, 24);
                  int lightningQuadCount = 0;
                  BufferAllocator lightningAllocator = this.geometryAllocator(0, 2048 * VertexFormats.POSITION_COLOR.getVertexSize());
                  BufferAllocator glowAllocator = this.geometryAllocator(1, 2048 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize());
                  this.lightningBuilder = new BufferBuilder(lightningAllocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
                  this.glowBuilder = new BufferBuilder(glowAllocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                  float previousX = handX;
                  float previousY = handY;
                  float previousZ = handZ;

                  for (int segmentIndex = 1; segmentIndex <= segmentCount; segmentIndex++) {
                     float segmentProgress = (float)segmentIndex / segmentCount;
                     float waveEnvelope = MathHelper.sin((3.1415927F) * segmentProgress);
                     float wavePhase = segmentProgress * (7.0F) + this.lightningElapsedSeconds * (4.0F);
                     float waveAmplitude = Math.min((0.3F), distance * (0.06F)) * waveEnvelope;
                     float pointX = handX + deltaX * segmentProgress + (normalX * MathHelper.sin(wavePhase) + binormalX * MathHelper.cos(wavePhase)) * waveAmplitude;
                     float pointY = handY
                        + deltaY * segmentProgress
                        + (normalY * MathHelper.sin(wavePhase) + binormalY * MathHelper.cos(wavePhase)) * waveAmplitude
                        - Math.min((0.6F), distance * (0.035F)) * waveEnvelope;
                     float pointZ = handZ + deltaZ * segmentProgress + (normalZ * MathHelper.sin(wavePhase) + binormalZ * MathHelper.cos(wavePhase)) * waveAmplitude;
                     float pulseDistance = Math.abs((segmentIndex - (0.5F)) / segmentCount - pulseProgress);
                     pulseDistance = Math.min(pulseDistance, 1.0F - pulseDistance);
                     float pulseBrightness = Math.max(0.0F, 1.0F - pulseDistance / (0.22F));
                     this.lightningColor = ArgbColor.multiplyAlpha(
                        ArgbColor.withAlpha(ArgbColor.lerp(GuiVisuals.accentColor, -1, (0.4F) + (0.6F) * pulseBrightness), 255),
                        ((0.5F) + (0.5F) * pulseBrightness * pulseBrightness) * brightness * alphaState[0]
                     );
                     this.glowColor = ArgbColor.multiplyAlpha(this.lightningColor, (0.13F));
                     lightningQuadCount += this.emitLightningBranches(previousX, previousY, previousZ, pointX, pointY, pointZ, (projectile.getId() * 131 + segmentIndex) * 17 | 1, noiseFrame, noiseProgress, segmentIndex == segmentCount);
                     previousX = pointX;
                     previousY = pointY;
                     previousZ = pointZ;
                  }

                  int glowQuadCount = lightningQuadCount;
                  this.emitBillboard(
                     this.glowBuilder,
                     this.billboardRotation,
                     this.billboardScratch,
                     projectileX,
                     projectileY,
                     projectileZ,
                     (0.067F) + (0.02F) * MathHelper.sin(this.lightningElapsedSeconds * (9.0F) + projectile.getId()),
                     0.0F,
                     ArgbColor.multiplyAlpha(
                        ArgbColor.withAlpha(ArgbColor.lerp(GuiVisuals.accentColor, -1, (0.7F)), 255), (0.45F) * brightness * alphaState[0]
                     )
                  );
                  glowQuadCount++;
                  boolean visible = EntityRaycast.hasLineOfSight(minecraftClient.player, projectile);

                  try (BuiltBuffer glowBuffer = this.glowBuilder.end()) {
                     this.drawQuads(matrices, ("textures/particles/glow.png"), glowBuffer, glowQuadCount, visible, false);
                  }

                  if (lightningQuadCount > 0) {
                     try (BuiltBuffer lightningBuffer = this.lightningBuilder.end()) {
                        this.drawQuads(matrices, null, lightningBuffer, lightningQuadCount, visible, false);
                     }
                  }
               }
            }
         }
      }
   }

   public record TargetScanState(int primaryColor, int secondaryColor, float phase, float alpha, float hit, float glow) {
   }
}
