package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.render.pipeline.SliderSetting;
import com.mouseclient.util.time.Stopwatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@ModuleInfo(
   name = "AutoSell",
   description = "Выставляет предмет из руки на аукцион по минимальной цене с процентом, по второму бинду продаёт весь инвентарь и содержимое шалкеров.",
   category = ModuleCategory.MISC
)
public final class AutoSell extends Module {
   private static AutoSell INSTANCE;
   private static final Pattern MAX_PRICE_MESSAGE = Pattern.compile((".*максимальная цена на этот товар.*"));
   private static final HashMap<String, String> gearSearchNames = new HashMap<>();
   public SliderSetting pricePercentage;
   public FilteredTextSetting searchCommand;
   public FilteredTextSetting sellCommand;
   public BindSetting sellHeldItemBind;
   public BindSetting sellInventoryBind;
   private AutoSell.SellingPhase phase = AutoSell.SellingPhase.IDLE;
   private final Stopwatch stateTimer = new Stopwatch();
   private final HashSet<String> skippedItemNames = new HashSet<>();
   private final HashSet<Integer> visitedShulkerSlots = new HashSet<>();
   private boolean sellInventoryMode;
   private boolean heldKeyWasPressed;
   private boolean inventoryKeyWasPressed;
   private int inventoryItemSlot = -1;
   private int shulkerSlot = -1;
   private boolean searchRetried;
   private String searchedItemName = "";
   private boolean maximumPriceRejected;
   private boolean splitSalePending;
   private int splitItemSlot = -1;
   private int minimumUnitPrice;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         boolean heldKeyPressed = this.sellHeldItemBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (heldKeyPressed && !this.heldKeyWasPressed) {
            this.toggleSelling(false);
         }

         this.heldKeyWasPressed = heldKeyPressed;
         boolean inventoryKeyPressed = this.sellInventoryBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (inventoryKeyPressed && !this.inventoryKeyWasPressed) {
            this.toggleSelling(true);
         }

         this.inventoryKeyWasPressed = inventoryKeyPressed;
         switch (this.phase) {
            case IDLE:
            default:
               break;
            case PICK:
               if (InventorySwapQueue.getInstance().hasPendingSwaps() || ItemUseQueue.getInstance().hasPendingUses() || !this.stateTimer.hasElapsed((1500L))) {
                  return;
               }

               if (minecraftClient.currentScreen != null) {
                  minecraftClient.player.closeHandledScreen();
                  return;
               }

               this.inventoryItemSlot = -1;
               this.shulkerSlot = -1;

               for (int inventorySlot = 0; inventorySlot <= 35; inventorySlot++) {
                  if (!minecraftClient.player.getInventory().getStack(inventorySlot).isEmpty()) {
                     if (Block.getBlockFromItem(minecraftClient.player.getInventory().getStack(inventorySlot).getItem()) instanceof ShulkerBoxBlock) {
                        if (this.shulkerSlot == -1 && !this.visitedShulkerSlots.contains(inventorySlot)) {
                           this.shulkerSlot = inventorySlot;
                        }
                     } else if (this.inventoryItemSlot == -1 && !this.skippedItemNames.contains(minecraftClient.player.getInventory().getStack(inventorySlot).getName().getString())) {
                        this.inventoryItemSlot = inventorySlot;
                     }
                  }
               }

               if (this.inventoryItemSlot == -1 && this.shulkerSlot == -1) {
                  this.phase = AutoSell.SellingPhase.IDLE;
                  return;
               }

               if (this.inventoryItemSlot == -1) {
                  this.visitedShulkerSlots.add(this.shulkerSlot);
                  InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(this.shulkerSlot), 1, SlotActionType.PICKUP, false);
                  this.stateTimer.reset();
                  this.phase = AutoSell.SellingPhase.SHULKER;
                  return;
               }

               if (this.inventoryItemSlot != InventoryActions.selectedHotbarSlot()) {
                  InventorySwapQueue.getInstance().queueSwap(this.inventoryItemSlot, InventoryActions.selectedHotbarSlot(), 1);
               }

