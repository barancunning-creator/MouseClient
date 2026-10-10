package com.mouseclient.util.entity;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.mixin.MinecraftActionInvoker;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.movement.StopUsingItemEvent;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import java.util.HashMap;
import java.util.LinkedList;
import lombok.Generated;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(name = "ProjectileHelper", description = "Доводит прицел по цели при стрельбе из лука и трезубца.", category = ModuleCategory.COMBAT)
public final class ProjectileHelper extends Module {
   private static ProjectileHelper INSTANCE;
   public BooleanSetting aimAssist;
   public MultiSelectSetting assistActions;
   public ModeSetting aimStyle;
   public BooleanSetting clientRotation;
   public BooleanSetting showHint;
   private final HashMap<LivingEntity, LinkedList<Vec3d>> targetPositionHistory = new HashMap<>();
   private LivingEntity target;
   private int targetChangeTicks;
   private boolean assistAllowed = true;
   private boolean attackWasDown;
   private float aimNoiseScale;
   private float hintAlpha;
   private boolean releasingSnap;
   private int snapReleaseTicks;
   private float savedYaw;
   private float savedPitch;
   private RotationAngles snapRotation;
   private boolean snapUsesClientRotation;
   private boolean stoppingItemUse;
   private int crossbowShotTicks;
   private boolean startingCrossbowUse;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         boolean attackPressed = minecraftClient.options.attackKey.isPressed();
         if (this.canAssist() && attackPressed && !this.attackWasDown) {
            this.assistAllowed = !this.assistAllowed;
         }

