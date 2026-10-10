package com.mouseclient.util.entity;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class EntityRaycast implements MinecraftAccess {

   public static Entity hitTarget(LivingEntity target, double yaw, double pitch, double range) {
      if (target != null && minecraftClient.player != null) {
         Vec3d position = minecraftClient.player.getEyePos();
         Vec3d rayEnd = position.add(direction(pitch, yaw).multiply(range));
         EntityHitResult hitResult = raycast(
            minecraftClient.player, position, rayEnd, target.getBoundingBox().expand(range), entity -> !entity.isSpectator() && entity.canHit(), range
         );
         if (hitResult != null && hitResult.getEntity() == target) {
            return target;
         } else {
            return target.getBoundingBox().raycast(position, rayEnd).isPresent() ? target : null;
         }
      } else {
         return null;
      }
   }

   public static boolean hasLineOfSight(Entity from, Entity to) {
      return from.getEntityWorld()
            .raycast(
               new RaycastContext(
                  new Vec3d(from.getX(), from.getEyeY(), from.getZ()),
                  new Vec3d(to.getX(), to.getEyeY(), to.getZ()),
                  ShapeType.COLLIDER,
                  FluidHandling.NONE,
                  from
               )
            )
            .getType()
         == Type.MISS;
   }

   private EntityRaycast() {
   }

   public static Vec3d direction(double pitch, double yaw) {
      double yawRadians = -yaw * (0.017453292519943295D) - (3.141592653589793D);
      double pitchRadians = -pitch * (0.017453292519943295D);
      return new Vec3d(Math.sin(yawRadians) * -Math.cos(pitchRadians), Math.sin(pitchRadians), Math.cos(yawRadians) * -Math.cos(pitchRadians));
   }

   public static EntityHitResult raycastFromCamera(double range, RotationAngles rotation, Predicate<Entity> predicate) {
      return minecraftClient.getCameraEntity() == null
         ? null
         : raycast(
            minecraftClient.getCameraEntity(),
            minecraftClient.getCameraEntity().getCameraPosVec(1.0F),
            minecraftClient.getCameraEntity().getCameraPosVec(1.0F).add(rotation.direction().multiply(range)),
            minecraftClient.getCameraEntity().getBoundingBox().stretch(rotation.direction().multiply(range)).expand(1.0, 1.0, 1.0),
            entity -> !entity.isSpectator() && predicate.test(entity),
            range
         );
   }

   public static EntityHitResult raycast(Entity source, Vec3d start, Vec3d end, Box searchBounds, Predicate<Entity> predicate, double range) {
      double nearestDistance = range;
      Entity entity = null;
      Vec3d position = null;

      for (Entity candidateEntity : source.getEntityWorld().getOtherEntities(source, searchBounds, predicate)) {
         Box candidateBox = candidateEntity.getBoundingBox();
         Optional candidateHit = candidateBox.raycast(start, end);
         if (candidateBox.contains(start)) {
            if (nearestDistance >= 0.0) {
               entity = candidateEntity;
               position = start;
               nearestDistance = 0.0;
            }
         } else if (candidateHit.isPresent()) {
            double candidateDistance = start.distanceTo((Vec3d)candidateHit.get());
            if (candidateDistance < nearestDistance || nearestDistance == 0.0) {
               if (candidateEntity.getRootVehicle() == source.getRootVehicle()) {
                  if (nearestDistance == 0.0) {
                     entity = candidateEntity;
                     position = (Vec3d)candidateHit.get();
                  }
               } else {
                  entity = candidateEntity;
                  position = (Vec3d)candidateHit.get();
                  nearestDistance = candidateDistance;
               }
            }
         }
      }

      return entity == null ? null : new EntityHitResult(entity, position);
   }

   public static Entity hitEntityNearTarget(LivingEntity target, double yaw, double pitch, double range) {
      if (target != null && minecraftClient.player != null) {
         Vec3d position = minecraftClient.player.getEyePos();
         Vec3d rayEnd = position.add(direction(pitch, yaw).multiply(range));
         EntityHitResult hitResult = raycast(minecraftClient.player, position, rayEnd, target.getBoundingBox().expand(range), entity -> !entity.isSpectator(), range);
         return hitResult == null ? null : hitResult.getEntity();
      } else {
         return null;
      }
   }
}
