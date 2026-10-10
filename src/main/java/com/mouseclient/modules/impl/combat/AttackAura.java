package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.events.impl.RenderStepEvent;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.internal.Interpolation;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.modules.impl.misc.FilteredTextSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.movement.MaceTest;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.CombatAttackHelper;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.RotationController;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.MaceFallPrediction;
import com.mouseclient.util.entity.TargetRotationController;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.entity.LegitRotationController;
import com.mouseclient.util.entity.BuilderRotationController;
import com.mouseclient.util.entity.CometRotationController;
import com.mouseclient.util.entity.RotationBuilder;
import com.mouseclient.util.entity.Shard022RotationController;
import com.mouseclient.util.entity.ShardTestRotationController;
import com.mouseclient.util.entity.SpookyRotationController;
import com.mouseclient.util.entity.AimRotationController;
import com.mouseclient.util.entity.FastRotationController;
import com.mouseclient.util.entity.FunTimeRotationController;
import com.mouseclient.util.entity.ArenaSpookyRotationController;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Generated;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(name = "AttackAura", description = "Автоатака по ближайшей цели с человечными ротациями.", category = ModuleCategory.COMBAT)
public final class AttackAura extends Module {
   private static AttackAura INSTANCE;
   public ModeSetting rotationMode;
   public DecimalSetting attackRange;
   public DecimalSetting aimRange;
   public MultiSelectSetting attackTargets;
   public ModeSetting targetSorting;
   public ModeSetting sprintMode;
   public BooleanSetting movementCorrection;
   public ModeSetting attackThroughBlocks;
   public MultiSelectSetting attackRestrictions;
   public MultiSelectSetting advancedSettings;
   public BooleanSetting autoMace;
   public BooleanSetting maximizeDamage;
   public FilteredTextSetting swapItem;
   public BooleanSetting syncWithAuras;
   private final FunTimeRotationController funTimeRotation = new FunTimeRotationController();
   private final SpookyRotationController spookyRotation = new SpookyRotationController();
   private final ArenaSpookyRotationController arenaSpookyRotation = new ArenaSpookyRotationController();
   private final AimRotationController aimRotation = new AimRotationController();
   private final FastRotationController fastRotation = new FastRotationController();
   private final LegitRotationController legitRotation = new LegitRotationController();
   private final Shard022RotationController shard022Rotation = new Shard022RotationController();
   private final BuilderRotationController builderRotation = new BuilderRotationController();
   private final CometRotationController cometRotation = new CometRotationController();
   private final ShardTestRotationController shardTestRotation = new ShardTestRotationController();
   private static final Pattern BOSS_BAR_SECONDS_PATTERN = Pattern.compile(("(\\d+)\\s*сек"));
   private LivingEntity target;
   public int successfulAttacks;
   private boolean maceEquipActive;
   private boolean maceUsedThisFall;
   private LivingEntity nearbyMaceCandidate;
   private LivingEntity lastWindBoostTarget;
   private int maceSourceSlot = -1;
   private int previousHotbarSlot = -1;
   private int unusedCounter;
   private boolean unusedFlag;
   private boolean fastSmoothReady = true;
   private long movementFreezeUntilMillis;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         if (!minecraftClient.player.isAlive()) {
            this.toggle();
         } else {
            if (this.syncWithAuras != null && this.syncWithAuras.getValue()) {
               boolean crystalWorking = CrystalAura.getInstance().isEnabled() && (CrystalAura.getInstance().isAiming() || AuraSync.isCrystalBusy());
               boolean anchorWorking = AnchorAura.getInstance().isEnabled() && (AnchorAura.getInstance().isAiming() || AuraSync.isAnchorBusy());
               boolean miningWorking = AuraSync.isMiningBusy();
               if (crystalWorking || anchorWorking || miningWorking) {
                  return;
               }
            }

            if (System.currentTimeMillis() < this.movementFreezeUntilMillis) {
               minecraftClient.player.setVelocity(Vec3d.ZERO);
            }

            MaceFallPrediction.tickAttackCooldown();
            this.updateMaceSelection();
            if (MaceHelper.getInstance().isWindBoostActive()
               && MaceHelper.getInstance().autoEquipMace.getValue()
               && this.lastWindBoostTarget != null
               && this.lastWindBoostTarget.isAlive()
               && !this.lastWindBoostTarget.isRemoved()) {
               this.target = this.lastWindBoostTarget;
            } else if (this.target == null || !this.isValidTarget(this.target)) {
               this.selectTarget();
            }

            if (!MaceHelper.getInstance().isWindBoostActive() && this.target != null) {
               this.lastWindBoostTarget = this.target;
            }

            if (this.target == null) {
               if (RotationController.getInstance().isDecoupledSilent()) {
                  RotationController.getInstance().reset();
               }
            } else {
               this.updateRotation();
               if ((this.activeRotationController() == null || !this.activeRotationController().shouldSkipAttack(this.target, (float)this.effectiveAttackRange()))
                   && (AuraSync.isSnap(this.rotationMode.getSelectedOption()) || this.fastSmoothReady)) {
                  this.tryAttack();
               }
            }
         }
      }
   };
   private final EventListener<RenderStepEvent> renderStepListener = event -> {
      if (this.activeRotationController() != null) {
         this.activeRotationController().updateFrame(this.target, event.getTickDelta());
      }
   };
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (minecraftClient.player != null && System.currentTimeMillis() < this.movementFreezeUntilMillis) {
         event.setForward(0.0F);
         event.setStrafe(0.0F);
         event.setJumping(false);
         event.setSneaking(false);
      } else {
         if (minecraftClient.player != null && this.movementCorrection.getValue() && RotationController.getInstance().isActive()) {
            RotationController.correctMovement(event, RotationAngles.cameraYaw());
         }

         if (this.target != null) {
            if (CombatTargeting.distanceToBounds(this.target) < this.effectiveAttackRange()
               && CombatAttackHelper.shouldPauseMovementForCritical(this.target, this.rangeProfile(), this.sprintMode.getSelectedOption())) {
               event.setJumping(false);
               event.setForward(0.0F);
               event.setStrafe(0.0F);
            } else {
               if (!this.movementCorrection.getValue() && RotationController.getInstance().isActive()) {
                  RotationController.correctMovementForGliding(
                     event,
                     minecraftClient.player.getYaw(),
                     MathHelper.wrapDegrees(
                        (float)Math.toDegrees(
                              Math.atan2(this.target.getZ() - minecraftClient.player.getZ(), this.target.getX() - minecraftClient.player.getX())
                           )
                           - (90.0F)
                     )
                  );
               }
            }
         }
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null && event.isOutgoing()) {
         if (System.currentTimeMillis() < this.movementFreezeUntilMillis) {
            if (event.getPacket() instanceof PositionAndOnGround || event.getPacket() instanceof Full) {
               event.markCancelled();
            }
         }
      }
   };

   private boolean shouldEquipMaceForFall() {
      if (minecraftClient.player.isTouchingWater() || minecraftClient.player.isInLava()) {
         return false;
      } else if (this.maceEquipActive && !minecraftClient.player.isOnGround()) {
         return true;
      } else {
         return minecraftClient.player.isOnGround() ? false : MaceFallPrediction.maximumFallDistance() >= (3.5D);
      }
   }

   private void updateRotation() {
      if (this.target != null) {
         if (!MaceHelper.getInstance().isThrowInProgress()) {
            boolean attackReady = CombatAttackHelper.canAttack(this.target, false, true, true, 0L, this.rangeProfile());
            if (AuraSync.isSnap(this.rotationMode.getSelectedOption())) {
               if (RotationController.getInstance().isActive()) {
                  RotationController.getInstance().reset();
               }
               float[] angles = CombatTargeting.anglesBetween(this.target.getBoundingBox().getCenter(), minecraftClient.player.getEyePos());
               minecraftClient.player.setYaw(angles[0]);
               minecraftClient.player.headYaw = angles[0];
               minecraftClient.player.bodyYaw = angles[0];
               minecraftClient.player.setPitch(MathHelper.clamp(angles[1], -90.0F, 90.0F));
               this.fastSmoothReady = true;
            } else {
               this.fastSmoothReady = AuraSync.stepFastSmoothRotation(this.target.getBoundingBox().getCenter());
            }
         }
      }
   }

   public float swingProgress() {
      return MathHelper.clamp(minecraftClient.player.handSwingTicks / this.randomFloat((8.0F), (12.0F)), 0.0F, 1.0F);
   }

   public boolean canDealSmashDamage() {
      return (this.isAirborneWithMace() || MaceFallPrediction.hasMaceAvailable() && minecraftClient.player != null && !minecraftClient.player.isOnGround())
         && MaceItem.shouldDealAdditionalDamage(minecraftClient.player);
   }

   private void updateMaceSelection() {
      if (!ItemUseQueue.getInstance().hasPendingUses() && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
         if (this.autoMace.getValue() && minecraftClient.player != null) {
            if (minecraftClient.player.isOnGround() || minecraftClient.player.getVelocity().y >= 0.0) {
               this.maceUsedThisFall = false;
            }

            this.updateNearbyMaceCandidate();
            if (this.shouldEquipMaceForFall()) {
               int hotbarMaceSlot = InventoryActions.findHotbarItem(Items.MACE);
               int maceSlot = hotbarMaceSlot != -1 ? hotbarMaceSlot : InventoryActions.findItem(Items.MACE);
               if (maceSlot != -1
                  && !this.maceEquipActive
                  && !this.maceUsedThisFall
                  && !minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getInventory().getStack(maceSlot))) {
                  this.maceEquipActive = true;
                  if (minecraftClient.player.getMainHandStack().isOf(Items.MACE)) {
                     return;
                  }

                  if (ServerHelper.getInstance().server.matchesOption(("SpookyTime"))
                     && !MaceHelper.getInstance().isWindBoostActive()
                     && minecraftClient.world.isSpaceEmpty(minecraftClient.player, minecraftClient.player.getBoundingBox().stretch(0.0, (-3.0D), 0.0))
                     && !minecraftClient.world.isSpaceEmpty(minecraftClient.player, minecraftClient.player.getBoundingBox().stretch(0.0, (-8.0D), 0.0))) {
                     this.movementFreezeUntilMillis = System.currentTimeMillis() + (200L);
                  }

                  this.previousHotbarSlot = InventoryActions.selectedHotbarSlot();
                  this.maceSourceSlot = maceSlot;
                  if (maceSlot > 8) {
                     InventorySwapQueue.getInstance().queueSwap(maceSlot, InventoryActions.selectedHotbarSlot(), 1);
                  } else {
                     InventoryActions.selectHotbarSlot(maceSlot, ServerHelper.getInstance().server.matchesOption(("SpookyTime")));
                  }
               }
            } else {
               this.restoreSelectedItem();
            }
         } else {
            this.nearbyMaceCandidate = null;
            this.restoreSelectedItem();
         }
      }
   }

   @Override
   protected void onDisable() {
      this.target = null;
      this.lastWindBoostTarget = null;
      this.successfulAttacks = 0;
      this.unusedFlag = false;
      this.movementFreezeUntilMillis = 0L;
      MaceFallPrediction.resetAttackState();
      this.restoreSelectedItem();
      this.spookyRotation.reset();
      this.arenaSpookyRotation.reset();
      this.resetAimRotation();
      this.fastRotation.reset();
      this.legitRotation.reset();
      this.shard022Rotation.reset();
      this.cometRotation.reset();
      this.builderRotation.reset();
      this.shardTestRotation.reset();
      this.fastSmoothReady = true;
      if (RotationController.getInstance().isActive()) {
         RotationController.getInstance().reset();
      }
   }

   public void updateLegitRotation() {
      if (this.isEnabled() && this.rotationMode.isSelected(("Legit")) && this.target != null) {
         this.legitRotation.update(this.target);
      }
   }

   private void restoreSelectedItem() {
      if (this.maceEquipActive && minecraftClient.player != null) {
         this.maceEquipActive = false;
         this.maceUsedThisFall = true;
         if (this.maceSourceSlot > 8) {
            InventorySwapQueue.getInstance().queueSwap(this.previousHotbarSlot, this.maceSourceSlot, 1);
         } else if (this.previousHotbarSlot != -1) {
            InventoryActions.selectHotbarSlot(this.previousHotbarSlot, ServerHelper.getInstance().server.matchesOption(("SpookyTime")));
         }

         this.maceSourceSlot = -1;
         this.previousHotbarSlot = -1;
      }
   }

   @Generated
   public LivingEntity target() {
      return this.target;
   }

   public float[] rangeProfile() {
      if (MaceHelper.getInstance().isWindBoostActive()) {
         return new float[]{(3.0F), (11.0F), (14.0F)};
      }

      if (!this.isAirborneWithMace() || !MaceHelper.getInstance().assistAuraAim.getValue() && !MaceTest.getInstance().assistAuraHits.getValue()) {
         if (this.isAirborneWithMace()) {
            return MaceFallPrediction.heightAboveLanding() > (2.0D)
               ? new float[]{(3.0F), (25.0F), (28.0F)}
               : new float[]{(3.0F), (15.0F), (18.0F)};
         } else {
            return this.autoMace.getValue() && InventoryActions.findItem(Items.MACE) != -1 && MaceFallPrediction.heightAboveLanding() > (2.0D)
               ? new float[]{
                  this.attackRange.getValue(),
                  this.aimRange.getValue() + (10.0F),
                  this.attackRange.getValue() + this.aimRange.getValue() + (10.0F)
               }
               : new float[]{
                  this.attackRange.getValue(),
                  this.aimRange.getValue(),
                  this.attackRange.getValue() + this.aimRange.getValue()
               };
         }
      } else {
         return MaceFallPrediction.heightAboveLanding() > (2.0D)
            ? new float[]{(3.0F), (21.0F), (24.0F)}
            : new float[]{(3.0F), (11.0F), (14.0F)};
      }
   }

   private void tryAttack() {
      if (this.target != null) {
         if (!(CombatTargeting.distanceToBounds(this.target) >= this.effectiveAttackRange())) {
            if (this.attackRestrictions.matchesOption(("Ешь")) && minecraftClient.player.isUsingItem()) {
               ItemStack stack = minecraftClient.player.getActiveItem();
               if (!stack.isEmpty() && (stack.get(DataComponentTypes.FOOD) != null || stack.get(DataComponentTypes.POTION_CONTENTS) != null)) {
                  return;
               }
            }

            if (!this.attackRestrictions.matchesOption(("Открыт инвентарь")) || minecraftClient.currentScreen == null) {
               CombatAttackHelper.updateRepeatAttacks(this.target, false, true, false);
               if (System.currentTimeMillis() >= this.movementFreezeUntilMillis) {
                  if (CombatAttackHelper.canAttack(this.target, true, true, true, 0L, this.rangeProfile())) {
                     if (!this.attackThroughBlocks.isSelected(("Неполноценные")) || CombatTargeting.isAimedAtVisibleSurface(this.target, this.effectiveAttackRange())) {
                        if (this.attackThroughBlocks.isSelected(("Shard"))
                           && minecraftClient.player.networkHandler != null
                           && !CombatTargeting.hasLineOfSight(minecraftClient.player.getEyePos(), this.target.getEyePos())) {
                           ArrayList visitedBlocks = new ArrayList();
                           Vec3d position = minecraftClient.player.getEyePos();
                           Vec3d viewDirection = CombatTargeting.direction(minecraftClient.player.getYaw(), minecraftClient.player.getPitch()).normalize();
                           double targetDistance = this.target
                              .getBoundingBox()
                              .raycast(
                                 position,
                                 position.add(
                                    viewDirection.multiply(Math.max(minecraftClient.player.distanceTo(this.target), this.attackRange.getValue() + 2.0F))
                                 )
                              )
                              .map(position::distanceTo)
                              .orElse(CombatTargeting.distanceToBounds(this.target));

                           for (double rayDistance = 0.0; rayDistance <= targetDistance; rayDistance += (0.1D)) {
                              BlockPos blockPosition = BlockPos.ofFloored(position.add(viewDirection.multiply(rayDistance)));
                              if (!visitedBlocks.contains(blockPosition)) {
                                 visitedBlocks.add(blockPosition);
                                 if (!minecraftClient.world.getBlockState(blockPosition).isAir()
                                    && !minecraftClient.world.getBlockState(blockPosition).getCollisionShape(minecraftClient.world, blockPosition).isEmpty()) {
                                    BlockHitResult hitResult = minecraftClient.world
                                       .raycast(
                                          new RaycastContext(
                                             position.add(viewDirection.multiply(rayDistance - (0.2D))),
                                             position.add(viewDirection.multiply(rayDistance + (0.2D))),
                                             ShapeType.COLLIDER,
                                             FluidHandling.NONE,
                                             minecraftClient.player
                                          )
                                       );
                                    Direction hitSide = hitResult.getType() == Type.BLOCK ? hitResult.getSide() : Direction.UP;
                                    minecraftClient.player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.START_DESTROY_BLOCK, blockPosition, hitSide));
                                    minecraftClient.player.networkHandler.sendPacket(new PlayerActionC2SPacket(Action.STOP_DESTROY_BLOCK, blockPosition, hitSide));
                                 }
                              }
                           }
                        }

                        int swapSlot = this.maximizeDamage.getValue() && !this.swapItem.getText().isBlank()
                           ? InventoryActions.findSlot(
                              candidateStack -> !candidateStack.isEmpty()
                                 && candidateStack.getName().getString().toLowerCase().contains(this.swapItem.getText().toLowerCase().trim())
                           )
                           : -1;
                        if (CombatAttackHelper.performAttack(
                           this.target, swapSlot == -1 ? null : () -> InventoryActions.swapWithHand(swapSlot, Hand.OFF_HAND), swapSlot == -1 ? null : () -> {
                              minecraftClient.player.currentScreenHandler.nextRevision();
                              InventoryActions.swapWithHand(swapSlot, Hand.OFF_HAND);
                           }, Hand.MAIN_HAND, false
                        )) {
                           this.successfulAttacks++;
                           MaceFallPrediction.markAttack();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isValidTargetAtRange(LivingEntity entity, float range) {
      if (entity != minecraftClient.player && entity.age >= 1 && !(entity instanceof ArmorStandEntity)) {
         if (minecraftClient.player.squaredDistanceTo(entity) > range * range) {
            return false;
         }

         if (this.attackThroughBlocks.isSelected(("Нет")) && !CombatTargeting.hasLineOfSight(minecraftClient.player.getEyePos(), entity.getEyePos())) {
            return false;
         }

         if (entity instanceof PlayerEntity) {
            if (FriendManager.contains(((PlayerEntity)entity).getGameProfile().name())) {
               return false;
            }

            if (this.isBot((PlayerEntity)entity)) {
               return this.attackTargets.matchesOption(("Боты")) && !entity.isInvulnerable() && entity.isAlive();
            }

            if (!this.attackTargets.matchesOption(("Игроки"))) {
               return false;
            }

            if (entity.getArmor() == 0 && !this.attackTargets.matchesOption(("Голые"))) {
               return false;
            }
         } else if (!this.attackTargets.matchesOption(("Мобы"))) {
            return false;
         }

         return !entity.isInvulnerable() && entity.isAlive();
      } else {
         return false;
      }
   }

   public float randomFloat(float minimum, float maximum) {
      return Interpolation.lerp(maximum, minimum, new SecureRandom().nextFloat());
   }

   private TargetRotationController activeRotationController() {
      return switch (this.rotationMode.getSelectedOption()) {
         case ("Aim") -> this.aimRotation;
         default -> null;
      };
   }

   public boolean smartCriticals() {
      return this.advancedSettings.matchesOption(("Умные криты"));
   }

   private AttackAura() {
      this.rotationMode = this.addSetting(new ModeSetting("Ротация", "Стиль наводки на цель.", "Быстрая плавная", "Быстрая плавная", "Snap"));
      this.attackRange = this.addSetting(new DecimalSetting(("Радиус удара"), ("На какой дистанции наносить удар."), (3.0D), (3.0D), (6.0D), (0.1D)));
      this.aimRange = this.addSetting(new DecimalSetting(("Радиус прицеливания"), ("На сколько блоков дальше радиуса удара вести цель."), (5.0D), 0.0, (10.0D), (0.1D)));
      this.attackTargets = this.addSetting(new MultiSelectSetting(("Кого атаковать"), ("Каких существ считать целью."), new String[]{("Игроки"), ("Голые"), ("Мобы"), ("Боты")}, ("Игроки"), ("Голые")));
      this.targetSorting = this.addSetting(new ModeSetting(("Сортировка целей"), ("По какому признаку выбирать цель из подходящих."), ("Наводка"), ("Наводка"), ("Адаптивный"), ("Дистанция"), ("Здоровье")));
      this.sprintMode = this.addSetting(new ModeSetting(("Тип спринта"), ("Как гасить спринт перед ударом для крита."), ("Легитный"), ("Легитный"), ("Быстрый")));
      this.movementCorrection = this.addSetting(new BooleanSetting(("Свободная коррекция движения"), ("WASD ведёт туда, куда смотришь ты, а не ротация."), true));
      this.attackThroughBlocks = this.addSetting(new ModeSetting(("Бить через блоки"), ("Разрешить наведение сквозь блоки."), ("Нет"), ("Нет"), ("Обычные"), ("Неполноценные"), ("Shard")));
      this.attackRestrictions = this.addSetting(new MultiSelectSetting(("Не бить если"), ("Условия паузы автоатаки."), new String[]{("Ешь"), ("Открыт инвентарь")}));
      this.advancedSettings = this.addSetting(new MultiSelectSetting(("Настройки"), ("Дополнительные опции удара."), new String[]{("Умные криты")}));
      this.autoMace = this.addSetting(new BooleanSetting(("Авто-булава"), ("Сама берёт булаву, бьёт смэшем при падении на цель и возвращает прежний предмет."), false));
      this.maximizeDamage = this.addSetting(new BooleanSetting(("Максимальный урон"), ("На момент удара уводит выбранный предмет в левую руку и сразу возвращает обратно."), false));
      this.swapItem = this.addSetting(new FilteredTextSetting(("Предмет для свапа"), ("Часть названия предмета, который уходит в левую руку на удар."), ("карател"), 32));
      this.swapItem.setVisibility(() -> this.maximizeDamage.getValue());
      this.syncWithAuras = this.addSetting(new BooleanSetting(("Синхронизация"), ("Координировать работу с CrystalAura и AnchorAura, не сбивать комбо и хотбар."), true));
      this.attackRange
         .withTextEntryCondition(
            () -> ServerHelper.getInstance().isEnabled()
               && ServerHelper.getInstance().server.isSelected(("SpookyTime"))
               && this.attackRange.getValue() > (3.65F)
         );
   }

   private double angularDistance(LivingEntity entity) {
      return Math.hypot(
         MathHelper.wrapDegrees(CombatTargeting.anglesBetween(entity.getEyePos(), minecraftClient.player.getEyePos())[0] - minecraftClient.player.getYaw()),
         CombatTargeting.anglesBetween(entity.getEyePos(), minecraftClient.player.getEyePos())[1] - minecraftClient.player.getPitch()
      );
   }

   public double effectiveAttackRange() {
      return minecraftClient.player != null && minecraftClient.player.getMainHandStack().isOf(Items.MACE)
         ? (3.0D)
         : Math.max((3.0D), this.attackRange.getValue());
   }

   private boolean isBot(PlayerEntity player) {
      if (minecraftClient.getNetworkHandler() == null) {
         return false;
      }

      PlayerListEntry playerEntry = minecraftClient.getNetworkHandler().getPlayerListEntry(player.getUuid());
      String profileName = player.getGameProfile().name();
      return playerEntry == null || profileName == null || !profileName.matches(("\\w{3,16}")) || !profileName.equals(playerEntry.getProfile().name());
   }

   private boolean isValidTarget(LivingEntity entity) {
      return this.isValidTargetAtRange(entity, this.rangeProfile()[2]);
   }

   public static AttackAura getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AttackAura();
      }

      return INSTANCE;
   }

   private boolean isArenaScenario() {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         ScoreboardObjective sidebarObjective = minecraftClient.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
         if (sidebarObjective != null) {
            String[] titleParts = sidebarObjective.getDisplayName().getString().split(("-"));
            if (titleParts.length > 1 && titleParts[1].replaceAll(("\\D"), "").matches(("70[24]"))) {
               return true;
            }
         }

         if (!minecraftClient.world.getRegistryKey().getValue().getPath().equals(("arenas"))) {
            return false;
         }

         for (ClientBossBar bossBar : ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents().values()) {
            Matcher secondsMatcher = BOSS_BAR_SECONDS_PATTERN.matcher(bossBar.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""));
            if (bossBar.getPercent() > (0.05F) && secondsMatcher.find() && Integer.parseInt(secondsMatcher.group(1)) / bossBar.getPercent() < (60.0F)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void selectTarget() {
      this.target = null;
      if (this.syncWithAuras != null && this.syncWithAuras.getValue()) {
         LivingEntity common = AuraSync.getCommonTarget();
         if (common != null && this.isValidTarget(common)) {
            this.target = common;
            return;
         }
      }
      ArrayList candidates = new ArrayList();

      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof LivingEntity candidateTarget && this.isValidTarget(candidateTarget)) {
            candidates.add(candidateTarget);
         }
      }

      if (!candidates.isEmpty()) {
         candidates.sort(
            this.targetSorting.isSelected(("Адаптивный"))
               ? Comparator.<LivingEntity>comparingDouble(candidateEntity -> -candidateEntity.getArmor())
                  .thenComparingDouble(candidateEntity -> this.angularDistance(candidateEntity))
                  .thenComparingDouble(candidateEntity -> CombatTargeting.distanceToBounds(candidateEntity))
                  .thenComparingDouble(candidateEntity -> CombatTargeting.displayedHealth(candidateEntity))
               : (
                  this.targetSorting.isSelected(("Дистанция"))
                     ? Comparator.<LivingEntity>comparingDouble(candidateEntity -> CombatTargeting.distanceToBounds(candidateEntity))
                        .thenComparingDouble(candidateEntity -> CombatTargeting.displayedHealth(candidateEntity))
                     : (
                        this.targetSorting.isSelected(("Здоровье"))
                           ? Comparator.<LivingEntity>comparingDouble(candidateEntity -> CombatTargeting.displayedHealth(candidateEntity))
                              .thenComparingDouble(candidateEntity -> CombatTargeting.distanceToBounds(candidateEntity))
                           : Comparator.<LivingEntity>comparingDouble(candidateEntity -> this.angularDistance(candidateEntity))
                              .thenComparingDouble(candidateEntity -> CombatTargeting.distanceToBounds(candidateEntity))
                              .thenComparingDouble(candidateEntity -> CombatTargeting.displayedHealth(candidateEntity))
                              .thenComparingDouble(candidateEntity -> -candidateEntity.getArmor())
                     )
               )
         );
         this.target = (LivingEntity)candidates.get(0);
      }

      this.spookyRotation.reset();
      this.arenaSpookyRotation.reset();
      this.resetAimRotation();
      this.fastRotation.reset();
      this.legitRotation.reset();
      this.shard022Rotation.reset();
      this.builderRotation.reset();
      this.cometRotation.reset();
      this.shardTestRotation.reset();
   }

   private void updateNearbyMaceCandidate() {
      this.nearbyMaceCandidate = null;
      double nearestSquaredDistance = (1.7976931348623157E308D);

      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof LivingEntity candidate
            && this.isValidTargetAtRange(candidate, (64.0F))
            && !candidate.isBlocking()
            && !(Math.hypot(candidate.getX() - minecraftClient.player.getX(), candidate.getZ() - minecraftClient.player.getZ()) > (6.0D))
            && minecraftClient.player.squaredDistanceTo(candidate) < nearestSquaredDistance) {
            nearestSquaredDistance = minecraftClient.player.squaredDistanceTo(candidate);
            this.nearbyMaceCandidate = candidate;
         }
      }
   }

   private void resetAimRotation() {
      this.aimRotation.reset();
   }

   public boolean hasMaceEquippedOrQueued() {
      return this.autoMace.getValue() && this.maceEquipActive
         || MaceHelper.getInstance().isAutoMaceActive()
         || minecraftClient.player != null && minecraftClient.player.getMainHandStack().isOf(Items.MACE);
   }

   @Override
   protected void onEnable() {
      this.spookyRotation.reset();
      this.arenaSpookyRotation.reset();
      this.resetAimRotation();
      this.fastRotation.reset();
      this.legitRotation.reset();
      this.shard022Rotation.reset();
      this.builderRotation.reset();
      this.cometRotation.reset();
      this.shardTestRotation.reset();
   }

   public boolean isAirborneWithMace() {
      return this.hasMaceEquippedOrQueued() && minecraftClient.player != null && !minecraftClient.player.isOnGround();
   }
}
