package com.mouseclient.mixin;

import com.mouseclient.internal.ClientWindow;
import net.minecraft.client.WindowEventHandler;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.util.MonitorTracker;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
   @Shadow
   @Final
   private long handle;

   @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private static String kimiko$forceInitialTitle(String fullscreenVideoMode) {
      return ClientWindow.title();
   }

   @ModifyVariable(method = "setTitle(Ljava/lang/String;)V", at = @At("HEAD"), argsOnly = true)
   private String kimiko$forceTitle(String title) {
      return ClientWindow.title();
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void kimiko$applyDarkTitleBar(WindowEventHandler eventHandler, MonitorTracker monitorTracker, WindowSettings settings, String fullscreenVideoMode, String title, CallbackInfo callback) {
      ClientWindow.applyDarkTitleBar(this.handle);
   }
}
