package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.entity.WorldProjection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "WardenEsp", description = "Показывает таймер над сундуком вардена через стены.", category = ModuleCategory.RENDER)
public final class WardenEsp extends Module {
   private static WardenEsp INSTANCE;
   private static final Pattern TIMER_PATTERN = Pattern.compile(("(?:\\d{1,2}:\\d{2}|\\d+\\s*(?:сек|с|s|мин|m)\\b)"));
   private final IntOpenHashSet timerLabelEntityIds = new IntOpenHashSet();
   private final HashMap<BlockPos, Long> inactiveChestTimes = new HashMap<>();
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isIncoming() && event.getPacket() instanceof PlaySoundS2CPacket soundPacket && soundPacket.getSound().value().id().getPath().startsWith(("entity.warden"))) {
         minecraftClient.execute(() -> this.markNearbyChests(new Vec3d(soundPacket.getX(), soundPacket.getY(), soundPacket.getZ())));
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      this.timerLabelEntityIds.clear();
      if (minecraftClient.player != null && minecraftClient.world != null) {
         long nowMillis = System.currentTimeMillis();
         this.inactiveChestTimes
            .entrySet()
            .removeIf(
               chestEntry -> nowMillis - chestEntry.getValue() > (30000L)
                  || !(minecraftClient.world.getBlockState(chestEntry.getKey()).getBlock() instanceof ChestBlock)
                  || this.hasTimerLabel(chestEntry.getKey())
            );

         for (Entity entity : minecraftClient.world.getEntities()) {
            if (this.entityLabel(entity) != null && this.isAboveTimedChest(entity)) {
               this.timerLabelEntityIds.add(entity.getId());
            }
         }
      } else {
         this.inactiveChestTimes.clear();
      }
   };

   private WardenEsp() {
   }

   private void drawLabel(DrawContext context, Vec3d position, Text text) {
      Vec3d screenPosition = WorldProjection.projectNearViewport(position, context.getScaledWindowWidth(), context.getScaledWindowHeight());
      if (screenPosition != null) {
         float scale = Math.max((0.3F), 1.0F - (float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(position) * (0.008F));
         java.util.List<net.minecraft.text.OrderedText> lines = minecraftClient.textRenderer.wrapLines(text, 200);
         int lineHeight = 9 + 1;
         context.getMatrices().pushMatrix();
         context.getMatrices().translate((float)screenPosition.x, (float)screenPosition.y);
         context.getMatrices().scale(scale, scale);
         int topY = -lines.size() * lineHeight;

         for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            int lineWidth = minecraftClient.textRenderer.getWidth((OrderedText)lines.get(lineIndex));
            int lineY = topY + lineIndex * lineHeight;
            context.fill(-lineWidth / 2 - 1, lineY - 1, lineWidth / 2 + 1, lineY + lineHeight - 1, (1073741824));
            context.drawText(minecraftClient.textRenderer, (OrderedText)lines.get(lineIndex), -lineWidth / 2, lineY, -1, false);
         }

         context.getMatrices().popMatrix();
      }
   }

   @Override
   protected void onDisable() {
      this.timerLabelEntityIds.clear();
      this.inactiveChestTimes.clear();
   }

   private boolean isAboveTimedChest(Entity entity) {
      BlockPos position = this.findChestBelow(entity.getBlockPos());
      return position != null && this.hasTimerLabel(position);
   }

   public static WardenEsp getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new WardenEsp();
      }

      return INSTANCE;
   }

   public void renderLabels(DrawContext context, float tickDelta) {
      if (this.isEnabled() && minecraftClient.player != null && minecraftClient.world != null && (!this.timerLabelEntityIds.isEmpty() || !this.inactiveChestTimes.isEmpty())) {
         for (BlockPos position : this.inactiveChestTimes.keySet()) {
            this.drawLabel(context, Vec3d.ofBottomCenter(position).add(0.0, (1.5D), 0.0), Text.literal(("Не активирован")).withColor((16733525)));
         }

         IntIterator entityIds = this.timerLabelEntityIds.iterator();

         while (entityIds.hasNext()) {
            int entityId = (Integer)entityIds.next();
            Entity entity = minecraftClient.world.getEntityById(entityId);
            Text text = entity == null ? null : this.entityLabel(entity);
            if (text != null) {
               this.drawLabel(
                  context, entity.getLerpedPos(tickDelta).add(0.0, entity instanceof ArmorStandEntity ? entity.getHeight() + (0.5D) : (0.5D), 0.0), text
               );
            }
         }

         context.createNewRootLayer();
      }
   }

   private Text entityLabel(Entity entity) {
      if (entity instanceof TextDisplayEntity textDisplay) {
         return textDisplay.getText();
      } else {
         return entity instanceof ArmorStandEntity armorStand && armorStand.hasCustomName() ? armorStand.getCustomName() : null;
      }
   }

   private BlockPos findChestBelow(BlockPos position) {
      for (int verticalOffset = 0; verticalOffset <= 4; verticalOffset++) {
         for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int zOffset = -1; zOffset <= 1; zOffset++) {
               BlockPos candidatePosition = position.add(xOffset, -verticalOffset, zOffset);
               if (minecraftClient.world.getBlockState(candidatePosition).getBlock() instanceof ChestBlock) {
                  return candidatePosition;
               }
            }
         }
      }

      return null;
   }

   public boolean shouldHideVanillaLabel(Entity entity) {
      return this.isEnabled() && this.timerLabelEntityIds.contains(entity.getId());
   }

   private void markNearbyChests(Vec3d position) {
      if (minecraftClient.world != null) {
         ChunkPos centerChunk = new ChunkPos(BlockPos.ofFloored(position));

         for (int chunkX = centerChunk.x - 1; chunkX <= centerChunk.x + 1; chunkX++) {
            for (int chunkZ = centerChunk.z - 1; chunkZ <= centerChunk.z + 1; chunkZ++) {
               if (minecraftClient.world.isChunkLoaded(chunkX, chunkZ)) {
                  for (BlockEntity blockEntity : minecraftClient.world.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                     if (blockEntity instanceof ChestBlockEntity
                        && blockEntity.getPos().toCenterPos().squaredDistanceTo(position) <= (256.0D)
                        && !this.hasTimerLabel(blockEntity.getPos())) {
                        this.inactiveChestTimes.put(blockEntity.getPos().toImmutable(), System.currentTimeMillis());
                     }
                  }
               }
            }
         }
      }
   }

   private boolean hasTimerLabel(BlockPos position) {
      for (Entity entity : minecraftClient.world.getOtherEntities(null, new Box(position).expand((1.5D), (4.0D), (1.5D)))) {
         Text text = this.entityLabel(entity);
         if (text != null && entity.getY() > position.getY() && TIMER_PATTERN.matcher(text.getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).find()) {
            return true;
         }
      }

      return false;
   }
}
