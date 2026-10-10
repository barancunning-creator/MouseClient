package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.events.impl.AttackEvent;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.CombatAttackHelper;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.MaceFallPrediction;
import lombok.Generated;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
   name = "TriggerBot",
   description = "Бьёт того, на кого уже наведён прицел, таймингами ауры и без своего наведения.",
   category = ModuleCategory.COMBAT
)
public final class TriggerBot extends Module {
   private static TriggerBot INSTANCE;
   public DecimalSetting attackRange;
   public MultiSelectSetting attackTargets;
   public ModeSetting attackThroughBlocks;
   public MultiSelectSetting attackRestrictions;
   public MultiSelectSetting advancedSettings;
   public BooleanSetting autoMace;
   private LivingEntity target;
   private boolean autoMaceActive;
   private boolean maceCycleFinished;
   private int maceInventorySlot = -1;
   private int previousHotbarSlot = -1;
   private long freezeUntilMillis;
   private final EventListener<AttackEvent> attackListener = event -> {
      if (minecraftClient.player != null && this.advancedSettings.matchesOption(("Кип спринт"))) {
         minecraftClient.player.setSprinting(true);
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null && event.isOutgoing() && System.currentTimeMillis() < this.freezeUntilMillis) {
         if (event.getPacket() instanceof PositionAndOnGround || event.getPacket() instanceof Full) {
            event.markCancelled();
         }
      }
   };
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (minecraftClient.player != null && System.currentTimeMillis() < this.freezeUntilMillis) {
         event.setForward(0.0F);
         event.setStrafe(0.0F);
         event.setJumping(false);
         event.setSneaking(false);
      } else if (this.target != null && this.usesSmartCriticals()) {
         if (!(CombatTargeting.distanceToBounds(this.target) >= this.effectiveAttackRange())
            && CombatAttackHelper.shouldPauseMovementForCritical(this.target, this.attackBounds(), ("Легитный"))) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      this.target = null;
      if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.player.isAlive()) {
         if (System.currentTimeMillis() < this.freezeUntilMillis) {
            minecraftClient.player.setVelocity(Vec3d.ZERO);
         }

         this.updateAutoMace();
         if (!this.attackRestrictions.matchesOption(("Открыт инвентарь")) || minecraftClient.currentScreen == null) {
            if (this.attackRestrictions.matchesOption(("Ешь")) && minecraftClient.player.isUsingItem()) {
               ItemStack stack = minecraftClient.player.getActiveItem();
               if (!stack.isEmpty() && (stack.get(DataComponentTypes.FOOD) != null || stack.get(DataComponentTypes.POTION_CONTENTS) != null)) {
                  return;
               }
            }

            this.target = this.findCrosshairTarget();
            if (this.target != null) {
               CombatAttackHelper.updateRepeatAttacks(this.target, true, true, true);
               if (CombatAttackHelper.canAttack(this.target, true, true, this.usesSmartCriticals(), 0L, this.attackBounds())) {
                  CombatAttackHelper.performAttack(this.target, null, null, Hand.MAIN_HAND, false);
               }
            }
         }
      }
   };

   private TriggerBot() {
      this.attackRange = this.addSetting(new DecimalSetting(("Радиус удара"), ("На какой дистанции наносить удар."), (3.0D), (3.0D), (6.0D), (0.1D)));
      this.attackTargets = this.addSetting(new MultiSelectSetting(("Кого атаковать"), ("Каких существ считать целью."), new String[]{("Игроки"), ("Голые"), ("Мобы")}, ("Игроки"), ("Голые")));
      this.attackThroughBlocks = this.addSetting(new ModeSetting(("Бить через блоки"), ("Разрешить удар сквозь блоки."), ("Нет"), ("Нет"), ("Обычные"), ("Неполноценные")));
      this.attackRestrictions = this.addSetting(new MultiSelectSetting(("Не бить если"), ("Условия паузы удара."), new String[]{("Ешь"), ("Открыт инвентарь")}, ("Ешь"), ("Открыт инвентарь")));
      this.advancedSettings = this.addSetting(new MultiSelectSetting(("Настройки"), ("Дополнительные опции удара."), new String[]{("Умные криты"), ("Кип спринт")}, ("Умные криты")));
      this.autoMace = this.addSetting(new BooleanSetting(("Авто-булава"), ("Сама берёт булаву, бьёт смэшем при падении на цель и возвращает прежний предмет."), false));
      this.attackRange
         .withTextEntryCondition(
            () -> ServerHelper.getInstance().isEnabled()
               && ServerHelper.getInstance().server.isSelected(("SpookyTime"))
               && this.attackRange.getValue() > (3.65F)
         );
   }

   public boolean usesSmartCriticals() {
      return this.advancedSettings.matchesOption(("Умные криты"));
   }

   private LivingEntity findCrosshairTarget() {
      LivingEntity closestTarget = null;

      for (Entity worldEntity : minecraftClient.world.getEntities()) {
         if (worldEntity instanceof LivingEntity candidate
            && this.isValidTarget(candidate, this.attackRange.getValue())
            && CombatAttackHelper.isTargetUnderCrosshair(candidate, this.attackRange.getValue())
            && (closestTarget == null || CombatTargeting.distanceToBounds(candidate) < CombatTargeting.distanceToBounds(closestTarget))) {
            closestTarget = candidate;
         }
      }

      return closestTarget;
   }

   private double effectiveAttackRange() {
      return minecraftClient.player != null && minecraftClient.player.getMainHandStack().isOf(Items.MACE)
         ? (3.0D)
         : Math.max((3.0D), this.attackRange.getValue());
   }

   public boolean isAirborneWithMace() {
      return this.hasMaceEquippedOrQueued() && minecraftClient.player != null && !minecraftClient.player.isOnGround();
   }

   public boolean canDealSmashDamage() {
      return this.isAirborneWithMace() && MaceItem.shouldDealAdditionalDamage(minecraftClient.player);
   }

   public boolean hasMaceEquippedOrQueued() {
      return this.autoMace.getValue() && this.autoMaceActive || minecraftClient.player != null && minecraftClient.player.getMainHandStack().isOf(Items.MACE);
   }

   private void restoreAfterMace() {
      if (this.autoMaceActive && minecraftClient.player != null) {
         this.autoMaceActive = false;
         this.maceCycleFinished = true;
         if (this.maceInventorySlot > 8) {
            InventorySwapQueue.getInstance().queueSwap(this.previousHotbarSlot, this.maceInventorySlot, 1);
         } else if (this.previousHotbarSlot != -1) {
            InventoryActions.selectHotbarSlot(this.previousHotbarSlot, ServerHelper.getInstance().server.matchesOption(("SpookyTime")));
         }

         this.maceInventorySlot = -1;
         this.previousHotbarSlot = -1;
      }
   }

   private boolean hasMaceFallWindow() {
      if (this.autoMaceActive && !minecraftClient.player.isOnGround()) {
         return true;
      }

      if (minecraftClient.player.isOnGround()) {
         return false;
      }

      Vec3d landingPosition = MaceFallPrediction.landingPosition();
      return landingPosition == null
         ? minecraftClient.world.isSpaceEmpty(minecraftClient.player, minecraftClient.player.getBoundingBox().stretch(0.0, (-3.5D), 0.0))
         : minecraftClient.player.getY() + minecraftClient.player.getVelocity().y > landingPosition.y && minecraftClient.player.getY() - landingPosition.y > (3.5D);
   }

   private void updateAutoMace() {
      if (!ItemUseQueue.getInstance().hasPendingUses() && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
         if (!this.autoMace.getValue()) {
            this.restoreAfterMace();
         } else {
            if (minecraftClient.player.isOnGround() || minecraftClient.player.getVelocity().y >= 0.0) {
               this.maceCycleFinished = false;
            }

            if (!this.hasMaceFallWindow()) {
               this.restoreAfterMace();
            } else {
               int hotbarMaceSlot = InventoryActions.findHotbarItem(Items.MACE);
               int maceSlot = hotbarMaceSlot != -1 ? hotbarMaceSlot : InventoryActions.findItem(Items.MACE);
               if (maceSlot != -1
                  && !this.autoMaceActive
                  && !this.maceCycleFinished
                  && !minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getInventory().getStack(maceSlot))) {
                  this.autoMaceActive = true;
                  if (!minecraftClient.player.getMainHandStack().isOf(Items.MACE)) {
                     if (ServerHelper.getInstance().server.matchesOption(("SpookyTime"))
                        && !this.isAirborneWithMace()
                        && !MaceHelper.getInstance().isAutoMaceActive()
                        && !MaceHelper.getInstance().isWindBoostActive()
                        && minecraftClient.world.isSpaceEmpty(minecraftClient.player, minecraftClient.player.getBoundingBox().stretch(0.0, (-3.0D), 0.0))) {
                        this.freezeUntilMillis = System.currentTimeMillis() + (200L);
                     }

                     this.previousHotbarSlot = InventoryActions.selectedHotbarSlot();
                     this.maceInventorySlot = maceSlot;
                     if (maceSlot > 8) {
                        InventorySwapQueue.getInstance().queueSwap(maceSlot, InventoryActions.selectedHotbarSlot(), 1);
                     } else {
                        InventoryActions.selectHotbarSlot(maceSlot, ServerHelper.getInstance().server.matchesOption(("SpookyTime")));
                     }
                  }
               }
            }
         }
      }
   }

   public static TriggerBot getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new TriggerBot();
      }

      return INSTANCE;
   }

   @Override
   protected void onDisable() {
      this.target = null;
      this.freezeUntilMillis = 0L;
      this.restoreAfterMace();
   }

   private float[] attackBounds() {
      return new float[]{(float)this.effectiveAttackRange(), 0.0F, (float)this.effectiveAttackRange()};
   }

   private boolean isValidTarget(LivingEntity entity, float range) {
      if (entity != minecraftClient.player && entity.age >= 1 && !(entity instanceof ArmorStandEntity)) {
         if (minecraftClient.player.squaredDistanceTo(entity) > range * range) {
            return false;
         }

         if (this.attackThroughBlocks.isSelected(("Нет")) && !CombatTargeting.hasLineOfSight(minecraftClient.player.getEyePos(), entity.getEyePos())) {
            return false;
         }

         if (this.attackThroughBlocks.isSelected(("Неполноценные")) && !CombatTargeting.hasLineOfSightIgnoringPartialBlocks(minecraftClient.player.getEyePos(), entity.getEyePos())) {
            return false;
         }

         if (entity instanceof PlayerEntity) {
            if (FriendManager.contains(((PlayerEntity)entity).getGameProfile().name())) {
               return false;
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

   @Generated
   public LivingEntity target() {
      return this.target;
   }
}
