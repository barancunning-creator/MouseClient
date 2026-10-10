package com.mouseclient.mixin;

import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.pipeline.GuiLayerTracker;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.ItemGuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin implements GuiLayerTracker {
   @Unique
   private int layerSerial;
   @Shadow
   private int blurLayer;

   @Override
   public int getLayerSerial() {
      return this.layerSerial;
   }

   @Override
   public void bumpLayerSerial() {
      this.layerSerial++;
   }

   @Inject(method = "createNewRootLayer()V", at = @At("RETURN"))
   private void trackNextStratum(CallbackInfo callback) {
      this.layerSerial++;
   }

   @Inject(method = "goUpLayer()V", at = @At("RETURN"))
   private void trackUpLayer(CallbackInfo callback) {
      this.layerSerial++;
   }

   @Inject(method = "clear()V", at = @At("HEAD"))
   private void resetLayerSerial(CallbackInfo callback) {
      this.layerSerial = 0;
   }

   @Inject(method = "applyBlur()V", at = @At("HEAD"), cancellable = true)
   private void skipSecondBlur(CallbackInfo callback) {
      if (this.blurLayer != (2147483647)) {
         callback.cancel();
      }
   }

   @Inject(method = "addItem(Lnet/minecraft/client/gui/render/state/ItemGuiElementRenderState;)V", at = @At("HEAD"))
   private void trackSpecialItemTint(ItemGuiElementRenderState state, CallbackInfo callback) {
      GuiItemRenderer.captureFallbackColor(state);
   }

   static {
      VMBridge.identifyClass(GuiRenderStateMixin.class, "dxbGAceV");
   }
}
