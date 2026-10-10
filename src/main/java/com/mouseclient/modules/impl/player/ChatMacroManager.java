package com.mouseclient.modules.impl.player;

import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.time.ConfigStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

public final class ChatMacroManager implements MinecraftAccess {
   private static ChatMacroManager INSTANCE;
   private final LinkedHashMap<String, ChatMacroManager.ChatMacro> macrosByName = new LinkedHashMap<>();
   private final HashSet<String> pressedMacros = new HashSet<>();
   private Path macrosPath;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && !this.macrosByName.isEmpty()) {
         for (ChatMacroManager.ChatMacro macro : this.macrosByName.values()) {
            if (!macro.key().isPressed(minecraftClient.getWindow().getHandle())) {
               this.pressedMacros.remove(macro.name());
            } else if (this.pressedMacros.add(macro.name())) {
               if (macro.chatLine().startsWith(("/"))) {
                  minecraftClient.player.networkHandler.sendChatCommand(macro.chatLine().substring(1));
               } else {
                  minecraftClient.player.networkHandler.sendChatMessage(macro.chatLine());
               }
            }
         }
      }
   };

   public InputBinding parseKeyName(String keyName) {
      if (keyName.length() == 2 && (keyName.charAt(0) == 'M' || keyName.charAt(0) == 'm') && Character.isDigit(keyName.charAt(1))) {
         return InputBinding.mouse(Character.getNumericValue(keyName.charAt(1)) - 1);
      }

      for (int var2 = 32; var2 <= 348; var2++) {
         if (InputBinding.keyboard(var2).getDisplayName().equalsIgnoreCase(keyName)) {
            return InputBinding.keyboard(var2);
         }
      }

      return InputBinding.INSTANCE;
   }

   private void saveMacros() {
      try {
         Files.createDirectories(this.macrosPath.getParent());
         JsonArray jsonArray = new JsonArray();

         for (ChatMacroManager.ChatMacro macro : this.macrosByName.values()) {
            JsonObject json = new JsonObject();
            json.addProperty(("name"), macro.name());
            json.addProperty(("key"), macro.key().getType().name() + ":" + macro.key().getCode());
            json.addProperty(("text"), macro.chatLine());
            jsonArray.add(json);
         }

         Files.writeString(this.macrosPath, jsonArray.toString(), StandardCharsets.UTF_8);
      } catch (Exception error) {
      }
   }

   public void putMacro(String name, InputBinding binding, String chatLine) {
      this.macrosByName.put(name.toLowerCase(Locale.ROOT), new ChatMacroManager.ChatMacro(name, binding, chatLine));
      this.saveMacros();
   }

   private ChatMacroManager() {
      this.macrosPath = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("macros.json"));

      try {
         if (!Files.exists(this.macrosPath)) {
            return;
         }

         for (JsonElement jsonElement : JsonParser.parseString(Files.readString(this.macrosPath, StandardCharsets.UTF_8)).getAsJsonArray()) {
            this.macrosByName
               .put(
                  jsonElement.getAsJsonObject().get(("name")).getAsString().toLowerCase(Locale.ROOT),
                  new ChatMacroManager.ChatMacro(
                     jsonElement.getAsJsonObject().get(("name")).getAsString(),
                     ConfigStore.parseBinding(jsonElement.getAsJsonObject().get(("key")).getAsString()),
                     jsonElement.getAsJsonObject().get(("text")).getAsString()
                  )
               );
         }
      } catch (Exception error) {
      }
   }

   public boolean removeMacro(String name) {
      if (this.macrosByName.remove(name.toLowerCase(Locale.ROOT)) == null) {
         return false;
      }

      this.saveMacros();
      return true;
   }

   public static ChatMacroManager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ChatMacroManager();
      }

      return INSTANCE;
   }

   public ArrayList<ChatMacroManager.ChatMacro> macros() {
      return new ArrayList<>(this.macrosByName.values());
   }

   public void clearMacros() {
      this.macrosByName.clear();
      this.pressedMacros.clear();
      this.saveMacros();
   }

   public record ChatMacro(String name, InputBinding key, String chatLine) {
   }
}
