package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;

@ModuleInfo(
   name = "NoEntityTrace",
   description = "Убирает хитбокс у всех сущностей в мире позволяя вам взаимодействовать через них.",
   category = ModuleCategory.COMBAT
)
public final class NoEntityTrace extends Module {
   private static NoEntityTrace INSTANCE;

   private NoEntityTrace() {
   }

   public static NoEntityTrace getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new NoEntityTrace();
      }

      return INSTANCE;
   }
}
