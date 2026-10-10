package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.RotationController;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
   name = "CrystalAura",
   description = "Ставит кристалл у цели и сразу взрывает его.",
   category = ModuleCategory.COMBAT
)
public final class CrystalAura extends Module {
   private static CrystalAura INSTANCE;
   public DecimalSetting range;
   public DecimalSetting minDamage;
   public DecimalSetting maxSelfDamage;
   public ModeSetting rotation;
   public BooleanSetting antiBurrow;
   public BooleanSetting placeObsidian;
   public BooleanSetting sync;
   public DecimalSetting delay;
   public BooleanSetting pauseOnEat;
   public BooleanSetting anchorAura;

   private LivingEntity target;
   private int delayTicks = 0;
   private boolean aiming;
   private BlockPos lockedBase;
   private BlockPos lockedObsidian;
   private EndCrystalEntity lockedCrystal;
   private final EventListener<com.mouseclient.network.PlayerTickEvent> playerTickListener = event -> this.tick();

   private CrystalAura() {
      this.range = this.addSetting(new DecimalSetting("Радиус", "Как далеко искать цель и ставить кристалл.", 5.0D, 3.0D, 8.0D, 0.1D));
      this.minDamage = this.addSetting(new DecimalSetting("Мин. урон", "Не бить, если по цели выйдет меньше.", 4.0D, 1.0D, 20.0D, 0.5D));
      this.maxSelfDamage = this.addSetting(new DecimalSetting("Макс. свой урон", "Не взрывать, если себе прилетит больше.", 8.0D, 0.0D, 20.0D, 0.5D));
      this.rotation = this.addSetting(new ModeSetting("Ротация", "Режим наводки при установке и взрыве.", "Быстрая плавная", "Быстрая плавная", "Snap"));
      this.antiBurrow = this.addSetting(new BooleanSetting("Подкоп", "Ломать блок в или под ногах цели при бурове/сурраунде.", true));
      this.placeObsidian = this.addSetting(new BooleanSetting("Ставить обсидиан", "Кладёт обсидиан рядом с целью, если базы нет.", true));
      this.sync = this.addSetting(new BooleanSetting("Синхронизация", "Синхронизировать работу с AnchorAura и AttackAura.", true));
      this.delay = this.addSetting(new DecimalSetting("Задержка", "Задержка между действиями в тиках (0 = мгновенно).", 0.0D, 0.0D, 5.0D, 1.0D));
      this.pauseOnEat = this.addSetting(new BooleanSetting("Пауза при еде", "Приостанавливать действия при еде или зельях.", true));
      this.anchorAura = this.addSetting(new BooleanSetting("AnchorAura", "Вместо кристаллов использовать режим якорей.", false));
      this.placeObsidian.visibleWhen(() -> !this.anchorAura.getValue());
   }

