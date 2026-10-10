package com.mouseclient.util.entity;

import com.mouseclient.mixin.EntityAccessor;
import com.mouseclient.mixin.LivingEntityAccessor;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;

public class PlayerMovementSimulation implements PlayerSimulation, MinecraftAccess {
   public final PlayerEntity sourcePlayer;
   public final PlayerMovementSimulation.SimulatedPlayerInput input;
   public Vec3d position;
   public Vec3d velocity;
   public Box boundingBox;
   public float yaw;
   public float pitch;
   public boolean sprinting;
   public float fallDistance;
   public int jumpCooldown;
   public boolean jumping;
   public boolean gliding;
   public boolean onGround;
   public boolean horizontalCollision;
   public boolean verticalCollision;
   public boolean touchingWater;
   public boolean swimming;
   public boolean submergedInWater;
   private final Object2DoubleMap<TagKey<Fluid>> fluidHeights;
   private final HashSet<TagKey<Fluid>> fluidsAtEyes;
   private int simulatedTicks = 0;
   private boolean movementRestrictedAtEdge = false;

   private Vec3d adjustMovementForCollisions(Vec3d movement) {
      Box collisionBounds = new Box((-0.3D), 0.0, (-0.3D), (0.3D), (1.8D), (0.3D)).offset(this.position);
      List<net.minecraft.util.shape.VoxelShape> extraCollisionShapes = Collections.emptyList();
      Vec3d adjustedMovement;
      if (movement.lengthSquared() == 0.0) {
         adjustedMovement = movement;
      } else {
         adjustedMovement = Entity.adjustMovementForCollisions(this.sourcePlayer, movement, collisionBounds, this.sourcePlayer.getEntityWorld(), extraCollisionShapes);
      }

      boolean blockedX = movement.x != adjustedMovement.x;
      boolean blockedY = movement.y != adjustedMovement.y;
      boolean blockedZ = movement.z != adjustedMovement.z;
      boolean canStep = this.onGround || blockedY && movement.y < 0.0;
      if (this.sourcePlayer.getStepHeight() > 0.0F && canStep && (blockedX || blockedZ)) {
         Vec3d stepMovement = Entity.adjustMovementForCollisions(
            this.sourcePlayer, new Vec3d(movement.x, this.sourcePlayer.getStepHeight(), movement.z), collisionBounds, this.sourcePlayer.getEntityWorld(), extraCollisionShapes
         );
         Vec3d verticalStep = Entity.adjustMovementForCollisions(
            this.sourcePlayer,
            new Vec3d(0.0, this.sourcePlayer.getStepHeight(), 0.0),
            collisionBounds.stretch(movement.x, 0.0, movement.z),
            this.sourcePlayer.getEntityWorld(),
            extraCollisionShapes
         );
         Vec3d alternateStep = Entity.adjustMovementForCollisions(
               this.sourcePlayer, new Vec3d(movement.x, 0.0, movement.z), collisionBounds.offset(verticalStep), this.sourcePlayer.getEntityWorld(), extraCollisionShapes
            )
            .add(verticalStep);
         if (verticalStep.y < this.sourcePlayer.getStepHeight() && alternateStep.horizontalLengthSquared() > stepMovement.horizontalLengthSquared()) {
            stepMovement = alternateStep;
         }

         if (stepMovement.horizontalLengthSquared() > adjustedMovement.horizontalLengthSquared()) {
            return stepMovement.add(
               Entity.adjustMovementForCollisions(
                  this.sourcePlayer, new Vec3d(0.0, -stepMovement.y + movement.y, 0.0), collisionBounds.offset(stepMovement), this.sourcePlayer.getEntityWorld(), extraCollisionShapes
               )
            );
         }
      }

      return adjustedMovement;
   }

