package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.hud.HudIcon;
import com.mouseclient.internal.StaffRegistry;
import com.mouseclient.network.HudStyle;
import com.mouseclient.render.avatar.Easings;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

public final class StaffHud {
   public static final Pattern PLAYER_NAME_PATTERN = Pattern.compile(("^\\w{3,16}$"));
   public static final Pattern STAFF_PREFIX_PATTERN = Pattern.compile((".*(mod|der|adm|help|wne|мод|хелп|помо|адм|владе|отри|таф|taf|curat|курато|dev|раз|supp|сапп|yt|ютуб).*"));
   private static final String[][] PREVIEW_ENTRIES = new String[][]{
      {("karandash"), ("Helper"), ("26:08"), ("ι")},
      {("smdxyxz"), ("Tester"), ("08:56"), ("κ")},
      {("Кирейко"), ("Moder"), ("10:34"), ("ι")}
   };
   private static final Map<String, StaffHud.StaffHudRow> rowsByName = new LinkedHashMap<>();
   private static final HashMap<String, Long> firstSeenMillis = new HashMap<>();
   private static final List<StaffHud.StaffHudRow> visibleRows = new ArrayList<>();
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();
   private static final TweenAnimation widthAnimation = new TweenAnimation();
   private static final TweenAnimation headerWidthAnimation = new TweenAnimation();
   private static final TweenAnimation timeColumnWidthAnimation = new TweenAnimation();
   private static float width;
   private static float height;
   private static long lastRefreshMillis;
   private static final IdentityHashMap<PlayerListEntry, StaffHud.StaffEntryCache> entryCache = new IdentityHashMap<>();
   private static final HashSet<String> presentStaffNames = new HashSet<>();
   private static Object cachedWorld;
   private static long lastCacheClearMillis;

   private static float prefixWidth(StaffHud.StaffHudRow row) {
      return row.rankText.isEmpty() ? 0.0F : GuiTextRenderer.getWidth(GuiFont.SEMI, row.rankText, (6.0F)) + (3.0F);
   }

   private StaffHud() {
   }

   private static String staffRankText(PlayerListEntry entry, String name, Team team) {
      if (!StaffRegistry.containsName(name) && !PLAYER_NAME_PATTERN.matcher(name).matches()) {
         return null;
      }

      String teamPrefix = team == null ? "" : team.getPrefix().getString().trim();
      String rankText = !teamPrefix.isEmpty() ? teamPrefix : (entry.getDisplayName() == null ? "" : entry.getDisplayName().getString().replace(name, "").trim());
      return StaffRegistry.containsName(name) || StaffRegistry.containsPrefix(rankText) || !rankText.isEmpty() && STAFF_PREFIX_PATTERN.matcher(rankText.toLowerCase(Locale.ROOT)).matches() ? rankText : null;
   }

   static {
      widthAnimation.setValue(1.0);
   }

   private static void renderRow(MinecraftClient client, float x, float cardWidth, float y, float alpha, StaffHud.StaffHudRow row) {
      float rowAlpha = row.visibilityAnimation.floatValue() * alpha;
      if (!(rowAlpha <= (0.001F))) {
         float timeWidth = timeTextWidth(row);
         float textY = y + 1.2F;
         float nameX = x + 6.0F;
         String skinPath = skinTexturePath(client, row.name);
         float avatarY = y + 0.4F;
         int avatarColor = GuiVisuals.multiplyAlpha(-1, rowAlpha);
         Render2D.drawNearestImageRegion(
            skinPath,
            nameX,
            avatarY,
            7.0F,
            7.0F,
            3.5F,
            0.25F,
            (0.125F),
            (0.125F),
            (0.25F),
            (0.25F),
            avatarColor
         );
         Render2D.drawNearestImageRegion(
            skinPath,
            nameX,
            avatarY,
            7.0F,
            7.0F,
            3.5F,
            0.25F,
            (0.625F),
            (0.125F),
            (0.75F),
            (0.25F),
            avatarColor
         );
         nameX += 10.0F;
         if (row.styledPrefix != null) {
            GuiTextRenderer.drawStyledText(GuiFont.SEMI, row.styledPrefix, nameX, textY, 6.0F, GuiVisuals.multiplyAlpha(0xFFE7EAF0, rowAlpha));
         } else if (!row.rankText.isEmpty()) {
            GuiTextRenderer.draw(GuiFont.SEMI, row.rankText, nameX, textY, 6.0F, GuiVisuals.multiplyAlpha(0xFFB7BDC8, rowAlpha));
         }
         GuiTextRenderer.draw(
            GuiFont.SEMI, row.name, nameX + prefixWidth(row), textY, 6.0F, GuiVisuals.multiplyAlpha(0xFFE7EAF0, rowAlpha)
         );
         GuiTextRenderer.draw(GuiFont.SEMI, row.elapsedText, x + cardWidth - 6.0F - timeWidth, textY, 6.0F, GuiVisuals.multiplyAlpha(0xFFF4F6FA, rowAlpha));
      }
   }

