package com.mouseclient.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class StaffRegistry {
   private static final Map<String, String> names = new LinkedHashMap<>();
   private static final Map<String, String> prefixes = new LinkedHashMap<>();
   private static boolean loaded;
   private static Path configPath;

   public static boolean addName(String name) {
      ensureLoaded();
      if (name != null && !name.trim().isEmpty() && !names.containsKey(name.toLowerCase(Locale.ROOT))) {
         names.put(name.toLowerCase(Locale.ROOT), name);
         save();
         return true;
      } else {
         return false;
      }
   }

   private static void save() {
      ensureLoaded();

      try {
         Files.createDirectories(configPath.getParent());
         JsonObject json = new JsonObject();
         JsonArray jsonArray = new JsonArray();

         for (String staffName : names.values()) {
            jsonArray.add(staffName);
         }

         JsonArray prefixesJson = new JsonArray();

         for (String staffPrefix : prefixes.values()) {
            prefixesJson.add(staffPrefix);
         }

         json.add(("names"), jsonArray);
         json.add(("prefixes"), prefixesJson);
         Files.writeString(configPath, json.toString(), StandardCharsets.UTF_8);
      } catch (Exception error) {
      }
   }

   public static boolean containsPrefix(String text) {
      ensureLoaded();
      if (text != null && !text.isEmpty()) {
         for (String normalizedPrefix : prefixes.keySet()) {
            if (text.toLowerCase(Locale.ROOT).contains(normalizedPrefix)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static ArrayList<String> prefixes() {
      ensureLoaded();
      return new ArrayList<>(prefixes.values());
   }

   public static ArrayList<String> names() {
      ensureLoaded();
      return new ArrayList<>(names.values());
   }

   public static boolean containsName(String name) {
      ensureLoaded();
      return name != null && names.containsKey(name.toLowerCase(Locale.ROOT));
   }

   public static void clear() {
      ensureLoaded();
      names.clear();
      prefixes.clear();
      save();
   }

   private static void ensureLoaded() {
      if (!loaded) {
         loaded = true;
         configPath = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("staffs.json"));

         try {
            if (!Files.exists(configPath)) {
               return;
            }

            JsonObject json = JsonParser.parseString(Files.readString(configPath, StandardCharsets.UTF_8)).getAsJsonObject();

            for (JsonElement jsonElement : json.getAsJsonArray(("names"))) {
               names.put(jsonElement.getAsString().toLowerCase(Locale.ROOT), jsonElement.getAsString());
            }

            for (JsonElement prefixElement : json.getAsJsonArray(("prefixes"))) {
               prefixes.put(prefixElement.getAsString().toLowerCase(Locale.ROOT), prefixElement.getAsString());
            }
         } catch (Exception error) {
         }
      }
   }

   public static boolean remove(String name) {
      ensureLoaded();
      if (name != null && (names.remove(name.toLowerCase(Locale.ROOT)) != null || prefixes.remove(name.toLowerCase(Locale.ROOT)) != null)) {
         save();
         return true;
      } else {
         return false;
      }
   }

   private StaffRegistry() {
   }

   public static boolean addPrefix(String prefix) {
      ensureLoaded();
      if (prefix != null && !prefix.trim().isEmpty() && !prefixes.containsKey(prefix.toLowerCase(Locale.ROOT))) {
         prefixes.put(prefix.toLowerCase(Locale.ROOT), prefix);
         save();
         return true;
      } else {
         return false;
      }
   }
}
