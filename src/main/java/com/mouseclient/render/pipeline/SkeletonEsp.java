package com.mouseclient.render.pipeline;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.ColorSetting;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.util.time.GuiVisuals;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.BufferAllocator.CloseableBuffer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@ModuleInfo(name = "SkeletonEsp", description = "Рисует скелет игроков по позе их модели сквозь стены.", category = ModuleCategory.RENDER)
public final class SkeletonEsp extends Module {
   private static SkeletonEsp INSTANCE;
   private static final Vector4f WHITE_COLOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
   private static final Vector3f ZERO_MODEL_OFFSET = new Vector3f();
   private static final Matrix4f IDENTITY_TEXTURE_MATRIX = new Matrix4f();
   private static final Matrix4f IDENTITY_MODEL_MATRIX = new Matrix4f();
   private static final RenderPipeline SKELETON_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/skeleton_bones")))
      .withVertexShader(Identifier.ofVanilla(("core/position_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_color")))
      .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   public BooleanSetting useCustomColor;
   public ColorSetting color;
   public ColorSetting friendColor;
   private GpuBuffer vertexBuffer;
   private BufferAllocator allocator;
   private final ArrayList<AbstractClientPlayerEntity> playersToRender = new ArrayList<>();
   private final Vector3f segmentStart = new Vector3f();
   private final Vector3f segmentEnd = new Vector3f();
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         java.util.ArrayList<net.minecraft.client.network.AbstractClientPlayerEntity> players = this.playersToRender;
         players.clear();

         for (Entity entity : minecraftClient.world.getEntities()) {
            if (entity instanceof AbstractClientPlayerEntity player
               && (minecraftClient.options.getPerspective() != Perspective.FIRST_PERSON || player != minecraftClient.player)) {
               players.add(player);
            }
         }

