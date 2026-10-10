package com.mouseclient.network;

import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.util.entity.MinecraftAccess;
import java.util.Locale;
import net.minecraft.network.packet.BrandCustomPayload;
import net.minecraft.network.packet.c2s.common.ClientOptionsC2SPacket;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.util.Identifier;

public final class ClientIdentityController implements MinecraftAccess {
   private static ClientIdentityController INSTANCE;
   private final ClientIdentityGenerator identityGenerator = new ClientIdentityGenerator();
   private ClientIdentity identity;
   private boolean clearChatAfterJoin;
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isIncoming()) {
         if (event.getPacket() instanceof GameJoinS2CPacket) {
            this.clearChatAfterJoin = true;
         }
      } else if (this.identity != null) {
         if (event.getPacket() instanceof CustomPayloadC2SPacket payloadPacket) {
            if (payloadPacket.payload() instanceof BrandCustomPayload) {
               event.setPacket(new CustomPayloadC2SPacket(new BrandCustomPayload(this.identity.brandTemplate().formatBrand(this.identity.version()))));
            } else {
               Identifier id = payloadPacket.payload().getId().id();
               if (!id.getNamespace().equals(("minecraft")) || id.getPath().equals(("register")) || id.getPath().equals(("unregister"))) {
                  event.markCancelled();
               }
            }
         } else {
            if (event.getPacket() instanceof ClientOptionsC2SPacket optionsPacket) {
               SyncedClientOptions originalOptions = optionsPacket.options();
               SyncedClientOptions identityOptions = this.identity.options();
               event.setPacket(
                  new ClientOptionsC2SPacket(
                     new SyncedClientOptions(
                        identityOptions.language(),
                        Math.max(originalOptions.viewDistance(), identityOptions.viewDistance()),
                        originalOptions.chatVisibility(),
                        originalOptions.chatColorsEnabled(),
                        identityOptions.playerModelParts(),
                        identityOptions.mainArm(),
                        identityOptions.filtersText(),
                        identityOptions.allowsServerListing(),
                        identityOptions.particleStatus()
                     )
                  )
               );
            }
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (this.clearChatAfterJoin && this.identity != null && minecraftClient.player != null && minecraftClient.world != null) {
         this.clearChatAfterJoin = false;
         minecraftClient.inGameHud.getChatHud().clear(true);
      }
   };

   public static ClientIdentityController getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ClientIdentityController();
      }

      return INSTANCE;
   }

   public void onConnect(String serverAddress) {
      this.clearChatAfterJoin = false;
      if (serverAddress != null && serverAddress.toLowerCase(Locale.ROOT).contains(("holyworld"))) {
         this.identity = this.identityGenerator.generate();
         EventBus.getInstance().subscribe(this);
      } else {
         this.identity = null;
         EventBus.getInstance().unsubscribe(this);
      }
   }
}
