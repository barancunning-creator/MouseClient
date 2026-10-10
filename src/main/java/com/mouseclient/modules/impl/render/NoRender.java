package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.sound.SoundCategory;

@ModuleInfo(name = "NoRender", description = "Отключение назойливых визуальных эффектов и звуков.", category = ModuleCategory.RENDER)
public final class NoRender extends Module {
   private static NoRender INSTANCE;
   public MultiSelectSetting overlays;
   public MultiSelectSetting sounds;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && this.isOverlayDisabled(("Эффекты вардена"))) {
         minecraftClient.player.removeStatusEffect(StatusEffects.DARKNESS);
      }
   };

   public boolean shouldMuteSound(SoundInstance sound) {
      if (!this.isEnabled()) {
         return false;
      } else {
         String soundPath = sound.getId().getPath();
         if (this.sounds.matchesOption(("Пластинки")) && sound.getCategory() == SoundCategory.RECORDS) {
            return true;
         } else if (this.sounds.matchesOption(("Портал")) && soundPath.startsWith(("block.portal."))) {
            return true;
         } else if (this.sounds.matchesOption(("Иссушитель")) && soundPath.startsWith(("entity.wither."))) {
            return true;
         } else if (this.sounds.matchesOption(("Варден")) && soundPath.startsWith(("entity.warden."))) {
            return true;
         } else if (this.sounds.matchesOption(("Шипы")) && soundPath.equals(("enchant.thorns.hit"))) {
            return true;
         } else {
            return !this.sounds.matchesOption(("Опыт"))
                  || !soundPath.equals(("entity.experience_orb.pickup"))
                  || minecraftClient.player != null
                     && !(
                        (sound.getX() - minecraftClient.player.getX()) * (sound.getX() - minecraftClient.player.getX())
                              + (sound.getY() - minecraftClient.player.getY()) * (sound.getY() - minecraftClient.player.getY())
                              + (sound.getZ() - minecraftClient.player.getZ()) * (sound.getZ() - minecraftClient.player.getZ())
                           > (4.0D)
                     )
               ? this.sounds.matchesOption(("Копание")) && soundPath.startsWith(("block.")) && (soundPath.endsWith((".hit")) || soundPath.endsWith((".break")))
               : true;
         }
      }
   }

   public static NoRender getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new NoRender();
      }

      return INSTANCE;
   }

   private NoRender() {
      this.addSetting(new SettingSeparator(("Отключение оверлея")));
      this.overlays = this.addSetting(
         new MultiSelectSetting(
            ("Оверлеи"),
            ("Какие эффекты отключать."),
            new String[]{
               ("Темнота"),
               ("Туман слепоты"),
               ("Тряска экрана"),
               ("Камера при уроне"),
               ("Эффекты FOV"),
               ("Оверлей лавы"),
               ("Оверлей огня"),
               ("Оверлей воды"),
               ("Тыква на голове"),
               ("Затемнение блоков"),
               ("Иссушающие сердца"),
               ("Эффекты вардена"),
               ("Индикатор атаки"),
               ("Скорборд"),
               ("Анимация тотема")
            },
            ("Темнота"),
            ("Тряска экрана")
         )
      );
      this.addSetting(new SettingSeparator(("Отключение звуков")));
      this.sounds = this.addSetting(
         new MultiSelectSetting(("Звуки"), ("Какие звуки заглушать."), new String[]{("Опыт"), ("Пластинки"), ("Портал"), ("Иссушитель"), ("Шипы"), ("Копание"), ("Варден")}, ("Опыт"), ("Портал"))
      );
   }

   public boolean isOverlayDisabled(String overlay) {
      return this.isEnabled() && this.overlays.matchesOption(overlay);
   }
}