   private void travel(Vec3d movementInput) {
      if (this.swimming && !this.sourcePlayer.hasVehicle()) {
         double swimLookY = this.rotationVector().y;
         double swimVerticalResponse = swimLookY < (-0.2D) ? (0.085D) : (0.06D);
         BlockPos position = new BlockPos(MathHelper.floor(this.position.x), MathHelper.floor(this.position.y + 1.0 - (0.1D)), MathHelper.floor(this.position.z));
         if (swimLookY <= 0.0
            || this.input.playerInput.jump()
            || !this.sourcePlayer.getEntityWorld().getBlockState(position).getFluidState().isEmpty()) {
            this.velocity = this.velocity.add(0.0, (swimLookY - this.velocity.y) * swimVerticalResponse, 0.0);
         }
      }

      double previousVerticalVelocity = this.velocity.y;
      double gravity = (0.08D);
      boolean falling = this.velocity.y <= 0.0;
      if (this.velocity.y <= 0.0 && this.hasActiveStatusEffect(StatusEffects.SLOW_FALLING)) {
         gravity = (0.01D);
         this.resetFallDistance();
      }

      if (this.isTouchingWater() && this.sourcePlayer.shouldSwimInFluids()) {
         double waterStartY = this.position.y;
         float waterDrag = this.isSprinting() ? (0.9F) : (0.8F);
         float waterAcceleration = (0.02F);
         float waterEfficiency = (float)this.getAttributeValue(EntityAttributes.WATER_MOVEMENT_EFFICIENCY);
         if (!this.onGround) {
            waterEfficiency *= (0.5F);
         }

         if (waterEfficiency > 0.0F) {
            waterDrag += ((0.54600006F) - waterDrag) * waterEfficiency / (3.0F);
            waterAcceleration += (this.baseMovementSpeed() - waterAcceleration) * waterEfficiency / (3.0F);
         }

         if (this.hasActiveStatusEffect(StatusEffects.DOLPHINS_GRACE)) {
            waterDrag = (0.96F);
         }

         this.addInputVelocity(waterAcceleration, movementInput);
         this.move(this.velocity);
         Vec3d waterVelocity = this.velocity;
         if (this.horizontalCollision && this.isClimbing()) {
            waterVelocity = new Vec3d(waterVelocity.x, (0.2D), waterVelocity.z);
         }

         this.velocity = waterVelocity.multiply(waterDrag, (0.8D), waterDrag);
         Vec3d fluidVelocity = this.sourcePlayer.applyFluidMovingSpeed(gravity, falling, this.velocity);
         this.velocity = fluidVelocity;
         if (this.horizontalCollision && this.canMoveInto(fluidVelocity.x, fluidVelocity.y + (0.6D) - this.position.y + waterStartY, fluidVelocity.z)) {
            this.velocity = new Vec3d(fluidVelocity.x, (0.3D), fluidVelocity.z);
         }
      } else if (this.isInLava() && this.sourcePlayer.shouldSwimInFluids()) {
         double lavaStartY = this.position.y;
         this.addInputVelocity((0.02F), movementInput);
         this.move(this.velocity);
         if (this.fluidHeight(FluidTags.LAVA) <= this.swimHeight()) {
            this.velocity = this.velocity.multiply((0.5D), (0.8D), (0.5D));
            this.velocity = this.sourcePlayer.applyFluidMovingSpeed(gravity, falling, this.velocity);
         } else {
            this.velocity = this.velocity.multiply((0.5D));
         }

         if (!this.sourcePlayer.hasNoGravity()) {
            this.velocity = this.velocity.add(0.0, -gravity / (4.0D), 0.0);
         }

         if (this.horizontalCollision && this.canMoveInto(this.velocity.x, this.velocity.y + (0.6D) - this.position.y + lavaStartY, this.velocity.z)) {
            this.velocity = new Vec3d(this.velocity.x, (0.3D), this.velocity.z);
         }
      } else if (this.gliding) {
         Vec3d glidingVelocity = this.velocity;
         if (glidingVelocity.y > (-0.5D)) {
            this.fallDistance = 1.0F;
         }

         Vec3d lookDirection = this.rotationVector();
         float pitchRadians = this.pitch * (0.017453292F);
         double horizontalLookLength = Math.sqrt(lookDirection.x * lookDirection.x + lookDirection.z * lookDirection.z);
         double horizontalSpeed = this.velocity.horizontalLength();
         double lookLength = lookDirection.length();
         float liftFactor = MathHelper.cos(pitchRadians);
         liftFactor = (float)(liftFactor * (liftFactor * Math.min(1.0, lookLength / (0.4D))));
         glidingVelocity = this.velocity.add(0.0, gravity * ((-1.0D) + liftFactor * (0.75D)), 0.0);
         if (glidingVelocity.y < 0.0 && horizontalLookLength > 0.0) {
            double downwardLift = glidingVelocity.y * (-0.1D) * liftFactor;
            glidingVelocity = glidingVelocity.add(lookDirection.x * downwardLift / horizontalLookLength, downwardLift, lookDirection.z * downwardLift / horizontalLookLength);
         }

         if (pitchRadians < 0.0F && horizontalLookLength > 0.0) {
            double upwardLift = horizontalSpeed * -MathHelper.sin(pitchRadians) * (0.04D);
            glidingVelocity = glidingVelocity.add(-lookDirection.x * upwardLift / horizontalLookLength, upwardLift * (3.2D), -lookDirection.z * upwardLift / horizontalLookLength);
         }

         if (horizontalLookLength > 0.0) {
            glidingVelocity = glidingVelocity.add((lookDirection.x / horizontalLookLength * horizontalSpeed - glidingVelocity.x) * (0.1D), 0.0, (lookDirection.z / horizontalLookLength * horizontalSpeed - glidingVelocity.z) * (0.1D));
         }

         this.velocity = glidingVelocity.multiply((0.99D), (0.98D), (0.99D));
         this.move(this.velocity);
      } else {
         BlockPos groundPos = this.slipperinessBlockPos();
         float slipperiness = this.sourcePlayer.getEntityWorld().getBlockState(groundPos).getBlock().getSlipperiness();
         float horizontalDrag = this.onGround ? slipperiness * (0.91F) : (0.91F);
         Vec3d groundVelocity = this.applyGroundMovement(movementInput, slipperiness);
         double verticalVelocity = groundVelocity.y;
         if (this.hasActiveStatusEffect(StatusEffects.LEVITATION)) {
            StatusEffectInstance effect = this.getActiveStatusEffect(StatusEffects.LEVITATION);
            if (effect != null) {
               verticalVelocity += ((0.05D) * (effect.getAmplifier() + 1) - groundVelocity.y) * (0.2D);
            }
         } else if (this.sourcePlayer.getEntityWorld().isClient() && !this.sourcePlayer.getEntityWorld().isChunkLoaded(groundPos)) {
            verticalVelocity = this.position.y > this.sourcePlayer.getEntityWorld().getBottomY() ? (-0.1D) : 0.0;
         } else if (!this.sourcePlayer.hasNoGravity()) {
            verticalVelocity -= gravity;
         }

         if (this.sourcePlayer.hasNoDrag()) {
            this.velocity = new Vec3d(groundVelocity.x, verticalVelocity, groundVelocity.z);
         } else {
            this.velocity = new Vec3d(groundVelocity.x * horizontalDrag, verticalVelocity * (0.9800000190734863D), groundVelocity.z * horizontalDrag);
         }
      }

      if (this.sourcePlayer.getAbilities().flying && !this.sourcePlayer.hasVehicle()) {
         this.velocity = new Vec3d(this.velocity.x, previousVerticalVelocity * (0.6D), this.velocity.z);
         this.resetFallDistance();
      }
   }

