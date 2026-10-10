package com.mouseclient.util.entity;

import com.mouseclient.internal.ClientMath;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.combat.MaceHelper;
import com.mouseclient.modules.impl.misc.YoutubeModeModule;
import com.mouseclient.network.RotationLimits;
import java.util.ArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class CombatTargeting implements MinecraftAccess {

   private CombatTargeting() {
   }

   public static boolean shouldIgnoreRaycastBlock(BlockState blockState, BlockPos blockPos) {
      if (minecraftClient.player == null) {
         return false;
      } else {
         BlockPos eyePos = BlockPos.ofFloored(minecraftClient.player.getEyePos());
         if (!eyePos.equals(blockPos) && (!minecraftClient.player.isInPose(EntityPose.SWIMMING) || !eyePos.up().equals(blockPos))) {
            Block block = blockState.getBlock();
            return block instanceof FenceBlock
               || block instanceof FenceGateBlock
               || block instanceof WallBlock
               || block instanceof PaneBlock
               || block instanceof SlabBlock
               || block instanceof StairsBlock
               || block instanceof TrapdoorBlock
               || block instanceof PointedDripstoneBlock;
         } else {
            return true;
         }
      }
   }

   public static float displayedHealth(LivingEntity entity) {
      if (minecraftClient.world != null) {
         for (ScoreboardObjective objective : minecraftClient.world.getScoreboard().getObjectives()) {
            ReadableScoreboardScore score = minecraftClient.world.getScoreboard().getScore(entity, objective);
            if (score != null) {
               return score.getScore();
            }
         }
      }

      return entity.getHealth() + entity.getAbsorptionAmount();
   }

   public static boolean overlapsPlayer(LivingEntity entity, float horizontalStretch) {
      Box playerBounds = minecraftClient.player.getBoundingBox();
      Box targetBounds = entity.getBoundingBox().stretch(horizontalStretch, 0.0, horizontalStretch);
      return playerBounds.intersects(targetBounds);
   }

   public static float clampedScaledMagnitude(float value, float factor, float minimum, float maximum) {
      return ClientMath.clamp(minimum, maximum, Math.abs(value) * ClientMath.clampUnit(factor));
   }

   public static boolean hasLineOfSightIgnoringPartialBlocks(Vec3d start, Vec3d end) {
      return minecraftClient.world != null && minecraftClient.player != null ? BlockView.raycast(start, end, null, (unusedRaycastContext, position) -> {
         BlockState blockState = minecraftClient.world.getBlockState(position);
         if (shouldIgnoreRaycastBlock(blockState, position)) {
            return null;
         }

         VoxelShape collisionShape = blockState.getCollisionShape(minecraftClient.world, position);
         return !collisionShape.isEmpty() && collisionShape.raycast(start, end, position) != null ? Boolean.FALSE : null;
      }, unusedRaycastContext -> Boolean.TRUE) : true;
   }

   private static boolean hasLineOfSightToSurface(Vec3d start, Vec3d surface) {
      Vec3d shortenedEnd = surface.add(start.subtract(surface).normalize().multiply((0.05D)));
      return hasLineOfSightIgnoringPartialBlocks(start, shortenedEnd);
   }

   public static boolean isDonationTotem(ItemStack stack) {
      return stack.getItem() == Items.TOTEM_OF_UNDYING
         && !stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getCompoundOrEmpty(("PublicBukkitValues")).getString(("minecraft:don-item"), "").isEmpty();
   }

   public static float[] anglesBetween(double targetX, double targetY, double targetZ, double sourceX, double sourceY, double sourceZ) {
      double[] delta = new double[]{targetX - sourceX, targetY - sourceY, targetZ - sourceZ};
      delta = new double[]{delta[0], delta[1], delta[2], Math.sqrt(delta[0] * delta[0] + delta[2] * delta[2])};
      return new float[]{(float)Math.atan2(delta[2], delta[0]) * (57.29578F) - (90.0F), (float)Math.atan2(delta[1], delta[3]) * (-57.29578F)};
   }

   public static float yawStepWithRandomDivisor(RotationAngles rotation, float minimumDivisor, float maximumDivisor) {
      return minecraftClient.player == null
         ? (360.0F)
         : Math.max(
            MouseSensitivity.rotationStep(),
            Math.abs(MathHelper.wrapDegrees(rotation.getYaw() - minecraftClient.player.getYaw())) / Math.max(1.0F, ClientMath.randomFloatMixture(minimumDivisor, maximumDivisor))
         );
   }

   public static boolean overlapsPlayer(LivingEntity entity) {
      return overlapsPlayer(entity, 0.0F);
   }

   public static boolean rayIntersectsBox(Vec3d start, Vec3d direction, Box bounds, double maxDistance) {
      double entryDistance = 0.0;
      double exitDistance = maxDistance;
      double startX = start.x;
      double startY = start.y;
      double startZ = start.z;

      for (int axis = 0; axis < 3; axis++) {
         double directionComponent = axis == 0 ? direction.x : (axis == 1 ? direction.y : direction.z);
         double startComponent = axis == 0 ? startX : (axis == 1 ? startY : startZ);
         double minimumBound = axis == 0 ? bounds.minX : (axis == 1 ? bounds.minY : bounds.minZ);
         double maximumBound = axis == 0 ? bounds.maxX : (axis == 1 ? bounds.maxY : bounds.maxZ);
         if (Math.abs(directionComponent) < (1.0E-9D)) {
            if (startComponent < minimumBound || startComponent > maximumBound) {
               return false;
            }
         } else {
            double inverseDirection = 1.0 / directionComponent;
            double nearDistance = (minimumBound - startComponent) * inverseDirection;
            double farDistance = (maximumBound - startComponent) * inverseDirection;
            if (nearDistance > farDistance) {
               double swappedDistance = nearDistance;
               nearDistance = farDistance;
               farDistance = swappedDistance;
            }

            entryDistance = Math.max(entryDistance, nearDistance);
            exitDistance = Math.min(exitDistance, farDistance);
            if (entryDistance > exitDistance) {
               return false;
            }
         }
      }

      return exitDistance >= 0.0 && entryDistance <= maxDistance;
   }

   public static float randomQuantizedStep(float minimum, float maximum) {
      return Math.max(MouseSensitivity.rotationStep(), MouseSensitivity.quantize(ClientMath.randomFloatMixture(minimum, maximum)));
   }

   public static float pitchStepWithRandomDivisor(RotationAngles rotation, float minimumDivisor, float maximumDivisor) {
      return minecraftClient.player == null
         ? (360.0F)
         : Math.max(
            MouseSensitivity.rotationStep(),
            Math.abs(rotation.getPitch() - minecraftClient.player.getPitch()) / Math.max(1.0F, ClientMath.randomFloatMixture(minimumDivisor, maximumDivisor))
         );
   }

   public static boolean rayHitsEntityBounds(Entity entity, double range, float yaw, float pitch) {
      if (entity != null && minecraftClient.player != null) {
         Vec3d eyePosition = minecraftClient.player.getEyePos();
         return entity.getBoundingBox().expand(entity.getTargetingMargin()).contains(eyePosition)
            || entity.getBoundingBox().expand(entity.getTargetingMargin()).raycast(eyePosition, eyePosition.add(direction(yaw, pitch).multiply(range))).isPresent();
      } else {
         return false;
      }
   }

   public static String healthText(LivingEntity entity) {
      if (YoutubeModeModule.getInstance().isEnabled() && entity.isInvisible()) {
         return ("1000");
      } else {
         return Math.round(displayedHealth(entity)) == 1000 ? ("?") : String.valueOf(Math.round(displayedHealth(entity)));
      }
   }

   public static float randomMouseStepMultiple(int minimumSteps, int maximumSteps) {
      return Math.max(1, Math.round((float)ClientMath.randomLong(minimumSteps, maximumSteps))) * MouseSensitivity.rotationStep();
   }

   public static float pitchForVector(double x, double y, double z) {
      return (float)Math.atan2(y, Math.sqrt(x * x + z * z)) * (-57.29578F);
   }

   public static float lerpByTargetDistance(Entity entity, float start, float end, float distanceScale) {
      return ClientMath.lerp(start, end, ClientMath.clampUnit((float)(distanceToBounds(entity) / Math.max((0.01F), distanceScale))));
   }

   public static boolean isAimedAtVisibleSurface(LivingEntity entity, double range) {
      if (minecraftClient.player != null && minecraftClient.world != null && entity != null) {
         Vec3d eyePosition = minecraftClient.player.getEyePos();
         Box targetBounds = entity.getBoundingBox().expand(entity.getTargetingMargin());
         if (targetBounds.contains(eyePosition)) {
            return true;
         }

         Vec3d surfaceHit = targetBounds.raycast(eyePosition, eyePosition.add(direction(minecraftClient.player.getYaw(), minecraftClient.player.getPitch()).multiply(range))).orElse(null);
         return surfaceHit != null && hasLineOfSightToSurface(eyePosition, surfaceHit);
      } else {
         return true;
      }
   }

   public static RotationLimits randomRotationLimits(RotationAngles rotation, float minimumDivisor, float maximumDivisor, float returnYawStep, float returnPitchStep) {
      return new RotationLimits(MouseSensitivity.quantize(yawStepWithRandomDivisor(rotation, minimumDivisor, maximumDivisor)), MouseSensitivity.quantize(pitchStepWithRandomDivisor(rotation, minimumDivisor, maximumDivisor)), returnYawStep, returnPitchStep);
   }

   public static Vec3d direction(float yaw, float pitch) {
      float yawRadians = -yaw * (0.017453292F) - (3.1415927F);
      float pitchRadians = -pitch * (0.017453292F);
      return new Vec3d((float)Math.sin(yawRadians) * -((float)Math.cos(pitchRadians)), (float)Math.sin(pitchRadians), (float)Math.cos(yawRadians) * -((float)Math.cos(pitchRadians)));
   }

   public static boolean hasLineOfSight(Vec3d start, Vec3d end) {
      return minecraftClient.world != null && minecraftClient.player != null
         ? minecraftClient.world.raycast(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, minecraftClient.player)).getType() == Type.MISS
         : true;
   }

   public static float directionAngleRadians(double x, double y) {
      return (float)Math.atan2(y, x);
   }

   public static float[] anglesBetween(Vec3d target, Vec3d source) {
      double[] delta = new double[]{target.getX() - source.getX(), target.getY() - source.getY(), target.getZ() - source.getZ()};
      delta = new double[]{delta[0], delta[1], delta[2], Math.sqrt(delta[0] * delta[0] + delta[2] * delta[2])};
      return new float[]{(float)Math.atan2(delta[2], delta[0]) * (57.29578F) - (90.0F), (float)Math.atan2(delta[1], delta[3]) * (-57.29578F)};
   }

   public static RotationAngles findVisibleAimRotation(RotationAngles rotation) {
      AttackAura aura = AttackAura.getInstance();
      if (!aura.isEnabled() || aura.target() == null || !aura.attackThroughBlocks.isSelected(("Неполноценные"))) {
         return rotation;
      }

      if (minecraftClient.player == null || minecraftClient.world == null) {
         return rotation;
      }

      if (!MaceHelper.getInstance().isThrowInProgress() && !aura.isAirborneWithMace()) {
         LivingEntity entity = aura.target();
         Vec3d eyePosition = minecraftClient.player.getEyePos();
         Box targetBounds = entity.getBoundingBox();
         double attackRange = aura.effectiveAttackRange();
         Vec3d currentHit = targetBounds.raycast(eyePosition, eyePosition.add(direction(rotation.getYaw(), rotation.getPitch()).normalize().multiply(attackRange + 1.0))).orElse(null);
         if (currentHit != null && !hasLineOfSightToSurface(eyePosition, currentHit)) {
            ArrayList<Vec3d> visiblePoints = new ArrayList();
            Vec3d pointSum = Vec3d.ZERO;

            for (int xIndex = 0; xIndex <= 8; xIndex++) {
               for (int yIndex = 0; yIndex <= 8; yIndex++) {
                  for (int zIndex = 0; zIndex <= 8; zIndex++) {
                     if (xIndex == 0 || xIndex == 8 || yIndex == 0 || yIndex == 8 || zIndex == 0 || zIndex == 8) {
                        Vec3d samplePoint = new Vec3d(
                           MathHelper.lerp(xIndex / (8.0D), targetBounds.minX, targetBounds.maxX),
                           MathHelper.lerp(yIndex / (8.0D), targetBounds.minY, targetBounds.maxY),
                           MathHelper.lerp(zIndex / (8.0D), targetBounds.minZ, targetBounds.maxZ)
                        );
                        if (!(eyePosition.squaredDistanceTo(samplePoint) > attackRange * attackRange) && hasLineOfSightToSurface(eyePosition, samplePoint)) {
                           visiblePoints.add(samplePoint);
                           pointSum = pointSum.add(samplePoint);
                        }
                     }
                  }
               }
            }

            if (visiblePoints.isEmpty()) {
               return rotation;
            }

            Vec3d averagePoint = pointSum.multiply(1.0 / visiblePoints.size());
            Vec3d selectedPoint = (Vec3d)visiblePoints.get(0);

            for (Vec3d candidatePoint : visiblePoints) {
               if (candidatePoint.squaredDistanceTo(averagePoint) < selectedPoint.squaredDistanceTo(averagePoint)) {
                  selectedPoint = candidatePoint;
               }
            }

            float[] angles = anglesBetween(selectedPoint, eyePosition);
            return new RotationAngles(angles[0], angles[1]);
         } else {
            return rotation;
         }
      } else {
         return rotation;
      }
   }

   public static double distanceToBounds(Entity entity) {
      if (entity != null && minecraftClient.player != null) {
         Vec3d eyePosition = minecraftClient.player.getEyePos();
         Box targetBounds = entity.getBoundingBox();
         double distanceX = Math.max(Math.max(targetBounds.minX - eyePosition.x, eyePosition.x - targetBounds.maxX), 0.0);
         double distanceY = Math.max(Math.max(targetBounds.minY - eyePosition.y, eyePosition.y - targetBounds.maxY), 0.0);
         double distanceZ = Math.max(Math.max(targetBounds.minZ - eyePosition.z, eyePosition.z - targetBounds.maxZ), 0.0);
         return Math.sqrt(distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ);
      } else {
         return (1.7976931348623157E308D);
      }
   }

   public static double cameraAngleToEntity(LivingEntity entity) {
      if (entity != null && minecraftClient.player != null) {
         Vec3d eyePosition = minecraftClient.player.getEyePos();
         Vec3d targetDirection = entity.getEntityPos().add(0.0, MathHelper.clamp(eyePosition.y - entity.getY(), 0.0, entity.getHeight() * (0.8D)), 0.0).subtract(eyePosition);
         float yawDelta = MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-targetDirection.x, targetDirection.z)) - RotationAngles.cameraYaw());
         float pitchDelta = (float)(-Math.toDegrees(Math.atan2(targetDirection.y, Math.hypot(targetDirection.x, targetDirection.z)))) - RotationAngles.cameraPitch();
         return Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
      } else {
         return 0.0;
      }
   }
}
