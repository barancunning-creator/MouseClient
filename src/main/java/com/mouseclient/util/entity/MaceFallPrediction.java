package com.mouseclient.util.entity;

import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.network.InventoryActions;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public final class MaceFallPrediction implements MinecraftAccess {

   private static final List<VoxelShape> emptyCollisionShapes = Collections.emptyList();
   private static Vec3d cachedLandingPosition;
   private static int landingCacheTick = -1;
   private static int smashWaitTicks;
   private static int attackCooldownTicks;
   private static double cachedMaximumFallDistance;
   private static int fallDistanceCacheTick = -1;

   public static boolean isLandingAwayFromTarget(LivingEntity target, boolean checkLanding) {
      if (checkLanding && !willLandWithinTwoTicks() && !willEnterFallInterruptingBlock()) {
         Vec3d position = landingPosition();
         return position == null || target == null || position.distanceTo(target.getEntityPos()) > (2.0D);
      } else {
         return true;
      }
   }

   public static boolean willLandNextTick() {
      if (minecraftClient.player != null && !(minecraftClient.player.fallDistance <= 1.0)) {
         PlayerMovementSimulation simulation = PlayerMovementSimulation.predictLocalPlayer(1);
         return simulation.onGround && minecraftClient.player.fallDistance > (1.5D) || simulation.touchingWater;
      } else {
         return false;
      }
   }

   private MaceFallPrediction() {
   }

   private static double calculateMaximumFallDistance() {
      if (minecraftClient.player == null || minecraftClient.world == null || minecraftClient.player.isOnGround()) {
         return 0.0;
      }

      if (minecraftClient.player.getAbilities().flying
         || minecraftClient.player.isGliding()
         || minecraftClient.player.hasVehicle()
         || minecraftClient.player.isClimbing()) {
         return 0.0;
      }

      if (!minecraftClient.player.hasStatusEffect(StatusEffects.LEVITATION) && !minecraftClient.player.isTouchingWater() && !minecraftClient.player.isInLava()) {
         PlayerMovementSimulation simulation = PlayerMovementSimulation.snapshotLocalPlayer(PlayerMovementSimulation.SimulatedPlayerInput.fromClientPlayer(minecraftClient.player.input.playerInput));
         double maximumFallDistance = minecraftClient.player.fallDistance;

         for (int var3 = 0; var3 < 60; var3++) {
            simulation.tick();
            maximumFallDistance = Math.max(maximumFallDistance, simulation.fallDistance);
            if (simulation.onGround || simulation.touchingWater) {
               break;
            }
         }

         return maximumFallDistance;
      } else {
         return 0.0;
      }
   }

   public static boolean canSmashAttack() {
      return hasMaceAvailable() && minecraftClient.player != null && minecraftClient.player.fallDistance > (1.2999999523162842D) && attackCooldownTicks <= 0;
   }

   public static float predictFallDistance(int ticks) {
      return minecraftClient.player == null ? 0.0F : PlayerMovementSimulation.predictLocalPlayer(ticks).fallDistance;
   }

   public static boolean isAttackCooldownActive() {
      return attackCooldownTicks > 0;
   }

   private static boolean containsFluid(Box bounds) {
      for (BlockPos position : BlockPos.iterate(BlockPos.ofFloored(bounds.minX, bounds.minY, bounds.minZ), BlockPos.ofFloored(bounds.maxX, bounds.maxY, bounds.maxZ))) {
         if (minecraftClient.world.getBlockState(position).getFluidState().isIn(FluidTags.WATER)
            || minecraftClient.world.getBlockState(position).getFluidState().isIn(FluidTags.LAVA)) {
            return true;
         }
      }

      return false;
   }

   public static void tickAttackCooldown() {
      if (attackCooldownTicks > 0) {
         attackCooldownTicks--;
      }
   }

   public static boolean hasMaceAvailable() {
      if (minecraftClient.player == null) {
         return false;
      } else if (minecraftClient.player.getMainHandStack().isOf(Items.MACE)) {
         return true;
      } else {
         return minecraftClient.player.fallDistance < 1.0
            ? false
            : AttackAura.getInstance().autoMace.getValue() && InventoryActions.findItem(Items.MACE) != -1;
      }
   }

   private static boolean containsFallInterruptingBlock(Box bounds) {
      for (BlockPos position : BlockPos.iterate(BlockPos.ofFloored(bounds.minX, bounds.minY, bounds.minZ), BlockPos.ofFloored(bounds.maxX, bounds.maxY, bounds.maxZ))) {
         BlockState blockState = minecraftClient.world.getBlockState(position);
         if (!blockState.isOf(Blocks.COBWEB) && !blockState.isOf(Blocks.SWEET_BERRY_BUSH)) {
            if (!blockState.getFluidState().isIn(FluidTags.WATER) && !blockState.getFluidState().isIn(FluidTags.LAVA)) {
               continue;
            }

            return true;
         }

         return true;
      }

      return false;
   }

   public static double heightAboveLanding() {
      Vec3d position = landingPosition();
      return position != null && minecraftClient.player != null ? minecraftClient.player.getY() - position.y : (-1.0D);
   }

   public static boolean willEnterFallInterruptingBlock() {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         double gravity = minecraftClient.player.getFinalGravity();
         double offsetY = 0.0;
         Vec3d velocity = minecraftClient.player.getVelocity();

         for (int var5 = 0; var5 < 2; var5++) {
            double nextVelocityY = (velocity.y - gravity) * (0.9799995613681012D);
            Box bounds = minecraftClient.player.getBoundingBox().offset(0.0, offsetY, 0.0);
            Vec3d adjustedMovement = Entity.adjustMovementForCollisions(minecraftClient.player, new Vec3d(0.0, nextVelocityY, 0.0), bounds, minecraftClient.world, emptyCollisionShapes);
            if (nextVelocityY < 0.0 && containsFallInterruptingBlock(bounds.offset(adjustedMovement.x, adjustedMovement.y, adjustedMovement.z))) {
               return true;
            }

            offsetY += adjustedMovement.y;
            velocity = new Vec3d(velocity.x, adjustedMovement.y, velocity.z);
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean willLandWithinTwoTicks() {
      if (minecraftClient.player != null && minecraftClient.world != null && !minecraftClient.player.isOnGround()) {
         double gravity = minecraftClient.player.getFinalGravity();
         double offsetY = 0.0;
         Vec3d velocity = minecraftClient.player.getVelocity();

         for (int var5 = 0; var5 < 2; var5++) {
            double nextVelocityY = (velocity.y - gravity) * (0.9799995613681012D);
            Vec3d adjustedMovement = Entity.adjustMovementForCollisions(
               minecraftClient.player, new Vec3d(0.0, nextVelocityY, 0.0), minecraftClient.player.getBoundingBox().offset(0.0, offsetY, 0.0), minecraftClient.world, emptyCollisionShapes
            );
            if (nextVelocityY < 0.0 && (var5 == 0 ? adjustedMovement.y != nextVelocityY : adjustedMovement.y > nextVelocityY * (0.5D))) {
               return true;
            }

            offsetY += adjustedMovement.y;
            velocity = new Vec3d(velocity.x, adjustedMovement.y, velocity.z);
         }

         return false;
      } else {
         return false;
      }
   }

   public static void resetAttackState() {
      smashWaitTicks = 0;
      attackCooldownTicks = 0;
   }

   private static boolean isTargetFallingJustAbovePlayer(LivingEntity target) {
      if (target != null && minecraftClient.player != null) {
         double heightDifference = target.getY() - minecraftClient.player.getY();
         return heightDifference > 0.0 && heightDifference <= (1.5D) && target.getY() - target.lastRenderY < 0.0;
      } else {
         return false;
      }
   }

   public static double maximumFallDistance() {
      if (minecraftClient.player == null) {
         return 0.0;
      }

      if (minecraftClient.player.age == fallDistanceCacheTick) {
         return cachedMaximumFallDistance;
      }

      fallDistanceCacheTick = minecraftClient.player.age;
      cachedMaximumFallDistance = calculateMaximumFallDistance();
      return cachedMaximumFallDistance;
   }

   public static Vec3d landingPosition() {
      if (minecraftClient.player == null) {
         return null;
      }

      if (minecraftClient.player.age == landingCacheTick) {
         return cachedLandingPosition;
      }

      landingCacheTick = minecraftClient.player.age;
      cachedLandingPosition = calculateLandingPosition();
      return cachedLandingPosition;
   }

   public static void markAttack() {
      if (minecraftClient.player != null && minecraftClient.player.getMainHandStack().isOf(Items.MACE)) {
         attackCooldownTicks = 20;
      }
   }

   public static boolean shouldWaitForSmash(LivingEntity target) {
      if (minecraftClient.player != null && hasMaceAvailable()) {
         if (maximumFallDistance() <= (1.5D)) {
            smashWaitTicks = 0;
            return false;
         }

         if (!willLandNextTick() && !isTargetFallingJustAbovePlayer(target)) {
            if (predictFallDistance(1) >= 1.0F && !willTargetBeReachable(target)) {
               smashWaitTicks = 0;
               return false;
            } else if (++smashWaitTicks >= 10) {
               smashWaitTicks = 0;
               return false;
            } else {
               return true;
            }
         } else {
            smashWaitTicks = 0;
            return false;
         }
      } else {
         smashWaitTicks = 0;
         return false;
      }
   }

   private static boolean willTargetBeReachable(LivingEntity target) {
      if (target != null && minecraftClient.player != null && minecraftClient.world != null) {
         PlayerMovementSimulation simulation = PlayerMovementSimulation.predictLocalPlayer(1);
         Vec3d predictedEyePosition = simulation.position.add(0.0, minecraftClient.player.getStandingEyeHeight(), 0.0);
         Vec3d targetMovement = new Vec3d(
            (target.getX() - target.lastRenderX) * (2.0D),
            (target.getY() - target.lastRenderY) * (2.0D),
            (target.getZ() - target.lastRenderZ) * (2.0D)
         );
         Box targetBounds = target.getBoundingBox().offset(targetMovement);
         if (!CombatTargeting.hasLineOfSight(predictedEyePosition, targetBounds.getCenter())) {
            return false;
         }

         double attackRange = AttackAura.getInstance().effectiveAttackRange();
         double distanceX = Math.max(Math.max(targetBounds.minX - predictedEyePosition.x, predictedEyePosition.x - targetBounds.maxX), 0.0);
         double distanceY = Math.max(Math.max(targetBounds.minY - predictedEyePosition.y, predictedEyePosition.y - targetBounds.maxY), 0.0);
         double distanceZ = Math.max(Math.max(targetBounds.minZ - predictedEyePosition.z, predictedEyePosition.z - targetBounds.maxZ), 0.0);
         return distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ < attackRange * attackRange;
      } else {
         return false;
      }
   }

   private static Vec3d calculateLandingPosition() {
      if (minecraftClient.player == null || minecraftClient.world == null || minecraftClient.player.isOnGround()) {
         return null;
      }

      if (minecraftClient.player.getAbilities().flying
         || minecraftClient.player.isGliding()
         || minecraftClient.player.hasVehicle()
         || minecraftClient.player.isClimbing()) {
         return null;
      }

      if (minecraftClient.player.hasStatusEffect(StatusEffects.LEVITATION)) {
         return null;
      }

      if (!minecraftClient.player.isTouchingWater() && !minecraftClient.player.isInLava()) {
         double gravity = minecraftClient.player.getFinalGravity();
         double offsetX = 0.0;
         double offsetY = 0.0;
         double offsetZ = 0.0;
         Box initialBounds = minecraftClient.player.getBoundingBox();
         Vec3d velocity = minecraftClient.player.getVelocity();

         for (int var10 = 0; var10 < 400; var10++) {
            double velocityX = velocity.x * (0.9799995613681012D);
            double velocityY = (velocity.y - gravity) * (0.9799995613681012D);
            double velocityZ = velocity.z * (0.9799995613681012D);
            Vec3d intendedMovement = new Vec3d(velocityX, velocityY, velocityZ);
            Vec3d adjustedMovement = Entity.adjustMovementForCollisions(minecraftClient.player, intendedMovement, initialBounds.offset(offsetX, offsetY, offsetZ), minecraftClient.world, emptyCollisionShapes);
            if (intendedMovement.y < (-9.999995680744685E-5D) && adjustedMovement.y > intendedMovement.y + (9.999995999686725E-5D)) {
               return minecraftClient.player.getEntityPos().add(offsetX + adjustedMovement.x, offsetY + adjustedMovement.y, offsetZ + adjustedMovement.z);
            }

            offsetX += adjustedMovement.x;
            offsetY += adjustedMovement.y;
            offsetZ += adjustedMovement.z;
            if (containsFluid(initialBounds.offset(offsetX, offsetY, offsetZ))) {
               return minecraftClient.player.getEntityPos().add(offsetX, offsetY, offsetZ);
            }

            if (Math.abs(adjustedMovement.x - intendedMovement.x) > (9.999995999686725E-5D)) {
               velocityX = 0.0;
            }

            if (Math.abs(adjustedMovement.y - intendedMovement.y) > (9.999995999686725E-5D)) {
               velocityY = 0.0;
            }

            if (Math.abs(adjustedMovement.z - intendedMovement.z) > (9.999995999686725E-5D)) {
               velocityZ = 0.0;
            }

            velocity = new Vec3d(velocityX, velocityY, velocityZ);
            if (velocity.lengthSquared() < (1.0000000090069629E-12D) && intendedMovement.y >= (-9.999995680744685E-5D)) {
               break;
            }
         }

         return null;
      } else {
         return null;
      }
   }
}
