package com.mouseclient.network;

import com.mouseclient.dreamix.protection.api.impl.profile.models.CosmeticModelCache;
import com.mouseclient.modules.impl.misc.Globals;
import com.mouseclient.render.CosmeticEntry;
import com.mouseclient.render.EquippedCosmetics;
import com.mouseclient.render.CosmeticModelRenderer;
import com.mouseclient.render.PlayerCosmeticsFeatureRenderer;
import com.mouseclient.util.entity.PetCosmeticsRenderer;
import com.mouseclient.util.time.CosmeticCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class PetCosmeticsInitializer {

   private static void renderLegacyPet(MatrixStack matrices, VertexConsumerProvider consumers, MinecraftClient client, AbstractClientPlayerEntity player, CosmeticEntry entry) {
      if (entry != null && entry.placement() == null) {
         float tickDelta = client.getRenderTickCounter().getTickProgress(false);
         Vec3d position = player.getLerpedPos(tickDelta);
         Vec3d cameraPosition = client.gameRenderer.getCamera().getCameraPos();
         CosmeticModelCache.ModelOffset modelOffset = CosmeticModelCache.modelOffset(entry);
         if (modelOffset != null) {
            matrices.push();
            matrices.translate(position.x - cameraPosition.x, position.y - cameraPosition.y, position.z - cameraPosition.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.getLerpedYaw(tickDelta)));
            matrices.translate(modelOffset.x(), player.getHeight() - modelOffset.y(), modelOffset.z());
            switch (entry.id()) {
               case 45:
               case 46:
               case 90:
                  matrices.translate(0.0, (-3.0D), 0.0);
                  break;
               case 132:
                  matrices.translate(0.0, (-2.0D), 0.0);
                  break;
               case 133:
                  matrices.translate((-1.0D), (-0.5D), 0.0);
                  break;
               case 134:
               case 135:
                  matrices.translate((-1.0D), 0.0, 0.0);
            }

            CosmeticModelRenderer.renderPreview(
               entry,
               matrices,
               consumers,
               WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(position)),
               player.isGliding(),
               player.isSwimming(),
               player.isSneaking(),
               CosmeticMovementTracker.isMoving(player),
               PlayerCosmeticsFeatureRenderer.time()
            );
            matrices.pop();
         }
      }
   }

   public static void initialize() {
      ClientTickEvents.END_CLIENT_TICK.register(client -> PetCosmeticsRenderer.getInstance().tick(client));
      WorldRenderEvents.AFTER_ENTITIES.register(renderContext -> {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.player != null && client.world != null) {
            PetCosmeticsRenderer.getInstance().render(renderContext.matrices(), renderContext.consumers(), client);
            if (!client.options.getPerspective().isFirstPerson()) {
               renderLegacyPet(renderContext.matrices(), renderContext.consumers(), client, client.player, EquippedCosmetics.equippedEntry(CosmeticCategory.PET));
            }

            if (Globals.getInstance().hasSyncSnapshot()) {
               for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
                  if (player != client.player && !player.isSpectator() && !player.isInvisible()) {
                     renderLegacyPet(renderContext.matrices(), renderContext.consumers(), client, player, Globals.getInstance().syncedCosmetic(player.getGameProfile().name(), CosmeticCategory.PET));
                  }
               }
            }
         }
      });
   }

   private PetCosmeticsInitializer() {
   }
}
