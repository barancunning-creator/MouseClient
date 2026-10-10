package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.network.HudStyle;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.util.entity.WorldProjection;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import com.mouseclient.util.time.Stopwatch;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

@ModuleInfo(
   name = "WardenHelper",
   description = "Подсвечивает сундуки вардена и показывает их таймеры: отсчёт, «Активен», «Не активированный».",
   category = ModuleCategory.MISC
)
public final class WardenHelper extends Module {
   private static WardenHelper INSTANCE;
   private static final Pattern TIMER_PATTERN = Pattern.compile(("(?:(\\d{1,2}):(\\d{2})|(\\d+)\\s*(?:сек|сек\\.|с|s)|(\\d+)\\s*(?:мин|мин\\.|m))"));
   private final ConcurrentHashMap<BlockPos, Long> chestActivationTimes = new ConcurrentHashMap<>();
   private final Stopwatch scanTimer = new Stopwatch();
   private final EventListener<PlayerTickEvent> playerTickListener = event -> this.scanChests();
   private final EventListener<HudRenderEvent> hudRenderListener = event -> this.renderTimerLabels(event);
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> this.renderChestBoxes(event);

   private int statusColor(long activationTimeMillis) {
      return activationTimeMillis > System.currentTimeMillis() ? (-43691) : (activationTimeMillis > 0L ? (-11141291) : -171);
   }

