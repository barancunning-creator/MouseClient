package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.LivingEntityAccessor;
import com.mouseclient.access.ItemCooldownAccess;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.ToastNotifications;
import java.util.Locale;
import lombok.Generated;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@ModuleInfo(name = "MaceHelper", description = "Взлёт на заряде ветра под смэш булавой по бинду.", category = ModuleCategory.COMBAT)
public final class MaceHelper extends Module {
   private static MaceHelper INSTANCE;
   public BindSetting windChargeBind;
   public BooleanSetting damageBoost;
   public BooleanSetting autoEquipMace;
   public BooleanSetting assistAuraAim;
   public BooleanSetting clearObstaclesAbove;
   private boolean bindWasDown;
   private boolean throwRequested;
   private boolean awaitingWindUsePacket;
   private int jumpDelayTicks = -1;
   private long lastCorrectionAtMillis;
   private boolean auraAssistActive;
   private boolean auraAssistWasAirborne;
   private boolean autoMaceActive;
   private int maceInventorySlot = -1;
   private int previousHotbarSlot = -1;
   private boolean windBoostActive;
   private boolean windBoostWasAirborne;
   private boolean equipMaceRequested;
   private boolean maceWasAirborne;
   private long forceDownwardUseUntilMillis;
   private long groundedSinceMillis;
   private long throwDeadlineMillis;
   private long boostGraceUntilMillis;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null && minecraftClient.world != null) {
         boolean bindPressed = this.windChargeBind.isBound() && this.windChargeBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (bindPressed && !this.bindWasDown && minecraftClient.currentScreen == null) {
            this.throwRequested = !this.throwRequested;
            this.throwDeadlineMillis = System.currentTimeMillis() + (1500L);
         }

         this.bindWasDown = bindPressed;
         if (!minecraftClient.player.isOnGround()) {
            this.groundedSinceMillis = 0L;
         } else if (this.groundedSinceMillis == 0L) {
            this.groundedSinceMillis = System.currentTimeMillis();
         }

         if (minecraftClient.player.isOnGround()
            && this.groundedSinceMillis != 0L
            && System.currentTimeMillis() - this.groundedSinceMillis > (500L)
            && !this.awaitingWindUsePacket
            && this.jumpDelayTicks < 0
            && System.currentTimeMillis() >= this.boostGraceUntilMillis) {
            this.windBoostActive = false;
            this.windBoostWasAirborne = false;
            this.auraAssistActive = false;
            this.auraAssistWasAirborne = false;
            if (this.autoMaceActive && minecraftClient.currentScreen == null && !ItemUseQueue.getInstance().hasPendingUses() && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
               this.restoreMaceSlot();
            }
         }

         if (this.auraAssistActive) {
            if (!minecraftClient.player.isOnGround()) {
               this.auraAssistWasAirborne = true;
            } else if (this.auraAssistWasAirborne) {
               this.auraAssistActive = false;
               this.auraAssistWasAirborne = false;
            }
         }

         if (this.windBoostActive) {
            if (!minecraftClient.player.isOnGround()) {
               this.windBoostWasAirborne = true;
            } else if (this.windBoostWasAirborne) {
               this.windBoostActive = false;
               this.windBoostWasAirborne = false;
               this.equipMaceRequested = false;
            }
         }

         if (this.autoMaceActive) {
            if (!minecraftClient.player.isOnGround()) {
               this.maceWasAirborne = true;
            } else if (this.maceWasAirborne && minecraftClient.currentScreen == null && !ItemUseQueue.getInstance().hasPendingUses() && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
               this.restoreMaceSlot();
            }
         }

         if (this.equipMaceRequested && !ItemUseQueue.getInstance().hasPendingUses() && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
            this.equipMaceRequested = false;
            this.equipMace();
         }

         if (this.awaitingWindUsePacket && (System.currentTimeMillis() > this.throwDeadlineMillis || InventoryActions.findItem(Items.WIND_CHARGE) == -1)) {
            this.cancelWindThrow(("бросок сорвался"));
         }

         if (this.throwRequested && System.currentTimeMillis() > this.throwDeadlineMillis) {
            this.cancelWindThrow(("не вышло кинуть"));
         }

         if (this.awaitingWindUsePacket) {
            RotationController.getInstance().requestRotation(new RotationAngles(RotationAngles.cameraYaw(), (90.0F)), new RotationLimits((360.0F), (360.0F), (44.0F), (44.0F)));
         }

         if (this.jumpDelayTicks > 0) {
            this.jumpDelayTicks--;
         }

         if (this.throwRequested) {
            int windChargeSlot = InventoryActions.findItem(Items.WIND_CHARGE);
            if (windChargeSlot == -1) {
               this.throwRequested = false;
               ToastNotifications.showStyledToast(this.category.iconGlyph(), ("Заряд ветра"), Text.literal(("нет в инвентаре")).setStyle(Style.EMPTY.withColor(Formatting.RED)), true);
            } else if (minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getInventory().getStack(windChargeSlot))) {
               this.throwRequested = false;
               ToastNotifications.showStyledToast(
                  this.category.iconGlyph(),
                  ("Заряд ветра"),
                  Text.literal(("в кд ещё "))
                     .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                     .append(
                        Text.literal(
                              String.format(
                                 Locale.US,
                                 ("%.1fс"),
                                 ((ItemCooldownAccess)minecraftClient.player.getItemCooldownManager())
                                       .remainingCooldownTicks(minecraftClient.player.getInventory().getStack(windChargeSlot))
                                    / (20.0F)
                              )
                           )
                           .setStyle(Style.EMPTY.withColor(Formatting.RED))
                     ),
                  true
               );
            } else if (this.clearObstaclesAbove.getValue()
               && !minecraftClient.world.isSpaceEmpty(minecraftClient.player, minecraftClient.player.getBoundingBox().stretch(0.0, (3.0D), 0.0))) {
               this.throwRequested = false;
               ToastNotifications.showStyledToast(this.category.iconGlyph(), ("Заряд ветра"), Text.literal(BLOCKED_ABOVE_TEXT).setStyle(Style.EMPTY.withColor(Formatting.RED)), true);
            } else {
               RotationController.getInstance().requestRotation(new RotationAngles(RotationAngles.cameraYaw(), WIND_THROW_PITCH), new RotationLimits((360.0F), (360.0F), (44.0F), (44.0F)));
               if (!(minecraftClient.player.getPitch() < MIN_THROW_PITCH)) {
                  if (minecraftClient.player.isOnGround() && System.currentTimeMillis() - this.groundedSinceMillis >= (40L)) {
                     this.throwRequested = false;
                     this.awaitingWindUsePacket = true;
                     this.throwDeadlineMillis = System.currentTimeMillis() + (800L);
                     this.groundedSinceMillis = System.currentTimeMillis();
                     int selectedSlotBeforeThrow = InventoryActions.selectedHotbarSlot();
                     this.windBoostActive = true;
                     this.windBoostWasAirborne = false;
                     if (this.assistAuraAim.getValue()) {
                        this.auraAssistActive = true;
                        this.auraAssistWasAirborne = false;
                     }

                     this.equipMaceRequested = true;
                     ItemUseQueue.getInstance().enqueue(minecraftClient.player.getInventory().getStack(windChargeSlot).copy());
                  }
               }
            }
         }
      }
   };
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (minecraftClient.player != null) {
         if (this.throwRequested || this.awaitingWindUsePacket || this.jumpDelayTicks > 0) {
            event.setJumping(false);
         }

         if (this.awaitingWindUsePacket || this.jumpDelayTicks > 0) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
         }

         if (this.jumpDelayTicks == 0) {
            this.jumpDelayTicks = -1;
            ((LivingEntityAccessor)(Object)minecraftClient.player).setNoJumpDelay(0);
            event.setJumping(true);
         }
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isOutgoing()
         && minecraftClient.player != null
         && System.currentTimeMillis() < this.forceDownwardUseUntilMillis
         && event.getPacket() instanceof PlayerInteractItemC2SPacket forcedUsePacket) {
         event.setPacket(new PlayerInteractItemC2SPacket(forcedUsePacket.getHand(), forcedUsePacket.getSequence(), forcedUsePacket.getYaw(), (90.0F)));
         this.forceDownwardUseUntilMillis = 0L;
      }

      if (event.isOutgoing()
         && minecraftClient.player != null
         && event.getPacket() instanceof PlayerInteractItemC2SPacket windUsePacket
         && (minecraftClient.player.getStackInHand(windUsePacket.getHand()).isOf(Items.WIND_CHARGE) || this.awaitingWindUsePacket)) {
         this.awaitingWindUsePacket = false;
         this.jumpDelayTicks = 1;
         this.boostGraceUntilMillis = System.currentTimeMillis() + (600L);
      }

      if (event.isIncoming()
         && minecraftClient.player != null
         && event.getPacket() instanceof ExplosionS2CPacket explosion
         && explosion.playerKnockback().isPresent()
         && explosion.center().squaredDistanceTo(minecraftClient.player.getEntityPos()) < (64.0D)) {
         this.boostGraceUntilMillis = 0L;
      }

      if (event.isIncoming() && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
         this.lastCorrectionAtMillis = System.currentTimeMillis();
      }
   };

   private static final String BLOCKED_ABOVE_TEXT = "сверху блоки";
   private static final float WIND_THROW_PITCH = 90.0F;

   private static final float MIN_THROW_PITCH = 85.0F;

   @Generated
   public boolean isAutoMaceActive() {
      return this.autoMaceActive;
   }

   public static MaceHelper getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new MaceHelper();
      }

      return INSTANCE;
   }

   private void equipMace() {
      int hotbarMaceSlot = InventoryActions.findHotbarItem(Items.MACE);
      int maceSlot = hotbarMaceSlot != -1 ? hotbarMaceSlot : InventoryActions.findItem(Items.MACE);
      if (this.autoEquipMace.getValue() && minecraftClient.player != null && !this.autoMaceActive && maceSlot != -1) {
         if (minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getInventory().getStack(maceSlot))) {
            ToastNotifications.showStyledToast(
               this.category.iconGlyph(),
               ("Булава"),
               Text.literal(("в кд ещё "))
                  .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                  .append(
                     Text.literal(
                           String.format(
                              Locale.US,
                              ("%.1fс"),
                              ((ItemCooldownAccess)minecraftClient.player.getItemCooldownManager())
                                    .remainingCooldownTicks(minecraftClient.player.getInventory().getStack(maceSlot))
                                 / (20.0F)
                           )
                        )
                        .setStyle(Style.EMPTY.withColor(Formatting.RED))
                  ),
               true
            );
         } else {
            this.autoMaceActive = true;
            this.maceWasAirborne = false;
            if (!minecraftClient.player.getMainHandStack().isOf(Items.MACE)) {
               this.previousHotbarSlot = InventoryActions.selectedHotbarSlot();
               this.maceInventorySlot = maceSlot;
               if (maceSlot > 8) {
                  InventorySwapQueue.getInstance().queueSwap(maceSlot, InventoryActions.selectedHotbarSlot(), 2);
               } else {
                  InventoryActions.selectHotbarSlot(maceSlot, false);
               }
            }
         }
      }
   }

   private void cancelWindThrow(String reason) {
      this.throwRequested = false;
      this.awaitingWindUsePacket = false;
      this.forceDownwardUseUntilMillis = 0L;
      this.boostGraceUntilMillis = 0L;
      if (minecraftClient.player.isOnGround()) {
         this.equipMaceRequested = false;
         this.windBoostActive = false;
         this.windBoostWasAirborne = false;
         ToastNotifications.showStyledToast(this.category.iconGlyph(), ("Заряд ветра"), Text.literal(reason).setStyle(Style.EMPTY.withColor(Formatting.RED)), true);
      }
   }

   private MaceHelper() {
      this.windChargeBind = this.addSetting(new BindSetting(("Кинуть заряд ветра"), ("Кидает заряд ветра под себя с прыжком для максимального взлёта.")));
      this.damageBoost = this.addSetting(new BooleanSetting(("Усиление урона"), ("Держит удар булавой до самого приземления, если падаешь на цель смэш пройдёт с максимальным падением."), true));
      this.autoEquipMace = this.addSetting(new BooleanSetting(("Автовзятие булавы"), ("После взлёта сама переключается на булаву и держит её до приземления."), true));
      this.assistAuraAim = this.addSetting(new BooleanSetting(("Помогать AttackAura в наведении"), ("После взлёта расширяет радиус прицеливания булавы до приземления."), true));
      this.clearObstaclesAbove = this.addSetting(new BooleanSetting(("Обходить препятствие сверху"), ("Не кидает, если над головой блоки и взлёт упрётся в потолок."), true));
   }

   private void restoreMaceSlot() {
      if (this.autoMaceActive && minecraftClient.player != null) {
         this.autoMaceActive = false;
         if (this.maceInventorySlot > 8) {
            InventorySwapQueue.getInstance().queueSwap(this.previousHotbarSlot, this.maceInventorySlot, 2);
         } else if (this.previousHotbarSlot != -1) {
            InventoryActions.selectHotbarSlot(this.previousHotbarSlot, false);
         }

         this.maceInventorySlot = -1;
         this.previousHotbarSlot = -1;
      }
   }

   public boolean isThrowInProgress() {
      return this.throwRequested || this.awaitingWindUsePacket;
   }

   @Override
   protected void onDisable() {
      this.restoreMaceSlot();
      this.throwRequested = false;
      this.awaitingWindUsePacket = false;
      this.jumpDelayTicks = -1;
      this.auraAssistActive = false;
      this.auraAssistWasAirborne = false;
      this.windBoostActive = false;
      this.windBoostWasAirborne = false;
      this.equipMaceRequested = false;
      this.maceWasAirborne = false;
      this.forceDownwardUseUntilMillis = 0L;
      this.groundedSinceMillis = 0L;
      this.throwDeadlineMillis = 0L;
      this.boostGraceUntilMillis = 0L;
   }

   @Generated
   public boolean isWindBoostActive() {
      return this.windBoostActive;
   }
}
