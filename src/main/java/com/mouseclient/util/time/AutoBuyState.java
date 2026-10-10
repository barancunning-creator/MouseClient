package com.mouseclient.util.time;

import com.mouseclient.gui.SmoothScrollState;
import com.mouseclient.modules.impl.player.ServerItemDefinition;
import com.mouseclient.modules.impl.player.ServerItemCatalog;
import com.mouseclient.render.pipeline.SliderSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Random;
import net.minecraft.item.Items;

public final class AutoBuyState {
   public static final String[] SPEED_LABELS = new String[]{("Slow"), ("Medium"), ("Ultrabuy")};
   public static final float[] INITIAL_CHART_HEIGHTS = new float[]{
      (0.41422594F),
      (0.5606695F),
      (0.29707113F),
      (0.65690374F),
      (0.41841003F),
      (0.7573222F),
      (0.59414226F),
      (0.37238494F),
      (0.42677826F),
      (0.53974897F),
      (0.37656903F),
      (0.8995816F),
      (0.5146443F),
      (0.53556484F),
      (0.28870293F),
      (0.50209206F)
   };
   private static final DateTimeFormatter PURCHASE_TIME_FORMAT = DateTimeFormatter.ofPattern(("HH:mm:ss"));
   private static final HashMap<String, Long> DEFAULT_BUY_PRICES = new HashMap<>();
   private static AutoBuyState INSTANCE;
   public TextSetting search;
   public SliderSetting autoBuySpeed;
   public AutoBuyState.NumericTextSetting maxBuyPrice;
   public AutoBuyState.NumericTextSetting minSellPrice;
   public AutoBuyState.NumericTextSetting minimumDurability;
   public TextSetting telegramBotToken;
   public TextSetting telegramChatId;
   public LinkedHashMap<String, AutoBuyState.ItemPriceSettings> selectedItems;
   public ArrayList<AutoBuyState.PurchaseEntry> purchaseHistory;
   public float[] chartHeights;
   public HashMap<String, Long> selectionChangedAtMillis;
   public HashMap<String, Long> inputConfirmedAtMillis;
   public SmoothScrollState itemGridScroll;
   public SmoothScrollState purchaseHistoryScroll;
   public float[] itemGridBounds;
   public float[] purchaseHistoryBounds;
   public long totalPurchaseCoins;
   public double animatedPurchaseTotal;
   public String editingItem;
   public long priceModeChangedAtMillis;
   public long gridChangedAtMillis;
   public String lastSearchQuery;
   private Random chartRandom;
   private long nextChartUpdateAtMillis;

   public static String formatCoins(long coins) {
      return String.format(Locale.US, ("%,d"), coins);
   }

   public static long parseCoins(String value) {
      if (value != null && !value.isEmpty()) {
         try {
            return Long.parseLong(value);
         } catch (NumberFormatException error) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   public static long defaultBuyPrice(String itemName) {
      return DEFAULT_BUY_PRICES.get(itemName) != null
         ? DEFAULT_BUY_PRICES.get(itemName)
         : Math.round(((25000L) + Math.floorMod(itemName.hashCode(), 900) * (1000L)) / (1000.0D)) * (1000L);
   }

   private AutoBuyState.ItemPriceSettings createItemPriceSettings(String itemName) {
      return new AutoBuyState.ItemPriceSettings(defaultBuyPrice(itemName), Math.round(defaultBuyPrice(itemName) * (1.12D) / (1000.0D)) * (1000L));
   }

   public void updateChartHeights(long nowMillis) {
      if (this.nextChartUpdateAtMillis == 0L) {
         this.nextChartUpdateAtMillis = nowMillis + (6000L) + this.chartRandom.nextInt(8000);
      }

      if (nowMillis >= this.nextChartUpdateAtMillis && !this.selectedItems.isEmpty()) {
         this.nextChartUpdateAtMillis = nowMillis + (6000L) + this.chartRandom.nextInt(8000);
         this.chartHeights[this.chartRandom.nextInt(this.chartHeights.length)] = Math.max(
            (0.12F),
            Math.min(1.0F, INITIAL_CHART_HEIGHTS[this.chartRandom.nextInt(this.chartHeights.length)] + (this.chartRandom.nextFloat() - (0.35F)) * (0.25F))
         );
      }
   }

   public static AutoBuyState getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoBuyState();
      }

      return INSTANCE;
   }

   public void toggleItem(String itemName) {
      if (this.selectedItems.remove(itemName) == null) {
         this.selectedItems.put(itemName, this.createItemPriceSettings(itemName));
      } else if (itemName.equals(this.editingItem)) {
         this.editingItem = null;
         this.priceModeChangedAtMillis = System.currentTimeMillis();
      }

      this.selectionChangedAtMillis.put(itemName, System.currentTimeMillis());
   }

   static {
      DEFAULT_BUY_PRICES.put(("Элитры Крушителя"), (2400000L));
      DEFAULT_BUY_PRICES.put(("Сфера Афины"), (1600000L));
      DEFAULT_BUY_PRICES.put(("Чарка"), (1250000L));
      DEFAULT_BUY_PRICES.put(("Меч Крушителя"), (890000L));
      DEFAULT_BUY_PRICES.put(("Незеритовый слиток"), (720000L));
      DEFAULT_BUY_PRICES.put(("Булава Крушителя"), (540000L));
      DEFAULT_BUY_PRICES.put(("Спавнер"), (480000L));
      DEFAULT_BUY_PRICES.put(("Талисман Крушителя"), (310000L));
   }

