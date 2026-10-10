package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.MouseSensitivity;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.Handler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "AdminShard", description = "Читает детект античита над хотбаром и пишет, какие ротации его двигают.", category = ModuleCategory.MISC)
public final class AdminShard extends Module {
   private static AdminShard INSTANCE;
   private static final Pattern PERCENT_PATTERN = Pattern.compile(("(\\d+(?:[.,]\\d+)?)\\s*%"));
   private static final Pattern SIGNED_NUMBER_PATTERN = Pattern.compile(("([+\\-−]?)\\s*(\\d+(?:[.,]\\d+)?)"));
   private static final String[] METRIC_LABELS = new String[]{
      ("yaw ср"),
      ("yaw макс"),
      ("pitch ср"),
      ("pitch макс"),
      ("рывок yaw"),
      ("рывок pitch"),
      ("pitch стоит %"),
      ("GCD %"),
      ("ошибка °"),
      ("удары"),
      ("в цель %"),
      ("снап удара"),
      ("дистанция"),
      ("ротаций/тик")
   };
   public BooleanSetting writeToChat;
   public BooleanSetting logTicks;
   private final double[] risingMetricSums = new double[METRIC_LABELS.length];
   private final double[] fallingMetricSums = new double[METRIC_LABELS.length];
   private int windowTicks;
   private int rotationSamples;
   private int pitchStillSamples;
   private int quantizedSamples;
   private int attacks;
   private int onTargetAttacks;
   private int targetSamples;
   private int risingWindowCount;
   private int fallingWindowCount;
   private double absYawSum;
   private double maxAbsYaw;
   private double absPitchSum;
   private double maxAbsPitch;
   private double yawAccelerationSum;
   private double pitchAccelerationSum;
   private double aimErrorSum;
   private double distanceSum;
   private double attackSnapSum;
   private float lastYaw;
   private float lastPitch;
   private float lastYawDelta;
   private float lastPitchDelta;
   private float lastPercent;
   private float lastBuffer;
   private boolean lookInitialized;
   private String lastOverlayText;
   private BufferedWriter tickWriter;
   private BufferedWriter eventWriter;
   private final EventListener<PlayerTickEvent> playerTickListener;
   private final EventListener<PacketEvent> packetListener;

   private AdminShard() {
      this.lastPercent = (-1.0F);
      this.lastBuffer = (-1.0F);
      this.lastOverlayText = "";
      this.playerTickListener = event -> this.windowTicks++;
      this.packetListener = packetEvent -> {
         if (minecraftClient.player != null) {
            if (packetEvent.isIncoming()) {
               Text text = packetEvent.getPacket() instanceof OverlayMessageS2CPacket overlayPacket
                  ? overlayPacket.text()
                  : (packetEvent.getPacket() instanceof GameMessageS2CPacket gameMessagePacket && gameMessagePacket.overlay() ? gameMessagePacket.content() : null);
               if (text != null) {
                  String overlayText = text.getString();
                  minecraftClient.execute(() -> this.recordOverlayText(overlayText));
               }
            } else {
               if (packetEvent.getPacket() instanceof PlayerMoveC2SPacket lookPacket && lookPacket.changesLook()) {
                  this.recordLook(lookPacket.getYaw(0.0F), lookPacket.getPitch(0.0F));
               } else if (packetEvent.getPacket() instanceof PlayerInteractEntityC2SPacket interactionPacket) {
                  interactionPacket.handle(new Handler() {
                     @Override
                     public void interact(Hand hand) {
                     }

                     @Override
                     public void interactAt(Hand hand, Vec3d position) {
                     }

                     @Override
                     public void attack() {
                        AdminShard.this.recordAttack();
                     }
                  });
               }
            }
         }
      };
      this.writeToChat = this.addSetting(new BooleanSetting(("Писать в чат"), ("Каждое изменение буфера с разбором ротаций за это время."), true));
      this.logTicks = this.addSetting(new BooleanSetting(("Лог тиков"), ("Каждый пакет ротации и удара в ticks.csv."), true));
   }

   private static boolean isSensitivityAligned(float delta, float step) {
      float stepCount = Math.abs(delta) / step;
      return Math.abs(stepCount - Math.round(stepCount)) < (0.02F) + stepCount * (1.0E-4F);
   }

