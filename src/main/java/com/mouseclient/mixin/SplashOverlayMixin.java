package com.mouseclient.mixin;

import com.mouseclient.gui.Render2D;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.util.time.GuiVisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
   @Shadow
   @Final
   private MinecraftClient client;
   @Shadow
   @Final
   private boolean reloading;
   @Shadow
   private long reloadCompleteTime;
   @Shadow
   private long reloadStartTime;
   @Unique
   private long mouseclient$shownAtMs;
   @Unique
   private boolean mouseclient$sceneMode;

   @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
   private void renderMouseClientOverlay(DrawContext context, int mouseX, int mouseY, float tickDelta, CallbackInfo callback) {
      long reloadStartTime = Util.getMeasuringTimeMs();
      if (this.reloading && this.reloadStartTime == (-1L)) {
         this.reloadStartTime = reloadStartTime;
      }

      if (this.mouseclient$shownAtMs == 0L) {
         this.mouseclient$shownAtMs = System.currentTimeMillis();
         this.mouseclient$sceneMode = GuiVisuals.loadingSceneEnabled;
      }

      float completionProgress = this.reloadCompleteTime > (-1L) ? (float)(reloadStartTime - this.reloadCompleteTime) / (1000.0F) : (-1.0F);
      float startProgress = this.reloadStartTime > (-1L) ? (float)(reloadStartTime - this.reloadStartTime) / (500.0F) : (-1.0F);
      float opacity;
      if (completionProgress >= 1.0F) {
         if (this.client.currentScreen != null) {
            this.client.currentScreen.renderWithTooltip(context, 0, 0, tickDelta);
         } else {
            this.client.inGameHud.renderDeferredSubtitles();
         }

         opacity = 1.0F - MathHelper.clamp(completionProgress - 1.0F, 0.0F, 1.0F);
         context.createNewRootLayer();
         context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), MathHelper.ceil(opacity * (255.0F)) << 24 | (1381139));
      } else if (this.reloading) {
         if (this.client.currentScreen != null && startProgress < 1.0F) {
            this.client.currentScreen.renderWithTooltip(context, mouseX, mouseY, tickDelta);
         } else {
            this.client.inGameHud.renderDeferredSubtitles();
         }

         opacity = MathHelper.clamp(startProgress, 0.0F, 1.0F);
         context.createNewRootLayer();
         context.fill(
            0,
            0,
            context.getScaledWindowWidth(),
            context.getScaledWindowHeight(),
            MathHelper.ceil(MathHelper.clamp(startProgress, (0.15F), 1.0F) * (255.0F)) << 24 | (1381139)
         );
      } else {
         opacity = 1.0F;
         context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), (-15396077));
      }

      if (this.mouseclient$sceneMode) {
         this.mouseclient$drawLoaderScene(context, opacity);
      }

      if (completionProgress >= 2.0F) {
         this.client.setOverlay(null);
      }

      callback.cancel();
   }

   private void mouseclient$drawLoaderScene(DrawContext context, float multiplier) {
      HudSettingsModule.getInstance().syncThemeColor();
      float coordinateScale = GuiScale.getWindowScale() / GuiScale.getBaseScale();
      float screenWidth = context.getScaledWindowWidth() * coordinateScale;
      float screenHeight = context.getScaledWindowHeight() * coordinateScale;
      Render2D.begin(context);
      Render2D.pushAlpha(multiplier);
      GuiVisuals.drawCoverBackground(("assets/mouseclient/textures/gui/mainmenu/background/loading.png"), screenWidth, screenHeight, 1.0F);
      GuiVisuals.drawLoadingScene(this.mouseclient$shownAtMs, screenWidth, screenHeight);
      Render2D.popAlpha();
      Render2D.end();
   }

   static {
      VMBridge.identifyClass(SplashOverlayMixin.class, "EXanmR3J");
   }
}
