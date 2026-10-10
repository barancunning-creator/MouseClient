package com.mouseclient.mixin;

import com.mouseclient.render.pipeline.CustomWorld;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.ByteBuffer;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
   private static final float RED_CHANNEL_SCALE = 255.0F;
   private static final float GREEN_CHANNEL_SCALE = 255.0F;

   @ModifyReturnValue(method = "getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;", at = @At("RETURN"))
   private Vector4f tintFogColor(Vector4f fogColor) {
      CustomWorld customWorld = CustomWorld.getInstance();
      if (customWorld.isCustomFogEnabled()) {
         int configuredColor = customWorld.fogColor.getArgb();
         CustomWorld.horizonRgb[0] = (configuredColor >> 16 & 0xFF) / RED_CHANNEL_SCALE;
         CustomWorld.horizonRgb[1] = (configuredColor >> 8 & 0xFF) / GREEN_CHANNEL_SCALE;
         CustomWorld.horizonRgb[2] = (configuredColor & 0xFF) / (255.0F);
         return new Vector4f(CustomWorld.horizonRgb[0], CustomWorld.horizonRgb[1], CustomWorld.horizonRgb[2], fogColor.w);
      } else {
         CustomWorld.horizonRgb[0] = fogColor.x;
         CustomWorld.horizonRgb[1] = fogColor.y;
         CustomWorld.horizonRgb[2] = fogColor.z;
         return fogColor;
      }
   }

   @WrapOperation(
      method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V")
   )
   private void scaleFogDistance(
      FogRenderer renderer,
      ByteBuffer buffer,
      int bufPos,
      Vector4f fogColor,
      float environmentalStart,
      float environmentalEnd,
      float renderDistanceStart,
      float renderDistanceEnd,
      float skyEnd,
      float cloudEnd,
      Operation<Void> operation
   ) {
      CustomWorld customWorld = CustomWorld.getInstance();
      if (customWorld.isCustomFogEnabled() && !customWorld.isHazeEnabled()) {
         float configuredDistance = customWorld.fogDistance.getValue();
         operation.call(renderer, buffer, bufPos, fogColor, environmentalStart, environmentalEnd, configuredDistance * (0.2F), configuredDistance, skyEnd, cloudEnd);
      } else {
         operation.call(renderer, buffer, bufPos, fogColor, environmentalStart, environmentalEnd, renderDistanceStart, renderDistanceEnd, skyEnd, cloudEnd);
      }
   }

   static {
      VMBridge.identifyClass(FogRendererMixin.class, "cvzKICOH");
   }
}
