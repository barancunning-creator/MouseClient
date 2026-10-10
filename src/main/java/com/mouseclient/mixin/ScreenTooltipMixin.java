package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.ShulkerPreview;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenTooltipMixin {
   @Inject(method = "renderWithTooltip(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
   private void renderShulkerToolip(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo callback) {
      if ((Object)this instanceof HandledScreen handledScreen) {
         if (((HandledScreenAccessor)(Object)handledScreen).hoveredSlot() != null && ((HandledScreenAccessor)(Object)handledScreen).hoveredSlot().hasStack()) {
            ShulkerPreview.getInstance().renderHoveredShulker(context, ((HandledScreenAccessor)(Object)handledScreen).hoveredSlot().getStack(), mouseX, mouseY);
         }
      }
   }
}
