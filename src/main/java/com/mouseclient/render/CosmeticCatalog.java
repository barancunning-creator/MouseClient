package com.mouseclient.render;

import com.mouseclient.network.ClientInitializer;
import com.mouseclient.render.avatar.CosmeticAssetCache;
import com.mouseclient.util.time.CosmeticPlacement;
import com.mouseclient.util.time.CosmeticCategory;
import com.mouseclient.util.time.MouseClientCosmeticWarmup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.MinecraftClient;

public final class CosmeticCatalog {
   private static final List<CosmeticEntry> entries = new ArrayList<>();
   private static final Map<Integer, CosmeticEntry> entriesById = new HashMap<>();
   private static final Map<CosmeticCategory, List<CosmeticEntry>> entriesByCategory = new EnumMap<>(CosmeticCategory.class);
   private static int revision;

   private CosmeticCatalog() {
   }

   public static boolean loadHostedCatalog() {
      try {
         ArrayList<CosmeticEntry> hostedEntries = new ArrayList();

         for (JsonElement jsonElement : JsonParser.parseString(new String(CosmeticAssetCache.readAsset(("catalog.json")), StandardCharsets.UTF_8)).getAsJsonArray()) {
            JsonObject json = jsonElement.getAsJsonObject();
            JsonArray jsonArray = json.getAsJsonArray(("transform"));
            JsonArray alignmentJson = json.has(("align")) ? json.getAsJsonArray(("align")) : null;
            CosmeticPlacement placement = new CosmeticPlacement(
               json.get(("kind")).getAsString(),
               json.get(("anchor")).getAsString(),
               jsonArray.get(0).getAsFloat(),
               jsonArray.get(1).getAsFloat(),
               jsonArray.get(2).getAsFloat(),
               jsonArray.get(3).getAsFloat(),
               jsonArray.get(4).getAsFloat(),
               jsonArray.get(5).getAsFloat(),
               jsonArray.get(6).getAsFloat(),
               alignmentJson == null ? 0.0F : alignmentJson.get(0).getAsFloat(),
               alignmentJson == null ? 0.0F : alignmentJson.get(1).getAsFloat(),
               alignmentJson == null ? 1.0F : alignmentJson.get(2).getAsFloat(),
               json.has(("flying")) && json.get(("flying")).getAsBoolean(),
               new ArrayList<>()
            );
            if (json.has(("targets"))) {
               for (JsonElement targetElement : json.getAsJsonArray(("targets"))) {
                  placement.targets().add(targetElement.getAsString());
               }
            }

            hostedEntries.add(
               new CosmeticEntry(
                  json.get(("id")).getAsInt(),
                  CosmeticCategory.valueOf(json.get(("category")).getAsString().toUpperCase(Locale.ROOT)),
                  json.get(("name")).getAsString(),
                  json.has(("beta")) && json.get(("beta")).getAsBoolean(),
                  json.get(("model")).getAsString(),
                  null,
                  json.get(("preview")).getAsString(),
                  placement
               )
            );
         }

         MinecraftClient.getInstance().execute(() -> {
            for (CosmeticEntry entry : hostedEntries) {
               if (!entriesById.containsKey(entry.id())) {
                  entries.add(entry);
                  entriesById.put(entry.id(), entry);
                  entriesByCategory.computeIfAbsent(entry.category(), category -> new ArrayList<>()).add(entry);
               }
            }

            revision++;
            MouseClientCosmeticWarmup.warmEquipped();
         });
         return true;
      } catch (Exception error) {
         ClientInitializer.logger().warn(("[Cosmetics] catalog.json not loaded: {}"), error.toString());
         return false;
      }
   }

   public static CosmeticEntry getById(int id) {
      return entriesById.get(id);
   }

   private static void registerBuiltin(int id, CosmeticCategory category, String name, boolean beta) {
      String resourceDirectory = "models/" + category.resourceDirectory() + "/" + id + "/";
      CosmeticEntry entry = new CosmeticEntry(id, category, name, beta, resourceDirectory + "model.json", resourceDirectory + "texture.png", resourceDirectory + "preview.png", null);
      entries.add(entry);
      entriesById.put(id, entry);
      entriesByCategory.computeIfAbsent(category, categoryKey -> new ArrayList<>()).add(entry);
   }