   private void appendTickRow(String type, float yawDelta, float pitchDelta, float yawAcceleration, float pitchAcceleration, boolean sensitivityAligned, LivingEntity entity, float aimError, boolean onTarget) {
      if (this.logTicks.getValue() && minecraftClient.player != null) {
         this.appendLogRow(
            false,
            String.format(
               Locale.ROOT,
               ("%d;%d;%s;%.3f;%.3f;%.3f;%.3f;%.3f;%.3f;%d;%s;%.2f;%.2f;%d;%d;%d;%s;%.0f;%.2f%n"),
               System.currentTimeMillis(),
               minecraftClient.player.age,
               type,
               this.lastYaw,
               this.lastPitch,
               yawDelta,
               pitchDelta,
               yawAcceleration,
               pitchAcceleration,
               sensitivityAligned ? 1 : 0,
               entity == null ? ("-") : entity.getName().getString(),
               entity == null ? (-1.0D) : CombatTargeting.distanceToBounds(entity),
               aimError,
               onTarget ? 1 : 0,
               minecraftClient.player.isSprinting() ? 1 : 0,
               minecraftClient.player.isOnGround() ? 1 : 0,
               AttackAura.getInstance().isEnabled() ? AttackAura.getInstance().rotationMode.getSelectedOption() : ("-"),
               this.lastPercent,
               this.lastBuffer
            )
         );
      }
   }

   @Override
   protected void onDisable() {
      this.writeSummary();
      this.closeWriters();
   }

   private static float parseNumber(String text) {
      return Float.parseFloat(text.replace(',', '.'));
   }

   private void writeSummary() {
      if (this.risingWindowCount + this.fallingWindowCount != 0) {
         StringBuilder textBuilder = new StringBuilder(String.format(Locale.ROOT, ("# рост %d, падение %d%n"), this.risingWindowCount, this.fallingWindowCount));
         ChatMessages.printPrefixed(String.format(Locale.ROOT, ("&fAdminShard: &cрост %d &7/ &aпадение %d &7(среднее за окно)"), this.risingWindowCount, this.fallingWindowCount));

         for (int metricIndex = 0; metricIndex < METRIC_LABELS.length; metricIndex++) {
            double risingMean = this.risingWindowCount == 0 ? 0.0 : this.risingMetricSums[metricIndex] / this.risingWindowCount;
            double fallingMean = this.fallingWindowCount == 0 ? 0.0 : this.fallingMetricSums[metricIndex] / this.fallingWindowCount;
            double relativeDifference = Math.abs(risingMean - fallingMean) / Math.max((0.001D), Math.max(Math.abs(risingMean), Math.abs(fallingMean)));
            ChatMessages.printPrefixed(String.format(Locale.ROOT, ("&7%s: &c%.2f &7/ &a%.2f%s"), METRIC_LABELS[metricIndex], risingMean, fallingMean, relativeDifference > (0.25D) ? (" &6◀") : ""));
            textBuilder.append(String.format(Locale.ROOT, ("# %s;%.3f;%.3f;%.2f%n"), METRIC_LABELS[metricIndex], risingMean, fallingMean, relativeDifference));
         }

         this.appendLogRow(true, textBuilder.toString());
      }
   }

   @Override
   protected void onEnable() {
      this.resetWindow();
      this.lookInitialized = false;
      this.lastOverlayText = "";
      this.lastPercent = this.lastBuffer = (-1.0F);
      Arrays.fill(this.risingMetricSums, 0.0);
      Arrays.fill(this.fallingMetricSums, 0.0);
      this.risingWindowCount = this.fallingWindowCount = 0;
   }

   private void resetWindow() {
      this.windowTicks = this.rotationSamples = this.pitchStillSamples = this.quantizedSamples = this.attacks = this.onTargetAttacks = this.targetSamples = 0;
      this.absYawSum = this.maxAbsYaw = this.absPitchSum = this.maxAbsPitch = this.yawAccelerationSum = this.pitchAccelerationSum = this.aimErrorSum = this.distanceSum = this.attackSnapSum = 0.0;
   }

   private void recordAttack() {
      LivingEntity entity = this.nearestTarget();
      boolean onTarget = entity != null && this.lookInitialized && CombatTargeting.rayHitsEntityBounds(entity, (6.0D), this.lastYaw, this.lastPitch);
      this.attacks++;
      if (onTarget) {
         this.onTargetAttacks++;
      }

      this.attackSnapSum = this.attackSnapSum + Math.hypot(this.lastYawDelta, this.lastPitchDelta);
      this.appendTickRow(
         ("attack"),
         this.lastYawDelta,
         this.lastPitchDelta,
         0.0F,
         0.0F,
         true,
         entity,
         entity == null ? (-1.0F) : angularError(entity, this.lastYaw, this.lastPitch),
         onTarget
      );
   }

