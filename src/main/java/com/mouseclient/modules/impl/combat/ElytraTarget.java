package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.RotationController;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.RotationAngles;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "ElytraTarget", description = "В полёте на элитрах ведёт прицел на цель и ускоряет фейерверком.", category = ModuleCategory.COMBAT)
public final class ElytraTarget extends Module {
   private static ElytraTarget INSTANCE;
   public DecimalSetting range;
   public DecimalSetting lead;
   public BooleanSetting firework;
   private LivingEntity target;
   private int boostCooldown;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> this.tick();
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (minecraftClient.player != null && minecraftClient.player.isGliding() && this.target != null && RotationController.getInstance().isActive()) {
         float aimYaw = (float)Math.toDegrees(Math.atan2(this.target.getZ() - minecraftClient.player.getZ(), this.target.getX() - minecraftClient.player.getX())) - 90.0F;
         RotationController.correctMovementForGliding(event, minecraftClient.player.getYaw(), MathHelper.wrapDegrees(aimYaw));
      }
   };

   private ElytraTarget() {
      this.range = this.addSetting(new DecimalSetting(("Радиус"), ("Как далеко брать цель в полёте."), (48.0D), (8.0D), (80.0D), (1.0D)));
      this.lead = this.addSetting(new DecimalSetting(("Упреждение"), ("На сколько тиков вперёд смотреть по скорости цели."), (5.0D), (0.0D), (16.0D), (1.0D)));
      this.firework = this.addSetting(new BooleanSetting(("Фейерверк"), ("Сам использует фейерверк, когда скорость падает."), true));
   }

   public static ElytraTarget getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ElytraTarget();
      }

      return INSTANCE;
   }

   public LivingEntity target() {
      return this.target;
   }

   @Override
   protected void onDisable() {
      this.target = null;
      this.boostCooldown = 0;
   }

   private void tick() {
      if (this.boostCooldown > 0) {
         this.boostCooldown--;
      }

      this.target = null;
      if (minecraftClient.player == null || minecraftClient.world == null || !minecraftClient.player.isAlive() || !minecraftClient.player.isGliding()) {
         return;
      }

      this.target = this.selectTarget();
      if (this.target == null
          || (CrystalAura.getInstance().isEnabled() && CrystalAura.getInstance().isAiming())
          || (AnchorAura.getInstance().isEnabled() && AnchorAura.getInstance().isAiming())) {
         return;
      }

      Vec3d predicted = this.target.getEntityPos().add(this.target.getVelocity().multiply(this.lead.getValue()));
      Vec3d aim = predicted.add(0.0, this.target.getHeight() * 0.62, 0.0);
      float[] angles = CombatTargeting.anglesBetween(aim, minecraftClient.player.getEyePos());
      RotationController.getInstance()
         .requestRotation(
            new RotationAngles(angles[0], MathHelper.clamp(angles[1], -90.0F, 90.0F)),
            new RotationLimits(36.0F, 20.0F, 14.0F, 10.0F),
            RotationController.ReturnMode.SMOOTH
         );
      this.boost();
   }

   private LivingEntity selectTarget() {
      LivingEntity best = null;
      double bestDistance = Double.MAX_VALUE;

      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof PlayerEntity player && this.valid(player)) {
            double distance = player.distanceTo(minecraftClient.player);
            if (distance < bestDistance) {
               best = player;
               bestDistance = distance;
            }
         }
      }

      return best;
   }

   private boolean valid(PlayerEntity player) {
      return player != minecraftClient.player
         && player.isAlive()
         && !player.isSpectator()
         && !player.isCreative()
         && !FriendManager.contains(player.getName().getString())
         && player.distanceTo(minecraftClient.player) <= this.range.getValue();
   }

   private void boost() {
      if (!this.firework.getValue() || this.boostCooldown > 0 || minecraftClient.player.getVelocity().horizontalLength() > 1.05) {
         return;
      }

      if (!ItemUseQueue.getInstance().hasUsableItem(Items.FIREWORK_ROCKET.getDefaultStack())) {
         return;
      }

      ItemUseQueue.getInstance().use(Items.FIREWORK_ROCKET.getDefaultStack(), true);
      this.boostCooldown = 12;
   }
}
