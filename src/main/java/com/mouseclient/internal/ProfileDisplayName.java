package com.mouseclient.internal;

import net.minecraft.client.MinecraftClient;
import ru.dreamix.protection.api.impl.profile.ProfileAPI;

/** Display name resolver for local session and development environment. */
public final class ProfileDisplayName {
   private ProfileDisplayName() {
   }

   public static String get(String fallback) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         if (client.getSession() != null && client.getSession().getUsername() != null && !client.getSession().getUsername().isBlank()) {
            return client.getSession().getUsername();
         }
         if (client.player != null && client.player.getName() != null && !client.player.getName().getString().isBlank()) {
            return client.player.getName().getString();
         }
      }
      ProfileAPI profile = ProfileAPI.get();
      String name = profile != null && profile.getModel() != null ? profile.getModel().getName() : null;
      if (name != null && !name.isBlank() && !"user".equals(name) && !"@pasterplace".equals(name)) {
         return name;
      }
      return fallback != null ? fallback : "Player";
   }

   static String resolve(boolean development, String name, String fallback) {
      return get(fallback);
   }
}
