package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;

@ModuleInfo(
   name = "NoServerPack",
   description = "Блокирует подкачку и включение серверного ресурспака, отвечая серверу что пак загружен.",
   category = ModuleCategory.COMBAT
)
public final class NoServerPack extends Module {
   private static NoServerPack INSTANCE;

   @Override
   protected void onEnable() {
      minecraftClient.getServerResourcePackProvider().removeAll();
   }

   public static NoServerPack getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new NoServerPack();
      }

      return INSTANCE;
   }

   private NoServerPack() {
   }
}
