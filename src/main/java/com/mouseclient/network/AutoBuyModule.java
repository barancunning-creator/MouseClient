package com.mouseclient.network;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.gui.AutoBuyScreen;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.AuctionHelper;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.player.ServerItemDefinition;
import com.mouseclient.modules.impl.player.ServerItemCatalog;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.SettingSeparator;
import com.mouseclient.render.pipeline.SliderSetting;
import com.mouseclient.util.time.AutoBuyGuiController;
import com.mouseclient.util.time.AutoBuyState;
import com.mouseclient.util.time.Stopwatch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

@ModuleInfo(name = "AutoBuy", description = "Скупает лоты с аукциона по своим ценам, меню открывается биндом.", category = ModuleCategory.MISC)
public final class AutoBuyModule extends Module {
   private static AutoBuyModule INSTANCE;
   private static final Pattern LISTED_MESSAGE_PATTERN = Pattern.compile((".*выставлен на продажу за.*"));
   private static final Pattern SOLD_MESSAGE_PATTERN = Pattern.compile((".*у вас купили.*"));
   private static final Pattern SOLD_DETAILS_PATTERN = Pattern.compile(("^\\[☃] У Вас купили (.+) за \\$([\\d,]+) на /ah$"));
   private static final Pattern PURCHASE_MESSAGE_PATTERN = Pattern.compile((".*вы успешно купили.*"));
   private static final Pattern MAX_PRICE_MESSAGE_PATTERN = Pattern.compile((".*максимальная цена на этот товар.*"));
   private static final Pattern REMOVED_MESSAGE_PATTERN = Pattern.compile((".*вы убрали предмет с продажи.*"));
   private static final String[] RANK_NAMES = new String[]{
      ("Игрок"),
      ("Барон"),
      ("Страж"),
      ("Герой"),
      ("Аспид"),
      ("Сквид"),
      ("Глава"),
      ("Элита"),
      ("Титан"),
      ("Принц"),
      ("Князь"),
      ("Герцог")
   };
   private static final int[] RANK_AUCTION_LIMITS = new int[]{3, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 21};
   public BindSetting openMenuBind;
   public BooleanSetting autoBuy;
   public BooleanSetting autoParser;
   public SliderSetting buyPercentage;
   public SliderSetting sellPercentage;
   public SliderSetting relistDiscount;
   public SliderSetting reparse;
   private boolean menuKeyWasPressed;
   private AutoBuyModule.AuctionPhase phase = AutoBuyModule.AuctionPhase.IDLE;
   private final Stopwatch stateTimer = new Stopwatch();
   private final Stopwatch purchaseTimer = new Stopwatch();
   private final Stopwatch auctionRefreshTimer = new Stopwatch();
   private final Stopwatch reparseTimer = new Stopwatch();
   private final Stopwatch resellTimer = new Stopwatch();
   private final Stopwatch _346 = new Stopwatch();
   private final Stopwatch reconnectTimer = new Stopwatch();
   private final ArrayList<String> priceParseQueue = new ArrayList<>();
   private final ArrayList<String> saleQueue = new ArrayList<>();
   private final ArrayList<String> specialSaleItems = new ArrayList<>();
   private final ArrayList<Integer> clickedAuctionSlots = new ArrayList<>();
   private final HashMap<String, Long> parsedSalePrices = new HashMap<>();
   private final HashMap<String, Long> hashMap2 = new HashMap<>();
   private ServerInfo lastServer;
   private String anarchyCommand;
   private String auctionPageCommand;
   private String screenTitle;
   private String parsingItemName;
   private String sellingItemName;
   private int listedStackCount;
   private boolean resumeSellingAfterParse;
   private boolean lotUnavailable;
   private int purchaseCount;
   private boolean purchasePending;
   private boolean listingConfirmed;
   private boolean maximumPriceRejected;
   private boolean splitSalePending;
   private final HashSet<String> rejectedSaleItems;
   private int remainingSales;
   private int saleCount;
   private int activeLotCount;
   private int auctionSlotLimit;
   private final HashMap<String, Long> relistUnitPrices;
   private final HashMap<String, Integer> relistCounts;
   private final Stopwatch relistTimer;
   private int pulledRelistCount;
   private boolean relistPullReady;
   private final HashSet<String> pricedRelistItems;
   private final Stopwatch saleActivityTimer;
   private final Stopwatch stashTimer;
   private final Stopwatch sellRetryTimer;
   private int sellFailures;
   private int sellRetryExponent;
   private final HashSet<String> missingStashItems;
   private int remainingFlipPurchases;
   private long lastInventoryUpdateAt;
   private final EventListener<PlayerTickEvent> playerTickListener;
   private final EventListener<ClientTickEvent> clientTickListener;
   private final EventListener<PacketEvent> packetListener;

   private boolean nonAuctionContainerOpen() {
      return minecraftClient.player.currentScreenHandler != minecraftClient.player.playerScreenHandler && !AuctionHelper.getInstance().isAuctionTitle(this.screenTitle);
   }

   @Override
   protected void onDisable() {
      this.autoBuy.setValue(false);
      this.autoParser.setValue(false);
      this.priceParseQueue.clear();
      this.specialSaleItems.clear();
      this.clickedAuctionSlots.clear();
      this.resumeSellingAfterParse = false;
      this.purchaseCount = 0;
      this.remainingSales = 0;
      this.saleCount = 0;
      this.activeLotCount = 0;
      this.purchasePending = false;
      this.remainingFlipPurchases = 0;
      this.maximumPriceRejected = false;
      this.splitSalePending = false;
      this.rejectedSaleItems.clear();
      this.relistUnitPrices.clear();
      this.relistCounts.clear();
      this.pulledRelistCount = 0;
      this.relistPullReady = false;
      this.pricedRelistItems.clear();
      this.sellRetryExponent = 0;
      this.sellFailures = 0;
      this.missingStashItems.clear();
      this.auctionSlotLimit = RANK_AUCTION_LIMITS[0];
      this.phase = AutoBuyModule.AuctionPhase.IDLE;
   }

   private boolean requiresSpecialSaleHandling(String itemName) {
      return this.specialSaleItems.contains(itemName) || ServerItemCatalog.findByName(itemName) != null && ServerItemCatalog.isSphere(ServerItemCatalog.findByName(itemName));
   }

   private long unitSalePrice(String itemName) {
      if (this.relistUnitPrices.getOrDefault(itemName, 0L) > 0L) {
         return this.relistUnitPrices.get(itemName);
      } else {
         return AutoBuyState.getInstance().selectedItems.get(itemName) != null
            ? AutoBuyState.parseCoins(AutoBuyState.getInstance().selectedItems.get(itemName).minSellPrice.getValue())
            : this.parsedSalePrices.getOrDefault(itemName, 0L);
      }
   }

   private long selectedDelayMillis(long unusedFirstDelay, long unusedSecondDelay, long delayMillis) {
      return delayMillis;
   }

