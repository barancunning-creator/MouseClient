package com.mouseclient.modules.impl.misc;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.mixin.ContainerComponentAccessor;
import com.mouseclient.access.ItemCooldownAccess;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.avatar.Easings;
import com.mouseclient.util.time.PotionPresetStore;
import com.mouseclient.util.time.HudLayout;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import com.mouseclient.util.time.AnimatedHudRow;
import com.mouseclient.util.time.TweenAnimation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class ServerItemBindsHud {
   private static final Map<String, ServerItemBindsHud.ServerBindHudRow> rowsByBinding = new HashMap<>();
   private static final List<ServerItemBindsHud.ServerBindHudRow> visibleRows = new ArrayList<>();
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();
   private static float width;
   private static float height;

   private static float labelWidth(ServerItemBindsHud.ServerBindHudRow row) {
      float cooldownProgress = row.cooldownAnimation.floatValue();
      float bindingWidth = GuiTextRenderer.getWidth(GuiFont.SEMI, row.bindingName, (6.0F));
      float cooldownWidth = row.cooldownText.isEmpty() ? 0.0F : GuiTextRenderer.getWidth(GuiFont.SEMI, row.cooldownText, (6.0F));
      return bindingWidth * (1.0F - cooldownProgress) + cooldownWidth * cooldownProgress;
   }

   private static void updateItemRow(MinecraftClient client, ServerItemBindsHud.ServerBindHudRow row, ServerHelper.ServerItemBind itemBinding) {
      int totalCount = 0;
      ItemStack stack = ItemStack.EMPTY;

      for (int inventoryIndex = 0; inventoryIndex < client.player.getInventory().size(); inventoryIndex++) {
         ItemStack inventoryStack = client.player.getInventory().getStack(inventoryIndex);
         if (itemBinding.matches(inventoryStack)) {
            totalCount += inventoryStack.getCount();
            if (stack.isEmpty()) {
               stack = inventoryStack;
            }
         }

         if (inventoryStack.get(DataComponentTypes.BUNDLE_CONTENTS) != null) {
            for (ItemStack bundleStack : inventoryStack.get(DataComponentTypes.BUNDLE_CONTENTS).iterate()) {
               if (itemBinding.matches(bundleStack)) {
                  totalCount += bundleStack.getCount();
                  if (stack.isEmpty()) {
                     stack = bundleStack;
                  }
               }
            }
         }

         if (Block.getBlockFromItem(inventoryStack.getItem()) instanceof ShulkerBoxBlock && inventoryStack.get(DataComponentTypes.CONTAINER) != null) {
            for (ItemStack shulkerStack : ((ContainerComponentAccessor)(Object)inventoryStack.get(DataComponentTypes.CONTAINER)).getItems()) {
               if (itemBinding.matches(shulkerStack)) {
                  totalCount += shulkerStack.getCount();
                  if (stack.isEmpty()) {
                     stack = shulkerStack;
                  }
               }

               if (shulkerStack.get(DataComponentTypes.BUNDLE_CONTENTS) != null) {
                  for (ItemStack nestedBundleStack : shulkerStack.get(DataComponentTypes.BUNDLE_CONTENTS).iterate()) {
                     if (itemBinding.matches(nestedBundleStack)) {
                        totalCount += nestedBundleStack.getCount();
                        if (stack.isEmpty()) {
                           stack = nestedBundleStack;
                        }
                     }
                  }
               }
            }
         }
      }

      if (!stack.isEmpty()) {
         row.displayStack = stack.copy();
      }

      row.count = totalCount;
      row.onCooldown = false;
      ItemStack cooldownStack = !stack.isEmpty() ? stack : (itemBinding.item() != null ? new ItemStack(itemBinding.item()) : ItemStack.EMPTY);
      if (!cooldownStack.isEmpty() && client.player.getItemCooldownManager().getCooldownProgress(cooldownStack, 0.0F) > (0.001F)) {
         int remainingTicks = ((ItemCooldownAccess)client.player.getItemCooldownManager()).remainingCooldownTicks(cooldownStack);
         row.cooldownFraction = client.player.getItemCooldownManager().getCooldownProgress(cooldownStack, 0.0F);
         int remainingTenths = Math.max(0, Math.round(remainingTicks / 2.0F));
         row.cooldownText = remainingTenths / 10 + "." + remainingTenths % 10 + "с";
         row.onCooldown = true;
         if (stack.isEmpty()) {
            row.displayStack = cooldownStack.copy();
         }
      }

      row.visible = totalCount > 0 || row.onCooldown;
   }

   private ServerItemBindsHud() {
   }

   private static float rowWidth(ServerItemBindsHud.ServerBindHudRow row) {
      return HudPanelRenderer.combinedRowWidth((8.0F) + (row.presetStacks == null ? 0.0F : (row.presetStacks.size() - 1) * (9.5F)), labelWidth(row));
   }

   public static float render(MinecraftClient client, float anchorX, float anchorY) {
      updateRows(client);
      float rowsWidth = 0.0F;
      float rowX = 0.0F;
      int rowCount = 1;
      boolean hasRows = false;

      for (ServerItemBindsHud.ServerBindHudRow measuredRow : visibleRows) {
         float measuredWidth = rowWidth(measuredRow);
         if (rowX > 0.0F && rowX + measuredWidth > (150.0F)) {
            rowCount++;
            rowX = 0.0F;
         }

         measuredRow.rowIndex = rowCount - 1;
         measuredRow.rowX = rowX;
         rowX += measuredWidth + 2.0F;
         rowsWidth = Math.max(rowsWidth, rowX - 2.0F);
         hasRows = true;
      }

      float rowsHeight = hasRows ? 2.0F + rowCount * (11.0F) + (rowCount - 1) * 2.0F : 0.0F;
      visibilityAnimation.update();
      visibilityAnimation.animateTo(visibleRows.isEmpty() ? 0.0 : 1.0, (0.4D), Easings.QUART_OUT);
      if (hasRows) {
         width = Math.max(rowsWidth, 1.0F);
         height = Math.max(rowsHeight, 1.0F);
      }

      float alpha = visibilityAnimation.floatValue();
      if (alpha <= (0.001F)) {
         return 0.0F;
      }

      float[] anchor = HudLayout.resolveAnchor(("serverbinds"), anchorX, anchorY);
      float left = anchor[0] - width / 2.0F;
      float top = anchor[1] - height;
      HudLayout.registerBounds(("serverbinds"), left, top, width, height, anchor[0], anchor[1]);
      HudRenderContext.pushIdentityScale(left + width / 2.0F, top + height / 2.0F, alpha);

      for (ServerItemBindsHud.ServerBindHudRow row : visibleRows) {
         float rowAlpha = row.visibilityAnimation.floatValue() * alpha;
         if (!(rowAlpha <= (0.001F))) {
            renderRow(left + row.rowX, top + row.rowIndex * (13.0F), rowAlpha, row);
         }
      }

      HudRenderContext.popTransform();
      return height * alpha;
   }

   private static void renderRow(float x, float y, float alpha, ServerItemBindsHud.ServerBindHudRow row) {
      float panelWidth = rowWidth(row);
      float labelTextWidth = labelWidth(row);
      HudPanelRenderer.drawPanel(x, y, panelWidth, (11.0F), alpha);
      float dividerX = x + 2.0F + (8.0F) + (row.presetStacks == null ? 0.0F : (row.presetStacks.size() - 1) * (9.5F)) + 1.0F;
      float labelStart = dividerX + (0.25F);
      HudPanelRenderer.drawDivider(dividerX, y + (5.5F), alpha, (3.0F));
      float itemStartX = x + (labelStart - x - (8.0F) - (row.presetStacks == null ? 0.0F : (row.presetStacks.size() - 1) * (9.5F))) / 2.0F;
      if (row.presetStacks != null) {
         for (int var9 = 0; var9 < row.presetStacks.size(); var9++) {
            GuiItemRenderer.render(
               row.presetStacks.get(var9),
               Math.round((itemStartX + var9 * (9.5F)) * 2.0F) / 2.0F,
               Math.round((y + (5.5F) - (4.0F)) * 2.0F) / 2.0F,
               (8.0F),
               ItemRenderOptions.noDecorations(alpha)
            );
         }
      } else if (row.displayStack != null && !row.displayStack.isEmpty()) {
         float singleItemX = Math.round(itemStartX * 2.0F) / 2.0F;
         float singleItemY = Math.round((y + (5.5F) - (4.0F)) * 2.0F) / 2.0F;
         GuiItemRenderer.render(row.displayStack, singleItemX, singleItemY, (8.0F), ItemRenderOptions.noDecorations(alpha));
      }

      if (row.count > 0) {
         String countText = Integer.toString(row.count);
         float countFontSize = (5.0F);
         float countTextWidth = GuiTextRenderer.getWidth(GuiFont.SEMI, countText, countFontSize);
         float countX = itemStartX + (8.0F) - countTextWidth;
         float countY = y + (5.5F) + (4.0F) - countFontSize;
         int countColor = ArgbColor.lerp(HudPanelRenderer.primaryTextColor(1.0F), (-16777216), (0.1F));
         GuiTextRenderer.draw(GuiFont.SEMI, countText, countX, countY, countFontSize, GuiVisuals.multiplyAlpha(countColor, alpha));
      }

      float labelX = labelStart + (x + panelWidth - labelStart - labelTextWidth) / 2.0F;
      float textY = HudPanelRenderer.rowTextY(y);
      float cooldownProgress = row.cooldownAnimation.floatValue();
      if (cooldownProgress < (0.999F)) {
         GuiTextRenderer.draw(
            GuiFont.SEMI, row.bindingName, labelX, textY, (6.0F), GuiVisuals.multiplyAlpha(HudPanelRenderer.primaryTextColor(1.0F), alpha * (1.0F - cooldownProgress))
         );
      }

      if (cooldownProgress > (0.001F) && !row.cooldownText.isEmpty()) {
         int cooldownColor = ArgbColor.lerp((-16711936), (-65536), row.cooldownFraction);
         GuiTextRenderer.draw(GuiFont.SEMI, row.cooldownText, labelX, textY, (6.0F), GuiVisuals.multiplyAlpha(cooldownColor, alpha * cooldownProgress));
      }
   }

   private static void updateRows(MinecraftClient client) {
      visibleRows.clear();
      rowsByBinding.values().forEach(candidateRow -> candidateRow.visible = false);
      if (client.player != null) {
         for (ServerHelper.ServerItemBind itemBinding : ServerHelper.getInstance().itemBinds()) {
            if (itemBinding.binding().isBound() && itemBinding.binding().isVisible()) {
               ServerItemBindsHud.ServerBindHudRow bindingRow = rowsByBinding.computeIfAbsent(itemBinding.binding().getBindingName(), ServerItemBindsHud.ServerBindHudRow::new);
               updateItemRow(client, bindingRow, itemBinding);
            }
         }

         for (PotionPresetStore.PotionPreset preset : PotionPresetStore.getInstance().presets) {
            if (preset.enabled && preset.hotkey.isBound() && PotionCombiner.getInstance().isEnabled()) {
               updatePresetRow(
                  client,
                  rowsByBinding.computeIfAbsent("combo:" + preset.name + ":" + preset.hotkey.getBindingName(), bindingRowKey -> new ServerItemBindsHud.ServerBindHudRow(preset.hotkey.getBindingName())),
                  preset
               );
            }
         }
      }

      boolean hasRealRows = rowsByBinding.values().stream().anyMatch(candidateRow -> candidateRow.visible);
      if (!hasRealRows && HudRenderContext.isChatVisible()) {
         Object[][] previewEntries = new Object[][]{
            {("R"), Items.NETHERITE_SCRAP, 4}, {("F"), Items.ENDER_EYE, 16}, {("V"), Items.PHANTOM_MEMBRANE, 8}, {("X"), Items.FIRE_CHARGE, 12}
         };

         for (Object[] previewEntry : previewEntries) {
            ServerItemBindsHud.ServerBindHudRow previewRow = rowsByBinding.computeIfAbsent("preview:" + previewEntry[0], bindingRowKey -> new ServerItemBindsHud.ServerBindHudRow((String)previewEntry[0]));
            previewRow.displayStack = new ItemStack((Item)previewEntry[1]);
            previewRow.count = (Integer)previewEntry[2];
            previewRow.onCooldown = false;
            previewRow.visible = true;
         }
      }

      for (ServerItemBindsHud.ServerBindHudRow row : rowsByBinding.values()) {
         if (row.visible || row.visibilityAnimation.floatValue() > (0.001D)) {
            visibleRows.add(row);
         }
      }

      rowsByBinding.entrySet()
         .removeIf(rowEntry -> !rowEntry.getValue().visible && rowEntry.getValue().visibilityAnimation.floatValue() <= (0.001D) && rowEntry.getValue().visibilityAnimation.isFinished());
      visibleRows.sort(
         Comparator.<ServerItemBindsHud.ServerBindHudRow>comparingInt(candidateRow -> candidateRow.onCooldown ? 1 : 0)
            .thenComparingDouble(candidateRow -> -((8.0F) + GuiTextRenderer.getWidth(GuiFont.SEMI, candidateRow.bindingName, (6.0F))))
      );

      for (ServerItemBindsHud.ServerBindHudRow animatedRow : visibleRows) {
         animatedRow.visibilityAnimation.update();
         animatedRow.visibilityAnimation.animateTo(animatedRow.visible ? 1.0 : 0.0, (0.6D), Easings.QUART_OUT);
         animatedRow.cooldownAnimation.update();
         animatedRow.cooldownAnimation.animateTo(animatedRow.onCooldown ? 1.0 : 0.0, (0.35D), Easings.QUART_OUT);
      }
   }

   private static void updatePresetRow(MinecraftClient client, ServerItemBindsHud.ServerBindHudRow row, PotionPresetStore.PotionPreset preset) {
      int totalCount = 0;

      for (int var4 = 0; var4 < client.player.getInventory().size(); var4++) {
         ItemStack stack = client.player.getInventory().getStack(var4);
         if (!stack.isEmpty()) {
            for (ServerHelper.ServerItemBind itemBinding : ServerHelper.getInstance().itemBinds()) {
               if (itemBinding.item() == null
                  && itemBinding.matches(stack)
                  && !preset.potions.stream().noneMatch(potion -> potion.name.equals(itemBinding.binding().name))) {
                  totalCount += stack.getCount();
                  break;
               }
            }
         }
      }

      row.presetStacks = new ArrayList<>();

      for (PotionPresetStore.PresetPotion presetPotion : preset.potions) {
         row.presetStacks.add(PotionPresetStore.createPreviewStack(presetPotion.name));
      }

      row.displayStack = row.presetStacks.isEmpty() ? ItemStack.EMPTY : row.presetStacks.get(0);
      row.count = totalCount;
      row.onCooldown = false;
      row.visible = totalCount > 0;
   }

   private static final class ServerBindHudRow extends AnimatedHudRow {
      public final String bindingName;
      public ItemStack displayStack;
      public ArrayList<ItemStack> presetStacks;
      public int count;
      public boolean onCooldown;
      public float cooldownFraction;
      public String cooldownText = "";
      public final TweenAnimation cooldownAnimation = new TweenAnimation();

      public ServerBindHudRow(String bindingName) {
         this.bindingName = bindingName;
      }
   }
}