   private static float namePanelWidth(StaffHud.StaffHudRow row) {
      return (15.0F) + prefixWidth(row) + GuiTextRenderer.getWidth(GuiFont.SEMI, row.name, (6.0F)) + (4.0F);
   }

   private static void updateRows(MinecraftClient client) {
      visibleRows.clear();
      long nowMillis = System.currentTimeMillis();
      if (nowMillis - lastRefreshMillis >= (250L)) {
         refreshStaffEntries(client);
         lastRefreshMillis = nowMillis;
      }

      boolean hasRealStaff = rowsByName.values().stream().anyMatch(candidateRow -> candidateRow.visible && !candidateRow.preview);
      if (!hasRealStaff && HudRenderContext.isChatVisible()) {
         for (String[] previewEntry : PREVIEW_ENTRIES) {
            StaffHud.StaffHudRow previewRow = rowsByName.computeIfAbsent(previewEntry[0], StaffHud.StaffHudRow::new);
            previewRow.rankText = previewEntry[1];
            previewRow.styledPrefix = null;
            previewRow.elapsedText = previewEntry[2];
            previewRow.statusIcon = previewEntry[3];
            previewRow.preview = true;
            previewRow.visible = true;
         }
      }

      for (StaffHud.StaffHudRow row : rowsByName.values()) {
         if (row.visible || row.visibilityAnimation.floatValue() > (0.001D)) {
            visibleRows.add(row);
         }
      }

      rowsByName.entrySet()
         .removeIf(rowEntry -> !rowEntry.getValue().visible && rowEntry.getValue().visibilityAnimation.floatValue() <= (0.001D) && rowEntry.getValue().visibilityAnimation.isFinished());

      for (StaffHud.StaffHudRow animatedRow : visibleRows) {
         animatedRow.visibilityAnimation.update();
         animatedRow.visibilityAnimation.animateTo(animatedRow.visible ? 1.0 : 0.0, (0.6D), Easings.QUART_OUT);
      }
   }

   public static float render(float rightX, float y) {
      MinecraftClient client = MinecraftClient.getInstance();
      updateRows(client);
      float headerWidth = HudPanelRenderer.headerWidth(("Staffs"));
      float timeColumnWidth = 0.0F;
      float nameColumnWidth = 0.0F;
      float rowsHeight = (13.0F);

      for (StaffHud.StaffHudRow measuredRow : visibleRows) {
         timeColumnWidth = Math.max(timeColumnWidth, HudPanelRenderer.combinedRowWidth((6.0F), timeTextWidth(measuredRow)));
         nameColumnWidth = Math.max(nameColumnWidth, namePanelWidth(measuredRow));
         rowsHeight += (14.0F) * measuredRow.visibilityAnimation.floatValue();
      }

      float combinedWidth = timeColumnWidth + (timeColumnWidth > 0.0F && nameColumnWidth > 0.0F ? 2.0F : 0.0F) + nameColumnWidth;
      float targetWidth = Math.max(headerWidth, Math.max(combinedWidth, 1.0F));
      boolean showPanel = !visibleRows.isEmpty() || HudRenderContext.isChatVisible();
      visibilityAnimation.update();
      visibilityAnimation.animateTo(showPanel ? 1.0 : 0.0, (0.4D), Easings.QUART_OUT);
      widthAnimation.update();
      if (!visibleRows.isEmpty()) {
         widthAnimation.animateTo(Math.max(targetWidth, 1.0F), (0.6D), Easings.QUART_OUT);
      }

      headerWidthAnimation.update();
      if (!visibleRows.isEmpty()) {
         headerWidthAnimation.animateTo(Math.max(targetWidth, 1.0F), (0.6D), Easings.QUART_OUT);
      }

      timeColumnWidthAnimation.update();
      if (!visibleRows.isEmpty()) {
         timeColumnWidthAnimation.animateTo(Math.max(timeColumnWidth, 0.0F), (0.6D), Easings.QUART_OUT);
      }

      width = widthAnimation.floatValue();
      height = Math.max(rowsHeight, 1.0F);
      float alpha = visibilityAnimation.floatValue();
      if (alpha <= (0.001F)) {
         return 0.0F;
      }

      float[] anchor = HudLayout.resolveAnchor(("staffs"), rightX - width, y);
      float left = anchor[0];
      float top = anchor[1];
      HudLayout.registerBounds(("staffs"), left, top, width, height);
      HudRenderContext.pushIdentityScale(left + width / 2.0F, top + height / 2.0F, alpha);
      float cardWidth = Math.max(width, 1.0F);
      HudPanelRenderer.drawStackCard(left, top, cardWidth, height, alpha);
      HudPanelRenderer.drawStackTitle(left, top, cardWidth, alpha, HudIcon.STAFF, ("Staffs"));
      float rowY = top + (15.0F);

      for (StaffHud.StaffHudRow row : visibleRows) {
         renderRow(client, left, cardWidth, rowY, alpha, row);
         rowY += (13.0F) * row.visibilityAnimation.floatValue();
      }

      HudRenderContext.popTransform();
      return height * alpha;
   }

