package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.RenderSlotsEvent;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.render.pipeline.SliderSetting;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

@ModuleInfo(
   name = "AuctionHelper",
   description = "Подсвечивает самые выгодные лоты на аукционе, фильтрует их по зачарованиям и ищет предмет из руки по бинду.",
   category = ModuleCategory.MISC
)
public final class AuctionHelper extends Module {
   private static AuctionHelper INSTANCE;
   public BooleanSetting calculator;
   public SliderSetting durability;
   public BooleanSetting considerBalance;
   public BooleanSetting enchantedOnly;
   public BooleanSetting quantityFilter;
   public ModeSetting quantityMode;
   public SliderSetting stackSize;
   public BooleanSetting itemFilters;
   public MultiChoiceSetting armorFilter;
   public MultiChoiceSetting elytraFilter;
   public MultiChoiceSetting swordFilter;
   public MultiChoiceSetting pickaxeFilter;
   public MultiChoiceSetting axeFilter;
   public MultiChoiceSetting shovelFilter;
   public MultiChoiceSetting exclusions;
   public FilteredTextSetting searchCommand;
   public BindSetting searchHeldItemBind;
   private final Pattern multiplicationPattern;
   private boolean searchKeyWasPressed;
   private final EventListener<PacketEvent> packetListener;
   private final EventListener<PlayerTickEvent> playerTickListener;
   private final EventListener<RenderSlotsEvent> slotRenderListener;

