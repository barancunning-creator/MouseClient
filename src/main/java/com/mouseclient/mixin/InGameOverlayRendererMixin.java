package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.NoRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {

   @Inject(method = "renderUnderwaterOverlay(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"), cancellable = true)
   private static void removeWaterOverlay(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Оверлей воды"))) {
         callback.cancel();
      }
   }

   @Inject(method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/texture/Sprite;)V", at = @At("HEAD"), cancellable = true)
   private static void removeFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Оверлей огня"))) {
         callback.cancel();
      }
   }

   @Inject(method = "renderInWallOverlay(Lnet/minecraft/client/texture/Sprite;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"), cancellable = true)
   private static void removeBlockDimming(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Затемнение блоков"))) {
         callback.cancel();
      }
   }

   static {
      VMBridge.identifyClass(InGameOverlayRendererMixin.class, "eLFn62Hf");
   }
}
