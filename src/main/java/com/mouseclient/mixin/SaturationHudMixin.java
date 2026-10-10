package com.mouseclient.mixin;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(InGameHud.class)
public abstract class SaturationHudMixin {
   @Unique
   private static final Identifier mouseclient$foodEmpty = Identifier.ofVanilla(("hud/food_empty"));
   @Unique
   private static final Identifier mouseclient$foodHalf = Identifier.ofVanilla(("hud/food_half"));
   @Unique
   private static final Identifier mouseclient$foodFull = Identifier.ofVanilla(("hud/food_full"));

   @Inject(method = "renderFood(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;II)V", at = @At("TAIL"))
   private void renderSaturationBar(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo callback) {
      int saturationLevel = Math.round(player.getHungerManager().getSaturationLevel());
      if (saturationLevel > 0) {
         for (int iconIndex = 0; iconIndex < 10; iconIndex++) {
            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, mouseclient$foodEmpty, right - iconIndex * 8 - 9, top - 10, 9, 9);
            if (iconIndex * 2 + 1 < saturationLevel) {
               context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, mouseclient$foodFull, right - iconIndex * 8 - 9, top - 10, 9, 9);
            } else if (iconIndex * 2 + 1 == saturationLevel) {
               context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, mouseclient$foodHalf, right - iconIndex * 8 - 9, top - 10, 9, 9);
            }
         }
      }
   }

   static {
      VMBridge.identifyClass(SaturationHudMixin.class, "n61THj3q");
   }
}