   public PlayerMovementSimulation(
      PlayerEntity sourcePlayer,
      PlayerMovementSimulation.SimulatedPlayerInput input,
      Vec3d position,
      Vec3d velocity,
      Box boundingBox,
      float yaw,
      float pitch,
      boolean sprinting,
      float fallDistance,
      int jumpCooldown,
      boolean jumping,
      boolean gliding,
      boolean onGround,
      boolean horizontalCollision,
      boolean verticalCollision,
      boolean touchingWater,
      boolean swimming,
      boolean submergedInWater,
      Object2DoubleMap<TagKey<Fluid>> fluidHeights,
      HashSet<TagKey<Fluid>> fluidsAtEyes
   ) {
      this.sourcePlayer = sourcePlayer;
      this.input = input;
      this.position = position;
      this.velocity = velocity;
      this.boundingBox = boundingBox;
      this.yaw = yaw;
      this.pitch = pitch;
      this.sprinting = sprinting;
      this.fallDistance = fallDistance;
      this.jumpCooldown = jumpCooldown;
      this.jumping = jumping;
      this.gliding = gliding;
      this.onGround = onGround;
      this.horizontalCollision = horizontalCollision;
      this.verticalCollision = verticalCollision;
      this.touchingWater = touchingWater;
      this.swimming = swimming;
      this.submergedInWater = submergedInWater;
      this.fluidHeights = fluidHeights;
      this.fluidsAtEyes = fluidsAtEyes;
   }

   private void addInputVelocity(float speed, Vec3d movementInput) {
      if (!(movementInput.lengthSquared() < (1.0E-7D))) {
         Vec3d scaledInput = (movementInput.lengthSquared() > 1.0 ? movementInput.normalize() : movementInput).multiply(speed);
         this.velocity = this.velocity
            .add(
               scaledInput.x * MathHelper.cos(this.yaw * (0.017453292F)) - scaledInput.z * MathHelper.sin(this.yaw * (0.017453292F)),
               scaledInput.y,
               scaledInput.z * MathHelper.cos(this.yaw * (0.017453292F)) + scaledInput.x * MathHelper.sin(this.yaw * (0.017453292F))
            );
      }
   }

   private StatusEffectInstance getActiveStatusEffect(RegistryEntry<StatusEffect> statusEffect) {
      StatusEffectInstance effect = this.sourcePlayer.getStatusEffect(statusEffect);
      return effect != null && effect.getDuration() >= this.simulatedTicks ? effect : null;
   }

