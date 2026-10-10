package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.internal.ClientMath;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.CombatAttackHelper;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.MouseSensitivity;
import java.util.ArrayList;
import java.util.Comparator;
import lombok.Generated;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
   name = "AimAssist",
   description = "Ведёт прицел к цели в пределах FOV, на входе в снап-FOV кидает его в цель резким рывком и бьёт.",
   category = ModuleCategory.COMBAT
)
public final class AimAssist extends Module {
   private static AimAssist INSTANCE;
   public ModeSetting aimMode;
   public DecimalSetting fov;
   public DecimalSetting radius;
   public DecimalSetting horizontalSpeed;
   public DecimalSetting verticalSpeed;
   public DecimalSetting aimStrength;
   public DecimalSetting attackAngle;
   public DecimalSetting snapSmoothness;
   public BooleanSetting attackTarget;
   public BooleanSetting smartCriticals;
   public MultiSelectSetting aimTargets;
   public ModeSetting targetSorting;
   public ModeSetting aimThroughBlocks;
   private LivingEntity target;
   private float smoothedYawSpeed;
   private float smoothedPitchSpeed;
   private float pendingYaw;
   private float pendingPitch;
   private boolean snapArmed = true;
   private long lastMouseUpdateMillis;
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (this.target != null && this.usesSmartCriticals()) {
         if (!(CombatTargeting.distanceToBounds(this.target) >= this.radius.getValue())
            && CombatAttackHelper.shouldPauseMovementForCritical(
               this.target, new float[]{this.radius.getValue(), 0.0F, this.radius.getValue()}, ("Легитный")
            )) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.player.isAlive()) {
         if (this.attackTarget.getValue()
            && this.target != null
            && this.angularDistance(this.target) <= this.attackAngle.getValue()
            && CombatAttackHelper.canAttack(
               this.target, true, true, this.usesSmartCriticals(), 0L, new float[]{this.radius.getValue(), 0.0F, this.radius.getValue()}
            )) {
            CombatAttackHelper.performAttack(this.target, null, null, Hand.MAIN_HAND, false);
         }

         if (this.target == null || !this.isValidTarget(this.target)) {
            ArrayList targets = new ArrayList();

            for (Entity entity : minecraftClient.world.getEntities()) {
               if (entity instanceof LivingEntity eligibleTarget && this.isValidTarget(eligibleTarget)) {
                  targets.add(eligibleTarget);
               }
            }

            if (!targets.isEmpty()) {
               targets.sort(
                  this.targetSorting.isSelected(("Адаптивный"))
                     ? Comparator.<LivingEntity>comparingDouble(entity -> -entity.getArmor())
                        .thenComparingDouble(entity -> this.angularDistance(entity))
                        .thenComparingDouble(entity -> CombatTargeting.distanceToBounds(entity))
                        .thenComparingDouble(entity -> CombatTargeting.displayedHealth(entity))
                     : (
                        this.targetSorting.isSelected(("Дистанция"))
                           ? Comparator.<LivingEntity>comparingDouble(entity -> CombatTargeting.distanceToBounds(entity))
                              .thenComparingDouble(entity -> CombatTargeting.displayedHealth(entity))
                           : (
                              this.targetSorting.isSelected(("Здоровье"))
                                 ? Comparator.<LivingEntity>comparingDouble(entity -> CombatTargeting.displayedHealth(entity))
                                    .thenComparingDouble(entity -> CombatTargeting.distanceToBounds(entity))
                                 : Comparator.<LivingEntity>comparingDouble(entity -> this.angularDistance(entity))
                                    .thenComparingDouble(entity -> CombatTargeting.distanceToBounds(entity))
                                    .thenComparingDouble(entity -> CombatTargeting.displayedHealth(entity))
                                    .thenComparingDouble(entity -> -entity.getArmor())
                           )
                     )
               );
               this.target = (LivingEntity)targets.get(0);
            } else {
               this.target = null;
            }

            this.smoothedYawSpeed = this.smoothedPitchSpeed = 0.0F;
            this.pendingYaw = this.pendingPitch = 0.0F;
            this.snapArmed = true;
         }
      } else {
         this.target = null;
      }
   };

   public boolean usesSmartCriticals() {
      return this.attackTarget.getValue() && this.smartCriticals.getValue();
   }

   @Override
   protected void onDisable() {
      this.target = null;
      this.smoothedYawSpeed = this.smoothedPitchSpeed = 0.0F;
      this.pendingYaw = this.pendingPitch = 0.0F;
      this.snapArmed = true;
   }

   public static AimAssist getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AimAssist();
      }

      return INSTANCE;
   }

   private double angularDistance(LivingEntity entity) {
      return Math.hypot(
         MathHelper.wrapDegrees(CombatTargeting.anglesBetween(entity.getEyePos(), minecraftClient.player.getEyePos())[0] - minecraftClient.player.getYaw()),
         CombatTargeting.anglesBetween(entity.getEyePos(), minecraftClient.player.getEyePos())[1] - minecraftClient.player.getPitch()
      );
   }

   private boolean isValidTarget(LivingEntity entity) {
      if (entity != minecraftClient.player && entity.age >= 1 && !(entity instanceof ArmorStandEntity)) {
         if (CombatTargeting.distanceToBounds(entity) > this.radius.getValue()) {
            return false;
         }

         if (this.angularDistance(entity) > this.fov.getValue()) {
            return false;
         }

         if (this.aimThroughBlocks.isSelected(("Нет")) && !CombatTargeting.hasLineOfSight(minecraftClient.player.getEyePos(), entity.getEyePos())) {
            return false;
         }

         if (this.aimThroughBlocks.isSelected(("Неполноценные")) && !CombatTargeting.hasLineOfSightIgnoringPartialBlocks(minecraftClient.player.getEyePos(), entity.getEyePos())) {
            return false;
         }

         if (entity instanceof PlayerEntity) {
            if (FriendManager.contains(((PlayerEntity)entity).getGameProfile().name())) {
               return false;
            }

            if (!this.aimTargets.matchesOption(("Игроки"))) {
               return false;
            }

            if (entity.getArmor() == 0 && !this.aimTargets.matchesOption(("Голые"))) {
               return false;
            }
         } else if (!this.aimTargets.matchesOption(("Мобы"))) {
            return false;
         }

         return !entity.isInvulnerable() && entity.isAlive();
      } else {
         return false;
      }
   }

   public void applyMouseAim(double mouseYawDelta, double mousePitchDelta) {
      if (this.isEnabled() && this.target != null && minecraftClient.player != null && minecraftClient.world != null) {
         float deltaTicks = Math.max(1.0F, Math.min((float)(System.currentTimeMillis() - this.lastMouseUpdateMillis), (100.0F))) / (50.0F);
         this.lastMouseUpdateMillis = System.currentTimeMillis();
         Vec3d eyePosition = minecraftClient.player.getEyePos();
         Box aimBounds = this.target
            .getBoundingBox()
            .contract(this.target.getWidth() * (0.25D), this.target.getHeight() * (0.2D), this.target.getWidth() * (0.25D));
         Vec3d lookPoint = eyePosition.add(
            CombatTargeting.direction(minecraftClient.player.getYaw(), minecraftClient.player.getPitch()).multiply(eyePosition.distanceTo(aimBounds.getCenter()))
         );
         float[] aimAngles = CombatTargeting.anglesBetween(
            new Vec3d(
               MathHelper.clamp(lookPoint.x, aimBounds.minX, aimBounds.maxX), MathHelper.clamp(lookPoint.y, aimBounds.minY, aimBounds.maxY), MathHelper.clamp(lookPoint.z, aimBounds.minZ, aimBounds.maxZ)
            ),
            eyePosition
         );
         float yawError = MathHelper.wrapDegrees(aimAngles[0] - minecraftClient.player.getYaw());
         float pitchError = aimAngles[1] - minecraftClient.player.getPitch();
         if (Math.hypot(yawError, pitchError) > this.attackAngle.getValue()) {
            this.snapArmed = true;
         }

         if (CombatTargeting.rayHitsEntityBounds(
            this.target, this.radius.getValue(), minecraftClient.player.getYaw(), minecraftClient.player.getPitch()
         )) {
            this.snapArmed = false;
         }

         float fovWeight = ClientMath.clampUnit(
            (this.fov.getValue() - (float)Math.hypot(yawError, pitchError)) / Math.max(1.0F, this.fov.getValue() * (0.25F))
         );
         if (!(fovWeight <= 0.0F)) {
            if (this.aimMode.isSelected(("Мышь"))) {
               float mouseMagnitude = (float)Math.hypot(mouseYawDelta * (0.15D), mousePitchDelta * (0.15D));
               float mouseAssistLimit = mouseMagnitude * this.cooldownStrength() * (this.aimStrength.getValue() / (100.0F)) * fovWeight;
               this.pendingYaw = this.pendingYaw + MathHelper.clamp(yawError, -mouseAssistLimit, mouseAssistLimit);
               this.pendingPitch = this.pendingPitch + MathHelper.clamp(pitchError, -mouseAssistLimit * (0.6F), mouseAssistLimit * (0.6F));
            } else {
               float strength = this.aimStrength.getValue() / (100.0F);
               float targetYawSpeed = MathHelper.clamp(yawError * strength, -this.horizontalSpeed.getValue(), this.horizontalSpeed.getValue()) * fovWeight;
               float targetPitchSpeed = MathHelper.clamp(pitchError * strength, -this.verticalSpeed.getValue(), this.verticalSpeed.getValue()) * fovWeight;
               this.smoothedYawSpeed = this.smoothedYawSpeed + (targetYawSpeed - this.smoothedYawSpeed) * ClientMath.clampUnit((0.4F) * deltaTicks);
               this.smoothedPitchSpeed = this.smoothedPitchSpeed + (targetPitchSpeed - this.smoothedPitchSpeed) * ClientMath.clampUnit((0.4F) * deltaTicks);
               this.pendingYaw = this.pendingYaw + MathHelper.clamp(this.smoothedYawSpeed * deltaTicks, -Math.abs(yawError), Math.abs(yawError));
               this.pendingPitch = this.pendingPitch + MathHelper.clamp(this.smoothedPitchSpeed * deltaTicks, -Math.abs(pitchError), Math.abs(pitchError));
            }

            float yawStep = MouseSensitivity.quantize(this.pendingYaw);
            float pitchStep = MouseSensitivity.quantize(this.pendingPitch);
            this.pendingYaw -= yawStep;
            this.pendingPitch -= pitchStep;
            minecraftClient.player.setYaw(minecraftClient.player.getYaw() + yawStep);
            minecraftClient.player.setPitch(MathHelper.clamp(minecraftClient.player.getPitch() + pitchStep, (-90.0F), (90.0F)));
            minecraftClient.player.lastYaw += yawStep;
            minecraftClient.player.lastPitch = MathHelper.clamp(minecraftClient.player.lastPitch + pitchStep, (-90.0F), (90.0F));
         }
      }
   }

   private float cooldownStrength() {
      float cooldownProgress = minecraftClient.player.getAttackCooldownProgress(1.0F);
      double attackSpeed = minecraftClient.player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
      if (attackSpeed > (1.6D)) {
         cooldownProgress *= (float)((1.6D) / attackSpeed);
      }

      float easedProgress;
      if (attackSpeed < (2.0D)) {
         easedProgress = (2.70158F) * cooldownProgress * cooldownProgress * cooldownProgress - (1.70158F) * cooldownProgress * cooldownProgress;
      } else if (cooldownProgress < (0.5F)) {
         easedProgress = (8.0F) * cooldownProgress * cooldownProgress * cooldownProgress * cooldownProgress;
      } else {
         easedProgress = 1.0F - (8.0F) * (cooldownProgress - 1.0F) * (cooldownProgress - 1.0F) * (cooldownProgress - 1.0F) * (cooldownProgress - 1.0F);
      }

      return (1.0F + (29.0F) * easedProgress) / (50.0F);
   }

   private AimAssist() {
      this.aimMode = this.addSetting(new ModeSetting(("Тип доводки"), ("Скорость ведёт прицел сама, Мышь доворачивает только пока ты крутишь мышкой."), ("Скорость"), ("Скорость"), ("Мышь")));
      this.fov = this.addSetting(new DecimalSetting(("FOV"), ("Максимальный угол от прицела до цели, за которым доводки нет."), (45.0D), (5.0D), (180.0D), 1.0));
      this.radius = this.addSetting(new DecimalSetting(("Радиус"), ("На какой дистанции держать цель."), (4.0D), 1.0, (8.0D), (0.1D)));
      this.horizontalSpeed = this.addSetting(new DecimalSetting(("Скорость по горизонтали"), ("Сколько градусов за тик выбирать по яву."), (9.0D), (0.5D), (40.0D), (0.5D)));
      this.verticalSpeed = this.addSetting(new DecimalSetting(("Скорость по вертикали"), ("Сколько градусов за тик выбирать по питчу."), (6.0D), 0.0, (40.0D), (0.5D)));
      this.aimStrength = this.addSetting(new DecimalSetting(("Сила доводки"), ("Какую долю нужного доворота отрабатывать: в Скорость тянет прицел мягче или резче, в Мышь масштабирует твоё движение мышкой."), (100.0D), (10.0D), (300.0D), (5.0D)));
      this.attackAngle = this.addSetting(new DecimalSetting(("Радиус удара"), ("Внутри этого угла от прицела цель считается взятой и по ней идёт удар."), (15.0D), 0.0, (90.0D), 1.0));
      this.snapSmoothness = this.addSetting(new DecimalSetting(("Плавность снапа"), ("За сколько миллисекунд рывок дотягивает прицел до хитбокса. 0 — мгновенный бросок."), (60.0D), 0.0, (300.0D), (5.0D)));
      this.snapSmoothness.setVisibility(() -> false);
      this.attackTarget = this.addSetting(new BooleanSetting(("Бить по цели"), ("Бить цель по готовности кулдауна, когда она внутри радиуса удара."), true));
      this.smartCriticals = this.addSetting(new BooleanSetting(("Умные криты"), ("Ждать момент крита и гасить спринт перед ударом, а не бить по готовности кулдауна."), true));
      this.smartCriticals.setVisibility(() -> this.attackTarget.getValue());
      this.aimTargets = this.addSetting(new MultiSelectSetting(("Кого доводить"), ("Каких существ считать целью."), new String[]{("Игроки"), ("Голые"), ("Мобы")}, ("Игроки"), ("Голые")));
      this.targetSorting = this.addSetting(new ModeSetting(("Сортировка целей"), ("По какому признаку выбирать цель из подходящих."), ("Наводка"), ("Наводка"), ("Адаптивный"), ("Дистанция"), ("Здоровье")));
      this.aimThroughBlocks = this.addSetting(new ModeSetting(("Доводить через блоки"), ("Разрешить наведение сквозь блоки."), ("Нет"), ("Нет"), ("Обычные"), ("Неполноценные")));
      this.horizontalSpeed.setVisibility(() -> this.aimMode.isSelected(("Скорость")));
      this.verticalSpeed.setVisibility(() -> this.aimMode.isSelected(("Скорость")));
   }

   @Generated
   public LivingEntity target() {
      return this.target;
   }
}