   private static float timeTextWidth(StaffHud.StaffHudRow row) {
      return GuiTextRenderer.getWidth(GuiFont.SEMI, row.elapsedText, (6.0F));
   }

   private static void refreshStaffEntries(MinecraftClient client) {
      for (StaffHud.StaffHudRow row : rowsByName.values()) {
         row.visible = false;
      }

      if (client.getNetworkHandler() != null && client.world != null) {
         long nowMillis = System.currentTimeMillis();
         if (client.world != cachedWorld || nowMillis - lastCacheClearMillis >= (5000L)) {
            entryCache.clear();
            cachedWorld = client.world;
            lastCacheClearMillis = nowMillis;
         }

         presentStaffNames.clear();
         java.util.Collection<net.minecraft.client.network.PlayerListEntry> playerList = client.getNetworkHandler().getPlayerList();
         if (entryCache.size() > playerList.size() * 2 + 16) {
            entryCache.clear();
         }

         for (PlayerListEntry entry : playerList) {
            String name = entry.getProfile().name();
            if (name != null && !name.isEmpty()) {
               Team team = client.world.getScoreboard().getScoreHolderTeam(name);
               Text text = team == null ? null : team.getPrefix();
               Text displayName = entry.getDisplayName();
               StaffHud.StaffEntryCache cachedEntry = entryCache.get(entry);
               if (cachedEntry == null || cachedEntry.team != team || cachedEntry.prefix != text || cachedEntry.display != displayName || !name.equals(cachedEntry.name)) {
                  cachedEntry = new StaffHud.StaffEntryCache(name, team, text, displayName, staffRankText(entry, name, team));
                  entryCache.put(entry, cachedEntry);
               }

               String rankText = cachedEntry.rankText;
               if (rankText != null) {
                  presentStaffNames.add(name);
                  long joinedAtMillis = firstSeenMillis.computeIfAbsent(name, staffName -> System.currentTimeMillis());
                  StaffHud.StaffHudRow staffRow = rowsByName.computeIfAbsent(name, StaffHud.StaffHudRow::new);
                  staffRow.rankText = rankText.isEmpty() ? ("Staff") : rankText;
                  staffRow.styledPrefix = team != null && !team.getPrefix().getString().trim().isEmpty() ? team.getPrefix() : null;
                  staffRow.elapsedText = String.format(
                     ("%02d:%02d"), (System.currentTimeMillis() - joinedAtMillis) / (60000L), (System.currentTimeMillis() - joinedAtMillis) / (1000L) % (60L)
                  );
                  staffRow.statusIcon = entry.getGameMode() == GameMode.SPECTATOR ? ("ι") : ("κ");
                  staffRow.preview = false;
                  staffRow.visible = true;
               }
            }
         }

         firstSeenMillis.keySet().retainAll(presentStaffNames);
      } else {
         firstSeenMillis.clear();
      }
   }

   private static String skinTexturePath(MinecraftClient client, String name) {
      if (client.getNetworkHandler() != null) {
         PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(name);
         if (entry != null) {
            return entry.getSkinTextures().body().texturePath().toString();
         }
      }

      UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
      return DefaultSkinHelper.getSkinTextures(offlineUuid).body().texturePath().toString();
   }

   private record StaffEntryCache(String name, Object team, Object prefix, Object display, String rankText) {
   }

   private static final class StaffHudRow extends AnimatedHudRow {
      public final String name;
      public String rankText = "";
      public Text styledPrefix;
      public String elapsedText = "";
      public String statusIcon;
      public boolean preview;

      public StaffHudRow(String name) {
         this.statusIcon = ("κ");
         this.name = name;
      }
   }
}
