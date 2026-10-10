package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.movement.AspectRatioEvent;
import com.mouseclient.render.pipeline.SliderSetting;

@ModuleInfo(name = "AspectRatio", description = "Меняет соотношение сторон картинки мира с плавной анимацией перехода.", category = ModuleCategory.RENDER)
public final class AspectRatio extends Module {
   private static AspectRatio INSTANCE;
   public ModeSetting aspectRatio;
   public SliderSetting customAspectRatio;
   private float currentAspectRatio;
   private long lastUpdateMillis;
   private final EventListener<AspectRatioEvent> aspectRatioListener = event -> {
      if (minecraftClient.getWindow() != null) {
         float windowAspectRatio = (float)minecraftClient.getWindow().getFramebufferWidth() / Math.max(1, minecraftClient.getWindow().getFramebufferHeight());

         float targetAspectRatio = switch (this.aspectRatio.getSelectedOption()) {
            case ("21:9") -> (2.3333333F);
            case ("16:9") -> (1.7777778F);
            case ("16:10") -> (1.6F);
            case ("5:4") -> (1.25F);
            case ("1:1") -> 1.0F;
            case ("9:16") -> (0.5625F);
            case ("Кастом") -> this.customAspectRatio.floatValue();
            default -> (1.3333334F);
         };
         long nowMillis = System.currentTimeMillis();
         if (this.currentAspectRatio == 0.0F) {
            this.currentAspectRatio = windowAspectRatio;
         }

         this.currentAspectRatio = this.currentAspectRatio + (targetAspectRatio - this.currentAspectRatio) * (1.0F - (float)Math.exp(-Math.min((100L), nowMillis - this.lastUpdateMillis) / (160.0D)));
         this.lastUpdateMillis = nowMillis;
         event.setAspectRatio(this.currentAspectRatio);
         event.setCancelled(true);
      }
   };

   @Override
   protected void onEnable() {
      this.currentAspectRatio = 0.0F;
      this.lastUpdateMillis = System.currentTimeMillis();
   }

   public int projectionWidth(int originalWidth) {
      return this.isEnabled() && !(this.currentAspectRatio <= 0.0F) && minecraftClient.getWindow() != null
         ? Math.max(1, Math.round(minecraftClient.getWindow().getFramebufferHeight() * this.currentAspectRatio))
         : originalWidth;
   }

   private AspectRatio() {
      this.aspectRatio = this.addSetting(
         new ModeSetting(("Соотношение"), ("Готовые пресеты соотношения сторон."), ("4:3"), ("21:9"), ("16:9"), ("16:10"), ("4:3"), ("5:4"), ("1:1"), ("9:16"), ("Кастом"))
      );
      this.customAspectRatio = this.addSetting(
         new SliderSetting(("Своё соотношение"), ("Ширина к высоте для пресета Кастом.")).withRange((0.4F), (3.2F)).withStep((0.01F)).setValue((1.78F)).withSuffix((":1"))
      );
   }

   public static AspectRatio getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AspectRatio();
      }

      return INSTANCE;
   }
}