   private void scanChests() {
      if (minecraftClient.player != null && minecraftClient.world != null && this.scanTimer.hasElapsed((250L))) {
         this.scanTimer.reset();

         for (int chunkX = minecraftClient.player.getChunkPos().x - 6; chunkX <= minecraftClient.player.getChunkPos().x + 6; chunkX++) {
            for (int chunkZ = minecraftClient.player.getChunkPos().z - 6; chunkZ <= minecraftClient.player.getChunkPos().z + 6; chunkZ++) {
               if (minecraftClient.world.isChunkLoaded(chunkX, chunkZ)) {
                  for (BlockEntity blockEntity : minecraftClient.world.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                     if (blockEntity instanceof ChestBlockEntity && !(minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(blockEntity.getPos())) > (6400.0D))) {
                        if (this.timerSeconds(blockEntity.getPos()) >= 0) {
                           this.chestActivationTimes.put(blockEntity.getPos().toImmutable(), System.currentTimeMillis() + this.timerSeconds(blockEntity.getPos()) * (1000L));
                        } else {
                           this.chestActivationTimes.putIfAbsent(blockEntity.getPos().toImmutable(), 0L);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void renderChestBoxes(WorldRenderEvent event) {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         for (Entry chestEntry : this.chestActivationTimes.entrySet()) {
            if (!(minecraftClient.player.getEntityPos().distanceTo(Vec3d.ofCenter((Vec3i)chestEntry.getKey())) > (80.0D))
               && minecraftClient.world.getBlockEntity((BlockPos)chestEntry.getKey()) instanceof ChestBlockEntity) {
               Box bounds = new Box((BlockPos)chestEntry.getKey()).contract((0.0625D), (0.0625D), (0.0625D));
               event.getMatrices().push();
               event.getMatrices()
                  .translate(
                     bounds.minX - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
                     bounds.minY - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
                     bounds.minZ - minecraftClient.gameRenderer.getCamera().getCameraPos().z
                  );
               Matrix4f positionMatrix = event.getMatrices().peek().getPositionMatrix();
               VertexConsumer vertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.debugQuads());
               float[] xCoordinates = new float[]{0.0F, (float)(bounds.maxX - bounds.minX)};
               float[] yCoordinates = new float[]{0.0F, (float)(bounds.maxY - bounds.minY)};
               float[] zCoordinates = new float[]{0.0F, (float)(bounds.maxZ - bounds.minZ)};
               int[][] faceVertices = new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

               for (int[] face : faceVertices) {
                  for (int vertexIndex : face) {
                     vertices.vertex(positionMatrix, xCoordinates[vertexIndex >> 2], yCoordinates[vertexIndex >> 1 & 1], zCoordinates[vertexIndex & 1])
                        .color(ArgbColor.multiplyAlpha(this.statusColor((Long)chestEntry.getValue()), (0.18F)));
                  }
               }

               VertexRendering.drawOutline(
                  event.getMatrices(),
                  minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines()),
                  VoxelShapes.cuboid(new Box(0.0, 0.0, 0.0, bounds.maxX - bounds.minX, bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ)),
                  0.0,
                  0.0,
                  0.0,
                  ArgbColor.multiplyAlpha(this.statusColor((Long)chestEntry.getValue()), (0.55F)),
                  1.0F
               );
               minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
               event.getMatrices().pop();
            }
         }
      }
   }

   public static WardenHelper getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new WardenHelper();
      }

      return INSTANCE;
   }

   private int timerSeconds(BlockPos position) {
      for (Entity entity : minecraftClient.world
         .getOtherEntities(
            minecraftClient.player,
            new Box(position).expand((3.0D), (4.0D), (3.0D)),
            candidate -> candidate instanceof ArmorStandEntity armorStand && armorStand.hasCustomName() || candidate instanceof TextDisplayEntity
         )) {
         Matcher matcher = TIMER_PATTERN.matcher((entity instanceof TextDisplayEntity textDisplay ? textDisplay.getText() : entity.getName()).getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim());
         if (matcher.find()) {
            if (matcher.group(1) != null) {
               return Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
            }

            if (matcher.group(3) != null) {
               return Integer.parseInt(matcher.group(3));
            }

            return Integer.parseInt(matcher.group(4)) * 60;
         }
      }

      return -1;
   }

   private void renderTimerLabels(HudRenderEvent event) {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         for (Entry chestEntry : this.chestActivationTimes.entrySet()) {
            if (!(minecraftClient.player.getEntityPos().distanceTo(Vec3d.ofCenter((Vec3i)chestEntry.getKey())) > (80.0D))
               && minecraftClient.world.getBlockEntity((BlockPos)chestEntry.getKey()) instanceof ChestBlockEntity) {
               Vec3d screenPosition = WorldProjection.projectNearViewport(Vec3d.ofCenter((Vec3i)chestEntry.getKey()).add(0.0, (0.7D), 0.0), event.getWidth(), event.getHeight());
               if (screenPosition != null) {
                  HudRenderContext.pushScale(
                     (float)screenPosition.x * GuiScale.getWindowScale() / GuiScale.getBaseScale(),
                     (float)screenPosition.y * GuiScale.getWindowScale() / GuiScale.getBaseScale(),
                     (float)Math.max((0.550000011920929D), 1.0 - minecraftClient.player.getEntityPos().distanceTo(Vec3d.ofCenter((Vec3i)chestEntry.getKey())) * (0.00800000037997961D))
                  );
                  Render2D.fillRoundedRectangle(
                     (float)screenPosition.x * GuiScale.getWindowScale() / GuiScale.getBaseScale()
                        - (GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, this.timerText((Long)chestEntry.getValue()), (4.0F)) + (4.0F)) / 2.0F,
                     (float)screenPosition.y * GuiScale.getWindowScale() / GuiScale.getBaseScale() - (4.5F),
                     GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, this.timerText((Long)chestEntry.getValue()), (4.0F)) + (4.0F),
                     (9.0F),
                     0.0F,
                     ArgbColor.withAlpha(HudStyle.backgroundColor, 133)
                  );
                  GuiTextRenderer.draw(
                     GuiFont.SEMI_BOLD,
                     this.timerText((Long)chestEntry.getValue()),
                     (float)screenPosition.x * GuiScale.getWindowScale() / GuiScale.getBaseScale()
                        - GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, this.timerText((Long)chestEntry.getValue()), (4.0F)) / 2.0F,
                     (float)screenPosition.y * GuiScale.getWindowScale() / GuiScale.getBaseScale() - (2.45F),
                     (4.0F),
                     this.statusColor((Long)chestEntry.getValue())
                  );
                  HudRenderContext.popTransform();
               }
            }
         }
      }
   }

   private String timerText(long activationTimeMillis) {
      if (activationTimeMillis > System.currentTimeMillis()) {
         return (activationTimeMillis - System.currentTimeMillis()) / (60000L) + ":" + String.format(("%02d"), (activationTimeMillis - System.currentTimeMillis()) / (1000L) % (60L));
      } else {
         return activationTimeMillis > 0L ? ("Активен") : ("Активирован");
      }
   }

   private WardenHelper() {
      ModuleRegistry.unregister(this);
   }
}
