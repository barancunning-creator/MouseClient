package com.mouseclient.util.time;

import com.mouseclient.modules.impl.player.ServerHelper;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public enum SpecialPotion {
   CRACKER(
      "${rgba(255,20,147,0)}[${rgba(231,41,152,0)}★${rgba(208,62,157,0)}]${rgba(185,84,162,0)} ${rgba(162,105,167,0)}Х${rgba(139,126,172,0)}л${rgba(115,148,178,0)}о${rgba(92,169,183,0)}п${rgba(69,190,188,0)}у${rgba(46,212,193,0)}ш${rgba(23,233,198,0)}к${rgba(0,255,204,0)}а",
      16738740,
      new SpecialPotion.SpecialPotionEffect(StatusEffects.SLOWNESS, 200, 9),
      new SpecialPotion.SpecialPotionEffect(StatusEffects.BLINDNESS, 100, 9)
   ),
   RADIATION(
      "${rgba(255,247,58,0)}[${rgba(248,247,58,0)}★${rgba(242,247,58,0)}]${rgba(235,248,58,0)} ${rgba(229,248,58,0)}З${rgba(223,249,59,0)}е${rgba(216,249,59,0)}л${rgba(210,250,59,0)}ь${rgba(204,250,59,0)}е${rgba(197,251,60,0)} ${rgba(191,251,60,0)}Р${rgba(185,252,60,0)}а${rgba(178,252,60,0)}д${rgba(172,253,61,0)}и${rgba(166,253,61,0)}а${rgba(159,254,61,0)}ц${rgba(153,254,61,0)}и${rgba(147,255,62,0)}и",
      3329330,
      new SpecialPotion.SpecialPotionEffect(StatusEffects.POISON, 1200, 1),
      new SpecialPotion.SpecialPotionEffect(StatusEffects.WITHER, 1200, 1),
      new SpecialPotion.SpecialPotionEffect(StatusEffects.SLOWNESS, 1800, 2)
   ),
   SLEEPING(
      "${rgba(63,13,154,0)}[${rgba(66,23,160,0)}★${rgba(69,33,166,0)}]${rgba(72,43,172,0)} ${rgba(75,53,178,0)}С${rgba(79,64,184,0)}н${rgba(82,74,190,0)}о${rgba(85,84,196,0)}т${rgba(88,94,202,0)}в${rgba(92,105,208,0)}о${rgba(95,115,214,0)}р${rgba(98,125,220,0)}н${rgba(101,135,226,0)}о${rgba(105,146,233,0)}е",
      254,
      new SpecialPotion.SpecialPotionEffect(StatusEffects.WEAKNESS, 1800, 1),
      new SpecialPotion.SpecialPotionEffect(StatusEffects.MINING_FATIGUE, 200, 1),
      new SpecialPotion.SpecialPotionEffect(StatusEffects.WITHER, 1800, 2),
      new SpecialPotion.SpecialPotionEffect(StatusEffects.BLINDNESS, 200, 0)
   ),
   WRATH(
      "${rgba(139,0,0,0)}[${rgba(147,10,0,0)}★${rgba(155,20,0,0)}]${rgba(163,30,0,0)} ${rgba(172,40,0,0)}З${rgba(180,50,0,0)}е${rgba(188,60,0,0)}л${rgba(197,70,0,0)}ь${rgba(205,80,0,0)}е${rgba(213,89,0,0)} ${rgba(221,100,0,0)}Г${rgba(230,109,0,0)}н${rgba(238,120,0,0)}е${rgba(246,129,0,0)}в${rgba(255,140,0,0)}а",
      10040115,
      new SpecialPotion.SpecialPotionEffect(StatusEffects.STRENGTH, 600, 4),
      new SpecialPotion.SpecialPotionEffect(StatusEffects.SLOWNESS, 600, 3)
   ),
   ASSASSIN(
      "${rgba(65,67,69,0)}[${rgba(68,63,64,0)}★${rgba(72,59,60,0)}]${rgba(76,55,56,0)} ${rgba(79,51,52,0)}З${rgba(83,47,48,0)}е${rgba(87,43,44,0)}л${rgba(90,39,40,0)}ь${rgba(94,35,36,0)}е${rgba(98,31,32,0)} ${rgba(102,27,28,0)}А${rgba(105,23,24,0)}с${rgba(109,19,20,0)}с${rgba(113,15,16,0)}а${rgba(116,11,12,0)}с${rgba(120,7,8,0)}и${rgba(124,3,4,0)}н${rgba(128,0,0,0)}а",
      3355443,
      new SpecialPotion.SpecialPotionEffect(StatusEffects.STRENGTH, 1200, 3)
   ),
   PALADIN(
      "${rgba(209,255,91,0)}[${rgba(209,255,91,0)}★${rgba(209,255,91,0)}]${rgba(209,255,91,0)} ${rgba(209,255,91,0)}З${rgba(209,255,91,0)}е${rgba(209,255,91,0)}л${rgba(209,255,91,0)}ь${rgba(209,255,91,0)}е${rgba(209,255,91,0)} ${rgba(209,255,91,0)}П${rgba(209,255,91,0)}а${rgba(209,255,91,0)}л${rgba(209,255,91,0)}л${rgba(209,255,91,0)}а${rgba(209,255,91,0)}д${rgba(209,255,91,0)}и${rgba(209,255,91,0)}н${rgba(209,255,91,0)}а",
      65535,
      new SpecialPotion.SpecialPotionEffect(StatusEffects.HEALTH_BOOST, 1200, 2)
   ),
   HOLY_WATER(
      "${rgba(255,255,203,0)}[${rgba(255,254,192,0)}★${rgba(255,254,181,0)}]${rgba(255,254,170,0)} ${rgba(255,254,159,0)}С${rgba(255,254,148,0)}в${rgba(255,254,137,0)}я${rgba(255,254,126,0)}т${rgba(255,254,115,0)}а${rgba(255,254,104,0)}я${rgba(255,254,93,0)} ${rgba(255,254,82,0)}в${rgba(255,254,71,0)}о${rgba(255,254,60,0)}д${rgba(255,254,50,0)}а",
      16777215,
      new SpecialPotion.SpecialPotionEffect(StatusEffects.REGENERATION, 900, 1)
   );

   private final int colorRgb;
   private final List<SpecialPotion.SpecialPotionEffect> effects;
   private final Text fullName;
   private final Text shortName;
   private final String plainName;

   SpecialPotion(String encodedName, int colorRgb, SpecialPotion.SpecialPotionEffect... effects) {
      this.colorRgb = colorRgb;
      this.effects = List.of(effects);
      this.fullName = parseStyledName(encodedName, 0);
      this.shortName = parseStyledName(encodedName, 4);
      this.plainName = this.fullName.getString();
   }

   public int colorRgb() {
      return this.colorRgb;
   }

   public Text shortName() {
      return this.shortName;
   }

   public static SpecialPotion fromName(String name) {
      for (SpecialPotion potion : values()) {
         if (potion.shortName.getString().equalsIgnoreCase(name.trim())) {
            return potion;
         }
      }

      return null;
   }

   public List<SpecialPotion.SpecialPotionEffect> effects() {
      return this.effects;
   }

   public Text fullName() {
      return this.fullName;
   }

   public String plainName() {
      return this.plainName;
   }

   public int splashDurationTicks(double factor) {
      if (factor <= 0.0) {
         return 0;
      }

      int maximumDuration = 0;

      for (SpecialPotion.SpecialPotionEffect effectSpec : this.effects) {
         int durationTicks = MathHelper.floor(effectSpec.ticks() * factor + 0.5);
         if (durationTicks > 20) {
            maximumDuration = Math.max(maximumDuration, durationTicks);
         }
      }

      return maximumDuration;
   }

   public static SpecialPotion fromColor(int color) {
      int colorRgb = color & 16777215;

      for (SpecialPotion potion : values()) {
         if (potion.colorRgb == colorRgb) {
            return potion;
         }
      }

      int red = colorRgb >> 16 & 0xFF;
      int green = colorRgb >> 8 & 0xFF;
      int blue = colorRgb & 0xFF;
      boolean assassinGray = Math.abs(red - green) <= 10 && Math.abs(green - blue) <= 10 && Math.abs(red - blue) <= 10 && red >= 14 && red <= 88;
      return assassinGray ? ASSASSIN : null;
   }

   public static SpecialPotion fromNearbyPotion(BlockPos splashPosition) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world == null) {
         return null;
      }

      for (PotionEntity potionEntity : client.world.getNonSpectatingEntities(PotionEntity.class, new Box(splashPosition).expand(3.0))) {
         String potionName = Formatting.strip(potionEntity.getStack().getName().getString()).toLowerCase();

         for (SpecialPotion potion : values()) {
            if (potionName.contains(potion.shortName.getString().toLowerCase())) {
               return potion;
            }
         }
      }

      return null;
   }

   public static boolean amplifierMatches(int actual, int expected) {
      return Math.abs(actual - expected) <= 1;
   }

   public static double splashFactor(Box bounds, Vec3d splashPosition) {
      return 1.0 - Math.sqrt(bounds.squaredMagnitude(splashPosition)) / 4.0;
   }

   public static boolean isTrackingEnabled() {
      ServerHelper serverHelper = ServerHelper.getInstance();
      return serverHelper.isEnabled() && (serverHelper.server.isSelected("FunTime") || serverHelper.server.isSelected("SpookyTime"));
   }

   private static MutableText parseStyledName(String encodedName, int skipSegments) {
      MutableText name = Text.empty();
      Matcher segments = Pattern.compile("\\$\\{rgba\\((\\d+),(\\d+),(\\d+),(\\d+)\\)\\}([^$]*)").matcher(encodedName);
      int segmentIndex = 0;

      while (segments.find()) {
         if (segmentIndex++ >= skipSegments) {
            name.append(
               Text.literal(segments.group(5))
                  .setStyle(
                     Style.EMPTY
                        .withColor(
                           TextColor.fromRgb(Integer.parseInt(segments.group(1)) << 16 | Integer.parseInt(segments.group(2)) << 8 | Integer.parseInt(segments.group(3)))
                        )
                  )
            );
         }
      }

      return name;
   }

   public record SpecialPotionEffect(RegistryEntry<StatusEffect> effect, int ticks, int amplifier) {
   }
}
