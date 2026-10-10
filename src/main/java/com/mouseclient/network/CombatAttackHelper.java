package com.mouseclient.network;

import com.mouseclient.internal.ClientMath;
import com.mouseclient.modules.impl.combat.AimAssist;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.combat.MaceHelper;
import com.mouseclient.modules.impl.combat.TriggerBot;
import com.mouseclient.modules.impl.movement.AirStuck;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.MaceFallPrediction;
import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.time.Stopwatch;
import java.util.Arrays;
import lombok.Generated;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class CombatAttackHelper implements MinecraftAccess {
   private static final Stopwatch attackTimer = new Stopwatch();
   private static int ticksSinceAttack;
   private static long attackSequenceCount;
   private static boolean repeatingAttacks;
   private static int remainingRepeatAttacks;
   private static float unusedAttackJitter = ClientMath.secureRandom().nextBoolean() ? 0.0F : (0.1F);

   @Generated
   public static Stopwatch attackTimer() {
      return attackTimer;
   }

   public static boolean isCooldownReady() {
      return isCooldownReady(0L);
   }

   public static boolean performAttack(LivingEntity target, Runnable beforeAttack, Runnable afterAttack, Hand swingHand, boolean advanceSequence) {
      if (target != null && minecraftClient.interactionManager != null) {
         if (beforeAttack != null) {
            beforeAttack.run();
         }

         minecraftClient.interactionManager.attackEntity(minecraftClient.player, target);
         if (advanceSequence) {
            advanceAttackSequence();
         } else {
            resetAttackSequence();
         }

         ticksSinceAttack = 0;
         unusedAttackJitter = ClientMath.secureRandom().nextBoolean() ? 0.0F : (0.1F);
         if (swingHand != null) {
            minecraftClient.player.swingHand(swingHand);
         }

         if (afterAttack != null) {
            afterAttack.run();
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean isExtendedRepeatDelay() {
      return attackSequenceCount % (7L) == (3L);
   }

   public static boolean isWithinRange(LivingEntity target, float range) {
      return target != null && minecraftClient.player != null ? minecraftClient.player.squaredDistanceTo(target) <= range * range : true;
   }

   public static void tick() {
      ticksSinceAttack++;
   }

   public static void resetAttackSequence() {
      attackSequenceCount = 0L;
   }

   public static float attackRecoveryProgress() {
      return Math.min(ticksSinceAttack / (10.0F), 1.0F);
   }

   public static long attackElapsedMillis() {
      return ticksSinceAttack * (50L);
   }

   public static void resetRepeatState() {
      repeatingAttacks = false;
      remainingRepeatAttacks = 0;
   }

   public static boolean canAttack(LivingEntity target, boolean requireCrosshair, boolean checkRange, boolean requireCritical, long delayMillis, float[] attackRanges) {
      float effectiveRange = attackRanges.length > 2 ? attackRanges[2] : attackRanges[0];
      if (checkRange && target != null && !isWithinRange(target, effectiveRange)) {
         return false;
      }

      if (!isCooldownReady(delayMillis)) {
         return false;
      }

      boolean attackAllowed = canAttackInCurrentFallState(requireCritical);
      if (attackAllowed && requireCrosshair && !isTargetUnderCrosshair(target, attackRanges[0])) {
         attackAllowed = false;
      }

      return attackAllowed;
   }

   private static int repeatAttackLimit() {
      return 3;
   }

   public static double verticalClearance(int rayDistance) {
      if (minecraftClient.world == null) {
         return 1.0;
      }

      Vec3d position = minecraftClient.player.getEyePos();
      double clearance = rayDistance * (2.0D);
      double lowestCeilingY = (255.0D);
      double highestFloorY = (-64.0D);
      float halfWidth = minecraftClient.player.getWidth() / 2.0F - (0.01F);

      for (Vec3d corner : Arrays.asList(position.add(-halfWidth, 0.0, -halfWidth), position.add(halfWidth, 0.0, halfWidth), position.add(halfWidth, 0.0, -halfWidth), position.add(-halfWidth, 0.0, halfWidth))) {
         double floorY = minecraftClient.world
            .raycast(new RaycastContext(corner, corner.add(0.0, -rayDistance, 0.0), ShapeType.VISUAL, FluidHandling.ANY, minecraftClient.player))
            .getPos()
            .y;
         double ceilingY = minecraftClient.world
            .raycast(new RaycastContext(corner, corner.add(0.0, rayDistance, 0.0), ShapeType.VISUAL, FluidHandling.ANY, minecraftClient.player))
            .getPos()
            .y;
         if (lowestCeilingY > ceilingY) {
            lowestCeilingY = ceilingY;
         }

         if (highestFloorY < floorY) {
            highestFloorY = floorY;
         }

         if (clearance > lowestCeilingY - highestFloorY) {
            clearance = lowestCeilingY - highestFloorY;
         }
      }

      return clearance - minecraftClient.player.getHeight();
   }

   public static void advanceAttackSequence() {
      attackSequenceCount++;
   }

   public static double effectiveFallThreshold() {
      double fallThreshold = minecraftClient.player.fallDistance;
      if (minecraftClient.world != null
         && !minecraftClient.player.isOnGround()
         && minecraftClient.player.getVelocity().y < (-0.0784000015258789D)
         && !minecraftClient.world.getBlockState(minecraftClient.player.getBlockPos()).getFluidState().isIn(FluidTags.WATER)
         && !minecraftClient.world.getBlockState(minecraftClient.player.getBlockPos().up()).getFluidState().isIn(FluidTags.WATER)) {
         fallThreshold = -minecraftClient.player.getVelocity().y;
      }

      return fallThreshold;
   }

   public static boolean canAttack(LivingEntity target, boolean requireCrosshair, boolean requireCritical, long delayMillis, float[] attackRanges) {
      return canAttack(target, requireCrosshair, true, requireCritical, delayMillis, attackRanges);
   }

   public static boolean isTargetUnderCrosshair(LivingEntity target, double range) {
      return target != null && CombatTargeting.rayHitsEntityBounds(target, range, minecraftClient.player.getYaw(), minecraftClient.player.getPitch());
   }

   public static void updateRepeatAttacks(LivingEntity target, boolean advanceSequence, boolean requireCrosshair, boolean repeatEnabled) {
      if (target == null || remainingRepeatAttacks == 0 || !repeatEnabled || target.hurtTime != 0) {
         resetRepeatState();
      }

      if (repeatEnabled
         && target != null
         && !(target instanceof PlayerEntity)
         && !minecraftClient.player.isUsingItem()
         && hasAttackDelayElapsed(isExtendedRepeatDelay() ? (250L) : (150L))
         && minecraftClient.player.handSwinging) {
         if (!repeatingAttacks && remainingRepeatAttacks == 0 && target.hurtTime == 0) {
            repeatingAttacks = true;
            remainingRepeatAttacks = repeatAttackLimit();
         }

         if (repeatingAttacks && remainingRepeatAttacks > 0 && (!requireCrosshair || isTargetUnderCrosshair(target, (6.0D))) && performAttack(target, () -> {}, () -> {}, Hand.MAIN_HAND, advanceSequence)) {
            remainingRepeatAttacks--;
         }
      }
   }

   private static LivingEntity currentTarget() {
      return AttackAura.getInstance().target() != null ? AttackAura.getInstance().target() : TriggerBot.getInstance().target();
   }

   private static boolean isMaceActive() {
      return AttackAura.getInstance().hasMaceEquippedOrQueued() || TriggerBot.getInstance().hasMaceEquippedOrQueued() || MaceFallPrediction.hasMaceAvailable();
   }

   public static boolean canAttackInCurrentFallState(boolean requireCritical) {
      if (isMaceActive()
         && minecraftClient.player != null
         && !minecraftClient.player.isOnGround()
         && InventoryActions.findItem(Items.MACE) != -1
         && !minecraftClient.player
            .getItemCooldownManager()
            .isCoolingDown(minecraftClient.player.getInventory().getStack(InventoryActions.findItem(Items.MACE)))) {
         if (MaceFallPrediction.shouldWaitForSmash(currentTarget())) {
            return false;
         }

         if (MaceItem.shouldDealAdditionalDamage(minecraftClient.player) && minecraftClient.player.getVelocity().y < 0.0) {
            return true;
         }
      }

      if (!requireCritical) {
         return true;
      }

      boolean smartCriticals = AttackAura.getInstance().isEnabled() && AttackAura.getInstance().smartCriticals()
         || TriggerBot.getInstance().isEnabled() && TriggerBot.getInstance().usesSmartCriticals()
         || AimAssist.getInstance().isEnabled() && AimAssist.getInstance().usesSmartCriticals();
      FluidState eyeFluid = minecraftClient.world.getFluidState(BlockPos.ofFloored(minecraftClient.player.getEyePos()));
      boolean canCritical = !minecraftClient.player.hasStatusEffect(StatusEffects.LEVITATION)
         && !minecraftClient.player.hasStatusEffect(StatusEffects.BLINDNESS)
         && !eyeFluid.isIn(FluidTags.WATER)
         && !eyeFluid.isIn(FluidTags.LAVA)
         && !minecraftClient.player.getAbilities().flying
         && !minecraftClient.player.isGliding()
         && !minecraftClient.player.isClimbing()
         && !minecraftClient.player.hasVehicle();
      return canCritical && minecraftClient.player.fallDistance > (AirStuck.getInstance().isFrozen() ? 0.0 : effectiveFallThreshold()) && !minecraftClient.player.isOnGround()
         || smartCriticals && minecraftClient.player.isOnGround() && !minecraftClient.player.input.playerInput.jump()
         || !canCritical;
   }

   private static boolean isMaceDamageBoostEnabled() {
      return MaceHelper.getInstance().damageBoost.getValue();
   }

   private CombatAttackHelper() {
   }

   public static boolean isCooldownReady(long delayMillis) {
      if (minecraftClient.player == null) {
         return false;
      } else if (AttackAura.getInstance().canDealSmashDamage() || TriggerBot.getInstance().canDealSmashDamage()) {
         return !minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getMainHandStack());
      } else if (minecraftClient.player.fallDistance > (1.5D)) {
         return !minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getMainHandStack())
            && ticksSinceAttack > (3L) + delayMillis / (50L);
      } else {
         return minecraftClient.player.getMainHandStack().isOf(Items.MACE)
            ? !minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getMainHandStack())
               && minecraftClient.player.getAttackCooldownProgress((0.5F) + (float)Math.max(0L, -delayMillis) / (50.0F)) >= (0.9F)
            : minecraftClient.player.getAttackCooldownProgress((0.5F) + (float)Math.max(0L, -delayMillis) / (50.0F)) >= (0.9F)
               && ticksSinceAttack >= (10L) + delayMillis / (50L);
      }
   }

   public static boolean shouldPauseMovementForCritical(LivingEntity target, float[] attackRanges, String sprintMode) {
      if (("Быстрый").equals(sprintMode)) {
         if (target != null
            && canAttack(target, false, false, false, (-50L), attackRanges)
            && !minecraftClient.player.isOnGround()
            && !minecraftClient.player.isSubmergedInWater()
            && minecraftClient.player.getVelocity().y <= (0.0030162615090425808D)) {
            return true;
         }
      } else if (("Легитный").equals(sprintMode)
         && target != null
         && canAttack(target, false, false, false, (-150L), attackRanges)
         && !minecraftClient.player.isOnGround()
         && !minecraftClient.player.isSubmergedInWater()
         && minecraftClient.player.getVelocity().y <= (0.16477328182606651D)) {
         return true;
      }

      return false;
   }

   public static boolean hasAttackDelayElapsed(long delayMillis) {
      return ticksSinceAttack * (50L) >= delayMillis;
   }
}
