package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.EntityUpdatePacketAccessor;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.HudStyle;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.util.time.Stopwatch;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Generated;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

@ModuleInfo(name = "BackTrack", description = "Замораживает цель на прошлой позиции, реальную показывает боксом.", category = ModuleCategory.COMBAT)
public final class BackTrack extends Module {
   private static BackTrack INSTANCE;
   public DecimalSetting delay;
   public DecimalSetting maxSeparation;
   public BooleanSetting showBox;
   private volatile int trackedEntityId = -1;
   private volatile Vec3d serverPosition;
   private final CopyOnWriteArrayList<BackTrack.QueuedEntityPacket> delayedPackets = new CopyOnWriteArrayList<>();
   private final Stopwatch lastTargetHit = new Stopwatch();
   private Box renderedBox;
   private float boxAlpha;
   private long lastBoxFrameMillis;
   private final EventListener<PacketEvent> packetListener = event -> {
      if (!event.isOutgoing()) {
         if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            this.trackedEntityId = -1;
            this.serverPosition = null;
         } else if (this.trackedEntityId != -1) {
            Packet packet = event.getPacket();
            if (packet instanceof EntityS2CPacket relativePositionPacket && ((EntityUpdatePacketAccessor)(Object)relativePositionPacket).entityId() == this.trackedEntityId) {
               if (relativePositionPacket.isPositionChanged() && this.serverPosition != null) {
                  this.serverPosition = this.serverPosition.add(relativePositionPacket.getDeltaX() / (4096.0D), relativePositionPacket.getDeltaY() / (4096.0D), relativePositionPacket.getDeltaZ() / (4096.0D));
               }
            } else if (packet instanceof EntityPositionSyncS2CPacket syncPositionPacket && syncPositionPacket.id() == this.trackedEntityId) {
               this.serverPosition = syncPositionPacket.values().position();
            } else if (packet instanceof EntityPositionS2CPacket positionPacket && positionPacket.entityId() == this.trackedEntityId) {
               if (positionPacket.relatives().isEmpty()) {
                  this.serverPosition = positionPacket.change().position();
               }
            } else if (!(packet instanceof EntityVelocityUpdateS2CPacket velocityPacket) || velocityPacket.getEntityId() != this.trackedEntityId) {
               return;
            }

            this.delayedPackets.add(new BackTrack.QueuedEntityPacket(packet, System.currentTimeMillis()));
            event.markCancelled();
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      Entity entity = this.trackedEntityId != -1 && minecraftClient.world != null ? minecraftClient.world.getEntityById(this.trackedEntityId) : null;
      boolean serverPositionCloser = entity != null
         && this.serverPosition != null
         && minecraftClient.player != null
         && this.serverPosition.squaredDistanceTo(minecraftClient.player.getEntityPos()) < entity.getEntityPos().squaredDistanceTo(minecraftClient.player.getEntityPos());
      this.releaseDuePackets(entity != null && !serverPositionCloser ? (long)this.delay.getValue() : 0L);
      if (this.trackedEntityId != -1) {
         if (entity == null
            || !entity.isAlive()
            || minecraftClient.player == null
            || this.lastTargetHit.hasElapsed((1500L))
            || this.serverPosition != null && this.serverPosition.distanceTo(entity.getEntityPos()) > this.maxSeparation.getValue()) {
            this.clearTrackedTarget();
         }
      }
   };
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && this.showBox.getValue()) {
         float deltaFrames = Math.max(1.0F, Math.min((float)(System.currentTimeMillis() - this.lastBoxFrameMillis), MAX_RENDER_DELTA_MILLIS)) / (16.666666F);
         this.lastBoxFrameMillis = System.currentTimeMillis();
         Entity entity = this.trackedEntityId == -1 ? null : minecraftClient.world.getEntityById(this.trackedEntityId);
         Vec3d serverPosition = this.serverPosition;
         Box targetBox = entity != null && serverPosition != null && !(serverPosition.distanceTo(entity.getEntityPos()) < (0.3D))
            ? entity.getBoundingBox().offset(serverPosition.subtract(entity.getEntityPos()))
            : null;
         if (targetBox != null) {
            this.renderedBox = this.renderedBox != null && !(this.boxAlpha < (0.003921569F))
               ? new Box(
                  MathHelper.lerp(Math.min(1.0F, (0.45F) * deltaFrames), this.renderedBox.minX, targetBox.minX),
                  MathHelper.lerp(Math.min(1.0F, (0.45F) * deltaFrames), this.renderedBox.minY, targetBox.minY),
                  MathHelper.lerp(Math.min(1.0F, (0.45F) * deltaFrames), this.renderedBox.minZ, targetBox.minZ),
                  MathHelper.lerp(Math.min(1.0F, BOX_INTERPOLATION_RATE * deltaFrames), this.renderedBox.maxX, targetBox.maxX),
                  MathHelper.lerp(Math.min(1.0F, (0.45F) * deltaFrames), this.renderedBox.maxY, targetBox.maxY),
                  MathHelper.lerp(Math.min(1.0F, (0.45F) * deltaFrames), this.renderedBox.maxZ, targetBox.maxZ)
               )
               : targetBox;
         }

         this.boxAlpha = MathHelper.clamp(this.boxAlpha + (targetBox == null ? (-0.25F) : (0.25F)) * deltaFrames, 0.0F, 1.0F);
         if (this.renderedBox != null && !(this.boxAlpha < (0.003921569F))) {
            event.getMatrices().push();
            event.getMatrices()
               .translate(
                  this.renderedBox.minX - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
                  this.renderedBox.minY - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
                  this.renderedBox.minZ - minecraftClient.gameRenderer.getCamera().getCameraPos().z
               );
            Matrix4f matrix = event.getMatrices().peek().getPositionMatrix();
            VertexConsumer vertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.debugQuads());
            float[] xOffsets = new float[]{0.0F, (float)(this.renderedBox.maxX - this.renderedBox.minX)};
            float[] yOffsets = new float[]{0.0F, (float)(this.renderedBox.maxY - this.renderedBox.minY)};
            float[] zOffsets = new float[]{0.0F, (float)(this.renderedBox.maxZ - this.renderedBox.minZ)};
            int[][] faceIndices = new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

            for (int[] face : faceIndices) {
               for (int cornerIndex : face) {
                  vertices.vertex(matrix, xOffsets[cornerIndex >> 2], yOffsets[cornerIndex >> 1 & 1], zOffsets[cornerIndex & 1])
                     .color(ArgbColor.multiplyAlpha(HudStyle.accentColor, (0.14F) * this.boxAlpha));
               }
            }

            VertexRendering.drawOutline(
               event.getMatrices(),
               minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines()),
               VoxelShapes.cuboid(new Box(0.0, 0.0, 0.0, xOffsets[1], yOffsets[1], zOffsets[1])),
               0.0,
               0.0,
               0.0,
               ArgbColor.multiplyAlpha(HudStyle.accentColor, (0.85F) * this.boxAlpha),
               2.0F
            );
            minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
            event.getMatrices().pop();
         }
      } else {
         this.renderedBox = null;
         this.boxAlpha = 0.0F;
      }
   };
   private static final String DELAY_LABEL = "Задержка";

   private static final double DELAY_STEP = 10.0;

   private static final float MAX_RENDER_DELTA_MILLIS = 100.0F;

   private static final float BOX_INTERPOLATION_RATE = 0.45F;

   private void releaseDuePackets(long delayMillis) {
      for (BackTrack.QueuedEntityPacket queuedPacket : this.delayedPackets) {
         if (System.currentTimeMillis() - queuedPacket.receivedAtMillis < delayMillis) {
            break;
         }

         if (minecraftClient.getNetworkHandler() != null) {
            ((Packet<ClientPlayNetworkHandler>)queuedPacket.packet).apply(minecraftClient.getNetworkHandler());
         }

         this.delayedPackets.remove(queuedPacket);
      }
   }

   @Override
   protected void onDisable() {
      this.clearTrackedTarget();
      this.renderedBox = null;
      this.boxAlpha = 0.0F;
   }

   private void clearTrackedTarget() {
      this.releaseDuePackets(0L);
      this.trackedEntityId = -1;
      this.serverPosition = null;
   }

   private BackTrack() {
      this.delay = this.addSetting(new DecimalSetting(DELAY_LABEL, "", (10.0D), (10.0D), (600.0D), DELAY_STEP));
      this.maxSeparation = this.addSetting(new DecimalSetting(("Максимальный отрыв"), "", (3.7D), (0.5D), (6.0D), (0.1D)));
      this.showBox = this.addSetting(new BooleanSetting(("Показывать бокс"), ("Рисует реальную позицию цели боксом."), true));
   }

   public void trackAttackedEntity(Entity entity) {
      if (this.isEnabled() && entity instanceof LivingEntity && entity != minecraftClient.player) {
         if (entity.getId() != this.trackedEntityId) {
            this.clearTrackedTarget();
            this.trackedEntityId = entity.getId();
            this.serverPosition = entity.getTrackedPosition().getPos();
         }

         this.lastTargetHit.reset();
      }
   }

   public static BackTrack getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new BackTrack();
      }

      return INSTANCE;
   }

   private static final class QueuedEntityPacket {
      private final Packet<?> packet;
      private final long receivedAtMillis;

      @Generated
      public QueuedEntityPacket(Packet<?> packet, long receivedAtMillis) {
         this.packet = packet;
         this.receivedAtMillis = receivedAtMillis;
      }
   }
}
