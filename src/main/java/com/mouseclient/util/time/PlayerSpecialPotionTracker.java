package com.mouseclient.util.time;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PlayerSpecialPotionTracker {
   private static final Map<UUID, PlayerSpecialPotionTracker.PlayerPotionState> statesByUuid = new HashMap<>();
   private static ClientWorld trackedWorld;

   public static Map<SpecialPotion, Long> expiresAtMillis(AbstractClientPlayerEntity player) {
      PlayerSpecialPotionTracker.PlayerPotionState state = statesByUuid.get(player.getUuid());
      if (state != null && state.entityId() == player.getId()) {
         if (player != MinecraftClient.getInstance().player) {
            return state.expiresAtMs();
         }

         java.util.EnumMap<com.mouseclient.util.time.SpecialPotion,java.lang.Long> actualExpiry = new EnumMap<>(SpecialPotion.class);
         long nowMillis = System.currentTimeMillis();

         for (SpecialPotion potion : state.expiresAtMs().keySet()) {
            int durationTicks = 0;

            for (SpecialPotion.SpecialPotionEffect effectSpec : potion.effects()) {
               StatusEffectInstance effect = player.getStatusEffect(effectSpec.effect());
               if (effect == null || !SpecialPotion.amplifierMatches(effect.getAmplifier(), effectSpec.amplifier())) {
                  durationTicks = 0;
                  break;
               }

               durationTicks = Math.max(durationTicks, effect.getDuration());
            }

            if (durationTicks > 0) {
               actualExpiry.put(potion, nowMillis + durationTicks * (50L));
            }
         }

         return actualExpiry;
      } else {
         return Map.of();
      }
   }

   private PlayerSpecialPotionTracker() {
   }

   public static void recordSplash(SpecialPotion potion, BlockPos splashPosition) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world != null && SpecialPotion.isTrackingEnabled()) {
         prune(client.world);
         Vec3d splashCenter = splashPosition.toCenterPos();
         long nowMillis = System.currentTimeMillis();

         for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player.isAlive()) {
               int durationTicks = potion.splashDurationTicks(SpecialPotion.splashFactor(player.getBoundingBox(), splashCenter));
               if (durationTicks > 0) {
                  PlayerSpecialPotionTracker.PlayerPotionState state = statesByUuid.get(player.getUuid());
                  if (state == null || state.entityId() != player.getId()) {
                     state = new PlayerSpecialPotionTracker.PlayerPotionState(player.getId(), new EnumMap<>(SpecialPotion.class));
                     statesByUuid.put(player.getUuid(), state);
                  }

                  state.expiresAtMs().merge(potion, nowMillis + durationTicks * (50L), Math::max);
               }
            }
         }
      }
   }

   public static void prune(ClientWorld world) {
      if (world != trackedWorld) {
         statesByUuid.clear();
         trackedWorld = world;
      }

      if (!statesByUuid.isEmpty()) {
         long nowMillis = System.currentTimeMillis();
         statesByUuid.entrySet().removeIf(entry -> {
            PlayerEntity player = world.getPlayerByUuid(entry.getKey());
            if (player != null && !player.isRemoved() && !(player.getHealth() <= 0.0F) && player.getId() == entry.getValue().entityId()) {
               entry.getValue().expiresAtMs().values().removeIf(expiresAtMillis -> expiresAtMillis <= nowMillis);
               return entry.getValue().expiresAtMs().isEmpty();
            } else {
               return true;
            }
         });
      }
   }

   private record PlayerPotionState(int entityId, EnumMap<SpecialPotion, Long> expiresAtMs) {
   }
}
