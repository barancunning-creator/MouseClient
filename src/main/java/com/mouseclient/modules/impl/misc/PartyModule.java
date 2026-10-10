package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.GuiShapeRenderer;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.network.HudStyle;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.entity.WorldProjection;
import com.mouseclient.util.time.ToastNotifications;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(
   name = "Party",
   description = "Пати с друзьями: видно участников на любом расстоянии и общие метки. Управление через .party",
   category = ModuleCategory.MISC
)
public final class PartyModule extends Module {
   private static PartyModule INSTANCE;
   public BindSetting label;
   public BindSetting playerLabel;
   private final HttpClient httpClient;
   private final ExecutorService partyExecutor;
   private final String sessionId;
   private final AtomicBoolean heartbeatInFlight;
   private final HashMap<String, Vec3d> smoothedMemberPositions;
   private final HashSet<String> seenMarkerIds;
   private volatile PartyModule.PartySnapshot snapshot;
   private String partyCode;
   private long lastHeartbeatMillis;
   private long lastRenderMillis;
   private boolean pointKeyWasPressed;
   private boolean playerKeyWasPressed;
   private final EventListener<PlayerTickEvent> playerTickListener;
   private final EventListener<HudRenderEvent> hudRenderListener;

   private void sendAction(JsonObject request, Consumer<JsonObject> onSuccess) {
      this.partyExecutor.execute(() -> {
         try {
            JsonObject response = this.postRequest(request);
            minecraftClient.execute(() -> {
               if (response.get(("ok")).getAsBoolean()) {
                  onSuccess.accept(response);
               } else {
                  ChatMessages.printPrefixed("&cПати: " + this.errorMessage(response.get(("error")).getAsString()));
               }
            });
         } catch (Exception error) {
            minecraftClient.execute(() -> ChatMessages.printPrefixed(("&cПати: сервер недоступен")));
         }
      });
   }

   private String serverHost() {
      IntegratedServer integratedServer = minecraftClient.getServer();
      if (integratedServer != null) {
         return "lan:" + (integratedServer.isRemote() ? integratedServer.getServerPort() : 0);
      } else if (minecraftClient.getNetworkHandler() == null
         || !(
            minecraftClient.getNetworkHandler().getConnection().getAddress() instanceof InetSocketAddress remoteAddress
               && remoteAddress.getAddress() != null
               && (remoteAddress.getAddress().isLoopbackAddress() || remoteAddress.getAddress().isSiteLocalAddress())
         )) {
         ServerInfo serverInfo = minecraftClient.getCurrentServerEntry();
         return serverInfo == null ? "" : serverInfo.address.trim().toLowerCase(Locale.ROOT).replaceFirst((":25565$"), "").replaceFirst(("\\.$"), "");
      } else {
         return "lan:" + remoteAddress.getPort();
      }
   }

   public void kickMember(String nick) {
      if (this.ensureJoined()) {
         JsonObject json = this.partyRequest(("kick"));
         json.addProperty(("target"), nick);
         this.sendAction(json, responseJson -> {
            this.applySnapshot(responseJson);
            ChatMessages.printPrefixed("&7" + nick + "&r исключён из пати.");
         });
      }
   }

   private void applyHeartbeatResponse(JsonObject response) {
      if (this.partyCode != null) {
         if (response.get(("ok")).getAsBoolean()) {
            this.applySnapshot(response);
         } else {
            String errorCode = response.get(("error")).getAsString();
            if (errorCode.equals(("not_member")) || errorCode.equals(("not_found"))) {
               String previousCode = this.partyCode;
               this.clearParty();
               ChatMessages.printPrefixed(errorCode.equals(("not_found")) ? "Пати &7" + previousCode + "&r распущено." : "Тебя больше нет в пати &7" + previousCode + "&r.");
            }
         }
      }
   }

   private void clearParty() {
      this.partyCode = null;
      this.snapshot = null;
      this.smoothedMemberPositions.clear();
      this.seenMarkerIds.clear();
   }

   private JsonObject partyRequest(String action) {
      JsonObject json = this.baseRequest(action);
      json.addProperty(("code"), this.partyCode);
      json.addProperty(("host"), this.serverHost());
      json.addProperty(("dim"), this.dimensionPath());
      return json;
   }

   public void leave() {
      if (this.partyCode == null) {
         ChatMessages.printPrefixed(("Ты не в пати."));
      } else {
         JsonObject json = this.partyRequest(("leave"));
         this.clearParty();
         this.sendAction(json, responseJson -> ChatMessages.printPrefixed(("Ты вышел из пати.")));
      }
   }

