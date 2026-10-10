package com.mouseclient.mixin;

import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleRegistry;
import com.mouseclient.modules.impl.player.InputBinding;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
   @Inject(method = "onKey(JILnet/minecraft/client/input/KeyInput;)V", at = @At("HEAD"))
   private void toggleBoundModules(long window, int action, KeyInput keyInput, CallbackInfo callback) {
      if (window == MinecraftClient.getInstance().getWindow().getHandle() && keyInput.key() != -1 && action != 2) {
         InputBinding.trackKeyboardInput(keyInput, action == 1);
      }

      if (action == 1
         && keyInput.key() != -1
         && window == MinecraftClient.getInstance().getWindow().getHandle()
         && MinecraftClient.getInstance().currentScreen == null
         && MinecraftClient.getInstance().player != null) {
         for (Module module : ModuleRegistry.modules()) {
            if (module.getKeyCode() == keyInput.key()) {
               module.toggle();
            }
         }
      }
   }
}
