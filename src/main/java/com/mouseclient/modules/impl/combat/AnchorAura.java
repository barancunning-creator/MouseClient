package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.RotationController;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

@ModuleInfo(
   name = "AnchorAura",
   description = "Автоматически ставит, заряжает и взрывает якоря возрождения.",
   category = ModuleCategory.COMBAT
)
public final class AnchorAura extends Module {
   private static AnchorAura INSTANCE;
   public DecimalSetting range;
   public DecimalSetting minDamage;
   public DecimalSetting maxSelfDamage;
   public ModeSetting rotation;
   public BooleanSetting antiBurrow;
   public BooleanSetting sync;
   public DecimalSetting delay;
   public BooleanSetting pauseOnEat;

   private LivingEntity target;
   private int delayTicks = 0;
   private boolean aiming;
   private BlockPos lockedChargedAnchor;
   private BlockPos lockedUnchargedAnchor;
   private BlockPos lockedSpot;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> this.tick();

   private AnchorAura() {
      this.range = this.addSetting(new DecimalSetting("Радиус", "Дистанция поиска цели и установки якоря.", 5.0D, 3.0D, 8.0D, 0.1D));
      this.minDamage = this.addSetting(new DecimalSetting("Мин. урон", "Не взрывать, если урон по цели меньше порога.", 4.0D, 1.0D, 20.0D, 0.5D));
      this.maxSelfDamage = this.addSetting(new DecimalSetting("Макс. свой урон", "Не взрывать, если себе прилетит больше порога.", 8.0D, 0.0D, 20.0D, 0.5D));
      this.rotation = this.addSetting(new ModeSetting("Ротация", "Режим наведения при установке, зарядке и взрыве.", "Быстрая плавная", "Быстрая плавная", "Snap"));
      this.antiBurrow = this.addSetting(new BooleanSetting("Подкоп", "Ломать блок в или под ногах цели при бурове/сурраунде.", true));
      this.sync = this.addSetting(new BooleanSetting("Синхронизация", "Синхронизировать работу с CrystalAura, исключая конфликты.", true));
      this.delay = this.addSetting(new DecimalSetting("Задержка", "Задержка между действиями в тиках (0 = мгновенно).", 0.0D, 0.0D, 5.0D, 1.0D));
      this.pauseOnEat = this.addSetting(new BooleanSetting("Пауза при еде", "Приостанавливать действия при использовании еды или зелий.", true));
   }

