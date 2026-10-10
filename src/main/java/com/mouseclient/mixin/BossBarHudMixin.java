package com.mouseclient.mixin;

import com.mouseclient.modules.impl.misc.StreamerMod;
import com.mouseclient.modules.impl.misc.YoutubeModeModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.entity.boss.BossBar.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
   @ModifyExpressionValue(method = "render(Lnet/minecraft/client/gui/DrawContext;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ClientBossBar;getName()Lnet/minecraft/text/Text;"))
   private Text maskDuelBossBarTitle(Text text) {
      if (YoutubeModeModule.getInstance().shouldReplaceBossBar()) {
         return YoutubeModeModule.getInstance().bossBarName();
      } else {
         return !StreamerMod.getInstance().isEnabled() ? text : StreamerMod.getInstance().bossBarTitle();
      }
   }

   @ModifyExpressionValue(
      method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/boss/BossBar;getPercent()F")
   )
   private float freezeDuelBossBarProgress(float progress) {
      return !StreamerMod.getInstance().isEnabled() && !YoutubeModeModule.getInstance().shouldReplaceBossBar() ? progress : 1.0F;
   }

   @ModifyExpressionValue(
      method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/boss/BossBar;getColor()Lnet/minecraft/entity/boss/BossBar$Color;")
   )
   private Color recolorDuelBossBar(Color color) {
      if (YoutubeModeModule.getInstance().shouldReplaceBossBar()) {
         return YoutubeModeModule.getInstance().bossBarColor();
      } else {
         return !StreamerMod.getInstance().isEnabled() ? color : Color.BLUE;
      }
   }

   @ModifyExpressionValue(
      method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/boss/BossBar;getStyle()Lnet/minecraft/entity/boss/BossBar$Style;")
   )
   private Style notchDuelBossBar(Style style) {
      return !StreamerMod.getInstance().isEnabled() && !YoutubeModeModule.getInstance().shouldReplaceBossBar() ? style : Style.NOTCHED_20;
   }
}