   private void move(Vec3d movement) {
      Vec3d edgeAdjustedMovement = movement;
      edgeAdjustedMovement = this.adjustMovementForSneaking(edgeAdjustedMovement);
      Vec3d adjustedMovement = this.adjustMovementForCollisions(edgeAdjustedMovement);
      if (adjustedMovement.lengthSquared() > (1.0E-7D)) {
         this.position = this.position.add(adjustedMovement);
         this.boundingBox = this.sourcePlayer.getDimensions(this.sourcePlayer.getPose()).getBoxAt(this.position);
      }

      boolean blockedX = !MathHelper.approximatelyEquals(movement.x, adjustedMovement.x);
      boolean blockedZ = !MathHelper.approximatelyEquals(movement.z, adjustedMovement.z);
      this.horizontalCollision = blockedX || blockedZ;
      this.verticalCollision = movement.y != adjustedMovement.y;
      this.onGround = this.verticalCollision && movement.y < 0.0;
      if (!this.isTouchingWater()) {
         this.updateWaterState();
      }

      if (this.onGround) {
         this.resetFallDistance();
      } else if (movement.y < 0.0) {
         this.fallDistance = this.fallDistance - (float)movement.y;
      }

      Vec3d previousVelocity = this.velocity;
      if (this.horizontalCollision || this.verticalCollision) {
         this.velocity = new Vec3d(blockedX ? 0.0 : previousVelocity.x, this.onGround ? 0.0 : previousVelocity.y, blockedZ ? 0.0 : previousVelocity.z);
      }
   }

   private Vec3d applyGroundMovement(Vec3d movementInput, float slipperiness) {
      this.addInputVelocity(this.movementSpeedForSlipperiness(slipperiness), movementInput);
      this.velocity = this.limitClimbingVelocity(this.velocity);
      this.move(this.velocity);
      Vec3d nextVelocity = this.velocity;
      BlockPos currentBlockPos = this.blockPos(this.position);
      BlockState blockState = this.getBlockState(currentBlockPos);
      if ((this.horizontalCollision || this.jumping)
         && (this.isClimbing() || blockState != null && blockState.isOf(Blocks.POWDER_SNOW) && PowderSnowBlock.canWalkOnPowderSnow(this.sourcePlayer))) {
         nextVelocity = new Vec3d(nextVelocity.x, (0.2D), nextVelocity.z);
      }

      return nextVelocity;
   }

   private float jumpVelocity() {
      return (0.42F) * this.jumpVelocityMultiplier() + this.jumpBoostVelocity();
   }

   public static PlayerMovementSimulation predictLocalPlayer(int ticks) {
      PlayerMovementSimulation simulation = snapshotLocalPlayer(PlayerMovementSimulation.SimulatedPlayerInput.fromClientPlayer(minecraftClient.player.input.playerInput));

      for (int var2 = 0; var2 < ticks; var2++) {
         simulation.tick();
      }

      return simulation;
   }

   private Vec3d adjustMovementForSneaking(Vec3d movement) {
      if (movement.y <= 0.0 && this.isCloseToGround()) {
         double adjustedX = movement.x;
         double adjustedZ = movement.z;

         double edgeStep;
         for (edgeStep = (0.05D);
            adjustedX != 0.0 && this.sourcePlayer.getEntityWorld().isSpaceEmpty(this.sourcePlayer, this.boundingBox.offset(adjustedX, (-0.5D), 0.0));
            adjustedX += adjustedX > 0.0 ? -edgeStep : edgeStep
         ) {
            if (adjustedX < edgeStep && adjustedX >= -edgeStep) {
               adjustedX = 0.0;
               break;
            }
         }

         while (adjustedZ != 0.0 && this.sourcePlayer.getEntityWorld().isSpaceEmpty(this.sourcePlayer, this.boundingBox.offset(0.0, (-0.5D), adjustedZ))) {
            if (adjustedZ < edgeStep && adjustedZ >= -edgeStep) {
               adjustedZ = 0.0;
               break;
            }

            adjustedZ += adjustedZ > 0.0 ? -edgeStep : edgeStep;
         }

         while (adjustedX != 0.0 && adjustedZ != 0.0 && this.sourcePlayer.getEntityWorld().isSpaceEmpty(this.sourcePlayer, this.boundingBox.offset(adjustedX, (-0.5D), adjustedZ))) {
            adjustedX = adjustedX < edgeStep && adjustedX >= -edgeStep ? 0.0 : (adjustedX > 0.0 ? adjustedX - edgeStep : adjustedX + edgeStep);
            if (adjustedZ < edgeStep && adjustedZ >= -edgeStep) {
               adjustedZ = 0.0;
               break;
            }

            adjustedZ += adjustedZ > 0.0 ? -edgeStep : edgeStep;
         }

         if (movement.x != adjustedX || movement.z != adjustedZ) {
            this.movementRestrictedAtEdge = true;
         }

         if (this.isSneakingForEdge()) {
            movement = new Vec3d(adjustedX, movement.y, adjustedZ);
         }
      }

      return movement;
   }

