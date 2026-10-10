package com.mouseclient.render;

import com.mouseclient.gui.Render2D;
import com.mouseclient.util.time.AltAccount;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;

public final class AccountSkinRenderer {
   private static PlayerEntityModel wideModel;
   private static PlayerEntityModel slimModel;
   private static final ConcurrentHashMap<String, Supplier<SkinTextures>> skinsByUsername = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<String, Boolean> requestedUsernames = new ConcurrentHashMap<>();
   private static final HttpClient HTTP = HttpClient.newHttpClient();
   private static final Pattern PROFILE_ID_PATTERN = Pattern.compile(("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\""));
   private static final Pattern TEXTURE_PROPERTY_PATTERN = Pattern.compile(("\\{[^{}]*?\"name\"\\s*:\\s*\"textures\"[^{}]*?\"value\"\\s*:\\s*\"([^\"]+)\"(?:[^{}]*?\"signature\"\\s*:\\s*\"([^\"]+)\")?[^{}]*?\\}"));
   private static int warmupIndex;

   private AccountSkinRenderer() {
   }

   private static void applyPose(PlayerEntityModel model) {
      float pitch = (0.05F);
      model.leftArm.pitch = pitch;
      model.rightArm.pitch = -pitch;
      model.leftLeg.pitch = -pitch;
      model.rightLeg.pitch = pitch;
      model.leftSleeve.pitch = pitch;
      model.rightSleeve.pitch = -pitch;
      model.leftPants.pitch = -pitch;
      model.rightPants.pitch = pitch;
      model.head.pitch = 0.0F;
      model.hat.pitch = 0.0F;
   }

   public static void render(DrawContext context, AltAccount account, float guiScale, float centerX, float centerY, float size) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         try {
            initializeModels(client);
         } catch (RuntimeException error) {
            return;
         }

         SkinTextures skin = skinTextures(account.username(), account.uuid());
         PlayerEntityModel model = skin.model() == PlayerSkinType.SLIM ? slimModel : wideModel;
         applyPose(model);
         context.addPlayerSkin(
            model,
            skin.body().texturePath(),
            Math.round(size / guiScale),
            (-10.0F),
            (20.0F),
            (-40.0F),
            Math.round(centerX / guiScale) - Math.round(Math.round(size / guiScale) * (0.8F)),
            Math.round(centerY / guiScale) - Math.round(Math.round(size / guiScale) * (0.8F)),
            Math.round(centerX / guiScale) + Math.round(Math.round(size / guiScale) * (0.8F)),
            Math.round(centerY / guiScale) + Math.round(Math.round(size / guiScale) * (0.8F))
         );
      }
   }

   private static void fetchOnlineSkin(MinecraftClient client, String username) {
      if (username.matches(("[A-Za-z0-9_]{1,16}"))) {
         if (requestedUsernames.putIfAbsent(username, Boolean.TRUE) == null) {
            Thread requestThread = new Thread(
               () -> {
                  try {
                     Matcher profileMatcher = PROFILE_ID_PATTERN.matcher(readJson("https://api.mojang.com/users/profiles/minecraft/" + username));
                     if (!profileMatcher.find()) {
                        return;
                     }

                     Matcher textureMatcher = TEXTURE_PROPERTY_PATTERN.matcher(
                        readJson("https://sessionserver.mojang.com/session/minecraft/profile/" + profileMatcher.group(1) + "?unsigned=false")
                     );
                     if (!textureMatcher.find()) {
                        return;
                     }

                     GameProfile profile = new GameProfile(UUID.fromString(profileMatcher.group(1).replaceFirst(("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"), ("$1-$2-$3-$4-$5"))), username);
                     profile.properties()
                        .put(("textures"), textureMatcher.group(2) != null ? new Property(("textures"), textureMatcher.group(1), textureMatcher.group(2)) : new Property(("textures"), textureMatcher.group(1)));
                     client.execute(() -> skinsByUsername.put(username, client.getSkinProvider().supplySkinTextures(profile, false)));
                  } catch (Exception error) {
                  }
               },
               "mouseclient-skin-" + username
            );
            requestThread.setDaemon(true);
            requestThread.start();
         }
      }
   }

   public static SkinTextures skinTextures(String username, UUID uuid) {
      MinecraftClient client = MinecraftClient.getInstance();
      Supplier skinSupplier = skinsByUsername.get(username);
      if (skinSupplier == null) {
         skinSupplier = client.getSkinProvider().supplySkinTextures(new GameProfile(uuid, username), false);
         skinsByUsername.put(username, skinSupplier);
         fetchOnlineSkin(client, username);
      }

      return (SkinTextures)skinSupplier.get();
   }

   private static String readJson(String url) throws Exception {
      HttpResponse response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header(("Accept"), ("application/json")).GET().build(), BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
         return "";
      }

      try (InputStream body = (InputStream)response.body()) {
         return new String(body.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   private static void initializeModels(MinecraftClient client) {
      if (wideModel == null) {
         wideModel = new PlayerEntityModel(client.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
         slimModel = new PlayerEntityModel(client.getLoadedEntityModels().getModelPart(new net.minecraft.client.render.entity.model.EntityModelLayer(net.minecraft.util.Identifier.ofVanilla("player_slim"), "main")), true);
      }
   }

   public static void warmup(DrawContext context, ArrayList<AltAccount> accounts) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         try {
            initializeModels(client);
         } catch (RuntimeException error) {
            return;
         }

         SkinTextures skin = skinTextures(("jeb_"), AltAccount.offlineUuid(("jeb_")));
         Render2D.drawNearestImageRegion(
            skin.body().texturePath().toString(), 0.0F, 0.0F, 2.0F, 2.0F, 0.0F, 1.0F, (0.125F), (0.125F), (0.25F), (0.25F), -1
         );
         if (!accounts.isEmpty()) {
            skin = skinTextures(
               ((AltAccount)accounts.get(Math.floorMod(warmupIndex, accounts.size()))).username(), ((AltAccount)accounts.get(Math.floorMod(warmupIndex++, accounts.size()))).uuid()
            );
         }

         PlayerEntityModel model = skin.model() == PlayerSkinType.SLIM ? slimModel : wideModel;
         applyPose(model);

         try {
            context.addPlayerSkin(model, skin.body().texturePath(), (24.0F), (-10.0F), (20.0F), (-40.0F), 0, 0, 24, 24);
         } catch (RuntimeException renderError) {
         }
      }
   }
}