   public static AnchorAura getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AnchorAura();
      }
      return INSTANCE;
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
      this.lockedChargedAnchor = null;
      this.lockedUnchargedAnchor = null;
      this.lockedSpot = null;
      AuraSync.setAnchorBusy(false);
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

      if (ItemUseQueue.getInstance().hasPendingUses() || InventorySwapQueue.getInstance().hasPendingSwaps()) {
         return;
      }

      if (AuraSync.isNether()) {
         AuraSync.setAnchorBusy(false);
         return;
      }

      if (this.sync.getValue() && AuraSync.isCrystalBusy()) {
         AuraSync.setAnchorBusy(false);
         return;
      }

      if (this.delayTicks > 0) {
         this.delayTicks--;
         return;
      }

      this.target = AuraSync.findTarget(this.range.getValue());
      if (this.target == null) {
         this.lockedChargedAnchor = null;
         this.lockedUnchargedAnchor = null;
         this.lockedSpot = null;
         AuraSync.setAnchorBusy(false);
         AuraSync.restoreSlot();
         if (RotationController.getInstance().isDecoupledSilent()) {
            RotationController.getInstance().reset();
         }
         return;
      }

      // Priority 1: Explode charged anchor
      BlockPos charged = (this.lockedChargedAnchor != null && this.isAnchor(this.lockedChargedAnchor, true))
         ? this.lockedChargedAnchor
         : this.bestAnchor(true);
      this.lockedChargedAnchor = charged;

      if (charged != null && this.holdExplodeHand()) {
         this.aiming = true;
         BlockHitResult hit = AuraSync.findBestBlockHit(charged);
         if (AuraSync.applyRotation(hit.getPos(), this.rotation.getValue())) {
            AuraSync.interactBlock(hit, Hand.MAIN_HAND);
            this.lockedChargedAnchor = null;
            AuraSync.setAnchorBusy(false);
            this.delayTicks = (int) this.delay.getValue();
         }
         return;
      }

      // Priority 2: Charge uncharged anchor with glowstone
      BlockPos uncharged = (this.lockedUnchargedAnchor != null && this.isAnchor(this.lockedUnchargedAnchor, false))
         ? this.lockedUnchargedAnchor
         : this.bestAnchor(false);
      this.lockedUnchargedAnchor = uncharged;

      if (uncharged != null && AuraSync.holdItem(Items.GLOWSTONE)) {
         this.aiming = true;
         BlockHitResult hit = AuraSync.findBestBlockHit(uncharged);
         if (AuraSync.applyRotation(hit.getPos(), this.rotation.getValue())) {
            Hand hand = minecraftClient.player.getOffHandStack().isOf(Items.GLOWSTONE) ? Hand.OFF_HAND : Hand.MAIN_HAND;
            AuraSync.interactBlock(hit, hand);
            this.lockedUnchargedAnchor = null;
            AuraSync.setAnchorBusy(true);
            this.delayTicks = (int) this.delay.getValue();
         }
         return;
      }

      // Priority 3: Anti-burrow
      if (this.antiBurrow.getValue()) {
         BlockPos burrow = AuraSync.findBurrowBlock(this.target);
         if (burrow != null && !minecraftClient.world.getBlockState(burrow).isOf(Blocks.RESPAWN_ANCHOR)
             && minecraftClient.player.getEyePos().distanceTo(burrow.toCenterPos()) <= this.range.getValue() + 0.6) {
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

      // Priority 4: Place anchor
      BlockPos spot = (this.lockedSpot != null && this.canPlaceAnchor(this.lockedSpot))
         ? this.lockedSpot
         : this.bestAnchorSpot();
      this.lockedSpot = spot;

      if (spot != null && AuraSync.holdItem(Items.RESPAWN_ANCHOR)) {
         this.aiming = true;
         BlockHitResult placement = AuraSync.findPlacementHit(spot);
         if (placement != null) {
            if (AuraSync.applyRotation(placement.getPos(), this.rotation.getValue())) {
               Hand hand = minecraftClient.player.getOffHandStack().isOf(Items.RESPAWN_ANCHOR) ? Hand.OFF_HAND : Hand.MAIN_HAND;
               AuraSync.interactBlock(placement, hand);
               this.lockedSpot = null;
               AuraSync.setAnchorBusy(true);
               this.delayTicks = (int) this.delay.getValue();
            }
         }
         return;
      }

      AuraSync.setAnchorBusy(false);
   }

   private BlockPos bestAnchor(boolean charged) {
      if (this.target == null || minecraftClient.player == null) {
         return null;
      }

      BlockPos origin = this.target.getBlockPos();
      BlockPos best = null;
      float bestScore = 0.0F;

      for (int x = -3; x <= 3; x++) {
         for (int y = -2; y <= 2; y++) {
            for (int z = -3; z <= 3; z++) {
               BlockPos pos = origin.add(x, y, z);
               if (this.isAnchor(pos, charged) && minecraftClient.player.getEyePos().distanceTo(pos.toCenterPos()) <= this.range.getValue() + 0.6) {
                  float score = ExplosionHelper.score(
                     pos.toCenterPos(),
                     5.0F,
                     this.target,
                     (float) this.minDamage.getValue(),
                     (float) this.maxSelfDamage.getValue(),
                     pos
                  );
                  if (score > bestScore) {
                     best = pos;
                     bestScore = score;
                  }
               }
            }
         }
      }

      return best;
   }

   private BlockPos bestAnchorSpot() {
      if (this.target == null || minecraftClient.player == null) {
         return null;
      }

      BlockPos origin = this.target.getBlockPos();
      BlockPos best = null;
      float bestScore = 0.0F;

      for (int x = -3; x <= 3; x++) {
         for (int y = -2; y <= 2; y++) {
            for (int z = -3; z <= 3; z++) {
               if (x == 0 && y == 0 && z == 0) {
                  continue;
               }

               BlockPos spot = origin.add(x, y, z);
               if (this.canPlaceAnchor(spot)) {
                  float score = ExplosionHelper.score(
                     spot.toCenterPos(),
                     5.0F,
                     this.target,
                     (float) this.minDamage.getValue(),
                     (float) this.maxSelfDamage.getValue(),
                     null
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

   private boolean isAnchor(BlockPos pos, boolean charged) {
      if (minecraftClient.world == null) {
         return false;
      }

      BlockState state = minecraftClient.world.getBlockState(pos);
      if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
         return false;
      }

      int charges = state.get(RespawnAnchorBlock.CHARGES);
      return charged ? charges > 0 : charges < RespawnAnchorBlock.MAX_CHARGES;
   }

   private boolean canPlaceAnchor(BlockPos spot) {
      if (minecraftClient.world == null || minecraftClient.player == null) {
         return false;
      }

      BlockState state = minecraftClient.world.getBlockState(spot);
      if (!state.isAir() && !state.isReplaceable()) {
         return false;
      }

      BlockHitResult placement = AuraSync.findPlacementHit(spot);
      if (placement == null || minecraftClient.player.getEyePos().distanceTo(placement.getPos()) > this.range.getValue() + 0.6) {
         return false;
      }

      Box box = new Box(spot);
      return minecraftClient.world.getOtherEntities(null, box).isEmpty();
   }

   private boolean holdExplodeHand() {
      if (minecraftClient.player == null) {
         return false;
      }

      if (!minecraftClient.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
         return true;
      }

      int swordSlot = InventoryActions.findHotbarItem(Items.NETHERITE_SWORD);
      if (swordSlot == -1) swordSlot = InventoryActions.findHotbarItem(Items.DIAMOND_SWORD);
      if (swordSlot == -1) swordSlot = InventoryActions.findHotbarItem(Items.TOTEM_OF_UNDYING);
      if (swordSlot == -1) swordSlot = InventoryActions.findHotbarItem(Items.RESPAWN_ANCHOR);

      if (swordSlot != -1) {
         AuraSync.selectSlot(swordSlot);
         return true;
      }

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = minecraftClient.player.getInventory().getStack(slot);
         if (!stack.isOf(Items.GLOWSTONE)) {
            AuraSync.selectSlot(slot);
            return true;
         }
      }

      return false;
   }
}