   public void recordPurchase(String description, long priceCoins) {
      this.purchaseHistory
         .add(
            0,
            new AutoBuyState.PurchaseEntry(
               ServerItemCatalog.findByName(description.replaceAll(("\\s+[xх]\\d+$"), "").trim()) == null
                  ? new ServerItemDefinition.Builder().withName(description.replaceAll(("\\s+[xх]\\d+$"), "").trim()).withItem(Items.PAPER).build()
                  : ServerItemCatalog.findByName(description.replaceAll(("\\s+[xх]\\d+$"), "").trim()),
               description.matches((".*\\s+[xх]\\d+$")) ? Integer.parseInt(description.replaceAll((".*\\s+[xх](\\d+)$"), ("$1"))) : 1,
               LocalTime.now().format(PURCHASE_TIME_FORMAT),
               priceCoins
            )
         );
      if (this.purchaseHistory.size() > 40) {
         this.purchaseHistory.remove(this.purchaseHistory.size() - 1);
      }

      this.totalPurchaseCoins += priceCoins;
   }

   public boolean isItemSelected(String itemName) {
      return this.selectedItems.containsKey(itemName);
   }

   private AutoBuyState() {
      this.search = new TextSetting(("Search"), "").withPlaceholder(("Поиск предметов..."));
      this.autoBuySpeed = new SliderSetting(("Auto Buy Speed"), ("Select scan frequency and execution speed")).withIntegerRange(0, 2).setValue(2.0F);
      this.maxBuyPrice = new AutoBuyState.NumericTextSetting(("Buy Price (Max)"), ("125000"), 9);
      this.minSellPrice = new AutoBuyState.NumericTextSetting(("Sell Price (Min)"), ("140000"), 9);
      this.minimumDurability = new AutoBuyState.NumericTextSetting(("Minimum Durability"), ("85"), 3);
      this.telegramBotToken = new TextSetting(("Telegram Bot Token"), "").setValue(("7123984120:AAHq_x9Z8k...")).withPlaceholder(("Bot token..."));
      this.telegramChatId = new TextSetting(("Telegram Chat ID"), "").setValue(("123456789")).withPlaceholder(("Chat ID..."));
      this.selectedItems = new LinkedHashMap<>();
      this.purchaseHistory = new ArrayList<>();
      this.chartHeights = new float[INITIAL_CHART_HEIGHTS.length];
      this.selectionChangedAtMillis = new HashMap<>();
      this.inputConfirmedAtMillis = new HashMap<>();
      this.itemGridScroll = new SmoothScrollState();
      this.purchaseHistoryScroll = new SmoothScrollState();
      this.lastSearchQuery = "";
      this.chartRandom = new Random();
      ServerItemCatalog.ensureInitialized();
      System.arraycopy(INITIAL_CHART_HEIGHTS, 0, this.chartHeights, 0, this.chartHeights.length);

      for (String itemName : new String[]{("Шлем Крушителя"), ("Нагрудник Крушителя"), ("Поножи Крушителя"), ("Ботинки Крушителя"), ("Меч Крушителя"), ("Булава Крушителя"), ("Сфера Афины"), ("Талисман Крушителя"), ("Чарка"), ("Элитры Крушителя"), ("Хлопушка"), ("Шалкер")}) {
         if (ServerItemCatalog.findByName(itemName) != null) {
            this.selectedItems.put(itemName, this.createItemPriceSettings(itemName));
         }
      }

      this.maxBuyPrice.withPlaceholder(("$0"));
      this.minSellPrice.withPlaceholder(("$0"));
      this.minimumDurability.withPlaceholder(("0%"));
   }

   public ArrayList<ServerItemDefinition> filteredItems() {
      if (!this.search.getValue().toLowerCase(Locale.ROOT).trim().equals(this.lastSearchQuery)) {
         this.lastSearchQuery = this.search.getValue().toLowerCase(Locale.ROOT).trim();
         this.gridChangedAtMillis = System.currentTimeMillis();
         this.itemGridScroll.target = 0.0F;
      }

      ArrayList matches = new ArrayList();

      for (ServerItemDefinition item : ServerItemCatalog.items()) {
         if (this.lastSearchQuery.isEmpty()
            || item.name().toLowerCase(Locale.ROOT).contains(this.lastSearchQuery)
            || item.category().toLowerCase(Locale.ROOT).contains(this.lastSearchQuery)) {
            matches.add(item);
         }
      }

      return matches;
   }

   public static final class ItemPriceSettings {
      public AutoBuyState.NumericTextSetting maxBuyPrice;
      public AutoBuyState.NumericTextSetting minSellPrice;

      public ItemPriceSettings(long buyPrice, long sellPrice) {
         this.maxBuyPrice = new AutoBuyState.NumericTextSetting(("Buy Price (Max)"), Long.toString(buyPrice), 9);
         this.minSellPrice = new AutoBuyState.NumericTextSetting(("Sell Price (Min)"), Long.toString(sellPrice), 9);
         this.maxBuyPrice.withPlaceholder(("$0"));
         this.minSellPrice.withPlaceholder(("$0"));
      }
   }

   public record PurchaseEntry(ServerItemDefinition item, int count, String time, long priceCoins) {
   }

   public static final class NumericTextSetting extends TextSetting {

      @Override
      public void insertText(String text) {
         super.insertText(text == null ? "" : text.replaceAll(("\\D"), ""));
      }

      public NumericTextSetting(String label, String initialValue, int maxLength) {
         super(label, "", initialValue, maxLength);
      }
   }
}
