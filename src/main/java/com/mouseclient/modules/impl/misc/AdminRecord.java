package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.CombatAttackHelper;
import com.mouseclient.network.RotationController;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.MouseSensitivity;
import com.mouseclient.util.entity.MinecraftAccess;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Handler;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
   name = "AdminRecord",
   description = "Пишет твои движения мышью по yaw и pitch, чтобы разобрать, как двигается рука.",
   category = ModuleCategory.MISC
)
public final class AdminRecord extends Module {
   private static AdminRecord INSTANCE;
   public BooleanSetting manualOnly;
   public BooleanSetting nearTargetOnly;
   private float lastYaw;
   private float lastPitch;
   private float lastTargetYaw;
   private float lastTargetPitch;
   private int lastLookTick;
   private int lastTargetTick;
   private int lastAttackTick;
   private int rowsWritten;
   private boolean lookInitialized;
   private Path outputPath;
   private BufferedWriter writer;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && this.lookInitialized) {
         if (minecraftClient.player.age - this.lastLookTick == 2) {
            this.recordRotationSample(minecraftClient.player.age - 1, 0.0F, 0.0F);
         }
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null && event.isOutgoing()) {
         if (event.getPacket() instanceof PlayerMoveC2SPacket lookPacket && lookPacket.changesLook()) {
            this.recordOutgoingLook(lookPacket.getYaw(0.0F), lookPacket.getPitch(0.0F));
         } else if (event.getPacket() instanceof PlayerInteractEntityC2SPacket interactionPacket) {
            interactionPacket.handle(new Handler() {
               @Override
               public void interactAt(Hand hand, Vec3d position) {
               }

               @Override
               public void interact(Hand hand) {
               }

               @Override
               public void attack() {
                  AdminRecord.this.lastAttackTick = MinecraftAccess.minecraftClient.player.age;
               }
            });
         }
      }
   };

   private void closeWriter() {
      try {
         if (this.writer != null) {
            this.writer.close();
         }
      } catch (IOException error) {
      }

      this.writer = null;
   }

   private void appendRow(String row) {
      try {
         if (this.writer == null) {
            Path outputDirectory = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("adminrecord"));
            Files.createDirectories(outputDirectory);
            this.outputPath = outputDirectory.resolve("rec-" + new SimpleDateFormat(("yyyyMMdd-HHmmss")).format(new Date()) + ".csv");
            this.writer = Files.newBufferedWriter(this.outputPath, StandardCharsets.UTF_8);
            this.writer.write(("time;tick;yaw;pitch;dyaw;dpitch;stepsYaw;stepsPitch;target;distance;flat;errYaw;errPitch;height;velYaw;velPitch;targetGround;ground;sprint;attack;strike;cooldown;msToReady;sinceAttack;swing;hurt;item;attackSpeed\n"));
            ChatMessages.printPrefixed("&7AdminRecord пишет в &f" + this.outputPath);
         }

         this.writer.write(row);
         if (++this.rowsWritten % 40 == 0) {
            this.writer.flush();
         }
      } catch (IOException error) {
         this.closeWriter();
         ChatMessages.printPrefixed("&cAdminRecord: запись не идёт — " + error.getMessage());
      }
   }

   public static AdminRecord getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AdminRecord();
      }

      return INSTANCE;
   }

   private LivingEntity nearestTarget() {
      if (AttackAura.getInstance().isEnabled() && AttackAura.getInstance().target() != null) {
         return AttackAura.getInstance().target();
      }

      PlayerEntity closestPlayer = null;

      for (PlayerEntity candidate : minecraftClient.world.getPlayers()) {
         if (candidate != minecraftClient.player
            && candidate.isAlive()
            && minecraftClient.player.distanceTo(candidate) < (6.0F)
            && (closestPlayer == null || minecraftClient.player.distanceTo(candidate) < minecraftClient.player.distanceTo(closestPlayer))) {
            closestPlayer = candidate;
         }
      }

      return closestPlayer;
   }

   @Override
   protected void onDisable() {
      ChatMessages.printPrefixed(this.writer == null ? ("&7AdminRecord: ничего не записано") : String.format(("&7AdminRecord: записано &f%d &7тиков в &f%s"), this.rowsWritten, this.outputPath));
      this.closeWriter();
   }

   private void recordRotationSample(int tick, float yawDelta, float pitchDelta) {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         if (!this.manualOnly.getValue() || !RotationController.getInstance().isActive()) {
            LivingEntity entity = this.nearestTarget();
            if (entity != null || !this.nearTargetOnly.getValue()) {
               float sensitivityStep = MouseSensitivity.rotationStep();
               float yawError = 0.0F;
               float pitchError = 0.0F;
               float targetYawVelocity = 0.0F;
               float targetPitchVelocity = 0.0F;
               float relativeTargetHeight = 0.0F;
               double distanceToBounds = (-1.0D);
               double horizontalDistance = (-1.0D);
               if (entity != null) {
                  Vec3d eyePosition = minecraftClient.player.getEyePos();
                  Vec3d targetCenter = entity.getBoundingBox().getCenter();
                  horizontalDistance = Math.hypot(targetCenter.x - eyePosition.x, targetCenter.z - eyePosition.z);
                  distanceToBounds = CombatTargeting.distanceToBounds(entity);
                  relativeTargetHeight = (float)(targetCenter.y - eyePosition.y);
                  float targetYaw = (float)Math.toDegrees(Math.atan2(targetCenter.z - eyePosition.z, targetCenter.x - eyePosition.x)) - (90.0F);
                  float targetPitch = (float)(-Math.toDegrees(Math.atan2(targetCenter.y - eyePosition.y, Math.max((0.6D), horizontalDistance))));
                  yawError = MathHelper.wrapDegrees(targetYaw - this.lastYaw);
                  pitchError = targetPitch - this.lastPitch;
                  if (tick - this.lastTargetTick == 1) {
                     targetYawVelocity = MathHelper.wrapDegrees(targetYaw - this.lastTargetYaw);
                     targetPitchVelocity = targetPitch - this.lastTargetPitch;
                  }

                  this.lastTargetYaw = targetYaw;
                  this.lastTargetPitch = targetPitch;
                  this.lastTargetTick = tick;
               }

               this.appendRow(
                  String.format(
                     Locale.ROOT,
                     ("%d;%d;%.3f;%.3f;%.3f;%.3f;%d;%d;%s;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f;%d;%d;%d;%d;%d;%.3f;%.0f;%d;%d;%d;%s;%.2f%n"),
                     System.currentTimeMillis(),
                     tick,
                     this.lastYaw,
                     this.lastPitch,
                     yawDelta,
                     pitchDelta,
                     Math.round(yawDelta / sensitivityStep),
                     Math.round(pitchDelta / sensitivityStep),
                     entity == null ? ("-") : entity.getName().getString(),
                     distanceToBounds,
                     horizontalDistance,
                     yawError,
                     pitchError,
                     relativeTargetHeight,
                     targetYawVelocity,
                     targetPitchVelocity,
                     entity != null && entity.isOnGround() ? 1 : 0,
                     minecraftClient.player.isOnGround() ? 1 : 0,
                     minecraftClient.player.isSprinting() ? 1 : 0,
                     this.lastAttackTick == tick ? 1 : 0,
                     CombatAttackHelper.isCooldownReady((-150L)) ? 1 : 0,
                     minecraftClient.player.getAttackCooldownProgress(0.0F),
                     Math.max(
                        0.0F,
                        ((0.9F) - minecraftClient.player.getAttackCooldownProgress(0.0F))
                           * (1000.0F)
                           / (float)minecraftClient.player.getAttributeValue(EntityAttributes.ATTACK_SPEED)
                     ),
                     Math.min(100, tick - this.lastAttackTick),
                     minecraftClient.player.handSwingTicks,
                     entity == null ? -1 : entity.hurtTime,
                     Registries.ITEM.getId(minecraftClient.player.getMainHandStack().getItem()).getPath(),
                     minecraftClient.player.getAttributeValue(EntityAttributes.ATTACK_SPEED)
                  )
               );
            }
         }
      }
   }

   private AdminRecord() {
      this.manualOnly = this.addSetting(new BooleanSetting(("Только вручную"), ("Не писать тики, где прицел ведёт аура или другой модуль."), true));
      this.nearTargetOnly = this.addSetting(new BooleanSetting(("Только рядом с целью"), ("Писать только когда рядом есть игрок в 6 блоках."), true));
   }

   private void recordOutgoingLook(float yaw, float pitch) {
      if (!this.lookInitialized) {
         this.lastYaw = yaw;
         this.lastPitch = pitch;
         this.lookInitialized = true;
         this.lastLookTick = minecraftClient.player.age;
      } else {
         float yawDelta = MathHelper.wrapDegrees(yaw - this.lastYaw);
         float pitchDelta = pitch - this.lastPitch;
         this.lastYaw = yaw;
         this.lastPitch = pitch;
         this.lastLookTick = minecraftClient.player.age;
         this.recordRotationSample(minecraftClient.player.age, yawDelta, pitchDelta);
      }
   }

   @Override
   protected void onEnable() {
      this.lookInitialized = false;
      this.rowsWritten = 0;
      this.lastLookTick = this.lastTargetTick = this.lastAttackTick = -10;
   }
}
