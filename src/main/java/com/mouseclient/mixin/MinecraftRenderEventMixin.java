package com.mouseclient.mixin;

import com.mouseclient.events.impl.RenderStepEvent;
import com.mouseclient.modules.impl.misc.EventBus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(MinecraftClient.class)
public class MinecraftRenderEventMixin {
   @Unique
   private static final long STEP_NANOS = 4166666L;
   @Unique
   private long lastHookTime;

   @Inject(method = "render(Z)V", at = @At("HEAD"))
   private void postAccumulatedRender(boolean tick, CallbackInfo callback) {
      long lastHookTime = Util.getMeasuringTimeNano();
      if (this.lastHookTime == 0L) {
         this.lastHookTime = lastHookTime;
      }

      long elapsedSteps = (lastHookTime - this.lastHookTime) / (4166666L);
      this.lastHookTime = this.lastHookTime + elapsedSteps * (4166666L);
      int stepCount = (int)Math.min(elapsedSteps, (240L));
      if (stepCount != 0 && EventBus.getInstance().hasListeners(RenderStepEvent.class)) {
         float tickProgress = ((MinecraftClient)(Object)this).getRenderTickCounter().getTickProgress(true);

         for (int stepIndex = 0; stepIndex < stepCount; stepIndex++) {
            EventBus.getInstance().post(new RenderStepEvent(stepIndex, stepCount, Math.min(1.0F, tickProgress + (stepIndex + 1) / (12.0F))));
         }
      }
   }

   static {
      VMBridge.identifyClass(MinecraftRenderEventMixin.class, "r2WM85Zf");
   }
}