   private boolean matchesLotFilters(ItemStack stack) {
      if (stack.isOf(Items.GRAY_DYE) && stack.getName().getString().contains(("Товар не актуален"))) {
         return false;
      }

      if (stack.isDamageable() && (float)(stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage() < this.durability.floatValue() / (100.0F)) {
         return false;
      }

      if (this.enchantedOnly.getValue() && stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).isEmpty()) {
         return false;
      }

      if (this.quantityFilter.getValue()) {
         if (this.quantityMode.isSelected(("Точно")) && stack.getCount() != this.stackSize.intValue()) {
            return false;
         }

         if (this.quantityMode.isSelected(("Минимум")) && stack.getCount() < this.stackSize.intValue()) {
            return false;
         }
      }

      if (!this.itemFilters.getValue()) {
         return true;
      }

      Item item = stack.getItem();
      if (this.exclusions.matchesOption(("Стрелы")) && item instanceof ArrowItem) {
         return false;
      }

      if (this.exclusions.matchesOption(("Пузырьки")) && stack.isOf(Items.GLASS_BOTTLE)) {
         return false;
      }

      if (this.exclusions.matchesOption(("Дыхание дракона")) && stack.isOf(Items.DRAGON_BREATH)) {
         return false;
      }

      if (this.isArmor(stack)) {
         if (this.exclusions.matchesOption(("Броня"))) {
            return false;
         }

         if (this.armorFilter.matchesOption(("Без шипов")) && this.enchantmentLevel(stack, ("minecraft:thorns")) != -1) {
            return false;
         }

         if (this.armorFilter.matchesOption(("Защита V")) && this.enchantmentLevel(stack, ("minecraft:protection")) < 5) {
            return false;
         }

         if (this.armorFilter.matchesOption(("Прочность V")) && this.enchantmentLevel(stack, ("minecraft:unbreaking")) < 5) {
            return false;
         }

         if (this.armorFilter.matchesOption(("Починка")) && this.enchantmentLevel(stack, ("minecraft:mending")) == -1) {
            return false;
         }

         if (this.armorFilter.matchesOption(("Починка II")) && this.enchantmentLevel(stack, ("minecraft:mending")) < 2) {
            return false;
         }
      }

      if (stack.isOf(Items.ELYTRA)) {
         if (this.exclusions.matchesOption(("Элитры"))) {
            return false;
         }

         if (this.elytraFilter.matchesOption(("Прочность V")) && this.enchantmentLevel(stack, ("minecraft:unbreaking")) < 5) {
            return false;
         }

         if (this.elytraFilter.matchesOption(("Починка")) && this.enchantmentLevel(stack, ("minecraft:mending")) == -1) {
            return false;
         }
      }

      if (this.exclusions.matchesOption(("Тотемы")) && stack.isOf(Items.TOTEM_OF_UNDYING)) {
         return false;
      }

      if (this.exclusions.matchesOption(("Головы")) && stack.isOf(Items.PLAYER_HEAD)) {
         return false;
      }

      if (this.exclusions.matchesOption(("Трезубцы")) && item instanceof TridentItem) {
         return false;
      }

      if (this.exclusions.matchesOption(("Луки")) && item instanceof BowItem) {
         return false;
      }

      if (this.exclusions.matchesOption(("Арбалеты")) && item instanceof CrossbowItem) {
         return false;
      }

      if (this.exclusions.matchesOption(("Огниво")) && item instanceof FlintAndSteelItem) {
         return false;
      }

      if (this.exclusions.matchesOption(("Конская броня"))
         && ("minecraft").equals(Registries.ITEM.getId(item).getNamespace())
         && Registries.ITEM.getId(item).getPath().endsWith(("_horse_armor"))) {
         return false;
      }

      if (stack.isIn(ItemTags.SWORDS)) {
         if (this.exclusions.matchesOption(("Мечи"))) {
            return false;
         }

         if (this.swordFilter.matchesOption(("С отдачей")) && this.enchantmentLevel(stack, ("minecraft:knockback")) == -1) {
            return false;
         }

         if (this.swordFilter.matchesOption(("Без отдачи")) && this.enchantmentLevel(stack, ("minecraft:knockback")) != -1) {
            return false;
         }

         if (this.swordFilter.matchesOption(("Острота")) && this.enchantmentLevel(stack, ("minecraft:sharpness")) == -1) {
            return false;
         }

         if (this.swordFilter.matchesOption(("Прочность")) && this.enchantmentLevel(stack, ("minecraft:unbreaking")) == -1) {
            return false;
         }

         if (this.swordFilter.matchesOption(("Яд")) && this.customEnchantmentLevel(stack, ("poison")) == -1) {
            return false;
         }

         if (this.swordFilter.matchesOption(("Обнаружение")) && this.customEnchantmentLevel(stack, ("detection")) == -1) {
            return false;
         }

         if (this.swordFilter.matchesOption(("Окисление")) && this.customEnchantmentLevel(stack, ("oxidation")) == -1) {
            return false;
         }

         if (this.swordFilter.matchesOption(("Вампиризм")) && this.customEnchantmentLevel(stack, ("vampirism")) == -1) {
            return false;
         }
      }

      if (stack.isIn(ItemTags.PICKAXES)) {
         if (this.exclusions.matchesOption(("Кирки"))) {
            return false;
         }

         if (this.pickaxeFilter.matchesOption(("Эффективность")) && this.enchantmentLevel(stack, ("minecraft:efficiency")) == -1) {
            return false;
         }

         if (this.pickaxeFilter.matchesOption(("Починка")) && this.enchantmentLevel(stack, ("minecraft:mending")) == -1) {
            return false;
         }

         if (this.pickaxeFilter.matchesOption(("Удача")) && this.enchantmentLevel(stack, ("minecraft:fortune")) == -1) {
            return false;
         }

         if (this.pickaxeFilter.matchesOption(("Прочность")) && this.enchantmentLevel(stack, ("minecraft:unbreaking")) == -1) {
            return false;
         }

         if (this.pickaxeFilter.matchesOption(("Шёлковое касание")) && this.enchantmentLevel(stack, ("minecraft:silk_touch")) == -1) {
            return false;
         }

         if (this.pickaxeFilter.matchesOption(("Переплавка")) && this.customEnchantmentLevel(stack, ("smelting")) == -1) {
            return false;
         }

         if (this.pickaxeFilter.matchesOption(("Магнит")) && this.customEnchantmentLevel(stack, ("magnet")) == -1) {
            return false;
         }

         if (this.pickaxeFilter.matchesOption(("Бульдозер")) && this.customEnchantmentLevel(stack, ("buldozing")) == -1 && this.customEnchantmentLevel(stack, ("megabuldozing")) == -1) {
            return false;
         }

         if (this.pickaxeFilter.matchesOption(("Без тяжести")) && this.customEnchantmentLevel(stack, ("heavy")) != -1) {
            return false;
         }
      }

      if (stack.isIn(ItemTags.AXES)) {
         if (this.exclusions.matchesOption(("Топоры"))) {
            return false;
         }

         if (this.axeFilter.matchesOption(("Эффективность")) && this.enchantmentLevel(stack, ("minecraft:efficiency")) == -1) {
            return false;
         }

         if (this.axeFilter.matchesOption(("Починка")) && this.enchantmentLevel(stack, ("minecraft:mending")) == -1) {
            return false;
         }

         if (this.axeFilter.matchesOption(("Удача")) && this.enchantmentLevel(stack, ("minecraft:fortune")) == -1) {
            return false;
         }

         if (this.axeFilter.matchesOption(("Прочность")) && this.enchantmentLevel(stack, ("minecraft:unbreaking")) == -1) {
            return false;
         }

         if (this.axeFilter.matchesOption(("Магнит")) && this.customEnchantmentLevel(stack, ("magnet")) == -1) {
            return false;
         }

         if (this.axeFilter.matchesOption(("Лесоруб")) && this.customEnchantmentLevel(stack, ("lumberjack")) == -1) {
            return false;
         }

         if (this.axeFilter.matchesOption(("Пингер")) && this.customEnchantmentLevel(stack, ("pinger")) == -1) {
            return false;
         }

         if (this.axeFilter.matchesOption(("Бульдозер")) && this.customEnchantmentLevel(stack, ("buldozing")) == -1 && this.customEnchantmentLevel(stack, ("megabuldozing")) == -1) {
            return false;
         }
      }

      if (stack.isIn(ItemTags.SHOVELS)) {
         if (this.exclusions.matchesOption(("Лопаты"))) {
            return false;
         }

         if (this.shovelFilter.matchesOption(("Эффективность")) && this.enchantmentLevel(stack, ("minecraft:efficiency")) == -1) {
            return false;
         }

         if (this.shovelFilter.matchesOption(("Починка")) && this.enchantmentLevel(stack, ("minecraft:mending")) == -1) {
            return false;
         }

         if (this.shovelFilter.matchesOption(("Удача")) && this.enchantmentLevel(stack, ("minecraft:fortune")) == -1) {
            return false;
         }

         if (this.shovelFilter.matchesOption(("Прочность")) && this.enchantmentLevel(stack, ("minecraft:unbreaking")) == -1) {
            return false;
         }

         if (this.shovelFilter.matchesOption(("Магнит")) && this.customEnchantmentLevel(stack, ("magnet")) == -1) {
            return false;
         }

         if (this.shovelFilter.matchesOption(("Бульдозер")) && this.customEnchantmentLevel(stack, ("buldozing")) == -1 && this.customEnchantmentLevel(stack, ("megabuldozing")) == -1) {
            return false;
         }
      }

      return !stack.isIn(ItemTags.HOES) || !this.exclusions.matchesOption(("Мотыги"));
   }

