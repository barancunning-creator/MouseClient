package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.ClientTickEvent;
import com.mouseclient.render.CosmeticEntry;
import com.mouseclient.render.EquippedCosmetics;
import com.mouseclient.render.CosmeticCatalog;
import com.mouseclient.util.time.CosmeticCategory;
import com.mouseclient.util.time.CosmeticWheelScreen;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;

@ModuleInfo(name = "Globals", description = "Синхронизирует косметику с игроками, у которых тоже включён Globals.", category = ModuleCategory.MISC)
public final class Globals extends Module {
   private static Globals INSTANCE;

   public BooleanSetting syncModels;
   public ModeSetting syncTargets;
   public BindSetting cosmeticsMenuBind;
   private final HttpClient httpClient;
   private final ExecutorService syncExecutor;
   private final AtomicBoolean requestInFlight;
   private volatile String sessionId;
   private volatile Globals.CosmeticSyncSnapshot syncSnapshot;
   private String sessionNick;
   private String sessionHost;
   private String lastPayload;
   private long lastRequestMillis;
   private boolean menuKeyWasPressed;
   public final EventListener<ClientTickEvent> clientTickListener;

   private Future<?> leaveSession() {
      String leavingSessionId = this.sessionId;
      String leavingNick = this.sessionNick;
      this.sessionId = null;
      this.sessionNick = null;
      this.sessionHost = null;
      this.syncSnapshot = null;
      this.lastPayload = null;
      this.lastRequestMillis = 0L;
      if (leavingSessionId == null) {
         return null;
      }

      JsonObject json = new JsonObject();
      json.addProperty(("action"), ("leave"));
      json.addProperty(("nick"), leavingNick);
      json.addProperty(("session"), leavingSessionId);
      return this.syncExecutor.submit(() -> {
         try {
            this.postSyncRequest(json.toString());
         } catch (Exception error) {
         }
      });
   }

   public boolean hasSyncSnapshot() {
      return this.syncSnapshot != null;
   }

   public CosmeticEntry syncedCosmetic(String nick, CosmeticCategory category) {
      Globals.CosmeticSyncSnapshot snapshot = this.syncSnapshot;
      if (snapshot == null || nick == null || !snapshot.sessionId().equals(this.sessionId) || System.currentTimeMillis() - snapshot.receivedAtMs() > (12000L)) {
         return null;
      }

      if (this.syncTargets.isSelected(("Только друзья")) && !FriendManager.contains(nick)) {
         return null;
      }

      java.util.EnumMap<com.mouseclient.util.time.CosmeticCategory,java.lang.Integer> selections = snapshot.selectionsByNick().get(nick.toLowerCase(Locale.ROOT));
      CosmeticEntry entry = selections == null ? null : CosmeticCatalog.getById(selections.getOrDefault(category, -1));
      return entry != null && entry.category() == category ? entry : null;
   }

   @Override
   protected void onDisable() {
      this.leaveSession();
   }

