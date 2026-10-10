package com.mouseclient.util.time;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public final class AltAccount {
   private static final DateTimeFormatter CREATED_DATE_FORMAT = DateTimeFormatter.ofPattern(("MMMM d, yyyy"), Locale.ENGLISH);
   private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern(("HH:mm:ss"));
   private String username;
   private UUID uuid;
   private AltAccount.AccountType type;
   private String lastServer;
   private String serverRank;
   private long createdAtMillis;
   private long playtimeMillis;
   private boolean favorite;
   private ArrayList<String[]> chatHistory = new ArrayList<>();

   public static String formatPlaytime(long millis) {
      long totalMinutes = millis / (60000L);
      long days = totalMinutes / (1440L);
      long hours = totalMinutes / (60L) % (24L);
      long minutes = totalMinutes % (60L);
      StringBuilder textBuilder = new StringBuilder();
      if (days > 0L) {
         textBuilder.append(days).append(("d "));
      }

      if (days > 0L || hours > 0L) {
         textBuilder.append(hours).append(("h "));
      }

      textBuilder.append(minutes).append('m');
      return textBuilder.toString();
   }

   public void addPlaytime(long millis) {
      this.playtimeMillis = this.playtimeMillis + Math.max(0L, millis);
   }

   public static AltAccount offline(String username) {
      return new AltAccount(username, offlineUuid(username), AltAccount.AccountType.CRACKED, ("—"), ("Player"), System.currentTimeMillis(), 0L, false);
   }

   public ArrayList<String[]> chatHistory() {
      return this.chatHistory;
   }

   public String lastServer() {
      return this.lastServer;
   }

   public AltAccount.AccountType type() {
      return this.type;
   }

   public String createdDateText() {
      return CREATED_DATE_FORMAT.format(Instant.ofEpochMilli(this.createdAtMillis).atZone(ZoneId.systemDefault()));
   }

   public long createdAtMillis() {
      return this.createdAtMillis;
   }

   public void setFavorite(boolean favorite) {
      this.favorite = favorite;
   }

   public void setServerRank(String serverRank) {
      this.serverRank = serverRank;
   }

   public static UUID offlineUuid(String username) {
      return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
   }

   public AltAccount(String username, UUID uuid, AltAccount.AccountType type, String lastServer, String serverRank, long createdAtMillis, long playtimeMillis, boolean favorite) {
      this.username = username;
      this.uuid = uuid;
      this.type = type;
      this.lastServer = lastServer;
      this.serverRank = serverRank;
      this.createdAtMillis = createdAtMillis;
      this.playtimeMillis = playtimeMillis;
      this.favorite = favorite;
   }

   public UUID uuid() {
      return this.uuid;
   }

   public void setUuid(UUID uuid) {
      this.uuid = uuid;
   }

   public void recordChat(String message) {
      if (message != null && !message.isBlank()) {
         this.chatHistory.add(0, new String[]{message.trim(), CHAT_TIME_FORMAT.format(Instant.now().atZone(ZoneId.systemDefault()))});

         while (this.chatHistory.size() > 50) {
            this.chatHistory.remove(this.chatHistory.size() - 1);
         }
      }
   }

   public long playtimeMillis() {
      return this.playtimeMillis;
   }

   public void setLastServer(String lastServer) {
      this.lastServer = lastServer;
   }

   public String username() {
      return this.username;
   }

   public boolean isFavorite() {
      return this.favorite;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public String serverRank() {
      return this.serverRank;
   }

   public enum AccountType {
      CRACKED,
      MICROSOFT;
   }
}
