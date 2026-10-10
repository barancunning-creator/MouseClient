package com.mouseclient.gui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

public final class WallpaperManager {
   public static final String DEFAULT_WALLPAPER = "assets/mouseclient/textures/gui/mainmenu/background/oboiclashroyal/mouseokak.png";
   private static String currentWallpaper = DEFAULT_WALLPAPER;
   private static final List<WallpaperEntry> systemWallpapers = new ArrayList<>();
   private static final List<WallpaperEntry> allWallpapers = new ArrayList<>();
   private static final Set<Identifier> registeredCustomTextures = new HashSet<>();
   private static final Set<String> systemFileNames = new HashSet<>();
   private static boolean initialized = false;

   public record WallpaperEntry(String displayName, String texturePath, boolean isCustom) {}

   static {
      addSystemWallpaper("Mouse Client", "assets/mouseclient/textures/gui/mainmenu/background/oboiclashroyal/mouseokak.png");
      addSystemWallpaper("Hatsune Miku", "assets/mouseclient/textures/gui/mainmenu/background/oboiclashroyal/hatsunee.png");
      addSystemWallpaper("Chocola", "assets/mouseclient/textures/gui/mainmenu/background/oboiclashroyal/chocola.png");
      addSystemWallpaper("Barani", "assets/mouseclient/textures/gui/mainmenu/background/oboiclashroyal/barani.png");
      addSystemWallpaper("Konata", "assets/mouseclient/textures/gui/mainmenu/background/oboiclashroyal/konatt.png");
      addSystemWallpaper("Атмосфера", "assets/mouseclient/textures/gui/mainmenu/background/oboiclashroyal/minecraftatmosphere.png");
      addSystemWallpaper("Vanilla", "assets/mouseclient/textures/gui/mainmenu/background/oboiclashroyal/vanilla.png");
   }

   private static void addSystemWallpaper(String name, String path) {
      systemWallpapers.add(new WallpaperEntry(name, path, false));
      int lastSlash = path.lastIndexOf('/');
      if (lastSlash >= 0) {
         systemFileNames.add(path.substring(lastSlash + 1).toLowerCase(Locale.ROOT));
      }
   }

   public static void init() {
      if (initialized) {
         return;
      }
      initialized = true;

      File folder = getWallpapersFolder();
      if (!folder.exists()) {
         folder.mkdirs();
      }

      // Copy system wallpapers into folder so user sees them there
      exportSystemWallpapers(folder);

      File currentFile = new File(folder, "current_wallpaper.txt");
      if (currentFile.exists()) {
         try {
            String saved = Files.readString(currentFile.toPath()).trim();
            if (!saved.isEmpty()) {
               currentWallpaper = saved;
            }
         } catch (Exception ignored) {
         }
      } else {
         setCurrentWallpaper(DEFAULT_WALLPAPER);
      }

      refreshWallpapers();
   }

   private static void exportSystemWallpapers(File folder) {
      for (WallpaperEntry entry : systemWallpapers) {
         String path = entry.texturePath();
         int lastSlash = path.lastIndexOf('/');
         String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
         File target = new File(folder, fileName);
         if (!target.exists()) {
            try {
               InputStream in = WallpaperManager.class.getClassLoader().getResourceAsStream(path);
               if (in == null && path.startsWith("assets/")) {
                  in = WallpaperManager.class.getClassLoader().getResourceAsStream(path.substring("assets/".length()));
               }
               if (in == null) {
                  File devFile = new File("src/main/resources/" + path);
                  if (devFile.exists()) {
                     in = new FileInputStream(devFile);
                  }
               }
               if (in != null) {
                  try (InputStream stream = in; FileOutputStream out = new FileOutputStream(target)) {
                     stream.transferTo(out);
                  }
               }
            } catch (Exception ignored) {
            }
         }
      }
   }

   public static File getWallpapersFolder() {
      MinecraftClient client = MinecraftClient.getInstance();
      File runDir = client != null && client.runDirectory != null ? client.runDirectory : new File(".");
      return new File(runDir, "wallpapers");
   }

   public static void refreshWallpapers() {
      allWallpapers.clear();
      allWallpapers.addAll(systemWallpapers);

      File folder = getWallpapersFolder();
      if (folder.exists() && folder.isDirectory()) {
         File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
               && !systemFileNames.contains(lower);
         });

         if (files != null) {
            for (File file : files) {
               String rawName = file.getName();
               String cleanName = rawName.substring(0, rawName.lastIndexOf('.'));
               String safeId = cleanName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
               Identifier id = Identifier.of("mouseclient", "custom_wp_" + safeId);

               if (!registeredCustomTextures.contains(id)) {
                  NativeImage image = loadNativeImage(file);
                  if (image != null) {
                     MinecraftClient client = MinecraftClient.getInstance();
                     if (client != null && client.getTextureManager() != null) {
                        try {
                           NativeImageBackedTexture texture = new NativeImageBackedTexture(id::toString, image);
                           texture.setImage(null);
                           client.getTextureManager().registerTexture(id, texture);
                           registeredCustomTextures.add(id);
                        } catch (Exception ignored) {
                        }
                     }
                  }
               }

               allWallpapers.add(new WallpaperEntry(cleanName, "mouseclient:custom_wp_" + safeId, true));
            }
         }
      }
   }

   private static NativeImage loadNativeImage(File file) {
      try (FileInputStream in = new FileInputStream(file)) {
         return NativeImage.read(in);
      } catch (Exception ex) {
         try {
            BufferedImage bImg = ImageIO.read(file);
            if (bImg != null) {
               ByteArrayOutputStream baos = new ByteArrayOutputStream();
               ImageIO.write(bImg, "png", baos);
               try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                  return NativeImage.read(bais);
               }
            }
         } catch (Exception ignored) {
         }
      }
      return null;
   }

   public static String getCurrentWallpaper() {
      if (!initialized) {
         init();
      }
      return currentWallpaper;
   }

   public static void setCurrentWallpaper(String path) {
      currentWallpaper = path;
      try {
         File folder = getWallpapersFolder();
         if (!folder.exists()) {
            folder.mkdirs();
         }
         Files.writeString(new File(folder, "current_wallpaper.txt").toPath(), path);
      } catch (Exception ignored) {
      }
   }

   public static List<WallpaperEntry> getWallpapers() {
      if (!initialized) {
         init();
      }
      return allWallpapers;
   }

   public static void openFolder() {
      File folder = getWallpapersFolder();
      if (!folder.exists()) {
         folder.mkdirs();
      }
      Util.getOperatingSystem().open(folder);
   }
}