   public static CrystalAura getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new CrystalAura();
      }
      return INSTANCE;
   }

   public LivingEntity target() {
      return this.target;
   }

   public LivingEntity getTarget() {
      return this.target;
   }

   public boolean isAiming() {
      return this.aiming;
   }

   @Override
   protected void onDisable() {
      this.target = null;
      this.aiming = false;
      this.delayTicks = 0;
      this.lockedBase = null;
      this.lockedObsidian = null;
      this.lockedCrystal = null;
      AuraSync.setCrystalBusy(false);
      AuraSync.setMiningBusy(false);
      AuraSync.restoreSlot();
      if (RotationController.getInstance().isDecoupledSilent()) {
         RotationController.getInstance().reset();
      }
   }

   private void tick() {
      this.aiming = false;
      if (minecraftClient.player == null || minecraftClient.world == null || minecraftClient.interactionManager == null || !minecraftClient.player.isAlive()) {
         return;
      }

      if (minecraftClient.currentScreen != null) {
         return;
      }

      if (this.pauseOnEat.getValue() && minecraftClient.player.isUsingItem()) {
         return;
      }

      if (com.mouseclient.network.ItemUseQueue.getInstance().hasPendingUses() || com.mouseclient.network.InventorySwapQueue.getInstance().hasPendingSwaps()) {
         return;
      }

      if (this.anchorAura.getValue()) {
         if (!AnchorAura.getInstance().isEnabled()) {
            AnchorAura.getInstance().setEnabled(true);
         }
         return;
      }

      if (this.sync.getValue() && !AuraSync.isNether() && AuraSync.isAnchorBusy()) {
         AuraSync.setCrystalBusy(false);
         return;
      }

      if (this.delayTicks > 0) {
         this.delayTicks--;
         return;
      }

      this.target = AuraSync.findTarget(this.range.getValue());
      if (this.target == null) {
         this.lockedBase = null;
         this.lockedObsidian = null;
         this.lockedCrystal = null;
         AuraSync.setCrystalBusy(false);
         AuraSync.restoreSlot();
         if (RotationController.getInstance().isDecoupledSilent()) {
            RotationController.getInstance().reset();
         }
         return;
      }

      // Priority 1: Detonate existing end crystal
      EndCrystalEntity crystal = (this.lockedCrystal != null && this.lockedCrystal.isAlive() && !this.lockedCrystal.isRemoved())
         ? this.lockedCrystal
         : this.bestCrystal();
      this.lockedCrystal = crystal;

      if (crystal != null) {
         this.aiming = true;
         Vec3d point = crystal.getBoundingBox().getCenter();
         if (AuraSync.applyRotation(point, this.rotation.getValue())) {
            AuraSync.attackEntity(crystal);
            this.lockedCrystal = null;
            AuraSync.setCrystalBusy(false);
            this.delayTicks = (int) this.delay.getValue();
         }
         return;
      }

      // Priority 2: Anti-burrow if enemy enclosed
      if (this.antiBurrow.getValue()) {
         BlockPos burrow = AuraSync.findBurrowBlock(this.target);
         if (burrow != null && minecraftClient.player.getEyePos().distanceTo(burrow.toCenterPos()) <= this.range.getValue() + 0.6) {
            this.aiming = true;
            AuraSync.setMiningBusy(true);
            if (AuraSync.performMine(burrow, this.rotation.getValue())) {
               this.delayTicks = (int) this.delay.getValue();
               return;
            }
         } else {
            AuraSync.setMiningBusy(false);
         }
      }

      // Priority 3: Place crystal on solid obsidian/bedrock base
      BlockPos base = (this.lockedBase != null && this.canCrystal(this.lockedBase))
         ? this.lockedBase
         : this.bestBase();
      this.lockedBase = base;

      if (base != null && AuraSync.holdItem(Items.END_CRYSTAL)) {
         this.aiming = true;
         BlockHitResult hit = AuraSync.findBestBlockHit(base);
         if (AuraSync.applyRotation(hit.getPos(), this.rotation.getValue())) {
            Hand hand = minecraftClient.player.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : Hand.MAIN_HAND;
            AuraSync.interactBlock(hit, hand);
            this.lockedBase = null;
            AuraSync.setCrystalBusy(true);
            this.delayTicks = (int) this.delay.getValue();
         }
         return;
      }

      // Priority 4: Place obsidian base if needed
      if (this.placeObsidian.getValue()) {
         BlockPos spot = (this.lockedObsidian != null && this.canObsidian(this.lockedObsidian))
            ? this.lockedObsidian
            : this.bestObsidian();
         this.lockedObsidian = spot;

         if (spot != null && AuraSync.holdItem(Items.OBSIDIAN)) {
            this.aiming = true;
            BlockHitResult placement = AuraSync.findPlacementHit(spot);
            if (placement != null) {
               if (AuraSync.applyRotation(placement.getPos(), this.rotation.getValue())) {
                  Hand hand = minecraftClient.player.getOffHandStack().isOf(Items.OBSIDIAN) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                  AuraSync.interactBlock(placement, hand);
                  this.lockedObsidian = null;
                  this.delayTicks = (int) this.delay.getValue();
               }
            }
         }
      }
   }

   private EndCrystalEntity bestCrystal() {
      if (this.target == null || minecraftClient.player == null || minecraftClient.world == null) {
         return null;
      }

      EndCrystalEntity best = null;
      float bestScore = 0.0F;

      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof EndCrystalEntity crystal && crystal.isAlive()) {
            Vec3d center = crystal.getEntityPos();
            if (minecraftClient.player.getEyePos().distanceTo(center) <= this.range.getValue() + 1.2) {
               float score = ExplosionHelper.score(
                  center,
                  6.0F,
                  this.target,
                  (float) this.minDamage.getValue(),
                  (float) this.maxSelfDamage.getValue(),
                  null
               );
               if (score > bestScore) {
                  best = crystal;
                  bestScore = score;
               }
            }
         }
      }

      return best;
   }

   private BlockPos bestBase() {
      if (this.target == null || minecraftClient.player == null) {
         return null;
      }

      BlockPos origin = this.target.getBlockPos();
      BlockPos best = null;
      float bestScore = 0.0F;

      for (int x = -3; x <= 3; x++) {
         for (int y = -2; y <= 2; y++) {
            for (int z = -3; z <= 3; z++) {
               BlockPos base = origin.add(x, y, z);
               if (this.canCrystal(base)) {
                  Vec3d explosion = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
                  float score = ExplosionHelper.score(
                     explosion,
                     6.0F,
                     this.target,
                     (float) this.minDamage.getValue(),
                     (float) this.maxSelfDamage.getValue(),
                     base
                  );
                  if (score > bestScore) {
                     best = base;
                     bestScore = score;
                  }
               }
            }
         }
      }

      return best;
   }

   private BlockPos bestObsidian() {
      if (this.target == null || minecraftClient.player == null) {
         return null;
      }

      BlockPos origin = this.target.getBlockPos();
      BlockPos best = null;
      float bestScore = 0.0F;

      for (int x = -3; x <= 3; x++) {
         for (int y = -2; y <= 1; y++) {
            for (int z = -3; z <= 3; z++) {
               if (x == 0 && z == 0) {
                  continue;
               }

               BlockPos spot = origin.add(x, y, z);
               if (this.canObsidian(spot)) {
                  Vec3d explosion = new Vec3d(spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5);
                  float score = ExplosionHelper.score(
                     explosion,
                     6.0F,
                     this.target,
                     (float) this.minDamage.getValue(),
                     (float) this.maxSelfDamage.getValue(),
                     spot
                  );
                  if (score > bestScore) {
                     best = spot;
                     bestScore = score;
                  }
               }
            }
         }
      }

      return best;
   }

   private boolean canCrystal(BlockPos base) {
      if (minecraftClient.world == null || minecraftClient.player == null) {
         return false;
      }

      if (!this.baseBlock(base) || this.crystalAt(base) || !this.clear(base)) {
         return false;
      }

      BlockHitResult hit = AuraSync.findBestBlockHit(base);
      if (hit == null || minecraftClient.player.getEyePos().distanceTo(hit.getPos()) > this.range.getValue() + 0.6) {
         return false;
      }

      Vec3d explosion = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);
      return ExplosionHelper.score(
         explosion,
         6.0F,
         this.target,
         (float) this.minDamage.getValue(),
         (float) this.maxSelfDamage.getValue(),
         base
      ) > 0.0F;
   }

   private boolean canObsidian(BlockPos spot) {
      if (minecraftClient.world == null || minecraftClient.player == null) {
         return false;
      }

      BlockState state = minecraftClient.world.getBlockState(spot);
      if (!state.isAir() && !state.isReplaceable()) {
         return false;
      }

      if (!this.clear(spot)) {
         return false;
      }

      BlockHitResult placement = AuraSync.findPlacementHit(spot);
      if (placement == null || minecraftClient.player.getEyePos().distanceTo(placement.getPos()) > this.range.getValue() + 0.6) {
         return false;
      }

      Vec3d explosion = new Vec3d(spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5);
      return ExplosionHelper.score(
         explosion,
         6.0F,
         this.target,
         (float) this.minDamage.getValue(),
         (float) this.maxSelfDamage.getValue(),
         spot
      ) > 0.0F;
   }

   private boolean baseBlock(BlockPos pos) {
      if (minecraftClient.world == null) return false;
      BlockState state = minecraftClient.world.getBlockState(pos);
      return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
   }

   private boolean clear(BlockPos base) {
      if (minecraftClient.world == null) return false;
      BlockPos up = base.up();
      BlockState state = minecraftClient.world.getBlockState(up);
      if (!state.isAir() && !state.isReplaceable()) {
         return false;
      }

      Box box = new Box(up);
      return minecraftClient.world.getOtherEntities(null, box, entity -> entity.isAlive() && !(entity instanceof EndCrystalEntity)).isEmpty();
   }

   private boolean crystalAt(BlockPos base) {
      if (minecraftClient.world == null) return false;
      Vec3d expected = new Vec3d(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5);

      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof EndCrystalEntity && entity.isAlive() && entity.getEntityPos().squaredDistanceTo(expected) < 1.0) {
            return true;
         }
      }

      return false;
   }
}
