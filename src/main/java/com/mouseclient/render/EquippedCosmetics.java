package com.mouseclient.render;

import com.mouseclient.util.time.CosmeticCategory;
import com.mouseclient.util.time.MouseClientCosmeticWarmup;
import com.mouseclient.util.time.CosmeticAvailability;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;

public final class EquippedCosmetics {
   private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("cosmetics.json"));
   private static final Map<CosmeticCategory, Integer> equippedIds = new EnumMap<>(CosmeticCategory.class);

   public static boolean isEquipped(CosmeticEntry entry) {
      return entry != null && equippedId(entry.category()) == entry.id();
   }

   private EquippedCosmetics() {
   }

   public static void toggle(CosmeticEntry entry) {
      if (entry != null && CosmeticAvailability.isAvailable(entry)) {
         if (isEquipped(entry)) {
            equippedIds.remove(entry.category());
         } else {
            equippedIds.put(entry.category(), entry.id());
            MouseClientCosmeticWarmup.warmModel(entry);
         }

         save();
      }
   }

   public static int equippedId(CosmeticCategory category) {
      return equippedIds.getOrDefault(category, -1);
   }

   private static void load() {
      equippedIds.clear();
      if (Files.isRegularFile(CONFIG_PATH)) {
         try {
            JsonObject json = JsonParser.parseString(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8)).getAsJsonObject();

            for (CosmeticCategory category : CosmeticCategory.values()) {
               String categoryKey = category.name().toLowerCase();
               if (json.has(categoryKey) && json.get(categoryKey).isJsonPrimitive()) {
                  CosmeticEntry entry = CosmeticCatalog.getById(json.get(categoryKey).getAsInt());
                  if (entry == null || entry.category() == category) {
                     equippedIds.put(category, json.get(categoryKey).getAsInt());
                  }
               }
            }
         } catch (Exception error) {
         }
      }
   }

   private static void save() {
      try {
         Files.createDirectories(CONFIG_PATH.getParent());
         JsonObject json = new JsonObject();

         for (Entry equippedEntry : equippedIds.entrySet()) {
            json.addProperty(((CosmeticCategory)equippedEntry.getKey()).name().toLowerCase(), (Number)equippedEntry.getValue());
         }

         Files.writeString(CONFIG_PATH, gson.toJson(json), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (Exception error) {
      }
   }

   public static void unequip(CosmeticCategory category) {
      equippedIds.remove(category);
      save();
   }

   static {
      load();
   }

   public static int equippedCount() {
      int equippedCount = 0;

      for (CosmeticCategory category : CosmeticCategory.values()) {
         if (equippedEntry(category) != null) {
            equippedCount++;
         }
      }

      return equippedCount;
   }

   public static CosmeticEntry equippedEntry(CosmeticCategory category) {
      CosmeticEntry entry = CosmeticCatalog.getById(equippedId(category));
      return entry != null && entry.category() == category && CosmeticAvailability.isAvailable(entry) ? entry : null;
   }

   public static void unequipAll() {
      equippedIds.clear();
      save();
   }
}
