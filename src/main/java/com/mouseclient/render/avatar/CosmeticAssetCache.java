package com.mouseclient.render.avatar;

import com.mouseclient.network.ClientInitializer;
import com.mouseclient.render.CosmeticEntry;
import com.mouseclient.util.time.MouseClientCosmeticWarmup;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

public final class CosmeticAssetCache {
   
   private static final Path CACHE_DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("cosmetics-cache"));
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds((6L))).followRedirects(Redirect.NORMAL).build();
   private static final Set<Identifier> registeredTextures = ConcurrentHashMap.newKeySet();
   private static final Set<Identifier> scheduledTextures = ConcurrentHashMap.newKeySet();
   private static final Map<Integer, CosmeticAssetCache.PreviewAtlasCell> previewCells = new ConcurrentHashMap<>();
   private static Map<String, String> manifestHashes;
   private static long lastManifestFailureMillis;

   public static boolean warmTexture(Identifier id, String resourcePath) {
      boolean loaded = loadTexture(id, resourcePath, null);

      try {
         Thread.sleep((120L));
      } catch (InterruptedException error) {
         Thread.currentThread().interrupt();
      }

      return loaded;
   }

   public static boolean loadPreviewAtlasIndex() {
      try {
         JsonObject json = JsonParser.parseString(new String(readAsset(("previews/atlas/index.json")), StandardCharsets.UTF_8)).getAsJsonObject();
         float cellSize = json.get(("cell")).getAsFloat();

         for (Entry atlasEntry : json.getAsJsonObject(("atlases")).entrySet()) {
            JsonObject atlasJson = ((JsonElement)atlasEntry.getValue()).getAsJsonObject();
            String atlasFile = atlasJson.get(("file")).getAsString();
            float atlasWidth = atlasJson.get(("width")).getAsFloat();
            float atlasHeight = atlasJson.get(("height")).getAsFloat();
            Identifier id = Identifier.of(("mouseclient"), "cosmetics/" + atlasFile);

            for (Entry cellEntry : atlasJson.getAsJsonObject(("cells")).entrySet()) {
               float cellX = ((JsonElement)cellEntry.getValue()).getAsJsonArray().get(0).getAsFloat();
               float cellY = ((JsonElement)cellEntry.getValue()).getAsJsonArray().get(1).getAsFloat();
               previewCells.put(
                  Integer.parseInt((String)cellEntry.getKey()),
                  new CosmeticAssetCache.PreviewAtlasCell(
                     id,
                     id.toString(),
                     (cellX + (0.5F)) / atlasWidth,
                     (cellY + (0.5F)) / atlasHeight,
                     (cellX + cellSize - (0.5F)) / atlasWidth,
                     (cellY + cellSize - (0.5F)) / atlasHeight
                  )
               );
            }

            MouseClientCosmeticWarmup.warmPreview(id, atlasFile);
         }

         return true;
      } catch (Exception error) {
         ClientInitializer.logger().warn(("[Cosmetics] preview atlases not loaded: {}"), error.toString());
         return false;
      }
   }

   private static Map<String, String> loadManifest() {
      if (manifestHashes != null) {
         return manifestHashes;
      }

      if (System.currentTimeMillis() - lastManifestFailureMillis < (30000L)) {
         return null;
      }

      try {
         HttpResponse response = HTTP.send(
            HttpRequest.newBuilder(URI.create(("http://deluxegaymarket.fun/cosmetics/manifest.php"))).timeout(Duration.ofSeconds((8L))).GET().build(), BodyHandlers.ofString(StandardCharsets.UTF_8)
         );
         if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
         }

         JsonObject json = JsonParser.parseString((String)response.body()).getAsJsonObject().getAsJsonObject(("files"));
         HashMap hashes = new HashMap();

         for (Entry fileEntry : json.entrySet()) {
            hashes.put((String)fileEntry.getKey(), ((JsonElement)fileEntry.getValue()).getAsString());
         }

         if (!hashes.isEmpty() && Files.isDirectory(CACHE_DIRECTORY)) {
            try (java.util.stream.Stream<java.nio.file.Path> cachedPaths = Files.walk(CACHE_DIRECTORY)) {
               for (Path cachedPath : cachedPaths.sorted(Comparator.reverseOrder()).toList()) {
                  if (!cachedPath.equals(CACHE_DIRECTORY) && !hashes.containsKey(CACHE_DIRECTORY.relativize(cachedPath).toString().replace('\\', '/'))) {
                     try {
                        Files.delete(cachedPath);
                     } catch (IOException error) {
                     }
                  }
               }
            } catch (IOException cacheWalkFailure) {
            }
         }

         manifestHashes = hashes;
         return manifestHashes;
      } catch (Exception manifestFailure) {
         if (manifestFailure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
         }

         lastManifestFailureMillis = System.currentTimeMillis();
         ClientInitializer.logger().warn(("[Cosmetics] manifest not loaded: {}"), manifestFailure.toString());
         return null;
      }
   }

   public static CosmeticAssetCache.PreviewAtlasCell previewCell(CosmeticEntry entry) {
      if (entry == null) {
         return null;
      }

      CosmeticAssetCache.PreviewAtlasCell cell = previewCells.get(entry.id());
      if (cell != null && registeredTextures.contains(cell.atlasId())) {
         return cell;
      }

      MouseClientCosmeticWarmup.warmAtlasIndex();
      return null;
   }

   public static synchronized byte[] readAsset(String resourcePath) throws IOException {
      Path cachePath = CACHE_DIRECTORY.resolve(resourcePath);
      java.util.Map<java.lang.String,java.lang.String> hashes = loadManifest();
      if (hashes == null) {
         if (Files.isRegularFile(cachePath)) {
            return Files.readAllBytes(cachePath);
         } else {
            throw new IOException("hosting unreachable and no cache for " + resourcePath);
         }
      } else {
         String expectedHash = (String)hashes.get(resourcePath);
         if (expectedHash == null) {
            throw new FileNotFoundException(resourcePath + " missing on hosting");
         }

         if (Files.isRegularFile(cachePath)) {
            byte[] cachedData = Files.readAllBytes(cachePath);
            if (md5(cachedData).equals(expectedHash)) {
               return cachedData;
            }
         }

         HttpResponse response;
         try {
            response = HTTP.send(
               HttpRequest.newBuilder(URI.create("http://deluxegaymarket.fun/cosmetics/" + resourcePath + "?v=" + expectedHash))
                  .timeout(Duration.ofSeconds((20L)))
                  .GET()
                  .build(),
               BodyHandlers.ofByteArray()
            );
         } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException(error);
         }

         if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + resourcePath);
         }

         byte[] downloadedData = (byte[])response.body();
         if (!md5(downloadedData).equals(expectedHash)) {
            throw new IOException("broken download " + resourcePath);
         }

         Files.createDirectories(cachePath.getParent());
         Path partialPath = cachePath.resolveSibling(cachePath.getFileName() + ".part");
         Files.write(partialPath, downloadedData);
         Files.move(partialPath, cachePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         return downloadedData;
      }
   }

   public static boolean loadTexture(Identifier id, String resourcePath, Runnable onRegistered) {
      if (scheduledTextures.contains(id)) {
         if (onRegistered != null) {
            MinecraftClient.getInstance().execute(onRegistered);
         }

         return true;
      } else {
         try {
            NativeImage image = NativeImage.read(readAsset(resourcePath));
            scheduledTextures.add(id);
            MinecraftClient.getInstance().execute(() -> {
               NativeImageBackedTexture texture = new NativeImageBackedTexture(id::toString, image);
               texture.setImage(null);
               MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
               registeredTextures.add(id);
               if (onRegistered != null) {
                  onRegistered.run();
               }
            });
            return true;
         } catch (Exception error) {
            ClientInitializer.logger().warn(("[Cosmetics] texture {} not loaded: {}"), resourcePath, error.toString());
            return false;
         }
      }
   }

   private static String md5(byte[] data) {
      try {
         return HexFormat.of().formatHex(MessageDigest.getInstance(("MD5")).digest(data));
      } catch (Exception error) {
         throw new IllegalStateException(error);
      }
   }

   private CosmeticAssetCache() {
   }

   public record PreviewAtlasCell(Identifier atlasId, String atlasTexture, float minU, float minV, float maxU, float maxV) {
      public float maxV() {
         return this.maxV;
      }

      public float maxU() {
         return this.maxU;
      }

      public float minU() {
         return this.minU;
      }

      public float minV() {
         return this.minV;
      }
   }
}