   public boolean isBuyingActive() {
      return this.autoBuy.getValue();
   }

   public static AutoBuyModule getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoBuyModule();
      }

      return INSTANCE;
   }

   private int saleStackCount(String itemName, int stackCount) {
      return ServerItemCatalog.findByName(itemName) != null && ServerItemCatalog.isSphere(ServerItemCatalog.findByName(itemName)) ? 1 : stackCount;
   }

   private boolean splitHeldStack() {
      Slot slot = null;
      Slot emptySlot = null;

      for (Slot inventorySlot : minecraftClient.player.currentScreenHandler.slots) {
         if (inventorySlot.inventory == minecraftClient.player.getInventory() && inventorySlot.getIndex() <= 35) {
            if (inventorySlot.getIndex() == InventoryActions.selectedHotbarSlot()) {
               slot = inventorySlot;
            } else if (emptySlot == null && !inventorySlot.hasStack()) {
               emptySlot = inventorySlot;
            }
         }
      }

      if (slot != null && emptySlot != null) {
         InventoryActions.clickSlot(slot.id, 1, SlotActionType.PICKUP, false);
         InventoryActions.clickSlot(emptySlot.id, 0, SlotActionType.PICKUP, false);
         return true;
      } else {
         return false;
      }
   }

   private int occupiedInventorySlots() {
      int occupiedCount = 0;

      for (int inventorySlot = 0; inventorySlot <= 35; inventorySlot++) {
         if (!minecraftClient.player.getInventory().getStack(inventorySlot).isEmpty()) {
            occupiedCount++;
         }
      }

      return occupiedCount;
   }

   public void togglePriceParser() {
      this.autoParser.setValue(!this.autoParser.getValue());
      this.priceParseQueue.clear();
      this.phase = AutoBuyModule.AuctionPhase.IDLE;
      if (!this.autoParser.getValue()) {
         ChatMessages.printPrefixed(("Автопарсер остановлен."));
      } else if (AutoBuyState.getInstance().selectedItems.isEmpty()) {
         ChatMessages.printPrefixed(("В меню AutoBuy не выбрано ни одного предмета."));
      } else {
         this.priceParseQueue.addAll(AutoBuyState.getInstance().selectedItems.keySet());
         this.stateTimer.reset();
         this.phase = AutoBuyModule.AuctionPhase.SEND;
      }
   }

   private boolean auctionOpen() {
      return minecraftClient.player.currentScreenHandler != minecraftClient.player.playerScreenHandler && AuctionHelper.getInstance().isAuctionTitle(this.screenTitle);
   }

   public void toggleBuying() {
      this.autoBuy.setValue(!this.autoBuy.getValue());
      this.clickedAuctionSlots.clear();
      ChatMessages.printPrefixed(
         this.autoBuy.getValue()
            ? "Автобай запущен, предметов в списке: &7" + AutoBuyState.getInstance().selectedItems.size() + "&r, лотов по донату: &7" + this.auctionSlotLimit
            : ("Автобай остановлен.")
      );
   }

   private int emptyInventorySlots() {
      return 36 - this.occupiedInventorySlots();
   }

   private int scoreboardBalance() {
      return minecraftClient.world != null && minecraftClient.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR) != null
         ? minecraftClient.world
            .getScoreboard()
            .getTeams()
            .stream()
            .filter(scoreboardTeam -> scoreboardTeam.getName().startsWith(("TAB-Sidebar-")))
            .map(scoreboardTeam -> scoreboardTeam.getPrefix().getString().toLowerCase(Locale.ROOT))
            .filter(balanceText -> balanceText.contains(("монеты")) || balanceText.contains(("$")))
            .map(balanceText -> balanceText.replaceAll(("\\D"), ""))
            .filter(balanceText -> !balanceText.isEmpty() && balanceText.length() <= 9)
            .map(Integer::parseInt)
            .findFirst()
            .orElse(-1)
         : -1;
   }

   public boolean isPriceParserActive() {
      return this.autoParser.getValue();
   }

   private AutoBuyModule() {
      this.auctionPageCommand = ("ah");
      this.screenTitle = "";
      this.parsingItemName = "";
      this.sellingItemName = "";
      this.listedStackCount = 1;
      this.rejectedSaleItems = new HashSet<>();
      this.auctionSlotLimit = RANK_AUCTION_LIMITS[0];
      this.relistUnitPrices = new HashMap<>();
      this.relistCounts = new HashMap<>();
      this.relistTimer = new Stopwatch();
      this.pricedRelistItems = new HashSet<>();
      this.saleActivityTimer = new Stopwatch();
      this.stashTimer = new Stopwatch();
      this.sellRetryTimer = new Stopwatch();
      this.missingStashItems = new HashSet<>();
      this.playerTickListener = event -> {
         if (minecraftClient.player != null && minecraftClient.getWindow() != null) {
            if (this.openMenuBind.isBound()
               && this.openMenuBind.getBinding().isPressed(minecraftClient.getWindow().getHandle())
               && !this.menuKeyWasPressed
               && !(minecraftClient.currentScreen instanceof ChatScreen)
               && !(minecraftClient.currentScreen instanceof AutoBuyScreen)) {
               AutoBuyGuiController.getInstance().open();
               minecraftClient.setScreen(AutoBuyScreen.instance());
            }

            this.menuKeyWasPressed = this.openMenuBind.isBound() && this.openMenuBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            this.reconnectTimer.reset();
            if (minecraftClient.getCurrentServerEntry() != null) {
               this.lastServer = minecraftClient.getCurrentServerEntry();
            }

            ScoreboardObjective sidebarObjective = minecraftClient.world == null
               ? null
               : minecraftClient.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (sidebarObjective != null
               && sidebarObjective.getDisplayName().getString().split(("-")).length > 1
               && !sidebarObjective.getDisplayName().getString().split(("-"))[1].replaceAll(("\\D"), "").isEmpty()) {
               this.anarchyCommand = "an" + sidebarObjective.getDisplayName().getString().split(("-"))[1].replaceAll(("\\D"), "");
            }

            label838:
            for (ScoreboardEntry scoreboardEntry : sidebarObjective == null ? List.<ScoreboardEntry>of() : minecraftClient.world.getScoreboard().getScoreboardEntries(sidebarObjective)) {
               String rankText = AuctionHelper.getInstance()
                  .normalizeServerText(
                     Team.decorateName(minecraftClient.world.getScoreboard().getScoreHolderTeam(scoreboardEntry.owner()), scoreboardEntry.name())
                        .getString()
                        .replaceAll(("(?i)§[0-9a-fk-or]"), "")
                  );
               if (rankText.contains(("ранг"))) {
                  for (int rankIndex = 0; rankIndex < RANK_NAMES.length; rankIndex++) {
                     if (rankText.contains(AuctionHelper.getInstance().normalizeServerText(RANK_NAMES[rankIndex]))) {
                        this.auctionSlotLimit = RANK_AUCTION_LIMITS[rankIndex];
                        break label838;
                     }
                  }
                  break;
               }
            }

            if (this.phase == AutoBuyModule.AuctionPhase.IDLE
               && InventoryActions.findSlot(stack -> ServerItemCatalog.isShulkerBox(stack.getItem())) != -1
               && (
                  InventoryActions.findEmptySlot(0, 35) == -1 && this.stashTimer.hasElapsed((8000L) << this.sellRetryExponent)
                     || !this.saleQueue.isEmpty() && this.occupiedInventorySlots() > 20 && this.stashTimer.hasElapsed((60000L))
               )) {
               this.stashTimer.reset();
               this.stateTimer.reset();
               this.phase = AutoBuyModule.AuctionPhase.STASH;
            }

            if (this.phase == AutoBuyModule.AuctionPhase.IDLE
               && !this.saleQueue.isEmpty()
               && InventoryActions.findEmptySlot(0, 35) == -1
               && this.sellRetryTimer.hasElapsed((5000L) << this.sellRetryExponent)) {
               this.remainingSales = this.saleQueue.size();
               this.stateTimer.reset();
               this.phase = AutoBuyModule.AuctionPhase.SELL_PICK;
            }

            if (this.phase == AutoBuyModule.AuctionPhase.IDLE) {
               for (int inventorySlot = 0; inventorySlot <= 35; inventorySlot++) {
                  ServerItemDefinition inventoryDefinition = minecraftClient.player.getInventory().getStack(inventorySlot).isEmpty()
                     ? null
                     : ServerItemCatalog.findMatching(minecraftClient.player.getInventory().getStack(inventorySlot));
                  if (inventoryDefinition != null
                     && AutoBuyState.getInstance().selectedItems.containsKey(inventoryDefinition.name())
                     && !this.rejectedSaleItems.contains(inventoryDefinition.name())
                     && !this.saleQueue.contains(inventoryDefinition.name())) {
                     this.saleQueue.add(inventoryDefinition.name());
                  }
               }
            }

            if (this.phase == AutoBuyModule.AuctionPhase.IDLE
               && this.activeLotCount < this.auctionSlotLimit
               && this.saleQueue.size() >= this.auctionSlotLimit - this.activeLotCount
               && this.saleActivityTimer.hasElapsed((2000L))
               && this.sellRetryTimer.hasElapsed((5000L) << this.sellRetryExponent)) {
               this.remainingSales = this.auctionSlotLimit - this.activeLotCount;
               this.stateTimer.reset();
               this.phase = AutoBuyModule.AuctionPhase.SELL_PICK;
            }

            if (this.phase == AutoBuyModule.AuctionPhase.IDLE
               && this.autoBuy.getValue()
               && !this.saleQueue.isEmpty()
               && this.activeLotCount >= this.auctionSlotLimit
               && this.emptyInventorySlots() >= this.activeLotCount
               && this.saleActivityTimer.hasElapsed((3000L))
               && this.relistTimer.hasElapsed((15000L))) {
               this.relistTimer.reset();
               this.stateTimer.reset();
               this.phase = AutoBuyModule.AuctionPhase.RELIST_SEARCH;
            }

            if (this.phase == AutoBuyModule.AuctionPhase.IDLE && this.resellTimer.hasElapsed((60000L))) {
               this.resellTimer.reset();
               this.stateTimer.reset();
               this.phase = AutoBuyModule.AuctionPhase.RESELL;
            }

            if (this.autoParser.getValue()
               && this.phase == AutoBuyModule.AuctionPhase.IDLE
               && this.reparse.intValue() > 0
               && this.reparseTimer.hasElapsed(this.reparse.intValue() * (60000L))) {
               this.priceParseQueue.addAll(AutoBuyState.getInstance().selectedItems.keySet());
               this.stateTimer.reset();
               this.phase = AutoBuyModule.AuctionPhase.SEND;
            }

            switch (this.phase) {
               case IDLE:
               default:
                  break;
               case SEND:
                  if (!this.stateTimer.hasElapsed(this.selectedDelayMillis((300L), (200L), (400L)))) {
                     return;
                  }

                  if (this.priceParseQueue.isEmpty()) {
                     this.reparseTimer.reset();
                     if (this.autoBuy.getValue() && (!this.resumeSellingAfterParse || this.saleQueue.isEmpty())) {
                        minecraftClient.player.networkHandler.sendChatCommand(this.auctionPageCommand);
                     }

                     if (this.resumeSellingAfterParse && !this.saleQueue.isEmpty()) {
                        this.remainingSales = this.saleQueue.size();
                     }

                     this.stateTimer.reset();
                     this.phase = this.resumeSellingAfterParse && !this.saleQueue.isEmpty()
                        ? AutoBuyModule.AuctionPhase.SELL_PICK
                        : AutoBuyModule.AuctionPhase.IDLE;
                     return;
                  }

                  this.parsingItemName = this.priceParseQueue.remove(0);
                  minecraftClient.player.networkHandler.sendChatCommand("ah search " + this.parsingItemName);
                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.WAIT_LOTS;
                  break;
               case WAIT_LOTS:
                  if (!this.auctionOpen()) {
                     if (!this.stateTimer.hasElapsed((2500L))) {
                        return;
                     }

                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.SEND;
                     return;
                  }

                  if (!this.stateTimer.hasElapsed(this.selectedDelayMillis((450L), (350L), (600L)))) {
                     return;
                  }

                  int minimumParsedUnitPrice = -1;

                  for (int parseLotSlot = 0; parseLotSlot < minecraftClient.player.currentScreenHandler.slots.size() - 36 - 9; parseLotSlot++) {
                     ItemStack parseLotStack = minecraftClient.player.currentScreenHandler.getSlot(parseLotSlot).getStack();
                     if (!parseLotStack.isEmpty()
                        && ServerItemCatalog.findByName(this.parsingItemName) != null
                        && ServerItemCatalog.matches(parseLotStack, ServerItemCatalog.findByName(this.parsingItemName))
                        && AuctionHelper.getInstance().lotPrice(parseLotStack) > 0
                        && (minimumParsedUnitPrice == -1 || AuctionHelper.getInstance().lotPrice(parseLotStack) / Math.max(1, parseLotStack.getCount()) < minimumParsedUnitPrice)) {
                        minimumParsedUnitPrice = AuctionHelper.getInstance().lotPrice(parseLotStack) / Math.max(1, parseLotStack.getCount());
                     }
                  }

                  if (minimumParsedUnitPrice != -1) {
                     this.parsedSalePrices.put(this.parsingItemName, Math.max(1L, Math.round(minimumParsedUnitPrice * this.sellPercentage.floatValue() / (100.0F))));
                  }

                  if (minimumParsedUnitPrice != -1) {
                     this.relistUnitPrices.remove(this.parsingItemName);
                     this.relistCounts.remove(this.parsingItemName);
                  }

                  if (AutoBuyState.getInstance().selectedItems.get(this.parsingItemName) != null) {
                     AutoBuyState.getInstance().selectedItems.get(this.parsingItemName)
                        .maxBuyPrice
                        .setValue(Long.toString(Math.max(1L, Math.round(minimumParsedUnitPrice * this.buyPercentage.floatValue() / (100.0F)))));
                     AutoBuyState.getInstance().selectedItems.get(this.parsingItemName)
                        .minSellPrice
                        .setValue(Long.toString(Math.max(1L, Math.round(minimumParsedUnitPrice * this.sellPercentage.floatValue() / (100.0F)))));
                  }

                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.SEND;
                  break;
               case SELL_PICK:
                  if (this.saleQueue.isEmpty() || this.remainingSales <= 0) {
                     this.resumeSellingAfterParse = false;
                     this.clickedAuctionSlots.clear();
                     if (this.autoBuy.getValue()) {
                        minecraftClient.player.networkHandler.sendChatCommand(this.auctionPageCommand);
                     }

                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.IDLE;
                     return;
                  }

                  if (!this.stateTimer.hasElapsed((250L))) {
                     return;
                  }

                  if (this.sellFailures > 3) {
                     this.sellFailures = 0;
                     this.remainingSales = 0;
                     this.resumeSellingAfterParse = false;
                     this.clickedAuctionSlots.clear();
                     this.sellRetryTimer.reset();
                     if (this.autoBuy.getValue()) {
                        minecraftClient.player.networkHandler.sendChatCommand(this.auctionPageCommand);
                     }

                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.IDLE;
                     return;
                  }

                  if (minecraftClient.player.currentScreenHandler != minecraftClient.player.playerScreenHandler) {
                     minecraftClient.player.closeHandledScreen();
                     this.stateTimer.reset();
                     return;
                  }

                  if (ServerItemCatalog.findByName(this.saleQueue.get(0)) != null
                     && ServerItemCatalog.matches(minecraftClient.player.getMainHandStack(), ServerItemCatalog.findByName(this.saleQueue.get(0)))
                     && minecraftClient.player.getMainHandStack().getCount()
                        == this.saleStackCount(this.saleQueue.get(0), minecraftClient.player.getMainHandStack().getCount())) {
                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.SELL_SEND;
                     return;
                  }

                  for (int saleQueueIndex = 0; saleQueueIndex < this.saleQueue.size(); saleQueueIndex++) {
                     for (Slot slot : minecraftClient.player.currentScreenHandler.slots) {
                        if (slot.inventory == minecraftClient.player.getInventory()
                           && ServerItemCatalog.findByName(this.saleQueue.get(saleQueueIndex)) != null
                           && ServerItemCatalog.matches(slot.getStack(), ServerItemCatalog.findByName(this.saleQueue.get(saleQueueIndex)))
                           && slot.getStack().getCount() == this.saleStackCount(this.saleQueue.get(saleQueueIndex), slot.getStack().getCount())) {
                           this.saleQueue.add(0, this.saleQueue.remove(saleQueueIndex));
                           if (slot.getIndex() <= 8) {
                              InventoryActions.selectHotbarSlot(slot.getIndex(), false);
                           } else {
                              InventoryActions.clickSlot(slot.id, InventoryActions.selectedHotbarSlot(), SlotActionType.SWAP, false);
                           }

                           this.stateTimer.reset();
                           this.phase = AutoBuyModule.AuctionPhase.SELL_SEND;
                           return;
                        }
                     }
                  }

                  for (int splitQueueIndex = 0; splitQueueIndex < this.saleQueue.size(); splitQueueIndex++) {
                     for (Slot splitSourceSlot : minecraftClient.player.currentScreenHandler.slots) {
                        if (splitSourceSlot.inventory == minecraftClient.player.getInventory()
                           && ServerItemCatalog.findByName(this.saleQueue.get(splitQueueIndex)) != null
                           && ServerItemCatalog.matches(splitSourceSlot.getStack(), ServerItemCatalog.findByName(this.saleQueue.get(splitQueueIndex)))
                           && splitSourceSlot.getStack().getCount() > this.saleStackCount(this.saleQueue.get(splitQueueIndex), splitSourceSlot.getStack().getCount())) {
                           for (Slot splitDestinationSlot : minecraftClient.player.currentScreenHandler.slots) {
                              if (splitDestinationSlot.inventory == minecraftClient.player.getInventory() && !splitDestinationSlot.hasStack()) {
                                 InventoryActions.clickSlot(splitSourceSlot.id, 0, SlotActionType.PICKUP, false);

                                 for (int splitItemIndex = 0; splitItemIndex < this.saleStackCount(this.saleQueue.get(splitQueueIndex), splitSourceSlot.getStack().getCount()); splitItemIndex++) {
                                    InventoryActions.clickSlot(splitDestinationSlot.id, 1, SlotActionType.PICKUP, false);
                                 }

                                 InventoryActions.clickSlot(splitSourceSlot.id, 0, SlotActionType.PICKUP, false);
                                 this.stateTimer.reset();
                                 return;
                              }
                           }
                        }
                     }
                  }

                  if (!this.stateTimer.hasElapsed((1500L))) {
                     return;
                  }

                  if (InventoryActions.findSlot(candidateStack -> ServerItemCatalog.isShulkerBox(candidateStack.getItem())) != -1 && this.stashTimer.hasElapsed((8000L))) {
                     this.stashTimer.reset();
                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.STASH;
                     return;
                  }

                  this.sellFailures++;
                  this.saleQueue
                     .removeIf(
                        candidateItemName -> (
                              ServerItemCatalog.findByName(candidateItemName) == null
                                 || InventoryActions.findSlot(candidateStack -> ServerItemCatalog.matches(candidateStack, ServerItemCatalog.findByName(candidateItemName))) == -1
                           )
                           && (InventoryActions.findSlot(candidateStack -> ServerItemCatalog.isShulkerBox(candidateStack.getItem())) == -1 || this.missingStashItems.contains(candidateItemName))
                     );
                  this.sellRetryExponent = Math.min(6, this.sellRetryExponent + 1);
                  this.remainingSales = 0;
                  this.resumeSellingAfterParse = false;
                  this.clickedAuctionSlots.clear();
                  this.sellRetryTimer.reset();
                  if (this.autoBuy.getValue()) {
                     minecraftClient.player.networkHandler.sendChatCommand(this.auctionPageCommand);
                  }

                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.IDLE;
                  break;
               case SELL_SEND:
                  if (!this.stateTimer.hasElapsed((200L))) {
                     return;
                  }

                  if (minecraftClient.player.currentScreenHandler != minecraftClient.player.playerScreenHandler) {
                     minecraftClient.player.closeHandledScreen();
                     this.stateTimer.reset();
                     return;
                  }

                  if (this.saleQueue.isEmpty() || ServerItemCatalog.findMatching(minecraftClient.player.getMainHandStack()) == null) {
                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.SELL_PICK;
                     return;
                  }

                  this.sellingItemName = ServerItemCatalog.findMatching(minecraftClient.player.getMainHandStack()).name();
                  if ((
                        AutoBuyState.getInstance().selectedItems.get(this.sellingItemName) != null
                           ? AutoBuyState.parseCoins(AutoBuyState.getInstance().selectedItems.get(this.sellingItemName).minSellPrice.getValue())
                           : this.parsedSalePrices.getOrDefault(this.sellingItemName, 0L)
                     )
                     <= 0L) {
                     if (!this.priceParseQueue.contains(this.sellingItemName)) {
                        this.priceParseQueue.add(this.sellingItemName);
                     }

                     this.resumeSellingAfterParse = true;
                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.SEND;
                     return;
                  }

                  this.listingConfirmed = false;
                  this.maximumPriceRejected = false;
                  this.splitSalePending = false;
                  this.listedStackCount = minecraftClient.player.getMainHandStack().getCount();
                  if (this.unitSalePrice(this.sellingItemName) * this.listedStackCount > (10000000L) && this.listedStackCount > 1 && this.splitHeldStack()) {
                     this.saleQueue.add(this.sellingItemName);
                     this.stateTimer.reset();
                     return;
                  }

                  long totalSalePrice = Math.max(1L, this.unitSalePrice(this.sellingItemName) * minecraftClient.player.getMainHandStack().getCount());
                  minecraftClient.player.networkHandler.sendChatCommand("ah sell " + totalSalePrice);
                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.SELL_WAIT;
                  break;
               case SELL_WAIT:
                  if (this.listingConfirmed) {
                     this.listingConfirmed = false;
                     this.sellFailures = 0;
                     this.remainingSales--;
                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.SELL_PICK;
                     return;
                  }

                  if (this.maximumPriceRejected) {
                     this.maximumPriceRejected = false;
                     if (minecraftClient.player.getMainHandStack().getCount() > 1 && this.splitHeldStack()) {
                        this.saleQueue.add(this.sellingItemName);
                        this.splitSalePending = true;
                        this.stateTimer.reset();
                        return;
                     }

                     this.rejectedSaleItems.add(this.sellingItemName);
                     this.saleQueue.remove(this.sellingItemName);
                     this.sellFailures++;
                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.SELL_PICK;
                     return;
                  }

                  if (this.splitSalePending) {
                     if (!this.stateTimer.hasElapsed((450L))) {
                        return;
                     }

                     this.splitSalePending = false;
                     minecraftClient.player
                        .networkHandler
                        .sendChatCommand("ah sell " + Math.max(1L, this.unitSalePrice(this.sellingItemName) * minecraftClient.player.getMainHandStack().getCount()));
                     this.stateTimer.reset();
                     return;
                  }

                  if (!this.stateTimer.hasElapsed((1500L))) {
                     return;
                  }

                  this.sellFailures++;
                  if (!this.saleQueue.isEmpty()) {
                     this.saleQueue.add(this.saleQueue.remove(0));
                  }

                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.SELL_PICK;
                  break;
               case FLIP_SEARCH:
                  if (!this.stateTimer.hasElapsed((250L))) {
                     return;
                  }

                  minecraftClient.player.networkHandler.sendChatCommand("ah " + minecraftClient.player.getName().getString());
                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.FLIP_BUY;
                  break;
               case FLIP_BUY:
                  if (!this.auctionOpen()) {
                     if (!this.stateTimer.hasElapsed((2500L))) {
                        return;
                     }

                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.IDLE;
                     return;
                  }

                  if (!this.stateTimer.hasElapsed(this.selectedDelayMillis((600L), (450L), (700L)))) {
                     return;
                  }

                  ArrayList<Integer> flipSlots = new ArrayList();

                  for (int flipScanSlot = 0; flipScanSlot < minecraftClient.player.currentScreenHandler.slots.size() - 36 - 9; flipScanSlot++) {
                     ItemStack flipScanStack = minecraftClient.player.currentScreenHandler.getSlot(flipScanSlot).getStack();
                     if (!flipScanStack.isEmpty()
                        && ServerItemCatalog.findMatching(flipScanStack) != null
                        && AuctionHelper.getInstance().lotPrice(flipScanStack) > 0
                        && !AuctionHelper.getInstance().normalizeServerText(flipScanStack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).contains(("не актуален"))) {
                        flipSlots.add(flipScanSlot);
                     }
                  }

                  flipSlots.sort(
                     Comparator.comparingInt(
                        slotId -> AuctionHelper.getInstance().lotPrice(minecraftClient.player.currentScreenHandler.getSlot(slotId).getStack())
                     )
                  );

                  for (int flipSlot : flipSlots) {
                     if (this.remainingFlipPurchases <= 0) {
                        break;
                     }

                     ItemStack flipStack = minecraftClient.player.currentScreenHandler.getSlot(flipSlot).getStack();
                     if (!this.clickedAuctionSlots.contains(flipSlot) && (this.scoreboardBalance() < 0 || AuctionHelper.getInstance().lotPrice(flipStack) <= this.scoreboardBalance())) {
                        this.clickedAuctionSlots.add(flipSlot);
                        this.remainingFlipPurchases--;
                        this.priceParseQueue.add(ServerItemCatalog.findMatching(flipStack).name());
                        this.saleQueue.add(ServerItemCatalog.findMatching(flipStack).name());
                        InventoryActions.clickSlot(flipSlot, 0, SlotActionType.QUICK_MOVE, true);
                        this.stateTimer.reset();
                        if (this.remainingFlipPurchases > 0) {
                           return;
                        }
                        break;
                     }
                  }

                  this.resumeSellingAfterParse = true;
                  this.stateTimer.reset();
                  this.phase = this.priceParseQueue.isEmpty() ? AutoBuyModule.AuctionPhase.IDLE : AutoBuyModule.AuctionPhase.SEND;
                  break;
               case RESELL:
                  if (!this.stateTimer.hasElapsed((250L))) {
                     return;
                  }

                  minecraftClient.player.networkHandler.sendChatCommand(("ah resell"));
                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.RESUME_PAGE;
                  break;
               case REJOIN_HUB:
                  if (!this.stateTimer.hasElapsed((400L))) {
                     return;
                  }

                  minecraftClient.player.networkHandler.sendChatCommand(("hub"));
                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.REJOIN_ANARCHY;
                  break;
               case REJOIN_ANARCHY:
                  if (!this.stateTimer.hasElapsed((1500L))) {
                     return;
                  }

                  if (this.anarchyCommand != null) {
                     minecraftClient.player.networkHandler.sendChatCommand(this.anarchyCommand);
                  }

                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.RESUME_PAGE;
                  break;
               case RESUME_PAGE:
                  if (!this.stateTimer.hasElapsed((800L))) {
                     return;
                  }

                  if (this.autoBuy.getValue()) {
                     minecraftClient.player.networkHandler.sendChatCommand(this.auctionPageCommand);
                  }

                  this.clickedAuctionSlots.clear();
                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.IDLE;
                  break;
               case STASH:
                  if (!this.stateTimer.hasElapsed((120L))) {
                     return;
                  }

                  if (!this.nonAuctionContainerOpen()) {
                     if (minecraftClient.player.currentScreenHandler != minecraftClient.player.playerScreenHandler) {
                        minecraftClient.player.closeHandledScreen();
                        return;
                     }

                     for (Slot shulkerSlot : minecraftClient.player.currentScreenHandler.slots) {
                        if (shulkerSlot.inventory == minecraftClient.player.getInventory() && ServerItemCatalog.isShulkerBox(shulkerSlot.getStack().getItem())) {
                           InventoryActions.clickSlot(shulkerSlot.id, 1, SlotActionType.PICKUP, false);
                           this.stateTimer.reset();
                           return;
                        }
                     }

                     this.stateTimer.reset();
                     this.phase = AutoBuyModule.AuctionPhase.IDLE;
                     return;
                  }

                  for (Slot storedItemSlot : minecraftClient.player.currentScreenHandler.slots) {
                     if (storedItemSlot.inventory != minecraftClient.player.getInventory()
                        && !this.saleQueue.isEmpty()
                        && ServerItemCatalog.findByName(this.saleQueue.get(0)) != null
                        && ServerItemCatalog.matches(storedItemSlot.getStack(), ServerItemCatalog.findByName(this.saleQueue.get(0)))) {
                        InventoryActions.clickSlot(storedItemSlot.id, 0, SlotActionType.QUICK_MOVE, false);
                        minecraftClient.player.closeHandledScreen();
                        this.stateTimer.reset();
                        this.phase = AutoBuyModule.AuctionPhase.SELL_PICK;
                        return;
                     }
                  }

                  for (Slot stashItemSlot : minecraftClient.player.currentScreenHandler.slots) {
                     if (stashItemSlot.inventory == minecraftClient.player.getInventory()
                        && stashItemSlot.hasStack()
                        && this.occupiedInventorySlots() > 20
                        && !this.stashTimer.hasElapsed((8000L))
                        && ServerItemCatalog.findMatching(stashItemSlot.getStack()) != null) {
                        InventoryActions.clickSlot(stashItemSlot.id, 0, SlotActionType.QUICK_MOVE, false);
                        this.stateTimer.reset();
                        return;
                     }
                  }

                  if (!this.saleQueue.isEmpty()) {
                     this.missingStashItems.add(this.saleQueue.get(0));
                  }

                  minecraftClient.player.closeHandledScreen();
                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.SELL_PICK;
                  break;
               case RELIST_SEARCH:
                  if (!this.stateTimer.hasElapsed((250L))) {
                     return;
                  }

                  if (minecraftClient.player.currentScreenHandler != minecraftClient.player.playerScreenHandler) {
                     minecraftClient.player.closeHandledScreen();
                     this.stateTimer.reset();
                     return;
                  }

                  this.pulledRelistCount = 0;
                  this.relistPullReady = true;
                  this.pricedRelistItems.clear();
                  this.clickedAuctionSlots.clear();
                  minecraftClient.player.networkHandler.sendChatCommand("ah " + minecraftClient.player.getName().getString());
                  this.stateTimer.reset();
                  this.phase = AutoBuyModule.AuctionPhase.RELIST_PULL;
                  break;
               case RELIST_PULL:
                  if (!this.auctionOpen()) {
                     if (!this.stateTimer.hasElapsed((2500L))) {
                        return;
                     }

                     this.stateTimer.reset();
                     this.phase = this.pulledRelistCount > 0 ? AutoBuyModule.AuctionPhase.SELL_PICK : AutoBuyModule.AuctionPhase.RESUME_PAGE;
                     this.remainingSales = this.pulledRelistCount;
                     return;
                  }

                  if (!this.relistPullReady && !this.stateTimer.hasElapsed((1500L))) {
                     return;
                  }

                  if (!this.stateTimer.hasElapsed(this.selectedDelayMillis((600L), (450L), (350L))) || System.currentTimeMillis() - this.lastInventoryUpdateAt < (90L)
                     )
                   {
                     return;
                  }

                  for (int relistSlot = 0; relistSlot < minecraftClient.player.currentScreenHandler.slots.size() - 36 - 9; relistSlot++) {
                     ItemStack relistStack = minecraftClient.player.currentScreenHandler.getSlot(relistSlot).getStack();
                     if (!relistStack.isEmpty() && ServerItemCatalog.findMatching(relistStack) != null && this.pulledRelistCount < this.auctionSlotLimit && InventoryActions.findEmptySlot(0, 35) != -1) {
                        String relistItemName = ServerItemCatalog.findMatching(relistStack).name();
                        if (this.pricedRelistItems.add(relistItemName)) {
                           long previousUnitPrice = AuctionHelper.getInstance().lotPrice(relistStack) <= 0
                              ? this.unitSalePrice(relistItemName)
                              : AuctionHelper.getInstance().lotPrice(relistStack) / Math.max(1, relistStack.getCount());
                           long minimumRelistPrice = AutoBuyState.getInstance().selectedItems.get(relistItemName) == null
                              ? 1L
                              : Math.max(1L, AutoBuyState.parseCoins(AutoBuyState.getInstance().selectedItems.get(relistItemName).maxBuyPrice.getValue()));
                           this.relistUnitPrices
                              .put(
                                 relistItemName,
                                 this.relistCounts.getOrDefault(relistItemName, 0) == 0
                                    ? previousUnitPrice
                                    : Math.max(minimumRelistPrice, Math.round(previousUnitPrice * (1.0 - this.relistDiscount.floatValue() / (100.0D))))
                              );
                           this.relistCounts.put(relistItemName, this.relistCounts.getOrDefault(relistItemName, 0) + 1);
                        }

                        this.saleQueue.add(Math.min(this.pulledRelistCount, this.saleQueue.size()), relistItemName);
                        this.pulledRelistCount++;
                        this.relistPullReady = false;
                        InventoryActions.clickSlot(relistSlot, 0, SlotActionType.QUICK_MOVE, false);
                        this.stateTimer.reset();
                        return;
                     }
                  }

                  this.relistTimer.reset();
                  this.remainingSales = this.pulledRelistCount;
                  this.clickedAuctionSlots.clear();
                  this.stateTimer.reset();
                  this.phase = this.pulledRelistCount > 0 ? AutoBuyModule.AuctionPhase.SELL_PICK : AutoBuyModule.AuctionPhase.RESUME_PAGE;
            }

            if (this.phase == AutoBuyModule.AuctionPhase.IDLE && this.autoBuy.getValue()) {
               if (this.lotUnavailable) {
                  this.lotUnavailable = false;
                  if (minecraftClient.currentScreen != null) {
                     minecraftClient.player.closeHandledScreen();
                  }

                  minecraftClient.player.networkHandler.sendChatCommand(this.auctionPageCommand);
                  this.purchasePending = false;
                  this.clickedAuctionSlots.clear();
                  this.auctionRefreshTimer.reset();
               } else if (minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler
                  || !AuctionHelper.getInstance().normalizeServerText(this.screenTitle).contains(("подтверждение"))
                     && !AuctionHelper.getInstance().normalizeServerText(this.screenTitle).contains(("подозрительная цена"))) {
                  if (!this.auctionOpen()) {
                     if (!(minecraftClient.currentScreen instanceof ChatScreen)
                        && !(minecraftClient.currentScreen instanceof AutoBuyScreen)
                        && this.auctionRefreshTimer.hasElapsed((300L))) {
                        minecraftClient.player.networkHandler.sendChatCommand(this.auctionPageCommand);
                        this.clickedAuctionSlots.clear();
                        this.auctionRefreshTimer.reset();
                     }
                  } else {
                     if (this.purchasePending) {
                        if (!this.purchaseTimer.hasElapsed((400L))) {
                           return;
                        }

                        this.purchasePending = false;
                     }

                     int cheapestBuySlot = -1;
                     long cheapestBuyPrice = (9223372036854775807L);

                     for (int buyScanSlot = 0; buyScanSlot < minecraftClient.player.currentScreenHandler.slots.size() - 36 - 9; buyScanSlot++) {
                        ItemStack buyScanStack = minecraftClient.player.currentScreenHandler.getSlot(buyScanSlot).getStack();
                        ServerItemDefinition buyDefinition = !buyScanStack.isEmpty() && !this.clickedAuctionSlots.contains(buyScanSlot) ? ServerItemCatalog.findMatching(buyScanStack) : null;
                        if (buyDefinition != null
                           && AutoBuyState.getInstance().isItemSelected(buyDefinition.name())
                           && !AuctionHelper.getInstance().normalizeServerText(buyScanStack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).contains(("не актуален"))
                           && (
                              AutoBuyState.parseCoins(AutoBuyState.getInstance().selectedItems.get(buyDefinition.name()).maxBuyPrice.getValue()) <= 0L
                                 || AuctionHelper.getInstance().lotPrice(buyScanStack) / Math.max(1, buyScanStack.getCount())
                                    <= AutoBuyState.parseCoins(AutoBuyState.getInstance().selectedItems.get(buyDefinition.name()).maxBuyPrice.getValue())
                           )
                           && AuctionHelper.getInstance().lotPrice(buyScanStack) >= 0
                           && (this.scoreboardBalance() < 0 || AuctionHelper.getInstance().lotPrice(buyScanStack) <= this.scoreboardBalance())
                           && AuctionHelper.getInstance().lotPrice(buyScanStack) < cheapestBuyPrice) {
                           cheapestBuySlot = buyScanSlot;
                           cheapestBuyPrice = AuctionHelper.getInstance().lotPrice(buyScanStack);
                        }
                     }

                     if (cheapestBuySlot != -1 && this.emptyInventorySlots() > this.activeLotCount) {
                        if (System.currentTimeMillis() - this.lastInventoryUpdateAt >= (90L)) {
                           this.clickedAuctionSlots.add(cheapestBuySlot);
                           InventoryActions.clickSlot(cheapestBuySlot, 0, SlotActionType.QUICK_MOVE, true);
                           this.purchasePending = true;
                           this.purchaseTimer.reset();
                           this.auctionRefreshTimer.reset();
                        }
                     } else if (this.auctionRefreshTimer.hasElapsed((400L))) {
                        InventoryActions.clickSlot(minecraftClient.player.currentScreenHandler.slots.size() - 36 - 5, 0, SlotActionType.QUICK_MOVE, false);
                        this.clickedAuctionSlots.clear();
                        this.auctionRefreshTimer.reset();
                     }
                  }
               } else if (minecraftClient.player
                  .currentScreenHandler
                  .slots
                  .stream()
                  .anyMatch(
                     candidateSlot -> candidateSlot.hasStack()
                        && AuctionHelper.getInstance().normalizeServerText(candidateSlot.getStack().getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).contains(("не актуален"))
                  )) {
                  minecraftClient.player.closeHandledScreen();
                  minecraftClient.player.networkHandler.sendChatCommand(this.auctionPageCommand);
                  this.purchasePending = false;
                  this.auctionRefreshTimer.reset();
               } else {
                  InventoryActions.clickSlot(0, 0, SlotActionType.QUICK_MOVE, false);
                  this.auctionRefreshTimer.reset();
               }
            }
         }
      };
      this.clientTickListener = event -> {
         if (event.isPre() && (minecraftClient.player == null || minecraftClient.world == null)) {
            this.stateTimer.reset();
            if (this.lastServer != null
               && (
                  minecraftClient.currentScreen instanceof DisconnectedScreen
                     || minecraftClient.currentScreen instanceof TitleScreen
                     || minecraftClient.currentScreen instanceof MultiplayerScreen
               )
               && this.reconnectTimer.hasElapsed((5000L))) {
               this.reconnectTimer.reset();
               this.phase = AutoBuyModule.AuctionPhase.REJOIN_ANARCHY;
               ConnectScreen.connect(
                  new MultiplayerScreen(new TitleScreen()), minecraftClient, ServerAddress.parse(this.lastServer.address), this.lastServer, false, null
               );
            }
         }
      };
      this.packetListener = event -> {
         if (event.isOutgoing()
            && this.phase == AutoBuyModule.AuctionPhase.IDLE
            && event.getPacket() instanceof CommandExecutionC2SPacket commandPacket
            && (commandPacket.command().equals(("ah")) || commandPacket.command().startsWith(("ah ")))
            && !commandPacket.command().startsWith(("ah sell"))) {
            this.auctionPageCommand = commandPacket.command();
         }

         if (event.isIncoming() && (event.getPacket() instanceof InventoryS2CPacket || event.getPacket() instanceof ScreenHandlerSlotUpdateS2CPacket)) {
            this.lastInventoryUpdateAt = System.currentTimeMillis();
         }

         if (event.isIncoming() && event.getPacket() instanceof OpenScreenS2CPacket openScreenPacket) {
            this.screenTitle = openScreenPacket.getName().getString();
         }

         if (event.isIncoming() && event.getPacket() instanceof CloseScreenS2CPacket) {
            this.screenTitle = "";
         }

         if (event.isIncoming() && event.getPacket() instanceof GameMessageS2CPacket chatPacket) {
            if (PURCHASE_MESSAGE_PATTERN.matcher(AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))).matches()) {
               this.purchasePending = false;
               if (this.phase == AutoBuyModule.AuctionPhase.FLIP_BUY) {
                  this.activeLotCount = Math.max(0, this.activeLotCount - 1);
               }

               for (ServerItemDefinition definition : ServerItemCatalog.items()) {
                  if (AuctionHelper.getInstance()
                     .normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))
                     .contains(AuctionHelper.getInstance().normalizeServerText(definition.name()))) {
                     this.saleQueue.add(definition.name());
                     this.missingStashItems.remove(definition.name());
                     break;
                  }
               }

               this.purchaseCount++;
            } else if (REMOVED_MESSAGE_PATTERN.matcher(AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))).matches()) {
               this.activeLotCount = Math.max(0, this.activeLotCount - 1);
               this.relistPullReady = true;
            } else if (SOLD_MESSAGE_PATTERN.matcher(AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))).matches()) {
               this.saleCount++;
               this.activeLotCount = Math.max(0, this.activeLotCount - 1);
               this.sellRetryExponent = 0;
               this.saleActivityTimer.reset();
               this.relistTimer.reset();
               String saleMessage = chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim();
               if (SOLD_DETAILS_PATTERN.matcher(saleMessage).matches()) {
                  this.relistUnitPrices.remove(SOLD_DETAILS_PATTERN.matcher(saleMessage).replaceAll(("$1")).replaceAll(("\\s+[xх]\\d+$"), "").trim());
                  this.relistCounts.remove(SOLD_DETAILS_PATTERN.matcher(saleMessage).replaceAll(("$1")).replaceAll(("\\s+[xх]\\d+$"), "").trim());
                  minecraftClient.execute(
                     () -> AutoBuyState.getInstance()
                        .recordPurchase(
                           SOLD_DETAILS_PATTERN.matcher(saleMessage).replaceAll(("$1")), AutoBuyState.parseCoins(SOLD_DETAILS_PATTERN.matcher(saleMessage).replaceAll(("$2")).replace((","), ""))
                        )
                  );
               }
            } else if (MAX_PRICE_MESSAGE_PATTERN.matcher(AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))).matches()) {
               if (this.phase == AutoBuyModule.AuctionPhase.SELL_WAIT) {
                  this.maximumPriceRejected = true;
               }
            } else if (!AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).contains(("не так быстро"))) {
               if (AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).contains(("этот товар уже купили"))) {
                  this.lotUnavailable = true;
               } else if (LISTED_MESSAGE_PATTERN.matcher(AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))).matches()) {
                  this.listingConfirmed = true;
                  this.sellRetryExponent = 0;
                  if (this.phase == AutoBuyModule.AuctionPhase.SELL_WAIT) {
                     this.activeLotCount = Math.min(this.auctionSlotLimit, this.activeLotCount + 1);
                  }

                  if (!this.saleQueue.remove(this.sellingItemName) && !this.saleQueue.isEmpty()) {
                     this.saleQueue.remove(0);
                  }

                  if (ServerItemCatalog.findByName(this.sellingItemName) != null
                     && InventoryActions.findSlot(candidateStack -> ServerItemCatalog.matches(candidateStack, ServerItemCatalog.findByName(this.sellingItemName))) == -1
                     && InventoryActions.findSlot(candidateStack -> ServerItemCatalog.isShulkerBox(candidateStack.getItem())) == -1) {
                     this.saleQueue.removeIf(candidateItemName -> candidateItemName.equals(this.sellingItemName));
                  }
               } else if (!chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim().equalsIgnoreCase(("Данная команда недоступна в режиме AFK"))
                  && !chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim().equalsIgnoreCase(("Недопустимо нажимать в режиме AFK"))) {
                  if (AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).contains(("освободите хранилище или арендуйте больше слотов"))) {
                     this.activeLotCount = this.auctionSlotLimit;
                  }

                  if (AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).contains(("освободите хранилище или арендуйте больше слотов"))
                     && InventoryActions.findEmptySlot(0, 35) != -1) {
                     if (this.phase == AutoBuyModule.AuctionPhase.IDLE
                        || this.phase == AutoBuyModule.AuctionPhase.SELL_PICK
                        || this.phase == AutoBuyModule.AuctionPhase.SELL_SEND
                        || this.phase == AutoBuyModule.AuctionPhase.SELL_WAIT) {
                        this.remainingFlipPurchases = 3;
                        this.clickedAuctionSlots.clear();
                        this.stateTimer.reset();
                        this.phase = AutoBuyModule.AuctionPhase.FLIP_SEARCH;
                     }
                  }
               } else {
                  this.phase = AutoBuyModule.AuctionPhase.REJOIN_HUB;
                  this.stateTimer.reset();
               }
            }
         }
      };
      this.addSetting(new SettingSeparator(("Скупка")));
      this.openMenuBind = this.addSetting(new BindSetting(("Открыть меню"), ("Клавиша, которая открывает окно AutoBuy.")));
      this.autoBuy = this.addSetting(new BooleanSetting(("Автобай"), ("Скупать выбранные предметы на открытой странице аукциона."), false));
      this.autoParser = this.addSetting(new BooleanSetting(("Автопарсер"), ("Проходить выбранные предметы и подставлять им цены."), false));
      this.relistDiscount = this.addSetting(new SliderSetting(("Скидка перевыставления"), ("На сколько процентов дешеветь помеченному лоту за каждый круг перевыставления, ниже цены покупки не опускается.")).withIntegerRange(1, 50).withSuffix(("%")).setValue((5.0F)));
      this.reparse = this.addSetting(new SliderSetting(("Перепарс"), ("Через сколько минут автопарсер заново проходит все предметы, 0 значит один проход.")).withIntegerRange(0, 60).withSuffix((" мин")).setValue((10.0F)));
      this.buyPercentage = new SliderSetting(("Процент покупки"), ("Какой процент от самого дешёвого лота автопарсер ставит в лимит покупки.")).withIntegerRange(10, 200).withSuffix(("%")).setValue((95.0F));
      this.sellPercentage = new SliderSetting(("Процент продажи"), ("Какой процент от самого дешёвого лота автопарсер ставит в цену продажи.")).withIntegerRange(10, 400).withSuffix(("%")).setValue((115.0F));
   }

   public static final class AutoBuyPercentageSlider extends SliderWidget {
      private final String label;
      private final SliderSetting setting;

      @Override
      protected void updateMessage() {
         this.setMessage(Text.literal(this.label + ": " + this.setting.intValue() + "%"));
      }

      @Override
      protected void applyValue() {
         this.setting.setNormalizedValue((float)this.value);
      }

      public AutoBuyPercentageSlider(int x, int y, int width, String label, SliderSetting setting) {
         super(x, y, width, 20, Text.literal(label + ": " + setting.intValue() + "%"), setting.getNormalizedValue());
         this.label = label;
         this.setting = setting;
      }
   }

   private enum AuctionPhase {
      IDLE,
      SEND,
      WAIT_LOTS,
      SELL_PICK,
      SELL_SEND,
      SELL_WAIT,
      FLIP_SEARCH,
      FLIP_BUY,
      RESELL,
      REJOIN_HUB,
      REJOIN_ANARCHY,
      RESUME_PAGE,
      STASH,
      RELIST_SEARCH,
      RELIST_PULL;
   }
}
