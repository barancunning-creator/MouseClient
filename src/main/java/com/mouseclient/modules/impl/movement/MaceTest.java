package com.mouseclient.modules.impl.movement;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.LivingEntityAccessor;
import com.mouseclient.access.ItemCooldownAccess;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.combat.MovementInputEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.ToastNotifications;
import java.util.Locale;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(name = "MaceTest", description = "Порт MaceHelper из devmouseclient: заряд ветра по бинду и булава в падение.", category = ModuleCategory.MOVEMENT)
public final class MaceTest extends Module {
   private static MaceTest INSTANCE;
   private BindSetting windChargeBind;
   public BooleanSetting assistAuraHits;
   public DecimalSetting aimDistance;
   public BooleanSetting equipWhileFalling;
   public BooleanSetting autoEquipMace;
   private boolean windKeyHeld;
   private boolean windThrowRequested;
   private boolean unusedWindThrowCompleted;
   private boolean maceEquippedForFall;
   private boolean restoreRequested;
   private boolean equipAfterThrowRequested;
   private boolean autoMaceEquipped;
   private boolean autoMaceWasAirborne;
   private long lastWindThrowMillis;
   private int jumpDelayTicks;
   private int jumpInputTicks;
   private long windPacketDeadlineMillis;
   private float windYaw;
   private float windPitch;
   private int maceInventorySlot = -1;
   private int previousHotbarSlot = -1;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         if (this.windChargeBind.isBound()
            && this.windChargeBind.getBinding().isPressed(minecraftClient.getWindow().getHandle())
            && !this.windKeyHeld
            && minecraftClient.currentScreen == null
            && InventoryActions.findItem(Items.WIND_CHARGE) != -1) {
            this.windThrowRequested = true;
            this.unusedWindThrowCompleted = false;
         }

         this.windKeyHeld = this.windChargeBind.isBound() && this.windChargeBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (this.jumpDelayTicks > 0 && --this.jumpDelayTicks == 0 && minecraftClient.player.isOnGround()) {
            ((LivingEntityAccessor)(Object)minecraftClient.player).setNoJumpDelay(0);
            this.jumpInputTicks = 1;
         }

         if (this.equipAfterThrowRequested) {
            this.equipAfterThrowRequested = false;
            this.unusedWindThrowCompleted = true;
            this.autoEquipAfterThrow();
         }

         if (this.restoreRequested) {
            this.restoreRequested = false;
            this.restoreSlots();
         }

         if (this.autoMaceEquipped) {
            if (!minecraftClient.player.isOnGround()) {
               this.autoMaceWasAirborne = true;
            } else if (this.autoMaceWasAirborne) {
               this.autoMaceEquipped = false;
               this.autoMaceWasAirborne = false;
               this.restoreSlots();
            }
         }

