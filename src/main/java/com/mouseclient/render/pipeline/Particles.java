package com.mouseclient.render.pipeline;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.ColorSetting;
import com.mouseclient.modules.impl.render.SettingSeparator;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.render.DepthSnapshot;
import com.mouseclient.util.entity.WorldProjection;
import com.mouseclient.util.entity.MinecraftAccess;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.BufferAllocator.CloseableBuffer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

@ModuleInfo(name = "Particles", description = "Частицы вокруг игрока: метеоры, фигуры и хит эффекты.", category = ModuleCategory.RENDER)
public final class Particles extends Module {
   private static Particles INSTANCE;
   private static final Vector4f WHITE_MODULATOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
   private static final Vector3f ZERO_OFFSET = new Vector3f();
   private static final Matrix4f IDENTITY_TEXTURE_MATRIX = new Matrix4f();
   private static final RenderPipeline SPRITE_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/particles_glow")))
      .withVertexShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_tex_color")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .build();
   private static final RenderPipeline WIRE_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/particles_wires")))
      .withVertexShader(Identifier.ofVanilla(("core/position_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_color")))
      .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   private static final int[] CUBE_EDGES = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 0, 2, 1, 3, 4, 6, 5, 7, 0, 4, 1, 5, 2, 6, 3, 7};
   private static final int[] PYRAMID_EDGES = new int[]{0, 1, 1, 2, 2, 3, 3, 0, 0, 4, 1, 4, 2, 4, 3, 4};
   private static final int[] OCTAHEDRON_EDGES = new int[]{0, 2, 0, 3, 0, 4, 0, 5, 1, 2, 1, 3, 1, 4, 1, 5, 2, 5, 5, 3, 3, 4, 4, 2};
   private static final int[] TETRAHEDRON_EDGES = new int[]{0, 1, 0, 2, 0, 3, 1, 2, 2, 3, 3, 1};
   public MultiSelectSetting effects;
   public DecimalSetting shapeCount;
   public DecimalSetting lifetime;
   public DecimalSetting size;
   public DecimalSetting minSpawnDistance;
   public DecimalSetting maxSpawnDistance;
   public DecimalSetting shapeHeight;
   public DecimalSetting glow;
   public ModeSetting worldSpawn;
   public MultiSelectSetting worldImages;
   public DecimalSetting worldCount;
   public DecimalSetting worldSize;
   public DecimalSetting minDistance;
   public DecimalSetting maxDistance;
   public DecimalSetting worldHeight;
   public BooleanSetting hitParticles;
   public MultiSelectSetting images;
   public DecimalSetting hitParticleCount;
   public DecimalSetting hitSize;
   public BooleanSetting criticalOnly;
   public BooleanSetting useCustomColor;
   public ColorSetting color;
   private final ArrayList<Particles.ShapeParticle> cubeParticles = new ArrayList<>();
   private final ArrayList<Particles.ShapeParticle> pyramidParticles = new ArrayList<>();
   private final ArrayList<Particles.ShapeParticle> totemParticles = new ArrayList<>();
   private final ArrayList<Particles.ShapeParticle> crescentParticles = new ArrayList<>();
   private final ArrayList<Particles.ShapeParticle> heartParticles = new ArrayList<>();
   private final ArrayList<Particles.ShapeParticle> starParticles = new ArrayList<>();
   private final ArrayList<Particles.ShapeParticle> logoParticles = new ArrayList<>();
   private final ArrayList<Particles.SpriteParticle> hitSprites = new ArrayList<>();
   private final ArrayList<Particles.SpriteParticle> worldSprites = new ArrayList<>();
   private final ConcurrentLinkedQueue<Particles.HitParticleRequest> hitRequests = new ConcurrentLinkedQueue<>();
   private long enabledAt;
   private final HashMap<String, GpuBuffer> textureVertexBuffers = new HashMap<>();
   private GpuBuffer wireVertexBuffer;
   private final Matrix3f shapeRotation = new Matrix3f();
   private final Vector3f[] shapeCorners = new Vector3f[8];
   private final Vector3f edgeStartScratch = new Vector3f();
   private final Vector3f edgeEndScratch = new Vector3f();
   private float[] totemEdges;
   private float[] starEdges;
   private float[] heartEdges;
   private float[] crescentEdges;
   private float[] logoEdges;
   private boolean totemLoadAttempted;
   private boolean starLoadAttempted;
   private boolean heartLoadAttempted;
   private boolean logoLoadAttempted;
   private volatile boolean modelWarmupStarted;
   private BufferAllocator geometryAllocator;
   private final int[] spriteTypeCounts;
   private final ArrayList<Particles.SpriteParticle> collisionScratch;
   private final EventListener<PacketEvent> packetListener;
   private final EventListener<PlayerTickEvent> playerTickListener;
   private final EventListener<WorldRenderEvent> worldRenderListener;
   private static final String[] SPRITE_NAMES = new String[]{
      ("Светлячок"), ("Звезда"), ("Сердце"), ("Снежинка"), ("Доллар")
   };

   private boolean criticalHitEligible() {
      return minecraftClient.player != null
         && minecraftClient.player.fallDistance > 0.0
         && !minecraftClient.player.isOnGround()
         && !minecraftClient.player.isClimbing()
         && !minecraftClient.player.isTouchingWater()
         && !minecraftClient.player.hasVehicle()
         && !minecraftClient.player.isSprinting();
   }

   private void emitBillboard(BufferBuilder builder, Quaternionf rotation, Vector3f scratch, double x, double y, double z, float halfSize, int color) {
      double relativeX = x - minecraftClient.gameRenderer.getCamera().getCameraPos().x;
      double relativeY = y - minecraftClient.gameRenderer.getCamera().getCameraPos().y;
      double relativeZ = z - minecraftClient.gameRenderer.getCamera().getCameraPos().z;
      rotation.transform(scratch.set(-halfSize, -halfSize, 0.0F));
      builder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z)).texture(0.0F, 0.0F).color(color);
      rotation.transform(scratch.set(-halfSize, halfSize, 0.0F));
      builder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z)).texture(0.0F, 1.0F).color(color);
      rotation.transform(scratch.set(halfSize, halfSize, 0.0F));
      builder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z)).texture(1.0F, 1.0F).color(color);
      rotation.transform(scratch.set(halfSize, -halfSize, 0.0F));
      builder.vertex((float)(relativeX + scratch.x), (float)(relativeY + scratch.y), (float)(relativeZ + scratch.z)).texture(1.0F, 0.0F).color(color);
   }

   private static int spriteTypeIndex(String imageName) {
      for (int imageIndex = 0; imageIndex < SPRITE_NAMES.length; imageIndex++) {
         if (SPRITE_NAMES[imageIndex].equals(imageName)) {
            return imageIndex;
         }
      }

      return -1;
   }

   private static void accumulateNodeTranslations(JsonArray nodes, int nodeIndex, float parentX, float parentY, float parentZ, float[][] translations, boolean[] visited) {
      if (nodeIndex >= 0 && nodeIndex < nodes.size() && !visited[nodeIndex]) {
         visited[nodeIndex] = true;
         JsonObject json = nodes.get(nodeIndex).getAsJsonObject();
         float translatedX = parentX;
         float translatedY = parentY;
         float translatedZ = parentZ;
         if (json.has(("translation"))) {
            JsonArray nodeTranslation = json.getAsJsonArray(("translation"));
            translatedX = parentX + nodeTranslation.get(0).getAsFloat();
            translatedY = parentY + nodeTranslation.get(1).getAsFloat();
            translatedZ = parentZ + nodeTranslation.get(2).getAsFloat();
         }

         translations[nodeIndex][0] = translatedX;
         translations[nodeIndex][1] = translatedY;
         translations[nodeIndex][2] = translatedZ;
         if (json.has(("children"))) {
            JsonArray children = json.getAsJsonArray(("children"));

            for (int childIndex = 0; childIndex < children.size(); childIndex++) {
               accumulateNodeTranslations(nodes, children.get(childIndex).getAsInt(), translatedX, translatedY, translatedZ, translations, visited);
            }
         }
      }
   }

   private void maintainShapeCounts(long nowMillis) {
      if (this.effects.isSelected(("Кубы"))
         || this.effects.isSelected(("Пирамиды"))
         || this.effects.isSelected(("Тотем"))
         || this.effects.isSelected(("Полумесяц"))
         || this.effects.isSelected(("Сердце"))
         || this.effects.isSelected(("Звёзды"))
         || this.effects.isSelected(("Делюкс"))) {
         Vec3d position = minecraftClient.player.getEntityPos();
         double minimumDistance = Math.min(this.minSpawnDistance.getValue(), this.maxSpawnDistance.getValue());
         double maximumDistance = Math.max(this.maxSpawnDistance.getValue(), minimumDistance + (0.1D));
         int targetCount = Math.max(1, (int)this.shapeCount.getValue());
         if (this.effects.isSelected(("Кубы"))) {
            this.maintainShapeList(this.cubeParticles, position, minimumDistance, maximumDistance, nowMillis, targetCount, (6.0F), (6.0F), (6.0F));
         } else {
            this.cubeParticles.clear();
         }

         if (this.effects.isSelected(("Пирамиды"))) {
            this.maintainShapeList(this.pyramidParticles, position, minimumDistance, maximumDistance, nowMillis, targetCount, (7.0F), (7.0F), (7.0F));
         } else {
            this.pyramidParticles.clear();
         }

         if (this.effects.isSelected(("Тотем"))) {
            this.maintainShapeList(this.totemParticles, position, minimumDistance, maximumDistance, nowMillis, targetCount, (5.0F), (8.0F), (5.0F));
         } else {
            this.totemParticles.clear();
         }

         if (this.effects.isSelected(("Полумесяц"))) {
            this.maintainShapeList(this.crescentParticles, position, minimumDistance, maximumDistance, nowMillis, targetCount, (6.0F), (6.0F), (6.0F));
         } else {
            this.crescentParticles.clear();
         }

         if (this.effects.isSelected(("Сердце"))) {
            this.maintainShapeList(this.heartParticles, position, minimumDistance, maximumDistance, nowMillis, targetCount, (6.0F), (6.0F), (6.0F));
         } else {
            this.heartParticles.clear();
         }

         if (this.effects.isSelected(("Звёзды"))) {
            this.maintainShapeList(this.starParticles, position, minimumDistance, maximumDistance, nowMillis, targetCount, (6.0F), (6.0F), (6.0F));
         } else {
            this.starParticles.clear();
         }

         if (this.effects.isSelected(("Делюкс"))) {
            this.maintainShapeList(this.logoParticles, position, minimumDistance, maximumDistance, nowMillis, targetCount, (6.0F), (6.0F), (6.0F));
         } else {
            this.logoParticles.clear();
         }
      }
   }

   private Particles() {
      this.spriteTypeCounts = new int[SPRITE_NAMES.length];
      this.collisionScratch = new ArrayList<>();
      this.packetListener = event -> {
         if (event.isOutgoing() && event.getPacket() instanceof HandSwingC2SPacket && this.hitParticles.getValue()) {
            if (minecraftClient.crosshairTarget instanceof EntityHitResult hitResult
               && hitResult.getEntity() instanceof LivingEntity entity
               && hitResult.getEntity() != minecraftClient.player) {
               if (!this.criticalOnly.getValue() || this.criticalHitEligible()) {
                  this.hitRequests.add(new Particles.HitParticleRequest(entity.getEntityPos(), entity.getHeight()));
               }
            }
         }
      };
      this.playerTickListener = event -> {
         if (minecraftClient.player != null && minecraftClient.world != null) {
            long nowMillis = System.currentTimeMillis();
            this.drainHitRequests(nowMillis);
            this.hitSprites.removeIf(hitParticle -> hitParticle.expired(nowMillis) || WorldProjection.projectNearViewport(hitParticle.position, 1.0F, 1.0F) == null);
            if (this.effects.isSelected(("Мир"))) {
               this.worldSprites
                  .removeIf(
                     worldParticle -> worldParticle.expired(nowMillis)
                        || worldParticle.position.distanceTo(minecraftClient.player.getEntityPos()) > this.maxDistance.getValue() + (10.0D)
                        || WorldProjection.projectNearViewport(worldParticle.position, 1.0F, 1.0F) == null
                  );

               for (int spawnIndex = 0; spawnIndex < (int)this.worldCount.getValue() && this.worldSprites.size() < (int)this.worldCount.getValue(); spawnIndex++) {
                  this.spawnWorldSprite(nowMillis);
               }
            } else {
               this.worldSprites.clear();
            }

            for (Particles.SpriteParticle hitSprite : this.hitSprites) {
               hitSprite.tick();
            }

            for (Particles.SpriteParticle worldSprite : this.worldSprites) {
               worldSprite.tick();
            }

            this.resolveSpriteCollisions();
            this.resolvePlayerCollisions(this.hitSprites);
            if (this.effects.isSelected(("Мир"))) {
               this.resolvePlayerCollisions(this.worldSprites);
            }
         }
      };
      this.worldRenderListener = event -> {
         if (minecraftClient.player != null && minecraftClient.world != null) {
            long nowMillis = System.currentTimeMillis();
            this.removeExpiredShapes(nowMillis);
            this.maintainShapeCounts(nowMillis);
            this.drawGlow(event.getMatrices(), event.getTickDelta(), nowMillis);
            this.drawSprites(this.hitSprites, this.images, this.hitSize.getValue(), event.getMatrices(), event.getTickDelta(), nowMillis);
            if (this.effects.isSelected(("Мир"))) {
               this.drawSprites(this.worldSprites, this.worldImages, this.worldSize.getValue(), event.getMatrices(), event.getTickDelta(), nowMillis);
            }

            this.drawShapeWires(event.getMatrices(), nowMillis);
         }
      };
      this.effects = this.addSetting(new MultiSelectSetting(("Эффекты"), ("Какие частицы живут вокруг игрока."), new String[]{("Мир"), ("Кубы"), ("Пирамиды"), ("Тотем"), ("Полумесяц"), ("Сердце"), ("Звёзды"), ("Делюкс")}, ("Мир")));
      this.shapeCount = this.addSetting(new DecimalSetting(("Количество"), ("Сколько частиц каждой фигуры держать вокруг."), (14.0D), 1.0, (80.0D), 1.0));
      this.lifetime = this.addSetting(new DecimalSetting(("Жизнь"), ("Время жизни частицы в миллисекундах."), (1600.0D), (500.0D), (5000.0D), (100.0D)));
      this.size = this.addSetting(new DecimalSetting(("Размер"), ("Множитель размера фигур."), (0.45D), (0.1D), (1.5D), (0.05D)));
      this.minSpawnDistance = this.addSetting(new DecimalSetting(("Спавн от"), ("Ближе этой дистанции фигуры не спавнятся."), (4.0D), (0.5D), (80.0D), (0.5D)));
      this.maxSpawnDistance = this.addSetting(new DecimalSetting(("Спавн до"), ("Дальше этой дистанции фигуры не спавнятся."), (10.0D), 1.0, (80.0D), (0.5D)));
      this.shapeHeight = this.addSetting(new DecimalSetting(("Высота фигур"), ("Вертикальный разброс спавна фигур вверх и вниз."), (6.0D), (0.5D), (40.0D), (0.5D)));
      this.glow = this.addSetting(new DecimalSetting(("Свечение"), ("Сила свечения фигур и картинок."), 1.0, 0.0, (3.0D), (0.1D)));
      this.addSetting(new SettingSeparator(("Мир")));
      this.worldSpawn = this.addSetting(new ModeSetting(("Спавн мира"), ("Где живут мировые частицы."), ("Небо"), ("Небо"), ("Земля")));
      this.worldImages = this.addSetting(new MultiSelectSetting(("Картинки мира"), ("Какие картинки летают в мире."), new String[]{("Светлячок"), ("Звезда"), ("Сердце"), ("Снежинка"), ("Доллар")}, ("Светлячок")));
      this.worldCount = this.addSetting(new DecimalSetting(("Кол-во в мире"), ("Сколько частиц держать в мире."), (100.0D), (10.0D), (300.0D), (10.0D)));
      this.worldSize = this.addSetting(new DecimalSetting(("Размер в мире"), ("Размер мировых картинок."), (0.1D), (0.03D), (0.5D), (0.005D)));
      this.minDistance = this.addSetting(new DecimalSetting(("Дистанция от"), ("Ближе этой дистанции мировые частицы не спавнятся."), (2.0D), 1.0, (100.0D), 1.0));
      this.maxDistance = this.addSetting(new DecimalSetting(("Дистанция до"), ("Дальше этой дистанции мировые частицы не спавнятся."), (16.0D), (5.0D), (120.0D), 1.0));
      this.worldHeight = this.addSetting(new DecimalSetting(("Высота мира"), ("Насколько высоко над игроком спавнятся небесные частицы."), (8.0D), (1.5D), (40.0D), (0.5D)));
      this.addSetting(new SettingSeparator(("Хит партиклы")));
      this.hitParticles = this.addSetting(new BooleanSetting(("Хит партиклы"), ("Разлетающиеся картинки при ударе по цели."), false));
      this.images = this.addSetting(new MultiSelectSetting(("Картинки"), ("Какие картинки вылетают из цели."), new String[]{("Светлячок"), ("Звезда"), ("Сердце"), ("Снежинка"), ("Доллар")}, ("Светлячок")));
      this.hitParticleCount = this.addSetting(new DecimalSetting(("Кол-во"), ("Сколько картинок спавнить на удар."), (12.0D), 1.0, (50.0D), 1.0));
      this.hitSize = this.addSetting(new DecimalSetting(("Размер удара"), ("Размер картинок при ударе."), (0.1D), (0.03D), (0.5D), (0.005D)));
      this.criticalOnly = this.addSetting(new BooleanSetting(("Только криты"), ("Спавнить хит частицы только на критах."), false));
      this.addSetting(new SettingSeparator(("Цвет")));
      this.useCustomColor = this.addSetting(new BooleanSetting(("Свой цвет"), ("Красить частицы своим цветом вместо темы клиента."), false));
      this.color = this.addSetting(new ColorSetting(("Цвет"), ("Кастомный цвет частиц.")));
      this.glow.setVisibility(() -> this.effects.getSelectedCount() > 0 || this.hitParticles.getValue());
      this.worldSpawn.setVisibility(() -> this.effects.isSelected(("Мир")));
      this.worldImages.setVisibility(() -> this.effects.isSelected(("Мир")));
      this.worldCount.setVisibility(() -> this.effects.isSelected(("Мир")));
      this.worldSize.setVisibility(() -> this.effects.isSelected(("Мир")));
      this.minDistance.setVisibility(() -> this.effects.isSelected(("Мир")));
      this.maxDistance.setVisibility(() -> this.effects.isSelected(("Мир")));
      this.worldHeight.setVisibility(() -> this.effects.isSelected(("Мир")) && this.worldSpawn.isSelected(("Небо")));
      this.images.setVisibility(() -> this.hitParticles.getValue());
      this.hitParticleCount.setVisibility(() -> this.hitParticles.getValue());
      this.hitSize.setVisibility(() -> this.hitParticles.getValue());
      this.criticalOnly.setVisibility(() -> this.hitParticles.getValue());
      this.color.setVisibility(() -> this.useCustomColor.getValue());

      for (int cornerIndex = 0; cornerIndex < 8; cornerIndex++) {
         this.shapeCorners[cornerIndex] = new Vector3f();
      }
   }

   @Override
   protected void onEnable() {
      this.cubeParticles.clear();
      this.pyramidParticles.clear();
      this.totemParticles.clear();
      this.crescentParticles.clear();
      this.heartParticles.clear();
      this.starParticles.clear();
      this.logoParticles.clear();
      this.hitSprites.clear();
      this.worldSprites.clear();
      this.hitRequests.clear();
      this.enabledAt = System.currentTimeMillis();
      if (!this.modelWarmupStarted) {
         this.modelWarmupStarted = true;
         Thread modelThread = new Thread(() -> {
            this.totemModelEdges();
            this.starModelEdges();
            this.heartModelEdges();
            this.logoModelEdges();
            this.crescentModelEdges();
         }, ("mouseclient-particles-models"));
         modelThread.setDaemon(true);
         modelThread.start();
      }
   }

   private Particles.ShapeParticle createShapeParticle(Vec3d origin, double minimumDistance, double maximumDistance, long nowMillis, float rotationRangeX, float rotationRangeY, float rotationRangeZ) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      double spawnAngle = random.nextDouble() * (3.141592653589793D) * (2.0D);
      double spawnDistance = minimumDistance + random.nextDouble() * (maximumDistance - minimumDistance);
      double travelAzimuth = random.nextDouble() * (3.141592653589793D) * (2.0D);
      double travelPolarAngle = Math.acos((2.0D) * random.nextDouble() - 1.0);
      double travelDistance = (1.6D) + random.nextDouble() * (1.8D);
      return new Particles.ShapeParticle(
         origin.add(
            Math.cos(spawnAngle) * spawnDistance, 1.0 + random.nextDouble(-this.shapeHeight.getValue(), this.shapeHeight.getValue()), Math.sin(spawnAngle) * spawnDistance
         ),
         spawnAngle,
         nowMillis,
         this.randomizedLifetime(this.lifetime.getValue()),
         random.nextInt(180),
         (random.nextFloat() - (0.5F)) * rotationRangeX,
         (random.nextFloat() - (0.5F)) * rotationRangeY,
         (random.nextFloat() - (0.5F)) * rotationRangeZ,
         Math.sin(travelPolarAngle) * Math.cos(travelAzimuth) * travelDistance,
         Math.cos(travelPolarAngle) * travelDistance,
         Math.sin(travelPolarAngle) * Math.sin(travelAzimuth) * travelDistance
      );
   }

   private int emitPyramidWires(BufferBuilder builder, long nowMillis) {
      float baseSize = this.size.getValue() * (0.75F);
      int quadCount = 0;

      for (Particles.ShapeParticle particle : this.pyramidParticles) {
         float progress = (float)(nowMillis - particle.createdAt) / (float)particle.lifetimeMillis;
         if (!(progress >= 1.0F)) {
            Vec3d position = particle.linearPosition(progress);
            if (WorldProjection.isSphereVisible(position.x, position.y, position.z, 2.0F)) {
               float halfSize = baseSize * ((0.65F) + progress * (0.55F)) * (0.5F);
               this.shapeRotation.identity().rotateY(particle.rotationY * progress).rotateX(particle.rotationX * progress).rotateZ(particle.rotationZ * progress);
               this.shapeRotation.transform(this.shapeCorners[0].set(-halfSize, -halfSize, -halfSize));
               this.shapeRotation.transform(this.shapeCorners[1].set(halfSize, -halfSize, -halfSize));
               this.shapeRotation.transform(this.shapeCorners[2].set(halfSize, -halfSize, halfSize));
               this.shapeRotation.transform(this.shapeCorners[3].set(-halfSize, -halfSize, halfSize));
               this.shapeRotation.transform(this.shapeCorners[4].set(0.0F, halfSize, 0.0F));
               int wireColor = ArgbColor.multiplyAlpha(this.particleColor(particle.colorPhaseDegrees), this.shapeAlpha(progress));

               for (int edgeIndex = 0; edgeIndex < PYRAMID_EDGES.length; edgeIndex += 2) {
                  quadCount += this.emitWireSegment(
                     builder,
                     position.x + this.shapeCorners[PYRAMID_EDGES[edgeIndex]].x,
                     position.y + this.shapeCorners[PYRAMID_EDGES[edgeIndex]].y,
                     position.z + this.shapeCorners[PYRAMID_EDGES[edgeIndex]].z,
                     position.x + this.shapeCorners[PYRAMID_EDGES[edgeIndex + 1]].x,
                     position.y + this.shapeCorners[PYRAMID_EDGES[edgeIndex + 1]].y,
                     position.z + this.shapeCorners[PYRAMID_EDGES[edgeIndex + 1]].z,
                     wireColor
                  );
               }
            }
         }
      }

      return quadCount;
   }

   private static void registerCoordinateEdge(
      HashMap<String, Particles.CoordinateModelEdge> edges,
      float startX,
      float startY,
      float startZ,
      float endX,
      float endY,
      float endZ,
      float normalX,
      float normalY,
      float normalZ
   ) {
      int quantizedStartX = Math.round(startX * (10000.0F));
      int quantizedStartY = Math.round(startY * (10000.0F));
      int quantizedStartZ = Math.round(startZ * (10000.0F));
      int quantizedEndX = Math.round(endX * (10000.0F));
      int quantizedEndY = Math.round(endY * (10000.0F));
      int quantizedEndZ = Math.round(endZ * (10000.0F));
      boolean reverseEndpoints = quantizedStartX != quantizedEndX ? quantizedStartX > quantizedEndX : (quantizedStartY != quantizedEndY ? quantizedStartY > quantizedEndY : quantizedStartZ > quantizedEndZ);
      String edgeKey = reverseEndpoints
         ? quantizedEndX + "," + quantizedEndY + "," + quantizedEndZ + "|" + quantizedStartX + "," + quantizedStartY + "," + quantizedStartZ
         : quantizedStartX + "," + quantizedStartY + "," + quantizedStartZ + "|" + quantizedEndX + "," + quantizedEndY + "," + quantizedEndZ;
      Particles.CoordinateModelEdge edge = (Particles.CoordinateModelEdge)edges.get(edgeKey);
      if (edge == null) {
         edges.put(edgeKey, new Particles.CoordinateModelEdge(startX, startY, startZ, endX, endY, endZ, normalX, normalY, normalZ));
      } else {
         edge.faceCount++;
         if (Math.abs(edge.normalX * normalX + edge.normalY * normalY + edge.normalZ * normalZ) < (0.985F)) {
            edge.coplanar = false;
         }
      }
   }

   private void drawWireQuads(MatrixStack matrices, BufferBuilder builder, int quadCount) {
      BuiltBuffer builtBuffer = builder.endNullable();
      if (builtBuffer != null) {
         try (builtBuffer) {
            if (quadCount != 0 && minecraftClient.getFramebuffer() != null && minecraftClient.getFramebuffer().getColorAttachmentView() != null) {
               if (this.wireVertexBuffer != null && !this.wireVertexBuffer.isClosed() && this.wireVertexBuffer.size() >= builtBuffer.getBuffer().remaining()) {
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.wireVertexBuffer.slice(0L, builtBuffer.getBuffer().remaining()), builtBuffer.getBuffer());
               } else {
                  if (this.wireVertexBuffer != null && !this.wireVertexBuffer.isClosed()) {
                     this.wireVertexBuffer.close();
                  }

                  this.wireVertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_particles_wires"), 40, builtBuffer.getBuffer());
               }

               GpuBufferSlice bufferSlice = RenderSystem.getDynamicUniforms().write(matrices.peek().getPositionMatrix(), WHITE_MODULATOR, ZERO_OFFSET, IDENTITY_TEXTURE_MATRIX);

               try (RenderPass renderPass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(
                        () -> ("mouseclient_particles_wires"),
                        minecraftClient.getFramebuffer().getColorAttachmentView(),
                        OptionalInt.empty(),
                        DepthSnapshot.depthView(),
                        OptionalDouble.empty()
                     )) {
                  renderPass.setPipeline(WIRE_PIPELINE);
                  renderPass.setUniform(("DynamicTransforms"), bufferSlice);
                  renderPass.setUniform(("Projection"), RenderSystem.getProjectionMatrixBuffer());
                  renderPass.setVertexBuffer(0, this.wireVertexBuffer);
                  renderPass.setIndexBuffer(
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(quadCount * 6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                  );
                  renderPass.drawIndexed(0, 0, quadCount * 6, 1);
                  return;
               }
            }
         }
      }
   }

   private synchronized float[] totemModelEdges() {
      if (this.totemLoadAttempted) {
         return this.totemEdges;
      }

      this.totemLoadAttempted = true;
      this.totemEdges = this.loadObjEdges(("/assets/mouseclient/models/particles/totem_undying.obj"), (-0.75F), false);
      return this.totemEdges;
   }

   private synchronized float[] logoModelEdges() {
      if (this.logoLoadAttempted) {
         return this.logoEdges;
      }

      this.logoLoadAttempted = true;
      InputStream input = Particles.class.getResourceAsStream(("/assets/mouseclient/models/particles/mouseclient.obj"));
      if (input == null) {
         return null;
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
         ArrayList vertices = new ArrayList();
         HashMap<String, Particles.CoordinateModelEdge> edges = new HashMap<>();
         float minX = Float.POSITIVE_INFINITY;
         float minY = Float.POSITIVE_INFINITY;
         float minZ = Float.POSITIVE_INFINITY;
         float maxX = Float.NEGATIVE_INFINITY;
         float maxY = Float.NEGATIVE_INFINITY;
         float maxZ = Float.NEGATIVE_INFINITY;

         String line;
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith(("v "))) {
               String[] vertexParts = line.split(("\\s+"));
               Vector3f vertex = new Vector3f(Float.parseFloat(vertexParts[1]), -Float.parseFloat(vertexParts[2]), Float.parseFloat(vertexParts[3]));
               vertices.add(vertex);
               minX = Math.min(minX, vertex.x);
               maxX = Math.max(maxX, vertex.x);
               minY = Math.min(minY, vertex.y);
               maxY = Math.max(maxY, vertex.y);
               minZ = Math.min(minZ, vertex.z);
               maxZ = Math.max(maxZ, vertex.z);
            } else if (line.startsWith(("f "))) {
               String[] faceParts = line.split(("\\s+"));
               Vector3f firstVertex = (Vector3f)vertices.get(Integer.parseInt(faceParts[1].split(("/"))[0]) - 1);
               Vector3f secondVertex = (Vector3f)vertices.get(Integer.parseInt(faceParts[2].split(("/"))[0]) - 1);
               Vector3f thirdVertex = (Vector3f)vertices.get(Integer.parseInt(faceParts[3].split(("/"))[0]) - 1);
               float normalX = (secondVertex.y - firstVertex.y) * (thirdVertex.z - firstVertex.z) - (secondVertex.z - firstVertex.z) * (thirdVertex.y - firstVertex.y);
               float normalY = (secondVertex.z - firstVertex.z) * (thirdVertex.x - firstVertex.x) - (secondVertex.x - firstVertex.x) * (thirdVertex.z - firstVertex.z);
               float normalZ = (secondVertex.x - firstVertex.x) * (thirdVertex.y - firstVertex.y) - (secondVertex.y - firstVertex.y) * (thirdVertex.x - firstVertex.x);
               float normalLength = (float)Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
               if (!(normalLength < (1.0E-8F))) {
                  registerCoordinateEdge(edges, firstVertex.x, firstVertex.y, firstVertex.z, secondVertex.x, secondVertex.y, secondVertex.z, normalX / normalLength, normalY / normalLength, normalZ / normalLength);
                  registerCoordinateEdge(edges, secondVertex.x, secondVertex.y, secondVertex.z, thirdVertex.x, thirdVertex.y, thirdVertex.z, normalX / normalLength, normalY / normalLength, normalZ / normalLength);
                  registerCoordinateEdge(edges, thirdVertex.x, thirdVertex.y, thirdVertex.z, firstVertex.x, firstVertex.y, firstVertex.z, normalX / normalLength, normalY / normalLength, normalZ / normalLength);
               }
            }
         }

         float centerX = (minX + maxX) * (0.5F);
         float centerY = (minY + maxY) * (0.5F);
         float centerZ = (minZ + maxZ) * (0.5F);
         float normalization = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) > (1.0E-5F)
            ? 1.0F / Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ))
            : 1.0F;
         ArrayList edgeCoordinates = new ArrayList(edges.size() * 6);

         for (Particles.CoordinateModelEdge edge : edges.values()) {
            if (edge.faceCount == 1 || !edge.coplanar) {
               edgeCoordinates.add((edge.startX - centerX) * normalization);
               edgeCoordinates.add((edge.startY - centerY) * normalization);
               edgeCoordinates.add((edge.startZ - centerZ) * normalization);
               edgeCoordinates.add((edge.endX - centerX) * normalization);
               edgeCoordinates.add((edge.endY - centerY) * normalization);
               edgeCoordinates.add((edge.endZ - centerZ) * normalization);
            }
         }

         this.logoEdges = new float[edgeCoordinates.size()];

         for (int coordinateIndex = 0; coordinateIndex < edgeCoordinates.size(); coordinateIndex++) {
            this.logoEdges[coordinateIndex] = (Float)edgeCoordinates.get(coordinateIndex);
         }
      } catch (Exception error) {
         this.logoEdges = null;
      }

      return this.logoEdges;
   }

   private void drawShapeWires(MatrixStack matrices, long nowMillis) {
      boolean hasShapes = this.effects.isSelected(("Кубы")) && !this.cubeParticles.isEmpty()
         || this.effects.isSelected(("Пирамиды")) && !this.pyramidParticles.isEmpty()
         || this.effects.isSelected(("Тотем")) && !this.totemParticles.isEmpty()
         || this.effects.isSelected(("Полумесяц")) && !this.crescentParticles.isEmpty()
         || this.effects.isSelected(("Сердце")) && !this.heartParticles.isEmpty()
         || this.effects.isSelected(("Звёзды")) && !this.starParticles.isEmpty()
         || this.effects.isSelected(("Делюкс")) && !this.logoParticles.isEmpty();
      if (hasShapes) {
         BufferBuilder builder = new BufferBuilder(this.geometryAllocator(), DrawMode.QUADS, VertexFormats.POSITION_COLOR);
         int quadCount = 0;
         if (this.effects.isSelected(("Кубы"))) {
            quadCount += this.emitCubeWires(builder, nowMillis);
         }

         if (this.effects.isSelected(("Пирамиды"))) {
            quadCount += this.emitPyramidWires(builder, nowMillis);
         }

         if (this.effects.isSelected(("Тотем"))) {
            quadCount += this.emitModelWires(this.totemParticles, this.totemModelEdges(), (0.78F), (0.75F), (0.35F), true, builder, nowMillis);
         }

         if (this.effects.isSelected(("Полумесяц"))) {
            quadCount += this.emitModelWires(this.crescentParticles, this.crescentModelEdges(), (0.72F), (0.7F), (0.45F), false, builder, nowMillis);
         }

         if (this.effects.isSelected(("Сердце"))) {
            quadCount += this.emitModelWires(this.heartParticles, this.heartModelEdges(), (0.7F), (0.7F), (0.45F), false, builder, nowMillis);
         }

         if (this.effects.isSelected(("Звёзды"))) {
            quadCount += this.emitModelWires(this.starParticles, this.starModelEdges(), (0.9F), (0.7F), (0.45F), false, builder, nowMillis);
         }

         if (this.effects.isSelected(("Делюкс"))) {
            quadCount += this.emitModelWires(this.logoParticles, this.logoModelEdges(), (0.9F), (0.7F), (0.45F), false, builder, nowMillis);
         }

         this.drawWireQuads(matrices, builder, quadCount);
      }
   }

   private void drawTexturedQuads(String texturePath, MatrixStack matrices, BufferBuilder builder, int quadCount) {
      BuiltBuffer builtBuffer = builder.endNullable();
      if (builtBuffer != null) {
         try (builtBuffer) {
            if (quadCount != 0
               && minecraftClient.getTextureManager().getTexture(Identifier.of(("mouseclient"), texturePath)).getGlTextureView() != null
               && minecraftClient.getFramebuffer() != null
               && minecraftClient.getFramebuffer().getColorAttachmentView() != null) {
               GpuBuffer buffer = this.textureVertexBuffers.get(texturePath);
               if (buffer != null && !buffer.isClosed() && buffer.size() >= builtBuffer.getBuffer().remaining()) {
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, builtBuffer.getBuffer().remaining()), builtBuffer.getBuffer());
               } else {
                  if (buffer != null && !buffer.isClosed()) {
                     buffer.close();
                  }

                  buffer = RenderSystem.getDevice().createBuffer(() -> "mouseclient_particles_" + texturePath, 40, builtBuffer.getBuffer());
                  this.textureVertexBuffers.put(texturePath, buffer);
               }

               GpuBufferSlice bufferSlice = RenderSystem.getDynamicUniforms().write(matrices.peek().getPositionMatrix(), WHITE_MODULATOR, ZERO_OFFSET, IDENTITY_TEXTURE_MATRIX);

               try (RenderPass renderPass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(
                        () -> ("mouseclient_particles"),
                        minecraftClient.getFramebuffer().getColorAttachmentView(),
                        OptionalInt.empty(),
                        DepthSnapshot.depthView(),
                        OptionalDouble.empty()
                     )) {
                  renderPass.setPipeline(SPRITE_PIPELINE);
                  renderPass.bindTexture(
                     ("Sampler0"),
                     minecraftClient.getTextureManager().getTexture(Identifier.of(("mouseclient"), texturePath)).getGlTextureView(),
                     RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
                  );
                  renderPass.setUniform(("DynamicTransforms"), bufferSlice);
                  renderPass.setUniform(("Projection"), RenderSystem.getProjectionMatrixBuffer());
                  renderPass.setVertexBuffer(0, buffer);
                  renderPass.setIndexBuffer(
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(quadCount * 6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                  );
                  renderPass.drawIndexed(0, 0, quadCount * 6, 1);
                  return;
               }
            }
         }
      }
   }

   private BufferAllocator geometryAllocator() {
      if (this.geometryAllocator == null) {
         this.geometryAllocator = new BufferAllocator((65536));
      }

      CloseableBuffer allocatedBuffer = this.geometryAllocator.getAllocated();
      if (allocatedBuffer != null) {
         allocatedBuffer.close();
      }

      return this.geometryAllocator;
   }

   private synchronized float[] crescentModelEdges() {
      if (this.crescentEdges != null) {
         return this.crescentEdges;
      }

      float outerRadius = (0.5F);
      float innerRadius = (0.42F);
      float centerSeparation = (0.3F);
      float maximumDepth = (0.13F);
      float minimumDepth = (0.022F);
      float intersectionX = (outerRadius * outerRadius - innerRadius * innerRadius + centerSeparation * centerSeparation) / (2.0F * centerSeparation);
      float intersectionY = (float)Math.sqrt(Math.max(0.0F, outerRadius * outerRadius - intersectionX * intersectionX));
      float outerStartAngle = (float)Math.atan2(intersectionY, intersectionX);
      float innerStartAngle = (float)Math.atan2(intersectionY, intersectionX - centerSeparation);
      byte segmentCount = 18;
      byte depthEdgeStep = 3;
      float centerOffsetX = (outerRadius - intersectionX) * (0.5F);
      float outerSweep = (6.2831855F) - 2.0F * outerStartAngle;
      float innerSweep = (6.2831855F) - 2.0F * innerStartAngle;
      float[] outerX = new float[segmentCount + 1];
      float[] outerY = new float[segmentCount + 1];
      float[] outerDepth = new float[segmentCount + 1];
      float[] innerX = new float[segmentCount + 1];
      float[] innerY = new float[segmentCount + 1];
      float[] innerDepth = new float[segmentCount + 1];

      for (int sampleIndex = 0; sampleIndex <= segmentCount; sampleIndex++) {
         float outerAngle = outerStartAngle + outerSweep / segmentCount * sampleIndex;
         outerX[sampleIndex] = (float)Math.cos(outerAngle) * outerRadius + centerOffsetX;
         outerY[sampleIndex] = (float)Math.sin(outerAngle) * outerRadius;
         outerDepth[sampleIndex] = minimumDepth + (maximumDepth - minimumDepth) * (float)Math.sin((outerAngle - outerStartAngle) / outerSweep * (3.141592653589793D));
         float innerAngle = innerStartAngle + innerSweep / segmentCount * sampleIndex;
         innerX[sampleIndex] = (float)Math.cos(innerAngle) * innerRadius + centerSeparation + centerOffsetX;
         innerY[sampleIndex] = (float)Math.sin(innerAngle) * innerRadius;
         innerDepth[sampleIndex] = minimumDepth + (maximumDepth - minimumDepth) * (float)Math.sin((innerAngle - innerStartAngle) / innerSweep * (3.141592653589793D));
      }

      ArrayList edgeCoordinates = new ArrayList(segmentCount * 28 + 64);

      for (int outerSegment = 0; outerSegment < segmentCount; outerSegment++) {
         edgeCoordinates.add(outerX[outerSegment]);
         edgeCoordinates.add(outerY[outerSegment]);
         edgeCoordinates.add(outerDepth[outerSegment]);
         edgeCoordinates.add(outerX[outerSegment + 1]);
         edgeCoordinates.add(outerY[outerSegment + 1]);
         edgeCoordinates.add(outerDepth[outerSegment + 1]);
         edgeCoordinates.add(outerX[outerSegment]);
         edgeCoordinates.add(outerY[outerSegment]);
         edgeCoordinates.add(-outerDepth[outerSegment]);
         edgeCoordinates.add(outerX[outerSegment + 1]);
         edgeCoordinates.add(outerY[outerSegment + 1]);
         edgeCoordinates.add(-outerDepth[outerSegment + 1]);
      }

      for (int innerSegment = 0; innerSegment < segmentCount; innerSegment++) {
         edgeCoordinates.add(innerX[innerSegment]);
         edgeCoordinates.add(innerY[innerSegment]);
         edgeCoordinates.add(innerDepth[innerSegment]);
         edgeCoordinates.add(innerX[innerSegment + 1]);
         edgeCoordinates.add(innerY[innerSegment + 1]);
         edgeCoordinates.add(innerDepth[innerSegment + 1]);
         edgeCoordinates.add(innerX[innerSegment]);
         edgeCoordinates.add(innerY[innerSegment]);
         edgeCoordinates.add(-innerDepth[innerSegment]);
         edgeCoordinates.add(innerX[innerSegment + 1]);
         edgeCoordinates.add(innerY[innerSegment + 1]);
         edgeCoordinates.add(-innerDepth[innerSegment + 1]);
      }

      for (int outerDepthIndex = 0; outerDepthIndex <= segmentCount; outerDepthIndex += depthEdgeStep) {
         edgeCoordinates.add(outerX[outerDepthIndex]);
         edgeCoordinates.add(outerY[outerDepthIndex]);
         edgeCoordinates.add(outerDepth[outerDepthIndex]);
         edgeCoordinates.add(outerX[outerDepthIndex]);
         edgeCoordinates.add(outerY[outerDepthIndex]);
         edgeCoordinates.add(-outerDepth[outerDepthIndex]);
      }

      for (int innerDepthIndex = depthEdgeStep; innerDepthIndex < segmentCount; innerDepthIndex += depthEdgeStep) {
         edgeCoordinates.add(innerX[innerDepthIndex]);
         edgeCoordinates.add(innerY[innerDepthIndex]);
         edgeCoordinates.add(innerDepth[innerDepthIndex]);
         edgeCoordinates.add(innerX[innerDepthIndex]);
         edgeCoordinates.add(innerY[innerDepthIndex]);
         edgeCoordinates.add(-innerDepth[innerDepthIndex]);
      }

      this.crescentEdges = new float[edgeCoordinates.size()];

      for (int coordinateIndex = 0; coordinateIndex < edgeCoordinates.size(); coordinateIndex++) {
         this.crescentEdges[coordinateIndex] = (Float)edgeCoordinates.get(coordinateIndex);
      }

      return this.crescentEdges;
   }

   private void resolveSpriteCollisions() {
      java.util.ArrayList<com.mouseclient.render.pipeline.Particles.SpriteParticle> sprites = this.collisionScratch;
      sprites.clear();
      sprites.addAll(this.hitSprites);
      if (this.effects.isSelected(("Мир"))) {
         sprites.addAll(this.worldSprites);
      }

      int firstIndex = 0;

      for (int spriteCount = sprites.size(); firstIndex < spriteCount; firstIndex++) {
         Particles.SpriteParticle firstSprite = (Particles.SpriteParticle)sprites.get(firstIndex);

         for (int secondIndex = firstIndex + 1; secondIndex < spriteCount; secondIndex++) {
            Particles.SpriteParticle secondSprite = (Particles.SpriteParticle)sprites.get(secondIndex);
            double combinedRadius = firstSprite.collisionRadius + secondSprite.collisionRadius;
            double deltaX = secondSprite.position.x - firstSprite.position.x;
            if (!(deltaX >= combinedRadius) && !(-deltaX >= combinedRadius)) {
               double deltaY = secondSprite.position.y - firstSprite.position.y;
               if (!(deltaY >= combinedRadius) && !(-deltaY >= combinedRadius)) {
                  double deltaZ = secondSprite.position.z - firstSprite.position.z;
                  if (!(deltaZ >= combinedRadius) && !(-deltaZ >= combinedRadius)) {
                     double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                     if (!(distanceSquared >= combinedRadius * combinedRadius) && !(distanceSquared < (1.0E-8D))) {
                        Vec3d position = new Vec3d(deltaX, deltaY, deltaZ);
                        double distance = Math.sqrt(distanceSquared);
                        Vec3d separationNormal = position.multiply(1.0 / distance);
                        firstSprite.position = firstSprite.position.subtract(separationNormal.multiply((combinedRadius - distance) * (0.5D)));
                        secondSprite.position = secondSprite.position.add(separationNormal.multiply((combinedRadius - distance) * (0.5D)));
                        double relativeNormalSpeed = secondSprite.velocity.subtract(firstSprite.velocity).dotProduct(separationNormal);
                        if (relativeNormalSpeed < 0.0) {
                           firstSprite.velocity = firstSprite.velocity.subtract(separationNormal.multiply(-relativeNormalSpeed * (0.45D)));
                           secondSprite.velocity = secondSprite.velocity.add(separationNormal.multiply(-relativeNormalSpeed * (0.45D)));
                        }
                     }
                  }
               }
            }
         }
      }

      sprites.clear();
   }

   private void drawGlow(MatrixStack matrices, float tickDelta, long nowMillis) {
      boolean hasHitGlow = this.glow.getValue() > 0.0F && this.hitParticles.getValue() && !this.hitSprites.isEmpty();
      boolean hasWorldGlow = this.glow.getValue() > 0.0F && this.effects.isSelected(("Мир")) && !this.worldSprites.isEmpty();
      boolean hasShapeGlow = this.glow.getValue() > 0.0F
         && (
            this.effects.isSelected(("Кубы")) && !this.cubeParticles.isEmpty()
               || this.effects.isSelected(("Пирамиды")) && !this.pyramidParticles.isEmpty()
               || this.effects.isSelected(("Тотем")) && !this.totemParticles.isEmpty()
               || this.effects.isSelected(("Полумесяц")) && !this.crescentParticles.isEmpty()
               || this.effects.isSelected(("Сердце")) && !this.heartParticles.isEmpty()
               || this.effects.isSelected(("Звёзды")) && !this.starParticles.isEmpty()
         );
      if (hasWorldGlow || hasShapeGlow || hasHitGlow) {
         Quaternionf cameraRotation = new Quaternionf(minecraftClient.gameRenderer.getCamera().getRotation()).rotateY((3.1415927F));
         Vector3f scratch = new Vector3f();
         BufferBuilder builder = new BufferBuilder(this.geometryAllocator(), DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
         int quadCount = 0;
         if (hasShapeGlow) {
            if (this.effects.isSelected(("Кубы"))) {
               quadCount += this.emitShapeGlow(this.cubeParticles, this.size.getValue() * (1.7F), false, builder, cameraRotation, scratch, nowMillis);
            }

            if (this.effects.isSelected(("Пирамиды"))) {
               quadCount += this.emitShapeGlow(this.pyramidParticles, this.size.getValue() * (1.7F), false, builder, cameraRotation, scratch, nowMillis);
            }

            if (this.effects.isSelected(("Тотем"))) {
               quadCount += this.emitShapeGlow(this.totemParticles, this.size.getValue() * 2.0F, true, builder, cameraRotation, scratch, nowMillis);
            }

            if (this.effects.isSelected(("Полумесяц"))) {
               quadCount += this.emitShapeGlow(this.crescentParticles, this.size.getValue() * (1.8F), false, builder, cameraRotation, scratch, nowMillis);
            }

            if (this.effects.isSelected(("Сердце"))) {
               quadCount += this.emitShapeGlow(this.heartParticles, this.size.getValue() * (1.8F), false, builder, cameraRotation, scratch, nowMillis);
            }

            if (this.effects.isSelected(("Звёзды"))) {
               quadCount += this.emitShapeGlow(this.starParticles, this.size.getValue() * (1.8F), false, builder, cameraRotation, scratch, nowMillis);
            }
         }

         if (hasHitGlow) {
            quadCount += this.emitSpriteGlow(this.hitSprites, this.hitSize.getValue() / (1.5F), tickDelta, builder, cameraRotation, scratch, nowMillis);
         }

         if (hasWorldGlow) {
            quadCount += this.emitSpriteGlow(this.worldSprites, this.worldSize.getValue(), tickDelta, builder, cameraRotation, scratch, nowMillis);
         }

         this.drawTexturedQuads(("textures/particles/glowboost.png"), matrices, builder, quadCount);
      }
   }

   private int emitShapeGlow(ArrayList<Particles.ShapeParticle> particles, float baseSize, boolean totemMotion, BufferBuilder builder, Quaternionf rotation, Vector3f scratch, long nowMillis) {
      int quadCount = 0;

      for (Particles.ShapeParticle particle : particles) {
         float progress = (float)(nowMillis - particle.createdAt) / (float)particle.lifetimeMillis;
         if (!(progress >= 1.0F)) {
            Vec3d position = totemMotion ? particle.totemPosition(progress) : particle.linearPosition(progress);
            if (WorldProjection.isSphereVisible(position.x, position.y, position.z, 2.0F)) {
               int glowColor = this.particleColor(particle.colorPhaseDegrees);
               float pulse = (0.85F) + (0.15F) * MathHelper.sin((float)particle.phase * (3.0F) + progress * (6.0F));
               this.emitBillboard(
                  builder,
                  rotation,
                  scratch,
                  position.x,
                  position.y,
                  position.z,
                  baseSize * ((0.95F) + progress * (0.25F)) * pulse,
                  ArgbColor.multiplyAlpha(glowColor, this.shapeAlpha(progress) * (0.22F) * this.glow.getValue())
               );
               this.emitBillboard(
                  builder,
                  rotation,
                  scratch,
                  position.x,
                  position.y,
                  position.z,
                  baseSize * (0.55F) * ((0.9F) + progress * (0.2F)) * pulse,
                  ArgbColor.multiplyAlpha(glowColor, this.shapeAlpha(progress) * (0.55F) * Math.min(1.0F, this.glow.getValue()))
               );
               quadCount += 2;
            }
         }
      }

      return quadCount;
   }

   private boolean visibleFromCamera(Vec3d position) {
      if (minecraftClient.getCameraEntity() == null) {
         return false;
      } else {
         Vec3d cameraPosition = minecraftClient.gameRenderer.getCamera().getCameraPos();
         BlockHitResult hitResult = minecraftClient.world
            .raycast(new RaycastContext(cameraPosition, position, ShapeType.COLLIDER, FluidHandling.NONE, minecraftClient.getCameraEntity()));
         if (hitResult.getType() == Type.BLOCK && hitResult.getPos().squaredDistanceTo(cameraPosition) < position.squaredDistanceTo(cameraPosition) - (0.001D)) {
            return false;
         } else {
            return minecraftClient.player.getBoundingBox().expand((0.15D)).raycast(cameraPosition, position).isPresent()
               ? false
               : minecraftClient.world
                  .getOtherEntities(
                     minecraftClient.getCameraEntity(),
                     minecraftClient.getCameraEntity().getBoundingBox().stretch(position.subtract(cameraPosition)).expand((0.3D)),
                     entity -> entity.getBoundingBox().expand((0.1D)).raycast(cameraPosition, position).isPresent()
                  )
                  .isEmpty();
         }
      }
   }

   public static Particles getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Particles();
      }

      return INSTANCE;
   }

   private int emitSpriteGlow(ArrayList<Particles.SpriteParticle> particles, float baseSize, float tickDelta, BufferBuilder builder, Quaternionf rotation, Vector3f scratch, long nowMillis) {
      int quadCount = 0;

      for (Particles.SpriteParticle particle : particles) {
         float opacity = particle.alpha(nowMillis);
         if (!(opacity <= 0.0F)) {
            Vec3d position = particle.previousPosition.lerp(particle.position, tickDelta);
            if (WorldProjection.isSphereVisible(position.x, position.y, position.z, 2.0F)) {
               int glowColor = this.particleColor(particle.colorPhaseDegrees);
               float pulse = (0.85F) + (0.15F) * MathHelper.sin(particle.colorPhaseDegrees + particle.lifeProgress(nowMillis) * (6.0F));
               this.emitBillboard(
                  builder,
                  rotation,
                  scratch,
                  position.x,
                  position.y,
                  position.z,
                  baseSize * (1.9F) * pulse,
                  ArgbColor.multiplyAlpha(glowColor, opacity * (0.22F) * this.glow.getValue())
               );
               this.emitBillboard(
                  builder,
                  rotation,
                  scratch,
                  position.x,
                  position.y,
                  position.z,
                  baseSize * (1.1F) * pulse,
                  ArgbColor.multiplyAlpha(glowColor, opacity * (0.55F) * Math.min(1.0F, this.glow.getValue()))
               );
               quadCount += 2;
            }
         }
      }

      return quadCount;
   }

   private float shapeAlpha(float progress) {
      if (progress < (0.15F)) {
         return progress / (0.15F);
      } else {
         return progress > (0.75F) ? Math.max(0.0F, (1.0F - progress) / (0.25F)) : 1.0F;
      }
   }

   @Override
   protected void onDisable() {
      this.cubeParticles.clear();
      this.pyramidParticles.clear();
      this.totemParticles.clear();
      this.crescentParticles.clear();
      this.heartParticles.clear();
      this.starParticles.clear();
      this.logoParticles.clear();
      this.hitSprites.clear();
      this.worldSprites.clear();
      this.hitRequests.clear();

      for (GpuBuffer buffer : this.textureVertexBuffers.values()) {
         if (!buffer.isClosed()) {
            buffer.close();
         }
      }

      this.textureVertexBuffers.clear();
      if (this.wireVertexBuffer != null && !this.wireVertexBuffer.isClosed()) {
         this.wireVertexBuffer.close();
      }

      this.wireVertexBuffer = null;
      if (this.geometryAllocator != null) {
         this.geometryAllocator.close();
         this.geometryAllocator = null;
      }

      this.collisionScratch.clear();
   }

   private synchronized float[] starModelEdges() {
      if (this.starLoadAttempted) {
         return this.starEdges;
      }

      this.starLoadAttempted = true;
      this.starEdges = this.loadObjEdges(("/assets/mouseclient/models/particles/fire_nether_star.obj"), 0.0F, true);
      return this.starEdges;
   }

   private int emitWireSegment(BufferBuilder builder, double startX, double startY, double startZ, double endX, double endY, double endZ, int color) {
      float relativeStartX = (float)(startX - minecraftClient.gameRenderer.getCamera().getCameraPos().x);
      float relativeStartY = (float)(startY - minecraftClient.gameRenderer.getCamera().getCameraPos().y);
      float relativeStartZ = (float)(startZ - minecraftClient.gameRenderer.getCamera().getCameraPos().z);
      float relativeEndX = (float)(endX - minecraftClient.gameRenderer.getCamera().getCameraPos().x);
      float relativeEndY = (float)(endY - minecraftClient.gameRenderer.getCamera().getCameraPos().y);
      float relativeEndZ = (float)(endZ - minecraftClient.gameRenderer.getCamera().getCameraPos().z);
      float deltaX = relativeEndX - relativeStartX;
      float deltaY = relativeEndY - relativeStartY;
      float deltaZ = relativeEndZ - relativeStartZ;
      float midpointX = (relativeStartX + relativeEndX) * (0.5F);
      float midpointY = (relativeStartY + relativeEndY) * (0.5F);
      float midpointZ = (relativeStartZ + relativeEndZ) * (0.5F);
      float normalX = deltaY * midpointZ - deltaZ * midpointY;
      float normalY = deltaZ * midpointX - deltaX * midpointZ;
      float normalZ = deltaX * midpointY - deltaY * midpointX;
      float normalLength = MathHelper.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
      if (normalLength < (1.0E-6F)) {
         return 0;
      }

      int quadCount = 0;
      if (this.glow.getValue() > (0.05F)) {
         float glowWidthScale = (0.0256F) / normalLength;
         int glowColor = ArgbColor.multiplyAlpha(color, (0.22F) + (0.1F) * this.glow.getValue());
         builder.vertex(relativeStartX - normalX * glowWidthScale, relativeStartY - normalY * glowWidthScale, relativeStartZ - normalZ * glowWidthScale).color(glowColor);
         builder.vertex(relativeStartX + normalX * glowWidthScale, relativeStartY + normalY * glowWidthScale, relativeStartZ + normalZ * glowWidthScale).color(glowColor);
         builder.vertex(relativeEndX + normalX * glowWidthScale, relativeEndY + normalY * glowWidthScale, relativeEndZ + normalZ * glowWidthScale).color(glowColor);
         builder.vertex(relativeEndX - normalX * glowWidthScale, relativeEndY - normalY * glowWidthScale, relativeEndZ - normalZ * glowWidthScale).color(glowColor);
         quadCount++;
      }

      float coreWidthScale = (0.008F) / normalLength;
      builder.vertex(relativeStartX - normalX * coreWidthScale, relativeStartY - normalY * coreWidthScale, relativeStartZ - normalZ * coreWidthScale).color(color);
      builder.vertex(relativeStartX + normalX * coreWidthScale, relativeStartY + normalY * coreWidthScale, relativeStartZ + normalZ * coreWidthScale).color(color);
      builder.vertex(relativeEndX + normalX * coreWidthScale, relativeEndY + normalY * coreWidthScale, relativeEndZ + normalZ * coreWidthScale).color(color);
      builder.vertex(relativeEndX - normalX * coreWidthScale, relativeEndY - normalY * coreWidthScale, relativeEndZ - normalZ * coreWidthScale).color(color);
      return quadCount + 1;
   }

   private long randomizedLifetime(float baseMillis) {
      return Math.max((50L), Math.round(baseMillis * ((0.75F) + ThreadLocalRandom.current().nextFloat() * (0.5F))));
   }

   private void removeExpiredShapes(long nowMillis) {
      this.cubeParticles.removeIf(particle -> nowMillis - particle.createdAt > particle.lifetimeMillis);
      this.pyramidParticles.removeIf(particle -> nowMillis - particle.createdAt > particle.lifetimeMillis);
      this.totemParticles.removeIf(particle -> nowMillis - particle.createdAt > particle.lifetimeMillis);
      this.crescentParticles.removeIf(particle -> nowMillis - particle.createdAt > particle.lifetimeMillis);
      this.heartParticles.removeIf(particle -> nowMillis - particle.createdAt > particle.lifetimeMillis);
      this.starParticles.removeIf(particle -> nowMillis - particle.createdAt > particle.lifetimeMillis);
      this.logoParticles.removeIf(particle -> nowMillis - particle.createdAt > particle.lifetimeMillis);
   }

   private void resolvePlayerCollisions(ArrayList<Particles.SpriteParticle> particles) {
      Vec3d playerMotion = minecraftClient.player
         .getEntityPos()
         .subtract(minecraftClient.player.lastRenderX, minecraftClient.player.lastRenderY, minecraftClient.player.lastRenderZ);

      for (Particles.SpriteParticle particle : particles) {
         double nearestX = MathHelper.clamp(particle.position.x, minecraftClient.player.getBoundingBox().minX, minecraftClient.player.getBoundingBox().maxX);
         double nearestY = MathHelper.clamp(particle.position.y, minecraftClient.player.getBoundingBox().minY, minecraftClient.player.getBoundingBox().maxY);
         double nearestZ = MathHelper.clamp(particle.position.z, minecraftClient.player.getBoundingBox().minZ, minecraftClient.player.getBoundingBox().maxZ);
         Vec3d separation = particle.position.subtract(nearestX, nearestY, nearestZ);
         double distanceSquared = separation.lengthSquared();
         if (!(distanceSquared >= particle.collisionRadius * particle.collisionRadius)) {
            double distance = Math.sqrt(Math.max(distanceSquared, (1.0E-8D)));
            Vec3d separationNormal = distance < (1.0E-4D) ? particle.position.subtract(minecraftClient.player.getEntityPos()) : separation.multiply(1.0 / distance);
            separationNormal = separationNormal.lengthSquared() < (1.0E-8D) ? new Vec3d(0.0, 1.0, 0.0) : separationNormal.normalize();
            particle.position = particle.position.add(separationNormal.multiply(particle.collisionRadius - distance + (0.01D)));
            particle.velocity = particle.velocity
               .add(separationNormal.multiply((0.035D) + Math.min(playerMotion.length() * (0.8D), (0.25D))))
               .add(playerMotion.x * (0.4D), Math.max(playerMotion.y * (0.2D), 0.0), playerMotion.z * (0.4D));
         }
      }
   }

   private int emitModelWires(
      ArrayList<Particles.ShapeParticle> particles, float[] modelEdges, float baseScale, float initialScale, float growthScale, boolean totemMotion, BufferBuilder builder, long nowMillis
   ) {
      if (modelEdges != null && modelEdges.length != 0) {
         float modelScale = this.size.getValue() * baseScale;
         int quadCount = 0;

         for (Particles.ShapeParticle particle : particles) {
            float progress = (float)(nowMillis - particle.createdAt) / (float)particle.lifetimeMillis;
            if (!(progress >= 1.0F)) {
               Vec3d position = totemMotion ? particle.totemPosition(progress) : particle.linearPosition(progress);
               if (WorldProjection.isSphereVisible(position.x, position.y, position.z, 2.0F)) {
                  float animatedScale = modelScale * (initialScale + progress * growthScale);
                  this.shapeRotation.identity().rotateY(particle.rotationY * progress).rotateX(particle.rotationX * progress).rotateZ(particle.rotationZ * progress);
                  int wireColor = ArgbColor.multiplyAlpha(this.particleColor(particle.colorPhaseDegrees), this.shapeAlpha(progress));

                  for (int edgeIndex = 0; edgeIndex < modelEdges.length; edgeIndex += 6) {
                     this.shapeRotation.transform(this.edgeStartScratch.set(modelEdges[edgeIndex] * animatedScale, modelEdges[edgeIndex + 1] * animatedScale, modelEdges[edgeIndex + 2] * animatedScale));
                     this.shapeRotation.transform(this.edgeEndScratch.set(modelEdges[edgeIndex + 3] * animatedScale, modelEdges[edgeIndex + 4] * animatedScale, modelEdges[edgeIndex + 5] * animatedScale));
                     quadCount += this.emitWireSegment(
                        builder,
                        position.x + this.edgeStartScratch.x,
                        position.y + this.edgeStartScratch.y,
                        position.z + this.edgeStartScratch.z,
                        position.x + this.edgeEndScratch.x,
                        position.y + this.edgeEndScratch.y,
                        position.z + this.edgeEndScratch.z,
                        wireColor
                     );
                  }
               }
            }
         }

         return quadCount;
      } else {
         return 0;
      }
   }

   private void maintainShapeList(
      ArrayList<Particles.ShapeParticle> particles, Vec3d origin, double minimumDistance, double maximumDistance, long nowMillis, int count, float rotationRangeX, float rotationRangeY, float rotationRangeZ
   ) {
      while (particles.size() > count) {
         particles.remove(0);
      }

      while (particles.size() < count) {
         particles.add(this.createShapeParticle(origin, minimumDistance, maximumDistance, nowMillis, rotationRangeX, rotationRangeY, rotationRangeZ));
      }
   }

   private void spawnWorldSprite(long nowMillis) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      double minimumDistance = Math.min(this.minDistance.getValue(), this.maxDistance.getValue());
      double spawnDistance = minimumDistance + random.nextDouble() * Math.max((0.1D), this.maxDistance.getValue() - minimumDistance);
      double spawnAngle = Math.toRadians(minecraftClient.player.getYaw() + random.nextDouble((-80.0D), (80.0D)));
      double spawnX = minecraftClient.player.getX() - Math.sin(spawnAngle) * spawnDistance;
      double spawnZ = minecraftClient.player.getZ() + Math.cos(spawnAngle) * spawnDistance;
      if (this.worldSpawn.isSelected(("Земля"))) {
         BlockPos position = minecraftClient.world
            .getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(spawnX, minecraftClient.player.getY(), spawnZ));
         Vec3d groundPosition = new Vec3d(spawnX + random.nextDouble(), position.getY() + random.nextDouble((0.02D), (0.12D)), spawnZ + random.nextDouble());
         if (WorldProjection.projectNearViewport(groundPosition, 1.0F, 1.0F) != null) {
            this.worldSprites
               .add(
                  new Particles.SpriteParticle(
                     groundPosition,
                     new Vec3d(
                        random.nextDouble((-0.02D), (0.02D)),
                        random.nextDouble((0.02D), (0.05D)),
                        random.nextDouble((-0.02D), (0.02D))
                     ),
                     nowMillis,
                     (2400L),
                     true,
                     random.nextInt(360),
                     this.worldImages.getSelectedCount() == 0 ? ("Светлячок") : this.worldImages.getSelectedOptions().get(random.nextInt(this.worldImages.getSelectedCount())),
                     this.worldSize.getValue() * (0.42F)
                  )
               );
         }
      } else {
         Vec3d skyPosition = new Vec3d(
            spawnX, minecraftClient.player.getY() + random.nextDouble(1.0, Math.max((1.5D), this.worldHeight.getValue())), spawnZ
         );
         if (WorldProjection.projectNearViewport(skyPosition, 1.0F, 1.0F) != null) {
            this.worldSprites
               .add(
                  new Particles.SpriteParticle(
                     skyPosition,
                     Vec3d.ZERO,
                     nowMillis,
                     (5000L),
                     false,
                     random.nextInt(360),
                     this.worldImages.getSelectedCount() == 0 ? ("Светлячок") : this.worldImages.getSelectedOptions().get(random.nextInt(this.worldImages.getSelectedCount())),
                     this.worldSize.getValue() * (0.42F)
                  )
               );
         }
      }
   }

   private int emitCubeWires(BufferBuilder builder, long nowMillis) {
      float baseSize = this.size.getValue() * (0.6F);
      int quadCount = 0;

      for (Particles.ShapeParticle particle : this.cubeParticles) {
         float progress = (float)(nowMillis - particle.createdAt) / (float)particle.lifetimeMillis;
         if (!(progress >= 1.0F)) {
            Vec3d position = particle.linearPosition(progress);
            if (WorldProjection.isSphereVisible(position.x, position.y, position.z, 2.0F)) {
               float halfSize = baseSize * ((0.6F) + progress * (0.6F)) * (0.5F);
               this.shapeRotation.identity().rotateY(particle.rotationY * progress).rotateX(particle.rotationX * progress).rotateZ(particle.rotationZ * progress);

               for (int cornerIndex = 0; cornerIndex < 8; cornerIndex++) {
                  this.shapeRotation
                     .transform(
                        this.shapeCorners[cornerIndex].set((cornerIndex & 1) == 0 ? -halfSize : halfSize, (cornerIndex & 2) == 0 ? -halfSize : halfSize, (cornerIndex & 4) == 0 ? -halfSize : halfSize)
                     );
               }

               int wireColor = ArgbColor.multiplyAlpha(this.particleColor(particle.colorPhaseDegrees), this.shapeAlpha(progress));

               for (int edgeIndex = 0; edgeIndex < CUBE_EDGES.length; edgeIndex += 2) {
                  quadCount += this.emitWireSegment(
                     builder,
                     position.x + this.shapeCorners[CUBE_EDGES[edgeIndex]].x,
                     position.y + this.shapeCorners[CUBE_EDGES[edgeIndex]].y,
                     position.z + this.shapeCorners[CUBE_EDGES[edgeIndex]].z,
                     position.x + this.shapeCorners[CUBE_EDGES[edgeIndex + 1]].x,
                     position.y + this.shapeCorners[CUBE_EDGES[edgeIndex + 1]].y,
                     position.z + this.shapeCorners[CUBE_EDGES[edgeIndex + 1]].z,
                     wireColor
                  );
               }
            }
         }
      }

      return quadCount;
   }

   private synchronized float[] heartModelEdges() {
      if (this.heartLoadAttempted) {
         return this.heartEdges;
      }

      this.heartLoadAttempted = true;
      InputStream input = Particles.class.getResourceAsStream(("/assets/mouseclient/models/particles/heart.gltf"));
      if (input == null) {
         return null;
      }

      try {
         JsonObject json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
         String bufferUri = json.getAsJsonArray(("buffers")).get(0).getAsJsonObject().get(("uri")).getAsString();
         ByteBuffer modelData = ByteBuffer.wrap(Base64.getDecoder().decode(bufferUri.substring(bufferUri.indexOf(44) + 1))).order(ByteOrder.LITTLE_ENDIAN);
         JsonArray jsonArray = json.getAsJsonArray(("bufferViews"));
         int[] viewOffsets = new int[jsonArray.size()];

         for (int viewIndex = 0; viewIndex < jsonArray.size(); viewIndex++) {
            viewOffsets[viewIndex] = jsonArray.get(viewIndex).getAsJsonObject().has(("byteOffset")) ? jsonArray.get(viewIndex).getAsJsonObject().get(("byteOffset")).getAsInt() : 0;
         }

         JsonArray accessors = json.getAsJsonArray(("accessors"));
         int[] accessorViews = new int[accessors.size()];
         int[] componentTypes = new int[accessors.size()];
         int[] accessorCounts = new int[accessors.size()];
         int[] accessorOffsets = new int[accessors.size()];

         for (int accessorIndex = 0; accessorIndex < accessors.size(); accessorIndex++) {
            JsonObject accessor = accessors.get(accessorIndex).getAsJsonObject();
            accessorViews[accessorIndex] = accessor.get(("bufferView")).getAsInt();
            componentTypes[accessorIndex] = accessor.get(("componentType")).getAsInt();
            accessorCounts[accessorIndex] = accessor.get(("count")).getAsInt();
            accessorOffsets[accessorIndex] = accessor.has(("byteOffset")) ? accessor.get(("byteOffset")).getAsInt() : 0;
         }

         JsonArray nodes = json.getAsJsonArray(("nodes"));
         float[][] nodeTranslations = new float[nodes.size()][3];
         boolean[] visitedNodes = new boolean[nodes.size()];
         JsonArray sceneRoots = json.getAsJsonArray(("scenes"))
            .get(json.has(("scene")) ? json.get(("scene")).getAsInt() : 0)
            .getAsJsonObject()
            .getAsJsonArray(("nodes"));

         for (int rootIndex = 0; rootIndex < sceneRoots.size(); rootIndex++) {
            accumulateNodeTranslations(nodes, sceneRoots.get(rootIndex).getAsInt(), 0.0F, 0.0F, 0.0F, nodeTranslations, visitedNodes);
         }

         JsonArray meshes = json.getAsJsonArray(("meshes"));
         int[] positionAccessors = new int[meshes.size()];
         int[] indexAccessors = new int[meshes.size()];

         for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++) {
            JsonObject primitive = meshes.get(meshIndex).getAsJsonObject().getAsJsonArray(("primitives")).get(0).getAsJsonObject();
            positionAccessors[meshIndex] = primitive.getAsJsonObject(("attributes")).get(("POSITION")).getAsInt();
            indexAccessors[meshIndex] = primitive.get(("indices")).getAsInt();
         }

         HashMap<String, Particles.CoordinateModelEdge> edges = new HashMap<>();
         float minX = Float.POSITIVE_INFINITY;
         float minY = Float.POSITIVE_INFINITY;
         float minZ = Float.POSITIVE_INFINITY;
         float maxX = Float.NEGATIVE_INFINITY;
         float maxY = Float.NEGATIVE_INFINITY;
         float maxZ = Float.NEGATIVE_INFINITY;

         for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            JsonObject node = nodes.get(nodeIndex).getAsJsonObject();
            if (node.has(("mesh"))) {
               int nodeMesh = node.get(("mesh")).getAsInt();
               int positionOffset = viewOffsets[accessorViews[positionAccessors[nodeMesh]]] + accessorOffsets[positionAccessors[nodeMesh]];
               float[] positions = new float[accessorCounts[positionAccessors[nodeMesh]] * 3];

               for (int vertexIndex = 0; vertexIndex < accessorCounts[positionAccessors[nodeMesh]]; vertexIndex++) {
                  positions[vertexIndex * 3] = modelData.getFloat(positionOffset + vertexIndex * 12) + nodeTranslations[nodeIndex][0];
                  positions[vertexIndex * 3 + 1] = modelData.getFloat(positionOffset + vertexIndex * 12 + 4) + nodeTranslations[nodeIndex][1];
                  positions[vertexIndex * 3 + 2] = modelData.getFloat(positionOffset + vertexIndex * 12 + 8) + nodeTranslations[nodeIndex][2];
                  minX = Math.min(minX, positions[vertexIndex * 3]);
                  maxX = Math.max(maxX, positions[vertexIndex * 3]);
                  minY = Math.min(minY, positions[vertexIndex * 3 + 1]);
                  maxY = Math.max(maxY, positions[vertexIndex * 3 + 1]);
                  minZ = Math.min(minZ, positions[vertexIndex * 3 + 2]);
                  maxZ = Math.max(maxZ, positions[vertexIndex * 3 + 2]);
               }

               int indexOffset = viewOffsets[accessorViews[indexAccessors[nodeMesh]]] + accessorOffsets[indexAccessors[nodeMesh]];
               int[] indices = new int[accessorCounts[indexAccessors[nodeMesh]]];

               for (int indexIndex = 0; indexIndex < indices.length; indexIndex++) {
                  if (componentTypes[indexAccessors[nodeMesh]] == 5123) {
                     indices[indexIndex] = modelData.getShort(indexOffset + indexIndex * 2) & (65535);
                  } else if (componentTypes[indexAccessors[nodeMesh]] == 5125) {
                     indices[indexIndex] = modelData.getInt(indexOffset + indexIndex * 4);
                  } else {
                     indices[indexIndex] = modelData.get(indexOffset + indexIndex) & 255;
                  }
               }

               for (int triangleIndex = 0; triangleIndex + 2 < indices.length; triangleIndex += 3) {
                  float firstX = positions[indices[triangleIndex] * 3];
                  float firstY = positions[indices[triangleIndex] * 3 + 1];
                  float firstZ = positions[indices[triangleIndex] * 3 + 2];
                  float secondX = positions[indices[triangleIndex + 1] * 3];
                  float secondY = positions[indices[triangleIndex + 1] * 3 + 1];
                  float secondZ = positions[indices[triangleIndex + 1] * 3 + 2];
                  float thirdX = positions[indices[triangleIndex + 2] * 3];
                  float thirdY = positions[indices[triangleIndex + 2] * 3 + 1];
                  float thirdZ = positions[indices[triangleIndex + 2] * 3 + 2];
                  float normalX = (secondY - firstY) * (thirdZ - firstZ) - (secondZ - firstZ) * (thirdY - firstY);
                  float normalY = (secondZ - firstZ) * (thirdX - firstX) - (secondX - firstX) * (thirdZ - firstZ);
                  float normalZ = (secondX - firstX) * (thirdY - firstY) - (secondY - firstY) * (thirdX - firstX);
                  float normalLength = (float)Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
                  if (!(normalLength < (1.0E-8F))) {
                     registerCoordinateEdge(edges, firstX, firstY, firstZ, secondX, secondY, secondZ, normalX / normalLength, normalY / normalLength, normalZ / normalLength);
                     registerCoordinateEdge(edges, secondX, secondY, secondZ, thirdX, thirdY, thirdZ, normalX / normalLength, normalY / normalLength, normalZ / normalLength);
                     registerCoordinateEdge(edges, thirdX, thirdY, thirdZ, firstX, firstY, firstZ, normalX / normalLength, normalY / normalLength, normalZ / normalLength);
                  }
               }
            }
         }

         float centerX = (minX + maxX) * (0.5F);
         float centerY = (minY + maxY) * (0.5F);
         float centerZ = (minZ + maxZ) * (0.5F);
         float normalization = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) > (1.0E-5F)
            ? (1.2F) / Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ))
            : 1.0F;
         ArrayList edgeCoordinates = new ArrayList(edges.size() * 6);

         for (Particles.CoordinateModelEdge edge : edges.values()) {
            if (edge.faceCount == 1 || !edge.coplanar) {
               edgeCoordinates.add((edge.startX - centerX) * normalization);
               edgeCoordinates.add((edge.startY - centerY) * normalization);
               edgeCoordinates.add((edge.startZ - centerZ) * normalization);
               edgeCoordinates.add((edge.endX - centerX) * normalization);
               edgeCoordinates.add((edge.endY - centerY) * normalization);
               edgeCoordinates.add((edge.endZ - centerZ) * normalization);
            }
         }

         this.heartEdges = new float[edgeCoordinates.size()];

         for (int coordinateIndex = 0; coordinateIndex < edgeCoordinates.size(); coordinateIndex++) {
            this.heartEdges[coordinateIndex] = (Float)edgeCoordinates.get(coordinateIndex);
         }
      } catch (Exception error) {
         this.heartEdges = null;
      }

      return this.heartEdges;
   }

   private void drawSprites(ArrayList<Particles.SpriteParticle> particles, MultiSelectSetting imageSetting, float baseSize, MatrixStack matrices, float tickDelta, long nowMillis) {
      if (!particles.isEmpty()) {
         Quaternionf cameraRotation = new Quaternionf(minecraftClient.gameRenderer.getCamera().getRotation()).rotateY((3.1415927F));
         Vector3f scratch = new Vector3f();
         Arrays.fill(this.spriteTypeCounts, 0);
         int unknownTypeCount = 0;
         int countIndex = 0;

         for (int particleCount = particles.size(); countIndex < particleCount; countIndex++) {
            int imageIndex = ((Particles.SpriteParticle)particles.get(countIndex)).imageIndex;
            if (imageIndex >= 0) {
               this.spriteTypeCounts[imageIndex]++;
            } else {
               unknownTypeCount++;
            }
         }

         for (String imageName : imageSetting.getOptions()) {
            int selectedImageIndex = spriteTypeIndex(imageName);
            if (selectedImageIndex >= 0 ? this.spriteTypeCounts[selectedImageIndex] != 0 : unknownTypeCount != 0) {
               BufferBuilder builder = new BufferBuilder(this.geometryAllocator(), DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
               int quadCount = 0;
               int particleIndex = 0;

               for (int renderCount = particles.size(); particleIndex < renderCount; particleIndex++) {
                  Particles.SpriteParticle particle = (Particles.SpriteParticle)particles.get(particleIndex);
                  if (selectedImageIndex >= 0 ? particle.imageIndex == selectedImageIndex : particle.imageName.equals(imageName)) {
                     float opacity = particle.alpha(nowMillis);
                     if (!(opacity <= 0.0F)) {
                        Vec3d position = particle.previousPosition.lerp(particle.position, tickDelta);
                        if (WorldProjection.isSphereVisible(position.x, position.y, position.z, 2.0F)) {
                           this.emitBillboard(
                              builder, cameraRotation, scratch, position.x, position.y, position.z, baseSize / 2.0F, ArgbColor.multiplyAlpha(this.particleColor(particle.colorPhaseDegrees), opacity)
                           );
                           quadCount++;
                        }
                     }
                  }
               }

               this.drawTexturedQuads(this.spriteTexturePath(imageName), matrices, builder, quadCount);
            }
         }
      }
   }

   private float[] loadObjEdges(String resourcePath, float offsetY, boolean normalizeModel) {
      InputStream input = Particles.class.getResourceAsStream(resourcePath);
      if (input == null) {
         return null;
      }

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
         ArrayList vertices = new ArrayList();
         HashMap<Long, Particles.IndexedModelEdge> edges = new HashMap<>();
         float minX = Float.POSITIVE_INFINITY;
         float minY = Float.POSITIVE_INFINITY;
         float minZ = Float.POSITIVE_INFINITY;
         float maxX = Float.NEGATIVE_INFINITY;
         float maxY = Float.NEGATIVE_INFINITY;
         float maxZ = Float.NEGATIVE_INFINITY;

         String line;
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith(("v "))) {
               String[] vertexParts = line.split(("\\s+"));
               Vector3f vertex = new Vector3f(Float.parseFloat(vertexParts[1]), Float.parseFloat(vertexParts[2]) + offsetY, Float.parseFloat(vertexParts[3]));
               vertices.add(vertex);
               minX = Math.min(minX, vertex.x);
               maxX = Math.max(maxX, vertex.x);
               minY = Math.min(minY, vertex.y);
               maxY = Math.max(maxY, vertex.y);
               minZ = Math.min(minZ, vertex.z);
               maxZ = Math.max(maxZ, vertex.z);
            } else if (line.startsWith(("f "))) {
               String[] faceParts = line.split(("\\s+"));

               for (int faceVertexIndex = 1; faceVertexIndex < faceParts.length; faceVertexIndex++) {
                  int startIndex = Integer.parseInt(faceParts[faceVertexIndex].split(("/"))[0]) - 1;
                  int endIndex = Integer.parseInt(faceParts[faceVertexIndex == faceParts.length - 1 ? 1 : faceVertexIndex + 1].split(("/"))[0]) - 1;
                  if (startIndex != endIndex) {
                     String[] indexParts = faceParts[faceVertexIndex].split(("/"));
                     int normalIndex = indexParts.length > 2 && !indexParts[2].isEmpty() ? Integer.parseInt(indexParts[2]) - 1 : -1;
                     long edgeKey = (long)Math.min(startIndex, endIndex) << 32 | Math.max(startIndex, endIndex) & (4294967295L);
                     Particles.IndexedModelEdge edge = (Particles.IndexedModelEdge)edges.get(edgeKey);
                     if (edge == null) {
                        edges.put(edgeKey, new Particles.IndexedModelEdge((Vector3f)vertices.get(startIndex), (Vector3f)vertices.get(endIndex), normalIndex));
                     } else {
                        edge.faceCount++;
                        if (edge.normalIndex != normalIndex) {
                           edge.sameNormal = false;
                        }
                     }
                  }
               }
            }
         }

         float centerX = normalizeModel ? (minX + maxX) * (0.5F) : 0.0F;
         float centerY = normalizeModel ? (minY + maxY) * (0.5F) : 0.0F;
         float centerZ = normalizeModel ? (minZ + maxZ) * (0.5F) : 0.0F;
         float normalization = normalizeModel && Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) > (1.0E-5F)
            ? 1.0F / Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ))
            : 1.0F;
         ArrayList edgeCoordinates = new ArrayList();

         for (Particles.IndexedModelEdge outputEdge : edges.values()) {
            if (outputEdge.faceCount == 1 || !outputEdge.sameNormal) {
               edgeCoordinates.add((outputEdge.startX - centerX) * normalization);
               edgeCoordinates.add((outputEdge.startY - centerY) * normalization);
               edgeCoordinates.add((outputEdge.startZ - centerZ) * normalization);
               edgeCoordinates.add((outputEdge.endX - centerX) * normalization);
               edgeCoordinates.add((outputEdge.endY - centerY) * normalization);
               edgeCoordinates.add((outputEdge.endZ - centerZ) * normalization);
            }
         }

         float[] coordinates = new float[edgeCoordinates.size()];

         for (int coordinateIndex = 0; coordinateIndex < edgeCoordinates.size(); coordinateIndex++) {
            coordinates[coordinateIndex] = (Float)edgeCoordinates.get(coordinateIndex);
         }

         return coordinates;
      } catch (Exception error) {
         return null;
      }
   }

   private String spriteTexturePath(String imageName) {
      return switch (imageName) {
         case ("Звезда") -> ("textures/particles/star.png");
         case ("Сердце") -> ("textures/particles/heart.png");
         case ("Снежинка") -> ("textures/particles/snowflake.png");
         case ("Доллар") -> ("textures/particles/dollar.png");
         default -> ("textures/particles/firefly.png");
      };
   }

   private int particleColor(int phaseDegrees) {
      return this.useCustomColor.getValue()
         ? this.color.getArgb()
         : (-16777216) | MouseClientClientPalette.sampleColor((float)(System.currentTimeMillis() % (3600L)) / (3600.0F) + phaseDegrees / (360.0F));
   }

   private void drainHitRequests(long nowMillis) {
      Particles.HitParticleRequest request;
      while ((request = this.hitRequests.poll()) != null) {
         if (this.hitParticles.getValue() && this.images.getSelectedCount() != 0) {
            for (int particleIndex = 0; particleIndex < Math.max(1, (int)this.hitParticleCount.getValue()); particleIndex++) {
               ThreadLocalRandom random = ThreadLocalRandom.current();
               if (this.hitSprites.size() < 200) {
                  this.hitSprites
                     .add(
                        new Particles.SpriteParticle(
                           new Vec3d(request.feetPos().x, request.feetPos().y + random.nextDouble() * request.heightBlocks(), request.feetPos().z),
                           new Vec3d(
                              random.nextDouble((-2.0D), (2.0D)) * (0.08D),
                              random.nextDouble((0.05D), (0.2D)) * (0.08D),
                              random.nextDouble((-2.0D), (2.0D)) * (0.08D)
                           ),
                           nowMillis,
                           (2200L),
                           false,
                           random.nextInt(360),
                           this.images.getSelectedOptions().get(random.nextInt(this.images.getSelectedCount())),
                           this.hitSize.getValue() * (0.42F)
                        )
                     );
               }
            }
         }
      }
   }

   private static final class ShapeParticle {
      public final Vec3d origin;
      public final double phase;
      public final long createdAt;
      public final long lifetimeMillis;
      public final int colorPhaseDegrees;
      public final float rotationX;
      public final float rotationY;
      public final float rotationZ;
      public final double travelX;
      public final double travelY;
      public final double travelZ;

      public Vec3d linearPosition(float progress) {
         return new Vec3d(this.origin.x + this.travelX * progress, this.origin.y + this.travelY * progress, this.origin.z + this.travelZ * progress);
      }

      public Vec3d totemPosition(float progress) {
         return new Vec3d(
            this.origin.x + MathHelper.sin((float)this.phase * (7.0F) + progress * (4.0F)) * (0.6F),
            this.origin.y + MathHelper.sin((float)this.phase * (5.0F) + progress * (3.0F)) * (0.4F) + progress * (0.6F),
            this.origin.z + MathHelper.cos((float)this.phase * (9.0F) + progress * (5.0F)) * (0.6F)
         );
      }

      public ShapeParticle(Vec3d origin, double phase, long createdAt, long lifetimeMillis, int colorPhaseDegrees, float rotationX, float rotationY, float rotationZ, double travelX, double travelY, double travelZ) {
         this.origin = origin;
         this.phase = phase;
         this.createdAt = createdAt;
         this.lifetimeMillis = lifetimeMillis;
         this.colorPhaseDegrees = colorPhaseDegrees;
         this.rotationX = rotationX;
         this.rotationY = rotationY;
         this.rotationZ = rotationZ;
         this.travelX = travelX;
         this.travelY = travelY;
         this.travelZ = travelZ;
      }
   }

   private static final class SpriteParticle {
      public Vec3d position;
      public Vec3d previousPosition;
      public Vec3d velocity;
      public final long createdAt;
      public final long lifetimeMillis;
      public final boolean groundMode;
      public final int colorPhaseDegrees;
      public final String imageName;
      public final int imageIndex;
      public final float collisionRadius;

      public SpriteParticle(Vec3d position, Vec3d velocity, long createdAt, long lifetimeMillis, boolean groundMode, int colorPhaseDegrees, String imageName, float collisionRadius) {
         this.position = position;
         this.previousPosition = position;
         this.velocity = velocity;
         this.createdAt = createdAt;
         this.lifetimeMillis = lifetimeMillis;
         this.groundMode = groundMode;
         this.colorPhaseDegrees = colorPhaseDegrees;
         this.imageName = imageName;
         this.imageIndex = Particles.spriteTypeIndex(imageName);
         this.collisionRadius = collisionRadius;
      }

      public float lifeProgress(long nowMillis) {
         return MathHelper.clamp((float)(nowMillis - this.createdAt) / (float)this.lifetimeMillis, 0.0F, 1.0F);
      }

      public void tick() {
         this.previousPosition = this.position;
         if (this.groundMode) {
            this.velocity = this.velocity.add(0.0, (-0.0045D), 0.0);
         }

         double nextX = this.position.x + this.velocity.x;
         double nextY = this.position.y + this.velocity.y;
         double nextZ = this.position.z + this.velocity.z;
         if (!MinecraftAccess.minecraftClient
            .world
            .getBlockState(BlockPos.ofFloored(this.position.x, this.position.y, nextZ))
            .getCollisionShape(MinecraftAccess.minecraftClient.world, BlockPos.ofFloored(this.position.x, this.position.y, nextZ))
            .isEmpty()) {
            this.velocity = new Vec3d(this.velocity.x, this.velocity.y, this.velocity.z * (-0.8D));
         }

         if (!MinecraftAccess.minecraftClient
            .world
            .getBlockState(BlockPos.ofFloored(this.position.x, nextY, this.position.z))
            .getCollisionShape(MinecraftAccess.minecraftClient.world, BlockPos.ofFloored(this.position.x, nextY, this.position.z))
            .isEmpty()) {
            if (this.groundMode && this.velocity.y < 0.0) {
               this.velocity = this.velocity
                  .lerp(
                     new Vec3d(this.velocity.x * (0.82D), ThreadLocalRandom.current().nextDouble((0.012D), (0.028D)), this.velocity.z * (0.82D)),
                     (0.42D)
                  );
            } else {
               this.velocity = new Vec3d(this.velocity.x * (0.999D), this.velocity.y * (-0.7D), this.velocity.z * (0.999D));
            }
         }

         if (!MinecraftAccess.minecraftClient
            .world
            .getBlockState(BlockPos.ofFloored(nextX, this.position.y, this.position.z))
            .getCollisionShape(MinecraftAccess.minecraftClient.world, BlockPos.ofFloored(nextX, this.position.y, this.position.z))
            .isEmpty()) {
            this.velocity = new Vec3d(this.velocity.x * (-0.8D), this.velocity.y, this.velocity.z);
         }

         if (this.groundMode) {
            this.velocity = this.velocity.multiply((0.996D));
         } else {
            this.velocity = new Vec3d(
               MathHelper.clamp(this.velocity.x + (ThreadLocalRandom.current().nextDouble() - (0.5D)) * (0.005D), (-0.5D), (0.5D)),
               MathHelper.clamp(this.velocity.y + (ThreadLocalRandom.current().nextDouble() - (0.5D)) * (0.005D), (-0.5D), (0.5D)),
               MathHelper.clamp(this.velocity.z + (ThreadLocalRandom.current().nextDouble() - (0.5D)) * (0.005D), (-0.5D), (0.5D))
            );
         }

         this.position = this.position.add(this.velocity);
         if (!this.groundMode) {
            this.velocity = new Vec3d(this.velocity.x / (0.999999D), this.velocity.y - (5.0E-5D), this.velocity.z / (0.999999D));
         }
      }

      public float alpha(long nowMillis) {
         long ageMillis = nowMillis - this.createdAt;
         if (ageMillis >= this.lifetimeMillis) {
            return 0.0F;
         } else if (ageMillis < (250L)) {
            return (float)ageMillis / (250.0F);
         } else {
            return ageMillis >= this.lifetimeMillis - (250L) ? (float)(this.lifetimeMillis - ageMillis) / (250.0F) : 1.0F;
         }
      }

      public boolean expired(long nowMillis) {
         return nowMillis - this.createdAt >= this.lifetimeMillis;
      }
   }

   private static final class CoordinateModelEdge {
      public final float startX;
      public final float startY;
      public final float startZ;
      public final float endX;
      public final float endY;
      public final float endZ;
      public final float normalX;
      public final float normalY;
      public final float normalZ;
      public int faceCount = 1;
      public boolean coplanar = true;

      public CoordinateModelEdge(float startX, float startY, float startZ, float endX, float endY, float endZ, float normalX, float normalY, float normalZ) {
         this.startX = startX;
         this.startY = startY;
         this.startZ = startZ;
         this.endX = endX;
         this.endY = endY;
         this.endZ = endZ;
         this.normalX = normalX;
         this.normalY = normalY;
         this.normalZ = normalZ;
      }
   }

   private static final class IndexedModelEdge {
      public final float startX;
      public final float startY;
      public final float startZ;
      public final float endX;
      public final float endY;
      public final float endZ;
      public final int normalIndex;
      public int faceCount = 1;
      public boolean sameNormal = true;

      public IndexedModelEdge(Vector3f start, Vector3f end, int normalIndex) {
         this.startX = start.x;
         this.startY = start.y;
         this.startZ = start.z;
         this.endX = end.x;
         this.endY = end.y;
         this.endZ = end.z;
         this.normalIndex = normalIndex;
      }
   }

   private record HitParticleRequest(Vec3d feetPos, float heightBlocks) {
   }
}
