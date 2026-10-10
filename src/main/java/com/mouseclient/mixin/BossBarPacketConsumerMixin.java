package com.mouseclient.mixin;

import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.entity.boss.BossBar.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net/minecraft/client/gui/hud/BossBarHud$1")
public abstract class BossBarPacketConsumerMixin {
   @Inject(method = "remove(Ljava/util/UUID;)V", at = @At("HEAD"), cancellable = true)
   private void skipUnknownRemove(UUID uuid, CallbackInfo callback) {
      if (unknownBar(uuid)) {
         callback.cancel();
      }
   }

   @Inject(method = "updateProgress(Ljava/util/UUID;F)V", at = @At("HEAD"), cancellable = true)
   private void skipUnknownProgress(UUID uuid, float progress, CallbackInfo callback) {
      if (unknownBar(uuid)) {
         callback.cancel();
      }
   }

   @Inject(method = "updateName(Ljava/util/UUID;Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
   private void skipUnknownName(UUID uuid, Text text, CallbackInfo callback) {
      if (unknownBar(uuid)) {
         callback.cancel();
      }
   }

   @Inject(method = "updateStyle(Ljava/util/UUID;Lnet/minecraft/entity/boss/BossBar$Color;Lnet/minecraft/entity/boss/BossBar$Style;)V", at = @At("HEAD"), cancellable = true)
   private void skipUnknownStyle(UUID uuid, Color color, Style style, CallbackInfo callback) {
      if (unknownBar(uuid)) {
         callback.cancel();
      }
   }

   @Inject(method = "updateProperties(Ljava/util/UUID;ZZZ)V", at = @At("HEAD"), cancellable = true)
   private void skipUnknownProperties(UUID uuid, boolean darkenSky, boolean dragonMusic, boolean thickenFog, CallbackInfo callback) {
      if (unknownBar(uuid)) {
         callback.cancel();
      }
   }

   private static boolean unknownBar(UUID uuid) {
      return !((BossBarHudAccessor)(Object)MinecraftClient.getInstance().inGameHud.getBossBarHud()).getEvents().containsKey(uuid);
   }
}
