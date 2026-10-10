package com.mouseclient.util.time;

import com.mouseclient.modules.impl.misc.AutoRegister;
import com.mouseclient.modules.impl.misc.MultiChoiceSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleRegistry;
import com.mouseclient.modules.impl.player.InputBinding;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.ColorSetting;
import com.mouseclient.network.AutoBuyModule;
import com.mouseclient.render.pipeline.SliderSetting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigStore {
   private static final DateTimeFormatter MODIFIED_DATE_FORMAT = DateTimeFormatter.ofPattern(("dd.MM.yy"));
   private static volatile int changeVersion;
   private static volatile String activeConfigName = ("default");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Map<String, String> lastQueuedJson = new ConcurrentHashMap<>();
   private static final Map<Path, String> pendingWrites = new ConcurrentHashMap<>();
   private static final ExecutorService writerExecutor = Executors.newSingleThreadExecutor(task -> {
      Thread thread = new Thread(task, ("mouseclient-config-writer"));
      thread.setDaemon(true);
      return thread;
   });

   public static boolean load(String name) {
      if (!exists(name)) {
         return false;
      }

      try {
         JsonObject json = JsonParser.parseString(Files.readString(pathFor(name), StandardCharsets.UTF_8)).getAsJsonObject();

         for (String moduleName : json.keySet()) {
            Module module = ModuleRegistry.findByName(moduleName);
            if (module != null && module != AutoDeliver.getInstance() && json.get(moduleName).isJsonObject()) {
               JsonObject moduleJson = json.getAsJsonObject(moduleName);
               if (moduleJson.has(("bind"))) {
                  module.withKeyCode(moduleJson.get(("bind")).getAsInt());
               }

               if (moduleJson.has(("settings")) && moduleJson.get(("settings")).isJsonObject()) {
                  HashMap occurrences = new HashMap();

                  for (Setting setting : module.getSettings()) {
                     String key = settingKey(occurrences, setting);
                     if (setting != AutoRegister.getInstance().password) {
                        readSetting(moduleJson.getAsJsonObject(("settings")), key, setting);
                     }
                  }
               }

               if (moduleJson.has(("enabled")) && moduleJson.get(("enabled")).getAsBoolean() != module.isEnabled()) {
                  module.setEnabled(moduleJson.get(("enabled")).getAsBoolean());
               }
            }
         }

         if (json.has(("autobuy")) && json.get(("autobuy")).isJsonObject()) {
            loadAutoBuy(json.getAsJsonObject(("autobuy")));
         }

         if (json.has(("draggables")) && json.get(("draggables")).isJsonObject()) {
            HudLayout.loadJson(json.getAsJsonObject(("draggables")));
         }

         setActiveName(name);
         return true;
      } catch (Exception error) {
         return false;
      }
   }

   public static int changeVersion() {
      return changeVersion;
   }

   public static ArrayList<String> names() {
      ArrayList configNames = new ArrayList();
      if (!Files.isDirectory(directory())) {
         return configNames;
      }

      try (java.nio.file.DirectoryStream<java.nio.file.Path> entries = Files.newDirectoryStream(directory(), ("*.json"))) {
         for (Path path : entries) {
            configNames.add(path.getFileName().toString().substring(0, path.getFileName().toString().length() - (".json").length()));
         }
      } catch (Exception error) {
      }

      configNames.sort(String.CASE_INSENSITIVE_ORDER);
      return configNames;
   }

   private static JsonObject serializeAutoBuy() {
      JsonObject json = new JsonObject();
      json.addProperty(("speed"), AutoBuyState.getInstance().autoBuySpeed.floatValue());
      json.addProperty(("buyPercent"), AutoBuyModule.getInstance().buyPercentage.floatValue());
      json.addProperty(("sellPercent"), AutoBuyModule.getInstance().sellPercentage.floatValue());
      json.addProperty(("buyMax"), AutoBuyState.getInstance().maxBuyPrice.getValue());
      json.addProperty(("sellMin"), AutoBuyState.getInstance().minSellPrice.getValue());
      json.addProperty(("durability"), AutoBuyState.getInstance().minimumDurability.getValue());
      json.addProperty(("tgToken"), AutoBuyState.getInstance().telegramBotToken.getValue());
      json.addProperty(("tgChat"), AutoBuyState.getInstance().telegramChatId.getValue());
      JsonArray jsonArray = new JsonArray();

      for (String itemName : AutoBuyState.getInstance().selectedItems.keySet()) {
         JsonObject priceSettings = new JsonObject();
         priceSettings.addProperty(("name"), itemName);
         priceSettings.addProperty(("buy"), AutoBuyState.getInstance().selectedItems.get(itemName).maxBuyPrice.getValue());
         priceSettings.addProperty(("sell"), AutoBuyState.getInstance().selectedItems.get(itemName).minSellPrice.getValue());
         jsonArray.add(priceSettings);
      }

      json.add(("items"), jsonArray);
      return json;
   }

   public static InputBinding parseBinding(String value) {
      if (!value.contains((":"))) {
         return InputBinding.INSTANCE;
      } else if (value.startsWith(("MOUSE:"))) {
         return InputBinding.mouse(Integer.parseInt(value.substring(("MOUSE:").length())));
      } else {
         return value.startsWith(("KEYBOARD:")) ? InputBinding.keyboard(Integer.parseInt(value.substring(("KEYBOARD:").length()))) : InputBinding.INSTANCE;
      }
   }

   private static void queueWrite(Path path, String content, Runnable onSuccess, Runnable onFailure) {
      pendingWrites.put(path, content);
      writerExecutor.execute(() -> {
         String queuedContent = pendingWrites.remove(path);
         if (queuedContent != null) {
            try {
               Files.createDirectories(path.getParent());
               Files.writeString(path, queuedContent, StandardCharsets.UTF_8);
               if (onSuccess != null) {
                  onSuccess.run();
               }
            } catch (Exception error) {
               if (onFailure != null) {
                  onFailure.run();
               }
            }
         }
      });
   }

   public static Path directory() {
      return FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("configs"));
   }

   public static String sanitizeName(String name) {
      return normalizeName(name);
   }

   public static Path pathFor(String name) {
      return directory().resolve(normalizeName(name) + ".json");
   }

   private static String serializeCurrent() {
      JsonObject json = new JsonObject();

      for (Module module : ModuleRegistry.modules()) {
         if (module != AutoDeliver.getInstance()) {
            JsonObject moduleJson = new JsonObject();
            moduleJson.addProperty(("enabled"), module.isEnabled());
            moduleJson.addProperty(("bind"), module.getKeyCode());
            JsonObject settingsJson = new JsonObject();
            HashMap occurrences = new HashMap();

            for (Setting setting : module.getSettings()) {
               String key = settingKey(occurrences, setting);
               if (setting != AutoRegister.getInstance().password) {
                  writeSetting(settingsJson, key, setting);
               }
            }

            moduleJson.add(("settings"), settingsJson);
            json.add(module.name, moduleJson);
         }
      }

      json.add(("autobuy"), serializeAutoBuy());
      json.add(("draggables"), HudLayout.toJson());
      return GSON.toJson(json);
   }

   private static void writeLastConfigName(String name) {
      try {
         Files.writeString(lastConfigPath(), name, StandardCharsets.UTF_8);
      } catch (Exception error) {
      }
   }

   public static void writeAsync(Path path, String content) {
      queueWrite(path, content, null, null);
   }

   public static void saveActive() {
      save(activeConfigName);
   }

   private ConfigStore() {
   }

   public static String modifiedDate(String name) {
      try {
         return MODIFIED_DATE_FORMAT.format(
            Instant.ofEpochMilli(Files.getLastModifiedTime(pathFor(name)).toMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
         );
      } catch (Exception error) {
         return "";
      }
   }

   private static void setActiveName(String name) {
      activeConfigName = normalizeName(name);

      try {
         Files.writeString(lastConfigPath(), activeConfigName, StandardCharsets.UTF_8);
      } catch (Exception error) {
      }
   }

   public static boolean exists(String name) {
      return !normalizeName(name).isEmpty() && Files.exists(pathFor(name));
   }

   public static long sizeBytes(String name) {
      try {
         return Files.size(pathFor(name));
      } catch (Exception error) {
         return 0L;
      }
   }

   private static void writeSetting(JsonObject json, String key, Setting<?> setting) {
      if (setting instanceof BooleanSetting booleanSetting) {
         json.addProperty(key, booleanSetting.getValue());
      } else if (setting instanceof ColorSetting colorSetting) {
         json.addProperty(key, colorSetting.getArgb());
      } else if (setting instanceof SliderSetting sliderSetting) {
         json.addProperty(key, sliderSetting.floatValue());
      } else if (setting instanceof ChoiceSetting choiceSetting) {
         json.addProperty(key, choiceSetting.getSelectedOption());
      } else if (setting instanceof MultiChoiceSetting multiChoiceSetting) {
         JsonArray jsonArray = new JsonArray();

         for (String option : multiChoiceSetting.getSelectedOptions()) {
            jsonArray.add(option);
         }

         json.add(key, jsonArray);
      } else if (setting instanceof BindSetting bindSetting) {
         json.addProperty(key, bindSetting.getBinding().getType().name() + ":" + bindSetting.getBinding().getCode());
      } else if (setting instanceof TextSetting textSetting) {
         json.addProperty(key, textSetting.getValue());
      }
   }

   private static String normalizeName(String name) {
      return name == null ? "" : name.replaceAll(("[^a-zA-Z0-9_ -]"), "").trim();
   }

   public static Path lastConfigPath() {
      return directory().resolve((".last"));
   }

   private static void flushWriter() {
      try {
         writerExecutor.submit(() -> {}).get((5L), TimeUnit.SECONDS);
      } catch (Exception error) {
      }
   }

   private static String settingKey(HashMap<String, Integer> occurrences, Setting<?> setting) {
      return occurrences.merge(setting.name, 1, Integer::sum) == 1 ? setting.name : setting.name + "#" + occurrences.get(setting.name);
   }

   public static void loadLast() {
      try {
         if (Files.exists(lastConfigPath())) {
            activeConfigName = normalizeName(Files.readString(lastConfigPath(), StandardCharsets.UTF_8).trim());
         }
      } catch (Exception error) {
      }

      if (activeConfigName.isEmpty()) {
         activeConfigName = ("default");
      }

      if (!exists(activeConfigName)) {
         long newestModifiedMillis = 0L;

         for (String candidateName : names()) {
            try {
               if (Files.getLastModifiedTime(pathFor(candidateName)).toMillis() > newestModifiedMillis) {
                  newestModifiedMillis = Files.getLastModifiedTime(pathFor(candidateName)).toMillis();
                  activeConfigName = candidateName;
               }
            } catch (Exception candidateError) {
            }
         }
      }

      load(activeConfigName);
   }

   public static void saveIfChanged() {
      String normalizedName = normalizeName(activeConfigName);
      if (!normalizedName.isEmpty()) {
         String serializedConfig;
         try {
            serializedConfig = serializeCurrent();
         } catch (Exception error) {
            return;
         }

         if (!serializedConfig.equals(lastQueuedJson.get(normalizedName))) {
            lastQueuedJson.put(normalizedName, serializedConfig);
            queueWrite(pathFor(normalizedName), serializedConfig, () -> {
               writeLastConfigName(normalizedName);
               changeVersion++;
            }, () -> lastQueuedJson.remove(normalizedName, serializedConfig));
         }
      }
   }

   private static void readSetting(JsonObject json, String key, Setting<?> setting) {
      if (json.has(key)) {
         JsonElement jsonElement = json.get(key);
         if (setting instanceof BooleanSetting booleanSetting) {
            booleanSetting.setValue(jsonElement.getAsBoolean());
         } else if (setting instanceof ColorSetting colorSetting) {
            colorSetting.setArgb(jsonElement.getAsInt());
         } else if (setting instanceof SliderSetting sliderSetting) {
            sliderSetting.setValue(jsonElement.getAsFloat());
         } else if (setting instanceof ChoiceSetting choiceSetting) {
            choiceSetting.selectOption(jsonElement.getAsString());
         } else if (setting instanceof MultiChoiceSetting multiChoiceSetting) {
            ArrayList<String> options = new ArrayList<>();

            for (JsonElement option : jsonElement.getAsJsonArray()) {
               options.add(option.getAsString());
            }

            multiChoiceSetting.withSelectedOptions(options.toArray(new String[0]));
         } else if (setting instanceof BindSetting bindSetting) {
            bindSetting.setBinding(parseBinding(jsonElement.getAsString()));
         } else if (setting instanceof TextSetting textSetting) {
            textSetting.setValue(jsonElement.getAsString());
         }
      }
   }

   public static boolean delete(String name) {
      try {
         if (Files.deleteIfExists(pathFor(name))) {
            changeVersion++;
            return true;
         }
      } catch (Exception error) {
      }

      return false;
   }

   public static boolean save(String name) {
      if (normalizeName(name).isEmpty()) {
         return false;
      }

      flushWriter();

      try {
         Files.createDirectories(directory());
         String serializedConfig = serializeCurrent();
         Files.writeString(pathFor(name), serializedConfig, StandardCharsets.UTF_8);
         lastQueuedJson.put(normalizeName(name), serializedConfig);
         setActiveName(name);
         changeVersion++;
         return true;
      } catch (Exception error) {
         return false;
      }
   }

   private static void loadAutoBuy(JsonObject json) {
      if (json.has(("speed"))) {
         AutoBuyState.getInstance().autoBuySpeed.setValue(json.get(("speed")).getAsFloat());
      }

      if (json.has(("buyPercent"))) {
         AutoBuyModule.getInstance().buyPercentage.setValue(json.get(("buyPercent")).getAsFloat());
      }

      if (json.has(("sellPercent"))) {
         AutoBuyModule.getInstance().sellPercentage.setValue(json.get(("sellPercent")).getAsFloat());
      }

      if (json.has(("buyMax"))) {
         AutoBuyState.getInstance().maxBuyPrice.setValue(json.get(("buyMax")).getAsString());
      }

      if (json.has(("sellMin"))) {
         AutoBuyState.getInstance().minSellPrice.setValue(json.get(("sellMin")).getAsString());
      }

      if (json.has(("durability"))) {
         AutoBuyState.getInstance().minimumDurability.setValue(json.get(("durability")).getAsString());
      }

      if (json.has(("tgToken"))) {
         AutoBuyState.getInstance().telegramBotToken.setValue(json.get(("tgToken")).getAsString());
      }

      if (json.has(("tgChat"))) {
         AutoBuyState.getInstance().telegramChatId.setValue(json.get(("tgChat")).getAsString());
      }

      if (json.has(("items")) && json.get(("items")).isJsonArray()) {
         AutoBuyState.getInstance().selectedItems.clear();

         for (JsonElement jsonElement : json.getAsJsonArray(("items"))) {
            AutoBuyState.getInstance().toggleItem(jsonElement.getAsJsonObject().get(("name")).getAsString());
            if (AutoBuyState.getInstance().selectedItems.get(jsonElement.getAsJsonObject().get(("name")).getAsString()) != null) {
               AutoBuyState.getInstance().selectedItems.get(jsonElement.getAsJsonObject().get(("name")).getAsString())
                  .maxBuyPrice
                  .setValue(jsonElement.getAsJsonObject().get(("buy")).getAsString());
               AutoBuyState.getInstance().selectedItems.get(jsonElement.getAsJsonObject().get(("name")).getAsString())
                  .minSellPrice
                  .setValue(jsonElement.getAsJsonObject().get(("sell")).getAsString());
            }
         }
      }
   }
}