   private Optional<Integer> displayedBalance() {
      Scoreboard scoreboard = minecraftClient.world.getScoreboard();
      ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
      return objective == null
         ? Optional.empty()
         : scoreboard.getScoreboardEntries(objective)
            .stream()
            .filter(scoreboardEntry -> !scoreboardEntry.hidden())
            .map(scoreEntry -> Team.decorateName(scoreboard.getScoreHolderTeam(scoreEntry.owner()), scoreEntry.name()).getString())
            .map(this::normalizeServerText)
            .filter(balanceLabel -> balanceLabel.contains(("монет")) || balanceLabel.contains(("баланс")))
            .map(this::parseDisplayedInteger)
            .flatMap(Optional::stream)
            .findFirst();
   }

   public static AuctionHelper getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AuctionHelper();
      }

      return INSTANCE;
   }

   private String expandMultiplications(String command) {
      Matcher multiplication = this.multiplicationPattern.matcher(command);
      StringBuilder textBuilder = new StringBuilder();

      while (multiplication.find()) {
         try {
            multiplication.appendReplacement(textBuilder, String.valueOf(Long.parseLong(multiplication.group(1)) * Long.parseLong(multiplication.group(2))));
         } catch (NumberFormatException error) {
            multiplication.appendReplacement(textBuilder, multiplication.group(0));
         }
      }

      multiplication.appendTail(textBuilder);
      return textBuilder.toString();
   }

   private AuctionHelper() {
      this.multiplicationPattern = Pattern.compile(("(\\d+)\\*(\\d+)"));
      this.packetListener = event -> {
         if (event.isOutgoing() && this.calculator.getValue() && event.getPacket() instanceof CommandExecutionC2SPacket commandPacket) {
            String expandedCommand = this.expandMultiplications(commandPacket.command());
            if (!expandedCommand.equals(commandPacket.command())) {
               event.setPacket(new CommandExecutionC2SPacket(expandedCommand));
            }
         }
      };
      this.playerTickListener = event -> {
         boolean searchKeyWasPressed = this.searchHeldItemBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (searchKeyWasPressed && !this.searchKeyWasPressed && minecraftClient.currentScreen == null) {
            this.searchHeldItem();
         }

         this.searchKeyWasPressed = searchKeyWasPressed;
      };
      this.slotRenderListener = event -> {
         if (minecraftClient.player != null
            && minecraftClient.world != null
            && event.getScreen() instanceof GenericContainerScreen containerScreen
            && this.isAuctionTitle(containerScreen.getTitle().getString())) {
            Optional balance = this.displayedBalance();
            java.util.List<com.mouseclient.modules.impl.misc.AuctionHelper.PricedLot> lots = containerScreen.getScreenHandler()
               .slots
               .stream()
               .filter(slot -> slot.hasStack() && this.matchesLotFilters(slot.getStack()))
               .map(lotSlot -> new AuctionHelper.PricedLot(this.lotPrice(lotSlot.getStack()), lotSlot))
               .filter(affordableLot -> affordableLot.price() >= 0 && (!this.considerBalance.getValue() || balance.isEmpty() || affordableLot.price() <= (Integer)balance.get()))
               .toList();
            this.highlightLot(event.getDrawContext(), lots.stream().min(Comparator.comparingInt(AuctionHelper.PricedLot::price)).orElse(null), (-16711936));
            this.highlightLot(
               event.getDrawContext(),
               lots.stream()
                  .filter(lot -> lot.slot().getStack().getCount() > 1)
                  .min(Comparator.comparingDouble(lot -> (double)lot.price() / lot.slot().getStack().getCount()))
                  .orElse(null),
               (-16711681)
            );
         }
      };
      this.calculator = this.addSetting(new BooleanSetting(("Калькулятор"), ("Считает выражения вида 2*64 в отправляемых командах (/ah sell 12*12)."), true));
      this.durability = this.addSetting(new SliderSetting(("Прочность"), ("Минимальная оставшаяся прочность лота.")).withRange(0.0F, (100.0F)).withStep(1.0F).withSuffix(("%")).setValue((95.0F)));
      this.considerBalance = this.addSetting(new BooleanSetting(("Учитывать баланс"), ("Не подсвечивает лоты дороже баланса."), true));
      this.enchantedOnly = this.addSetting(new BooleanSetting(("Только зачарованные"), ("Пропускает лоты без зачарований."), false));
      this.quantityFilter = this.addSetting(new BooleanSetting(("Фильтр количества"), ("Отбирает лоты по размеру стака."), false));
      this.quantityMode = this.addSetting(new ModeSetting(("Режим количества"), ("Точно ровно столько, Минимум не меньше."), ("Точно"), ("Точно"), ("Минимум")));
      this.stackSize = this.addSetting(new SliderSetting(("Размер стака"), ("Сколько предметов должно быть в лоте.")).withIntegerRange(1, 64).setValue(1.0F));
      this.quantityMode.setVisibility(() -> this.quantityFilter.getValue());
      this.stackSize.setVisibility(() -> this.quantityFilter.getValue());
      this.itemFilters = this.addSetting(new BooleanSetting(("Фильтры предметов"), ("Требования к зачарованиям по типам предметов."), false));
      this.armorFilter = this.addSetting(new MultiChoiceSetting(("Броня"), ("Каким требованиям должна отвечать броня.")).withOptions(("Без шипов"), ("Защита V"), ("Прочность V"), ("Починка"), ("Починка II")));
      this.elytraFilter = this.addSetting(new MultiChoiceSetting(("Элитры"), ("Каким требованиям должны отвечать элитры.")).withOptions(("Прочность V"), ("Починка")));
      this.swordFilter = this.addSetting(
         new MultiChoiceSetting(("Мечи"), ("Каким требованиям должны отвечать мечи.")).withOptions(("С отдачей"), ("Без отдачи"), ("Острота"), ("Прочность"), ("Яд"), ("Обнаружение"), ("Окисление"), ("Вампиризм"))
      );
      this.pickaxeFilter = this.addSetting(
         new MultiChoiceSetting(("Кирки"), ("Каким требованиям должны отвечать кирки.")).withOptions(("Эффективность"), ("Починка"), ("Удача"), ("Прочность"), ("Шёлковое касание"), ("Переплавка"), ("Магнит"), ("Бульдозер"), ("Без тяжести"))
      );
      this.axeFilter = this.addSetting(
         new MultiChoiceSetting(("Топоры"), ("Каким требованиям должны отвечать топоры.")).withOptions(("Эффективность"), ("Починка"), ("Удача"), ("Прочность"), ("Магнит"), ("Лесоруб"), ("Пингер"), ("Бульдозер"))
      );
      this.shovelFilter = this.addSetting(new MultiChoiceSetting(("Лопаты"), ("Каким требованиям должны отвечать лопаты.")).withOptions(("Эффективность"), ("Починка"), ("Удача"), ("Прочность"), ("Магнит"), ("Бульдозер")));
      this.exclusions = this.addSetting(
         new MultiChoiceSetting(("Исключения"), ("Какие лоты не подсвечивать вовсе."))
            .withOptions(
               ("Стрелы"),
               ("Пузырьки"),
               ("Дыхание дракона"),
               ("Броня"),
               ("Элитры"),
               ("Тотемы"),
               ("Головы"),
               ("Трезубцы"),
               ("Луки"),
               ("Арбалеты"),
               ("Огниво"),
               ("Конская броня"),
               ("Мечи"),
               ("Кирки"),
               ("Топоры"),
               ("Лопаты"),
               ("Мотыги")
            )
      );
      this.armorFilter.setVisibility(() -> this.itemFilters.getValue());
      this.elytraFilter.setVisibility(() -> this.itemFilters.getValue());
      this.swordFilter.setVisibility(() -> this.itemFilters.getValue());
      this.pickaxeFilter.setVisibility(() -> this.itemFilters.getValue());
      this.axeFilter.setVisibility(() -> this.itemFilters.getValue());
      this.shovelFilter.setVisibility(() -> this.itemFilters.getValue());
      this.exclusions.setVisibility(() -> this.itemFilters.getValue());
      this.searchCommand = this.addSetting(new FilteredTextSetting(("Команда поиска"), ("Команда, к которой допишется имя предмета из руки."), ("ah search"), 50));
      this.searchHeldItemBind = this.addSetting(new BindSetting(("Поиск с руки"), ("Ищет предмет из главной руки на аукционе.")));
   }

   public int lotPrice(ItemStack stack) {
      for (Text text : stack.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).lines()) {
         if (this.normalizeServerText(text.getString()).contains(("цена")) || this.normalizeServerText(text.getString()).contains(("монет"))) {
            return this.parseDisplayedInteger(text.getString()).orElse(-1);
         }
      }

      return -1;
   }

   public void searchHeldItem() {
      if (minecraftClient.player != null && !minecraftClient.player.getMainHandStack().isEmpty()) {
         String itemName = Formatting.strip(minecraftClient.player.getMainHandStack().getName().getString().trim().replace(("\""), ("\\\"")))
            .replace(("[★]"), "")
            .replace(("fff"), "")
            .replace(("ggg"), "")
            .replace(("xxx"), "")
            .replace(("123"), "")
            .trim();
         if (!itemName.isEmpty()) {
            String commandPrefix = this.searchCommand.getValue().isBlank() ? ("ah search") : this.searchCommand.getValue().stripLeading();
            minecraftClient.player.networkHandler.sendChatCommand((commandPrefix.startsWith(("/")) ? commandPrefix.substring(1) : commandPrefix) + " " + itemName);
         }
      }
   }

   private boolean isArmor(ItemStack stack) {
      return stack.isIn(ItemTags.HEAD_ARMOR) || stack.isIn(ItemTags.CHEST_ARMOR) || stack.isIn(ItemTags.LEG_ARMOR) || stack.isIn(ItemTags.FOOT_ARMOR);
   }

   public String normalizeServerText(String text) {
      return text.toLowerCase(Locale.ROOT).replace('o', 'о').replace('e', 'е').replace('a', 'а').replace('c', 'с');
   }

   private int customEnchantmentLevel(ItemStack stack, String type) {
      NbtList enchantments = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getListOrEmpty(("custom-enchantments"));

      for (int enchantmentIndex = 0; enchantmentIndex < enchantments.size(); enchantmentIndex++) {
         if (enchantments.getCompoundOrEmpty(enchantmentIndex).getString(("type"), "").equals(type)) {
            return enchantments.getCompoundOrEmpty(enchantmentIndex).getInt(("level"), -1);
         }
      }

      return -1;
   }

   private int enchantmentLevel(ItemStack stack, String enchantmentId) {
      for (Entry enchantment : stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT).getEnchantmentEntries()) {
         if (((RegistryEntry)enchantment.getKey()).matchesId(Identifier.of(enchantmentId))) {
            return enchantment.getIntValue();
         }
      }

      return -1;
   }

   public boolean isAuctionTitle(String title) {
      return title.contains(("1A0ꀄ"))
         || title.contains(("ꈁꀀꈂꌲꈂꀁ§0ꈃꄙ"))
         || title.toLowerCase().contains(("漢:"))
         || title.toLowerCase().contains(("аукцион"))
         || title.toLowerCase().contains(("поиск:"))
         || title.toLowerCase().contains(("п:"))
         || title.toLowerCase().contains(("категория:"));
   }

   private Optional<Integer> parseDisplayedInteger(String text) {
      String digits = text.replaceAll(("\\D"), "");
      return !digits.isEmpty() && digits.length() <= 9 ? Optional.of(Integer.parseInt(digits)) : Optional.empty();
   }

   private void highlightLot(DrawContext context, AuctionHelper.PricedLot lot, int color) {
      if (lot != null) {
         context.fill(lot.slot().x, lot.slot().y, lot.slot().x + 16, lot.slot().y + 16, color);
      }
   }

   private record PricedLot(int price, Slot slot) {
   }
}
