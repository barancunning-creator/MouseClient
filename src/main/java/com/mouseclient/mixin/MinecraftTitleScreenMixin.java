package com.mouseclient.mixin;

import com.mouseclient.gui.MainMenuScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public class MinecraftTitleScreenMixin {
   @Unique
   private static boolean mouseclient$loadingShown;

   @ModifyVariable(method = "setScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"), argsOnly = true)
   private Screen replaceTitleScreen(Screen screen) {
      if (screen instanceof com.mouseclient.util.time.WelcomeScreen) {
         return new MainMenuScreen(false);
      }
      if (!(screen instanceof TitleScreen) || screen instanceof MainMenuScreen) {
         return screen;
      } else {
         return new MainMenuScreen(false);
      }
   }

   @ModifyVariable(method = "setScreenAndRender(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"), argsOnly = true)
   private Screen replaceQuitWorldTitleScreen(Screen screen) {
      if (screen instanceof com.mouseclient.util.time.WelcomeScreen) {
         return new MainMenuScreen(false);
      }
      return screen instanceof TitleScreen && !(screen instanceof MainMenuScreen) ? new MainMenuScreen(false) : screen;
   }
}
