package com.mouseclient.util.time;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.entity.ModuleSoundPlayer;
import com.mouseclient.util.entity.MinecraftAccess;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Generated;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.ClickEvent.SuggestCommand;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.text.StyleSpriteSource.Font;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import ru.dreamix.protection.api.impl.profile.ProfileAPI;
import ru.dreamix.protection.api.impl.profile.objects.RoleObject;

public final class IrcClient implements MinecraftAccess {
   private static IrcClient INSTANCE;

   private static final Style ICON_STYLE = Style.EMPTY.withFont(new Font(Identifier.of(("mouseclient"), ("irc")))).withColor((16777215)).withoutShadow();
   private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern(("HH:mm:ss")).withZone(ZoneId.systemDefault());
   private final HttpClient httpClient;
   private Path configPath;
   private boolean enabled;
   private volatile boolean pollInFlight;
   private long lastPollAtMillis;
   private HashMap<String, String> knownNicknames;
   private final EventListener<PlayerTickEvent> playerTickListener;

   private JsonObject requestPayload(String action) {
      JsonObject json = new JsonObject();
      json.addProperty(("action"), action);
      json.addProperty(("name"), ProfileAPI.get().getModel().getName());
      json.addProperty(("role"), ProfileAPI.get().getModel().getRole().name());
      json.addProperty(("nick"), minecraftClient.getSession().getUsername());
      return json;
   }

   public void sendMessage(String message) {
      if (!message.isBlank()) {
         JsonObject json = this.requestPayload(("send"));
         Matcher replyMatcher = Pattern.compile(("^([\\p{L}\\p{N}_.\\-]{1,24}),\\s*(.+)$")).matcher(message);
         boolean replyKnown = replyMatcher.matches() && this.knownNicknames.containsKey(replyMatcher.group(1).toLowerCase(Locale.ROOT));
         json.addProperty(("text"), replyKnown ? replyMatcher.group(2) : message);
         if (replyKnown) {
            json.addProperty(("replyTo"), replyMatcher.group(1));
            json.addProperty(("replyToNick"), this.knownNicknames.get(replyMatcher.group(1).toLowerCase(Locale.ROOT)));
         }

         this.request(json).thenAccept(response -> {
            if (response == null) {
               ChatMessages.printPrefixed(("&cIRC сейчас недоступен, попробуй позже."));
            } else if (!response.get(("ok")).getAsBoolean()) {
               ChatMessages.printPrefixed("&c" + (response.has(("message")) ? response.get(("message")).getAsString() : ("Сообщение не отправлено.")));
            } else {
               this.lastPollAtMillis = 0L;
            }
         });
      }
   }

   private int roleColor(RoleObject role) {
      return switch (role.name()) {
         case ("BETA") -> (11832575);
         case ("YOUTUBE") -> (16734826);
         case ("ADMIN") -> (16757575);
         default -> (13226457);
      };
   }

   private RoleObject parseRole(String name) {
      try {
         return RoleObject.valueOf(name);
      } catch (Exception error) {
         return RoleObject.DEFAULT;
      }
   }

   public void showOnlineUsers() {
      this.request(this.requestPayload(("online"))).thenAccept(json -> {
         if (json != null && json.has(("online"))) {
            MutableText onlineText = Text.literal("В IRC сейчас " + json.getAsJsonArray(("online")).size() + ": ");
            boolean firstUser = true;

            for (JsonElement jsonElement : json.getAsJsonArray(("online"))) {
               if (!firstUser) {
                  onlineText.append(Text.literal((", ")).formatted(Formatting.DARK_GRAY));
               }

               firstUser = false;
               RoleObject role = this.parseRole(jsonElement.getAsJsonObject().get(("role")).getAsString());
               onlineText.append(Text.literal(String.valueOf((char)((57360) + role.ordinal()))).setStyle(ICON_STYLE)).append((" "));
               onlineText.append(Text.literal(jsonElement.getAsJsonObject().get(("name")).getAsString()).setStyle(Style.EMPTY.withColor(this.roleColor(role))));
            }

            ChatMessages.printPrefixed(onlineText);
         } else {
            ChatMessages.printPrefixed(("&cIRC сейчас недоступен, попробуй позже."));
         }
      });
   }

   @Generated
   public boolean isEnabled() {
      return this.enabled;
   }

   private CompletableFuture<JsonObject> request(JsonObject payload) {
      return this.httpClient
         .sendAsync(
            HttpRequest.newBuilder(URI.create(("http://deluxegaymarket.fun/irc/irc.php")))
               .timeout(Duration.ofSeconds((6L)))
               .header(("Content-Type"), ("application/json"))
               .header(("X-Deluxe-Key"), ("dlx_7Qm2vX9pK4tR8wZ1nB6s"))
               .POST(BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
               .build(),
            BodyHandlers.ofString(StandardCharsets.UTF_8)
         )
         .thenApply(response -> response.statusCode() == 200 ? JsonParser.parseString(response.body()).getAsJsonObject() : null)
         .exceptionally(error -> null);
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
      this.lastPollAtMillis = 0L;
      if (!enabled) {
         this.request(this.requestPayload(("leave")));
      }

      try {
         Files.createDirectories(this.configPath.getParent());
         JsonObject json = new JsonObject();
         json.addProperty(("enabled"), enabled);
         Files.writeString(this.configPath, json.toString(), StandardCharsets.UTF_8);
      } catch (Exception error) {
      }
   }

   private IrcClient() {
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds((5L))).build();
      this.knownNicknames = new HashMap<>();
      this.playerTickListener = event -> {
         if (this.enabled && !this.pollInFlight && System.currentTimeMillis() - this.lastPollAtMillis >= (2000L)) {
            this.pollInFlight = true;
            this.lastPollAtMillis = System.currentTimeMillis();
            this.request(this.requestPayload(("poll"))).whenComplete((json, pollError) -> {
               this.pollInFlight = false;
               if (json != null && json.has(("messages"))) {
                  for (JsonElement jsonElement : json.getAsJsonArray(("messages"))) {
                     minecraftClient.execute(() -> this.displayMessage(jsonElement.getAsJsonObject()));
                  }
               }
            });
         }
      };
      this.configPath = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("irc.json"));

