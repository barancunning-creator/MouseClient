package com.mouseclient.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class FriendManager {
   private static final Map<String, String> friendsByLowercaseName = new LinkedHashMap<>();
   private static boolean loaded;
   private static Path storagePath;

   private FriendManager() {
   }

   public static boolean contains(String name) {
      ensureLoaded();
      return name != null && friendsByLowercaseName.containsKey(name.toLowerCase(Locale.ROOT));
   }

   public static ArrayList<String> names() {
      ensureLoaded();
      return new ArrayList<>(friendsByLowercaseName.values());
   }

   private static void save() {
      ensureLoaded();

      try {
         Files.createDirectories(storagePath.getParent());
         JsonArray jsonArray = new JsonArray();

         for (String name : friendsByLowercaseName.values()) {
            jsonArray.add(name);
         }

         Files.writeString(storagePath, jsonArray.toString(), StandardCharsets.UTF_8);
      } catch (Exception error) {
      }
   }

   public static boolean add(String name) {
      ensureLoaded();
      if (name != null && !name.trim().isEmpty() && !friendsByLowercaseName.containsKey(name.toLowerCase(Locale.ROOT))) {
         friendsByLowercaseName.put(name.toLowerCase(Locale.ROOT), name);
         save();
         return true;
      } else {
         return false;
      }
   }

   public static void clear() {
      ensureLoaded();
      friendsByLowercaseName.clear();
      save();
   }

   public static boolean remove(String name) {
      ensureLoaded();
      if (name != null && friendsByLowercaseName.remove(name.toLowerCase(Locale.ROOT)) != null) {
         save();
         return true;
      } else {
         return false;
      }
   }

   private static void ensureLoaded() {
      if (!loaded) {
         loaded = true;
         storagePath = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("friends.json"));

         try {
            if (!Files.exists(storagePath)) {
               return;
            }

            for (JsonElement jsonElement : JsonParser.parseString(Files.readString(storagePath, StandardCharsets.UTF_8)).getAsJsonArray()) {
               friendsByLowercaseName.put(jsonElement.getAsString().toLowerCase(Locale.ROOT), jsonElement.getAsString());
            }
         } catch (Exception error) {
         }
      }
   }
}
