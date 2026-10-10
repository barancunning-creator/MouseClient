package com.mouseclient.util.entity;

import com.mouseclient.events.impl.AttackEvent;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.RotationController;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationBuilder implements MinecraftAccess {
   private static final int BIN_COUNT = 8;
   private static final float[] BIN_EDGES = {4.0F, 8.0F, 14.0F, 22.0F, 35.0F, 55.0F, 90.0F};
   private static RotationBuilder INSTANCE;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> this.captureTick();
   private final EventListener<AttackEvent> attackListener = event -> this.captureAttack();
   private final ArrayList<Sample> bag = new ArrayList<>();
   private final Map<String, LearnedRotation> loaded = new LinkedHashMap<>();
   private final java.util.HashSet<String> missing = new java.util.HashSet<>();
   private LearnedRotation active;
   private String sessionName = "default";
   private boolean recording;
   private boolean bound;
   private boolean attackPending;
   private int attacks;
   private int skipped;
   private float lastYaw;
   private float lastPitch;
   private boolean hasLastAngles;
   private LivingEntity lastTarget;

   public static RotationBuilder getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new RotationBuilder();
      }

      return INSTANCE;
   }

   public void bind() {
      if (!this.bound) {
         EventBus.getInstance().subscribe(this);
         this.bound = true;
         this.indexSaved();
      }
   }

   public boolean start(String name) {
      this.bind();
      String cleaned = sanitize(name);
      if (cleaned.isEmpty()) {
         return false;
      }

      this.sessionName = cleaned;
      this.bag.clear();
      this.attacks = 0;
      this.skipped = 0;
      this.attackPending = false;
      this.hasLastAngles = false;
      this.lastTarget = null;
      this.recording = true;
      return true;
   }

   public void stop() {
      this.recording = false;
   }

   public boolean isRecording() {
      return this.recording;
   }

   public String sessionName() {
      return this.sessionName;
   }

   public int sampleCount() {
      return this.bag.size();
   }

   public int attackCount() {
      return this.attacks;
   }

   public void rename(String name) {
      String cleaned = sanitize(name);
      if (!cleaned.isEmpty()) {
         this.sessionName = cleaned;
      }
   }

   public LearnedRotation save() {
      this.recording = false;
      LearnedRotation trained = this.train(this.sessionName, this.bag, this.attacks);
      if (trained == null) {
         return null;
      }

      if (!this.write(trained)) {
         return null;
      }

      this.loaded.put(trained.name.toLowerCase(Locale.ROOT), trained);
      this.active = trained;
      this.installMode(trained.name);
      return trained;
   }

   public LearnedRotation load(String name) {
      this.bind();
      LearnedRotation profile = this.profile(name);
      if (profile == null) {
         return null;
      }

      this.active = profile;
      this.installMode(profile.name);
      return profile;
   }

   public boolean delete(String name) {
      String key = sanitize(name).toLowerCase(Locale.ROOT);
      if (key.isEmpty()) {
         return false;
      }

      this.loaded.remove(key);
      try {
         boolean removed = Files.deleteIfExists(this.path(key));
         this.missing.add(key);
         if (removed && AttackAura.getInstance() != null) {
            AttackAura.getInstance().rotationMode.getOptions().removeIf(option -> option.equalsIgnoreCase(key) && !this.builtIn(option));
            if (AttackAura.getInstance().rotationMode.getSelectedOption().equalsIgnoreCase(key)) {
               AttackAura.getInstance().rotationMode.withSelectedOption("FunTime");
            }
         }

         return removed;
      } catch (IOException error) {
         return false;
      }
   }

   public ArrayList<String> names() {
      this.bind();
      ArrayList<String> names = new ArrayList<>();
      if (!Files.isDirectory(this.directory())) {
         return names;
      }

      try (var entries = Files.newDirectoryStream(this.directory(), "*.json")) {
         for (Path entry : entries) {
            String fileName = entry.getFileName().toString();
            names.add(fileName.substring(0, fileName.length() - 5));
         }
      } catch (IOException error) {
         return names;
      }

      names.sort(Comparator.naturalOrder());
      return names;
   }

   public boolean owns(String name) {
      if (name == null || this.builtIn(name)) {
         return false;
      }

      return this.profile(name) != null;
   }

   public LearnedRotation resolve(String mode) {
      if (mode == null) {
         return this.active;
      }

      if (mode.equalsIgnoreCase("builder")) {
         return this.active;
      }

      LearnedRotation profile = this.profile(mode);
      if (profile != null) {
         this.active = profile;
      }

      return profile;
   }

   public LearnedRotation active() {
      return this.active;
   }

   public boolean profileExists(String name) {
      return Files.isRegularFile(this.path(sanitize(name).toLowerCase(Locale.ROOT)));
   }

   private void captureAttack() {
      if (!this.recording) {
         return;
      }

      this.attackPending = true;
      this.attacks++;
      if (!this.bag.isEmpty()) {
         this.bag.get(this.bag.size() - 1).attacked = true;
      }

      if (this.attacks == 1 || this.attacks % 10 == 0) {
         ChatMessages.printPrefixed("Builder: удар &7" + this.attacks + "&r, семплов &7" + this.bag.size());
      }
   }

   private void captureTick() {
      if (!this.recording || minecraftClient.player == null || minecraftClient.world == null || minecraftClient.currentScreen != null) {
         this.hasLastAngles = false;
         return;
      }

      float yaw = minecraftClient.player.getYaw();
      float pitch = minecraftClient.player.getPitch();
      if (!this.hasLastAngles) {
         this.lastYaw = yaw;
         this.lastPitch = pitch;
         this.hasLastAngles = true;
         return;
      }

      float yawDelta = MathHelper.wrapDegrees(yaw - this.lastYaw);
      float pitchDelta = pitch - this.lastPitch;
      this.lastYaw = yaw;
      this.lastPitch = pitch;
      if (RotationController.getInstance().isActive()) {
         this.skipped++;
         return;
      }

      LivingEntity target = this.findTarget();
      this.lastTarget = target;
      if (target == null) {
         return;
      }

      Vec3d eye = minecraftClient.player.getEyePos();
      Box box = target.getBoundingBox();
      Vec3d right = this.horizontalRight(eye, box.getCenter());
      Vec3d motion = new Vec3d(target.getX() - target.lastX, 0.0, target.getZ() - target.lastZ);
      boolean onBox = CombatTargeting.rayHitsEntityBounds(target, Math.max(6.0, eye.distanceTo(box.getCenter()) + 1.0), yaw, pitch);
      Vec3d aim = this.lookPoint(box, eye, yaw, pitch);
      float[] angles = CombatTargeting.anglesBetween(aim, eye);
      Sample sample = new Sample();
      sample.yawError = MathHelper.wrapDegrees(angles[0] - yaw);
      sample.pitchError = angles[1] - pitch;
      sample.distance = (float)CombatTargeting.distanceToBounds(target);
      sample.sideSpeed = (float)motion.dotProduct(right);
      sample.cooldown = minecraftClient.player.getAttackCooldownProgress(0.0F);
      sample.yawDelta = yawDelta;
      sample.pitchDelta = pitchDelta;
      sample.onBox = onBox;
      sample.attacked = this.attackPending;
      sample.aimHeight = (float)((aim.y - box.minY) / Math.max(0.1, box.getLengthY()));
      sample.aimSide = (float)(((aim.x - box.getCenter().x) * right.x + (aim.z - box.getCenter().z) * right.z) / Math.max(0.15, box.getLengthX() * 0.5));
      this.attackPending = false;
      this.bag.add(sample);
      if (this.bag.size() > 12000) {
         this.bag.remove(0);
      }
   }

   private LivingEntity findTarget() {
      if (minecraftClient.targetedEntity instanceof LivingEntity looked && looked != minecraftClient.player && looked.isAlive() && !(looked instanceof ArmorStandEntity)) {
         return looked;
      }

      if (this.lastTarget != null && this.lastTarget.isAlive() && minecraftClient.player.distanceTo(this.lastTarget) < 7.0) {
         float[] lastAngles = CombatTargeting.anglesBetween(this.lastTarget.getBoundingBox().getCenter(), minecraftClient.player.getEyePos());
         if (Math.abs(MathHelper.wrapDegrees(lastAngles[0] - minecraftClient.player.getYaw())) < 50.0F) {
            return this.lastTarget;
         }
      }

      LivingEntity best = null;
      float bestError = 75.0F;
      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof LivingEntity living && living != minecraftClient.player && living.isAlive() && !(living instanceof ArmorStandEntity) && minecraftClient.player.distanceTo(living) <= 6.5) {
            float[] angles = CombatTargeting.anglesBetween(living.getBoundingBox().getCenter(), minecraftClient.player.getEyePos());
            float error = Math.abs(MathHelper.wrapDegrees(angles[0] - minecraftClient.player.getYaw()));
            if (error < bestError) {
               bestError = error;
               best = living;
            }
         }
      }

      return best;
   }

   private LearnedRotation train(String name, List<Sample> samples, int attackTotal) {
      if (samples.size() < 30 || attackTotal < 2) {
         return null;
      }

      double[] yawSum = new double[BIN_COUNT];
      double[] yawSq = new double[BIN_COUNT];
      double[] pitchSum = new double[BIN_COUNT];
      double[] pitchSq = new double[BIN_COUNT];
      int[] counts = new int[BIN_COUNT];
      double heightSum = 0.0;
      double sideSum = 0.0;
      double sideSpeedSum = 0.0;
      double sideProduct = 0.0;
      double sideSpeedSq = 0.0;
      int aimCount = 0;
      int onBox = 0;
      int held = 0;
      int flicks = 0;
      int crossings = 0;
      double overshootSum = 0.0;
      double ratioSum = 0.0;
      int ratioCount = 0;
      double pitchLagSum = 0.0;
      int pitchLagCount = 0;
      Sample previous = null;

      for (Sample sample : samples) {
         if (sample.distance > 7.5F || Math.abs(sample.yawError) > 150.0F) {
            previous = sample;
            continue;
         }

         int bin = bin(sample.yawError);
         float yawStep = Math.abs(sample.yawDelta);
         float pitchStep = Math.abs(sample.pitchDelta);
         yawSum[bin] += yawStep;
         yawSq[bin] += yawStep * yawStep;
         pitchSum[bin] += pitchStep;
         pitchSq[bin] += pitchStep * pitchStep;
         counts[bin]++;
         if (sample.onBox) {
            onBox++;
            if (yawStep < 0.8F && pitchStep < 0.6F) {
               held++;
            }
         }

         if (Math.abs(sample.yawError) > 18.0F) {
            flicks++;
         }

         if (sample.attacked || sample.onBox) {
            int weight = sample.attacked ? 4 : 1;
            heightSum += sample.aimHeight * weight;
            sideSum += sample.aimSide * weight;
            sideSpeedSum += sample.sideSpeed * weight;
            sideProduct += sample.aimSide * sample.sideSpeed * weight;
            sideSpeedSq += sample.sideSpeed * sample.sideSpeed * weight;
            aimCount += weight;
         }

         if (yawStep > 0.2F) {
            pitchLagSum += pitchStep / yawStep;
            pitchLagCount++;
         }

         if (previous != null && Math.abs(sample.yawError) > 10.0F && Math.abs(previous.yawDelta) > 0.2F && Math.signum(sample.yawDelta) == Math.signum(previous.yawDelta)) {
            ratioSum += MathHelper.clamp(yawStep / Math.abs(previous.yawDelta), 0.2F, 1.8F);
            ratioCount++;
         }

         if (previous != null && sample.yawError * previous.yawError < 0.0F && Math.abs(previous.yawDelta) > 3.5F && Math.abs(previous.yawError) < 12.0F) {
            crossings++;
            overshootSum += Math.abs(previous.yawDelta);
         }

         previous = sample;
      }

      int used = 0;
      for (int count : counts) {
         used += count;
      }

      if (used < 30) {
         return null;
      }

      LearnedRotation profile = new LearnedRotation();
      profile.name = name;
      profile.ticks = used;
      profile.attacks = attackTotal;
      for (int bin = 0; bin < BIN_COUNT; bin++) {
         if (counts[bin] == 0) {
            continue;
         }

         profile.yawMean[bin] = (float)(yawSum[bin] / counts[bin]);
         profile.pitchMean[bin] = (float)(pitchSum[bin] / counts[bin]);
         profile.yawDev[bin] = (float)Math.sqrt(Math.max(0.0, yawSq[bin] / counts[bin] - profile.yawMean[bin] * profile.yawMean[bin]));
         profile.pitchDev[bin] = (float)Math.sqrt(Math.max(0.0, pitchSq[bin] / counts[bin] - profile.pitchMean[bin] * profile.pitchMean[bin]));
      }

      this.fillEmptyBins(profile, counts);
      if (aimCount > 0) {
         profile.aimHeight = MathHelper.clamp((float)(heightSum / aimCount), 0.25F, 0.9F);
         profile.aimSide = MathHelper.clamp((float)(sideSum / aimCount), -0.8F, 0.8F);
         double variance = sideSpeedSq / aimCount - Math.pow(sideSpeedSum / aimCount, 2.0);
         if (variance > 1.0E-4D) {
            double covariance = sideProduct / aimCount - (sideSum / aimCount) * (sideSpeedSum / aimCount);
            profile.sideFollow = MathHelper.clamp((float)(covariance / variance), -1.5F, 1.5F);
         }
      }

      profile.holdRate = onBox == 0 ? 0.65F : held / (float)onBox;
      profile.overshoot = crossings == 0 ? 4.5F : MathHelper.clamp((float)(overshootSum / crossings), 2.0F, 18.0F);
      profile.overshootRate = flicks == 0 ? 0.0F : MathHelper.clamp(crossings / (float)flicks, 0.0F, 1.0F);
      float ratio = ratioCount == 0 ? 1.05F : (float)(ratioSum / ratioCount);
      profile.windup = MathHelper.clamp(0.16F + (ratio - 0.85F) * 0.45F, 0.16F, 0.62F);
      profile.pitchLag = pitchLagCount == 0 ? 0.55F : MathHelper.clamp((float)(pitchLagSum / pitchLagCount), 0.2F, 1.0F);
      profile.prototypes = this.prototypes(samples);
      return profile;
   }

   private void fillEmptyBins(LearnedRotation profile, int[] counts) {
      for (int bin = 0; bin < BIN_COUNT; bin++) {
         if (counts[bin] != 0) {
            continue;
         }

         int neighbor = -1;
         for (int distance = 1; distance < BIN_COUNT && neighbor == -1; distance++) {
            if (bin - distance >= 0 && counts[bin - distance] != 0) {
               neighbor = bin - distance;
            } else if (bin + distance < BIN_COUNT && counts[bin + distance] != 0) {
               neighbor = bin + distance;
            }
         }

         if (neighbor != -1) {
            profile.yawMean[bin] = profile.yawMean[neighbor];
            profile.pitchMean[bin] = profile.pitchMean[neighbor];
            profile.yawDev[bin] = profile.yawDev[neighbor];
            profile.pitchDev[bin] = profile.pitchDev[neighbor];
         }
      }
   }

   private float[][] prototypes(List<Sample> samples) {
      int stride = Math.max(1, samples.size() / 640);
      ArrayList<float[]> picked = new ArrayList<>();
      for (int index = 0; index < samples.size(); index++) {
         Sample sample = samples.get(index);
         if (sample.distance > 7.5F) {
            continue;
         }

         if (sample.attacked || Math.abs(sample.yawError) > 20.0F || index % stride == 0) {
            picked.add(new float[]{sample.yawError, sample.pitchError, sample.distance, sample.sideSpeed, sample.onBox ? 1.0F : 0.0F, sample.cooldown, sample.yawDelta, sample.pitchDelta});
         }
      }

      if (picked.size() > 900) {
         int keepStride = Math.max(1, picked.size() / 800);
         ArrayList<float[]> reduced = new ArrayList<>();
         for (int index = 0; index < picked.size(); index += keepStride) {
            reduced.add(picked.get(index));
         }

         picked = reduced;
      }

      return picked.toArray(float[][]::new);
   }

   private void indexSaved() {
      for (String name : this.names()) {
         this.addOption(name);
      }
   }

   private void installMode(String name) {
      this.addOption(name);
      if (AttackAura.getInstance() != null && name != null && !name.isBlank()) {
         AttackAura.getInstance().rotationMode.withSelectedOption(name);
      }
   }

   private void addOption(String name) {
      if (AttackAura.getInstance() == null || name == null || name.isBlank() || this.builtIn(name)) {
         return;
      }

      List<String> options = AttackAura.getInstance().rotationMode.getOptions();
      boolean present = false;
      for (String option : options) {
         if (option.equalsIgnoreCase(name)) {
            present = true;
            break;
         }
      }

      if (!present) {
         options.add(name);
      }
   }

   private LearnedRotation profile(String name) {
      String key = sanitize(name).toLowerCase(Locale.ROOT);
      if (key.isEmpty()) {
         return null;
      }

      LearnedRotation cached = this.loaded.get(key);
      if (cached != null) {
         return cached;
      }

      if (this.missing.contains(key) || !Files.isRegularFile(this.path(key))) {
         this.missing.add(key);
         return null;
      }

      try {
         LearnedRotation parsed = this.read(Files.readString(this.path(key), StandardCharsets.UTF_8));
         if (parsed != null) {
            this.loaded.put(key, parsed);
            this.missing.remove(key);
         }

         return parsed;
      } catch (Exception error) {
         return null;
      }
   }

   private boolean write(LearnedRotation profile) {
      try {
         Files.createDirectories(this.directory());
         Files.writeString(this.path(profile.name.toLowerCase(Locale.ROOT)), this.toJson(profile).toString(), StandardCharsets.UTF_8);
         this.missing.remove(profile.name.toLowerCase(Locale.ROOT));
         return true;
      } catch (IOException error) {
         return false;
      }
   }

   private JsonObject toJson(LearnedRotation profile) {
      JsonObject json = new JsonObject();
      json.addProperty("name", profile.name);
      json.addProperty("ticks", profile.ticks);
      json.addProperty("attacks", profile.attacks);
      json.add("yawMean", this.array(profile.yawMean));
      json.add("yawDev", this.array(profile.yawDev));
      json.add("pitchMean", this.array(profile.pitchMean));
      json.add("pitchDev", this.array(profile.pitchDev));
      json.addProperty("aimHeight", profile.aimHeight);
      json.addProperty("aimSide", profile.aimSide);
      json.addProperty("sideFollow", profile.sideFollow);
      json.addProperty("overshoot", profile.overshoot);
      json.addProperty("overshootRate", profile.overshootRate);
      json.addProperty("holdRate", profile.holdRate);
      json.addProperty("windup", profile.windup);
      json.addProperty("pitchLag", profile.pitchLag);
      JsonArray prototypes = new JsonArray();
      if (profile.prototypes != null) {
         for (float[] prototype : profile.prototypes) {
            prototypes.add(this.array(prototype));
         }
      }

      json.add("prototypes", prototypes);
      return json;
   }

   private LearnedRotation read(String text) {
      JsonObject json = JsonParser.parseString(text).getAsJsonObject();
      LearnedRotation profile = new LearnedRotation();
      profile.name = json.get("name").getAsString();
      profile.ticks = json.get("ticks").getAsInt();
      profile.attacks = json.get("attacks").getAsInt();
      this.readArray(json.getAsJsonArray("yawMean"), profile.yawMean);
      this.readArray(json.getAsJsonArray("yawDev"), profile.yawDev);
      this.readArray(json.getAsJsonArray("pitchMean"), profile.pitchMean);
      this.readArray(json.getAsJsonArray("pitchDev"), profile.pitchDev);
      profile.aimHeight = json.get("aimHeight").getAsFloat();
      profile.aimSide = json.get("aimSide").getAsFloat();
      profile.sideFollow = json.get("sideFollow").getAsFloat();
      profile.overshoot = json.get("overshoot").getAsFloat();
      profile.overshootRate = json.get("overshootRate").getAsFloat();
      profile.holdRate = json.get("holdRate").getAsFloat();
      profile.windup = json.get("windup").getAsFloat();
      profile.pitchLag = json.get("pitchLag").getAsFloat();
      JsonArray prototypes = json.getAsJsonArray("prototypes");
      profile.prototypes = new float[prototypes.size()][8];
      for (int index = 0; index < prototypes.size(); index++) {
         this.readArray(prototypes.get(index).getAsJsonArray(), profile.prototypes[index]);
      }

      return profile;
   }

   private JsonArray array(float[] values) {
      JsonArray array = new JsonArray();
      for (float value : values) {
         array.add(value);
      }

      return array;
   }

   private void readArray(JsonArray array, float[] target) {
      for (int index = 0; index < target.length && index < array.size(); index++) {
         target[index] = array.get(index).getAsFloat();
      }
   }

   private Path directory() {
      return FabricLoader.getInstance().getConfigDir().resolve("mouseclient").resolve("rotations");
   }

   private Path path(String name) {
      return this.directory().resolve(name + ".json");
   }

   private Vec3d lookPoint(Box box, Vec3d eye, float yaw, float pitch) {
      Vec3d look = CombatTargeting.direction(yaw, pitch);
      Vec3d end = eye.add(look.multiply(Math.max(0.4, eye.distanceTo(box.getCenter()) + 0.5)));
      return box.raycast(eye, end).orElseGet(() -> this.clamp(end, box));
   }

   private Vec3d horizontalRight(Vec3d eye, Vec3d center) {
      Vec3d forward = new Vec3d(center.x - eye.x, 0.0, center.z - eye.z);
      if (forward.lengthSquared() < 1.0E-6D) {
         forward = new Vec3d(0.0, 0.0, 1.0);
      }

      forward = forward.normalize();
      return new Vec3d(-forward.z, 0.0, forward.x);
   }

   private Vec3d clamp(Vec3d point, Box box) {
      return new Vec3d(MathHelper.clamp(point.x, box.minX, box.maxX), MathHelper.clamp(point.y, box.minY, box.maxY), MathHelper.clamp(point.z, box.minZ, box.maxZ));
   }

   public static int bin(float error) {
      float absolute = Math.abs(error);
      for (int index = 0; index < BIN_EDGES.length; index++) {
         if (absolute < BIN_EDGES[index]) {
            return index;
         }
      }

      return BIN_COUNT - 1;
   }

   public static String sanitize(String name) {
      if (name == null) {
         return "";
      }

      String cleaned = name.trim().replaceAll("[^\\p{L}\\p{N}_\\- ]", "").replace(' ', '_');
      if (cleaned.length() > 32) {
         cleaned = cleaned.substring(0, 32);
      }

      return cleaned;
   }

   private boolean builtIn(String name) {
      return name.equalsIgnoreCase("FunTime")
         || name.equalsIgnoreCase("SpookyTime")
         || name.equalsIgnoreCase("Legit")
         || name.equalsIgnoreCase("shard022")
         || name.equalsIgnoreCase("comet")
         || name.equalsIgnoreCase("ShardTest")
         || name.equalsIgnoreCase("builder");
   }

   public int skippedTicks() {
      return this.skipped;
   }

   private static final class Sample {
      private float yawError;
      private float pitchError;
      private float distance;
      private float sideSpeed;
      private float cooldown;
      private float yawDelta;
      private float pitchDelta;
      private float aimHeight;
      private float aimSide;
      private boolean onBox;
      private boolean attacked;
   }

   public static final class LearnedRotation {
      public String name = "default";
      public int ticks;
      public int attacks;
      public float[] yawMean = new float[BIN_COUNT];
      public float[] yawDev = new float[BIN_COUNT];
      public float[] pitchMean = new float[BIN_COUNT];
      public float[] pitchDev = new float[BIN_COUNT];
      public float aimHeight = 0.62F;
      public float aimSide;
      public float sideFollow;
      public float overshoot = 4.5F;
      public float overshootRate;
      public float holdRate = 0.7F;
      public float windup = 0.3F;
      public float pitchLag = 0.55F;
      public float[][] prototypes = new float[0][8];
   }
}