               this.stateTimer.reset();
               this.phase = AutoSell.SellingPhase.SEARCH;
               break;
            case SEARCH:
               if (InventorySwapQueue.getInstance().hasPendingSwaps() || ItemUseQueue.getInstance().hasPendingUses() || !this.stateTimer.hasElapsed((200L))) {
                  return;
               }

               if (minecraftClient.player.getMainHandStack().isEmpty()) {
                  this.stateTimer.reset();
                  this.phase = this.sellInventoryMode ? AutoSell.SellingPhase.PICK : AutoSell.SellingPhase.IDLE;
                  return;
               }

               this.searchedItemName = minecraftClient.player.getMainHandStack().getName().getString();
               this.searchRetried = false;
               this.searchHeldItem();
               this.stateTimer.reset();
               this.phase = AutoSell.SellingPhase.WAIT_LOTS;
               break;
            case WAIT_LOTS:
               if (minecraftClient.currentScreen instanceof GenericContainerScreen auctionScreen && AuctionHelper.getInstance().isAuctionTitle(auctionScreen.getTitle().getString())) {
                  if (!this.stateTimer.hasElapsed((250L))) {
                     return;
                  }

                  int minimumLotUnitPrice = -1;

                  for (int lotSlot = 0; lotSlot < minecraftClient.player.currentScreenHandler.slots.size() - 36; lotSlot++) {
                     ItemStack stack = minecraftClient.player.currentScreenHandler.getSlot(lotSlot).getStack();
                     if (!stack.isEmpty()
                        && ItemStack.areItemsEqual(stack, minecraftClient.player.getMainHandStack())
                        && this.matchesSaleItem(stack, minecraftClient.player.getMainHandStack())
                        && AuctionHelper.getInstance().lotPrice(stack) > 0
                        && !AuctionHelper.getInstance().normalizeServerText(stack.getName().getString()).contains(("не актуален"))
                        && (minimumLotUnitPrice == -1 || AuctionHelper.getInstance().lotPrice(stack) / Math.max(1, stack.getCount()) < minimumLotUnitPrice)) {
                        minimumLotUnitPrice = AuctionHelper.getInstance().lotPrice(stack) / Math.max(1, stack.getCount());
                     }
                  }

                  if (minimumLotUnitPrice == -1) {
                     if (!this.stateTimer.hasElapsed((600L))) {
                        return;
                     }

                     minecraftClient.player.closeHandledScreen();
                     this.skippedItemNames.add(this.searchedItemName);
                     this.stateTimer.reset();
                     this.phase = this.sellInventoryMode ? AutoSell.SellingPhase.PICK : AutoSell.SellingPhase.IDLE;
                     return;
                  }

                  minecraftClient.player.closeHandledScreen();
                  this.minimumUnitPrice = minimumLotUnitPrice;
                  this.maximumPriceRejected = false;
                  minecraftClient.player.networkHandler.sendChatCommand(this.sellCommandPrefix() + " " + this.salePrice(minimumLotUnitPrice));
                  this.stateTimer.reset();
                  this.phase = AutoSell.SellingPhase.AFTER_SELL;
                  break;
               }

               if (!this.searchRetried && this.stateTimer.hasElapsed((600L)) && minecraftClient.currentScreen == null) {
                  this.searchRetried = true;
                  this.searchHeldItem();
                  return;
               }

               if (!this.stateTimer.hasElapsed((1800L))) {
                  return;
               }

               this.skippedItemNames.add(this.searchedItemName);
               this.stateTimer.reset();
               this.phase = this.sellInventoryMode ? AutoSell.SellingPhase.PICK : AutoSell.SellingPhase.IDLE;
               return;
            case AFTER_SELL:
               if (!this.stateTimer.hasElapsed((100L))) {
                  return;
               }