   static {
      registerBuiltin(36, CosmeticCategory.BODYWEAR, ("Louis Vuitton"), true);
      registerBuiltin(37, CosmeticCategory.BODYWEAR, ("Gucci"), false);
      registerBuiltin(38, CosmeticCategory.BODYWEAR, ("Adidas"), false);
      registerBuiltin(39, CosmeticCategory.BODYWEAR, ("Nike"), false);
      registerBuiltin(40, CosmeticCategory.BODYWEAR, ("Supreme"), false);
      registerBuiltin(41, CosmeticCategory.BODYWEAR, ("Vlone"), false);
      registerBuiltin(42, CosmeticCategory.BODYWEAR, ("Bape"), false);
      registerBuiltin(43, CosmeticCategory.BODYWEAR, ("Balenciaga"), true);
      registerBuiltin(44, CosmeticCategory.BODYWEAR, ("Chrome Hearts"), false);
      registerBuiltin(95, CosmeticCategory.BODYWEAR, ("PePe"), false);
      registerBuiltin(137, CosmeticCategory.BODYWEAR, ("Катана - ila_studio"), true);
      registerBuiltin(25, CosmeticCategory.WINGS, ("Aurum"), false);
      registerBuiltin(61, CosmeticCategory.WINGS, ("Angel"), false);
      registerBuiltin(62, CosmeticCategory.WINGS, ("Dragon"), false);
      registerBuiltin(63, CosmeticCategory.WINGS, ("Diamond"), false);
      registerBuiltin(64, CosmeticCategory.WINGS, ("Spider"), false);
      registerBuiltin(65, CosmeticCategory.WINGS, ("Easter"), true);
      registerBuiltin(82, CosmeticCategory.WINGS, ("Fluger"), true);
      registerBuiltin(86, CosmeticCategory.WINGS, ("Трезубец - Броя"), true);
      registerBuiltin(91, CosmeticCategory.WINGS, ("Аквыч"), false);
      registerBuiltin(99, CosmeticCategory.WINGS, ("Dark"), false);
      registerBuiltin(123, CosmeticCategory.WINGS, ("Pulse"), false);
      registerBuiltin(124, CosmeticCategory.WINGS, ("Ghoul"), false);
      registerBuiltin(136, CosmeticCategory.WINGS, ("Ягуар"), false);
      registerBuiltin(48, CosmeticCategory.HAT, ("Armour"), false);
      registerBuiltin(49, CosmeticCategory.HAT, ("Capybara"), false);
      registerBuiltin(50, CosmeticCategory.HAT, ("Chicken"), false);
      registerBuiltin(53, CosmeticCategory.HAT, ("Pepe"), false);
      registerBuiltin(57, CosmeticCategory.HAT, ("Sheep"), false);
      registerBuiltin(80, CosmeticCategory.HAT, ("Fluger"), true);
      registerBuiltin(88, CosmeticCategory.HAT, ("Аквыч"), false);
      registerBuiltin(92, CosmeticCategory.HAT, ("Bear"), false);
      registerBuiltin(96, CosmeticCategory.HAT, ("ПеПе"), false);
      registerBuiltin(97, CosmeticCategory.HAT, ("Happy"), false);
      registerBuiltin(98, CosmeticCategory.HAT, ("Нимб"), true);
      registerBuiltin(45, CosmeticCategory.PET, ("Angel"), false);
      registerBuiltin(46, CosmeticCategory.PET, ("Demon"), false);
      registerBuiltin(47, CosmeticCategory.PET, ("Buddy"), false);
      registerBuiltin(81, CosmeticCategory.PET, ("Fluger Door"), true);
      registerBuiltin(84, CosmeticCategory.PET, ("Diamond"), true);
      registerBuiltin(90, CosmeticCategory.PET, ("Аквыч"), true);
      registerBuiltin(93, CosmeticCategory.PET, ("Bee"), false);
      registerBuiltin(126, CosmeticCategory.PET, ("1 Год"), false);
      registerBuiltin(132, CosmeticCategory.PET, ("Ягуар"), false);
      registerBuiltin(133, CosmeticCategory.PET, ("Cergifff"), false);
      registerBuiltin(134, CosmeticCategory.PET, ("Ila_Studio"), false);
      registerBuiltin(135, CosmeticCategory.PET, ("Creeper"), false);

      for (CosmeticCategory category : CosmeticCategory.values()) {
         entriesByCategory.computeIfAbsent(category, categoryKey -> new ArrayList<>());
      }
   }

   public static int revision() {
      return revision;
   }

   public static List<CosmeticEntry> inCategory(CosmeticCategory category) {
      return Collections.unmodifiableList(entriesByCategory.getOrDefault(category, List.of()));
   }

   public static List<CosmeticEntry> allEntries() {
      return Collections.unmodifiableList(entries);
   }
}