   private Globals() {
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds((5L))).build();
      this.syncExecutor = Executors.newSingleThreadExecutor(task -> {
         Thread syncThread = new Thread(task, ("mouseclient-globals-sync"));
         syncThread.setDaemon(true);
         return syncThread;
      });
      this.requestInFlight = new AtomicBoolean();
      this.clientTickListener = event -> {
         if (event.isPre()) {
            boolean menuKeyPressed = this.cosmeticsMenuBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            if (menuKeyPressed && !this.menuKeyWasPressed && minecraftClient.currentScreen == null && minecraftClient.player != null) {
               minecraftClient.setScreen(new CosmeticWheelScreen());
            }

            this.menuKeyWasPressed = menuKeyPressed;
            ServerInfo serverInfo = minecraftClient.getCurrentServerEntry();
            IntegratedServer integratedServer = minecraftClient.getServer();
            boolean lanServer = integratedServer != null && integratedServer.isRemote();
            boolean canSync = this.syncModels.getValue()
               && minecraftClient.player != null
               && minecraftClient.world != null
               && minecraftClient.getNetworkHandler() != null
               && (serverInfo != null || lanServer);
            InetSocketAddress remoteAddress = canSync
                  && minecraftClient.getNetworkHandler().getConnection().getAddress() instanceof InetSocketAddress socketAddress
                  && socketAddress.getAddress() != null
               ? socketAddress
               : null;
            String nick = canSync ? minecraftClient.player.getGameProfile().name().toLowerCase(Locale.ROOT) : null;
            String host = !canSync
               ? null
               : (
                  lanServer
                     ? "lan:" + integratedServer.getServerPort()
                     : (
                        remoteAddress == null || !remoteAddress.getAddress().isLoopbackAddress() && !remoteAddress.getAddress().isSiteLocalAddress()
                           ? serverInfo.address.trim().toLowerCase(Locale.ROOT).replaceFirst((":25565$"), "").replaceFirst(("\\.$"), "")
                           : "lan:" + remoteAddress.getPort()
                     )
               );
            if (!Objects.equals(nick, this.sessionNick) || !Objects.equals(host, this.sessionHost)) {
               this.leaveSession();
               if (nick == null) {
                  return;
               }

               this.sessionNick = nick;
               this.sessionHost = host;
               this.sessionId = UUID.randomUUID().toString();
            }

            if (nick != null && System.currentTimeMillis() - this.lastRequestMillis >= (1000L)) {
               JsonObject json = new JsonObject();
               json.addProperty(("action"), ("beat"));
               json.addProperty(("nick"), nick);
               json.addProperty(("session"), this.sessionId);
               json.addProperty(("host"), host);
               json.addProperty(("ip"), remoteAddress != null ? remoteAddress.getAddress().getHostAddress() + ":" + remoteAddress.getPort() : "");
               JsonObject selectionsJson = new JsonObject();

               for (CosmeticCategory category : CosmeticCategory.values()) {
                  CosmeticEntry entry = EquippedCosmetics.equippedEntry(category);
                  selectionsJson.addProperty(category.name().toLowerCase(Locale.ROOT), entry == null ? -1 : entry.id());
               }

               json.add(("selections"), selectionsJson);
               JsonArray jsonArray = new JsonArray();

               for (String friendName : FriendManager.names()) {
                  jsonArray.add(friendName.toLowerCase(Locale.ROOT));
               }

               json.add(("friends"), jsonArray);
               json.addProperty(("audience"), this.syncTargets.isSelected(("Все с Globals")) ? ("all") : ("friends"));
               LinkedHashSet<String> nearbyNames = new LinkedHashSet();

               for (AbstractClientPlayerEntity player : minecraftClient.world.getPlayers()) {
                  if (player != minecraftClient.player) {
                     nearbyNames.add(player.getGameProfile().name().toLowerCase(Locale.ROOT));
                  }
               }

               TreeSet tabNames = new TreeSet();

               for (PlayerListEntry playerEntry : minecraftClient.getNetworkHandler().getPlayerList()) {
                  tabNames.add(playerEntry.getProfile().name().toLowerCase(Locale.ROOT));
               }

               tabNames.remove(nick);
               nearbyNames.addAll(tabNames);
               JsonArray nearbyJson = new JsonArray();

               for (String nearbyName : nearbyNames) {
                  if (nearbyJson.size() >= 300) {
                     break;
                  }

                  nearbyJson.add(nearbyName);
               }

               json.add(("nearby"), nearbyJson);
               String payload = json.toString();
               if (System.currentTimeMillis() - this.lastRequestMillis >= (payload.equals(this.lastPayload) ? (3000L) : (1000L))) {
                  if (this.requestInFlight.compareAndSet(false, true)) {
                     this.lastRequestMillis = System.currentTimeMillis();
                     this.lastPayload = payload;
                     String requestSessionId = this.sessionId;
                     this.syncExecutor.execute(() -> {
                        try {
                           JsonObject playersJson = JsonParser.parseString(this.postSyncRequest(payload)).getAsJsonObject().getAsJsonObject(("players"));
                           HashMap selectionsByNick = new HashMap();

                           for (Entry playerSelection : playersJson.entrySet()) {
                              java.util.EnumMap<com.mouseclient.util.time.CosmeticCategory,java.lang.Object> categorySelections = new EnumMap<>(CosmeticCategory.class);

                              for (CosmeticCategory responseCategory : CosmeticCategory.values()) {
                                 JsonElement jsonElement = ((JsonElement)playerSelection.getValue()).getAsJsonObject().get(responseCategory.name().toLowerCase(Locale.ROOT));
                                 if (jsonElement != null && jsonElement.isJsonPrimitive()) {
                                    categorySelections.put(responseCategory, jsonElement.getAsInt());
                                 }
                              }

                              selectionsByNick.put((String)playerSelection.getKey(), categorySelections);
                           }

                           this.syncSnapshot = new Globals.CosmeticSyncSnapshot(requestSessionId, selectionsByNick, System.currentTimeMillis());
                        } catch (Exception error) {
                        } finally {
                           this.requestInFlight.set(false);
                        }
                     });
                  }
               }
            }
         }
      };
      this.syncModels = this.addSetting(new BooleanSetting(("Синхронизация моделей"), ("Игроки с включённым Globals видят твою косметику, а ты их."), true));
      this.syncTargets = this.addSetting(new ModeSetting(("С кем"), ("Только друзья - обмен косметикой только с друзьями из .friend, Все с Globals - с любым игроком, у кого включён Globals."), ("Только друзья"), ("Только друзья"), ("Все с Globals")));
      this.syncTargets.setVisibility(() -> this.syncModels.getValue());
      this.cosmeticsMenuBind = this.addSetting(new BindSetting(("Меню косметики"), ("Открывает меню косметики прямо в игре.")));
      ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
         Future leaveRequest = this.leaveSession();
         if (leaveRequest != null) {
            try {
               leaveRequest.get((1500L), TimeUnit.MILLISECONDS);
            } catch (Exception shutdownFailure) {
            }
         }
      });
   }

   public static Globals getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Globals();
      }

      return INSTANCE;
   }

   private String postSyncRequest(String payload) throws IOException, InterruptedException {
      HttpResponse response = this.httpClient
         .send(
            HttpRequest.newBuilder(URI.create(("http://deluxegaymarket.fun/cosmetics/sync.php")))
               .timeout(Duration.ofSeconds((6L)))
               .header(("Content-Type"), ("application/json"))
               .header(("X-Deluxe-Key"), ("dlx_7Qm2vX9pK4tR8wZ1nB6s"))
               .POST(BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
               .build(),
            BodyHandlers.ofString(StandardCharsets.UTF_8)
         );
      if (response.statusCode() != 200) {
         throw new IOException("sync HTTP " + response.statusCode());
      } else {
         return (String)response.body();
      }
   }

   private record CosmeticSyncSnapshot(String sessionId, HashMap<String, EnumMap<CosmeticCategory, Integer>> selectionsByNick, long receivedAtMs) {
   }
}
