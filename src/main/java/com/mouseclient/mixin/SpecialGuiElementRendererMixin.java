package com.mouseclient.mixin;

import com.mouseclient.util.time.CosmeticWheelScreen;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(SpecialGuiElementRenderer.class)
public abstract class SpecialGuiElementRendererMixin {

   @ModifyArg(
      method = "renderElement(Lnet/minecraft/client/gui/render/state/special/SpecialGuiElementRenderState;Lnet/minecraft/client/gui/render/state/GuiRenderState;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/render/state/TexturedQuadGuiElementRenderState;<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;Lorg/joml/Matrix3x2f;IIIIFFFFILnet/minecraft/client/gui/ScreenRect;Lnet/minecraft/client/gui/ScreenRect;)V"
      ),
      index = 11
   )
   private int fadeCosmeticModel(int color) {
      float modelAlpha = CosmeticWheelScreen.modelAlpha();
      if (modelAlpha >= (0.999F)) {
         return color;
      }

      int channelValue = Math.round(Math.max(0.0F, modelAlpha) * (255.0F));
      return channelValue << 24 | channelValue << 16 | channelValue << 8 | channelValue;
   }

   static {
      VMBridge.identifyClass(SpecialGuiElementRendererMixin.class, "JXhSlpVX");
   }
}
