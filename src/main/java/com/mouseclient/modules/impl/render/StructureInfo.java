package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.GuiShapeRenderer;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.HudStyle;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.util.entity.WorldProjection;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.ScalarAnimation;
import com.mouseclient.util.time.ExtendedTargetHud;
import com.mouseclient.util.time.GuiFont;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

@ModuleInfo(name = "StructureInfo", description = "Определяет трапки и пласты.", category = ModuleCategory.RENDER)
public final class StructureInfo extends Module {
   private static StructureInfo INSTANCE;
   public ModeSetting server;
   private static final HashSet<Block> STRUCTURE_BLOCKS = new HashSet<>();
   private static final ItemStack TRAP_ICON;
   private static final ItemStack PLATFORM_ICON;
   private static final ItemStack STUN_ICON;
   private final ConcurrentHashMap<BlockPos, StructureInfo.DetectedStructure> detectedStructures = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<BlockPos, Block> pendingBlocks = new ConcurrentHashMap<>();
   private final HashSet<UUID> ownedBossBars = new HashSet<>();
   private ClientWorld observedWorld;
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isIncoming() && minecraftClient.world != null && minecraftClient.player != null) {
         if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket chunkUpdate) {
            if (this.server.isSelected(("FunTime/SpookyTime"))) {
               minecraftClient.execute(() -> chunkUpdate.visitUpdates((position, blockState) -> {
                  if (!blockState.isAir() && STRUCTURE_BLOCKS.contains(blockState.getBlock())) {
                     this.pendingBlocks.put(position.toImmutable(), blockState.getBlock());
                  }
               }));
            }
         } else if (event.getPacket() instanceof BlockUpdateS2CPacket blockUpdate) {
            if (this.server.isSelected(("FunTime/SpookyTime"))) {
               if (!blockUpdate.getState().isAir() && STRUCTURE_BLOCKS.contains(blockUpdate.getState().getBlock())) {
                  minecraftClient.execute(() -> this.pendingBlocks.put(blockUpdate.getPos().toImmutable(), blockUpdate.getState().getBlock()));
               }
            }
         } else if (event.getPacket() instanceof PlaySoundS2CPacket soundPacket) {
            if (this.server.isSelected(("HolyWorld"))) {
               minecraftClient.execute(() -> {
                  if (minecraftClient.world != null && this.isEnabled()) {
                     BlockPos soundPosition = new BlockPos((int)soundPacket.getX(), (int)soundPacket.getY(), (int)soundPacket.getZ());
                     String soundId = soundPacket.getSound().value().id().toString();
                     if (this.server.isSelected(("HolyWorld")) && soundId.contains(("block.beacon.deactivate"))) {
                        this.detectedStructures.put(soundPosition, new StructureInfo.DetectedStructure(soundPosition, ("Стан"), STUN_ICON, (15000L), null, true));
                     }
                  }
               });
            }
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      this.detectedStructures.entrySet().removeIf(expirationEntry -> System.currentTimeMillis() - expirationEntry.getValue().createdAtMillis > expirationEntry.getValue().durationMillis);
      if (minecraftClient.player != null && minecraftClient.world != null) {
         if (minecraftClient.world != this.observedWorld) {
            this.observedWorld = minecraftClient.world;
            this.pendingBlocks.clear();
            this.detectedStructures.clear();
            this.clearBossBars();
         }

         java.util.Map<java.util.UUID,net.minecraft.client.gui.hud.ClientBossBar> bossBars = ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents();
         HashSet activeBossBars = new HashSet();

         for (StructureInfo.DetectedStructure structure : this.detectedStructures.values()) {
            if (structure.box != null && this.containsLocalPlayer(structure.box)) {
               long remainingMillis = Math.max(0L, structure.durationMillis - (System.currentTimeMillis() - structure.createdAtMillis));
               MutableText bossBarText = Text.literal(structure.name + " " + (remainingMillis >= (60000L) ? remainingMillis / (60000L) + "мин" : (remainingMillis + (999L)) / (1000L) + "с"))
                  .setStyle(Style.EMPTY.withColor((16777215)).withBold(false));
               ClientBossBar bossBar = (ClientBossBar)bossBars.get(structure.bossBarId);
               if (bossBar == null) {
                  bossBar = new ClientBossBar(
                     structure.bossBarId, bossBarText, (float)remainingMillis / (float)structure.durationMillis, Color.PURPLE, net.minecraft.entity.boss.BossBar.Style.PROGRESS, false, false, false
                  );
                  bossBars.put(structure.bossBarId, bossBar);
               }

               bossBar.setName(bossBarText);
               bossBar.setPercent((float)remainingMillis / (float)structure.durationMillis);
               activeBossBars.add(structure.bossBarId);
               this.ownedBossBars.add(structure.bossBarId);
            }
         }

         this.ownedBossBars.removeIf(bossBarId -> {
            if (activeBossBars.contains(bossBarId)) {
               return false;
            }

            bossBars.remove(bossBarId);
            return true;
         });
         if (!this.pendingBlocks.isEmpty()) {
            if (this.pendingBlocks.size() >= 2) {
               java.util.HashSet<net.minecraft.block.Block> observedBlocks = new HashSet<>(this.pendingBlocks.values());
               StructureInfo.TrapType trapType = null;

               for (StructureInfo.TrapType candidateTrap : StructureInfo.TrapType.values()) {
                  if (candidateTrap.blocks.equals(observedBlocks)) {
                     trapType = candidateTrap;
                     break;
                  }
               }

               StructureInfo.PlatformType platformType = null;
               StructureInfo.PlatformType[] platformTypes = StructureInfo.PlatformType.values();
               int platformTypeCount = platformTypes.length;
               int platformBlockCount = 0;

               label115: {
                  StructureInfo.PlatformType candidatePlatform;
                  while (true) {
                     if (platformBlockCount >= platformTypeCount) {
                        break label115;
                     }

                     candidatePlatform = platformTypes[platformBlockCount];
                     if (candidatePlatform.blocks.equals(observedBlocks)) {
                        break;
                     }

                     if (!candidatePlatform.optionalBlocks.isEmpty()) {
                        java.util.HashSet<net.minecraft.block.Block> combinedBlocks = new HashSet<>(candidatePlatform.blocks);
                        combinedBlocks.addAll(candidatePlatform.optionalBlocks);
                        if (combinedBlocks.equals(observedBlocks)) {
                           break;
                        }
                     }

                     platformBlockCount++;
                  }

                  platformType = candidatePlatform;
               }

               if (trapType != null || platformType != null) {
                  int minX = (2147483647);
                  platformTypeCount = (2147483647);
                  platformBlockCount = (2147483647);
                  int maxX = (-2147483648);
                  int maxY = (-2147483648);
                  int maxZ = (-2147483648);

                  for (BlockPos position : this.pendingBlocks.keySet()) {
                     minX = Math.min(minX, position.getX());
                     platformTypeCount = Math.min(platformTypeCount, position.getY());
                     platformBlockCount = Math.min(platformBlockCount, position.getZ());
                     maxX = Math.max(maxX, position.getX() + 1);
                     maxY = Math.max(maxY, position.getY() + 1);
                     maxZ = Math.max(maxZ, position.getZ() + 1);
                  }

                  Box bounds = new Box(minX, platformTypeCount, platformBlockCount, maxX, maxY, maxZ);
                  BlockPos structurePosition = BlockPos.ofFloored(bounds.getCenter());
                  boolean spookyServer = minecraftClient.getCurrentServerEntry() != null
                     && minecraftClient.getCurrentServerEntry().address.toLowerCase(Locale.ROOT).contains(("spooky"));
                  ExtendedTargetHud.recordStructureItemUse(
                     trapType != null, trapType != null ? trapType == StructureInfo.TrapType.DRAGON : platformType == StructureInfo.PlatformType.DRAGON
                  );
                  if (trapType != null) {
                     this.detectedStructures.put(structurePosition, new StructureInfo.DetectedStructure(structurePosition, trapType.displayName, TRAP_ICON, trapType.durationMillis, bounds, false));
                  } else {
                     this.detectedStructures
                        .put(
                           structurePosition,
                           new StructureInfo.DetectedStructure(
                              structurePosition,
                              platformType.displayName,
                              PLATFORM_ICON,
                              platformType == StructureInfo.PlatformType.DRAGON && spookyServer
                                 ? (20000L)
                                 : (bounds.getLengthY() <= (2.0D) ? (60000L) : (20000L)),
                              bounds,
                              false
                           )
                        );
                  }
               }
            }

            this.pendingBlocks.clear();
         }
      } else {
         this.pendingBlocks.clear();
      }
   };
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && this.server.isSelected(("HolyWorld"))) {
         for (StructureInfo.DetectedStructure structure : this.detectedStructures.values()) {
            if (structure.areaHighlight
               && !(minecraftClient.player.getEntityPos().distanceTo(Vec3d.ofCenter(structure.position)) > (100.0D))
               && WorldProjection.project(Vec3d.ofCenter(structure.position), 1.0F, 1.0F) != null) {
               Box bounds = new Box(structure.position.up()).contract(0.0, (0.2D), 0.0).expand((1.99D));
               event.getMatrices().push();
               event.getMatrices()
                  .translate(
                     bounds.minX - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
                     bounds.minY - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
                     bounds.minZ - minecraftClient.gameRenderer.getCamera().getCameraPos().z
                  );
               Matrix4f positionMatrix = event.getMatrices().peek().getPositionMatrix();
               VertexConsumer vertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.debugQuads());
               float[] xBounds = new float[]{0.0F, (float)(bounds.maxX - bounds.minX)};
               float[] yBounds = new float[]{0.0F, (float)(bounds.maxY - bounds.minY)};
               float[] zBounds = new float[]{0.0F, (float)(bounds.maxZ - bounds.minZ)};
               int[][] faces = new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

               for (int[] face : faces) {
                  for (int vertexIndex : face) {
                     vertices.vertex(positionMatrix, xBounds[vertexIndex >> 2], yBounds[vertexIndex >> 1 & 1], zBounds[vertexIndex & 1])
                        .color(ArgbColor.multiplyAlpha(HudStyle.accentColor, (0.3529412F)));
                  }
               }

               VertexRendering.drawOutline(
                  event.getMatrices(),
                  minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines()),
                  VoxelShapes.cuboid(new Box(0.0, 0.0, 0.0, bounds.maxX - bounds.minX, bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ)),
                  0.0,
                  0.0,
                  0.0,
                  ArgbColor.multiplyAlpha(HudStyle.accentColor, (0.55F)),
                  1.0F
               );
               minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
               event.getMatrices().pop();
            }
         }
      }
   };

   @Override
   protected void onEnable() {
      this.detectedStructures.clear();
      this.pendingBlocks.clear();
      this.clearBossBars();
   }

   @Override
   protected void onDisable() {
      this.detectedStructures.clear();
      this.pendingBlocks.clear();
      this.clearBossBars();
   }

   public void renderLabels(HudRenderEvent event) {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         for (StructureInfo.DetectedStructure structure : this.detectedStructures.values()) {
            if (!(minecraftClient.player.getEntityPos().distanceTo(structure.labelPosition) > (100.0D)) && !(this.lifetimeOpacity(structure) < (0.01F))) {
               structure.labelVisibility.setTarget(structure.box != null && this.containsLocalPlayer(structure.box) ? 0.0 : 1.0);
               if (!(this.lifetimeOpacity(structure) * structure.labelVisibility.value() < (0.009999999776482582D))) {
                  Vec3d position = WorldProjection.projectNearViewport(structure.labelPosition, event.getWidth(), event.getHeight());
                  if (position != null) {
                     float distance = (float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(structure.labelPosition);
                     HudRenderContext.pushScale(
                        (float)position.x * GuiScale.getWindowScale() / GuiScale.getBaseScale(),
                        (float)position.y * GuiScale.getWindowScale() / GuiScale.getBaseScale(),
                        Math.max((0.3F), 1.0F - distance * (0.008F))
                           * Math.max((0.7F), 1.0F - distance * (0.005F))
                           * ((0.85F) + (0.15F) * this.lifetimeOpacity(structure) * (float)structure.labelVisibility.value())
                     );
                     this.drawLabel(
                        structure,
                        (float)position.x * GuiScale.getWindowScale() / GuiScale.getBaseScale(),
                        (float)position.y * GuiScale.getWindowScale() / GuiScale.getBaseScale()
                     );
                     HudRenderContext.popTransform();
                  }
               }
            }
         }
      }
   }

   private StructureInfo() {
      this.server = this.addSetting(new ModeSetting(("Сервер"), ("На каком сервере определять структуры."), ("FunTime/SpookyTime"), ("FunTime/SpookyTime"), ("HolyWorld")));
   }

   private void drawLabel(StructureInfo.DetectedStructure structure, float x, float y) {
      long remainingMillis = Math.max(0L, structure.durationMillis - (System.currentTimeMillis() - structure.createdAtMillis));
      String durationText = remainingMillis >= (60000L) ? remainingMillis / (60000L) + "мин" : (remainingMillis + (999L)) / (1000L) + "с";
      float alpha = this.lifetimeOpacity(structure) * (float)structure.labelVisibility.value();
      float height = (13.0F);
      float nameWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, structure.name, (6.0F));
      float durationWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, durationText, (6.0F));
      float width = (16.0F) + nameWidth + (3.0F) + durationWidth + (4.0F);
      float panelX = Math.round((x - width / 2.0F) * 2.0F) / 2.0F;
      float panelY = Math.round((y - height / 2.0F) * 2.0F) / 2.0F;
      HudPanelRenderer.drawGlassPanel(panelX, panelY, width, height, alpha, (4.5F), GuiShapeRenderer.BlurQuality.LOW);
      GuiItemRenderer.render(structure.icon, panelX + (4.0F), panelY + (2.5F), (8.0F), ItemRenderOptions.noDecorations(alpha));
      float textY = panelY + height / 2.0F - (3.5F);
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, structure.name, panelX + (16.0F), textY, (6.0F), HudPanelRenderer.primaryTextColor(alpha));
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, durationText, panelX + (16.0F) + nameWidth + (3.0F), textY, (6.0F), HudPanelRenderer.secondaryTextColor(alpha));
   }

   private boolean containsLocalPlayer(Box bounds) {
      return minecraftClient.player.getX() >= bounds.minX
         && minecraftClient.player.getX() < bounds.maxX
         && minecraftClient.player.getZ() >= bounds.minZ
         && minecraftClient.player.getZ() < bounds.maxZ
         && minecraftClient.player.getY() >= bounds.minY - 1.0
         && minecraftClient.player.getY() < bounds.maxY + 1.0;
   }

   private float lifetimeOpacity(StructureInfo.DetectedStructure structure) {
      float progress = Math.min(
         Math.min(1.0F, (float)(System.currentTimeMillis() - structure.createdAtMillis) / (220.0F)),
         Math.min(1.0F, (float)(structure.durationMillis - (System.currentTimeMillis() - structure.createdAtMillis)) / (220.0F))
      );
      return progress * progress * ((3.0F) - 2.0F * progress);
   }

   static {
      for (StructureInfo.TrapType var3 : StructureInfo.TrapType.values()) {
         STRUCTURE_BLOCKS.addAll(var3.blocks);
      }

      for (StructureInfo.PlatformType var7 : StructureInfo.PlatformType.values()) {
         STRUCTURE_BLOCKS.addAll(var7.blocks);
         STRUCTURE_BLOCKS.addAll(var7.optionalBlocks);
      }

      TRAP_ICON = Items.NETHERITE_SCRAP.getDefaultStack();
      PLATFORM_ICON = Items.DRIED_KELP.getDefaultStack();
      STUN_ICON = Items.NETHER_STAR.getDefaultStack();
   }

   public static StructureInfo getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new StructureInfo();
      }

      return INSTANCE;
   }

   private void clearBossBars() {
      if (minecraftClient.inGameHud != null) {
         java.util.Map<java.util.UUID,net.minecraft.client.gui.hud.ClientBossBar> bossBars = ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents();

         for (UUID bossBarId : this.ownedBossBars) {
            bossBars.remove(bossBarId);
         }

         this.ownedBossBars.clear();
      }
   }

   private enum PlatformType {
      DEFAULT("Обычный", Set.of(Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.DEAD_TUBE_CORAL_BLOCK), Set.of()),
      DRAGON(
         "Драконий",
         Set.of(
            Blocks.CHISELED_POLISHED_BLACKSTONE,
            Blocks.LODESTONE,
            Blocks.POLISHED_BASALT,
            Blocks.POLISHED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.SMOOTH_STONE
         ),
         Set.of(Blocks.NETHERITE_BLOCK)
      ),
      PEARL("Жемчужный", Set.of(Blocks.DARK_PRISMARINE, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS), Set.of()),
      MYSTIC("Мистический", Set.of(Blocks.END_STONE_BRICKS, Blocks.PURPLE_STAINED_GLASS, Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR), Set.of()),
      GHOST("Призрачный", Set.of(Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR, Blocks.SMOOTH_QUARTZ, Blocks.WHITE_STAINED_GLASS), Set.of()),
      NIGHTMARISH("Кошмарный", Set.of(Blocks.BLACKSTONE, Blocks.BONE_BLOCK, Blocks.POLISHED_BASALT), Set.of()),
      SPOOKY(
         "Зловещий",
         Set.of(Blocks.INFESTED_CRACKED_STONE_BRICKS, Blocks.INFESTED_MOSSY_STONE_BRICKS, Blocks.INFESTED_STONE_BRICKS, Blocks.LODESTONE),
         Set.of(Blocks.INFESTED_CHISELED_STONE_BRICKS)
      ),
      LETHAL("Смертельный", Set.of(Blocks.CHISELED_NETHER_BRICKS, Blocks.MAGMA_BLOCK, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS), Set.of()),
      CRUEL(
         "Жестокий",
         Set.of(Blocks.BLACKSTONE, Blocks.CRYING_OBSIDIAN, Blocks.GILDED_BLACKSTONE, Blocks.NETHERITE_BLOCK, Blocks.POLISHED_BLACKSTONE_BRICKS),
         Set.of()
      ),
      ICY("Ледяной", Set.of(Blocks.ICE, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK), Set.of()),
      INSIDIOUS("Коварный", Set.of(Blocks.CHISELED_NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.NETHER_WART_BLOCK, Blocks.RED_NETHER_BRICKS), Set.of()),
      CURSED(
         "Проклятый",
         Set.of(Blocks.BLACKSTONE, Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.CRYING_OBSIDIAN, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.RESPAWN_ANCHOR),
         Set.of()
      ),
      EXCRUCIATING("Мучительный", Set.of(Blocks.BLACKSTONE, Blocks.COAL_BLOCK, Blocks.NETHERITE_BLOCK, Blocks.POLISHED_BLACKSTONE_BRICKS), Set.of()),
      ROYAL(
         "Царский", Set.of(Blocks.DIORITE, Blocks.LIGHT_GRAY_STAINED_GLASS, Blocks.NETHERITE_BLOCK, Blocks.POLISHED_DIORITE), Set.of(Blocks.GILDED_BLACKSTONE)
      ),
      SINGULAR("Сингулярный", Set.of(Blocks.BLACK_CONCRETE, Blocks.COAL_BLOCK), Set.of()),
      UNVOIDABLE("Неизбежный", Set.of(Blocks.ANDESITE, Blocks.DEAD_TUBE_CORAL_BLOCK, Blocks.POLISHED_BASALT), Set.of());

      final String displayName;
      final Set<Block> blocks;
      final Set<Block> optionalBlocks;

      PlatformType(String name, Set<Block> blocks, Set<Block> optionalBlocks) {
         this.displayName = name + " пласт";
         this.blocks = blocks;
         this.optionalBlocks = optionalBlocks;
      }
   }

   private static final class DetectedStructure {
      public final BlockPos position;
      public final Vec3d labelPosition;
      public final String name;
      public final ItemStack icon;
      public final long createdAtMillis;
      public final long durationMillis;
      public final Box box;
      public final boolean areaHighlight;
      public final UUID bossBarId;
      public final ScalarAnimation labelVisibility;

      public DetectedStructure(BlockPos position, String name, ItemStack icon, long durationMillis, Box box, boolean areaHighlight) {
         this.labelVisibility = new ScalarAnimation((220L)).reset(1.0);
         this.position = position.toImmutable();
         this.labelPosition = box == null ? Vec3d.ofCenter(position) : box.getCenter();
         this.name = name;
         this.icon = icon;
         this.createdAtMillis = System.currentTimeMillis();
         this.durationMillis = durationMillis;
         this.box = box;
         this.areaHighlight = areaHighlight;
         this.bossBarId = UUID.randomUUID();
      }
   }

   private enum TrapType {
      DEFAULT("Обычная", 15000L, Set.of(Blocks.ANDESITE, Blocks.ANDESITE_STAIRS, Blocks.COBBLESTONE, Blocks.DEAD_TUBE_CORAL_BLOCK)),
      DRAGON(
         "Драконья",
         30000L,
         Set.of(
            Blocks.CHISELED_POLISHED_BLACKSTONE,
            Blocks.LODESTONE,
            Blocks.NETHERITE_BLOCK,
            Blocks.POLISHED_BASALT,
            Blocks.POLISHED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
            Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
            Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.RESPAWN_ANCHOR,
            Blocks.SMOOTH_STONE_SLAB
         )
      ),
      PEARL(
         "Жемчужная",
         15000L,
         Set.of(
            Blocks.DARK_PRISMARINE, Blocks.DARK_PRISMARINE_SLAB, Blocks.DARK_PRISMARINE_STAIRS, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN
         )
      ),
      MYSTIC(
         "Мистическая",
         15000L,
         Set.of(
            Blocks.END_STONE,
            Blocks.END_STONE_BRICK_WALL,
            Blocks.END_STONE_BRICKS,
            Blocks.PURPLE_STAINED_GLASS,
            Blocks.PURPUR_BLOCK,
            Blocks.PURPUR_PILLAR,
            Blocks.PURPUR_STAIRS
         )
      ),
      GHOST(
         "Призрачная",
         15000L,
         Set.of(
            Blocks.CHISELED_QUARTZ_BLOCK,
            Blocks.DIORITE,
            Blocks.QUARTZ_PILLAR,
            Blocks.QUARTZ_STAIRS,
            Blocks.SEA_LANTERN,
            Blocks.SMOOTH_QUARTZ_SLAB,
            Blocks.WHITE_STAINED_GLASS
         )
      ),
      NIGHTMARISH(
         "Кошмарная", 15000L, Set.of(Blocks.BLACKSTONE, Blocks.BLACKSTONE_SLAB, Blocks.BONE_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.POLISHED_BASALT)
      ),
      SPOOKY(
         "Зловещая",
         15000L,
         Set.of(
            Blocks.INFESTED_CHISELED_STONE_BRICKS,
            Blocks.INFESTED_CRACKED_STONE_BRICKS,
            Blocks.INFESTED_MOSSY_STONE_BRICKS,
            Blocks.INFESTED_STONE_BRICKS,
            Blocks.LODESTONE,
            Blocks.STONE_BRICK_SLAB,
            Blocks.STONE_BRICK_STAIRS,
            Blocks.STONE_BRICK_WALL
         )
      ),
      LETHAL(
         "Смертельная",
         15000L,
         Set.of(
            Blocks.CHISELED_NETHER_BRICKS,
            Blocks.MAGMA_BLOCK,
            Blocks.NETHER_BRICKS,
            Blocks.RED_NETHER_BRICK_STAIRS,
            Blocks.RED_NETHER_BRICK_WALL,
            Blocks.RED_NETHER_BRICKS
         )
      ),
      CRUEL(
         "Жестокая",
         15000L,
         Set.of(
            Blocks.BLACKSTONE,
            Blocks.BLACKSTONE_SLAB,
            Blocks.CRYING_OBSIDIAN,
            Blocks.GILDED_BLACKSTONE,
            Blocks.NETHERITE_BLOCK,
            Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
            Blocks.POLISHED_BLACKSTONE_BRICKS
         )
      ),
      ICY("Ледяная", 15000L, Set.of(Blocks.ICE, Blocks.PACKED_ICE, Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.WHITE_CONCRETE)),
      INSIDIOUS(
         "Коварная",
         15000L,
         Set.of(
            Blocks.CHISELED_NETHER_BRICKS,
            Blocks.NETHER_BRICK_SLAB,
            Blocks.NETHER_BRICK_STAIRS,
            Blocks.NETHER_BRICKS,
            Blocks.NETHER_WART_BLOCK,
            Blocks.RED_NETHER_BRICK_STAIRS,
            Blocks.RED_NETHER_BRICKS
         )
      ),
      CURSED(
         "Проклятая",
         15000L,
         Set.of(
            Blocks.BLACKSTONE,
            Blocks.BLACKSTONE_SLAB,
            Blocks.BLACKSTONE_STAIRS,
            Blocks.CHISELED_POLISHED_BLACKSTONE,
            Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
            Blocks.CRYING_OBSIDIAN,
            Blocks.NETHERITE_BLOCK,
            Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
            Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.RESPAWN_ANCHOR
         )
      ),
      EXCRUCIATING(
         "Мучительная",
         15000L,
         Set.of(
            Blocks.BLACK_CONCRETE,
            Blocks.BLACKSTONE,
            Blocks.BLACKSTONE_STAIRS,
            Blocks.COAL_BLOCK,
            Blocks.NETHERITE_BLOCK,
            Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
            Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
            Blocks.POLISHED_BLACKSTONE_BRICKS,
            Blocks.POLISHED_BLACKSTONE_WALL
         )
      ),
      ROYAL(
         "Царская",
         15000L,
         Set.of(
            Blocks.DIORITE,
            Blocks.DIORITE_STAIRS,
            Blocks.DIORITE_WALL,
            Blocks.GILDED_BLACKSTONE,
            Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.NETHERITE_BLOCK,
            Blocks.POLISHED_DIORITE,
            Blocks.POLISHED_DIORITE_SLAB
         )
      ),
      SINGULAR("Сингулярная", 15000L, Set.of(Blocks.BLACK_CONCRETE, Blocks.BLACKSTONE_SLAB, Blocks.COAL_BLOCK)),
      UNVOIDABLE("Неизбежная", 15000L, Set.of(Blocks.ANDESITE, Blocks.ANDESITE_STAIRS, Blocks.DEAD_TUBE_CORAL_BLOCK, Blocks.POLISHED_BASALT));

      final String displayName;
      final long durationMillis;
      final Set<Block> blocks;

      TrapType(String name, long durationMillis, Set<Block> blocks) {
         this.displayName = name + " трапка";
         this.durationMillis = durationMillis;
         this.blocks = blocks;
      }
   }
}
