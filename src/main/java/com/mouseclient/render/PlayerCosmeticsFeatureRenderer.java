package com.mouseclient.render;

import com.mouseclient.modules.impl.misc.Globals;
import com.mouseclient.util.time.CosmeticPlacement;
import com.mouseclient.util.time.CosmeticCategory;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer.HeadTransformation;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

public final class PlayerCosmeticsFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
   private static final long animationStartNanos = System.nanoTime();
   private static final CosmeticCategory[] LAYER_CATEGORIES = new CosmeticCategory[]{CosmeticCategory.BODYWEAR, CosmeticCategory.WINGS, CosmeticCategory.HAT, CosmeticCategory.ACCESSORY};
   
   private static final Int2ObjectOpenHashMap<CosmeticEntry[]> syncedEntriesByEntity = new Int2ObjectOpenHashMap();
   private static long cachedTick = (-9223372036854775808L);
   private static Object cachedWorld;

   private static boolean isLocal(PlayerEntityRenderState state) {
      MinecraftClient client = MinecraftClient.getInstance();
      return client.player != null && client.player.getId() == state.id;
   }

   public PlayerCosmeticsFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
      super(context);
   }

   private static int layerSlot(CosmeticCategory category) {
      for (int var1 = 0; var1 < LAYER_CATEGORIES.length; var1++) {
         if (LAYER_CATEGORIES[var1] == category) {
            return var1;
         }
      }

      return 0;
   }

   @Override
   public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float yaw, float pitch) {
      if (!state.spectator && (isLocal(state) || Globals.getInstance().hasSyncSnapshot())) {
         float animationTime = time();
         boolean moving = Math.abs(state.limbSwingAmplitude) > (0.01F);
         this.submitGeometry(CosmeticCategory.BODYWEAR, matrices, queue, light, state, moving, animationTime);
         this.submitGeometry(CosmeticCategory.WINGS, matrices, queue, light, state, moving, animationTime);
         this.submitGeometry(CosmeticCategory.HAT, matrices, queue, light, state, moving, animationTime);
         this.submitGeometry(CosmeticCategory.ACCESSORY, matrices, queue, light, state, moving, animationTime);
      }
   }

   private static CosmeticEntry equippedEntry(CosmeticCategory category, PlayerEntityRenderState state) {
      if (isLocal(state)) {
         return EquippedCosmetics.equippedEntry(category);
      }

      CosmeticEntry[] synchronizedEntries = syncedEntries(state.id);
      return synchronizedEntries == null ? null : synchronizedEntries[layerSlot(category)];
   }

   private static CosmeticEntry[] syncedEntries(int entityId) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world == null) {
         return null;
      }

      long currentTick = System.currentTimeMillis() / (50L);
      if (currentTick != cachedTick || client.world != cachedWorld) {
         syncedEntriesByEntity.clear();
         cachedTick = currentTick;
         cachedWorld = client.world;
      }

      CosmeticEntry[] cachedEntries = (CosmeticEntry[])syncedEntriesByEntity.get(entityId);
      if (cachedEntries == null) {
         cachedEntries = new CosmeticEntry[LAYER_CATEGORIES.length];
         if (client.world.getEntityById(entityId) instanceof AbstractClientPlayerEntity player) {
            String playerName = player.getGameProfile().name();

            for (int categoryIndex = 0; categoryIndex < LAYER_CATEGORIES.length; categoryIndex++) {
               cachedEntries[categoryIndex] = Globals.getInstance().syncedCosmetic(playerName, LAYER_CATEGORIES[categoryIndex]);
            }
         }

         syncedEntriesByEntity.put(entityId, cachedEntries);
      }

      return cachedEntries;
   }

   private void submitGeometry(
      CosmeticCategory category, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, boolean moving, float time
   ) {
      CosmeticEntry entry = equippedEntry(category, state);
      if (entry != null) {
         if (entry.placement() != null) {
            this.submitHostedModel(entry, entry.placement(), matrices, queue, light, state);
         } else {
            matrices.push();
            if (category == CosmeticCategory.HAT) {
               this.getContextModel().head.applyTransform(matrices);
               matrices.scale((1.08F), (1.08F), (1.08F));
               switch (entry.id()) {
                  case 48:
                  case 49:
                  case 50:
                  case 53:
                  case 57:
                     matrices.translate(0.0, (-0.365D), 0.0);
                     break;
                  case 80:
                     matrices.translate(0.0, (0.06D), 0.0);
                  case 85:
                  case 98:
                     break;
                  case 88:
                  case 97:
                  case 125:
                     matrices.translate(0.0, (0.0625D), 0.0);
                     break;
                  case 92:
                     matrices.translate(0.0, 0.0, (-0.00625D));
                     break;
                  case 96:
                     matrices.translate(0.0, 0.0, (-0.06875D));
                     break;
                  default:
                     matrices.translate(0.0, (-0.15D), 0.0);
               }
            } else if (category == CosmeticCategory.BODYWEAR) {
               this.getContextModel().body.applyTransform(matrices);
               matrices.translate(0.0, (0.25D), 0.0);
            } else if (category == CosmeticCategory.WINGS) {
               this.getContextModel().body.applyTransform(matrices);
               matrices.translate(0.0, (0.27D), 0.0);
            }

            CosmeticModelRenderer.submitModel(entry, matrices, queue, light, state.isGliding, state.isSwimming, state.isInSneakingPose, moving, time);
            matrices.pop();
         }
      }
   }

   private void submitHostedModel(CosmeticEntry entry, CosmeticPlacement placement, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state) {
      if (!placement.isPet()) {
         JsonCosmeticModel cosmeticModel = JsonCosmeticModel.getOrRequest(entry);
         if (cosmeticModel != null) {
            matrices.push();
            this.getContextModel().getRootPart().applyTransform(matrices);
            if (placement.onBody()) {
               this.getContextModel().body.applyTransform(matrices);
            } else {
               this.getContextModel().head.applyTransform(matrices);
            }

            float shoulderAdjustedX = placement.anchor().equals(("shoulder")) ? Math.max((0.3F), Math.abs(placement.x())) : placement.x();
            matrices.translate(shoulderAdjustedX, placement.y(), placement.z());
            if (!placement.isAnimatedHead()) {
               HeadFeatureRenderer.translate(matrices, HeadTransformation.DEFAULT);
            }

            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(placement.rotationX()));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(placement.rotationY()));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(placement.rotationZ()));
            if (placement.isAnimatedHead()) {
               float animatedHeadScale = placement.alignScale() * Math.max((0.05F), placement.scale());
               matrices.translate(0.0F, (-0.25F), 0.0F);
               matrices.scale(animatedHeadScale, -animatedHeadScale, animatedHeadScale);
               matrices.translate(-placement.alignX() / (16.0F), -placement.alignY() / (16.0F), 0.0F);
            } else {
               float modelScale = Math.max((0.05F), placement.scale());
               matrices.scale(modelScale, modelScale, modelScale);
               cosmeticModel.applyHeadTransform(matrices);
            }

            cosmeticModel.submit(matrices, queue, light, state.age / (20.0F), 0.0F);
            matrices.pop();
         }
      }
   }

   public static float time() {
      return (float)((System.nanoTime() - animationStartNanos) / (1.0E9D));
   }
}