               if (this.maximumPriceRejected) {
                  this.maximumPriceRejected = false;
                  if (minecraftClient.player.getMainHandStack().getCount() > 1 && InventoryActions.findEmptySlot(0, 35) != -1) {
                     this.splitItemSlot = InventoryActions.findEmptySlot(0, 35);
                     InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(InventoryActions.selectedHotbarSlot()), 1, SlotActionType.PICKUP, false);
                     InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(this.splitItemSlot), 0, SlotActionType.PICKUP, false);
                     this.splitSalePending = true;
                     this.stateTimer.reset();
                     return;
                  }

                  this.skippedItemNames.add(this.searchedItemName);
                  this.stateTimer.reset();
                  this.phase = this.sellInventoryMode ? AutoSell.SellingPhase.PICK : AutoSell.SellingPhase.IDLE;
                  return;
               }

               if (this.splitSalePending) {
                  if (!this.stateTimer.hasElapsed((300L))) {
                     return;
                  }

                  this.splitSalePending = false;
                  minecraftClient.player.networkHandler.sendChatCommand(this.sellCommandPrefix() + " " + this.salePrice(this.minimumUnitPrice));
                  this.stateTimer.reset();
                  return;
               }

               if (!minecraftClient.player.getMainHandStack().isEmpty() && minecraftClient.player.getMainHandStack().getName().getString().equals(this.searchedItemName)
                  )
                {
                  if (!this.stateTimer.hasElapsed((700L))) {
                     return;
                  }

                  this.skippedItemNames.add(this.searchedItemName);
               }

               this.stateTimer.reset();
               if (!this.sellInventoryMode
                  && this.splitItemSlot != -1
                  && !this.skippedItemNames.contains(this.searchedItemName)
                  && !minecraftClient.player.getInventory().getStack(this.splitItemSlot).isEmpty()) {
                  int splitSlot = this.splitItemSlot;
                  InventorySwapQueue.getInstance().queueSwap(splitSlot, InventoryActions.selectedHotbarSlot(), 1);
                  this.splitItemSlot = -1;
                  this.phase = AutoSell.SellingPhase.SEARCH;
                  return;
               }

               this.phase = this.sellInventoryMode ? AutoSell.SellingPhase.PICK : AutoSell.SellingPhase.IDLE;
               break;
            case SHULKER:
               if (InventorySwapQueue.getInstance().hasPendingSwaps() || ItemUseQueue.getInstance().hasPendingUses()) {
                  return;
               }

               if (!(
                  minecraftClient.currentScreen instanceof GenericContainerScreen shulkerScreen && !AuctionHelper.getInstance().isAuctionTitle(shulkerScreen.getTitle().getString())
               )) {
                  if (!this.stateTimer.hasElapsed((800L))) {
                     return;
                  }

                  if (!minecraftClient.player.currentScreenHandler.getCursorStack().isEmpty()) {
                     InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(this.shulkerSlot), 0, SlotActionType.PICKUP, false);
                  }

                  this.stateTimer.reset();
                  this.phase = AutoSell.SellingPhase.PICK;
                  return;
               }

               if (!this.stateTimer.hasElapsed((200L))) {
                  return;
               }

               if (InventoryActions.findEmptySlot(0, 35) == -1) {
                  minecraftClient.player.closeHandledScreen();
                  this.stateTimer.reset();
                  this.phase = AutoSell.SellingPhase.PICK;
                  return;
               }

               for (int shulkerItemSlot = 0; shulkerItemSlot < minecraftClient.player.currentScreenHandler.slots.size() - 36; shulkerItemSlot++) {
                  if (minecraftClient.player.currentScreenHandler.getSlot(shulkerItemSlot).hasStack()) {
                     InventoryActions.clickSlot(shulkerItemSlot, 0, SlotActionType.QUICK_MOVE, false);
                     this.stateTimer.reset();
                     return;
                  }
               }

               minecraftClient.player.closeHandledScreen();
               this.stateTimer.reset();
               this.phase = AutoSell.SellingPhase.PICK;
         }
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (this.phase == AutoSell.SellingPhase.AFTER_SELL && event.isIncoming() && event.getPacket() instanceof GameMessageS2CPacket chatPacket
         )
       {
         if (MAX_PRICE_MESSAGE.matcher(AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))).matches()) {
            this.maximumPriceRejected = true;
         }
      }
   };
   private static final HashMap<String, String> loreSearchNames = new HashMap<>();
   private static final HashMap<String, String> arrowSearchNames = new HashMap<>();

   private int salePrice(int unitPrice) {
      return Math.max(1, (int)Math.ceil(unitPrice * (1.0F + this.pricePercentage.getValue() / (100.0F))))
         * Math.max(1, minecraftClient.player.getMainHandStack().getCount());
   }

   static {
      gearSearchNames.put(("minecraft:netherite_helmet minecraft:aqua_affinity1 minecraft:blast_protection5 minecraft:fire_protection5 minecraft:mending1 minecraft:projectile_protection5 minecraft:protection5 minecraft:respiration3 minecraft:unbreaking5"), ("Шлем Крушителя"));
      gearSearchNames.put(("minecraft:netherite_chestplate minecraft:blast_protection5 minecraft:fire_protection5 minecraft:mending1 minecraft:projectile_protection5 minecraft:protection5 minecraft:unbreaking5"), ("Нагрудник Крушителя"));
      gearSearchNames.put(("minecraft:netherite_leggings minecraft:blast_protection5 minecraft:fire_protection5 minecraft:mending1 minecraft:projectile_protection5 minecraft:protection5 minecraft:unbreaking5"), ("Поножи Крушителя"));
      gearSearchNames.put(("minecraft:netherite_boots minecraft:blast_protection5 minecraft:depth_strider3 minecraft:feather_falling4 minecraft:fire_protection5 minecraft:mending1 minecraft:projectile_protection5 minecraft:protection5 minecraft:soul_speed3 minecraft:unbreaking5"), ("Ботинки Крушителя"));
      gearSearchNames.put(("minecraft:netherite_sword minecraft:bane_of_arthropods7 minecraft:fire_aspect2 minecraft:looting5 minecraft:mending1 minecraft:sharpness7 minecraft:smite7 minecraft:sweeping_edge3 minecraft:unbreaking5"), ("Меч Крушителя"));
      gearSearchNames.put(("minecraft:netherite_pickaxe minecraft:efficiency10 minecraft:fortune5 minecraft:mending1 minecraft:unbreaking5"), ("Кирка Крушителя"));
      gearSearchNames.put(("minecraft:trident minecraft:channeling1 minecraft:fire_aspect2 minecraft:impaling5 minecraft:loyalty3 minecraft:mending1 minecraft:sharpness7 minecraft:unbreaking5"), ("Трезубец Крушителя"));
      gearSearchNames.put(("minecraft:crossbow minecraft:mending1 minecraft:multishot1 minecraft:piercing5 minecraft:quick_charge3 minecraft:unbreaking3"), ("Арбалет Крушителя"));
      gearSearchNames.put(("minecraft:bow minecraft:flame1 minecraft:infinity1 minecraft:mending1 minecraft:power5 minecraft:unbreaking5"), ("Лук Крушителя"));
      gearSearchNames.put(("minecraft:elytra minecraft:mending1 minecraft:unbreaking5"), ("Элитры Крушителя"));
      loreSearchNames.put(("Хаос искажает реальность"), ("сфера Хаоса"));
      loreSearchNames.put(("Шёпот Сатира звучит"), ("сфера Сатира"));
      loreSearchNames.put(("Звериная дикая мощь"), ("сфера Бестии"));
      loreSearchNames.put(("Дух Ареса пылает внутри"), ("сфера Ареса"));
      loreSearchNames.put(("Живучесть темных глубин"), ("сфера Гидры"));
      loreSearchNames.put(("Хранит волю Икара"), ("сфера Икара"));
      loreSearchNames.put(("Мощь Титанов крепка"), ("сфера Титана"));
      loreSearchNames.put(("Холод Эриды вечен"), ("сфера Эрида"));
      loreSearchNames.put(("Несокрушимая мощь"), ("талисман Крушителя"));
      loreSearchNames.put(("Раздор жаждает хаоса"), ("талисман Раздора"));
      loreSearchNames.put(("Тиран подавляет слабых"), ("талисман Тирана"));
      loreSearchNames.put(("Чистая, дикая агрессия"), ("талисман Ярости"));
      loreSearchNames.put(("Вихрь не знает покоя"), ("талисман Вихря"));
      loreSearchNames.put(("Мрак сгущается рядом"), ("талисман Мрака"));
      loreSearchNames.put(("Несёт строгий приговор"), ("талисман Карателя"));
      loreSearchNames.put(("Печать разжигает ярость"), ("талисман Демона"));
      loreSearchNames.put(("Иссушение III (0:05)"), ("Мучительная стрела"));
      loreSearchNames.put(("Мучительная стрела"), ("Мучительная стрела"));
      loreSearchNames.put(("Замедление III (0:03)"), ("Стрела Зевса"));
      loreSearchNames.put(("Стрела Зевса"), ("Стрела Зевса"));
      loreSearchNames.put(("Тошнота (0:05)"), ("Кровавая стрела"));
      loreSearchNames.put(("Кровавая стрела"), ("Кровавая стрела"));
      loreSearchNames.put(("Стрела обледенения"), ("Стрела обледенения"));
      loreSearchNames.put(("Спешка III (0:05)"), ("Световая стрела"));
      loreSearchNames.put(("Световая стрела"), ("Световая стрела"));
      loreSearchNames.put(("Замедление V (0:07)"), ("Стрела терапии"));
      loreSearchNames.put(("Стрела терапии"), ("Стрела терапии"));
      arrowSearchNames.put(("Замедление III\nИссушение III\nОтравление III"), ("Мучительная стрела"));
      arrowSearchNames.put(("Замедление III\nМоментальный урон II"), ("стрела Зевса"));
      arrowSearchNames.put(("Слабость III\nСлепота\nТошнота\nУтомление"), ("Кровавая стрела"));
      arrowSearchNames.put(("Скорость V\nСлепота\nСпешка III"), ("Световая стрела"));
      arrowSearchNames.put(("Замедление V\nИсцеление\nСлабость"), ("стрела терапии"));
      arrowSearchNames.put(("Замедление IX\nУтомление III"), ("стрела обледенения"));
      arrowSearchNames.put(("Замедление potion.potency.8\nУтомление III"), ("стрела обледенения"));
   }

   private String specialSearchName(ItemStack stack) {
      Matcher nameMatcher = Pattern.compile(("(?i)(Сфера|Талисман) (Хаоса|Сатира|Бестии|Ареса|Гидры|Икара|Титана|Эрида|Крушителя|Раздора|Тирана|Ярости|Вихря|Мрака|Карателя|Демона)")).matcher(Formatting.strip(stack.getName().getString()));
      if (nameMatcher.find()) {
         return nameMatcher.group(1).toLowerCase() + " " + nameMatcher.group(2);
      }

      for (Entry nameEntry : loreSearchNames.entrySet()) {
         if (Formatting.strip(stack.getName().getString()).contains((CharSequence)nameEntry.getKey())) {
            return (String)nameEntry.getValue();
         }
      }

      if (stack.get(DataComponentTypes.LORE) == null) {
         return "";
      }

      for (Text text : stack.get(DataComponentTypes.LORE).lines()) {
         for (Entry loreEntry : loreSearchNames.entrySet()) {
            if (Formatting.strip(text.getString()).contains((CharSequence)loreEntry.getKey())) {
               return (String)loreEntry.getValue();
            }
         }
      }

      return "";
   }

   private int enchantmentLevel(ItemStack stack, String enchantmentId) {
      for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry enchantment : stack.getEnchantments().getEnchantmentEntries()) {
         if (((RegistryEntry)enchantment.getKey()).getIdAsString().equals(enchantmentId)) {
            return enchantment.getIntValue();
         }
      }

      return 0;
   }

   private HashSet<String> customEnchantmentSignature(ItemStack stack) {
      NbtCompound customData = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt().getCompoundOrEmpty(("PublicBukkitValues"));
      HashSet enchantments = new HashSet();

      for (String enchantmentKey : customData.getKeys()) {
         if (enchantmentKey.startsWith(("spookyenchantments:")) && enchantmentKey.endsWith(("lvl"))) {
            enchantments.add(enchantmentKey + customData.getInt(enchantmentKey, 0));
         }
      }

      return enchantments;
   }

   private String auctionSearchName(ItemStack stack) {
      if (!this.arrowEffectLabel(stack).isEmpty()) {
         return arrowSearchNames.getOrDefault(this.arrowEffectLabel(stack), this.arrowEffectLabel(stack).split(("\n"))[0]);
      } else if (!this.specialSearchName(stack).isEmpty()) {
         return this.specialSearchName(stack);
      } else {
         return this.gearSearchName(stack).isEmpty()
            ? Formatting.strip(stack.getName().getString()).replaceAll(("[^\\p{L} ]"), (" ")).replaceAll((" +"), (" ")).trim()
            : this.gearSearchName(stack);
      }
   }

   private String sellCommandPrefix() {
      return this.sellCommand.getValue().isBlank()
         ? ("ah sell")
         : (
            this.sellCommand.getValue().trim().startsWith(("/"))
               ? this.sellCommand.getValue().trim().substring(1)
               : this.sellCommand.getValue().trim()
         );
   }

   private boolean matchesSaleItem(ItemStack candidate, ItemStack target) {
      if (!this.arrowEffectLabel(target).isEmpty()) {
         return this.arrowEffectLabel(target).equals(this.arrowEffectLabel(candidate));
      } else if (!this.specialSearchName(target).isEmpty()) {
         return this.specialSearchName(target).equals(this.specialSearchName(candidate));
      } else {
         return !this.gearSearchName(target).isEmpty()
            ? this.gearSearchName(target).equals(this.gearSearchName(candidate))
            : candidate.getEnchantments().equals(target.getEnchantments())
               && candidate.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
                  .equals(target.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT))
               && this.customEnchantmentSignature(candidate).equals(this.customEnchantmentSignature(target))
               && this.potionEffectSignature(candidate).equals(this.potionEffectSignature(target));
      }
   }

   private String gearSearchName(ItemStack stack) {
      for (Entry gearEntry : gearSearchNames.entrySet()) {
         if (this.matchesGearSignature(stack, (String)gearEntry.getKey())) {
            return (String)gearEntry.getValue();
         }
      }

      return "";
   }

   private boolean matchesGearSignature(ItemStack stack, String signature) {
      for (String requirement : signature.split((" "))) {
         if (!Character.isDigit(requirement.charAt(requirement.length() - 1))) {
            if (!Registries.ITEM.getId(stack.getItem()).toString().equals(requirement)) {
               return false;
            }
         } else if (this.enchantmentLevel(stack, requirement.replaceAll(("\\d+$"), "")) < Integer.parseInt(requirement.replaceAll(("^.*?(\\d+)$"), ("$1")))) {
            return false;
         }
      }

      return true;
   }

   private AutoSell() {
      this.pricePercentage = this.addSetting(new SliderSetting(("Процент к цене"), ("Сколько процентов накинуть на минимальную цену лота, минус выставлять дешевле.")).withIntegerRange(-90, 300).withSuffix(("%")).setValue(0.0F));
      this.searchCommand = this.addSetting(new FilteredTextSetting(("Команда поиска"), ("Команда, к которой допишется имя предмета из руки."), ("ah search"), 50));
      this.sellCommand = this.addSetting(new FilteredTextSetting(("Команда продажи"), ("Команда, к которой допишется посчитанная цена."), ("ah sell"), 50));
      this.sellHeldItemBind = this.addSetting(new BindSetting(("Продать с руки"), ("Выставляет предмет из главной руки.")));
      this.sellInventoryBind = this.addSetting(new BindSetting(("Продать инвентарь"), ("Выставляет весь инвентарь и содержимое шалкеров, сами шалкеры не продаёт.")));
   }

   @Override
   protected void onDisable() {
      this.phase = AutoSell.SellingPhase.IDLE;
   }

   public static AutoSell getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoSell();
      }

      return INSTANCE;
   }

   private HashSet<String> potionEffectSignature(ItemStack stack) {
      HashSet effects = new HashSet();

      for (StatusEffectInstance effect : stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT).getEffects()) {
         effects.add(effect.getEffectType().getIdAsString() + effect.getAmplifier() + "/" + effect.getDuration());
      }

      return effects;
   }

   private void searchHeldItem() {
      if (minecraftClient.player != null && !minecraftClient.player.getMainHandStack().isEmpty()) {
         PotionContentsComponent potionContents = minecraftClient.player.getMainHandStack().get(DataComponentTypes.POTION_CONTENTS);
         if (potionContents != null) {
            for (StatusEffectInstance effect : potionContents.getEffects()) {
               if (effect.getEffectType().value() == StatusEffects.INSTANT_HEALTH.value() || effect.getEffectType().value() == StatusEffects.STRENGTH.value()) {
                  minecraftClient.player
                     .networkHandler
                     .sendChatCommand(
                        (
                              this.searchCommand.getValue().isBlank()
                                 ? ("ah search")
                                 : (
                                    this.searchCommand.getValue().trim().startsWith(("/"))
                                       ? this.searchCommand.getValue().trim().substring(1)
                                       : this.searchCommand.getValue().trim()
                                 )
                           )
                           + " "
                           + (effect.getEffectType().value() == StatusEffects.STRENGTH.value() ? ("Зелье силы") : ("Зелье исцеления"))
                     );
                  return;
               }
            }
         }

         if (!this.auctionSearchName(minecraftClient.player.getMainHandStack()).isEmpty()) {
            minecraftClient.player
               .networkHandler
               .sendChatCommand(
                  (
                        this.searchCommand.getValue().isBlank()
                           ? ("ah search")
                           : (
                              this.searchCommand.getValue().trim().startsWith(("/"))
                                 ? this.searchCommand.getValue().trim().substring(1)
                                 : this.searchCommand.getValue().trim()
                           )
                     )
                     + " "
                     + this.auctionSearchName(minecraftClient.player.getMainHandStack())
               );
         }
      }
   }

   private String arrowEffectLabel(ItemStack stack) {
      if (!stack.isOf(Items.TIPPED_ARROW)) {
         return "";
      }

      String[] romanSuffixes = new String[]{"", (" II"), (" III"), (" IV"), (" V"), (" VI"), (" VII"), (" VIII"), (" IX"), (" X")};
      ArrayList effectLabels = new ArrayList();
      if (stack.get(DataComponentTypes.POTION_CONTENTS) != null) {
         for (StatusEffectInstance effect : stack.get(DataComponentTypes.POTION_CONTENTS).getEffects()) {
            effectLabels.add(effect.getEffectType().value().getName().getString().replace(("Мгновенное исцеление"), ("Исцеление")) + romanSuffixes[Math.max(0, Math.min(9, effect.getAmplifier()))]);
         }
      }

      if (effectLabels.isEmpty() && stack.get(DataComponentTypes.LORE) != null) {
         for (Text text : stack.get(DataComponentTypes.LORE).lines()) {
            if (Formatting.strip(text.getString()).trim().isEmpty()) {
               break;
            }

            effectLabels.add(Formatting.strip(text.getString()).trim().replaceAll(("\\s*\\(.*"), ""));
         }
      }

      Collections.sort(effectLabels);
      return String.join(("\n"), effectLabels);
   }

   private void toggleSelling(boolean inventoryMode) {
      if (this.phase != AutoSell.SellingPhase.IDLE) {
         this.phase = AutoSell.SellingPhase.IDLE;
      } else {
         this.sellInventoryMode = inventoryMode;
         this.skippedItemNames.clear();
         this.visitedShulkerSlots.clear();
         this.maximumPriceRejected = false;
         this.splitSalePending = false;
         this.splitItemSlot = -1;
         this.stateTimer.reset();
         this.phase = inventoryMode ? AutoSell.SellingPhase.PICK : AutoSell.SellingPhase.SEARCH;
      }
   }

   private enum SellingPhase {
      IDLE,
      PICK,
      SEARCH,
      WAIT_LOTS,
      AFTER_SELL,
      SHULKER;
   }
}
