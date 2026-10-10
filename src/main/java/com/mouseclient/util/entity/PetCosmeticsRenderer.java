package com.mouseclient.util.entity;

import com.mouseclient.modules.impl.misc.Globals;
import com.mouseclient.render.CosmeticEntry;
import com.mouseclient.render.EquippedCosmetics;
import com.mouseclient.render.JsonCosmeticModel;
import com.mouseclient.util.time.CosmeticPlacement;
import com.mouseclient.util.time.CosmeticCategory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class PetCosmeticsRenderer {
   private static final PetCosmeticsRenderer INSTANCE = new PetCosmeticsRenderer();

   private final HashMap<UUID, PetCosmeticsRenderer.PetState> petsByPlayer = new HashMap<>();
   private final HashSet<UUID> visiblePlayers = new HashSet<>();
   private Object lastWorld;

   private boolean fallToGround(PetCosmeticsRenderer.PetState pet, double groundY) {
      pet.fallSpeed = Math.min((0.85D), pet.fallSpeed + (0.08D));
      pet.y = Math.max(groundY, pet.y - pet.fallSpeed);
      if (pet.y > groundY + (0.001D)) {
         return true;
      }

      pet.y = groundY;
      pet.fallSpeed = 0.0;
      return false;
   }

   private static Vec3d followOffset(Vec3d ownerPosition, float bodyYaw, boolean flying) {
      double yawRadians = Math.toRadians(bodyYaw);
      return flying
         ? new Vec3d(ownerPosition.x + Math.cos(yawRadians - (1.5707963267948966D)) * (1.25D), ownerPosition.y, ownerPosition.z + Math.sin(yawRadians - (1.5707963267948966D)) * (1.25D))
         : new Vec3d(ownerPosition.x + Math.sin(yawRadians) * (1.75D), ownerPosition.y, ownerPosition.z - Math.cos(yawRadians) * (1.75D));
   }

   public static PetCosmeticsRenderer getInstance() {
      return INSTANCE;
   }

   private double raycastGroundHeight(MinecraftClient client, Vec3d start, Vec3d end) {
      BlockHitResult hitResult = client.world.raycast(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, client.player));
      if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() != Type.MISS) {
         BlockPos groundPos = blockHit.getBlockPos();
         BlockPos abovePos = groundPos.up();
         if (client.world.getBlockState(groundPos).getCollisionShape(client.world, groundPos).isEmpty()) {
            return Double.NaN;
         } else {
            return client.world.getBlockState(abovePos).getCollisionShape(client.world, abovePos).isEmpty()
                  && client.world.getBlockState(abovePos.up()).getCollisionShape(client.world, abovePos.up()).isEmpty()
               ? hitResult.getPos().y
               : Double.NaN;
         }
      } else {
         return Double.NaN;
      }
   }

   private Vec3d findNearbyGround(MinecraftClient client, double x, double z, double referenceY) {
      for (int radius = 0; radius <= 4; radius++) {
         for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
               if (radius <= 0 || Math.abs(offsetX) == radius || Math.abs(offsetZ) == radius) {
                  double sampleX = radius == 0 ? x : Math.floor(x) + offsetX + (0.5D);
                  double sampleZ = radius == 0 ? z : Math.floor(z) + offsetZ + (0.5D);
                  double groundY = this.findGroundHeight(client, sampleX, sampleZ, referenceY);
                  if (!Double.isNaN(groundY)) {
                     return new Vec3d(sampleX, groundY, sampleZ);
                  }
               }
            }
         }
      }

      return null;
   }

   public void tick(MinecraftClient client) {
      if (client.player != null && client.world != null) {
         if (this.lastWorld != client.world) {
            this.petsByPlayer.clear();
            this.lastWorld = client.world;
         }

         java.util.HashSet<java.util.UUID> visibleIds = this.visiblePlayers;
         visibleIds.clear();

         for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            CosmeticEntry entry = player == client.player
               ? EquippedCosmetics.equippedEntry(CosmeticCategory.PET)
               : (
                  !player.isSpectator() && !player.isInvisible() && Globals.getInstance().hasSyncSnapshot()
                     ? Globals.getInstance().syncedCosmetic(player.getGameProfile().name(), CosmeticCategory.PET)
                     : null
               );
            if (entry != null && entry.placement() != null && entry.placement().isPet()) {
               visibleIds.add(player.getUuid());
               PetCosmeticsRenderer.PetState pet = this.petsByPlayer.get(player.getUuid());
               if (pet == null || pet.cosmetic.id() != entry.id()) {
                  Vec3d spawnPosition = followOffset(player.getEntityPos(), player.bodyYaw, entry.placement().flying());
                  double spawnY = entry.placement().flying() ? player.getY() + (1.3D) : this.groundHeightOrFallback(client, spawnPosition.x, spawnPosition.z, player.getY());
                  pet = new PetCosmeticsRenderer.PetState(entry, player.getUuid().getLeastSignificantBits() ^ entry.id(), spawnPosition.x, spawnY, spawnPosition.z, player.bodyYaw);
                  this.petsByPlayer.put(player.getUuid(), pet);
               }

               this.updatePet(client, player, pet);
            }
         }

         this.petsByPlayer.keySet().retainAll(visibleIds);
      } else {
         this.petsByPlayer.clear();
         this.lastWorld = null;
      }
   }

   private double groundHeightOrFallback(MinecraftClient client, double x, double z, double referenceY) {
      double groundY = this.findGroundHeight(client, x, z, referenceY);
      return Double.isNaN(groundY) ? referenceY : groundY;
   }

   private boolean moveToward(MinecraftClient client, PetCosmeticsRenderer.PetState pet, Vec3d target, double speed, boolean flying) {
      double deltaX = target.x - pet.x;
      double deltaZ = target.z - pet.z;
      double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
      boolean falling = false;
      if (!flying) {
         double groundY = this.findGroundBelow(client, pet.x, pet.z, pet.y);
         if (!Double.isNaN(groundY) && groundY < pet.y - (0.02D)) {
            falling = this.fallToGround(pet, groundY);
         } else if (!Double.isNaN(groundY)) {
            pet.y = groundY;
            pet.fallSpeed = 0.0;
         }
      }

      if (distance > (15.0D)) {
         if (flying) {
            pet.teleport(target.x, target.y, target.z);
         } else {
            Vec3d teleportPosition = this.findNearbyGround(client, target.x, target.z, target.y);
            if (teleportPosition != null) {
               pet.teleport(teleportPosition.x, teleportPosition.y, teleportPosition.z);
            }
         }

         return false;
      } else {
         boolean moved = false;
         if (distance > (0.12D)) {
            double step = Math.min(speed, distance);
            double directionX = deltaX / distance;
            double directionZ = deltaZ / distance;
            if (flying) {
               pet.x += directionX * step;
               pet.z += directionZ * step;
               pet.yaw = pet.yaw + MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-deltaX, deltaZ)) - pet.yaw) * (0.28F);
               moved = true;
            } else {
               moved = this.tryGroundStep(client, pet, pet.x + directionX * step, pet.z + directionZ * step);
               if (!moved) {
                  moved = this.tryGroundStep(
                     client, pet, pet.x - directionZ * step * pet.avoidanceDirection, pet.z + directionX * step * pet.avoidanceDirection
                  );
               }

               if (!moved) {
                  pet.avoidanceDirection = -pet.avoidanceDirection;
                  moved = this.tryGroundStep(
                     client, pet, pet.x - directionZ * step * pet.avoidanceDirection, pet.z + directionX * step * pet.avoidanceDirection
                  );
               }
            }
         }

         if (flying) {
            pet.fallSpeed = 0.0;
            pet.y = pet.y + MathHelper.clamp(target.y - pet.y, (-0.32D), (0.32D));
         }

         if (!moved && !falling && distance > (0.12D)) {
            pet.wanderTicks = Math.min(pet.wanderTicks, 5);
         }

         return moved || falling;
      }
   }

   public void render(MatrixStack matrices, VertexConsumerProvider consumers, MinecraftClient client) {
      if (client.player != null && client.world != null && matrices != null && consumers != null) {
         float tickDelta = client.getRenderTickCounter().getTickProgress(false);
         Vec3d cameraPosition = client.gameRenderer.getCamera().getCameraPos();

         for (PetCosmeticsRenderer.PetState pet : this.petsByPlayer.values()) {
            double renderX = MathHelper.lerp(tickDelta, pet.previousX, pet.x) - cameraPosition.x;
            double renderY = MathHelper.lerp(tickDelta, pet.previousY, pet.y) - cameraPosition.y;
            double renderZ = MathHelper.lerp(tickDelta, pet.previousZ, pet.z) - cameraPosition.z;
            JsonCosmeticModel model = JsonCosmeticModel.getOrRequest(pet.cosmetic);
            if (model != null) {
               CosmeticPlacement placement = pet.cosmetic.placement();
               float yaw = pet.previousYaw + MathHelper.wrapDegrees(pet.yaw - pet.previousYaw) * tickDelta;
               float scale = Math.max((0.05F), placement.scale());
               matrices.push();
               matrices.translate(renderX, renderY, renderZ);
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((180.0F) - yaw));
               matrices.translate(placement.x(), placement.y(), placement.z());
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(placement.rotationX()));
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(placement.rotationY()));
               matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(placement.rotationZ()));
               matrices.scale(scale, scale, scale);
               model.render(matrices, consumers, (15728880), (pet.age + tickDelta) / (20.0F), MathHelper.lerp(tickDelta, pet.previousMovementBlend, pet.movementBlend));
               matrices.pop();
            }
         }
      }
   }

   private double findGroundHeight(MinecraftClient client, double x, double z, double referenceY) {
      return this.raycastGroundHeight(client, new Vec3d(x, referenceY + (4.0D), z), new Vec3d(x, referenceY - (12.0D), z));
   }

   private static double horizontalDistanceSquared(Vec3d first, Vec3d second) {
      double deltaX = first.x - second.x;
      double deltaZ = first.z - second.z;
      return deltaX * deltaX + deltaZ * deltaZ;
   }

   private boolean tryGroundStep(MinecraftClient client, PetCosmeticsRenderer.PetState pet, double nextX, double nextZ) {
      double groundY = this.findGroundHeight(client, nextX, nextZ, pet.y);
      if (Double.isNaN(groundY) || groundY < pet.y - (1.25D)) {
         groundY = this.findGroundBelow(client, nextX, nextZ, pet.y);
      }

      if (!Double.isNaN(groundY) && !(groundY > pet.y + (1.25D))) {
         double deltaX = nextX - pet.x;
         double deltaZ = nextZ - pet.z;
         pet.x = nextX;
         pet.z = nextZ;
         if (groundY < pet.y - (1.25D)) {
            this.fallToGround(pet, groundY);
         } else {
            pet.y = groundY;
            pet.fallSpeed = 0.0;
         }

         pet.yaw = pet.yaw + MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-deltaX, deltaZ)) - pet.yaw) * (0.28F);
         return true;
      } else {
         return false;
      }
   }

   private double findGroundBelow(MinecraftClient client, double x, double z, double referenceY) {
      return this.raycastGroundHeight(client, new Vec3d(x, referenceY + (0.35D), z), new Vec3d(x, referenceY - (64.0D), z));
   }

   private Vec3d wanderTarget(PetCosmeticsRenderer.PetState pet, Vec3d ownerPosition, double minimumRadius, double maximumRadius) {
      if (--pet.wanderTicks <= 0 || horizontalDistanceSquared(new Vec3d(pet.wanderX, ownerPosition.y, pet.wanderZ), ownerPosition) > maximumRadius * maximumRadius) {
         double angle = pet.random.nextDouble() * (3.141592653589793D) * (2.0D);
         double radius = minimumRadius + pet.random.nextDouble() * (maximumRadius - minimumRadius);
         pet.wanderX = ownerPosition.x + Math.cos(angle) * radius;
         pet.wanderZ = ownerPosition.z + Math.sin(angle) * radius;
         pet.wanderTicks = 45 + pet.random.nextInt(90);
      }

      return new Vec3d(pet.wanderX, ownerPosition.y, pet.wanderZ);
   }

   private void updatePet(MinecraftClient client, AbstractClientPlayerEntity player, PetCosmeticsRenderer.PetState pet) {
      boolean flying = pet.cosmetic.placement().flying();
      Vec3d ownerPosition = player.getEntityPos();
      boolean ownerMoved = pet.previousOwnerPosition != null && horizontalDistanceSquared(ownerPosition, pet.previousOwnerPosition) > (4.0E-4D);
      pet.previousOwnerPosition = ownerPosition;
      if (ownerMoved) {
         pet.ownerBodyYaw = player.bodyYaw;
      }

      Vec3d followPosition = followOffset(ownerPosition, pet.ownerBodyYaw, flying);
      if (flying) {
         followPosition = new Vec3d(followPosition.x, ownerPosition.y + (1.3D), followPosition.z);
      }

      pet.previousX = pet.x;
      pet.previousY = pet.y;
      pet.previousZ = pet.z;
      pet.previousYaw = pet.yaw;
      pet.previousMovementBlend = pet.movementBlend;
      pet.age++;
      double ownerDistanceSquared = horizontalDistanceSquared(new Vec3d(pet.x, pet.y, pet.z), ownerPosition);
      double followStartDistance = flying ? (4.0D) : (7.0D);
      double followStopDistance = flying ? (3.5D) : (6.0D);
      if (ownerDistanceSquared > followStartDistance * followStartDistance) {
         pet.followingOwner = true;
      } else if (ownerDistanceSquared < followStopDistance * followStopDistance) {
         pet.followingOwner = false;
      }

      Vec3d target = pet.followingOwner ? followPosition : this.wanderTarget(pet, ownerPosition, flying ? (3.0D) : (1.4D), flying ? (4.0D) : (6.5D));
      if (flying) {
         target = new Vec3d(target.x, ownerPosition.y + (1.3D), target.z);
      }

      boolean moving = this.moveToward(client, pet, target, pet.followingOwner ? (0.22D) : (0.075D), flying);
      pet.movementBlend = pet.movementBlend + ((moving ? 1.0F : 0.0F) - pet.movementBlend) * (0.25F);
   }

   private static final class PetState {
      private final CosmeticEntry cosmetic;
      private final Random random;
      private double x;
      private double y;
      private double z;
      private double previousX;
      private double previousY;
      private double previousZ;
      private double wanderX;
      private double wanderZ;
      private double fallSpeed;
      private double avoidanceDirection;
      private float yaw;
      private float previousYaw;
      private float ownerBodyYaw;
      private float movementBlend;
      private float previousMovementBlend;
      private int wanderTicks;
      private int age;
      private int unusedCounter;
      private final int unusedVariant;
      private double unusedDoubleA;
      private double unusedDoubleB;
      private double unusedDoubleC;
      private double unusedDoubleD;
      private boolean followingOwner;
      private boolean unusedFlag;
      private Vec3d previousOwnerPosition;

      private void teleport(double x, double y, double z) {
         this.x = this.previousX = this.wanderX = x;
         this.y = this.previousY = y;
         this.z = this.previousZ = this.wanderZ = z;
         this.fallSpeed = 0.0;
      }

      private void savePreviousPose() {
         this.previousX = this.x;
         this.previousY = this.y;
         this.previousZ = this.z;
         this.previousYaw = this.yaw;
         this.previousMovementBlend = this.movementBlend;
      }

      private PetState(CosmeticEntry cosmetic, long seed, double x, double y, double z, float yaw) {
         this.cosmetic = cosmetic;
         this.random = new Random(seed);
         this.unusedVariant = (int)(seed & (2147483647L)) % 4;
         this.x = this.previousX = this.wanderX = x;
         this.y = this.previousY = y;
         this.z = this.previousZ = this.wanderZ = z;
         this.yaw = this.previousYaw = this.ownerBodyYaw = yaw;
         this.avoidanceDirection = this.random.nextBoolean() ? 1.0 : (-1.0D);
      }
   }
}
