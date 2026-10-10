package com.mouseclient.modules.impl.player;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;

@ModuleInfo(name = "NoPush", description = "Не даёт выбранным источникам толкать игрока.", category = ModuleCategory.PLAYER)
public final class NoPush extends Module {
   private static NoPush INSTANCE;
   public final MultiSelectSetting pushSources;

   public static NoPush getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new NoPush();
      }

      return INSTANCE;
   }

   private NoPush() {
      this.pushSources = this.addSetting(new MultiSelectSetting(("Толчки от"), ("Что не может толкать игрока."), new String[]{("Вода"), ("Лава"), ("Блоки"), ("Сущности"), ("Граница мира")}, ("Вода"), ("Сущности")));
   }

   public boolean blocksSource(String source) {
      return this.isEnabled() && this.pushSources.matchesOption(source);
   }
}