   private boolean updateFluidHeightAndPush(TagKey<Fluid> fluidTag, double pushStrength) {
      if (this.isRegionUnloaded()) {
         return false;
      }

      Box fluidBounds = this.boundingBox.contract((0.001D));
      int minX = MathHelper.floor(fluidBounds.minX);
      int maxX = MathHelper.ceil(fluidBounds.maxX);
      int minY = MathHelper.floor(fluidBounds.minY);
      int maxY = MathHelper.ceil(fluidBounds.maxY);
      int minZ = MathHelper.floor(fluidBounds.minZ);
      int maxZ = MathHelper.ceil(fluidBounds.maxZ);
      double maximumHeight = 0.0;
      boolean applyPush = true;
      boolean touchesFluid = false;
      Vec3d flow = Vec3d.ZERO;
      int flowSamples = 0;
      Mutable samplePos = new Mutable();

      for (int x = minX; x < maxX; x++) {
         for (int y = minY; y < maxY; y++) {
            for (int z = minZ; z < maxZ; z++) {
               samplePos.set(x, y, z);
               FluidState fluidState = this.sourcePlayer.getEntityWorld().getFluidState(samplePos);
               if (fluidState.isIn(fluidTag)) {
                  double surfaceY = y + fluidState.getHeight(this.sourcePlayer.getEntityWorld(), samplePos);
                  if (surfaceY >= fluidBounds.minY) {
                     touchesFluid = true;
                     maximumHeight = Math.max(surfaceY - fluidBounds.minY, maximumHeight);
                     if (applyPush) {
                        Vec3d sampleFlow = fluidState.getVelocity(this.sourcePlayer.getEntityWorld(), samplePos);
                        if (maximumHeight < (0.4D)) {
                           sampleFlow = sampleFlow.multiply(maximumHeight);
                        }

                        flow = flow.add(sampleFlow);
                        flowSamples++;
                     }
                  }
               }
            }
         }
      }

      if (flow.length() > 0.0) {
         if (flowSamples > 0) {
            flow = flow.multiply(1.0 / flowSamples);
         }

         flow = flow.multiply(pushStrength);
         if (Math.abs(this.velocity.x) < (0.003D) && Math.abs(this.velocity.z) < (0.003D) && flow.length() < (0.0045D)) {
            flow = flow.normalize().multiply((0.0045D));
         }

         this.velocity = this.velocity.add(flow);
      }

      this.fluidHeights.put(fluidTag, maximumHeight);
      return touchesFluid;
   }

   public boolean isInLava() {
      return this.fluidHeights.getDouble(FluidTags.LAVA) > 0.0;
   }

   private BlockPos slipperinessBlockPos() {
      return BlockPos.ofFloored(this.position.x, this.boundingBox.minY - (0.5000001D), this.position.z);
   }

   private void updateSubmergedInWater() {
      this.submergedInWater = this.fluidsAtEyes.contains(FluidTags.WATER);
      this.fluidsAtEyes.clear();
      double eyeLevel = this.eyeY() - (0.1111111119389534D);
      if (!(
         this.sourcePlayer.getVehicle() instanceof AbstractBoatEntity boat
            && !boat.isSubmergedInWater()
            && boat.getBoundingBox().maxY >= eyeLevel
            && boat.getBoundingBox().minY <= eyeLevel
      )) {
         BlockPos position = BlockPos.ofFloored(this.position.x, eyeLevel, this.position.z);
         FluidState fluidState = this.sourcePlayer.getEntityWorld().getFluidState(position);
         double fluidSurfaceY = position.getY() + fluidState.getHeight(this.sourcePlayer.getEntityWorld(), position);
         if (fluidSurfaceY > eyeLevel) {
            this.fluidsAtEyes.addAll(fluidState.streamTags().toList());
         }
      }
   }

   public static PlayerMovementSimulation predictPlayer(PlayerEntity player, int ticks) {
      PlayerMovementSimulation simulation = snapshotPlayer(player, PlayerMovementSimulation.SimulatedPlayerInput.guessInput(player));

      for (int var3 = 0; var3 < ticks; var3++) {
         simulation.tick();
      }

      return simulation;
   }

   @Override
   public Vec3d sourcePosition() {
      return this.sourcePlayer.getEntityPos();
   }

   private boolean canMoveInto(double offsetX, double offsetY, double offsetZ) {
      return this.isSpaceEmptyAndDry(this.boundingBox.offset(offsetX, offsetY, offsetZ));
   }

   private boolean canClimbThroughTrapdoor(BlockPos trapdoorPos, BlockState trapdoorState) {
      if (!trapdoorState.get(TrapdoorBlock.OPEN)) {
         return false;
      }

      BlockState ladderState = this.sourcePlayer.getEntityWorld().getBlockState(trapdoorPos.down());
      return ladderState.isOf(Blocks.LADDER) && ladderState.get(LadderBlock.FACING).equals(trapdoorState.get(TrapdoorBlock.FACING));
   }

   private void swimUpward(TagKey<Fluid> unusedFluidTag) {
      this.velocity = this.velocity.add(0.0, (0.03999999910593033D), 0.0);
   }

