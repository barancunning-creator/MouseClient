package com.mouseclient.util.time;

import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.MinecraftSessionAccessor;
import com.mouseclient.modules.impl.misc.AutoRegister;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Formatting;

public final class AltAccountManager {
   private static final AltAccountManager INSTANCE = new AltAccountManager();
   private ArrayList<AltAccount> accounts = new ArrayList<>();
   private AltAccount activeAccount;
   private long activeSinceMillis;
   private Path storagePath;
   private final EventListener<PacketEvent> packetListener = event -> {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         if (event.isOutgoing() && event.getPacket() instanceof CommandExecutionC2SPacket commandPacket) {
            String[] commandTokens = commandPacket.command().trim().split(("\\s+"));
            if (commandTokens.length >= 2
               && (
                  commandTokens[0].equalsIgnoreCase(("reg"))
                     || commandTokens[0].equalsIgnoreCase(("register"))
                     || commandTokens[0].equalsIgnoreCase(("l"))
                     || commandTokens[0].equalsIgnoreCase(("login"))
               )) {
               client.execute(() -> AutoRegister.getInstance().password.setValue(commandTokens[1]));
            }
         }

         if (this.activeAccount != null) {
            AltAccount recordedAccount = this.activeAccount;
            if (event.isOutgoing() && event.getPacket() instanceof ChatMessageC2SPacket chatPacket) {
               client.execute(() -> recordedAccount.recordChat(chatPacket.chatMessage()));
            } else if (event.isIncoming() && event.getPacket() instanceof GameJoinS2CPacket) {
               client.execute(() -> {
                  if (client.getCurrentServerEntry() != null) {
                     recordedAccount.setLastServer(serverDisplayName(client.getCurrentServerEntry().address));
                     this.save();
                  }
               });
            }
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && this.activeAccount != null && client.player != null && client.world != null) {
         Team team = client.world.getScoreboard().getScoreHolderTeam(client.player.getNameForScoreboard());
         String serverRank = team != null && team.getPrefix() != null ? Formatting.strip(team.getPrefix().getString()).replaceAll(("[^\\p{L}\\p{N}]"), "") : "";
         if (!serverRank.isEmpty() && !serverRank.equals(this.activeAccount.serverRank())) {
            this.activeAccount.setServerRank(serverRank);
            this.save();
         }
      }
   };

   private static AltAccount parseAccountLine(String line) {
      if (line != null && !line.isBlank()) {
         String[] columns = line.split(("\t"), -1);
         if (columns.length < 9) {
            return null;
         }

         try {
            return new AltAccount(
               columns[0],
               UUID.fromString(columns[1]),
               ("MICROSOFT").equals(columns[2]) ? AltAccount.AccountType.MICROSOFT : AltAccount.AccountType.CRACKED,
               columns[3],
               columns[4],
               Long.parseLong(columns[5]),
               Long.parseLong(columns[6]),
               Boolean.parseBoolean(columns[7])
            );
         } catch (RuntimeException error) {
            return null;
         }
      } else {
         return null;
      }
   }

   public void commitActivePlaytime() {
      if (this.activeAccount != null && this.activeSinceMillis > 0L) {
         this.activeAccount.addPlaytime(System.currentTimeMillis() - this.activeSinceMillis);
      }

      this.activeSinceMillis = System.currentTimeMillis();
   }

   private static long dateMillis(int year, int month, int day) {
      return LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
   }

   public long playtimeMillis(AltAccount account) {
      return account.playtimeMillis() + (account == this.activeAccount && this.activeSinceMillis > 0L ? Math.max(0L, System.currentTimeMillis() - this.activeSinceMillis) : 0L);
   }

   public void renameAccount(AltAccount account, String username) {
      String trimmedName = username == null ? "" : username.trim();
      if (account != null && !trimmedName.isEmpty() && !trimmedName.equals(account.username())) {
         for (AltAccount existingAccount : this.accounts) {
            if (existingAccount != account && existingAccount.username().equalsIgnoreCase(trimmedName)) {
               return;
            }
         }

         account.setUsername(trimmedName);
         if (account.type() == AltAccount.AccountType.CRACKED) {
            account.setUuid(AltAccount.offlineUuid(trimmedName));
         }

         if (account == this.activeAccount) {
            this.switchSession(account);
         }

         this.save();
      }
   }

   private void findCurrentSessionAccount() {
      MinecraftClient client = MinecraftClient.getInstance();
      Session session = client != null ? client.getSession() : null;
      if (session != null) {
         for (AltAccount account : this.accounts) {
            if (account.username().equalsIgnoreCase(session.getUsername())) {
               this.activeAccount = account;
               this.activeSinceMillis = System.currentTimeMillis();
               return;
            }
         }
      }
   }

