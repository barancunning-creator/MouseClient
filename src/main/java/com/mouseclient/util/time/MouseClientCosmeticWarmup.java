package com.mouseclient.util.time;

import com.mouseclient.dreamix.protection.api.impl.profile.models.CosmeticModelCache;
import com.mouseclient.render.CosmeticEntry;
import com.mouseclient.render.EquippedCosmetics;
import com.mouseclient.render.JsonCosmeticModel;
import com.mouseclient.render.CosmeticCatalog;
import com.mouseclient.render.avatar.CosmeticAssetCache;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import net.minecraft.util.Identifier;

public final class MouseClientCosmeticWarmup {
   private static final Set<String> submittedResources = ConcurrentHashMap.newKeySet();
   private static final Map<String, Long> failedAtMillis = new ConcurrentHashMap<>();
   private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
      Thread workerThread = new Thread(task, ("mouseclient-cosmetic-warmup"));
      workerThread.setDaemon(true);
      workerThread.setPriority(1);
      return workerThread;
   });

   public static void warmEquipped() {
      for (CosmeticCategory category : CosmeticCategory.values()) {
         warmModel(EquippedCosmetics.equippedEntry(category));
      }
   }

   public static void warmModel(CosmeticEntry entry) {
      if (entry != null) {
         scheduleWarmup(entry.modelResource(), () -> entry.placement() != null ? JsonCosmeticModel.load(entry) : CosmeticModelCache.warmup(entry));
      }
   }

   public static void warmAtlasIndex() {
      scheduleWarmup(("previews/atlas/index.json"), CosmeticAssetCache::loadPreviewAtlasIndex);
   }

   private MouseClientCosmeticWarmup() {
   }

   public static void warmPreview(Identifier id, String resourcePath) {
      scheduleWarmup(resourcePath, () -> CosmeticAssetCache.warmTexture(id, resourcePath));
   }

   private static void scheduleWarmup(String resourcePath, BooleanSupplier loader) {
      Long lastFailureMillis = failedAtMillis.get(resourcePath);
      if (lastFailureMillis != null && System.currentTimeMillis() - lastFailureMillis >= (30000L) && failedAtMillis.remove(resourcePath, lastFailureMillis)) {
         submittedResources.remove(resourcePath);
      }

      if (submittedResources.add(resourcePath)) {
         WORKER.execute(() -> {
            if (!loader.getAsBoolean()) {
               failedAtMillis.put(resourcePath, System.currentTimeMillis());
            }
         });
      }
   }

   public static void warmCatalog() {
      scheduleWarmup(("catalog.json"), CosmeticCatalog::loadHostedCatalog);
   }
}