         if (!players.isEmpty()) {
            if (this.allocator == null) {
               this.allocator = new BufferAllocator(520 * VertexFormats.POSITION_COLOR.getVertexSize());
            }

            CloseableBuffer previousBuffer = this.allocator.getAllocated();
            if (previousBuffer != null) {
               previousBuffer.close();
            }

            BufferBuilder builder = new BufferBuilder(this.allocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            int quadCount = 0;

            for (AbstractClientPlayerEntity targetPlayer : players) {
               quadCount += this.emitPlayerSkeleton(
                  event.getMatrices(),
                  builder,
                  targetPlayer,
                  event.getTickDelta(),
                  FriendManager.contains(targetPlayer.getGameProfile().name())
                     ? this.friendColor.getArgb()
                     : (this.useCustomColor.getValue() ? this.color.getArgb() : GuiVisuals.accentColor)
               );
            }

            this.drawBones(builder, quadCount);
            players.clear();
         }
      }
   };

   @Override
   protected void onDisable() {
      if (this.vertexBuffer != null && !this.vertexBuffer.isClosed()) {
         this.vertexBuffer.close();
      }

      this.vertexBuffer = null;
      if (this.allocator != null) {
         this.allocator.close();
         this.allocator = null;
      }

      this.playersToRender.clear();
   }

   public static SkeletonEsp getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new SkeletonEsp();
      }

      return INSTANCE;
   }

   private int emitModelBone(MatrixStack matrices, BufferBuilder builder, ModelPart part, float x, float y, float z, float length, int color) {
      matrices.push();
      matrices.translate(x, y, z);
      if (part.roll != 0.0F) {
         matrices.multiply(RotationAxis.POSITIVE_Z.rotation(part.roll));
      }

      if (part.yaw != 0.0F) {
         matrices.multiply(RotationAxis.NEGATIVE_Y.rotation(part.yaw));
      }

      if (part.pitch != 0.0F) {
         matrices.multiply(RotationAxis.NEGATIVE_X.rotation(part.pitch));
      }

      int quadCount = this.emitSegment(builder, matrices.peek().getPositionMatrix(), 0.0F, 0.0F, 0.0F, 0.0F, length, 0.0F, color);
      matrices.pop();
      return quadCount;
   }

   private int emitSegment(BufferBuilder builder, Matrix4f transform, float startX, float startY, float startZ, float endX, float endY, float endZ, int color) {
      transform.transformPosition(this.segmentStart.set(startX, startY, startZ));
      transform.transformPosition(this.segmentEnd.set(endX, endY, endZ));
      float deltaX = this.segmentEnd.x - this.segmentStart.x;
      float deltaY = this.segmentEnd.y - this.segmentStart.y;
      float deltaZ = this.segmentEnd.z - this.segmentStart.z;
      float midX = (this.segmentStart.x + this.segmentEnd.x) * (0.5F);
      float midY = (this.segmentStart.y + this.segmentEnd.y) * (0.5F);
      float midZ = (this.segmentStart.z + this.segmentEnd.z) * (0.5F);
      float sideX = deltaY * midZ - deltaZ * midY;
      float sideY = deltaZ * midX - deltaX * midZ;
      float sideZ = deltaX * midY - deltaY * midX;
      float sideLength = MathHelper.sqrt(sideX * sideX + sideY * sideY + sideZ * sideZ);
      if (sideLength < (1.0E-6F)) {
         return 0;
      }

      float cameraDistance = MathHelper.sqrt(midX * midX + midY * midY + midZ * midZ);
      float widthScale = Math.max((0.008F), cameraDistance * (0.0022F) * MathHelper.clamp((1.35F) - cameraDistance / (80.0F), (0.5F), 1.0F)) / sideLength;
      this.emitSegmentQuad(builder, sideX, sideY, sideZ, widthScale * (1.9F), color & (16777215) | (int)((color >>> 24) * (0.3F)) << 24);
      this.emitSegmentQuad(builder, sideX, sideY, sideZ, widthScale, color);
      return 2;
   }

   private void drawBones(BufferBuilder builder, int quadCount) {
      BuiltBuffer meshData = builder.endNullable();
      if (meshData != null) {
         try (meshData) {
            if (quadCount != 0 && minecraftClient.getFramebuffer() != null && minecraftClient.getFramebuffer().getColorAttachmentView() != null) {
               if (this.vertexBuffer != null && !this.vertexBuffer.isClosed() && this.vertexBuffer.size() >= meshData.getBuffer().remaining()) {
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0L, meshData.getBuffer().remaining()), meshData.getBuffer());
               } else {
                  if (this.vertexBuffer != null && !this.vertexBuffer.isClosed()) {
                     this.vertexBuffer.close();
                  }

                  this.vertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_skeleton_bones"), 40, meshData.getBuffer());
               }

               GpuBufferSlice bufferSlice = RenderSystem.getDynamicUniforms().write(IDENTITY_MODEL_MATRIX, WHITE_COLOR, ZERO_MODEL_OFFSET, IDENTITY_TEXTURE_MATRIX);

               try (RenderPass renderPass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(
                        () -> ("mouseclient_skeleton"), minecraftClient.getFramebuffer().getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty()
                     )) {
                  renderPass.setPipeline(SKELETON_PIPELINE);
                  renderPass.setUniform(("DynamicTransforms"), bufferSlice);
                  renderPass.setUniform(("Projection"), RenderSystem.getProjectionMatrixBuffer());
                  renderPass.setVertexBuffer(0, this.vertexBuffer);
                  renderPass.setIndexBuffer(
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(quadCount * 6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                  );
                  renderPass.drawIndexed(0, 0, quadCount * 6, 1);
                  return;
               }
            }
         }
      }
   }

   private SkeletonEsp() {
      this.useCustomColor = this.addSetting(new BooleanSetting(("Свой цвет"), ("Красить кости своим цветом вместо ACCENT."), false));
      this.color = this.addSetting(new ColorSetting(("Цвет"), ("Цвет костей скелета.")));
      this.friendColor = this.addSetting(new ColorSetting(("Цвет друзей"), ("Цвет костей друзей."), new Color(64, 255, 128, 255)));
      this.color.setVisibility(() -> this.useCustomColor.getValue());
   }

   private void emitSegmentQuad(BufferBuilder builder, float sideX, float sideY, float sideZ, float widthScale, int color) {
      builder.vertex(this.segmentStart.x - sideX * widthScale, this.segmentStart.y - sideY * widthScale, this.segmentStart.z - sideZ * widthScale).color(color);
      builder.vertex(this.segmentStart.x + sideX * widthScale, this.segmentStart.y + sideY * widthScale, this.segmentStart.z + sideZ * widthScale).color(color);
      builder.vertex(this.segmentEnd.x + sideX * widthScale, this.segmentEnd.y + sideY * widthScale, this.segmentEnd.z + sideZ * widthScale).color(color);
      builder.vertex(this.segmentEnd.x - sideX * widthScale, this.segmentEnd.y - sideY * widthScale, this.segmentEnd.z - sideZ * widthScale).color(color);
   }

   private int emitPlayerSkeleton(MatrixStack matrices, BufferBuilder builder, AbstractClientPlayerEntity player, float tickDelta, int color) {
      PlayerEntityRenderer renderer = (PlayerEntityRenderer)minecraftClient.getEntityRenderDispatcher().<AbstractClientPlayerEntity>getRenderer(player);
      PlayerEntityRenderState state = renderer.createRenderState();
      renderer.updateRenderState(player, state, tickDelta);
      PlayerEntityModel model = (PlayerEntityModel)renderer.getModel();
      model.setAngles(state);
      boolean swimming = player.isInSwimmingPose();
      boolean gliding = player.isGliding();
      boolean crouching = player.isSneaking() && !player.getAbilities().flying;
      float hipY = crouching ? (0.6F) : (0.7F);
      float hipZ = crouching ? (0.23F) : 0.0F;
      float neckY = crouching ? (1.05F) : (1.4F);
      float shoulderY = crouching ? (1.05F) : (1.35F);
      matrices.push();
      Vec3d playerPosition = player.getLerpedPos(tickDelta);
      Vec3d cameraPosition = minecraftClient.gameRenderer.getCamera().getCameraPos();
      matrices.translate(playerPosition.x - cameraPosition.x, playerPosition.y - cameraPosition.y, playerPosition.z - cameraPosition.z);
      if (swimming) {
         matrices.translate(0.0F, (0.35F), 0.0F);
      }

      matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, player.lastBodyYaw, player.bodyYaw) + (180.0F)));
      if (swimming || gliding) {
         matrices.multiply(RotationAxis.NEGATIVE_X.rotationDegrees((90.0F) + player.getPitch(tickDelta)));
      }

      if (swimming) {
         matrices.translate(0.0F, (-0.95F), 0.0F);
      }

      int quadCount = 0;
      quadCount += this.emitSegment(builder, matrices.peek().getPositionMatrix(), 0.0F, hipY, hipZ, 0.0F, neckY, 0.0F, color);
      quadCount += this.emitSegment(builder, matrices.peek().getPositionMatrix(), (-0.37F), shoulderY, 0.0F, (0.37F), shoulderY, 0.0F, color);
      quadCount += this.emitSegment(builder, matrices.peek().getPositionMatrix(), (-0.15F), hipY, hipZ, (0.15F), hipY, hipZ, color);
      quadCount += this.emitModelBone(matrices, builder, model.head, 0.0F, neckY, 0.0F, (0.15F), color);
      quadCount += this.emitModelBone(matrices, builder, model.rightLeg, (0.15F), hipY, hipZ, (-0.6F), color);
      quadCount += this.emitModelBone(matrices, builder, model.leftLeg, (-0.15F), hipY, hipZ, (-0.6F), color);
      quadCount += this.emitModelBone(matrices, builder, model.rightArm, (0.37F), shoulderY, 0.0F, (-0.55F), color);
      quadCount += this.emitModelBone(matrices, builder, model.leftArm, (-0.37F), shoulderY, 0.0F, (-0.55F), color);
      matrices.pop();
      return quadCount;
   }
}
