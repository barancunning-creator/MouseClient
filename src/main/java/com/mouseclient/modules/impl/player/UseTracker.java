package com.mouseclient.modules.impl.player;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.events.impl.PotionSplashEvent;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.SettingSeparator;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.time.ToastNotifications;
import com.mouseclient.util.time.GuiVisuals;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

@ModuleInfo(
   name = "UseTracker",
   description = "Уведомляет о сломанных тотемах, использованных предметах, кулдаунах и специальных зельях.",
   category = ModuleCategory.PLAYER
)
public final class UseTracker extends Module {
   private static UseTracker INSTANCE;
   public BooleanSetting brokenTotems;
   public BooleanSetting usedItems;
   public BooleanSetting cooldownFinished;
   public BooleanSetting specialPotions;
   public BooleanSetting expiringEffects;
   public BooleanSetting arrowCounter;
   public BooleanSetting showInChat;
   public BooleanSetting showNotifications;
   private final HashSet<Integer> itemUseNotifiedPlayers = new HashSet<>();
   private final HashMap<String, ItemStack> cooldownItems = new HashMap<>();
   private final HashMap<String, Long> cooldownStartTimes = new HashMap<>();
   private final HashMap<RegistryEntry<StatusEffect>, StatusEffectInstance> previousEffects = new HashMap<>();
   private final String assassinPotionName;
   private final LinkedHashMap<String, int[]> potionColors;
   private final LinkedHashMap<String, List<Entry<RegistryEntry<StatusEffect>, int[]>>> potionEffectsByName;
   private final HashSet<Integer> seenProjectileIds;
   private UUID trackedPlayerUuid;
   private LivingEntity lastAuraTarget;
   private World trackedWorld;
   private int arrowCount;
   private ItemStack displayedArrow;
   private final String[] ROMAN_LEVELS;
   private final EventListener<PlayerTickEvent> playerTickListener;
   private final EventListener<PacketEvent> packetListener;
   private final EventListener<PotionSplashEvent> potionSplashListener;

   private static final String ARROW_COUNTER_DESCRIPTION = "Показывает на экране, сколько стрел выпустила цель ауры.";
   private static final String NOTIFICATIONS_SECTION = "Уведомления";

   private static final int POTION_COLOR_MASK = 16777215;

   private static final String RGBA_MARKUP_PATTERN = "\\$\\{rgba\\((\\d+),(\\d+),(\\d+),(\\d+)\\)\\}([^$]*)";

   private static final double SPLASH_X_DISTANCE_POWER = 2.0;
   
   private static final double SPLASH_Z_DISTANCE_POWER = 2.0;

   private static final int LOW_STRENGTH_COLOR = 16729156;

   private static final int EFFECT_COLOR_MASK = 16777215;

   private boolean hasRangedWeapon(PlayerEntity player) {
      return player.getMainHandStack().getItem() instanceof BowItem
         || player.getMainHandStack().getItem() instanceof CrossbowItem
         || player.getOffHandStack().getItem() instanceof BowItem
         || player.getOffHandStack().getItem() instanceof CrossbowItem;
   }