   public void join(String code) {
      if (minecraftClient.player != null) {
         if (!this.isEnabled()) {
            this.setEnabled(true);
         }

         if (this.partyCode != null) {
            this.leave();
         }

         JsonObject json = this.baseRequest(("join"));
         json.addProperty(("code"), code.toUpperCase(Locale.ROOT));
         this.sendAction(json, responseJson -> {
            this.applySnapshot(responseJson);
            ChatMessages.printPrefixed("Ты в пати &a" + this.partyCode + "&r, владелец: &7" + responseJson.get(("owner")).getAsString());
         });
      }
   }

   private void markLookedAtBlock() {
      if (this.ensureJoined()) {
         HitResult hitResult = minecraftClient.player.raycast((256.0D), 1.0F, false);
         if (hitResult instanceof BlockHitResult blockHit && hitResult.getType() == Type.BLOCK) {
            Vec3d position = blockHit.getBlockPos().toCenterPos().add(0.0, (0.5D), 0.0);
            this.sendMarker(("point"), "", position);
         } else {
            ToastNotifications.showIconToast(this.category.iconGlyph(), ("Метка"), ("Слишком далеко"), true);
         }
      }
   }

   private void drawMarker(HudRenderEvent event, Vec3d position, String label, String detail, int color, float scale) {
      Vec3d screenPosition = WorldProjection.projectNearViewport(position, event.getWidth(), event.getHeight());
      if (screenPosition != null) {
         float scaleRatio = GuiScale.getWindowScale() / GuiScale.getBaseScale();
         float screenX = (float)screenPosition.x * scaleRatio;
         float screenY = (float)screenPosition.y * scaleRatio;
         float distance = (float)minecraftClient.player.getEntityPos().distanceTo(position);
         String distanceText = String.format(Locale.US, ("%.0fм"), distance);
         float labelWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, label, (6.0F));
         float detailWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, detail, (6.0F));
         float distanceWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, distanceText, (6.0F));
         float width = (4.0F) + labelWidth + (3.0F) + detailWidth + (3.0F) + distanceWidth + (4.0F);
         float panelX = Math.round((screenX - width / 2.0F) * 2.0F) / 2.0F;
         float panelY = Math.round((screenY - (6.5F)) * 2.0F) / 2.0F;
         HudRenderContext.pushScale(screenX, screenY, scale > 0.0F ? scale : (float)Math.max((0.6D), (1.1D) - distance / (200.0D)));
         HudPanelRenderer.drawGlassPanel(panelX, panelY, width, (13.0F), 1.0F, (4.5F), GuiShapeRenderer.BlurQuality.LOW);
         float textY = panelY + (6.5F) - (3.5F);
         GuiTextRenderer.draw(GuiFont.SEMI_BOLD, label, panelX + (4.0F), textY, (6.0F), color);
         GuiTextRenderer.draw(GuiFont.SEMI_BOLD, detail, panelX + (4.0F) + labelWidth + (3.0F), textY, (6.0F), HudPanelRenderer.secondaryTextColor(1.0F));
         GuiTextRenderer.draw(
            GuiFont.SEMI_BOLD, distanceText, panelX + (4.0F) + labelWidth + (3.0F) + detailWidth + (3.0F), textY, (6.0F), HudPanelRenderer.secondaryTextColor(1.0F)
         );
         HudRenderContext.popTransform();
      }
   }

   public void setLocked(boolean locked) {
      if (this.ensureJoined()) {
         JsonObject json = this.partyRequest(("lock"));
         json.addProperty(("locked"), locked);
         this.sendAction(json, responseJson -> {
            this.applySnapshot(responseJson);
            ChatMessages.printPrefixed(locked ? ("Пати &cзакрыто&r, новые игроки не зайдут.") : ("Пати &aоткрыто&r для входа."));
         });
      }
   }

   private void sendMarker(String type, String targetNick, Vec3d position) {
      JsonObject json = this.partyRequest(("mark"));
      json.addProperty(("type"), type);
      json.addProperty(("target"), targetNick);
      json.addProperty(("x"), position.x);
      json.addProperty(("y"), position.y);
      json.addProperty(("z"), position.z);
      this.sendAction(json, responseJson -> {
         this.applySnapshot(responseJson);
         ToastNotifications.showIconToast(this.category.iconGlyph(), ("Метка"), type.equals(("player")) ? "Отмечен " + targetNick : ("Метка поставлена"), true);
      });
   }

   private void applySnapshot(JsonObject response) {
      PartyModule.PartySnapshot previousSnapshot = this.snapshot;
      ArrayList<PartyModule.PartyMember> members = new ArrayList();

      for (JsonElement jsonElement : response.getAsJsonArray(("members"))) {
         JsonObject memberJson = jsonElement.getAsJsonObject();
         members.add(
            new PartyModule.PartyMember(
               memberJson.get(("nick")).getAsString(),
               memberJson.get(("host")).getAsString(),
               memberJson.get(("dim")).getAsString(),
               new Vec3d(memberJson.get(("x")).getAsDouble(), memberJson.get(("y")).getAsDouble(), memberJson.get(("z")).getAsDouble()),
               memberJson.get(("hp")).getAsFloat()
            )
         );
      }

      ArrayList<PartyModule.PartyMarker> markers = new ArrayList();

      for (JsonElement markerElement : response.getAsJsonArray(("marks"))) {
         JsonObject markerJson = markerElement.getAsJsonObject();
         markers.add(
            new PartyModule.PartyMarker(
               markerJson.get(("id")).getAsString(),
               markerJson.get(("by")).getAsString(),
               markerJson.get(("type")).getAsString(),
               markerJson.get(("target")).getAsString(),
               markerJson.get(("host")).getAsString(),
               markerJson.get(("dim")).getAsString(),
               new Vec3d(markerJson.get(("x")).getAsDouble(), markerJson.get(("y")).getAsDouble(), markerJson.get(("z")).getAsDouble())
            )
         );
      }

      PartyModule.PartySnapshot newSnapshot = new PartyModule.PartySnapshot(
         response.get(("code")).getAsString(), response.get(("owner")).getAsString(), response.get(("locked")).getAsBoolean(), members, markers
      );
      this.partyCode = newSnapshot.code();
      this.snapshot = newSnapshot;
      if (previousSnapshot != null) {
         String localNick = minecraftClient.player == null ? "" : minecraftClient.player.getGameProfile().name();

         for (PartyModule.PartyMember joinedMember : members) {
            if (previousSnapshot.members().stream().noneMatch(partyMember -> partyMember.nick().equals(joinedMember.nick()))) {
               ChatMessages.printPrefixed("&a+ &7" + joinedMember.nick() + "&r зашёл в пати.");
            }
         }

         for (PartyModule.PartyMember leftMember : previousSnapshot.members()) {
            if (members.stream().noneMatch(partyMember -> partyMember.nick().equals(leftMember.nick()))) {
               ChatMessages.printPrefixed("&c- &7" + leftMember.nick() + "&r вышел из пати.");
            }
         }

         for (PartyModule.PartyMarker newMarker : markers) {
            if (this.seenMarkerIds.add(newMarker.id()) && !newMarker.createdBy().equals(localNick)) {
               ToastNotifications.showIconToast(
                  this.category.iconGlyph(), newMarker.createdBy(), newMarker.type().equals(("player")) ? "отметил " + newMarker.target() : ("поставил метку"), true
               );
            }
         }

         this.seenMarkerIds.removeIf(markerId -> markers.stream().noneMatch(partyMarker -> partyMarker.id().equals(markerId)));
      } else {
         for (PartyModule.PartyMarker initialMarker : markers) {
            this.seenMarkerIds.add(initialMarker.id());
         }
      }
   }

   public void printParty() {
      PartyModule.PartySnapshot currentSnapshot = this.snapshot;
      if (currentSnapshot == null) {
         ChatMessages.printPrefixed(("Ты не в пати. &7.party create&r или &7.party join <код>"));
      } else {
         StringBuilder textBuilder = new StringBuilder();

         for (PartyModule.PartyMember member : currentSnapshot.members()) {
            textBuilder.append(textBuilder.isEmpty() ? "" : ("&r, ")).append(member.nick().equals(currentSnapshot.owner()) ? ("&e") : ("&7")).append(member.nick());
         }

         ChatMessages.printPrefixed("Пати &a" + currentSnapshot.code() + "&r " + (currentSnapshot.locked() ? ("&c(закрыто)") : ("&a(открыто)")) + "&r: " + textBuilder);
      }
   }

   private String errorMessage(String errorCode) {
      return switch (errorCode) {
         case ("not_found") -> ("пати с таким кодом нет");
         case ("locked") -> ("пати закрыто");
         case ("full") -> ("пати заполнено");
         case ("not_owner") -> ("это может только создатель пати");
         case ("not_member") -> ("ты не в этом пати");
         default -> errorCode;
      };
   }

   private AbstractClientPlayerEntity findPlayer(String nick) {
      for (AbstractClientPlayerEntity player : minecraftClient.world.getPlayers()) {
         if (player.getGameProfile().name().equalsIgnoreCase(nick)) {
            return player;
         }
      }

      return null;
   }

   public static PartyModule getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new PartyModule();
      }

      return INSTANCE;
   }

   private JsonObject postRequest(JsonObject request) throws Exception {
      HttpResponse response = this.httpClient
         .send(
            HttpRequest.newBuilder(URI.create(("http://deluxegaymarket.fun/party/party.php")))
               .timeout(Duration.ofSeconds((6L)))
               .header(("Content-Type"), ("application/json"))
               .header(("X-Deluxe-Key"), ("dlx_7Qm2vX9pK4tR8wZ1nB6s"))
               .POST(BodyPublishers.ofString(request.toString(), StandardCharsets.UTF_8))
               .build(),
            BodyHandlers.ofString(StandardCharsets.UTF_8)
         );
      return JsonParser.parseString((String)response.body()).getAsJsonObject();
   }

   private JsonObject baseRequest(String action) {
      JsonObject json = new JsonObject();
      json.addProperty(("action"), action);
      json.addProperty(("nick"), minecraftClient.player == null ? "" : minecraftClient.player.getGameProfile().name());
      json.addProperty(("session"), this.sessionId);
      return json;
   }

   private boolean ensureJoined() {
      if (this.partyCode != null) {
         return true;
      }

      ChatMessages.printPrefixed(("Ты не в пати. &7.party create&r или &7.party join <код>"));
      return false;
   }

   private String dimensionPath() {
      return minecraftClient.world == null ? "" : minecraftClient.world.getRegistryKey().getValue().getPath();
   }

   private void markLookedAtPlayer() {
      if (this.ensureJoined()) {
         AbstractClientPlayerEntity player = null;
         double smallestAngle = (4.0D);
         Vec3d position = minecraftClient.player.getEyePos();
         Vec3d viewDirection = minecraftClient.player.getRotationVec(1.0F);

         for (AbstractClientPlayerEntity candidate : minecraftClient.world.getPlayers()) {
            if (candidate != minecraftClient.player) {
               Vec3d relativePosition = candidate.getBoundingBox().getCenter().subtract(position);
               double angle = Math.toDegrees(Math.acos(Math.max((-1.0D), Math.min(1.0, relativePosition.normalize().dotProduct(viewDirection)))));
               if (angle < smallestAngle) {
                  smallestAngle = angle;
                  player = candidate;
               }
            }
         }

         if (player == null) {
            ToastNotifications.showIconToast(this.category.iconGlyph(), ("Метка"), ("Наведись на игрока"), true);
         } else {
            this.sendMarker(("player"), player.getGameProfile().name(), player.getEntityPos());
         }
      }
   }

   public void create() {
      if (minecraftClient.player != null) {
         if (!this.isEnabled()) {
            this.setEnabled(true);
         }

         if (this.partyCode != null) {
            this.leave();
         }

         JsonObject json = this.baseRequest(("create"));
         this.sendAction(json, responseJson -> {
            this.applySnapshot(responseJson);
            ChatMessages.printPrefixed("Пати создано, код: &a" + this.partyCode + "&r. Друзья заходят через &7.party join " + this.partyCode);
         });
      }
   }

   public boolean isMember(String nick) {
      PartyModule.PartySnapshot currentSnapshot = this.snapshot;
      if (currentSnapshot != null && nick != null) {
         for (PartyModule.PartyMember member : currentSnapshot.members()) {
            if (member.nick().equalsIgnoreCase(nick)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private PartyModule() {
      this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds((5L))).build();
      this.partyExecutor = Executors.newSingleThreadExecutor(task -> {
         Thread partyThread = new Thread(task, ("mouseclient-party"));
         partyThread.setDaemon(true);
         return partyThread;
      });
      this.sessionId = UUID.randomUUID().toString();
      this.heartbeatInFlight = new AtomicBoolean();
      this.smoothedMemberPositions = new HashMap<>();
      this.seenMarkerIds = new HashSet<>();
      this.playerTickListener = event -> {
         if (minecraftClient.player != null && minecraftClient.world != null) {
            boolean pointKeyWasPressed = this.label.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            if (pointKeyWasPressed && !this.pointKeyWasPressed && minecraftClient.currentScreen == null) {
               this.markLookedAtBlock();
            }

            this.pointKeyWasPressed = pointKeyWasPressed;
            boolean playerKeyWasPressed = this.playerLabel.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            if (playerKeyWasPressed && !this.playerKeyWasPressed && minecraftClient.currentScreen == null) {
               this.markLookedAtPlayer();
            }

            this.playerKeyWasPressed = playerKeyWasPressed;
            if (this.partyCode != null && System.currentTimeMillis() - this.lastHeartbeatMillis >= (500L) && this.heartbeatInFlight.compareAndSet(false, true)) {
               this.lastHeartbeatMillis = System.currentTimeMillis();
               JsonObject json = this.partyRequest(("beat"));
               json.addProperty(("x"), minecraftClient.player.getX());
               json.addProperty(("y"), minecraftClient.player.getY());
               json.addProperty(("z"), minecraftClient.player.getZ());
               json.addProperty(("hp"), minecraftClient.player.getHealth() + minecraftClient.player.getAbsorptionAmount());
               this.partyExecutor.execute(() -> {
                  try {
                     JsonObject heartbeatResponse = this.postRequest(json);
                     minecraftClient.execute(() -> this.applyHeartbeatResponse(heartbeatResponse));
                  } catch (Exception error) {
                  } finally {
                     this.heartbeatInFlight.set(false);
                  }
               });
            }
         }
      };
      this.hudRenderListener = event -> {
         PartyModule.PartySnapshot currentSnapshot = this.snapshot;
         if (currentSnapshot != null && minecraftClient.player != null && minecraftClient.world != null) {
            long lastRenderMillis = System.currentTimeMillis();
            float interpolation = Math.min(1.0F, (float)(lastRenderMillis - this.lastRenderMillis) / (1000.0F) * (8.0F));
            this.lastRenderMillis = lastRenderMillis;
            HudRenderContext.setDrawContext(event.getDrawContext());
            String currentHost = this.serverHost();
            String dimension = this.dimensionPath();
            String localNick = minecraftClient.player.getGameProfile().name();

            for (PartyModule.PartyMember member : currentSnapshot.members()) {
               if (!member.nick().equals(localNick) && member.host().equals(currentHost) && member.dim().equals(dimension)) {
                  AbstractClientPlayerEntity player = this.findPlayer(member.nick());
                  Vec3d position = player != null ? player.getLerpedPos(event.getTickProgress()) : member.position();
                  Vec3d displayPosition = this.smoothedMemberPositions.get(member.nick());
                  displayPosition = displayPosition != null && player == null ? displayPosition.lerp(position, interpolation) : position;
                  this.smoothedMemberPositions.put(member.nick(), displayPosition);
                  String healthText = (int)Math.ceil(player != null ? player.getHealth() + player.getAbsorptionAmount() : member.health()) + "hp";
                  this.drawMarker(event, displayPosition.add(0.0, (2.3D), 0.0), member.nick(), healthText, HudPanelRenderer.primaryTextColor(1.0F), (-1.0F));
               }
            }

            this.smoothedMemberPositions.keySet().removeIf(cachedNick -> currentSnapshot.members().stream().noneMatch(cachedMember -> cachedMember.nick().equals(cachedNick)));

            for (PartyModule.PartyMarker marker : currentSnapshot.marks()) {
               if (marker.host().equals(currentHost) && marker.dim().equals(dimension)) {
                  if (marker.type().equals(("player"))) {
                     AbstractClientPlayerEntity markedPlayer = this.findPlayer(marker.target());
                     Vec3d markedPosition = markedPlayer != null ? markedPlayer.getLerpedPos(event.getTickProgress()) : marker.position();
                     float markedDistance = (float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(markedPosition);
                     this.drawMarker(
                        event,
                        markedPosition.add(0.0, (2.9D), 0.0),
                        "Цель " + marker.target(),
                        marker.createdBy(),
                        (-43691),
                        Math.max((0.3F), 1.0F - markedDistance * (0.008F))
                     );
                  } else {
                     this.drawMarker(event, marker.position(), ("Метка"), marker.createdBy(), HudStyle.accentColor, (-1.0F));
                  }
               }
            }
         }
      };
      this.label = this.addSetting(new BindSetting(("Метка"), ("Ставит метку туда, куда смотришь. Видят все в пати.")));
      this.playerLabel = this.addSetting(new BindSetting(("Метка на игрока"), ("Отмечает игрока, на которого смотришь. Видят все в пати.")));
   }

   @Override
   protected void onDisable() {
      if (this.partyCode != null) {
         this.leave();
      }
   }

   private record PartyMember(String nick, String host, String dim, Vec3d position, float health) {
   }

   private record PartyMarker(String id, String createdBy, String type, String target, String host, String dim, Vec3d position) {
      public String createdBy() {
         return this.createdBy;
      }
   }

   private record PartySnapshot(
      String code, String owner, boolean locked, ArrayList<PartyModule.PartyMember> members, ArrayList<PartyModule.PartyMarker> marks
   ) {
   }
}