   public void toggleFavorite(AltAccount account) {
      account.setFavorite(!account.isFavorite());
      this.save();
   }

   public void removeAccount(AltAccount account) {
      if (account == this.activeAccount) {
         this.commitActivePlaytime();
         this.activeAccount = null;
      }

      this.accounts.remove(account);
      this.save();
   }

   private static long durationMillis(long days, long hours, long minutes) {
      return ((days * (24L) + hours) * (60L) + minutes) * (60000L);
   }

   public AltAccount addOfflineAccount(String username) {
      String trimmedName = username.trim();
      if (trimmedName.isEmpty()) {
         return null;
      }

      for (AltAccount existingAccount : this.accounts) {
         if (existingAccount.username().equalsIgnoreCase(trimmedName)) {
            return existingAccount;
         }
      }

      AltAccount newAccount = AltAccount.offline(trimmedName);
      this.accounts.add(newAccount);
      this.save();
      return newAccount;
   }

   public ArrayList<AltAccount> accounts() {
      return this.accounts;
   }

   private void initializeDefaults() {
   }

   public AltAccount activeAccount() {
      return this.activeAccount;
   }

   private void load() {
      this.accounts.clear();

      try {
         if (Files.exists(this.storagePath)) {
            for (String line : Files.readAllLines(this.storagePath, StandardCharsets.UTF_8)) {
               AltAccount account = parseAccountLine(line);
               if (account != null) {
                  this.accounts.add(account);
               }
            }
         }
      } catch (IOException error) {
      }

      if (this.accounts.isEmpty()) {
         this.initializeDefaults();
         this.save();
      }
   }

   public boolean isActive(AltAccount account) {
      return account != null && account == this.activeAccount;
   }

   private static String sanitizeField(String value) {
      return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
   }

   private void addOfflineAccountRecord(String username, String serverRank, String lastServer, long createdAtMillis, long playtimeMillis, boolean favorite) {
      this.accounts.add(new AltAccount(username, AltAccount.offlineUuid(username), AltAccount.AccountType.CRACKED, lastServer, serverRank, createdAtMillis, playtimeMillis, favorite));
   }

   public static AltAccountManager getInstance() {
      return INSTANCE;
   }

   private AltAccountManager() {
      this.storagePath = FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("altmanager.txt"));
      this.load();
      this.findCurrentSessionAccount();
   }

   public static String serverDisplayName(String address) {
      if (address != null && !address.isBlank()) {
         String host = address.trim().toLowerCase(Locale.ROOT).split((":"), 2)[0];
         String[] hostParts = host.split(("\\."));
         String serverName = hostParts.length >= 2 ? hostParts[hostParts.length - 2] : hostParts[0];
         return serverName.isEmpty() ? ("—") : Character.toUpperCase(serverName.charAt(0)) + serverName.substring(1);
      } else {
         return ("—");
      }
   }

   public ArrayList<AltAccount> filteredAccounts(String query) {
      String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
      ArrayList<AltAccount> matches = new ArrayList<>();

      for (AltAccount account : this.accounts) {
         if (normalizedQuery.isEmpty() || account.username().toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            matches.add(account);
         }
      }

      matches.sort((left, right) -> left.isFavorite() != right.isFavorite() ? (left.isFavorite() ? -1 : 1) : Long.compare(right.createdAtMillis(), left.createdAtMillis()));
      return matches;
   }

   public void switchSession(AltAccount account) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && account != null) {
         ((MinecraftSessionAccessor)(Object)client).setUser(new Session(account.username(), account.uuid(), "", Optional.empty(), Optional.empty()));
         this.commitActivePlaytime();
         this.activeAccount = account;
         this.activeSinceMillis = System.currentTimeMillis();
         this.save();
      }
   }

   public void save() {
      try {
         Files.createDirectories(this.storagePath.getParent());
         StringBuilder textBuilder = new StringBuilder();

         for (AltAccount account : this.accounts) {
            textBuilder.append(sanitizeField(account.username()))
               .append('\t')
               .append(account.uuid())
               .append('\t')
               .append(account.type())
               .append('\t')
               .append(sanitizeField(account.lastServer()))
               .append('\t')
               .append(sanitizeField(account.serverRank()))
               .append('\t')
               .append(account.createdAtMillis())
               .append('\t')
               .append(account == this.activeAccount ? this.playtimeMillis(account) : account.playtimeMillis())
               .append('\t')
               .append(account.isFavorite())
               .append('\t')
               .append('1')
               .append('\n');
         }

         Files.writeString(this.storagePath, textBuilder.toString(), StandardCharsets.UTF_8);
      } catch (IOException error) {
      }
   }

   public int countByType(AltAccount.AccountType type) {
      int count = 0;

      for (AltAccount account : this.accounts) {
         if (account.type() == type) {
            count++;
         }
      }

      return count;
   }
}
