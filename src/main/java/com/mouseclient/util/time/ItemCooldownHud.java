package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.hud.HudIcon;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.access.ItemCooldownAccess;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.avatar.Easings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public final class ItemCooldownHud {
   private static final Item[] PREVIEW_ITEMS = new Item[]{
      Items.ENDER_EYE,
      Items.ENDER_PEARL,
      Items.SUGAR,
      Items.MACE,
      Items.ENCHANTED_GOLDEN_APPLE,
      Items.TRIDENT,
      Items.CROSSBOW,
      Items.DRIED_KELP,
      Items.NETHERITE_SCRAP
   };
   private static final ItemCooldownHud.CooldownHudRow previewRow = new ItemCooldownHud.CooldownHudRow(Items.ENDER_EYE);
   private static long previewCycleStartMillis;
   private static int previewItemIndex;
   private static final Map<Item, ItemCooldownHud.CooldownHudRow> rowsByItem = new LinkedHashMap<>();
   private static final Map<Item, ItemCooldownHud.ReportedCooldown> reportedCooldowns = new LinkedHashMap<>();
   private static final List<ItemCooldownHud.CooldownHudRow> visibleRows = new ArrayList<>();
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();
   private static final TweenAnimation widthAnimation = new TweenAnimation();
   private static final TweenAnimation headerWidthAnimation = new TweenAnimation();
   private static final TweenAnimation durationColumnWidthAnimation = new TweenAnimation();
   private static float width;
   private static float height;

   public static float reportedSecondsRemaining(Item item) {
      ItemCooldownHud.ReportedCooldown cooldown = reportedCooldowns.get(item);
      return cooldown == null ? 0.0F : Math.max(0.0F, cooldown.durationSeconds - (float)(System.currentTimeMillis() - cooldown.startedAtMillis) / (1000.0F));
   }

   private static TextColor itemNameColor(ItemStack stack) {
      TextColor[] firstColor = new TextColor[]{null};
      stack.getName().visit((style, segment) -> {
         if (firstColor[0] == null && style.getColor() != null && !segment.isBlank()) {
            firstColor[0] = style.getColor();
         }

         return Optional.empty();
      }, Style.EMPTY);
      return firstColor[0] != null ? firstColor[0] : TextColor.fromFormatting(stack.getRarity().getFormatting());
   }

   private static void updateDurationText(ItemCooldownHud.CooldownHudRow row, int remainingTenths) {
      if (remainingTenths != row.remainingTenths) {
         row.remainingTenths = remainingTenths;
         int totalSeconds = (remainingTenths + 9) / 10;
         int minutes = totalSeconds / 60;
         int seconds = totalSeconds % 60;
         row.durationText = minutes > 0 ? minutes + "м " + seconds + "с" : seconds + "с";
         row.durationTextWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, row.durationText, (6.0F));
      }
   }

   private static ItemStack cachedDisplayStack(MinecraftClient client, Item item, ItemCooldownHud.ReportedCooldown cooldown) {
      int inventoryChangeCount = client.player.getInventory().getChangeCount();
      long nowMillis = System.currentTimeMillis();
      if (cooldown.cachedStack == null || cooldown.cachedPlayer != client.player || cooldown.inventoryChangeCount != inventoryChangeCount || nowMillis - cooldown.lastStackRefreshMillis >= (1000L)) {
         cooldown.cachedStack = findDisplayStack(client, item, cooldown.name);
         cooldown.cachedPlayer = client.player;
         cooldown.inventoryChangeCount = inventoryChangeCount;
         cooldown.lastStackRefreshMillis = nowMillis;
      }

      return cooldown.cachedStack;
   }

   private ItemCooldownHud() {
   }

   public static void recordReportedCooldown(String name, Item item, float durationSeconds) {
      ItemCooldownHud.ReportedCooldown existingCooldown = reportedCooldowns.get(item);
      if (existingCooldown == null || !((float)(System.currentTimeMillis() - existingCooldown.startedAtMillis) < existingCooldown.durationSeconds * (1000.0F))) {
         ItemCooldownHud.ReportedCooldown newCooldown = new ItemCooldownHud.ReportedCooldown();
         newCooldown.name = name;
         newCooldown.startedAtMillis = System.currentTimeMillis();
         newCooldown.durationSeconds = durationSeconds;
         reportedCooldowns.put(item, newCooldown);
      }
   }

   private static void updateRows(MinecraftClient client) {
      visibleRows.clear();
      rowsByItem.values().forEach(candidateRow -> candidateRow.visible = false);
      if (client.player != null) {
         for (int var1 = 0; var1 < client.player.getInventory().size(); var1++) {
            updateVanillaRow(client, client.player.getInventory().getStack(var1));
         }

         updateVanillaRow(client, client.player.getOffHandStack());
         reportedCooldowns.values().removeIf(reportedCooldown -> (float)(System.currentTimeMillis() - reportedCooldown.startedAtMillis) >= reportedCooldown.durationSeconds * (1000.0F));

         for (Entry entry : reportedCooldowns.entrySet()) {
            ItemCooldownHud.ReportedCooldown cooldown = (ItemCooldownHud.ReportedCooldown)entry.getValue();
            float remainingSeconds = Math.max(0.0F, cooldown.durationSeconds - (float)(System.currentTimeMillis() - cooldown.startedAtMillis) / (1000.0F));
            ItemCooldownHud.CooldownHudRow cooldownRow = rowsByItem.computeIfAbsent((Item)entry.getKey(), ItemCooldownHud.CooldownHudRow::new);
            cooldownRow.displayStack = cachedDisplayStack(client, (Item)entry.getKey(), cooldown);
            cooldownRow.name = cooldown.name;
            cooldownRow.customName = true;
            updateDurationText(cooldownRow, Math.round(remainingSeconds * (10.0F)));
            cooldownRow.visible = true;
         }
      }

      for (ItemCooldownHud.CooldownHudRow row : rowsByItem.values()) {
         if (row.visible || row.visibilityAnimation.floatValue() > (0.001D)) {
            visibleRows.add(row);
         }
      }

      rowsByItem.entrySet()
         .removeIf(rowEntry -> !rowEntry.getValue().visible && rowEntry.getValue().visibilityAnimation.floatValue() <= (0.001D) && rowEntry.getValue().visibilityAnimation.isFinished());
      previewRow.visible = visibleRows.isEmpty() && HudRenderContext.isChatVisible();
      if (previewRow.visible) {
         long nowMillis = System.currentTimeMillis();
         float previewSeconds = (float)((10000L) - (nowMillis - previewCycleStartMillis)) / (1000.0F);
         if (previewSeconds <= 0.0F) {
            previewCycleStartMillis = nowMillis;
            previewItemIndex++;
            previewSeconds = (10.0F);
         }

         Item item = PREVIEW_ITEMS[previewItemIndex % PREVIEW_ITEMS.length];
         if (previewRow.displayStack == null || !previewRow.displayStack.isOf(item)) {
            previewRow.displayStack = new ItemStack(item);
         }

         previewRow.name = ("Example Cooldown");
         previewRow.customName = true;
         updateDurationText(previewRow, Math.round(Math.max(1.0F, previewSeconds) * (10.0F)));
      } else {
         previewCycleStartMillis = 0L;
      }

      if (previewRow.visible || previewRow.visibilityAnimation.floatValue() > (0.001D)) {
         visibleRows.add(previewRow);
      }

      for (ItemCooldownHud.CooldownHudRow measuredRow : visibleRows) {
         measuredRow.nameWidth = nameWidth(measuredRow);
      }

      visibleRows.sort(Comparator.<ItemCooldownHud.CooldownHudRow>comparingDouble(candidateRow -> candidateRow.nameWidth).reversed());

      for (ItemCooldownHud.CooldownHudRow animatedRow : visibleRows) {
         animatedRow.visibilityAnimation.update();
         animatedRow.visibilityAnimation.animateTo(animatedRow.visible ? 1.0 : 0.0, (0.6D), Easings.QUART_OUT);
      }
   }

   static {
      widthAnimation.setValue(1.0);
   }

   private static ItemStack findDisplayStack(MinecraftClient client, Item item, String name) {
      ItemStack stack = ItemStack.EMPTY;
      String[] nameFragments = name.toLowerCase(Locale.ROOT).split(("/"));

      for (int inventoryIndex = 0; inventoryIndex < client.player.getInventory().size() + 1; inventoryIndex++) {
         ItemStack candidateStack = inventoryIndex < client.player.getInventory().size() ? client.player.getInventory().getStack(inventoryIndex) : client.player.getOffHandStack();
         if (!candidateStack.isEmpty() && candidateStack.isOf(item)) {
            if (stack.isEmpty()) {
               stack = candidateStack;
            }

            String stackName = Formatting.strip(candidateStack.getName().getString());
            if (stackName != null) {
               stackName = stackName.toLowerCase(Locale.ROOT);

               for (String fragment : nameFragments) {
                  if (!fragment.isBlank() && stackName.contains(fragment.trim())) {
                     return candidateStack.copy();
                  }
               }
            }
         }
      }

      return stack.isEmpty() ? new ItemStack(item) : stack.copy();
   }

   private static float nameWidth(ItemCooldownHud.CooldownHudRow row) {
      float[] totalWidth = new float[]{0.0F};
      displayName(row).visit((style, segment) -> {
         totalWidth[0] += GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, segment, (6.0F));
         return Optional.empty();
      }, Style.EMPTY);
      return totalWidth[0];
   }

   public static float render(MinecraftClient client, float rightX, float y) {
      updateRows(client);
      float headerWidth = HudPanelRenderer.headerWidth(("Cooldowns"));
      float durationColumnWidth = 0.0F;
      float nameColumnWidth = 0.0F;
      float rowsHeight = (13.0F);

      for (ItemCooldownHud.CooldownHudRow measuredRow : visibleRows) {
         durationColumnWidth = Math.max(durationColumnWidth, HudPanelRenderer.combinedRowWidth((8.0F), measuredRow.durationTextWidth));
         nameColumnWidth = Math.max(nameColumnWidth, HudPanelRenderer.textChipWidth(measuredRow.nameWidth));
         rowsHeight += (14.0F) * measuredRow.visibilityAnimation.floatValue();
      }

      float combinedWidth = durationColumnWidth + (durationColumnWidth > 0.0F && nameColumnWidth > 0.0F ? 2.0F : 0.0F) + nameColumnWidth;
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

      durationColumnWidthAnimation.update();
      if (!visibleRows.isEmpty()) {
         durationColumnWidthAnimation.animateTo(Math.max(durationColumnWidth, 0.0F), (0.6D), Easings.QUART_OUT);
      }

      width = widthAnimation.floatValue();
      height = Math.max(rowsHeight, 1.0F);
      float alpha = visibilityAnimation.floatValue();
      if (alpha <= (0.001F)) {
         return 0.0F;
      }

      float[] anchor = HudLayout.resolveAnchor(("cooldowns"), rightX - width, y);
      float left = anchor[0];
      float top = anchor[1];
      HudLayout.registerBounds(("cooldowns"), left, top, width, height);
      HudRenderContext.pushIdentityScale(left + width / 2.0F, top + height / 2.0F, alpha);
      float cardWidth = Math.max(width, 1.0F);
      HudPanelRenderer.drawStackCard(left, top, cardWidth, height, alpha);
      HudPanelRenderer.drawStackTitle(left, top, cardWidth, alpha, HudIcon.COOLDOWNS, ("Cooldowns"));
      float rowY = top + (15.0F);

      for (ItemCooldownHud.CooldownHudRow row : visibleRows) {
         renderRow(left, cardWidth, rowY, alpha, row);
         rowY += (13.0F) * row.visibilityAnimation.floatValue();
      }

      HudRenderContext.popTransform();
      return height * alpha + (8.0F) * alpha;
   }

   private static void updateVanillaRow(MinecraftClient client, ItemStack stack) {
      if (!stack.isEmpty() && !stack.isOf(Items.BOW) && !stack.isOf(Items.CROSSBOW) && !stack.isOf(Items.TRIDENT)) {
         if (!(client.player.getItemCooldownManager().getCooldownProgress(stack, 0.0F) <= (0.001F))) {
            ItemCooldownHud.CooldownHudRow row = rowsByItem.computeIfAbsent(stack.getItem(), ItemCooldownHud.CooldownHudRow::new);
            if (!row.visible) {
               int remainingTicks = ((ItemCooldownAccess)client.player.getItemCooldownManager()).remainingCooldownTicks(stack);
               updateDurationText(row, Math.max(0, Math.round(remainingTicks / 2.0F)));
               if (row.displayStack == null || row.displayStack.getItem() != stack.getItem()) {
                  row.displayStack = stack.copy();
                  row.name = stack.getName().getString();
               }

               row.customName = false;
               row.visible = true;
            }
         }
      }
   }

   public static void renderHotbarTimers(MinecraftClient client, float screenWidth, float screenHeight) {
      if (client.player != null) {
         float guiScale = GuiScale.getWindowScale() / GuiScale.getBaseScale();

         for (int var4 = 0; var4 < 9; var4++) {
            ItemStack stack = client.player.getInventory().getStack(var4);
            if (!stack.isEmpty() && !stack.isOf(Items.BOW) && !stack.isOf(Items.CROSSBOW) && !stack.isOf(Items.TRIDENT)) {
               float remainingSeconds = Math.max(
                  ((ItemCooldownAccess)client.player.getItemCooldownManager()).remainingCooldownTicks(stack) / (20.0F), reportedSecondsRemaining(stack.getItem())
               );
               if (!(remainingSeconds <= (0.05F))) {
                  float slotX = screenWidth / 2.0F - (91.0F) + (3.0F) + var4 * (20.0F);
                  Render2D.fillRoundedRectangle(
                     slotX * guiScale,
                     (screenHeight - (19.0F)) * guiScale,
                     (16.0F) * guiScale,
                     (16.0F) * guiScale,
                     2.0F * guiScale,
                     Math.round(HudSettingsModule.getInstance().cooldownSlotDimming.floatValue() * (2.55F)) << 24
                  );
                  String timerText = String.valueOf((int)Math.ceil(remainingSeconds));
                  float fontSize = (5.0F) * guiScale;
                  float outlineOffset = (0.7F) * guiScale;
                  float textX = (slotX + 1.0F) * guiScale;
                  float textY = (screenHeight - (19.0F) + (2.5F)) * guiScale - fontSize / 2.0F;
                  int timerColor = remainingSeconds > (30.0F) ? (-50384) : (remainingSeconds <= (5.0F) ? (-12517568) : (remainingSeconds < (15.0F) ? -20440 : (-34264)));
                  GuiTextRenderer.draw(GuiFont.BOLD, timerText, textX - outlineOffset, textY, fontSize, (-16777216));
                  GuiTextRenderer.draw(GuiFont.BOLD, timerText, textX + outlineOffset, textY, fontSize, (-16777216));
                  GuiTextRenderer.draw(GuiFont.BOLD, timerText, textX, textY - outlineOffset, fontSize, (-16777216));
                  GuiTextRenderer.draw(GuiFont.BOLD, timerText, textX, textY + outlineOffset, fontSize, (-16777216));
                  GuiTextRenderer.draw(GuiFont.BOLD, timerText, textX, textY, fontSize, timerColor);
               }
            }
         }
      }
   }

   private static Text displayName(ItemCooldownHud.CooldownHudRow row) {
      if (row.displayStack == null || row.displayStack.isEmpty()) {
         return Text.literal(row.name);
      } else {
         return row.customName
            ? Text.literal(row.name).styled(style -> style.withColor(itemNameColor(row.displayStack)))
            : row.displayStack.getName().copy().styled(fallbackStyle -> fallbackStyle.getColor() == null ? fallbackStyle.withColor(row.displayStack.getRarity().getFormatting()) : fallbackStyle);
      }
   }

   private static void renderRow(float x, float cardWidth, float y, float alpha, ItemCooldownHud.CooldownHudRow row) {
      float rowAlpha = row.visibilityAnimation.floatValue() * alpha;
      if (!(rowAlpha <= (0.001F))) {
         float textY = y + 1.2F;
         float nameX = x + 6.0F;
         if (row.displayStack != null && !row.displayStack.isEmpty()) {
            GuiItemRenderer.render(row.displayStack, nameX, y + 0.4F, 7.0F, ItemRenderOptions.noDecorations(rowAlpha));
            nameX += 10.0F;
         }
         int nameColor = GuiVisuals.multiplyAlpha(0xFFE7EAF0, rowAlpha);
         float[] textCursorX = new float[]{nameX};
         displayName(row).visit((style, segment) -> {
            if (!segment.isEmpty()) {
               int segmentColor = style.getColor() != null ? style.getColor().getRgb() & (16777215) | nameColor & (-16777216) : nameColor;
               GuiTextRenderer.draw(GuiFont.SEMI, segment, textCursorX[0], textY, 6.0F, segmentColor);
               textCursorX[0] += GuiTextRenderer.getWidth(GuiFont.SEMI, segment, 6.0F);
            }

            return Optional.empty();
         }, Style.EMPTY);
         GuiTextRenderer.draw(GuiFont.SEMI, row.durationText, x + cardWidth - 6.0F - row.durationTextWidth, textY, 6.0F, GuiVisuals.multiplyAlpha(0xFFF4F6FA, rowAlpha));
      }
   }

   private static final class CooldownHudRow extends AnimatedHudRow {
      public String name = "";
      public String durationText = "";
      public float durationTextWidth;
      public float nameWidth;
      public int remainingTenths = -1;
      public boolean customName;
      public ItemStack displayStack;

      public CooldownHudRow(Item unusedItem) {
      }
   }

   private static final class ReportedCooldown {
      public String name = "";
      public long startedAtMillis;
      public float durationSeconds;
      public ItemStack cachedStack;
      public Object cachedPlayer;
      public int inventoryChangeCount;
      public long lastStackRefreshMillis;
   }
}
