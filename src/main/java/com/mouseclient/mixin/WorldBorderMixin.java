package com.mouseclient.mixin;

import com.mouseclient.modules.impl.player.NoPush;
import com.mouseclient.util.entity.MinecraftAccess;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.world.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(WorldBorder.class)
public abstract class WorldBorderMixin implements MinecraftAccess {

   @Inject(method = "canCollide(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Z", at = @At("HEAD"), cancellable = true)
   private void removeBorderPush(Entity entity, Box box, CallbackInfoReturnable<Boolean> callback) {
      if (entity == minecraftClient.player && NoPush.getInstance().blocksSource(("Граница мира"))) {
         callback.setReturnValue(false);
      }
   }

   static {
      VMBridge.identifyClass(WorldBorderMixin.class, "RMgutFys");
   }
}
