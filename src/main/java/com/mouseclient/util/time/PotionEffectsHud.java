package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.hud.HudIcon;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.network.HudStyle;
import com.mouseclient.render.avatar.Easings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class PotionEffectsHud {
   private static final Set<RegistryEntry<StatusEffect>> PRIORITY_EFFECTS = Set.of(
      StatusEffects.STRENGTH,
      StatusEffects.SPEED,
      StatusEffects.FIRE_RESISTANCE,
      StatusEffects.RESISTANCE,
      StatusEffects.ABSORPTION,
      StatusEffects.REGENERATION
   );
   private static final List<RegistryEntry<StatusEffect>> PREVIEW_EFFECTS = List.of(
      StatusEffects.SPEED,
      StatusEffects.STRENGTH,
      StatusEffects.REGENERATION,
      StatusEffects.FIRE_RESISTANCE,
      StatusEffects.WATER_BREATHING,
      StatusEffects.POISON
   );
   private static final PotionEffectsHud.PotionHudRow previewRow = new PotionEffectsHud.PotionHudRow(("Example Potion"), 0);
   private static long previewCycleStartMillis;
   private static int previewEffectIndex;
   private static final Map<SpecialPotion, PotionEffectsHud.PotionHudRow> specialRows = new EnumMap<>(SpecialPotion.class);
   private static final Map<SpecialPotion, Long> splashTimes = new EnumMap<>(SpecialPotion.class);
   private static final Map<RegistryEntry<StatusEffect>, PotionEffectsHud.PotionHudRow> effectRows = new IdentityHashMap<>();
   private static final List<PotionEffectsHud.PotionHudRow> visibleRows = new ArrayList<>();
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();
   private static final TweenAnimation widthAnimation = new TweenAnimation();
   private static final TweenAnimation headerWidthAnimation = new TweenAnimation();
   private static final TweenAnimation durationColumnWidthAnimation = new TweenAnimation();
   private static float width;
   private static float height;

   private static float nameWidth(PotionEffectsHud.PotionHudRow row) {
      return GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, row.name, (6.0F)) + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, row.amplifierSuffix, (6.0F));
   }

   private static void updateRows(MinecraftClient client) {
      visibleRows.clear();
      effectRows.values().forEach(candidateRow -> candidateRow.visible = false);
      if (client.player != null) {
         for (StatusEffectInstance statusEffectInstance : client.player.getStatusEffects()) {
            PotionEffectsHud.PotionHudRow effectRow = effectRows.computeIfAbsent(statusEffectInstance.getEffectType(), PotionEffectsHud.PotionHudRow::new);
            int amplifier = statusEffectInstance.getAmplifier();
            if (amplifier != effectRow.cachedAmplifier) {
               effectRow.cachedAmplifier = amplifier;
               effectRow.amplifierSuffix = amplifier > 0 ? " " + (amplifier + 1) : "";
            }

            effectRow.effect = statusEffectInstance;
            effectRow.durationText = durationText(statusEffectInstance);
            effectRow.visible = true;
         }
      }

      updateSpecialRows(client);

      for (PotionEffectsHud.PotionHudRow vanillaRow : effectRows.values()) {
         if (vanillaRow.visible || vanillaRow.visibilityAnimation.floatValue() > (0.001D)) {
            visibleRows.add(vanillaRow);
         }
      }

      for (PotionEffectsHud.PotionHudRow specialRow : specialRows.values()) {
         if (specialRow.visible || specialRow.visibilityAnimation.floatValue() > (0.001D)) {
            visibleRows.add(specialRow);
         }
      }

      effectRows.entrySet()
         .removeIf(rowEntry -> !rowEntry.getValue().visible && rowEntry.getValue().visibilityAnimation.floatValue() <= (0.001D) && rowEntry.getValue().visibilityAnimation.isFinished());
      specialRows.entrySet()
         .removeIf(rowEntry -> !rowEntry.getValue().visible && rowEntry.getValue().visibilityAnimation.floatValue() <= (0.001D) && rowEntry.getValue().visibilityAnimation.isFinished());
      previewRow.visible = visibleRows.isEmpty() && HudRenderContext.isChatVisible();
      if (previewRow.visible) {
         long nowMillis = System.currentTimeMillis();
         float previewSeconds = (float)((10000L) - (nowMillis - previewCycleStartMillis)) / (1000.0F);
         if (previewSeconds <= 0.0F) {
            previewCycleStartMillis = nowMillis;
            previewEffectIndex++;
            previewSeconds = (10.0F);
         }

         previewRow.effect = new StatusEffectInstance(
            PREVIEW_EFFECTS.get(previewEffectIndex % PREVIEW_EFFECTS.size()), (int)Math.ceil(Math.max(1.0F, previewSeconds)) * 20, 0
         );
         previewRow.durationText = durationText(previewRow.effect);
      } else {
         previewCycleStartMillis = 0L;
      }

      if (previewRow.visible || previewRow.visibilityAnimation.floatValue() > (0.001D)) {
         visibleRows.add(previewRow);
      }

      visibleRows.sort(
         Comparator.<PotionEffectsHud.PotionHudRow, Integer>comparing(candidateRow -> candidateRow.sortOrder)
            .thenComparing(Comparator.<PotionEffectsHud.PotionHudRow>comparingDouble(candidateRow -> nameWidth(candidateRow)).reversed())
      );

      for (PotionEffectsHud.PotionHudRow animatedRow : visibleRows) {
         animatedRow.visibilityAnimation.update();
         animatedRow.visibilityAnimation.animateTo(animatedRow.visible ? 1.0 : 0.0, (0.6D), Easings.QUART_OUT);
      }
   }

   static {
      widthAnimation.setValue(1.0);
   }

   private static String durationText(StatusEffectInstance effect) {
      if (effect.isInfinite()) {
         return ("INF");
      }

      int totalSeconds = effect.getDuration() / 20;
      int minutes = totalSeconds / 60;
      int seconds = totalSeconds % 60;
      return minutes > 0 ? minutes + "м " + seconds + "с" : seconds + "с";
   }

   public static float render(MinecraftClient client, float rightX, float y) {
      updateRows(client);
      float headerWidth = HudPanelRenderer.headerWidth(("Potions"));
      float durationColumnWidth = 0.0F;
      float nameColumnWidth = 0.0F;
      float rowsHeight = (13.0F);

      for (PotionEffectsHud.PotionHudRow measuredRow : visibleRows) {
         durationColumnWidth = Math.max(durationColumnWidth, HudPanelRenderer.combinedRowWidth((8.0F), durationWidth(measuredRow)));
         nameColumnWidth = Math.max(nameColumnWidth, HudPanelRenderer.textChipWidth(nameWidth(measuredRow)));
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

      float[] anchor = HudLayout.resolveAnchor(("potions"), rightX - width, y);
      float left = anchor[0];
      float top = anchor[1];
      HudLayout.registerBounds(("potions"), left, top, width, height);
      HudRenderContext.pushIdentityScale(left + width / 2.0F, top + height / 2.0F, alpha);
      float cardWidth = Math.max(width, 1.0F);
      HudPanelRenderer.drawStackCard(left, top, cardWidth, height, alpha);
      HudPanelRenderer.drawStackTitle(left, top, cardWidth, alpha, HudIcon.POTIONS, ("Potions"));
      float rowY = top + (15.0F);

      for (PotionEffectsHud.PotionHudRow row : visibleRows) {
         renderRow(left, cardWidth, rowY, alpha, row);
         rowY += (13.0F) * row.visibilityAnimation.floatValue();
      }

      HudRenderContext.popTransform();
      return height * alpha + (8.0F) * alpha;
   }

   public static void recordNearbySplash(SpecialPotion potion, BlockPos splashPosition) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && !(client.player.getBoundingBox().squaredMagnitude(splashPosition.toCenterPos()) > (16.0D))) {
         splashTimes.put(potion, System.currentTimeMillis());
      }
   }

   public static int effectSortOrder(RegistryEntry<StatusEffect> effectType) {
      if (PRIORITY_EFFECTS.contains(effectType)) {
         return 0;
      } else {
         StatusEffectCategory category = ((StatusEffect)effectType.value()).getCategory();
         if (category == StatusEffectCategory.BENEFICIAL) {
            return 1;
         } else {
            return category == StatusEffectCategory.NEUTRAL ? 2 : 3;
         }
      }
   }

   private PotionEffectsHud() {
   }

   private static float durationWidth(PotionEffectsHud.PotionHudRow row) {
      return GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, row.durationText, (6.0F));
   }

   private static void updateSpecialRows(MinecraftClient client) {
      EnumMap<SpecialPotion, Boolean> previousVisibility = new EnumMap<>(SpecialPotion.class);
      specialRows.forEach((specialPotion, candidateRow) -> previousVisibility.put(specialPotion, candidateRow.visible));
      specialRows.values().forEach(candidateRow -> candidateRow.visible = false);
      if (client.player != null && HudSettingsModule.getInstance().showSpecialPotions.getValue() && SpecialPotion.isTrackingEnabled()) {
         for (SpecialPotion potion : SpecialPotion.values()) {
            StatusEffectInstance representativeEffect = null;

            for (SpecialPotion.SpecialPotionEffect effectSpec : potion.effects()) {
               StatusEffectInstance effect = client.player.getStatusEffect(effectSpec.effect());
               if (effect == null || !SpecialPotion.amplifierMatches(effect.getAmplifier(), effectSpec.amplifier())) {
                  representativeEffect = null;
                  break;
               }

               if (representativeEffect == null || effect.getDuration() > representativeEffect.getDuration()) {
                  representativeEffect = effect;
               }
            }

            boolean recentlyVisible = previousVisibility.getOrDefault(potion, false) || System.currentTimeMillis() - splashTimes.getOrDefault(potion, 0L) < (3000L);
            if (representativeEffect != null && recentlyVisible) {
               PotionEffectsHud.PotionHudRow specialRow = specialRows.computeIfAbsent(potion, specialPotion -> {
                  PotionEffectsHud.PotionHudRow candidateRow = new PotionEffectsHud.PotionHudRow(specialPotion.plainName(), 0);
                  candidateRow.styledName = specialPotion.fullName();
                  return candidateRow;
               });

               for (SpecialPotion.SpecialPotionEffect suppressedEffectSpec : potion.effects()) {
                  PotionEffectsHud.PotionHudRow vanillaRow = effectRows.get(suppressedEffectSpec.effect());
                  if (vanillaRow != null) {
                     vanillaRow.visible = false;
                  }
               }

               specialRow.effect = representativeEffect;
               specialRow.durationText = durationText(representativeEffect);
               specialRow.visible = true;
            }
         }
      }
   }

   private static void renderRow(float x, float cardWidth, float y, float alpha, PotionEffectsHud.PotionHudRow row) {
      float rowAlpha = row.visibilityAnimation.floatValue() * alpha;
      if (!(rowAlpha <= (0.001F))) {
         boolean harmful = row.effect != null && row.effect.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL;
         float textY = y + 1.2F;
         float nameX = x + 6.0F;
         if (row.effect != null) {
            Render2D.pushAlpha(rowAlpha);
            Render2D.drawEffectIcon(row.effect.getEffectType(), nameX, y + 0.6F, 7.0F);
            Render2D.popAlpha();
            nameX += 10.0F;
         }
         int nameColor = GuiVisuals.multiplyAlpha(harmful ? 0xFFFF6B6B : 0xFFE7EAF0, rowAlpha);
         if (row.styledName != null) {
            GuiTextRenderer.drawStyledText(GuiFont.SEMI, row.styledName, nameX, textY, 6.0F, GuiVisuals.multiplyAlpha(-1, rowAlpha));
         } else {
            GuiTextRenderer.draw(GuiFont.SEMI, row.name, nameX, textY, 6.0F, nameColor);
         }
         if (!row.amplifierSuffix.isEmpty()) {
            float labelWidth = row.styledName != null ? GuiTextRenderer.getWidth(GuiFont.SEMI, row.name, 6.0F) : GuiTextRenderer.getWidth(GuiFont.SEMI, row.name, 6.0F);
            GuiTextRenderer.draw(
               GuiFont.SEMI,
               row.amplifierSuffix,
               nameX + labelWidth,
               textY,
               6.0F,
               GuiVisuals.multiplyAlpha(0xFFB7BDC8, rowAlpha)
            );
         }
         float valueWidth = durationWidth(row);
         GuiTextRenderer.draw(GuiFont.SEMI, row.durationText, x + cardWidth - 6.0F - valueWidth, textY, 6.0F, GuiVisuals.multiplyAlpha(0xFFF4F6FA, rowAlpha));
      }
   }

   private static final class PotionHudRow extends AnimatedHudRow {
      public final String name;
      public String amplifierSuffix = "";
      public String durationText = "";
      public StatusEffectInstance effect;
      public final int sortOrder;
      public int cachedAmplifier = -1;
      public Text styledName;

      public PotionHudRow(String name, int sortOrder) {
         this.name = name;
         this.sortOrder = sortOrder;
      }

      public PotionHudRow(RegistryEntry<StatusEffect> effectType) {
         this.name = ((StatusEffect)effectType.value()).getName().getString();
         this.sortOrder = PotionEffectsHud.effectSortOrder(effectType);
      }
   }
}
