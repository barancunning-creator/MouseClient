package com.mouseclient.modules.impl.fun;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.ClientTickEvent;
import com.mouseclient.util.time.ActionSetting;
import com.mouseclient.util.time.ChoiceSetting;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class FunModules {
   private FunModules() {
   }

   public static void registerAll() {
      DerpHead.getInstance();
      Twerk.getInstance();
      DiscoSky.getInstance();
      JokeRain.getInstance();
      PartyPopper.getInstance();
      Giggle.getInstance();
   }

   @ModuleInfo(name = "Derp", description = "Голова мотается в стороны. Видно от третьего лица.", category = ModuleCategory.FUN)
   public static final class DerpHead extends Module {
      private static DerpHead instance;
      public DecimalSetting speed = this.addSetting(new DecimalSetting("Скорость", "Как быстро мотать головой.", 8.0, 1.0, 20.0, 1.0));
      public DecimalSetting range = this.addSetting(new DecimalSetting("Размах", "Насколько сильно вертеть.", 55.0, 10.0, 90.0, 5.0));
      private final EventListener<ClientTickEvent> tick = event -> {
         if (!event.isPost() || minecraftClient.player == null) {
            return;
         }
         float wave = (float)Math.sin(minecraftClient.player.age * this.speed.floatValue() * 0.15F) * this.range.floatValue();
         minecraftClient.player.lastHeadYaw = minecraftClient.player.headYaw;
         minecraftClient.player.headYaw = minecraftClient.player.getYaw() + wave;
         minecraftClient.player.lastBodyYaw = minecraftClient.player.bodyYaw;
         minecraftClient.player.bodyYaw = minecraftClient.player.getYaw() + wave * 0.35F;
      };

      public static DerpHead getInstance() {
         if (instance == null) {
            instance = new DerpHead();
         }
         return instance;
      }
   }

   @ModuleInfo(name = "Twerk", description = "Быстро приседает и встаёт.", category = ModuleCategory.FUN)
   public static final class Twerk extends Module {
      private static Twerk instance;
      public DecimalSetting tempo = this.addSetting(new DecimalSetting("Темп", "Как часто приседать.", 3.0, 1.0, 8.0, 1.0));
      private final EventListener<ClientTickEvent> tick = event -> {
         if (!event.isPost() || minecraftClient.player == null) {
            return;
         }
         int step = Math.max(1, this.tempo.intValue());
         minecraftClient.player.setSneaking((minecraftClient.player.age / step) % 2 == 0);
      };

      @Override
      protected void onDisable() {
         if (minecraftClient.player != null) {
            minecraftClient.player.setSneaking(false);
         }
      }

      public static Twerk getInstance() {
         if (instance == null) {
            instance = new Twerk();
         }
         return instance;
      }
   }

   @ModuleInfo(name = "DiscoSky", description = "Клиентское небо быстро меняет время суток.", category = ModuleCategory.FUN)
   public static final class DiscoSky extends Module {
      private static DiscoSky instance;
      public static long visualTime = -1L;
      public DecimalSetting speed = this.addSetting(new DecimalSetting("Скорость", "Насколько быстро крутить сутки.", 240.0, 40.0, 800.0, 20.0));
      private final EventListener<ClientTickEvent> tick = event -> {
         if (!event.isPost() || minecraftClient.world == null) {
            return;
         }
         long time = (System.currentTimeMillis() / 50L) * (long)this.speed.floatValue();
         visualTime = time % 24000L;
      };

      @Override
      protected void onDisable() {
         visualTime = -1L;
      }

      public static DiscoSky getInstance() {
         if (instance == null) {
            instance = new DiscoSky();
         }
         return instance;
      }
   }

   @ModuleInfo(name = "JokeRain", description = "Включает дождь только у тебя на клиенте.", category = ModuleCategory.FUN)
   public static final class JokeRain extends Module {
      private static JokeRain instance;
      public BooleanSetting thunder = this.addSetting(new BooleanSetting("Гроза", "Добавить гром и молнии на клиенте.", true));
      private final EventListener<ClientTickEvent> tick = event -> {
         if (!event.isPost() || minecraftClient.world == null) {
            return;
         }
         minecraftClient.world.setRainGradient(1.0F);
         minecraftClient.world.setThunderGradient(this.thunder.getValue() ? 1.0F : 0.0F);
      };

      @Override
      protected void onDisable() {
         if (minecraftClient.world != null) {
            minecraftClient.world.setRainGradient(0.0F);
            minecraftClient.world.setThunderGradient(0.0F);
         }
      }

      public static JokeRain getInstance() {
         if (instance == null) {
            instance = new JokeRain();
         }
         return instance;
      }
   }

   @ModuleInfo(name = "Party", description = "Сердечки, ноты и хлопушка вокруг тебя.", category = ModuleCategory.FUN)
   public static final class PartyPopper extends Module {
      private static PartyPopper instance;
      public ChoiceSetting style = this.addSetting(new ChoiceSetting("Стиль", "Какие частицы сыпать.").withOptions("Сердечки", "Ноты", "Хлопушка", "Всё сразу"));
      private final EventListener<ClientTickEvent> tick = event -> {
         if (!event.isPost() || minecraftClient.player == null || minecraftClient.world == null || minecraftClient.player.age % 2 != 0) {
            return;
         }
         double x = minecraftClient.player.getX();
         double y = minecraftClient.player.getY() + 1.1;
         double z = minecraftClient.player.getZ();
         String selected = this.style.getSelectedOption();
         if (selected.equals("Сердечки") || selected.equals("Всё сразу")) {
            minecraftClient.world.addParticleClient(ParticleTypes.HEART, x, y, z, 0.2, 0.3, 0.0);
         }
         if (selected.equals("Ноты") || selected.equals("Всё сразу")) {
            minecraftClient.world.addParticleClient(ParticleTypes.NOTE, x, y + 0.4, z, minecraftClient.player.age % 24 / 24.0, 0.0, 0.0);
         }
         if (selected.equals("Хлопушка") || selected.equals("Всё сразу")) {
            minecraftClient.world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 0.0, 0.2, 0.0);
            minecraftClient.world.addParticleClient(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0.1, 0.2, 0.1);
         }
      };

      public static PartyPopper getInstance() {
         if (instance == null) {
            instance = new PartyPopper();
         }
         return instance;
      }
   }

   @ModuleInfo(name = "Giggle", description = "Кнопка говорит одну случайную шутку в чат.", category = ModuleCategory.FUN)
   public static final class Giggle extends Module {
      private static final String[] LINES = {
         "я не отсталый, я просто в креативе по жизни",
         "мой пинг ушёл за хлебом и не вернулся",
         "если я упал — это был паркур",
         "крипер не взорвался, мы просто обнялись",
         "инвентарь пустой, зато душа полная",
         "я афк, но морально на месте"
      };
      private static Giggle instance;
      public ActionSetting say = this.addSetting(new ActionSetting("Сказать шутку", "Отправить одну случайную фразу в чат.").withLabel("Сказать").withAction(this::sayJoke));
      public BooleanSetting cluck = this.addSetting(new BooleanSetting("Кудахтанье", "Иногда кудахтать курицей.", true));
      private int nextCluck;
      private final EventListener<ClientTickEvent> tick = event -> {
         if (!event.isPost() || minecraftClient.player == null || !this.cluck.getValue()) {
            return;
         }
         if (minecraftClient.player.age < this.nextCluck) {
            return;
         }
         this.nextCluck = minecraftClient.player.age + 80 + minecraftClient.player.getRandom().nextInt(80);
         minecraftClient.player.playSound(SoundEvents.ENTITY_CHICKEN_AMBIENT, 0.7F, 1.15F + minecraftClient.player.getRandom().nextFloat() * 0.4F);
      };

      private void sayJoke() {
         if (minecraftClient.player == null || minecraftClient.player.networkHandler == null) {
            return;
         }
         String line = LINES[minecraftClient.player.getRandom().nextInt(LINES.length)];
         minecraftClient.player.networkHandler.sendChatMessage(line);
         minecraftClient.player.sendMessage(Text.literal("Шутка улетела в чат"), true);
      }

      public static Giggle getInstance() {
         if (instance == null) {
            instance = new Giggle();
         }
         return instance;
      }
   }
}