         if (this.maceEquippedForFall) {
            if (minecraftClient.player.isOnGround()) {
               this.maceEquippedForFall = false;
               this.restoreRequested = true;
            }
         } else if (this.equipWhileFalling.getValue() && minecraftClient.currentScreen == null && !this.autoMaceEquipped) {
            if (!minecraftClient.player.isOnGround() && !(minecraftClient.player.fallDistance < (2.299999952316284D))) {
               if (minecraftClient.player.getMainHandStack().getItem() != Items.MACE) {
                  if (InventoryActions.findItem(Items.MACE) != -1) {
                     this.maceEquippedForFall = true;
                     this.equipMace();
                  }
               }
            }
         }
      }
   };
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (minecraftClient.player != null) {
         if (this.windThrowRequested) {
            this.lastWindThrowMillis = System.currentTimeMillis();
            if (AttackAura.getInstance().isEnabled() && AttackAura.getInstance().target() != null) {
               RotationController.getInstance().reset();
            }

            if (!ScreenWalk.getInstance().isSpookyTimeConfigured()) {
               RotationController.getInstance()
                  .requestRotation(
                     new RotationAngles(minecraftClient.player.getYaw(), (90.0F)),
                     new RotationLimits((360.0F), ServerHelper.getInstance().server.isSelected(("SpookyTime")) ? (360.0F) : (30.0F), (20.0F), (20.0F))
                  );
               if (!ServerHelper.getInstance().server.isSelected(("SpookyTime")) && Math.abs(minecraftClient.player.getPitch() - (90.0F)) >= (3.0F)) {
                  return;
               }
            }

            this.windThrowRequested = false;
            this.throwWindCharge();
         } else if (this.jumpInputTicks > 0) {
            this.jumpInputTicks--;
            event.setJumping(true);
         }
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null) {
         if (event.isOutgoing() && System.currentTimeMillis() < this.windPacketDeadlineMillis && event.getPacket() instanceof PlayerInteractItemC2SPacket interactionPacket) {
            event.setPacket(new PlayerInteractItemC2SPacket(interactionPacket.getHand(), interactionPacket.getSequence(), this.windYaw, this.windPitch));
            this.windPacketDeadlineMillis = 0L;
            this.jumpDelayTicks = 1;
         }
      }
   };

   @Override
   protected void onDisable() {
      this.windThrowRequested = false;
      this.unusedWindThrowCompleted = false;
      this.maceEquippedForFall = false;
      this.restoreRequested = false;
      this.equipAfterThrowRequested = false;
      this.windPacketDeadlineMillis = 0L;
      this.jumpDelayTicks = 0;
      this.jumpInputTicks = 0;
      this.autoMaceEquipped = false;
      this.autoMaceWasAirborne = false;
      if (minecraftClient.player != null) {
         this.restoreSlots();
      }
   }

   private boolean hasBlockAlongLook(float pitch, float yaw) {
      return minecraftClient.world
            .raycast(
               new RaycastContext(
                  minecraftClient.player.getEyePos(),
                  minecraftClient.player.getEyePos().add(new RotationAngles(yaw, pitch).direction().multiply((1.5D))),
                  ShapeType.COLLIDER,
                  FluidHandling.NONE,
                  minecraftClient.player
               )
            )
            .getType()
         == Type.BLOCK;
   }

   public boolean isWindThrowActive() {
      return this.windThrowRequested || System.currentTimeMillis() - this.lastWindThrowMillis < (300L);
   }

   private void autoEquipAfterThrow() {
      if (minecraftClient.player != null && this.autoEquipMace.getValue() && !this.autoMaceEquipped && !this.maceEquippedForFall) {
         int maceSlot = InventoryActions.findHotbarItem(Items.MACE) != -1 ? InventoryActions.findHotbarItem(Items.MACE) : InventoryActions.findItem(Items.MACE);
         if (maceSlot != -1) {
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
               this.autoMaceEquipped = true;
               this.autoMaceWasAirborne = false;
               this.equipMace();
            }
         }
      }
   }

   public boolean isMaceEquippedForFall() {
      return this.maceEquippedForFall;
   }

   private void equipMace() {
      if (minecraftClient.player != null && minecraftClient.player.getMainHandStack().getItem() != Items.MACE) {
         if (InventoryActions.findHotbarItem(Items.MACE) != -1) {
            this.previousHotbarSlot = InventoryActions.selectedHotbarSlot();
            InventoryActions.selectHotbarSlot(InventoryActions.findHotbarItem(Items.MACE), false);
         } else if (InventoryActions.findItem(Items.MACE) != -1) {
            this.maceInventorySlot = InventoryActions.findItem(Items.MACE);
            InventoryActions.swapWithHand(this.maceInventorySlot, Hand.MAIN_HAND);
            InventoryActions.closeInventory(true);
         }
      }
   }

   public static MaceTest getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new MaceTest();
      }

      return INSTANCE;
   }

   private MaceTest() {
      this.windChargeBind = this.addSetting(new BindSetting(("Кнопка"), ("Кидает заряд ветра под себя с прыжком и берёт булаву.")));
      this.assistAuraHits = this.addSetting(new BooleanSetting(("Помогать киллауре в попадании"), ("Отдаёт киллауре расширенный радиус наводки на время захода."), false));
      this.aimDistance = this.addSetting(new DecimalSetting(("Дистанция наводки"), ("На какой дистанции киллаура ищет цель во время захода."), (30.0D), (3.0D), (30.0D), (0.1D)));
      this.aimDistance.setVisibility(() -> this.assistAuraHits.getValue());
      this.equipWhileFalling = this.addSetting(new BooleanSetting(("Брать в падение"), ("Берёт булаву в руку, когда пролетел вниз 2.3 блока."), false));
      this.autoEquipMace = this.addSetting(new BooleanSetting(("Автовзятие булавы"), ("После взлёта сама переключается на булаву и держит её до приземления."), true));
   }

   private void throwWindCharge() {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         if (InventoryActions.findItem(Items.WIND_CHARGE) != -1
            && !minecraftClient.player
               .getItemCooldownManager()
               .isCoolingDown(minecraftClient.player.getInventory().getStack(InventoryActions.findItem(Items.WIND_CHARGE)))) {
            this.windYaw = minecraftClient.player.getYaw();
            this.windPitch = (90.0F);
            if (!minecraftClient.player.isOnGround()
               && minecraftClient.world.getBlockState(minecraftClient.player.getBlockPos().add(0, -2, 0)).isAir()
               && minecraftClient.player.getVelocity().y > (0.4D)) {
               this.windPitch = (75.0F);

               for (int yawSample = 0; yawSample < 360; yawSample += 45) {
                  if (this.hasBlockAlongLook(this.windPitch, yawSample)) {
                     this.windYaw = MathHelper.wrapDegrees(minecraftClient.player.getYaw() + yawSample);
                  }
               }
            }

            this.windPacketDeadlineMillis = System.currentTimeMillis() + (500L);
            RotationController.getInstance().reset();
            if (!ScreenWalk.getInstance().isSpookyTimeConfigured()) {
               RotationController.getInstance().requestRotation(new RotationAngles(this.windYaw, this.windPitch), new RotationLimits((360.0F), (360.0F), (20.0F), (20.0F)));
            }

            ItemUseQueue.getInstance().use(minecraftClient.player.getInventory().getStack(InventoryActions.findItem(Items.WIND_CHARGE)).copy(), true);
            this.equipAfterThrowRequested = true;
         }
      }
   }

   private void restoreSlots() {
      if (this.maceInventorySlot != -1) {
         InventoryActions.swapWithHand(this.maceInventorySlot, Hand.MAIN_HAND);
         InventoryActions.closeInventory(true);
         this.maceInventorySlot = -1;
      }

      if (this.previousHotbarSlot != -1) {
         InventoryActions.selectHotbarSlot(this.previousHotbarSlot, false);
         this.previousHotbarSlot = -1;
      }
   }
}