   private MutableText itemText(ItemStack stack) {
      MutableText styledName = Text.empty();
      styledName.append(stack.getName().copy().styled(style -> style.getColor() == null ? style.withColor(stack.getRarity().getFormatting()) : style));
      if (stack.getCount() > 1) {
         styledName.append(Text.literal((" [")).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
         styledName.append(Text.literal("x" + stack.getCount()).setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
         styledName.append(Text.literal(("]")).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
      }

      return styledName;
   }

   public static UseTracker getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new UseTracker();
      }

      return INSTANCE;
   }

   private MutableText parseGradientText(String markup) {
      MutableText text = Text.empty();
      Matcher matcher = Pattern.compile(RGBA_MARKUP_PATTERN).matcher(markup);

      while (matcher.find()) {
         text.append(
            Text.literal(matcher.group(5))
               .setStyle(
                  Style.EMPTY
                     .withColor(
                        TextColor.fromRgb(Integer.parseInt(matcher.group(1)) << 16 | Integer.parseInt(matcher.group(2)) << 8 | Integer.parseInt(matcher.group(3)))
                     )
               )
         );
      }

      return text;
   }

   public int arrowCount() {
      return this.arrowCount;
   }

   private String potionNameForColor(int color) {
      int rgb = color & POTION_COLOR_MASK;

      for (Entry potionEntry : this.potionColors.entrySet()) {
         for (int catalogColor : (int[])potionEntry.getValue()) {
            if ((catalogColor & (16777215)) == rgb) {
               return (String)potionEntry.getKey();
            }
         }
      }

      int red = rgb >> 16 & 0xFF;
      int green = rgb >> 8 & 0xFF;
      int blue = rgb & 0xFF;
      if (Math.abs(red - green) <= 10 && Math.abs(green - blue) <= 10 && Math.abs(red - blue) <= 10 && red >= 14 && red <= 88) {
         Objects.requireNonNull(this);
         return ("${rgba(65,67,69,0)}[${rgba(68,63,64,0)}★${rgba(72,59,60,0)}]${rgba(76,55,56,0)} ${rgba(79,51,52,0)}З${rgba(83,47,48,0)}е${rgba(87,43,44,0)}л${rgba(90,39,40,0)}ь${rgba(94,35,36,0)}е${rgba(98,31,32,0)} ${rgba(102,27,28,0)}А${rgba(105,23,24,0)}с${rgba(109,19,20,0)}с${rgba(113,15,16,0)}а${rgba(116,11,12,0)}с${rgba(120,7,8,0)}и${rgba(124,3,4,0)}н${rgba(128,0,0,0)}а");
      } else {
         return null;
      }
   }

   private UseTracker() {
      this.assassinPotionName = ("${rgba(65,67,69,0)}[${rgba(68,63,64,0)}★${rgba(72,59,60,0)}]${rgba(76,55,56,0)} ${rgba(79,51,52,0)}З${rgba(83,47,48,0)}е${rgba(87,43,44,0)}л${rgba(90,39,40,0)}ь${rgba(94,35,36,0)}е${rgba(98,31,32,0)} ${rgba(102,27,28,0)}А${rgba(105,23,24,0)}с${rgba(109,19,20,0)}с${rgba(113,15,16,0)}а${rgba(116,11,12,0)}с${rgba(120,7,8,0)}и${rgba(124,3,4,0)}н${rgba(128,0,0,0)}а");
      this.potionColors = new LinkedHashMap<>();
      this.potionEffectsByName = new LinkedHashMap<>();
      this.seenProjectileIds = new HashSet<>();
      this.displayedArrow = Items.ARROW.getDefaultStack();
      this.ROMAN_LEVELS = new String[]{("I"), ("II"), ("III"), ("IV"), ("V"), ("VI"), ("VII"), ("VIII"), ("IX"), ("X")};
      this.playerTickListener = event -> {
         if (minecraftClient.player != null && minecraftClient.world != null) {
            this.updateArrowCounter();
            if (this.usedItems.getValue()) {
               for (AbstractClientPlayerEntity player : minecraftClient.world.getPlayers()) {
                  if (!player.getUuid().equals(minecraftClient.player.getUuid())) {
                     if (player.isUsingItem() && !player.getActiveItem().isEmpty()) {
                        if (player.getItemUseTimeLeft() <= 1 && this.itemUseNotifiedPlayers.add(player.getId())) {
                           this.notifyEvent(
                              ("Предмет"),
                              player.getDisplayName()
                                 .copy()
                                 .append(
                                    Text.literal(
                                          (
                                                Formatting.strip(player.getDisplayName().getString()).stripTrailing().length()
                                                      == Formatting.strip(player.getDisplayName().getString()).length()
                                                   ? (" ")
                                                   : ""
                                             )
                                             + "использовал "
                                       )
                                       .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                                 )
                                 .append(this.itemText(player.getActiveItem().copyWithCount(1)))
                           );
                        }
                     } else {
                        this.itemUseNotifiedPlayers.remove(player.getId());
                     }
                  }
               }
            }

            if (this.cooldownFinished.getValue()) {
               HashMap<String, ItemStack> visibleInventoryItems = new HashMap<>();

               for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
                  if (!minecraftClient.player.getInventory().getStack(inventorySlot).isEmpty()) {
                     visibleInventoryItems.putIfAbsent(
                        Formatting.strip(minecraftClient.player.getInventory().getStack(inventorySlot).getName().getString()),
                        minecraftClient.player.getInventory().getStack(inventorySlot)
                     );
                  }

                  if (minecraftClient.player.getInventory().getStack(inventorySlot).get(DataComponentTypes.BUNDLE_CONTENTS) != null) {
                     for (ItemStack stack : minecraftClient.player.getInventory().getStack(inventorySlot).get(DataComponentTypes.BUNDLE_CONTENTS).iterate()) {
                        if (!stack.isEmpty()) {
                           visibleInventoryItems.putIfAbsent(Formatting.strip(stack.getName().getString()), stack);
                        }
                     }
                  }
               }

               for (Entry previousCooldown : this.cooldownItems.entrySet()) {
                  if (visibleInventoryItems.containsKey(previousCooldown.getKey())
                     && !minecraftClient.player.getItemCooldownManager().isCoolingDown((ItemStack)previousCooldown.getValue())
                     && System.currentTimeMillis() - this.cooldownStartTimes.getOrDefault(previousCooldown.getKey(), System.currentTimeMillis()) > (7000L)) {
                     this.notifyEvent(
                        ("Кулдаун"),
                        this.itemText(((ItemStack)visibleInventoryItems.get(previousCooldown.getKey())).copyWithCount(1))
                           .append(Text.literal((" вышел из кд")).setStyle(Style.EMPTY.withColor(Formatting.GREEN)))
                     );
                  }
               }

               this.cooldownItems.clear();

               for (Entry currentCooldown : visibleInventoryItems.entrySet()) {
                  if (minecraftClient.player.getItemCooldownManager().isCoolingDown((ItemStack)currentCooldown.getValue())) {
                     this.cooldownItems.put((String)currentCooldown.getKey(), (ItemStack)currentCooldown.getValue());
                  }
               }

               this.cooldownStartTimes.keySet().retainAll(this.cooldownItems.keySet());

               for (String cooldownName : this.cooldownItems.keySet()) {
                  this.cooldownStartTimes.putIfAbsent(cooldownName, System.currentTimeMillis());
               }
            }

            if (this.expiringEffects.getValue()) {
               for (Entry previousEffect : this.previousEffects.entrySet()) {
                  if (minecraftClient.player.getStatusEffect((RegistryEntry<StatusEffect>)previousEffect.getKey()) == null) {
                     this.notifyEvent(
                        ("Эффект"),
                        Text.literal(((StatusEffectInstance)previousEffect.getValue()).getEffectType().value().getName().getString())
                           .setStyle(
                              Style.EMPTY.withColor(TextColor.fromRgb(((StatusEffectInstance)previousEffect.getValue()).getEffectType().value().getColor() & EFFECT_COLOR_MASK))
                           )
                           .append(Text.literal((" пропал")).setStyle(Style.EMPTY.withColor(Formatting.RED)))
                     );
                  }
               }

               this.previousEffects.clear();

               for (StatusEffectInstance effect : minecraftClient.player.getStatusEffects()) {
                  if (effect.getEffectType() == StatusEffects.STRENGTH
                     || effect.getEffectType() == StatusEffects.SPEED
                     || effect.getEffectType() == StatusEffects.RESISTANCE
                     || effect.getEffectType() == StatusEffects.WEAKNESS) {
                     this.previousEffects.put(effect.getEffectType(), effect);
                  }
               }
            }
         }
      };
      this.packetListener = packetEvent -> {
         if (packetEvent.isIncoming() && this.brokenTotems.getValue() && packetEvent.getPacket() instanceof EntityStatusS2CPacket statusPacket && statusPacket.getStatus() == 35) {
            minecraftClient.execute(
               () -> {
                  if (minecraftClient.world != null && statusPacket.getEntity(minecraftClient.world) instanceof PlayerEntity totemPlayer) {
                     this.notifyEvent(
                        ("Тотем"),
                        totemPlayer.getDisplayName()
                           .copy()
                           .append(
                              Text.literal(
                                    (
                                          Formatting.strip(totemPlayer.getDisplayName().getString()).stripTrailing().length()
                                                == Formatting.strip(totemPlayer.getDisplayName().getString()).length()
                                             ? (" ")
                                             : ""
                                       )
                                       + "лишился "
                                 )
                                 .setStyle(Style.EMPTY.withColor(Formatting.RED))
                           )
                           .append(
                              this.itemText(
                                 (totemPlayer.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING
                                       ? totemPlayer.getOffHandStack()
                                       : (
                                          totemPlayer.getMainHandStack().getItem() == Items.TOTEM_OF_UNDYING
                                             ? totemPlayer.getMainHandStack()
                                             : Items.TOTEM_OF_UNDYING.getDefaultStack()
                                       ))
                                    .copyWithCount(1)
                              )
                           )
                     );
                  }
               }
            );
         }
      };
      this.potionSplashListener = splashEvent -> {
         String potionName = this.potionNameForColor(splashEvent.getPotionColor());
         if (this.specialPotions.getValue() && minecraftClient.world != null && potionName != null) {
            for (AbstractClientPlayerEntity affectedPlayer : minecraftClient.world.getPlayers()) {
               if (affectedPlayer.isAlive() && !(affectedPlayer.squaredDistanceTo(splashEvent.getPosition().toCenterPos()) > (64.0D))) {
                  double effectStrength = 1.0
                     - Math.sqrt(
                           Math.pow(
                                 splashEvent.getPosition().toCenterPos().x
                                    - MathHelper.clamp(splashEvent.getPosition().toCenterPos().x, affectedPlayer.getBoundingBox().minX, affectedPlayer.getBoundingBox().maxX),
                                 SPLASH_X_DISTANCE_POWER
                              )
                              + Math.pow(
                                 splashEvent.getPosition().toCenterPos().y
                                    - MathHelper.clamp(splashEvent.getPosition().toCenterPos().y, affectedPlayer.getBoundingBox().minY, affectedPlayer.getBoundingBox().maxY),
                                 (2.0D)
                              )
                              + Math.pow(
                                 splashEvent.getPosition().toCenterPos().z
                                    - MathHelper.clamp(splashEvent.getPosition().toCenterPos().z, affectedPlayer.getBoundingBox().minZ, affectedPlayer.getBoundingBox().maxZ),
                                 SPLASH_Z_DISTANCE_POWER
                              )
                        )
                        / (4.0D);
                  if (!(effectStrength <= 0.0)) {
                     int percent = MathHelper.clamp((int)Math.round(effectStrength * (100.0D)), 0, 100);
                     MutableText notificationText = affectedPlayer.getDisplayName()
                        .copy()
                        .append(
                           Text.literal(
                                 (
                                       Formatting.strip(affectedPlayer.getDisplayName().getString()).stripTrailing().length()
                                             == Formatting.strip(affectedPlayer.getDisplayName().getString()).length()
                                          ? (" ")
                                          : ""
                                    )
                                    + "получил "
                              )
                              .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                        )
                        .append(this.parseGradientText(potionName))
                        .append(
                           Text.literal(" (" + percent + "%)")
                              .setStyle(
                                 Style.EMPTY
                                    .withColor(
                                       TextColor.fromRgb(
                                          (
                                                percent <= 50
                                                   ? ArgbColor.lerp(LOW_STRENGTH_COLOR, (16768324), percent / (50.0F))
                                                   : ArgbColor.lerp((16768324), (4521813), (percent - 50) / (50.0F))
                                             )
                                             & (16777215)
                                       )
                                    )
                              )
                        );
                     MutableText chatText = Text.empty().append(notificationText);

                     for (Entry effectEntry : this.potionEffectsByName.getOrDefault(this.parseGradientText(potionName).getString(), List.of())) {
                        int durationTicks = ((int[])effectEntry.getValue())[0] == 0 ? 0 : Math.max(0, MathHelper.floor(((int[])effectEntry.getValue())[0] * effectStrength + (0.5D)));
                        if (((int[])effectEntry.getValue())[0] <= 0 || durationTicks > 20) {
                           chatText.append(Text.literal(("\n  • ")).setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY)))
                              .append(
                                 Text.literal(
                                       ((StatusEffect)((RegistryEntry)effectEntry.getKey()).value()).getName().getString()
                                          + (((int[])effectEntry.getValue())[1] > 0 ? " " + this.ROMAN_LEVELS[Math.min(((int[])effectEntry.getValue())[1], 9)] : "")
                                    )
                                    .setStyle(
                                       Style.EMPTY
                                          .withColor(TextColor.fromRgb(((StatusEffect)((RegistryEntry)effectEntry.getKey()).value()).getColor() & (16777215)))
                                    )
                              )
                              .append(
                                 Text.literal(
                                       ((int[])effectEntry.getValue())[0] == 0 ? (" — сразу") : " — " + durationTicks / 1200 + ":" + String.format(("%02d"), durationTicks / 20 % 60)
                                    )
                                    .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                              );
                        }
                     }

                     this.notifyDetailedEvent(("Зелье"), notificationText, chatText);
                  }
               }
            }
         }
      };
      this.potionColors.put(("${rgba(255,20,147,0)}[${rgba(231,41,152,0)}★${rgba(208,62,157,0)}]${rgba(185,84,162,0)} ${rgba(162,105,167,0)}Х${rgba(139,126,172,0)}л${rgba(115,148,178,0)}о${rgba(92,169,183,0)}п${rgba(69,190,188,0)}у${rgba(46,212,193,0)}ш${rgba(23,233,198,0)}к${rgba(0,255,204,0)}а"), new int[]{(16738740)});
      this.potionColors.put(("${rgba(255,247,58,0)}[${rgba(248,247,58,0)}★${rgba(242,247,58,0)}]${rgba(235,248,58,0)} ${rgba(229,248,58,0)}З${rgba(223,249,59,0)}е${rgba(216,249,59,0)}л${rgba(210,250,59,0)}ь${rgba(204,250,59,0)}е${rgba(197,251,60,0)} ${rgba(191,251,60,0)}Р${rgba(185,252,60,0)}а${rgba(178,252,60,0)}д${rgba(172,253,61,0)}и${rgba(166,253,61,0)}а${rgba(159,254,61,0)}ц${rgba(153,254,61,0)}и${rgba(147,255,62,0)}и"), new int[]{(3329330)});
      this.potionColors.put(("${rgba(255,255,203,0)}[${rgba(255,254,192,0)}★${rgba(255,254,181,0)}]${rgba(255,254,170,0)} ${rgba(255,254,159,0)}С${rgba(255,254,148,0)}в${rgba(255,254,137,0)}я${rgba(255,254,126,0)}т${rgba(255,254,115,0)}а${rgba(255,254,104,0)}я${rgba(255,254,93,0)} ${rgba(255,254,82,0)}в${rgba(255,254,71,0)}о${rgba(255,254,60,0)}д${rgba(255,254,50,0)}а"), new int[]{(16777215)});
      this.potionColors.put(("${rgba(139,0,0,0)}[${rgba(147,10,0,0)}★${rgba(155,20,0,0)}]${rgba(163,30,0,0)} ${rgba(172,40,0,0)}З${rgba(180,50,0,0)}е${rgba(188,60,0,0)}л${rgba(197,70,0,0)}ь${rgba(205,80,0,0)}е${rgba(213,89,0,0)} ${rgba(221,100,0,0)}Г${rgba(230,109,0,0)}н${rgba(238,120,0,0)}е${rgba(246,129,0,0)}в${rgba(255,140,0,0)}а"), new int[]{(10040115)});
      this.potionColors.put(("${rgba(209,255,91,0)}[${rgba(209,255,91,0)}★${rgba(209,255,91,0)}]${rgba(209,255,91,0)} ${rgba(209,255,91,0)}З${rgba(209,255,91,0)}е${rgba(209,255,91,0)}л${rgba(209,255,91,0)}ь${rgba(209,255,91,0)}е${rgba(209,255,91,0)} ${rgba(209,255,91,0)}П${rgba(209,255,91,0)}а${rgba(209,255,91,0)}л${rgba(209,255,91,0)}л${rgba(209,255,91,0)}а${rgba(209,255,91,0)}д${rgba(209,255,91,0)}и${rgba(209,255,91,0)}н${rgba(209,255,91,0)}а"), new int[]{(65535)});
      java.util.LinkedHashMap<java.lang.String,int[]> var10000 = this.potionColors;
      Objects.requireNonNull(this);
      var10000.put(("${rgba(65,67,69,0)}[${rgba(68,63,64,0)}★${rgba(72,59,60,0)}]${rgba(76,55,56,0)} ${rgba(79,51,52,0)}З${rgba(83,47,48,0)}е${rgba(87,43,44,0)}л${rgba(90,39,40,0)}ь${rgba(94,35,36,0)}е${rgba(98,31,32,0)} ${rgba(102,27,28,0)}А${rgba(105,23,24,0)}с${rgba(109,19,20,0)}с${rgba(113,15,16,0)}а${rgba(116,11,12,0)}с${rgba(120,7,8,0)}и${rgba(124,3,4,0)}н${rgba(128,0,0,0)}а"), new int[]{(3355443)});
      this.potionColors.put(("${rgba(63,13,154,0)}[${rgba(66,23,160,0)}★${rgba(69,33,166,0)}]${rgba(72,43,172,0)} ${rgba(75,53,178,0)}С${rgba(79,64,184,0)}н${rgba(82,74,190,0)}о${rgba(85,84,196,0)}т${rgba(88,94,202,0)}в${rgba(92,105,208,0)}о${rgba(95,115,214,0)}р${rgba(98,125,220,0)}н${rgba(101,135,226,0)}о${rgba(105,146,233,0)}е"), new int[]{254});
      this.potionEffectsByName
         .put(
            ("[★] Хлопушка"),
            List.of(
               Map.entry(StatusEffects.SLOWNESS, new int[]{200, 9}),
               Map.entry(StatusEffects.SPEED, new int[]{300, 4}),
               Map.entry(StatusEffects.BLINDNESS, new int[]{100, 9}),
               Map.entry(StatusEffects.GLOWING, new int[]{3600, 0})
            )
         );
      this.potionEffectsByName
         .put(
            ("[★] Зелье Радиации"),
            List.of(
               Map.entry(StatusEffects.POISON, new int[]{1200, 1}),
               Map.entry(StatusEffects.WITHER, new int[]{1200, 1}),
               Map.entry(StatusEffects.SLOWNESS, new int[]{1800, 2}),
               Map.entry(StatusEffects.HUNGER, new int[]{1200, 4}),
               Map.entry(StatusEffects.GLOWING, new int[]{2400, 0})
            )
         );
      this.potionEffectsByName
         .put(
            ("[★] Святая вода"),
            List.of(
               Map.entry(StatusEffects.REGENERATION, new int[]{900, 1}),
               Map.entry(StatusEffects.INVISIBILITY, new int[]{12000, 1}),
               Map.entry(StatusEffects.INSTANT_HEALTH, new int[]{0, 2})
            )
         );
      this.potionEffectsByName.put(("[★] Зелье Гнева"), List.of(Map.entry(StatusEffects.STRENGTH, new int[]{600, 4}), Map.entry(StatusEffects.SLOWNESS, new int[]{600, 3})));
      this.potionEffectsByName
         .put(
            ("[★] Зелье Палладина"),
            List.of(
               Map.entry(StatusEffects.RESISTANCE, new int[]{12000, 0}),
               Map.entry(StatusEffects.FIRE_RESISTANCE, new int[]{12000, 0}),
               Map.entry(StatusEffects.HEALTH_BOOST, new int[]{1200, 2}),
               Map.entry(StatusEffects.INVISIBILITY, new int[]{18000, 2})
            )
         );
      this.potionEffectsByName
         .put(
            ("[★] Зелье Ассасина"),
            List.of(
               Map.entry(StatusEffects.STRENGTH, new int[]{1200, 3}),
               Map.entry(StatusEffects.SPEED, new int[]{6000, 2}),
               Map.entry(StatusEffects.HASTE, new int[]{1200, 0}),
               Map.entry(StatusEffects.INSTANT_DAMAGE, new int[]{0, 1})
            )
         );
      this.potionEffectsByName
         .put(
            ("[★] Снотворное"),
            List.of(
               Map.entry(StatusEffects.WEAKNESS, new int[]{1800, 1}),
               Map.entry(StatusEffects.MINING_FATIGUE, new int[]{200, 1}),
               Map.entry(StatusEffects.WITHER, new int[]{1800, 2}),
               Map.entry(StatusEffects.BLINDNESS, new int[]{200, 0})
            )
         );
      this.brokenTotems = this.addSetting(new BooleanSetting(("Сломанные тотемы"), ("Пишет, когда игрок лишается тотема."), true));
      this.usedItems = this.addSetting(new BooleanSetting(("Использованные предметы"), ("Пишет, когда игрок использует предмет."), true));
      this.cooldownFinished = this.addSetting(new BooleanSetting(("Предмет вышел из кд"), ("Пишет, когда твой предмет из инвентаря, хотбара или мешка выходит из кулдауна длиннее 7 секунд."), true));
      this.specialPotions = this.addSetting(new BooleanSetting(("Специальные зелья"), ("Пишет, кто и на сколько процентов получил специальное зелье."), true));
      this.expiringEffects = this.addSetting(new BooleanSetting(("Пропадающие эффекты"), ("Пишет, когда у тебя пропадает сила, скорость, сопротивление или слабость."), true));
      this.arrowCounter = this.addSetting(new BooleanSetting(("Счётчик стрел"), ARROW_COUNTER_DESCRIPTION, true));
      this.addSetting(new SettingSeparator(NOTIFICATIONS_SECTION));
      this.showInChat = this.addSetting(new BooleanSetting(("Показывать в чате"), ("Дублирует уведомления в чат."), true));
      this.showNotifications = this.addSetting(new BooleanSetting(("Показывать в уведомлении"), ("Показывает всплывающее уведомление."), true));
   }

   private void notifyEvent(String title, MutableText text) {
      this.notifyDetailedEvent(title, text, text);
   }

   private void notifyDetailedEvent(String title, MutableText notificationText, MutableText chatText) {
      if (this.showNotifications.getValue()) {
         ToastNotifications.showStyledToast(this.category.iconGlyph(), title, notificationText, true);
      }

      if (this.showInChat.getValue()) {
         ChatMessages.print(
            ChatMessages.gradientText(("UseTracker"), GuiVisuals.accentColor, GuiVisuals.accentGradientColor, true)
               .append(Text.literal(Formatting.DARK_GRAY + " » " + Formatting.RESET))
               .append(chatText)
         );
      }
   }

   private void updateArrowCounter() {
      LivingEntity selectedTarget = AttackAura.getInstance().isEnabled() ? AttackAura.getInstance().target() : null;
      if (minecraftClient.world != this.trackedWorld || selectedTarget != null && selectedTarget != this.lastAuraTarget) {
         this.trackedWorld = minecraftClient.world;
         if (selectedTarget != null) {
            this.lastAuraTarget = selectedTarget;
         }

         this.trackedPlayerUuid = selectedTarget instanceof PlayerEntity targetPlayer ? targetPlayer.getUuid() : null;
         this.arrowCount = 0;
         this.displayedArrow = Items.ARROW.getDefaultStack();
         this.seenProjectileIds.clear();
      }

      if (this.trackedPlayerUuid != null) {
         PlayerEntity player = minecraftClient.world.getPlayerByUuid(this.trackedPlayerUuid);
         if (player != null && !player.isAlive()) {
            this.trackedPlayerUuid = null;
            this.arrowCount = 0;
         } else {
            for (Entity entity : minecraftClient.world.getEntities()) {
               if (entity instanceof PersistentProjectileEntity projectile && !(entity instanceof TridentEntity) && !this.seenProjectileIds.contains(entity.getId())) {
                  if (projectile.getOwner() == null) {
                     if (projectile.age > 5) {
                        this.seenProjectileIds.add(entity.getId());
                     }
                  } else {
                     this.seenProjectileIds.add(entity.getId());
                     if (projectile.getOwner() instanceof PlayerEntity ownerPlayer && ownerPlayer.getUuid().equals(this.trackedPlayerUuid) && this.hasRangedWeapon(ownerPlayer)) {
                        this.arrowCount++;
                        ItemStack stack = projectile.getItemStack();
                        this.displayedArrow = stack.isEmpty() ? Items.ARROW.getDefaultStack() : stack.copyWithCount(1);
                     }
                  }
               }
            }

            if (this.seenProjectileIds.size() > 512) {
               this.seenProjectileIds.clear();
            }
         }
      }
   }

   public ItemStack displayedArrow() {
      return this.displayedArrow;
   }
}