   private float jumpVelocityMultiplier() {
      float currentMultiplier = 0.0F;
      Block currentBlock = this.getBlockState(this.blockPos(this.position)).getBlock();
      if (currentBlock != null) {
         currentMultiplier = currentBlock.getJumpVelocityMultiplier();
      }

      float groundMultiplier = 0.0F;
      Block groundBlock = this.getBlockState(this.slipperinessBlockPos()).getBlock();
      if (groundBlock != null) {
         groundMultiplier = groundBlock.getJumpVelocityMultiplier();
      }

      return currentMultiplier == 1.0F ? groundMultiplier : currentMultiplier;
   }

   private Vec3d direction(float pitch, float yaw) {
      float pitchRadians = (float)(pitch * (3.141592653589793D) / (180.0D));
      float negativeYawRadians = (float)(-yaw * (3.141592653589793D) / (180.0D));
      float yawCos = MathHelper.cos(negativeYawRadians);
      float yawSin = MathHelper.sin(negativeYawRadians);
      float pitchCos = MathHelper.cos(pitchRadians);
      float pitchSin = MathHelper.sin(pitchRadians);
      return new Vec3d(yawSin * pitchCos, -pitchSin, yawCos * pitchCos);
   }

   public BlockState getBlockState(BlockPos blockPos) {
      return this.sourcePlayer.getEntityWorld().getBlockState(blockPos);
   }

   public BlockPos blockPos(Vec3d worldPosition) {
      return new BlockPos(MathHelper.floor(worldPosition.x), MathHelper.floor(worldPosition.y), MathHelper.floor(worldPosition.z));
   }

   private double swimHeight() {
      return this.sourcePlayer.getStandingEyeHeight() < (0.4D) ? 0.0 : (0.4D);
   }

   private float jumpBoostVelocity() {
      if (this.hasActiveStatusEffect(StatusEffects.JUMP_BOOST)) {
         StatusEffectInstance effect = this.getActiveStatusEffect(StatusEffects.JUMP_BOOST);
         return (0.1F) * (effect.getAmplifier() + 1);
      } else {
         return 0.0F;
      }
   }

   private void updateWaterState() {
      if (this.sourcePlayer.getVehicle() instanceof AbstractBoatEntity boat && !boat.isSubmergedInWater()) {
         this.touchingWater = false;
      } else {
         if (this.updateFluidHeightAndPush(FluidTags.WATER, (0.014D))) {
            this.resetFallDistance();
            this.touchingWater = true;
         } else {
            this.touchingWater = false;
         }
      }
   }

   @Override
   public void tick() {
      this.simulatedTicks++;
      this.movementRestrictedAtEdge = false;
      if (!(this.position.y <= (-70.0D))) {
         this.input.update();
         this.updateWaterState();
         this.updateSubmergedInWater();
         this.updateSwimming();
         if (this.jumpCooldown > 0) {
            this.jumpCooldown--;
         }

         this.jumping = this.input.playerInput.jump();
         double velocityX = this.velocity.x;
         double velocityY = this.velocity.y;
         double velocityZ = this.velocity.z;
         if (Math.abs(this.velocity.x) < (0.003D)) {
            velocityX = 0.0;
         }

         if (Math.abs(this.velocity.y) < (0.003D)) {
            velocityY = 0.0;
         }

         if (Math.abs(this.velocity.z) < (0.003D)) {
            velocityZ = 0.0;
         }

         if (this.onGround) {
            this.gliding = false;
         }

         this.velocity = new Vec3d(velocityX, velocityY, velocityZ);
         if (this.jumping) {
            double currentFluidHeight = this.isInLava() ? this.fluidHeight(FluidTags.LAVA) : this.fluidHeight(FluidTags.WATER);
            boolean inWater = this.isTouchingWater() && currentFluidHeight > 0.0;
            double minimumSwimHeight = this.swimHeight();
            if (!inWater || this.onGround && !(currentFluidHeight > minimumSwimHeight)) {
               if (!this.isInLava() || this.onGround && !(currentFluidHeight > minimumSwimHeight)) {
                  if ((this.onGround || inWater && currentFluidHeight <= minimumSwimHeight) && this.jumpCooldown == 0) {
                     this.jump();
                     if (this.sourcePlayer.equals(minecraftClient.player)) {
                        this.jumpCooldown = 10;
                     }
                  }
               } else {
                  this.swimUpward(FluidTags.LAVA);
               }
            } else {
               this.swimUpward(FluidTags.WATER);
            }
         }

         float strafeInput = this.input.strafe * (0.98F);
         float forwardInput = this.input.forward * (0.98F);
         float verticalInput = 0.0F;
         if (this.hasActiveStatusEffect(StatusEffects.SLOW_FALLING) || this.hasActiveStatusEffect(StatusEffects.LEVITATION)) {
            this.resetFallDistance();
         }

         this.travel(new Vec3d(strafeInput, verticalInput, forwardInput));
      }
   }

   private void resetFallDistance() {
      this.fallDistance = 0.0F;
   }