      try {
         if (Files.exists(this.configPath)) {
            this.enabled = JsonParser.parseString(Files.readString(this.configPath, StandardCharsets.UTF_8)).getAsJsonObject().get(("enabled")).getAsBoolean();
         }
      } catch (Exception error) {
      }
   }

   private void displayMessage(JsonObject message) {
      String senderName = message.get(("name")).getAsString();
      String messageText = message.get(("text")).getAsString();
      String senderNickname = message.get(("nick")).getAsString();
      String localName = ProfileAPI.get().getModel().getName();
      String localNickname = minecraftClient.getSession().getUsername();
      String replyToName = message.has(("replyTo")) ? message.get(("replyTo")).getAsString() : "";
      String replyToNickname = message.has(("replyToNick")) ? message.get(("replyToNick")).getAsString() : "";
      RoleObject senderRole = this.parseRole(message.get(("role")).getAsString());
      boolean fromSelf = senderName.equalsIgnoreCase(localName) && senderNickname.equalsIgnoreCase(localNickname);
      boolean replyToSelf = !fromSelf && replyToName.equalsIgnoreCase(localName) && (replyToNickname.isEmpty() || replyToNickname.equalsIgnoreCase(localNickname));
      boolean mentionsSelf = replyToSelf || !fromSelf && messageText.toLowerCase(Locale.ROOT).contains("@" + localName.toLowerCase(Locale.ROOT));
      if (!fromSelf) {
         this.knownNicknames.put(senderName.toLowerCase(Locale.ROOT), senderNickname);
      }

      MutableText senderTooltip = Text.literal(senderRole.toString())
         .setStyle(Style.EMPTY.withColor(this.roleColor(senderRole)))
         .append(
            Text.literal("\nНик в игре: " + (message.get(("nick")).getAsString().isEmpty() ? ("—") : message.get(("nick")).getAsString()))
               .formatted(Formatting.GRAY)
         )
         .append(Text.literal("\nОтправлено: " + TIME_FORMAT.format(Instant.ofEpochSecond(message.get(("time")).getAsLong()))).formatted(Formatting.GRAY));
      SuggestCommand replyCommand = new SuggestCommand("@" + senderName + ", ");
      MutableText chatLine = Text.empty();
      chatLine.append(Text.literal(("")).setStyle(ICON_STYLE.withColor(replyToSelf ? (16763709) : GuiVisuals.accentColor & (16777215))));
      chatLine.append(Text.literal((" ")));
      chatLine.append(Text.literal(String.valueOf((char)((57600) + Math.floorMod(message.get(("emoji")).getAsInt(), 20)))).setStyle(ICON_STYLE));
      chatLine.append(Text.literal((" ")));
      ShowText adminHover = ProfileAPI.get().getModel().getRole().priorityHigherOrEquals(RoleObject.ADMIN) ? new ShowText(senderTooltip) : null;
      chatLine.append(Text.literal(String.valueOf((char)((57360) + senderRole.ordinal()))).setStyle(ICON_STYLE.withHoverEvent(adminHover)));
      chatLine.append(Text.literal((" ")));
      chatLine.append(Text.literal(senderName).setStyle(Style.EMPTY.withColor(this.roleColor(senderRole)).withHoverEvent(adminHover).withClickEvent(replyCommand)));
      if (replyToSelf) {
         chatLine.append(Text.literal((" "))).append(Text.literal(("")).setStyle(ICON_STYLE.withHoverEvent(new ShowText(Text.literal(senderName + " ответил тебе")))));
      } else if (!replyToName.isEmpty()) {
         chatLine.append(Text.literal((" → ")).formatted(Formatting.DARK_GRAY)).append(Text.literal(replyToName).setStyle(Style.EMPTY.withColor((10134445))));
      }

      chatLine.append(Text.literal((" » ")).formatted(Formatting.DARK_GRAY));
      chatLine.append(Text.literal(messageText).setStyle(Style.EMPTY.withColor(replyToSelf ? (16765788) : (mentionsSelf ? (16769902) : (15593459)))));
      chatLine.append(Text.literal((" ")));
      chatLine.append(
         Text.literal(("")).setStyle(ICON_STYLE.withColor((9279397)).withHoverEvent(new ShowText(Text.literal("Ответить " + senderName))).withClickEvent(replyCommand))
      );
      ChatMessages.print(chatLine);
      if (mentionsSelf) {
         ModuleSoundPlayer.play(("irc_mention"));
         if (replyToSelf) {
            minecraftClient.inGameHud
               .setOverlayMessage(
                  Text.empty()
                     .append(Text.literal(("")).setStyle(ICON_STYLE))
                     .append(Text.literal(" " + senderName).setStyle(Style.EMPTY.withColor(this.roleColor(senderRole))))
                     .append(Text.literal((" » ")).formatted(Formatting.DARK_GRAY))
                     .append(Text.literal(messageText.length() > 60 ? messageText.substring(0, 60) + "…" : messageText).setStyle(Style.EMPTY.withColor((16765788)))),
                  false
               );
         }
      }
   }

   public static IrcClient getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new IrcClient();
      }

      return INSTANCE;
   }
}
