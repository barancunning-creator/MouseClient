package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;

@ModuleInfo(name = "SeeInvisible", description = "Показывает невидимых игроков и мобов с настраиваемой прозрачностью.", category = ModuleCategory.RENDER)
public final class SeeInvisible extends Module {
   private static SeeInvisible INSTANCE;
   public DecimalSetting opacity;

   public static SeeInvisible getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new SeeInvisible();
      }

      return INSTANCE;
   }

   private SeeInvisible() {
      this.opacity = this.addSetting(new DecimalSetting(("Прозрачность"), ("Насколько плотно рисовать невидимых."), (0.55D), (0.05D), 1.0, (0.05D)));
   }
}
