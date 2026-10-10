package com.mouseclient.modules.impl.combat;

import com.mouseclient.internal.FriendManager;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.RotationController;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.entity.MouseSensitivity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class AuraSync implements MinecraftAccess {
   private static int savedHotbarSlot = -1;
   private static boolean anchorBusy = false;
   private static boolean crystalBusy = false;
   private static boolean miningBusy = false;
   private static LivingEntity commonTarget = null;
   private static long lastTargetTick = 0;

   private AuraSync() {
   }

   public static boolean isAnchorBusy() {
      return anchorBusy;
   }

   public static void setAnchorBusy(boolean busy) {
      anchorBusy = busy;
   }

   public static boolean isCrystalBusy() {
      return crystalBusy;
   }

   public static void setCrystalBusy(boolean busy) {
      crystalBusy = busy;
   }

   public static boolean isMiningBusy() {
      return miningBusy;
   }

   public static void setMiningBusy(boolean busy) {
      miningBusy = busy;
   }

   public static LivingEntity getCommonTarget() {
      return commonTarget;
   }

   public static void setCommonTarget(LivingEntity target) {
      commonTarget = target;
   }

   public static void selectSlot(int slot) {
      if (minecraftClient.player == null || slot < 0 || slot > 8) {
         return;
      }

      if (savedHotbarSlot == -1) {
         savedHotbarSlot = InventoryActions.selectedHotbarSlot();
      }

      minecraftClient.player.getInventory().setSelectedSlot(slot);
      if (minecraftClient.player.networkHandler != null) {
         minecraftClient.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
      }
   }

   public static void restoreSlot() {
      if (minecraftClient.player == null) {
         savedHotbarSlot = -1;
         return;
      }

      if (savedHotbarSlot != -1 && savedHotbarSlot >= 0 && savedHotbarSlot <= 8) {
         minecraftClient.player.getInventory().setSelectedSlot(savedHotbarSlot);
         if (minecraftClient.player.networkHandler != null) {
            minecraftClient.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(savedHotbarSlot));
         }
      }

      savedHotbarSlot = -1;
   }

   public static LivingEntity findTarget(double range) {
      if (minecraftClient.player == null || minecraftClient.world == null) {
         commonTarget = null;
         return null;
      }

      if (minecraftClient.player.isGliding() && ElytraTarget.getInstance().isEnabled()) {
         LivingEntity chased = ElytraTarget.getInstance().target();
         if (chased != null && chased.distanceTo(minecraftClient.player) <= range + 2.0F) {
            commonTarget = chased;
            return chased;
         }
      }

      LivingEntity best = null;
      double bestDistance = Double.MAX_VALUE;

      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof PlayerEntity player && isValidTarget(player, range)) {
            double distance = player.distanceTo(minecraftClient.player);
            if (distance < bestDistance) {
               best = player;
               bestDistance = distance;
            }
         }
      }

      commonTarget = best;
      return best;
   }

   public static boolean isValidTarget(PlayerEntity player, double range) {
      return player != null
         && player != minecraftClient.player
         && player.isAlive()
         && !player.isSpectator()
         && !player.isCreative()
         && !FriendManager.contains(player.getName().getString())
         && player.distanceTo(minecraftClient.player) <= range + 2.0F;
   }

   public static boolean isSnap(String mode) {
      if (mode == null) {
         return false;
      }
      String lower = mode.toLowerCase();
      return lower.contains("snap") || lower.contains("снап");
   }

   public static boolean applyRotation(Vec3d point, String mode) {
      if (minecraftClient.player == null || point == null) {
         return true;
      }

      if (isSnap(mode)) {
         if (RotationController.getInstance().isActive()) {
            RotationController.getInstance().reset();
         }
         float[] angles = CombatTargeting.anglesBetween(point, minecraftClient.player.getEyePos());
         float yaw = angles[0];
         float pitch = MathHelper.clamp(angles[1], -90.0F, 90.0F);
         minecraftClient.player.setYaw(yaw);
         minecraftClient.player.headYaw = yaw;
         minecraftClient.player.bodyYaw = yaw;
         minecraftClient.player.setPitch(pitch);
         return true;
      }

      return stepFastSmoothRotation(point);
   }

   public static boolean stepFastSmoothRotation(Vec3d targetPoint) {
      if (minecraftClient.player == null || targetPoint == null) {
         return true;
      }

      RotationController.getInstance().lockCamera();

      float[] angles = CombatTargeting.anglesBetween(targetPoint, minecraftClient.player.getEyePos());
      float targetYaw = angles[0];
      float targetPitch = MathHelper.clamp(angles[1], -90.0F, 90.0F);

      float currentYaw = minecraftClient.player.getYaw();
      float currentPitch = minecraftClient.player.getPitch();

      float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
      float pitchDiff = targetPitch - currentPitch;

      float dist = (float) Math.hypot(yawDiff, pitchDiff);
      if (dist < 0.5F) {
         return true;
      }

      // Быстрая плавная скорость:
      // При больших углах (> 60°): скорость до 75° за тик
      // При средних углах (25°-60°): скорость 50° за тик
      // Если осталось <= 35°, быстрый плавный доворот прямо на цель
      float maxSpeed = dist > 60.0F ? 75.0F : (dist > 25.0F ? 50.0F : 35.0F);

      float yawStep;
      float pitchStep;

      if (dist <= 35.0F) {
         yawStep = Math.abs(yawDiff);
         pitchStep = Math.abs(pitchDiff);
      } else {
         float ratio = maxSpeed / dist;
         yawStep = Math.min(Math.abs(yawDiff), Math.abs(yawDiff) * ratio);
         pitchStep = Math.min(Math.abs(pitchDiff), Math.abs(pitchDiff) * ratio);
      }

      float newYaw = currentYaw + MouseSensitivity.quantize(Math.signum(yawDiff) * yawStep);
      float newPitch = MathHelper.clamp(
         currentPitch + MouseSensitivity.quantize(Math.signum(pitchDiff) * pitchStep),
         -90.0F,
         90.0F
      );

      minecraftClient.player.setYaw(newYaw);
      minecraftClient.player.setPitch(newPitch);
      minecraftClient.player.headYaw = newYaw;
      minecraftClient.player.bodyYaw = newYaw;

      float remainingYaw = Math.abs(MathHelper.wrapDegrees(targetYaw - newYaw));
      float remainingPitch = Math.abs(targetPitch - newPitch);

      return remainingYaw <= 15.0F && remainingPitch <= 15.0F;
   }

   public static BlockHitResult findBestBlockHit(BlockPos pos) {
      if (minecraftClient.player == null || minecraftClient.world == null) {
         return new BlockHitResult(pos.toCenterPos().add(0.0, 0.5, 0.0), Direction.UP, pos, false);
      }

      Vec3d eye = minecraftClient.player.getEyePos();
      Direction bestFace = null;
      Vec3d bestHit = null;
      double bestDistSq = Double.MAX_VALUE;

      for (Direction side : Direction.values()) {
         BlockPos neighbor = pos.offset(side);
         BlockState neighborState = minecraftClient.world.getBlockState(neighbor);
         if (neighborState.isAir() || neighborState.isReplaceable() || neighborState.getCollisionShape(minecraftClient.world, neighbor).isEmpty()) {
            Vec3d faceHit = pos.toCenterPos().add(
               side.getOffsetX() * 0.5,
               side.getOffsetY() * 0.5,
               side.getOffsetZ() * 0.5
            );
            Vec3d toEye = eye.subtract(faceHit);
            double dot = toEye.x * side.getOffsetX() + toEye.y * side.getOffsetY() + toEye.z * side.getOffsetZ();
            if (dot > 0.0) {
               double distSq = eye.squaredDistanceTo(faceHit);
               if (side == Direction.UP) {
                  distSq *= 0.8;
               }
               if (distSq < bestDistSq) {
                  bestDistSq = distSq;
                  bestFace = side;
                  bestHit = faceHit;
               }
            }
         }
      }

      if (bestFace != null && bestHit != null) {
         return new BlockHitResult(bestHit, bestFace, pos, false);
      }

      Vec3d fallbackHit = pos.toCenterPos().add(0.0, 0.5, 0.0);
      return new BlockHitResult(fallbackHit, Direction.UP, pos, false);
   }

   public static BlockHitResult findPlacementHit(BlockPos targetPos) {
      if (minecraftClient.player == null || minecraftClient.world == null) {
         return null;
      }

      Vec3d eye = minecraftClient.player.getEyePos();
      Direction bestFace = null;
      BlockPos bestNeighbor = null;
      Vec3d bestHit = null;
      double bestDistSq = Double.MAX_VALUE;

      for (Direction side : Direction.values()) {
         BlockPos neighbor = targetPos.offset(side);
         BlockState state = minecraftClient.world.getBlockState(neighbor);
         if (isSolidForPlacement(state)) {
            Direction face = side.getOpposite();
            Vec3d faceHit = neighbor.toCenterPos().add(
               face.getOffsetX() * 0.5,
               face.getOffsetY() * 0.5,
               face.getOffsetZ() * 0.5
            );
            Vec3d toEye = eye.subtract(faceHit);
            double dot = toEye.x * face.getOffsetX() + toEye.y * face.getOffsetY() + toEye.z * face.getOffsetZ();
            if (dot > 0.0) {
               double distSq = eye.squaredDistanceTo(faceHit);
               if (side == Direction.DOWN && face == Direction.UP) {
                  distSq *= 0.7;
               }
               if (distSq < bestDistSq) {
                  bestDistSq = distSq;
                  bestFace = face;
                  bestNeighbor = neighbor;
                  bestHit = faceHit;
               }
            }
         }
      }

      if (bestNeighbor != null && bestFace != null && bestHit != null) {
         return new BlockHitResult(bestHit, bestFace, bestNeighbor, false);
      }

      return null;
   }

   private static boolean isSolidForPlacement(BlockState state) {
      if (state == null || state.isAir() || state.isReplaceable()) {
         return false;
      }
      return state.getFluidState().isEmpty();
   }

   public static void interactBlock(BlockHitResult hitResult, Hand hand) {
      if (minecraftClient.player == null || minecraftClient.interactionManager == null || hitResult == null) {
         return;
      }

      minecraftClient.crosshairTarget = hitResult;
      minecraftClient.interactionManager.interactBlock(minecraftClient.player, hand, hitResult);
      minecraftClient.player.swingHand(hand);
   }

   public static void interactBlock(BlockHitResult hitResult, Hand hand, String rotationMode) {
      interactBlock(hitResult, hand);
   }

   public static void interactBlock(BlockPos pos, Direction side, Hand hand) {
      if (pos == null) {
         return;
      }
      Vec3d hit = pos.toCenterPos().add(
         side.getOffsetX() * 0.5,
         side.getOffsetY() * 0.5,
         side.getOffsetZ() * 0.5
      );
      interactBlock(new BlockHitResult(hit, side, pos, false), hand);
   }

   public static void attackEntity(Entity entity) {
      if (minecraftClient.player == null || minecraftClient.interactionManager == null || entity == null) {
         return;
      }

      minecraftClient.interactionManager.attackEntity(minecraftClient.player, entity);
      minecraftClient.player.swingHand(Hand.MAIN_HAND);
   }

   public static void attackEntity(Entity entity, String rotationMode) {
      attackEntity(entity);
   }

   public static boolean isNether() {
      return minecraftClient.world != null && minecraftClient.world.getRegistryKey() == World.NETHER;
   }

   public static int findPickaxeSlot() {
      if (minecraftClient.player == null) {
         return -1;
      }

      int bestSlot = -1;
      float bestSpeed = 1.0F;

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = minecraftClient.player.getInventory().getStack(slot);
         if (isPickaxe(stack)) {
            float speed = stack.getMiningSpeedMultiplier(Blocks.OBSIDIAN.getDefaultState());
            if (speed > bestSpeed) {
               bestSpeed = speed;
               bestSlot = slot;
            }
         }
      }

      if (bestSlot != -1) {
         return bestSlot;
      }

      for (int slot = 9; slot < 36; slot++) {
         ItemStack stack = minecraftClient.player.getInventory().getStack(slot);
         if (isPickaxe(stack)) {
            return slot;
         }
      }

      return -1;
   }

   private static boolean isPickaxe(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }
      return stack.isIn(ItemTags.PICKAXES)
         || stack.isOf(Items.NETHERITE_PICKAXE)
         || stack.isOf(Items.DIAMOND_PICKAXE)
         || stack.isOf(Items.IRON_PICKAXE)
         || stack.isOf(Items.GOLDEN_PICKAXE);
   }

   public static BlockPos findBurrowBlock(LivingEntity target) {
      if (minecraftClient.world == null || target == null || minecraftClient.player == null) {
         return null;
      }

      BlockPos feet = target.getBlockPos();
      BlockState feetState = minecraftClient.world.getBlockState(feet);
      if (isBreakableBurrowBlock(feetState, feet)) {
         return feet;
      }

      BlockPos[] surround = new BlockPos[]{
         feet.north(), feet.south(), feet.east(), feet.west()
      };

      BlockPos bestSurround = null;
      double closestDist = Double.MAX_VALUE;
      for (BlockPos pos : surround) {
         BlockState state = minecraftClient.world.getBlockState(pos);
         if (isBreakableBurrowBlock(state, pos)) {
            double dist = minecraftClient.player.squaredDistanceTo(pos.toCenterPos());
            if (dist < closestDist) {
               closestDist = dist;
               bestSurround = pos;
            }
         }
      }

      if (bestSurround != null) {
         return bestSurround;
      }

      BlockPos under = feet.down();
      BlockState underState = minecraftClient.world.getBlockState(under);
      if (isBreakableBurrowBlock(underState, under)) {
         return under;
      }

      return null;
   }

   private static boolean isBreakableBurrowBlock(BlockState state, BlockPos pos) {
      if (state == null || state.isAir() || state.isLiquid()) {
         return false;
      }
      if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.BARRIER)) {
         return false;
      }
      return state.isOf(Blocks.OBSIDIAN)
         || state.isOf(Blocks.CRYING_OBSIDIAN)
         || state.isOf(Blocks.RESPAWN_ANCHOR)
         || state.isOf(Blocks.ENDER_CHEST)
         || state.isOf(Blocks.ANVIL)
         || state.isOf(Blocks.CHIPPED_ANVIL)
         || state.isOf(Blocks.DAMAGED_ANVIL)
         || state.getHardness(minecraftClient.world, pos) > 0.0F;
   }

   public static boolean performMine(BlockPos pos, String rotationMode) {
      if (minecraftClient.player == null || minecraftClient.interactionManager == null || minecraftClient.world == null || pos == null) {
         return false;
      }

      int pickaxeSlot = findPickaxeSlot();
      if (pickaxeSlot == -1) {
         return false;
      }

      if (pickaxeSlot < 9) {
         selectSlot(pickaxeSlot);
      } else {
         InventoryActions.swapWithHand(pickaxeSlot, Hand.MAIN_HAND);
      }

      BlockHitResult hitResult = findBestBlockHit(pos);
      if (!applyRotation(hitResult.getPos(), rotationMode)) {
         return false;
      }

      Direction face = hitResult.getSide();
      minecraftClient.crosshairTarget = hitResult;
      minecraftClient.interactionManager.attackBlock(pos, face);
      minecraftClient.interactionManager.updateBlockBreakingProgress(pos, face);

      if (minecraftClient.player.networkHandler != null) {
         minecraftClient.player.networkHandler.sendPacket(
            new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, face)
         );
         minecraftClient.player.networkHandler.sendPacket(
            new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, face)
         );
      }

      minecraftClient.player.swingHand(Hand.MAIN_HAND);
      return true;
   }

   public static boolean holdItem(net.minecraft.item.Item item) {
      if (minecraftClient.player == null) {
         return false;
      }

      if (minecraftClient.player.getOffHandStack().isOf(item)) {
         return true;
      }

      if (minecraftClient.player.getMainHandStack().isOf(item)) {
         return true;
      }

      int hotbar = InventoryActions.findHotbarItem(item);
      if (hotbar != -1) {
         selectSlot(hotbar);
         return true;
      }

      int inventory = InventoryActions.findItem(item);
      if (inventory != -1) {
         InventoryActions.swapWithHand(inventory, Hand.MAIN_HAND);
         return minecraftClient.player.getMainHandStack().isOf(item);
      }

      return false;
   }
}