   private void updateSwimming() {
      if (this.swimming) {
         this.swimming = this.isSprinting() && this.isTouchingWater() && !this.sourcePlayer.hasVehicle();
      } else {
         this.swimming = this.isSprinting()
            && this.isSubmergedInWater()
            && !this.sourcePlayer.hasVehicle()
            && this.sourcePlayer.getEntityWorld().getFluidState(this.blockPos(this.position)).isIn(FluidTags.WATER);
      }
   }

   private boolean isSprinting() {
      return this.sprinting;
   }

   public double getAttributeValue(RegistryEntry<EntityAttribute> attribute) {
      return this.sourcePlayer.getAttributes().getValue(attribute);
   }

   private float movementSpeedForSlipperiness(float slipperiness) {
      return this.onGround ? this.baseMovementSpeed() * ((0.21600002F) / (slipperiness * slipperiness * slipperiness)) : this.airMovementSpeed();
   }

   private float baseMovementSpeed() {
      return (0.1F);
   }

   public boolean hasActiveStatusEffect(RegistryEntry<StatusEffect> statusEffect) {
      StatusEffectInstance effect = this.sourcePlayer.getStatusEffect(statusEffect);
      return effect != null && effect.getDuration() >= this.simulatedTicks;
   }

   public boolean isClimbing() {
      BlockPos position = this.blockPos(this.position);
      BlockState blockState = this.getBlockState(position);
      return blockState.isIn(BlockTags.CLIMBABLE) ? true : blockState.getBlock() instanceof TrapdoorBlock && this.canClimbThroughTrapdoor(position, blockState);
   }

   private Vec3d limitClimbingVelocity(Vec3d movement) {
      if (!this.isClimbing()) {
         return movement;
      }

      this.resetFallDistance();
      double limitedX = MathHelper.clamp(movement.x, (-0.15000000596046448D), (0.15000000596046448D));
      double limitedZ = MathHelper.clamp(movement.z, (-0.15000000596046448D), (0.15000000596046448D));
      double limitedY = Math.max(movement.y, (-0.15000000596046448D));
      if (limitedY < 0.0 && !this.getBlockState(this.blockPos(this.position)).isOf(Blocks.SCAFFOLDING) && this.sourcePlayer.isHoldingOntoLadder()) {
         limitedY = 0.0;
      }

      return new Vec3d(limitedX, limitedY, limitedZ);
   }

   private double fluidHeight(TagKey<Fluid> fluidTag) {
      return this.fluidHeights.getDouble(fluidTag);
   }

   public static PlayerMovementSimulation snapshotPlayer(PlayerEntity player, PlayerMovementSimulation.SimulatedPlayerInput input) {
      return new PlayerMovementSimulation(
         player,
         input,
         player.getEntityPos(),
         player.getEntityPos().subtract(new Vec3d(player.lastX, player.lastY, player.lastZ)),
         player.getBoundingBox(),
         player.getYaw(),
         player.getPitch(),
         player.isSprinting(),
         (float)player.fallDistance,
         ((LivingEntityAccessor)(Object)player).getNoJumpDelay(),
         ((LivingEntityAccessor)(Object)player).getJumping(),
         player.isGliding(),
         player.isOnGround(),
         player.horizontalCollision,
         player.verticalCollision,
         player.isTouchingWater(),
         player.isSwimming(),
         player.isSubmergedInWater(),
         new Object2DoubleArrayMap(((EntityAccessor)(Object)player).getFluidHeight()),
         new HashSet<>(((EntityAccessor)(Object)player).getFluidOnEyes())
      );
   }

   private boolean isRegionUnloaded() {
      Box regionBounds = this.boundingBox.expand(1.0);
      int minX = MathHelper.floor(regionBounds.minX);
      int maxX = MathHelper.ceil(regionBounds.maxX);
      int minZ = MathHelper.floor(regionBounds.minZ);
      int maxZ = MathHelper.ceil(regionBounds.maxZ);
      return !this.sourcePlayer.getEntityWorld().isRegionLoaded(minX, minZ, maxX, maxZ);
   }

   public static PlayerMovementSimulation snapshotLocalPlayer(PlayerMovementSimulation.SimulatedPlayerInput input) {
      ClientPlayerEntity player = minecraftClient.player;
      return new PlayerMovementSimulation(
         player,
         input,
         player.getEntityPos(),
         player.getVelocity(),
         player.getBoundingBox(),
         player.getYaw(),
         player.getPitch(),
         player.isSprinting(),
         (float)player.fallDistance,
         ((LivingEntityAccessor)(Object)player).getNoJumpDelay(),
         ((LivingEntityAccessor)(Object)player).getJumping(),
         player.isGliding(),
         player.isOnGround(),
         player.horizontalCollision,
         player.verticalCollision,
         player.isTouchingWater(),
         player.isSwimming(),
         player.isSubmergedInWater(),
         new Object2DoubleArrayMap(((EntityAccessor)(Object)player).getFluidHeight()),
         new HashSet<>(((EntityAccessor)(Object)player).getFluidOnEyes())
      );
   }

