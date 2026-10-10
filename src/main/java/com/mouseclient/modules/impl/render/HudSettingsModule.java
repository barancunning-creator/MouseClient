package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.player.UseTracker;
import com.mouseclient.util.time.TextSetting;
import com.mouseclient.util.time.ClickGuiTheme;
import com.mouseclient.util.time.ExtendedTargetHud;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;

@ModuleInfo(name = "Interface", description = "Какие элементы худа показывать.", category = ModuleCategory.RENDER)
public final class HudSettingsModule extends Module {
   private static HudSettingsModule INSTANCE;
   public ColorSetting theme;
   public MultiSelectSetting elements;
   public DecimalSetting cooldownSlotDimming;
   public BooleanSetting centered;
   public MultiSelectSetting components;
   public TextSetting watermarkOrder;
   public BooleanSetting showSpecialPotions;
   public BooleanSetting headHitParticles;
   public BooleanSetting moduleSounds;
   public DecimalSetting soundVolume;
   private int lastThemeArgb;
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isIncoming() && event.getPacket() instanceof PlaySoundS2CPacket soundPacket) {
         minecraftClient.execute(() -> {
            if (minecraftClient.player != null && minecraftClient.world != null && this.isEnabled()) {
               ExtendedTargetHud.observeItemSound(minecraftClient.player, soundPacket.getX(), soundPacket.getY(), soundPacket.getZ(), soundPacket.getSound().value().id().toString());
            }
         });
      }
   };

   private HudSettingsModule() {
      this.theme = this.addSetting(new ColorSetting(("Тема"), ("Акцентный цвет всего клиента.")).setArgb((-16723350)));
      this.elements = this.addSetting(
         new MultiSelectSetting(
            ("Элементы"),
            ("Какие элементы худа рисовать."),
            new String[]{("watermark"), ("keybinds"), ("serverbinds"), ("potions"), ("cooldowns"), ("staffs"), ("target"), ("inventory"), ("Target"), ("toasts")},
            ("watermark"),
            ("keybinds"),
            ("serverbinds"),
            ("potions"),
            ("cooldowns"),
            ("staffs"),
            ("target"),
            ("Target"),
            ("toasts")
         )
      );
      this.cooldownSlotDimming = this.addSetting(new DecimalSetting(("Затемнение слота кд"), ("Насколько затемнять слот хотбара под цифрой кулдауна."), (25.0D), 0.0, (100.0D), 1.0));
      this.cooldownSlotDimming.setVisibility(() -> this.elements.matchesOption(("cooldowns")));
      this.centered = this.addSetting(new BooleanSetting(("Отцентрировать"), ("Держать ватермарку по центру сверху и уступать боссбару."), false));
      this.centered.setVisibility(() -> false);
      this.components = this.addSetting(
         new MultiSelectSetting(("Компоненты"), ("Что показывать в ватермарке."), new String[]{("Аватарка"), ("Логин"), ("ФПС"), ("Пинг"), ("Координаты"), ("Время")}, ("Аватарка"), ("Логин"), ("ФПС"), ("Время"))
      );
      this.components.setVisibility(() -> false);
      this.watermarkOrder = this.addSetting(new TextSetting(("Порядок ватермарки"), ("Порядок компонентов ватермарки.")));
      this.watermarkOrder.setVisibility(() -> false);
      this.showSpecialPotions = this.addSetting(new BooleanSetting(("Показывать донатные зелья"), ("Показывать в зельях донатные зелья FunTime и SpookyTime вместо их эффектов."), true));
      this.showSpecialPotions.setVisibility(() -> false);
      this.headHitParticles = this.addSetting(new BooleanSetting(("Партиклы из головы при ударе"), ("При ударе по цели из её головы в TargetHud вылетают светящиеся частицы."), true));
      this.headHitParticles.setVisibility(() -> false);
      this.moduleSounds = this.addSetting(new BooleanSetting(("Звуки модулей"), ("Проигрывать звук при включении и выключении модуля."), true));
      this.soundVolume = this.addSetting(new DecimalSetting(("Громкость звуков"), ("Громкость звуков модулей в процентах."), (12.0D), 0.0, (100.0D), 1.0));
      this.soundVolume.setVisibility(() -> this.moduleSounds.getValue());
   }

   public boolean isElementVisible(String element) {
      return element.equals(("arrows"))
         ? UseTracker.getInstance().isEnabled() && UseTracker.getInstance().arrowCounter.getValue()
         : this.isEnabled() && this.elements.matchesOption(element);
   }

   public static HudSettingsModule getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new HudSettingsModule();
      }

      return INSTANCE;
   }

   public void syncThemeColor() {
      int themeArgb = (-16777216) | this.theme.getArgb() & (16777215);
      if (themeArgb != this.lastThemeArgb) {
         this.lastThemeArgb = themeArgb;
         ClickGuiTheme.updateAccent(themeArgb);
      }
   }

   public boolean isWatermarkCentered() {
      return this.isEnabled() && this.centered.getValue();
   }
}