   private double[] windowMetrics() {
      int rotationCount = Math.max(1, this.rotationSamples);
      int targetCount = Math.max(1, this.targetSamples);
      int attackCount = Math.max(1, this.attacks);
      return new double[]{
         this.absYawSum / rotationCount,
         this.maxAbsYaw,
         this.absPitchSum / rotationCount,
         this.maxAbsPitch,
         this.yawAccelerationSum / rotationCount,
         this.pitchAccelerationSum / rotationCount,
         this.pitchStillSamples * (100.0D) / rotationCount,
         this.quantizedSamples * (100.0D) / rotationCount,
         this.aimErrorSum / targetCount,
         this.attacks,
         this.onTargetAttacks * (100.0D) / attackCount,
         this.attackSnapSum / attackCount,
         this.distanceSum / targetCount,
         (double)this.rotationSamples / Math.max(1, this.windowTicks)
      };
   }

   private void recordOverlayText(String text) {
      if (minecraftClient.player != null && this.isEnabled() && !text.equals(this.lastOverlayText)) {
         this.lastOverlayText = text;
         this.appendTickRow("text " + text.replace(';', ',').replace('\n', ' '), 0.0F, 0.0F, 0.0F, 0.0F, true, null, (-1.0F), false);
         Matcher percentMatcher = PERCENT_PATTERN.matcher(text);
         if (percentMatcher.find()) {
            float percent = parseNumber(percentMatcher.group(1));
            float reportedDelta = Float.NaN;
            float buffer = Float.NaN;
            Matcher numberMatcher = SIGNED_NUMBER_PATTERN.matcher(text.substring(percentMatcher.end()));

            while (numberMatcher.find()) {
               float number = parseNumber(numberMatcher.group(2));
               if (!numberMatcher.group(1).isEmpty() && Float.isNaN(reportedDelta)) {
                  reportedDelta = numberMatcher.group(1).equals(("+")) ? number : -number;
               } else {
                  buffer = number;
               }
            }

            if (!Float.isNaN(buffer)) {
               float bufferChange = this.lastBuffer < 0.0F ? (Float.isNaN(reportedDelta) ? 0.0F : reportedDelta) : buffer - this.lastBuffer;
               if (Float.isNaN(reportedDelta)) {
                  reportedDelta = bufferChange;
               }

               this.lastPercent = percent;
               if (!(this.lastBuffer >= 0.0F) || bufferChange != 0.0F) {
                  this.lastBuffer = buffer;
                  double[] metrics = this.windowMetrics();
                  if (bufferChange > 0.0F) {
                     this.risingWindowCount++;
                  }

                  if (bufferChange < 0.0F) {
                     this.fallingWindowCount++;
                  }

                  for (int metricIndex = 0; metricIndex < metrics.length; metricIndex++) {
                     if (bufferChange > 0.0F) {
                        this.risingMetricSums[metricIndex] = this.risingMetricSums[metricIndex] + metrics[metricIndex];
                     }

                     if (bufferChange < 0.0F) {
                        this.fallingMetricSums[metricIndex] = this.fallingMetricSums[metricIndex] + metrics[metricIndex];
                     }
                  }

                  if (this.writeToChat.getValue()) {
                     ChatMessages.printPrefixed(
                        String.format(
                           Locale.ROOT,
                           ("%s%+.2f &7→ &e%.2f &7(%.0f%%) &8| &7yaw &f%.1f/%.1f &7pitch &f%.1f/%.1f &7рывок &f%.1f/%.1f &7удары &f%d &7в цель &f%.0f%% &7ошибка &f%.1f° &7GCD &f%.0f%%"),
                           bufferChange > 0.0F ? ("&c") : ("&a"),
                           bufferChange,
                           buffer,
                           percent,
                           metrics[0],
                           metrics[1],
                           metrics[2],
                           metrics[3],
                           metrics[4],
                           metrics[5],
                           this.attacks,
                           metrics[10],
                           metrics[8],
                           metrics[7]
                        )
                     );
                  }

                  StringBuilder textBuilder = new StringBuilder(
                     String.format(Locale.ROOT, ("%d;%.2f;%.2f;%.2f;%.2f;%d"), System.currentTimeMillis(), percent, reportedDelta, bufferChange, buffer, this.windowTicks)
                  );

                  for (double metricValue : metrics) {
                     textBuilder.append(String.format(Locale.ROOT, (";%.3f"), metricValue));
                  }

                  this.appendLogRow(
                     true,
                     textBuilder.append(';')
                        .append(AttackAura.getInstance().isEnabled() ? AttackAura.getInstance().rotationMode.getSelectedOption() : ("-"))
                        .append(';')
                        .append(text.replace(';', ',').replace('\n', ' '))
                        .append('\n')
                        .toString()
                  );
                  this.resetWindow();
               }
            }
         }
      }
   }

