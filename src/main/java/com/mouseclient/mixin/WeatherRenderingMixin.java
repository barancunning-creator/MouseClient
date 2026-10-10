package com.mouseclient.mixin;

import com.mouseclient.render.pipeline.CustomWorld;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(WeatherRendering.class)
public abstract class WeatherRenderingMixin {

   @WrapOperation(
      method = "renderPrecipitation(Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/client/render/state/WeatherRenderState;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WeatherRendering;renderPieces(Lnet/minecraft/client/render/VertexConsumer;Ljava/util/List;Lnet/minecraft/util/math/Vec3d;FIF)V",
         ordinal = 0
      )
   )
   private void skipVanillaRain(WeatherRendering renderer, VertexConsumer vertices, List<?> pieces, Vec3d position, float intensity, int range, float gradient, Operation<Void> operation) {
      if (!CustomWorld.getInstance().isEnabled()
         || !CustomWorld.getInstance().shaderRain.getValue()
            && (
               !CustomWorld.getInstance().worldRain.getValue()
                  || !CustomWorld.getInstance().hideVanillaRain.getValue()
                  || !(CustomWorld.getInstance().rainStreaks.getValue() > (0.002F))
            )) {
         operation.call(renderer, vertices, pieces, position, intensity, range, gradient);
      }
   }

   @ModifyArg(method = "renderPieces(Lnet/minecraft/client/render/VertexConsumer;Ljava/util/List;Lnet/minecraft/util/math/Vec3d;FIF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;color(I)Lnet/minecraft/client/render/VertexConsumer;"))
   private int tintPrecipitationColor(int color) {
      CustomWorld customWorld = CustomWorld.getInstance();
      if (customWorld.isEnabled() && customWorld.customPrecipitationColor.getValue()) {
         int precipitationColor = customWorld.precipitationColor.getArgb();
         int alpha = color >>> 24;
         return alpha << 24 | precipitationColor & (16777215);
      } else {
         return color;
      }
   }

   static {
      VMBridge.identifyClass(WeatherRenderingMixin.class, "xV8TSUVH");
   }
}
