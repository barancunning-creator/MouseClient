package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.events.impl.SharedHitBoxEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.render.pipeline.SliderSetting;

@ModuleInfo(name = "HitBoxes", description = "Раздувает хитбоксы сущностей, по которым попадают удары.", category = ModuleCategory.COMBAT)
public final class HitBoxes extends Module {
   private static HitBoxes INSTANCE;
   public SliderSetting boxSize;
   public BooleanSetting showDebugHitboxes;
   public boolean debugRenderPass;
   public final EventListener<SharedHitBoxEvent> sharedHitBoxListener = event -> {
      if (!this.debugRenderPass || this.showDebugHitboxes.getValue()) {
         if (event.getExpansion() < this.boxSize.floatValue()) {
            event.setExpansion(this.boxSize.floatValue());
         }
      }
   };

   private HitBoxes() {
      this.boxSize = this.addSetting(new SliderSetting(("Установите размер бокса"), ("На сколько блоков раздуть бокс по горизонтали.")).withRange(0.0F, (5.0F)).withStep((0.1F)).setValue((0.3F)));
      this.showDebugHitboxes = this.addSetting(new BooleanSetting(("Отображать в F3+B"), ("Рисовать раздутый бокс в дебаг-рендере хитбоксов."), true));
   }

   public static HitBoxes getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new HitBoxes();
      }

      return INSTANCE;
   }
}