         this.attackWasDown = attackPressed;
         if (!this.canAssist()) {
            this.assistAllowed = true;
            this.target = null;
            this.targetChangeTicks = 0;
            this.crossbowShotTicks = 0;
            if (this.releasingSnap) {
               if (!this.snapUsesClientRotation) {
                  minecraftClient.player.setYaw(minecraftClient.player.headYaw = this.savedYaw);
                  minecraftClient.player.setPitch(this.savedPitch);
               }

               this.releasingSnap = false;
            }
         } else if (!this.assistAllowed) {
            this.target = null;
            this.targetChangeTicks = 0;
            this.crossbowShotTicks = 0;
         } else if (minecraftClient.player.getMainHandStack().getItem() instanceof CrossbowItem) {
            this.selectTarget();
            if (this.target == null) {
               this.crossbowShotTicks = 0;
            } else {
               this.sampleTargetVelocity(this.target);
               if (this.crossbowShotTicks != 0) {
                  this.crossbowShotTicks++;
                  RotationController.getInstance()
                     .requestForDuration(SmoothedAimVector.anglesForDirection(this.aimDirection()), new RotationLimits((360.0F), (360.0F), (30.0F), (30.0F)), (2L));
                  minecraftClient.player.setYaw(minecraftClient.player.headYaw = SmoothedAimVector.anglesForDirection(this.aimDirection()).getYaw());
                  minecraftClient.player.setPitch(MathHelper.clamp(SmoothedAimVector.anglesForDirection(this.aimDirection()).getPitch(), (-90.0F), (90.0F)));
                  if (minecraftClient.interactionManager != null) {
                     this.startingCrossbowUse = true;
                     ((MinecraftActionInvoker)(Object)minecraftClient).invokeStartUseItem();
                     this.startingCrossbowUse = false;
                     if (this.usesSpookyTimeRotation()) {
                        RotationController.getInstance().refreshCameraRotation();
                     }

                     this.crossbowShotTicks = 0;
                  }
               }
            }
         } else if (!this.aimStyle.isSelected(("Снап"))) {
            this.selectTarget();
            if (this.target != null) {
               this.updateSmoothAim();
            }
         } else if (!this.releasingSnap) {
            this.selectTarget();
            if (this.target != null) {
               this.sampleTargetVelocity(this.target);
            }
         } else if (!this.snapUsesClientRotation) {
            if (++this.snapReleaseTicks >= 2 && minecraftClient.interactionManager != null) {
               this.stoppingItemUse = true;
               minecraftClient.interactionManager.stopUsingItem(minecraftClient.player);
               this.stoppingItemUse = false;
               minecraftClient.player.setYaw(minecraftClient.player.headYaw = this.savedYaw);
               minecraftClient.player.setPitch(this.savedPitch);
               this.releasingSnap = false;
               this.target = null;
            }
         } else {
            RotationController.getInstance().requestForDuration(this.snapRotation, new RotationLimits((360.0F), (360.0F), (30.0F), (30.0F)), (2L));
            this.snapReleaseTicks++;
            boolean rotationReached = Math.abs(MathHelper.wrapDegrees(this.snapRotation.getYaw() - minecraftClient.player.getYaw())) <= 1.0F
               && Math.abs(this.snapRotation.getPitch() - minecraftClient.player.getPitch()) <= 1.0F;
            if ((rotationReached && this.snapReleaseTicks >= 2 || this.snapReleaseTicks >= 10) && minecraftClient.interactionManager != null) {
               this.stoppingItemUse = true;
               minecraftClient.interactionManager.stopUsingItem(minecraftClient.player);
               this.stoppingItemUse = false;
               if (this.usesSpookyTimeRotation()) {
                  RotationController.getInstance().refreshCameraRotation();
               }

               this.releasingSnap = false;
               this.target = null;
            }
         }
      }
   };
   private final EventListener<StopUsingItemEvent> stopUsingItemListener = event -> {
      if (this.isEnabled()
         && !this.stoppingItemUse
         && minecraftClient.player != null
         && minecraftClient.world != null
         && this.aimAssist.getValue()
         && this.usesSpookyTimeRotation()
         && this.canAssist()
         && this.assistAllowed) {
         RotationController.getInstance().refreshCameraRotation();
      }

      if (this.isEnabled() && !this.stoppingItemUse && this.aimStyle.isSelected(("Снап")) && minecraftClient.player != null && minecraftClient.world != null) {
         if (!(minecraftClient.player.getMainHandStack().getItem() instanceof CrossbowItem)) {
            if (this.canAssist() && this.assistAllowed) {
               if (!this.releasingSnap) {
                  this.selectTarget();
                  if (this.target == null) {
                     return;
                  }

                  this.snapRotation = SmoothedAimVector.anglesForDirection(this.aimDirection());
                  this.snapUsesClientRotation = this.clientRotation.getValue();
                  if (this.snapUsesClientRotation) {
                     RotationController.getInstance().requestForDuration(this.snapRotation, new RotationLimits((360.0F), (360.0F), (30.0F), (30.0F)), (2L));
                  } else {
                     this.savedYaw = minecraftClient.player.getYaw();
                     this.savedPitch = minecraftClient.player.getPitch();
                     minecraftClient.player.setYaw(minecraftClient.player.headYaw = this.snapRotation.getYaw());
                     minecraftClient.player.setPitch(this.snapRotation.getPitch());
                  }

                  this.releasingSnap = true;
                  this.snapReleaseTicks = 0;
               }

               event.setCancelled(true);
            }
         }
      }
   };
   private final EventListener<HudRenderEvent> hudRenderListener = event -> {
      if (this.showHint.getValue()) {
         this.hintAlpha = this.hintAlpha
            + ((this.canAssist() && this.assistAllowed && this.target != null ? 1.0F : 0.0F) - this.hintAlpha) * (0.2F);
         if (!(this.hintAlpha < (0.01F))) {
            ;
         }
      }
   };

   private Vec3d aimDirection() {
      ItemStack stack = minecraftClient.player.getMainHandStack();
      double speed = this.launchSpeed(stack);
      double gravity = this.projectileGravity(stack);
      if (!(speed <= 0.0) && !(gravity <= 0.0)) {
         int leadTicks = minecraftClient.player.isUsingItem() ? 1 : 0;
         Vec3d playerVelocity = minecraftClient.player.getVelocity();
         Vec3d launchOrigin = minecraftClient.player.getCameraPosVec(1.0F).add(playerVelocity.multiply(leadTicks));
         Vec3d targetVelocity = stack.getItem() instanceof CrossbowItem ? Vec3d.ZERO : this.sampleTargetVelocity(this.target);
         Box targetBounds = this.target.getBoundingBox().offset(targetVelocity.multiply(leadTicks));
         Vec3d aimPoint = this.visibleAimPoint(launchOrigin, targetBounds, this.target);
         ProjectileHelper.LaunchSolution solution = null;

         for (int iteration = 0; iteration < 4; iteration++) {
            Vec3d displacement = aimPoint.subtract(launchOrigin);
            if (solution != null) {
               displacement = displacement.subtract(playerVelocity.multiply(solution.flightTicks()));
            }

            solution = this.solveLaunch(displacement, speed, gravity);
            if (solution == null) {
               break;
            }

            Vec3d predictedAimPoint = this.visibleAimPoint(launchOrigin, targetBounds.offset(targetVelocity.multiply(solution.flightTicks())), this.target);
            if (predictedAimPoint.squaredDistanceTo(aimPoint) <= (1.0E-4D)) {
               aimPoint = predictedAimPoint;
               break;
            }

            aimPoint = predictedAimPoint;
         }

         if (solution != null) {
            ProjectileHelper.LaunchSolution refinedSolution = this.solveLaunch(aimPoint.subtract(launchOrigin), speed, gravity);
            if (refinedSolution != null) {
               solution = refinedSolution;
            }
         }

         if (solution == null) {
            Vec3d fallbackDirection = aimPoint.subtract(launchOrigin);
            return fallbackDirection.lengthSquared() > (1.0E-6D) ? fallbackDirection.normalize() : new Vec3d(0.0, (-1.0D), 0.0);
         } else {
            return solution.launchDir();
         }
      } else {
         return this.target.getBoundingBox().getCenter().subtract(minecraftClient.player.getCameraPosVec(1.0F)).normalize();
      }
   }

   private ProjectileHelper.LaunchSolution solveLaunch(Vec3d displacement, double speed, double gravity) {
      double horizontalDistance = Math.hypot(displacement.x, displacement.z);
      double verticalDistance = displacement.y;
      double speedSquared = speed * speed;
      double discriminant = speedSquared * speedSquared - gravity * (gravity * horizontalDistance * horizontalDistance + (2.0D) * verticalDistance * speedSquared);
      if (discriminant < 0.0) {
         return null;
      }

      double root = Math.sqrt(discriminant);
      double lowArcTangent = (speedSquared - root) / (gravity * horizontalDistance);
      double highArcTangent = (speedSquared + root) / (gravity * horizontalDistance);
      double arcTangent = !Double.isNaN(lowArcTangent) && !Double.isInfinite(lowArcTangent) ? lowArcTangent : highArcTangent;
      if (!Double.isNaN(arcTangent) && !Double.isInfinite(arcTangent)) {
         double elevation = Math.atan(arcTangent);
         if (elevation < (-1.5707963267948966D) || elevation > (1.5707963267948966D)) {
            elevation = Math.atan(highArcTangent);
         }

         double flightTicks = horizontalDistance / (speed * Math.cos(elevation));
         return !Double.isNaN(flightTicks) && !Double.isInfinite(flightTicks) && !(flightTicks <= 0.0)
            ? new ProjectileHelper.LaunchSolution(new Vec3d(displacement.x, horizontalDistance * Math.tan(elevation), displacement.z).normalize(), flightTicks)
            : null;
      } else {
         return null;
      }
   }

   private void selectTarget() {
      AbstractClientPlayerEntity candidateTarget = null;
      double bestScore = (1.7976931348623157E308D);

      for (AbstractClientPlayerEntity player : minecraftClient.world.getPlayers()) {
         if (player != minecraftClient.player && !FriendManager.contains(player.getNameForScoreboard())) {
            double score = this.angularDistance(player) * minecraftClient.player.distanceTo(player);
            if (score < bestScore) {
               bestScore = score;
               candidateTarget = player;
            }
         }
      }

      if (this.target == null) {
         this.target = candidateTarget;
         this.targetChangeTicks = 0;
      } else if (candidateTarget != null && candidateTarget != this.target && ++this.targetChangeTicks >= 3) {
         this.target = candidateTarget;
         this.targetChangeTicks = 0;
      } else if (candidateTarget == this.target) {
         this.targetChangeTicks = 0;
      }
   }

   private double launchSpeed(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0.0;
      } else if (stack.getItem() == Items.BOW && minecraftClient.player.isUsingItem()) {
         return (3.0F) * BowItem.getPullProgress(minecraftClient.player.getItemUseTime());
      } else if (stack.getItem() instanceof CrossbowItem) {
         return CrossbowItem.isCharged(stack) ? (3.15D) : 0.0;
      } else {
         return stack.getItem() instanceof TridentItem && minecraftClient.player.isUsingItem() ? (2.5D) : 0.0;
      }
   }

   private Vec3d visibleAimPoint(Vec3d origin, Box bounds, LivingEntity entity) {
      Vec3d center = bounds.getCenter();
      Vec3d facing = entity.getRotationVec(minecraftClient.getRenderTickCounter().getTickProgress(true));
      double halfWidth = Math.min(bounds.getLengthX(), bounds.getLengthZ()) * (0.5D);
      Vec3d facingPoint = new Vec3d(
         MathHelper.clamp(center.x + facing.x * halfWidth, bounds.minX, bounds.maxX),
         MathHelper.clamp(center.y + facing.y * halfWidth, bounds.minY, bounds.maxY),
         MathHelper.clamp(center.z + facing.z * halfWidth, bounds.minZ, bounds.maxZ)
      );
      if (!this.isPathBlocked(origin, facingPoint)) {
         return facingPoint;
      }

      if (!this.isPathBlocked(origin, center)) {
         return center;
      }

      double probeDistance = halfWidth * (0.6D);
      Vec3d[] facingOffsets = new Vec3d[]{facing.multiply(probeDistance), facing.multiply(probeDistance * (0.5D)), facing.multiply(-probeDistance * (0.5D))};

      for (Vec3d facingOffset : facingOffsets) {
         Vec3d facingProbe = center.add(facingOffset.x, facingOffset.y, facingOffset.z);
         if (bounds.contains(facingProbe) && !this.isPathBlocked(origin, facingProbe)) {
            return facingProbe;
         }
      }

      Vec3d[] boxOffsets = new Vec3d[]{
         new Vec3d(0.0, (0.25D), 0.0),
         new Vec3d(0.0, (-0.25D), 0.0),
         new Vec3d((0.25D), 0.0, 0.0),
         new Vec3d((-0.25D), 0.0, 0.0),
         new Vec3d(0.0, 0.0, (0.25D)),
         new Vec3d(0.0, 0.0, (-0.25D)),
         new Vec3d((0.18D), (0.2D), (0.18D)),
         new Vec3d((-0.18D), (0.2D), (0.18D)),
         new Vec3d((0.18D), (0.2D), (-0.18D)),
         new Vec3d((-0.18D), (0.2D), (-0.18D)),
         new Vec3d((0.18D), (-0.1D), (0.18D)),
         new Vec3d((-0.18D), (-0.1D), (0.18D)),
         new Vec3d((0.18D), (-0.1D), (-0.18D)),
         new Vec3d((-0.18D), (-0.1D), (-0.18D))
      };

      for (Vec3d boxOffset : boxOffsets) {
         Vec3d boxProbe = center.add(bounds.getLengthX() * boxOffset.x, bounds.getLengthY() * boxOffset.y, bounds.getLengthZ() * boxOffset.z);
         if (bounds.contains(boxProbe) && !this.isPathBlocked(origin, boxProbe)) {
            return boxProbe;
         }
      }

      return facingPoint;
   }

   private Vec3d sampleTargetVelocity(LivingEntity entity) {
      this.targetPositionHistory.keySet().removeIf(trackedEntity -> trackedEntity.isRemoved() || trackedEntity != entity && trackedEntity.squaredDistanceTo(entity) > (4096.0D));
      java.util.LinkedList<net.minecraft.util.math.Vec3d> positions = this.targetPositionHistory.computeIfAbsent(entity, historyEntity -> new LinkedList<>());
      if (positions.size() >= 5) {
         positions.pollFirst();
      }

      positions.add(entity.getEntityPos());
      if (positions.size() < 2) {
         return Vec3d.ZERO;
      }

      Vec3d averageVelocity = Vec3d.ZERO;

      for (int sampleIndex = 1; sampleIndex < positions.size(); sampleIndex++) {
         Vec3d displacement = ((Vec3d)positions.get(sampleIndex)).subtract((Vec3d)positions.get(sampleIndex - 1));
         averageVelocity = averageVelocity.add(displacement.x, 0.0, displacement.z);
      }

      return averageVelocity.multiply(1.0 / (positions.size() - 1));
   }

   public boolean usesSpookyTimeRotation() {
      return ServerHelper.getInstance().isEnabled() && ServerHelper.getInstance().server.isSelected(("SpookyTime"));
   }

   private ProjectileHelper() {
      this.aimAssist = this.addSetting(new BooleanSetting(("Доводка прицела"), ("Выключи, чтобы модуль только рисовал предикт и не трогал прицел."), true));
      this.assistActions = this.addSetting(new MultiSelectSetting(("Помогать с"), ("Из какого оружия доводить прицел."), new String[]{("Трезубцем"), ("Луком"), ("Арбалетом")}, ("Трезубцем"), ("Луком"), ("Арбалетом")));
      this.assistActions.setVisibility(() -> this.aimAssist.getValue());
      this.aimStyle = this.addSetting(new ModeSetting(("Стиль наводки"), ("Снап доворачивает мгновенно в точку, плавная ведёт с разбросом."), ("Плавная"), ("Плавная"), ("Снап")));
      this.aimStyle.setVisibility(() -> this.aimAssist.getValue());
      this.clientRotation = this.addSetting(
         new BooleanSetting(("Клиентская ротация"), ("Снап ведёт камеру ротатором, выкл тихо разворачивает на один тик."), true).setVisibility(() -> this.aimAssist.getValue() && this.aimStyle.isSelected(("Снап")))
      );
      this.showHint = this.addSetting(new BooleanSetting(("Отображение подсказки"), ("Пишет по центру, как отключить доводку."), false));
   }

   private boolean canAssist() {
      return false;
   }

   public boolean deferCrossbowUse() {
      return false;
   }

   @Override
   protected void onEnable() {
      ProjectilePrediction.getInstance().setSubscribed(true);
   }

   private double angularDistance(LivingEntity entity) {
      Vec3d eyePosition = new Vec3d(
         minecraftClient.player.getX(), minecraftClient.player.getY() + minecraftClient.player.getStandingEyeHeight(), minecraftClient.player.getZ()
      );
      Box bounds = entity.getBoundingBox();
      Vec3d nearestPoint = new Vec3d(
         Math.max(bounds.minX, Math.min(eyePosition.x, bounds.maxX)), Math.max(bounds.minY, Math.min(eyePosition.y, bounds.maxY)), Math.max(bounds.minZ, Math.min(eyePosition.z, bounds.maxZ))
      );
      Vec3d lookDirection = new RotationAngles(minecraftClient.player.getYaw(), minecraftClient.player.getPitch()).direction();
      return Math.toDegrees(Math.acos(lookDirection.dotProduct(nearestPoint.subtract(eyePosition).normalize())));
   }

   private void updateSmoothAim() {
      Vec3d direction = this.aimDirection();
      RotationAngles angles = SmoothedAimVector.anglesForDirection(direction);
      float yaw = angles.getYaw();
      float pitch = angles.getPitch();
      if (this.usesSpookyTimeRotation()) {
         RotationController.getInstance()
            .requestForDuration(new RotationAngles(yaw, MathHelper.clamp(pitch, (-90.0F), (90.0F))), new RotationLimits((160.0F), (160.0F), (30.0F), (30.0F)), (2L));
      } else {
         float tickTime = minecraftClient.player.age + minecraftClient.getRenderTickCounter().getTickProgress(true);
         float yawError = Math.abs(MathHelper.wrapDegrees(yaw - minecraftClient.player.getYaw()));
         float pitchError = Math.abs(MathHelper.wrapDegrees(pitch - minecraftClient.player.getPitch()));
         float angularError = (float)Math.hypot(yawError, pitchError);
         float noiseTarget = MathHelper.clamp(angularError <= (8.0F) ? 0.0F : (angularError - (8.0F)) / (26.0F), 0.0F, 1.0F);
         this.aimNoiseScale = MathHelper.clamp(MathHelper.lerp((0.2F), this.aimNoiseScale, noiseTarget), (0.05F), (22.0F));
         float useTimeScale = MathHelper.clamp(minecraftClient.player.getItemUseTimeLeft(), 1, 6);
         float yawNoise = (float)(
               Math.sin(tickTime * (0.8F)) * (15.0D)
                  + Math.sin(tickTime * (0.04D) + (17.2D)) * (1.5D)
                  + Math.sin(tickTime * (0.11D) + (5.8D)) * (3.0D)
                  + Math.sin(tickTime * (0.07D) + (12.3D)) * 1.0
            )
            / useTimeScale
            * this.aimNoiseScale;
         float pitchNoise = (float)(Math.sin(tickTime * (0.4D)) * (15.0D) + Math.sin(tickTime * (0.03D) + (54.1D)) * (0.5D))
            / MathHelper.clamp(minecraftClient.player.getItemUseTimeLeft(), 1, 10)
            * this.aimNoiseScale;
         RotationController.getInstance()
            .requestForDuration(
               new RotationAngles(yaw + yawNoise, MathHelper.clamp(pitch + pitchNoise, (-90.0F), (90.0F))),
               new RotationLimits((160.0F), (160.0F), (30.0F), (30.0F)),
               (2L)
            );
      }
   }

   @Generated
   public LivingEntity target() {
      return this.target;
   }

   private double projectileGravity(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0.0;
      } else if (stack.getItem() == Items.BOW) {
         return (0.05D);
      } else if (stack.getItem() instanceof CrossbowItem) {
         return (0.05D);
      } else {
         return stack.getItem() instanceof TridentItem ? (0.05D) : 0.0;
      }
   }

   @Override
   protected void onDisable() {
      ProjectilePrediction.getInstance().setSubscribed(false);
      if (this.releasingSnap && !this.snapUsesClientRotation && minecraftClient.player != null) {
         minecraftClient.player.setYaw(minecraftClient.player.headYaw = this.savedYaw);
         minecraftClient.player.setPitch(this.savedPitch);
      }

      this.target = null;
      this.targetChangeTicks = 0;
      this.assistAllowed = true;
      this.crossbowShotTicks = 0;
      this.releasingSnap = false;
      this.snapReleaseTicks = 0;
      this.stoppingItemUse = false;
      this.aimNoiseScale = 0.0F;
      this.hintAlpha = 0.0F;
      this.targetPositionHistory.clear();
   }

   public static ProjectileHelper getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ProjectileHelper();
      }

      return INSTANCE;
   }

   private boolean isPathBlocked(Vec3d origin, Vec3d destination) {
      return minecraftClient.world == null
         ? false
         : minecraftClient.world.raycast(new RaycastContext(origin, destination, ShapeType.COLLIDER, FluidHandling.NONE, minecraftClient.player)).getType()
            == Type.BLOCK;
   }

   private record LaunchSolution(Vec3d launchDir, double flightTicks) {
   }
}