   private double eyeY() {
      return this.position.y + this.sourcePlayer.getStandingEyeHeight();
   }

   private boolean isCloseToGround() {
      return this.onGround
         || this.fallDistance < (0.5D)
            && !this.sourcePlayer.getEntityWorld().isSpaceEmpty(this.sourcePlayer, this.boundingBox.offset(0.0, this.fallDistance - (0.5D), 0.0));
   }

   private Vec3d rotationVector() {
      return this.direction(this.pitch, this.yaw);
   }

   private float airMovementSpeed() {
      float baseSpeed = (0.02F);
      return this.input.playerInput.sprint() ? baseSpeed + (0.006F) : baseSpeed;
   }

   public void jump() {
      this.velocity = this.velocity.add(0.0, this.jumpVelocity() - this.velocity.y, 0.0);
      if (this.isSprinting()) {
         float yawRadians = (float)Math.toRadians(this.yaw);
         this.velocity = this.velocity.add(-MathHelper.sin(yawRadians) * (0.2D), 0.0, MathHelper.cos(yawRadians) * (0.2D));
      }
   }

   public PlayerMovementSimulation copy() {
      return new PlayerMovementSimulation(
         this.sourcePlayer,
         this.input,
         this.position,
         this.velocity,
         this.boundingBox,
         this.yaw,
         this.pitch,
         this.sprinting,
         this.fallDistance,
         this.jumpCooldown,
         this.jumping,
         this.gliding,
         this.onGround,
         this.horizontalCollision,
         this.verticalCollision,
         this.touchingWater,
         this.swimming,
         this.submergedInWater,
         new Object2DoubleArrayMap(this.fluidHeights),
         new HashSet<>(this.fluidsAtEyes)
      );
   }

   private boolean isTouchingWater() {
      return this.touchingWater;
   }

   protected boolean isSneakingForEdge() {
      return this.input.playerInput.sneak() || this.input.forceEdgeSneaking;
   }

   public boolean isSubmergedInWater() {
      return this.submergedInWater && this.isTouchingWater();
   }

   private boolean isSpaceEmptyAndDry(Box bounds) {
      return this.sourcePlayer.getEntityWorld().isSpaceEmpty(this.sourcePlayer, bounds) && !this.sourcePlayer.getEntityWorld().containsFluid(bounds);
   }

   public static class SimulatedPlayerInput extends Input {
      public boolean forceEdgeSneaking = false;
      public float forward;
      public float strafe;
      public PlayerInput playerInput;
      public static final double SPRINT_SPEED_THRESHOLD = 0.121;

      public void update() {
         if (this.playerInput.forward() != this.playerInput.backward()) {
            this.forward = this.playerInput.forward() ? 1.0F : (-1.0F);
         } else {
            this.forward = 0.0F;
         }

         if (this.playerInput.left() == this.playerInput.right()) {
            this.strafe = 0.0F;
         } else {
            this.strafe = this.playerInput.left() ? 1.0F : (-1.0F);
         }

         if (this.playerInput.sneak()) {
            this.strafe = this.strafe * (0.3F);
            this.forward = this.forward * (0.3F);
         }
      }

      public SimulatedPlayerInput(PlayerInput playerInput) {
         this.playerInput = playerInput;
      }

      public static PlayerMovementSimulation.SimulatedPlayerInput guessInput(PlayerEntity player) {
         Vec3d movementDelta = player.getEntityPos().subtract(new Vec3d(player.lastX, player.lastY, player.lastZ));
         double horizontalSpeedSquared = movementDelta.horizontalLengthSquared();
         PlayerInput input = new PlayerInput(false, false, false, false, !player.isOnGround(), player.isSneaking(), horizontalSpeedSquared >= (0.014641D));
         if (horizontalSpeedSquared > (0.0025000000000000005D)) {
            double movementAngle = PlayerMovement.movementYawDifference(movementDelta, player.getYaw());
            double wrappedAngle = MathHelper.wrapDegrees(movementAngle);
            input = PlayerMovement.addKeysForAngle(input, wrappedAngle);
         }

         return new PlayerMovementSimulation.SimulatedPlayerInput(input);
      }

      public static PlayerMovementSimulation.SimulatedPlayerInput fromClientPlayer(PlayerInput input) {
         return new PlayerMovementSimulation.SimulatedPlayerInput(input);
      }

      @Override
      public String toString() {
         return "SimulatedPlayerInput(forwards={"
            + this.playerInput.forward()
            + "}, backwards={"
            + this.playerInput.backward()
            + "}, left={"
            + this.playerInput.left()
            + "}, right={"
            + this.playerInput.right()
            + "}, jumping={"
            + this.playerInput.jump()
            + "}, sprinting="
            + this.playerInput.sprint()
            + ", slowDown="
            + this.playerInput.sneak()
            + ")";
      }
   }
}
