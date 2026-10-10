package com.mouseclient.render.pipeline;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.ModuleRegistry;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.AutoTotem;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.network.HudStyle;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

@ModuleInfo(name = "TotemGhost", description = "Призрак модельки на месте лопнувшего тотема.", category = ModuleCategory.RENDER)
public final class TotemGhost extends Module {
   private static TotemGhost INSTANCE;
   private static final RenderPipeline FILL_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/totem_ghost_fill")))
      .withVertexShader(Identifier.ofVanilla(("core/position_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_color")))
      .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   private static final RenderPipeline OUTLINE_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/totem_ghost_lines")))
      .withVertexShader(Identifier.ofVanilla(("core/position_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_color")))
      .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/totem_ghost_glow")))
      .withVertexShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .build();
   private static final RenderLayer FILL_LAYER = RenderLayer.of(("totem_ghost_fill"), RenderSetup.builder(FILL_PIPELINE).expectedBufferSize(16384).build());
   private static final RenderLayer OUTLINE_LAYER = RenderLayer.of(("totem_ghost_lines"), RenderSetup.builder(OUTLINE_PIPELINE).expectedBufferSize(4096).build());
   private static final RenderLayer GLOW_LAYER = RenderLayer.of(
      ("totem_ghost_glow"),
      RenderSetup.builder(GLOW_PIPELINE).texture(("Sampler0"), ClientInitializer.id(("textures/particles/glow.png"))).expectedBufferSize(1024).build()
   );
   private static final float[][] PART_BOUNDS = new float[][]{
      {(-0.25F), (1.5F), (-0.25F), (0.25F), 2.0F, (0.25F)},
      {
            (-0.25F),
            (0.75F),
            (-0.125F),
            (0.25F),
            (1.5F),
            (0.125F)
      },
      {
            (0.25F),
            (0.75F),
            (-0.125F),
            (0.5F),
            (1.5F),
            (0.125F)
      },
      {
            (-0.5F),
            (0.75F),
            (-0.125F),
            (-0.25F),
            (1.5F),
            (0.125F)
      },
      {0.0F, 0.0F, (-0.125F), (0.25F), (0.75F), (0.125F)},
      {(-0.25F), 0.0F, (-0.125F), 0.0F, (0.75F), (0.125F)}
   };
   private static final float[][] PART_PIVOTS = new float[][]{
      {0.0F, (1.5F), 0.0F},
      {0.0F, (0.75F), 0.0F},
      {(0.375F), (1.4F), 0.0F},
      {(-0.375F), (1.4F), 0.0F},
      {(0.125F), (0.75F), 0.0F},
      {(-0.125F), (0.75F), 0.0F}
   };
   private static final int[][] BOX_FACES = new int[][]{{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
   private static final int[][] BOX_EDGES = new int[][]{{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
   private final ArrayList<TotemGhost.GhostPose> ghosts = new ArrayList<>();
   private BufferAllocator allocator;
   private Immediate vertices;
   private final EventListener<PacketEvent> packetListener;
   private final EventListener<WorldRenderEvent> worldRenderListener;

   private void emitGlowQuad(VertexConsumer vertices, Matrix4f transform, int color, float halfSize) {
      vertices.vertex(transform, -halfSize, -halfSize, 0.0F).texture(0.0F, 1.0F).color(color);
      vertices.vertex(transform, halfSize, -halfSize, 0.0F).texture(1.0F, 1.0F).color(color);
      vertices.vertex(transform, halfSize, halfSize, 0.0F).texture(1.0F, 0.0F).color(color);
      vertices.vertex(transform, -halfSize, halfSize, 0.0F).texture(0.0F, 0.0F).color(color);
   }

   private float boxCornerCoordinate(float[] bounds, int cornerIndex, int axisIndex) {
      return bounds[(cornerIndex >> axisIndex & 1) * 3 + axisIndex];
   }

   private int accentColor() {
      return HudStyle.accentColor;
   }

   public static TotemGhost getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new TotemGhost();
      }

      return INSTANCE;
   }

   private TotemGhost() {
      this.allocator = new BufferAllocator((131072));
      this.vertices = VertexConsumerProvider.immediate(this.allocator);
      this.packetListener = event -> {
         if (AutoTotem.getInstance().showTotemPop.getValue()
            && event.isIncoming()
            && event.getPacket() instanceof EntityStatusS2CPacket statusPacket
            && statusPacket.getStatus() == 35) {
            minecraftClient.execute(
               () -> {
                  if (minecraftClient.world != null
                     && statusPacket.getEntity(minecraftClient.world) != null
                     && statusPacket.getEntity(minecraftClient.world) != minecraftClient.player) {
                     TotemGhost.GhostPose ghostPose = new TotemGhost.GhostPose(
                        statusPacket.getEntity(minecraftClient.world).getEntityPos().x,
                        statusPacket.getEntity(minecraftClient.world).getEntityPos().y,
                        statusPacket.getEntity(minecraftClient.world).getEntityPos().z,
                        statusPacket.getEntity(minecraftClient.world).getYaw()
                     );
                     if (statusPacket.getEntity(minecraftClient.world) instanceof LivingEntity entity) {
                        ghostPose.bodyYaw = entity.bodyYaw;
                        ghostPose.relativeHeadYaw = MathHelper.clamp(MathHelper.wrapDegrees(entity.headYaw - entity.bodyYaw), (-75.0F), (75.0F));
                        ghostPose.partPitchDegrees[0] = entity.getPitch();
                        ghostPose.partPitchDegrees[2] = (float)Math.toDegrees(
                           MathHelper.cos(entity.limbAnimator.getAnimationProgress() * (0.6662F) + (3.1415927F))
                              * 2.0F
                              * Math.min(entity.limbAnimator.getSpeed(), 1.0F)
                              * (0.5F)
                        );
                        ghostPose.partPitchDegrees[3] = (float)Math.toDegrees(
                           MathHelper.cos(entity.limbAnimator.getAnimationProgress() * (0.6662F))
                              * 2.0F
                              * Math.min(entity.limbAnimator.getSpeed(), 1.0F)
                              * (0.5F)
                        );
                        ghostPose.partPitchDegrees[4] = (float)Math.toDegrees(
                           MathHelper.cos(entity.limbAnimator.getAnimationProgress() * (0.6662F))
                              * (1.4F)
                              * Math.min(entity.limbAnimator.getSpeed(), 1.0F)
                        );
                        ghostPose.partPitchDegrees[5] = (float)Math.toDegrees(
                           MathHelper.cos(entity.limbAnimator.getAnimationProgress() * (0.6662F) + (3.1415927F))
                              * (1.4F)
                              * Math.min(entity.limbAnimator.getSpeed(), 1.0F)
                        );
                     }

                     this.ghosts.add(ghostPose);
                  }
               }
            );
         }
      };
      this.worldRenderListener = renderEvent -> {
         if (AutoTotem.getInstance().showTotemPop.getValue()
            && minecraftClient.player != null
            && minecraftClient.world != null
            && minecraftClient.gameRenderer != null) {
            this.ghosts.removeIf(expiredGhost -> (float)(System.currentTimeMillis() - expiredGhost.spawnAtMillis) >= (2000.0F));
            if (!this.ghosts.isEmpty()) {
               for (TotemGhost.GhostPose animatedGhost : this.ghosts) {
                  animatedGhost.ageProgress = (float)(System.currentTimeMillis() - animatedGhost.spawnAtMillis) / (2000.0F);
                  animatedGhost.opacity = Math.min(animatedGhost.ageProgress * (8.0F), 1.0F)
                     * (animatedGhost.ageProgress > (0.55F) ? 1.0F - (animatedGhost.ageProgress - (0.55F)) / (0.45F) : 1.0F);
                  renderEvent.getMatrices().push();
                  renderEvent.getMatrices()
                     .translate(
                        animatedGhost.x - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
                        animatedGhost.y
                           - minecraftClient.gameRenderer.getCamera().getCameraPos().y
                           + (1.0F - (1.0F - animatedGhost.ageProgress) * (1.0F - animatedGhost.ageProgress) * (1.0F - animatedGhost.ageProgress)) * 2.0F,
                        animatedGhost.z - minecraftClient.gameRenderer.getCamera().getCameraPos().z
                     );
                  renderEvent.getMatrices().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-animatedGhost.bodyYaw + animatedGhost.ageProgress * (40.0F)));
                  renderEvent.getMatrices().push();
                  renderEvent.getMatrices().translate(0.0F, (2.16F), 0.0F);
                  renderEvent.getMatrices().multiply(RotationAxis.POSITIVE_X.rotationDegrees((11.0F)));
                  renderEvent.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees((-4.0F)));
                  animatedGhost.haloTransform.set(renderEvent.getMatrices().peek().getPositionMatrix());
                  renderEvent.getMatrices().pop();

                  for (int partIndex = 0; partIndex < PART_BOUNDS.length; partIndex++) {
                     if (animatedGhost.partPitchDegrees[partIndex] != 0.0F || partIndex == 0 && animatedGhost.relativeHeadYaw != 0.0F) {
                        renderEvent.getMatrices().push();
                        renderEvent.getMatrices().translate(PART_PIVOTS[partIndex][0], PART_PIVOTS[partIndex][1], PART_PIVOTS[partIndex][2]);
                        if (partIndex == 0 && animatedGhost.relativeHeadYaw != 0.0F) {
                           renderEvent.getMatrices().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-animatedGhost.relativeHeadYaw));
                        }

                        if (animatedGhost.partPitchDegrees[partIndex] != 0.0F) {
                           renderEvent.getMatrices().multiply(RotationAxis.POSITIVE_X.rotationDegrees(animatedGhost.partPitchDegrees[partIndex]));
                        }

                        renderEvent.getMatrices().translate(-PART_PIVOTS[partIndex][0], -PART_PIVOTS[partIndex][1], -PART_PIVOTS[partIndex][2]);
                        animatedGhost.partTransforms[partIndex].set(renderEvent.getMatrices().peek().getPositionMatrix());
                        renderEvent.getMatrices().pop();
                     } else {
                        animatedGhost.partTransforms[partIndex].set(renderEvent.getMatrices().peek().getPositionMatrix());
                     }
                  }

                  renderEvent.getMatrices().pop();
                  renderEvent.getMatrices().push();
                  renderEvent.getMatrices()
                     .translate(
                        animatedGhost.x - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
                        animatedGhost.y
                           - minecraftClient.gameRenderer.getCamera().getCameraPos().y
                           + (1.0F - (1.0F - animatedGhost.ageProgress) * (1.0F - animatedGhost.ageProgress) * (1.0F - animatedGhost.ageProgress)) * 2.0F
                           + (1.100000023841858D),
                        animatedGhost.z - minecraftClient.gameRenderer.getCamera().getCameraPos().z
                     );
                  renderEvent.getMatrices().multiply(minecraftClient.gameRenderer.getCamera().getRotation());
                  animatedGhost.glowTransform.set(renderEvent.getMatrices().peek().getPositionMatrix());
                  renderEvent.getMatrices().pop();
               }

               for (TotemGhost.GhostPose fillGhost : this.ghosts) {
                  if (!(fillGhost.opacity <= 0.0F)) {
                     for (int fillPartIndex = 0; fillPartIndex < PART_BOUNDS.length; fillPartIndex++) {
                        for (int[] faceCorners : BOX_FACES) {
                           for (int cornerIndex : faceCorners) {
                              this.vertices
                                 .getBuffer(FILL_LAYER)
                                 .vertex(
                                    fillGhost.partTransforms[fillPartIndex],
                                    this.boxCornerCoordinate(PART_BOUNDS[fillPartIndex], cornerIndex, 0),
                                    this.boxCornerCoordinate(PART_BOUNDS[fillPartIndex], cornerIndex, 1),
                                    this.boxCornerCoordinate(PART_BOUNDS[fillPartIndex], cornerIndex, 2)
                                 )
                                 .color(ArgbColor.multiplyAlpha(this.accentColor(), fillGhost.opacity * (0.25F)));
                           }
                        }
                     }
                  }
               }

               for (TotemGhost.GhostPose haloGhost : this.ghosts) {
                  this.emitAnnulus(
                     this.vertices.getBuffer(FILL_LAYER),
                     haloGhost.haloTransform,
                     ArgbColor.multiplyAlpha(this.accentColor(), haloGhost.opacity * (0.2F)),
                     0,
                     (0.3F),
                     (0.395F),
                     0.0F
                  );
                  this.emitAnnulus(
                     this.vertices.getBuffer(FILL_LAYER),
                     haloGhost.haloTransform,
                     0,
                     ArgbColor.multiplyAlpha(this.accentColor(), haloGhost.opacity * (0.2F)),
                     (0.405F),
                     (0.5F),
                     0.0F
                  );
               }

               for (TotemGhost.GhostPose rimGhost : this.ghosts) {
                  this.emitAnnulus(
                     this.vertices.getBuffer(FILL_LAYER),
                     rimGhost.haloTransform,
                     ArgbColor.multiplyAlpha(this.accentColor(), rimGhost.opacity * (0.92F)),
                     ArgbColor.multiplyAlpha(this.accentColor(), rimGhost.opacity * (0.92F)),
                     (0.39F),
                     (0.41F),
                     (0.006F)
                  );
                  this.emitAnnulus(
                     this.vertices.getBuffer(FILL_LAYER),
                     rimGhost.haloTransform,
                     ArgbColor.multiplyAlpha(this.accentColor(), rimGhost.opacity * (0.92F)),
                     ArgbColor.multiplyAlpha(this.accentColor(), rimGhost.opacity * (0.92F)),
                     (0.39F),
                     (0.41F),
                     (-0.006F)
                  );

                  for (int rimSegment = 0; rimSegment < 96; rimSegment++) {
                     this.vertices
                        .getBuffer(FILL_LAYER)
                        .vertex(
                           rimGhost.haloTransform,
                           MathHelper.cos((6.2831855F) * rimSegment / (96.0F)) * (0.41F),
                           (-0.006F),
                           MathHelper.sin((6.2831855F) * rimSegment / (96.0F)) * (0.41F)
                        )
                        .color(ArgbColor.multiplyAlpha(this.accentColor(), rimGhost.opacity * (0.92F)));
                     this.vertices
                        .getBuffer(FILL_LAYER)
                        .vertex(
                           rimGhost.haloTransform,
                           MathHelper.cos((6.2831855F) * (rimSegment + 1) / (96.0F)) * (0.41F),
                           (-0.006F),
                           MathHelper.sin((6.2831855F) * (rimSegment + 1) / (96.0F)) * (0.41F)
                        )
                        .color(ArgbColor.multiplyAlpha(this.accentColor(), rimGhost.opacity * (0.92F)));
                     this.vertices
                        .getBuffer(FILL_LAYER)
                        .vertex(
                           rimGhost.haloTransform,
                           MathHelper.cos((6.2831855F) * (rimSegment + 1) / (96.0F)) * (0.41F),
                           (0.006F),
                           MathHelper.sin((6.2831855F) * (rimSegment + 1) / (96.0F)) * (0.41F)
                        )
                        .color(ArgbColor.multiplyAlpha(this.accentColor(), rimGhost.opacity * (0.92F)));
                     this.vertices
                        .getBuffer(FILL_LAYER)
                        .vertex(
                           rimGhost.haloTransform,
                           MathHelper.cos((6.2831855F) * rimSegment / (96.0F)) * (0.41F),
                           (0.006F),
                           MathHelper.sin((6.2831855F) * rimSegment / (96.0F)) * (0.41F)
                        )
                        .color(ArgbColor.multiplyAlpha(this.accentColor(), rimGhost.opacity * (0.92F)));
                  }
               }

               for (TotemGhost.GhostPose outlineGhost : this.ghosts) {
                  if (!(outlineGhost.opacity <= 0.0F)) {
                     for (int outlinePartIndex = 0; outlinePartIndex < PART_BOUNDS.length; outlinePartIndex++) {
                        for (int[] edgeVertices : BOX_EDGES) {
                           this.vertices
                              .getBuffer(OUTLINE_LAYER)
                              .vertex(
                                 outlineGhost.partTransforms[outlinePartIndex],
                                 this.boxCornerCoordinate(PART_BOUNDS[outlinePartIndex], edgeVertices[0], 0),
                                 this.boxCornerCoordinate(PART_BOUNDS[outlinePartIndex], edgeVertices[0], 1),
                                 this.boxCornerCoordinate(PART_BOUNDS[outlinePartIndex], edgeVertices[0], 2)
                              )
                              .color(ArgbColor.multiplyAlpha(this.accentColor(), outlineGhost.opacity * (0.45F)));
                           this.vertices
                              .getBuffer(OUTLINE_LAYER)
                              .vertex(
                                 outlineGhost.partTransforms[outlinePartIndex],
                                 this.boxCornerCoordinate(PART_BOUNDS[outlinePartIndex], edgeVertices[1], 0),
                                 this.boxCornerCoordinate(PART_BOUNDS[outlinePartIndex], edgeVertices[1], 1),
                                 this.boxCornerCoordinate(PART_BOUNDS[outlinePartIndex], edgeVertices[1], 2)
                              )
                              .color(ArgbColor.multiplyAlpha(this.accentColor(), outlineGhost.opacity * (0.45F)));
                        }
                     }
                  }
               }

               for (TotemGhost.GhostPose glowGhost : this.ghosts) {
                  this.emitGlowQuad(
                     this.vertices.getBuffer(GLOW_LAYER),
                     glowGhost.glowTransform,
                     ArgbColor.multiplyAlpha(this.accentColor(), glowGhost.opacity * (0.43F)),
                     (1.5F)
                  );
                  this.emitGlowQuad(
                     this.vertices.getBuffer(GLOW_LAYER),
                     glowGhost.glowTransform,
                     ArgbColor.multiplyAlpha(this.accentColor(), glowGhost.opacity * (0.63F)),
                     (0.7F)
                  );
               }

               this.vertices.draw();
            }
         } else {
            this.ghosts.clear();
         }
      };
      ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> this.ghosts.clear());
      ModuleRegistry.unregister(this);
      this.setSubscribed(true);
   }

   @Override
   protected void onDisable() {
      this.ghosts.clear();
   }

   private void emitAnnulus(VertexConsumer vertices, Matrix4f transform, int innerColor, int outerColor, float innerRadius, float outerRadius, float y) {
      if (innerColor >>> 24 != 0 || outerColor >>> 24 != 0) {
         for (int segmentIndex = 0; segmentIndex < 96; segmentIndex++) {
            vertices.vertex(transform, MathHelper.cos((6.2831855F) * segmentIndex / (96.0F)) * innerRadius, y, MathHelper.sin((6.2831855F) * segmentIndex / (96.0F)) * innerRadius)
               .color(innerColor);
            vertices.vertex(transform, MathHelper.cos((6.2831855F) * segmentIndex / (96.0F)) * outerRadius, y, MathHelper.sin((6.2831855F) * segmentIndex / (96.0F)) * outerRadius)
               .color(outerColor);
            vertices.vertex(
                  transform, MathHelper.cos((6.2831855F) * (segmentIndex + 1) / (96.0F)) * outerRadius, y, MathHelper.sin((6.2831855F) * (segmentIndex + 1) / (96.0F)) * outerRadius
               )
               .color(outerColor);
            vertices.vertex(
                  transform, MathHelper.cos((6.2831855F) * (segmentIndex + 1) / (96.0F)) * innerRadius, y, MathHelper.sin((6.2831855F) * (segmentIndex + 1) / (96.0F)) * innerRadius
               )
               .color(innerColor);
         }
      }
   }

   private static final class GhostPose {
      private final double x;
      private final double y;
      private final double z;
      private final long spawnAtMillis;
      private float bodyYaw;
      private float relativeHeadYaw;
      private final float[] partPitchDegrees = new float[TotemGhost.PART_BOUNDS.length];
      private float ageProgress;
      private float opacity;
      private final Matrix4f[] partTransforms = new Matrix4f[TotemGhost.PART_BOUNDS.length];
      private final Matrix4f haloTransform = new Matrix4f();
      private final Matrix4f glowTransform = new Matrix4f();

      private GhostPose(double x, double y, double z, float bodyYaw) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.bodyYaw = bodyYaw;
         this.spawnAtMillis = System.currentTimeMillis();

         for (int partIndex = 0; partIndex < TotemGhost.PART_BOUNDS.length; partIndex++) {
            this.partTransforms[partIndex] = new Matrix4f();
         }
      }
   }
}
