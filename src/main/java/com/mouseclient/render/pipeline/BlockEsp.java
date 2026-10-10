package com.mouseclient.render.pipeline;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.network.PlayerTickEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.BufferAllocator.CloseableBuffer;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@ModuleInfo(name = "BlockEsp", description = "Подсвечивает сундуки, шалкеры, бочки и прочие хранилища сквозь стены.", category = ModuleCategory.RENDER)
public final class BlockEsp extends Module {
   private static BlockEsp INSTANCE;
   private static final RenderPipeline STORAGE_PIPELINE = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
      .withLocation(ClientInitializer.id(("pipeline/storage_esp")))
      .withVertexShader(Identifier.ofVanilla(("core/position_color")))
      .withFragmentShader(Identifier.ofVanilla(("core/position_color")))
      .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .build();
   private static final Vector4f WHITE_COLOR = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
   private static final Vector3f ZERO_MODEL_OFFSET = new Vector3f();
   private static final Matrix4f IDENTITY_TEXTURE_MATRIX = new Matrix4f();
   private static final int[][] FACE_VERTICES = new int[][]{{0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
   private static final float[] FACE_BRIGHTNESS = new float[]{
      (0.45F), 1.0F, (0.7F), (0.7F), (0.55F), (0.55F)
   };
   private static final int[] STORAGE_COLORS = new int[]{
      (-1526454), (-5219088), (-1020704), (-3569072), -29632, (-5195576), (-9651984)
   };
   private static final String[] STORAGE_LABELS = new String[]{
      ("Сундуки"), ("Эндер-сундуки"), ("Шалкеры"), ("Бочки"), ("Печки"), ("Воронки"), ("Раздатчики")
   };
   public MultiSelectSetting storageTypes;
   public DecimalSetting distance;
   public DecimalSetting density;
   private final ArrayList<float[]> blockEntries = new ArrayList<>();
   private final LinkedHashSet<Block> customBlocks = new LinkedHashSet<>();
   private boolean customBlocksLoaded;
   private long lastScanMillis;
   private GpuBuffer vertexBuffer;
   private BufferAllocator allocator;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && System.currentTimeMillis() - this.lastScanMillis >= (500L)) {
         this.loadCustomBlocks();
         this.lastScanMillis = System.currentTimeMillis();
         this.blockEntries.clear();
         int chunkRadius = (int)Math.ceil(this.distance.getValue() / (16.0F)) + 1;
         int centerChunkX = minecraftClient.player.getChunkPos().x;
         int centerChunkZ = minecraftClient.player.getChunkPos().z;
         float distanceSquared = this.distance.getValue() * this.distance.getValue();

         for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
               WorldChunk chunk = minecraftClient.world.getChunk(chunkX, chunkZ);

               for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                  int storageIndex = blockEntity instanceof ChestBlockEntity
                     ? 0
                     : (
                        blockEntity instanceof EnderChestBlockEntity
                           ? 1
                           : (
                              blockEntity instanceof ShulkerBoxBlockEntity
                                 ? 2
                                 : (
                                    blockEntity instanceof BarrelBlockEntity
                                       ? 3
                                       : (
                                          blockEntity instanceof AbstractFurnaceBlockEntity
                                             ? 4
                                             : (blockEntity instanceof HopperBlockEntity ? 5 : (blockEntity instanceof DispenserBlockEntity ? 6 : -1))
                                       )
                                 )
                           )
                     );
                  if (storageIndex != -1 && this.storageTypes.matchesOption(STORAGE_LABELS[storageIndex])) {
                     BlockPos position = blockEntity.getPos();
                     if (!(
                           minecraftClient.player.squaredDistanceTo(position.getX() + (0.5D), position.getY() + (0.5D), position.getZ() + (0.5D))
                              > distanceSquared
                        )
                        && this.blockEntries.size() < 256) {
                        this.blockEntries.add(new float[]{position.getX(), position.getY(), position.getZ(), Float.intBitsToFloat(STORAGE_COLORS[storageIndex])});
                     }
                  }
               }

               if (!this.customBlocks.isEmpty()) {
                  this.scanCustomBlocks(chunk, distanceSquared);
               }
            }
         }
      }
   };
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && !this.blockEntries.isEmpty()) {
         if (minecraftClient.getFramebuffer() != null && minecraftClient.getFramebuffer().getColorAttachmentView() != null) {
            float cameraX = (float)minecraftClient.gameRenderer.getCamera().getCameraPos().x;
            float cameraY = (float)minecraftClient.gameRenderer.getCamera().getCameraPos().y;
            float cameraZ = (float)minecraftClient.gameRenderer.getCamera().getCameraPos().z;
            int quadCount = 0;
            float inset = (0.04F);
            if (this.allocator == null) {
               this.allocator = new BufferAllocator(12288 * VertexFormats.POSITION_COLOR.getVertexSize());
            }

            CloseableBuffer previousBuffer = this.allocator.getAllocated();
            if (previousBuffer != null) {
               previousBuffer.close();
            }

            BufferBuilder builder = new BufferBuilder(this.allocator, DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (float[] blockEntry : this.blockEntries) {
               float minX = blockEntry[0] - cameraX + inset;
               float minY = blockEntry[1] - cameraY + inset;
               float minZ = blockEntry[2] - cameraZ + inset;
               float maxX = minX + 1.0F - inset * 2.0F;
               float maxY = minY + 1.0F - inset * 2.0F;
               float maxZ = minZ + 1.0F - inset * 2.0F;
               int baseColor = Float.floatToRawIntBits(blockEntry[3]);

               for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
                  int faceColor = ArgbColor.multiplyAlpha(
                     ArgbColor.lerp((-16777216), baseColor, FACE_BRIGHTNESS[faceIndex]), this.density.getValue()
                  );

                  for (int vertexIndex : FACE_VERTICES[faceIndex]) {
                     builder.vertex((vertexIndex & 4) == 0 ? minX : maxX, (vertexIndex & 2) == 0 ? minY : maxY, (vertexIndex & 1) == 0 ? minZ : maxZ).color(faceColor);
                  }

                  quadCount++;
               }
            }

            try (BuiltBuffer meshData = builder.end()) {
               if (this.vertexBuffer != null && !this.vertexBuffer.isClosed() && this.vertexBuffer.size() >= meshData.getBuffer().remaining()) {
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0L, meshData.getBuffer().remaining()), meshData.getBuffer());
               } else {
                  if (this.vertexBuffer != null && !this.vertexBuffer.isClosed()) {
                     this.vertexBuffer.close();
                  }

                  this.vertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_storage_esp_vertices"), 40, meshData.getBuffer());
               }

               GpuBufferSlice bufferSlice = RenderSystem.getDynamicUniforms().write(event.getMatrices().peek().getPositionMatrix(), WHITE_COLOR, ZERO_MODEL_OFFSET, IDENTITY_TEXTURE_MATRIX);

               try (RenderPass renderPass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(
                        () -> ("mouseclient_storage_esp"), minecraftClient.getFramebuffer().getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty()
                     )) {
                  renderPass.setPipeline(STORAGE_PIPELINE);
                  renderPass.setUniform(("DynamicTransforms"), bufferSlice);
                  renderPass.setUniform(("Projection"), RenderSystem.getProjectionMatrixBuffer());
                  renderPass.setVertexBuffer(0, this.vertexBuffer);
                  renderPass.setIndexBuffer(
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(quadCount * 6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                  );
                  renderPass.drawIndexed(0, 0, quadCount * 6, 1);
               }
            }
         }
      }
   };

   public boolean removeCustomBlock(Block block) {
      this.loadCustomBlocks();
      boolean removed = this.customBlocks.remove(block);
      if (removed) {
         this.saveCustomBlocks();
      }

      return removed;
   }

   @Override
   protected void onDisable() {
      if (this.allocator != null) {
         this.allocator.close();
         this.allocator = null;
      }

      if (this.vertexBuffer != null && !this.vertexBuffer.isClosed()) {
         this.vertexBuffer.close();
      }

      this.vertexBuffer = null;
   }

   private void scanCustomBlocks(WorldChunk chunk, float distanceSquared) {
      ChunkSection[] sections = chunk.getSectionArray();
      int minSectionY = MathHelper.floor((minecraftClient.player.getY() - this.distance.getValue()) / (16.0D));
      int maxSectionY = MathHelper.floor((minecraftClient.player.getY() + this.distance.getValue()) / (16.0D));

      for (int sectionIndex = 0; sectionIndex < sections.length && this.blockEntries.size() < 512; sectionIndex++) {
         int sectionY = minecraftClient.world.sectionIndexToCoord(sectionIndex);
         ChunkSection section = sections[sectionIndex];
         if (sectionY >= minSectionY && sectionY <= maxSectionY && !section.isEmpty() && section.hasAny(blockState -> this.customBlocks.contains(blockState.getBlock()))) {
            int chunkStartX = chunk.getPos().getStartX();
            int sectionStartY = sectionY << 4;
            int chunkStartZ = chunk.getPos().getStartZ();

            for (int localY = 0; localY < 16; localY++) {
               for (int localZ = 0; localZ < 16; localZ++) {
                  for (int localX = 0; localX < 16; localX++) {
                     Block block = section.getBlockState(localX, localY, localZ).getBlock();
                     if (this.customBlocks.contains(block)
                        && !(
                           minecraftClient.player.squaredDistanceTo(chunkStartX + localX + (0.5D), sectionStartY + localY + (0.5D), chunkStartZ + localZ + (0.5D))
                              > distanceSquared
                        )) {
                        this.blockEntries
                           .add(new float[]{chunkStartX + localX, sectionStartY + localY, chunkStartZ + localZ, Float.intBitsToFloat(block.getDefaultMapColor().color | (-16777216))});
                        if (this.blockEntries.size() >= 512) {
                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void loadCustomBlocks() {
      if (!this.customBlocksLoaded) {
         this.customBlocksLoaded = true;

         try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("blockesp.json"));
            if (!Files.exists(path)) {
               return;
            }

            for (JsonElement jsonElement : JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonArray()) {
               Identifier id = Identifier.tryParse(jsonElement.getAsString());
               if (id != null) {
                  Registries.BLOCK.getOptionalValue(id).ifPresent(this.customBlocks::add);
               }
            }
         } catch (Exception error) {
         }
      }
   }

   public void clearCustomBlocks() {
      this.loadCustomBlocks();
      this.customBlocks.clear();
      this.saveCustomBlocks();
   }

   public boolean addCustomBlock(Block block) {
      this.loadCustomBlocks();
      boolean added = this.customBlocks.add(block);
      if (added) {
         this.saveCustomBlocks();
      }

      return added;
   }

   public static BlockEsp getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new BlockEsp();
      }

      return INSTANCE;
   }

   private void saveCustomBlocks() {
      try {
         Path path = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("blockesp.json"));
         Files.createDirectories(path.getParent());
         JsonArray jsonArray = new JsonArray();

         for (Block block : this.customBlocks) {
            jsonArray.add(Registries.BLOCK.getId(block).toString());
         }

         Files.writeString(path, jsonArray.toString(), StandardCharsets.UTF_8);
      } catch (Exception error) {
      }
   }

   public ArrayList<Block> customBlocks() {
      this.loadCustomBlocks();
      return new ArrayList<>(this.customBlocks);
   }

   private BlockEsp() {
      this.storageTypes = this.addSetting(
         new MultiSelectSetting(("Хранилища"), ("Какие блоки подсвечивать."), new String[]{("Сундуки"), ("Эндер-сундуки"), ("Шалкеры"), ("Бочки"), ("Печки"), ("Воронки"), ("Раздатчики")}, ("Сундуки"), ("Эндер-сундуки"), ("Шалкеры"), ("Бочки"))
      );
      this.distance = this.addSetting(new DecimalSetting(("Дистанция"), ("Радиус подсветки в блоках."), (48.0D), (16.0D), (128.0D), (4.0D)));
      this.density = this.addSetting(new DecimalSetting(("Плотность"), ("Прозрачность заливки."), (0.3D), (0.05D), (0.8D), (0.05D)));
   }
}