   private void recordLook(float yaw, float pitch) {
      if (!this.lookInitialized) {
         this.lastYaw = yaw;
         this.lastPitch = pitch;
         this.lookInitialized = true;
      } else {
         float yawDelta = MathHelper.wrapDegrees(yaw - this.lastYaw);
         float pitchDelta = pitch - this.lastPitch;
         float yawAcceleration = Math.abs(yawDelta - this.lastYawDelta);
         float pitchAcceleration = Math.abs(pitchDelta - this.lastPitchDelta);
         float sensitivityStep = MouseSensitivity.rotationStep();
         boolean sensitivityAligned = isSensitivityAligned(yawDelta, sensitivityStep) && isSensitivityAligned(pitchDelta, sensitivityStep);
         this.lastYaw = yaw;
         this.lastPitch = pitch;
         this.lastYawDelta = yawDelta;
         this.lastPitchDelta = pitchDelta;
         this.rotationSamples++;
         this.absYawSum = this.absYawSum + Math.abs(yawDelta);
         this.maxAbsYaw = Math.max(this.maxAbsYaw, Math.abs(yawDelta));
         this.absPitchSum = this.absPitchSum + Math.abs(pitchDelta);
         this.maxAbsPitch = Math.max(this.maxAbsPitch, Math.abs(pitchDelta));
         this.yawAccelerationSum += yawAcceleration;
         this.pitchAccelerationSum += pitchAcceleration;
         if (pitchDelta == 0.0F && yawDelta != 0.0F) {
            this.pitchStillSamples++;
         }

         if (sensitivityAligned) {
            this.quantizedSamples++;
         }

         LivingEntity entity = this.nearestTarget();
         float aimError = entity == null ? (-1.0F) : angularError(entity, yaw, pitch);
         if (entity != null) {
            this.aimErrorSum += aimError;
            this.distanceSum = this.distanceSum + CombatTargeting.distanceToBounds(entity);
            this.targetSamples++;
         }

         this.appendTickRow(("rot"), yawDelta, pitchDelta, yawAcceleration, pitchAcceleration, sensitivityAligned, entity, aimError, entity != null && CombatTargeting.rayHitsEntityBounds(entity, (6.0D), yaw, pitch));
      }
   }

   private static float angularError(LivingEntity entity, float yaw, float pitch) {
      float[] targetAngles = CombatTargeting.anglesBetween(entity.getBoundingBox().getCenter(), minecraftClient.player.getEyePos());
      return (float)Math.hypot(MathHelper.wrapDegrees(targetAngles[0] - yaw), targetAngles[1] - pitch);
   }

   private void closeWriters() {
      try {
         if (this.eventWriter != null) {
            this.eventWriter.close();
         }

         if (this.tickWriter != null) {
            this.tickWriter.close();
         }
      } catch (IOException error) {
      }

      this.eventWriter = this.tickWriter = null;
   }

   public static AdminShard getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AdminShard();
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
            && minecraftClient.player.distanceTo(candidate) < (8.0F)
            && (closestPlayer == null || minecraftClient.player.distanceTo(candidate) < minecraftClient.player.distanceTo(closestPlayer))) {
            closestPlayer = candidate;
         }
      }

      return closestPlayer;
   }

   private void appendLogRow(boolean eventRow, String row) {
      try {
         if (this.eventWriter == null) {
            Path outputDirectory = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("adminshard"));
            Files.createDirectories(outputDirectory);
            String fileTimestamp = new SimpleDateFormat(("yyyyMMdd-HHmmss")).format(new Date());
            this.eventWriter = Files.newBufferedWriter(outputDirectory.resolve("events-" + fileTimestamp + ".csv"), StandardCharsets.UTF_8);
            this.eventWriter.write("time;percent;delta;change;buffer;ticks;" + String.join((";"), METRIC_LABELS) + ";rotation;text\n");
            this.tickWriter = Files.newBufferedWriter(outputDirectory.resolve("ticks-" + fileTimestamp + ".csv"), StandardCharsets.UTF_8);
            this.tickWriter.write(("time;tick;type;yaw;pitch;dyaw;dpitch;accelYaw;accelPitch;gcd;target;distance;aimError;onTarget;sprint;ground;rotation;percent;buffer\n"));
            ChatMessages.printPrefixed("&7AdminShard пишет в &f" + outputDirectory);
         }

         if (eventRow) {
            this.eventWriter.write(row);
            this.eventWriter.flush();
            this.tickWriter.flush();
         } else {
            this.tickWriter.write(row);
         }
      } catch (IOException error) {
         this.closeWriters();
         ChatMessages.printPrefixed("&cAdminShard: лог не пишется — " + error.getMessage());
      }
   }
}
