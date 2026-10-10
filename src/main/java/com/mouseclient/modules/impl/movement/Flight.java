package com.mouseclient.modules.impl.movement;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.ClientInteractionManagerInvoker;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.HudStyle;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.render.pipeline.SliderSetting;
import com.mouseclient.util.entity.PlayerMovementSimulation;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.Stopwatch;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

@ModuleInfo(name = "Flight", description = "Полёт обычным движением с постоянной скоростью.", category = ModuleCategory.MOVEMENT)
public final class Flight extends Module {
   private static Flight INSTANCE;
   public ModeSetting mode;
   public SliderSetting speed;
   private final HashMap<BlockPos, Long> placedBlockTimes = new HashMap<>();
   private final Stopwatch placementTimer = new Stopwatch();
   private int previousHotbarSlot = -1;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.interactionManager != null) {
         if (this.mode.isSelected(("Обычный"))) {
            Vec3d movement = Vec3d.ZERO;
            if (minecraftClient.options.forwardKey.isPressed()) {
               movement = movement.add(Vec3d.fromPolar(0.0F, minecraftClient.player.getYaw()));
            }

            if (minecraftClient.options.backKey.isPressed()) {
               movement = movement.subtract(Vec3d.fromPolar(0.0F, minecraftClient.player.getYaw()));
            }

            if (minecraftClient.options.rightKey.isPressed()) {
               movement = movement.add(Vec3d.fromPolar(0.0F, minecraftClient.player.getYaw() + (90.0F)));
            }

            if (minecraftClient.options.leftKey.isPressed()) {
               movement = movement.subtract(Vec3d.fromPolar(0.0F, minecraftClient.player.getYaw() + (90.0F)));
            }

            if (minecraftClient.options.jumpKey.isPressed()) {
               movement = movement.add(0.0, 1.0, 0.0);
            }

            if (minecraftClient.options.sneakKey.isPressed()) {
               movement = movement.subtract(0.0, 1.0, 0.0);
            }

            minecraftClient.player.setVelocity(movement.lengthSquared() > 0.0 ? movement.normalize().multiply(this.speed.floatValue()) : Vec3d.ZERO);
         }
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null) {
         if (event.getPacket() instanceof OpenScreenS2CPacket screenPacket && this.mode.isSelected(("FunTime")) && screenPacket.getName().getString().contains(("Блок дамагер"))) {
            minecraftClient.execute(
               () -> {
                  if (minecraftClient.player != null) {
                     minecraftClient.player
                        .networkHandler
                        .sendPacket(
                           new ClickSlotC2SPacket(
                              screenPacket.getSyncId(), 0, (short)14, (byte)0, SlotActionType.QUICK_MOVE, Int2ObjectMaps.emptyMap(), ItemStackHash.EMPTY
                           )
                        );
                     minecraftClient.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(screenPacket.getSyncId()));
                     this.placementTimer.delayStart((-5000L));
                  }
               }
            );
            event.markCancelled();
         }
      }
   };
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && !this.placedBlockTimes.isEmpty()) {
         this.placedBlockTimes
            .entrySet()
            .removeIf(blockTime -> System.currentTimeMillis() - blockTime.getValue() > (200L) || minecraftClient.world.getBlockState(blockTime.getKey()).isAir());

         for (Entry blockEntry : this.placedBlockTimes.entrySet()) {
            float visibility = 1.0F - MathHelper.clamp((float)(System.currentTimeMillis() - (Long)blockEntry.getValue()) / (200.0F), 0.0F, 1.0F);
            Box bounds = new Box((BlockPos)blockEntry.getKey());
            event.getMatrices().push();
            event.getMatrices()
               .translate(
                  bounds.minX - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
                  bounds.minY - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
                  bounds.minZ - minecraftClient.gameRenderer.getCamera().getCameraPos().z
               );
            Matrix4f positionMatrix = event.getMatrices().peek().getPositionMatrix();
            VertexConsumer vertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.debugQuads());
            float[] xCoordinates = new float[]{0.0F, 1.0F};
            float[] yCoordinates = new float[]{0.0F, 1.0F};
            float[] zCoordinates = new float[]{0.0F, 1.0F};
            int[][] faceVertices = new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

            for (int[] face : faceVertices) {
               for (int vertexIndex : face) {
                  vertices.vertex(positionMatrix, xCoordinates[vertexIndex >> 2], yCoordinates[vertexIndex >> 1 & 1], zCoordinates[vertexIndex & 1])
                     .color(ArgbColor.multiplyAlpha(HudStyle.accentColor, (0.16F) * visibility));
               }
            }

            VertexRendering.drawOutline(
               event.getMatrices(),
               minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines()),
               VoxelShapes.cuboid(new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
               0.0,
               0.0,
               0.0,
               ArgbColor.multiplyAlpha(HudStyle.accentColor, visibility),
               (1.5F)
            );
            minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
            event.getMatrices().pop();
         }
      }
   };

   private Flight() {
      this.mode = this.addSetting(new ModeSetting(("Режим"), ("Чем держаться в воздухе."), ("Обычный"), ("Обычный")));
      this.speed = this.addSetting(
         new SliderSetting(("Скорость"), ("Блоков за тик полёта."))
            .withRange((0.1F), (3.0F))
            .withStep((0.1F))
            .setValue(1.0F)
            .setVisibility(() -> this.mode.isSelected(("Обычный")))
      );
   }

   @Override
   protected void onDisable() {
      this.placedBlockTimes.clear();
      if (this.previousHotbarSlot != -1) {
         InventoryActions.selectHotbarSlot(this.previousHotbarSlot, true);
         this.previousHotbarSlot = -1;
      }
   }

   public boolean isNonInteractiveSupportBlock(BlockState blockState) {
      return !blockState.isReplaceable()
         && !blockState.onUse(
               minecraftClient.world, minecraftClient.player, new BlockHitResult(BlockPos.ORIGIN.toCenterPos(), Direction.SOUTH, BlockPos.ORIGIN, false)
            )
            .isAccepted();
   }

   public BlockHitResult placementHit(BlockPos position, Direction direction) {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         BlockPos supportPosition = position.offset(direction).toImmutable();
         Vec3d eyePosition = minecraftClient.player.getEyePos();
         Box supportBounds = new Box(supportPosition).contract((0.02D));
         Direction hitFace = direction.getOpposite();
         Vec3d facePoint = new Vec3d(
            hitFace.getOffsetX() == 0 ? MathHelper.clamp(eyePosition.x, supportBounds.minX, supportBounds.maxX) : (hitFace.getOffsetX() > 0 ? supportBounds.maxX : supportBounds.minX),
            hitFace.getOffsetY() == 0 ? MathHelper.clamp(eyePosition.y, supportBounds.minY, supportBounds.maxY) : (hitFace.getOffsetY() > 0 ? supportBounds.maxY : supportBounds.minY),
            hitFace.getOffsetZ() == 0 ? MathHelper.clamp(eyePosition.z, supportBounds.minZ, supportBounds.maxZ) : (hitFace.getOffsetZ() > 0 ? supportBounds.maxZ : supportBounds.minZ)
         );
         BlockHitResult hitResult = Box.raycast(List.of(Box.from(Vec3d.ZERO)), eyePosition, facePoint, supportPosition);
         return hitResult != null && direction.equals(hitResult.getSide().getOpposite()) && eyePosition.distanceTo(facePoint) < (4.45D)
            ? new BlockHitResult(hitResult.getPos().add(Vec3d.of(direction.getVector()).multiply((1.0E-12D))), hitResult.getSide(), hitResult.getBlockPos(), hitResult.isInsideBlock())
            : null;
      } else {
         return null;
      }
   }

   private void tryPlaceJigsaw(BlockPos position, BlockState blockState) {
      if (this.previousHotbarSlot != -1) {
         InventoryActions.selectHotbarSlot(this.previousHotbarSlot, true);
         this.previousHotbarSlot = -1;
      } else {
         Box placementBounds = new Box(position).contract((0.01D));
         if (blockState.getOutlineShape(minecraftClient.world, BlockPos.ORIGIN).isEmpty() && this.isAreaFreeOfEntities(placementBounds)) {
            if (!InventorySwapQueue.getInstance().hasPendingSwaps() && !minecraftClient.player.getBoundingBox().contains(position)) {
               if (BlockPos.ofFloored(PlayerMovementSimulation.predictLocalPlayer(2).position).getY() <= position.getY() && this.placementTimer.hasElapsed((2000L))) {
                  int jigsawSlot = InventoryActions.findHotbarItem(Items.JIGSAW);
                  if (jigsawSlot != -1) {
                     this.placementTimer.reset();
                     this.previousHotbarSlot = InventoryActions.selectedHotbarSlot();
                     RotationController.getInstance()
                        .requestRotation(
                           new RotationAngles(minecraftClient.player.getYaw(), (90.0F)),
                           new RotationLimits((180.0F), (180.0F), (20.0F), (20.0F)),
                           RotationController.ReturnMode.SMOOTH
                        );
                     InventoryActions.selectHotbarSlot(jigsawSlot, true);
                     ((ClientInteractionManagerInvoker)(Object)minecraftClient.interactionManager)
                        .invokeStartPrediction(
                           minecraftClient.world,
                           placeSequence -> new PlayerInteractBlockC2SPacket(
                              Hand.MAIN_HAND, new BlockHitResult(position.toCenterPos().add(0.0, (0.5D), 0.0), Direction.UP, position, false), placeSequence
                           )
                        );
                     this.placedBlockTimes.put(position, System.currentTimeMillis());
                     minecraftClient.world.setBlockState(position, Blocks.JIGSAW.getDefaultState());
                     ((ClientInteractionManagerInvoker)(Object)minecraftClient.interactionManager)
                        .invokeStartPrediction(
                           minecraftClient.world,
                           interactSequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, new BlockHitResult(placementBounds.getCenter(), Direction.UP, position, false), interactSequence)
                        );
                  }
               }
            }
         }
      }
   }

   private boolean isAreaFreeOfEntities(Box bounds) {
      if (minecraftClient.player.getBoundingBox().intersects(bounds)) {
         return false;
      }

      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity != minecraftClient.player && entity.getBoundingBox().intersects(bounds)) {
            return false;
         }
      }

      return true;
   }

   @Override
   protected void onEnable() {
      this.placedBlockTimes.clear();
   }

   public static Flight getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Flight();
      }

      return INSTANCE;
   }
}
