package com.mouseclient.util.time;

import com.mouseclient.gui.HudPanelRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.gui.HudRenderContext;
import com.mouseclient.hud.HudIcon;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.mode.GlintMode;
import com.mouseclient.mode.ItemRenderOptions;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.render.GuiItemRenderer;
import com.mouseclient.render.avatar.Easings;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.entity.RotationAngles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class ExtendedTargetHud implements MinecraftAccess {
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   private static final HashMap<String, Item> targetSoundItems = new HashMap<>();
   private static final HashMap<String, Item> localSoundItems = new HashMap<>();
   private static final HashMap<Item, Integer> itemCooldownSeconds = new HashMap<>();
   private static final HashMap<Item, Long> localItemUseTimes = new HashMap<>();
   private static final HashMap<UUID, ExtendedTargetHud.TargetSnapshot> targetSnapshots = new HashMap<>();
   private static final HashSet<Integer> observedProjectiles = new HashSet<>();
   private static final TweenAnimation visibilityAnimation = new TweenAnimation();
   private static final TweenAnimation playerRowAnimation = new TweenAnimation();
   private static final TweenAnimation panelWidthAnimation = new TweenAnimation();
   private static final TweenAnimation headerWidthAnimation = new TweenAnimation();
   private static final TweenAnimation healthWidthAnimation = new TweenAnimation();
   private static final TweenAnimation itemRowAnimation = new TweenAnimation();
   private static final TweenAnimation bearingAnimation = new TweenAnimation();
   private static UUID targetUuid;
   private static long lastTargetSeenMillis;
   private static boolean bossBarRetention;
   private static long bossBarEndedMillis;
   private static ClientWorld displayWorld;
   private static ExtendedTargetHud.TargetSnapshot lastSnapshot;
   private static final long LOCAL_USE_SUPPRESSION_MILLIS = 2500L;

   private static final double HEALTH_WIDTH_DURATION = 0.6;

   private static final float EQUIPMENT_SPACING = 3.0F;

   private static final float ITEM_COUNT_FONT_SIZE = 5.0F;

   private static void updateDisplayTarget(MinecraftClient client) {
      if (client.player != null && client.world != null) {
         if (client.world != displayWorld) {
            displayWorld = client.world;
            targetUuid = null;
            bossBarRetention = false;
            targetSnapshots.clear();
         }

         long nowMillis = System.currentTimeMillis();
         if (targetUuid != null && client.inGameHud != null) {
            if (!((BossBarHudAccessor)(Object)client.inGameHud.getBossBarHud()).getEvents().isEmpty()) {
               bossBarRetention = true;
               bossBarEndedMillis = 0L;
            } else if (bossBarRetention && bossBarEndedMillis == 0L) {
               bossBarEndedMillis = nowMillis;
            } else if (bossBarRetention && nowMillis - bossBarEndedMillis > (2000L)) {
               targetUuid = null;
               bossBarRetention = false;
               bossBarEndedMillis = 0L;
               targetSnapshots.clear();
            }
         }

         PlayerEntity player = AttackAura.getInstance().isEnabled() && AttackAura.getInstance().target() instanceof PlayerEntity auraTarget ? auraTarget : null;
         if (player != null && !FriendManager.contains(player.getGameProfile().name())) {
            lastTargetSeenMillis = nowMillis;
            if (!player.getUuid().equals(targetUuid)) {
               targetUuid = player.getUuid();
               bossBarRetention = false;
               bossBarEndedMillis = 0L;
            }
         }

         boolean localTarget = targetUuid != null && targetUuid.equals(client.player.getUuid());
         if (player != null || !HudRenderContext.isChatVisible() || targetUuid != null && !localTarget) {
            if (targetUuid != null
               && (!localTarget || HudRenderContext.isChatVisible())
               && (player != null || nowMillis - lastTargetSeenMillis <= (3000L) || bossBarRetention || HudRenderContext.isChatVisible())) {
               PlayerEntity displayPlayer = null;

               for (PlayerEntity candidate : client.world.getPlayers()) {
                  if (candidate.getUuid().equals(targetUuid)) {
                     displayPlayer = candidate;
                  }
               }

               if (displayPlayer != null && CombatTargeting.displayedHealth(displayPlayer) <= 0.0F) {
                  targetSnapshots.clear();
                  targetUuid = null;
               } else if (displayPlayer != null || targetSnapshots.containsKey(targetUuid)) {
                  if (displayPlayer != null) {
                     targetSnapshots.computeIfAbsent(targetUuid, ExtendedTargetHud.TargetSnapshot::new).update(client, displayPlayer, nowMillis);
                  }

                  targetSnapshots.keySet().removeIf(snapshotUuid -> !snapshotUuid.equals(targetUuid));
                  observeProjectiles(client, nowMillis);
               }
            } else {
               targetSnapshots.clear();
               targetUuid = null;
            }
         } else {
            targetUuid = client.player.getUuid();
            targetSnapshots.computeIfAbsent(targetUuid, ExtendedTargetHud.TargetSnapshot::new).update(client, client.player, nowMillis);
            targetSnapshots.get(targetUuid).name = ("Example Player");
         }
      }
   }

   private static boolean isTrackedConsumable(ItemStack stack) {
      return isHealingPotion(stack) && serverItemPriority(stack) <= 0
         || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)
         || stack.isOf(Items.GOLDEN_APPLE)
         || stack.isOf(Items.GOLDEN_CARROT);
   }

   public static void recordLocalItemUse(Item item) {
      localItemUseTimes.put(item, System.currentTimeMillis());
   }

   private static void observeProjectiles(MinecraftClient client, long nowMillis) {
      ExtendedTargetHud.TargetSnapshot snapshot = targetUuid == null ? null : targetSnapshots.get(targetUuid);
      if (snapshot != null) {
         for (Entity entity : client.world.getEntities()) {
            if (entity instanceof ProjectileEntity projectile && !observedProjectiles.contains(entity.getId()) && projectile.getOwner() != null) {
               observedProjectiles.add(entity.getId());
               if (projectile.getOwner().getUuid().equals(targetUuid)) {
                  if (entity instanceof ThrownItemEntity thrownItem && !thrownItem.getStack().isEmpty()) {
                     snapshot.recordItemUse(thrownItem.getStack().getItem(), nowMillis);
                  } else if (entity.getType() == EntityType.WIND_CHARGE) {
                     snapshot.recordItemUse(Items.WIND_CHARGE, nowMillis);
                  } else if (entity.getType() == EntityType.SMALL_FIREBALL) {
                     snapshot.recordItemUse(Items.FIRE_CHARGE, nowMillis);
                  }
               }
            }
         }

         if (observedProjectiles.size() > 256) {
            observedProjectiles.clear();
         }
      }
   }

   static {
      itemCooldownSeconds.put(Items.DRIED_KELP, 10);
      itemCooldownSeconds.put(Items.NETHERITE_SCRAP, 10);
      itemCooldownSeconds.put(Items.SNOWBALL, 20);
      itemCooldownSeconds.put(Items.ENDER_EYE, 60);
      itemCooldownSeconds.put(Items.FIRE_CHARGE, 60);
      itemCooldownSeconds.put(Items.SUGAR, 60);
      itemCooldownSeconds.put(Items.ENCHANTED_GOLDEN_APPLE, 150);
      itemCooldownSeconds.put(Items.GOLDEN_APPLE, 30);
      itemCooldownSeconds.put(Items.GOLDEN_CARROT, 30);
      localSoundItems.put(("minecraft:entity.illusioner.mirror_move"), Items.ENDER_EYE);
      targetSoundItems.put(("minecraft:entity.blaze.shoot"), Items.FIRE_CHARGE);
      targetSoundItems.put(("minecraft:entity.wind_charge.throw"), Items.WIND_CHARGE);
   }

   public static void recordStructureItemUse(boolean netheriteScrap, boolean dragonStructure) {
      ExtendedTargetHud.TargetSnapshot snapshot = targetUuid == null ? null : targetSnapshots.get(targetUuid);
      if (snapshot != null) {
         Item item = netheriteScrap ? Items.NETHERITE_SCRAP : Items.DRIED_KELP;
         if (System.currentTimeMillis() - localItemUseTimes.getOrDefault(item, 0L) >= LOCAL_USE_SUPPRESSION_MILLIS) {
            snapshot.recordItemUse(item, System.currentTimeMillis());
            ExtendedTargetHud.TrackedItem trackedItem = snapshot.trackedItems.get(item);
            if (trackedItem != null) {
               trackedItem.cooldownOverrideSeconds = dragonStructure ? 15 : 10;
            }
         }
      }
   }

   public static void selectTarget(PlayerEntity player) {
      targetUuid = player.getUuid();
   }

   private static float animatedRelativeBearing(MinecraftClient client, ExtendedTargetHud.TargetSnapshot snapshot) {
      float bearing = 0.0F;
      if (client.player != null && snapshot.position != null) {
         double deltaX = snapshot.position.x - client.player.getX();
         double deltaZ = snapshot.position.z - client.player.getZ();
         if (deltaX * deltaX + deltaZ * deltaZ >= (0.01D)) {
            bearing = (float)Math.toDegrees(Math.atan2(deltaZ, deltaX)) - (90.0F) - RotationAngles.cameraYaw();
         }
      }

      bearingAnimation.update();
      bearingAnimation.animateTo(bearingAnimation.floatValue() + MathHelper.wrapDegrees(bearing - bearingAnimation.floatValue()), (0.25D), Easings.QUART_OUT);
      return bearingAnimation.floatValue();
   }

   private static void drawTrackedItems(ExtendedTargetHud.TargetSnapshot snapshot, float x, float y, float alpha) {
      if (!(alpha <= (0.001F)) && !snapshot.trackedItems.isEmpty()) {
         float openingOffset = HudPanelRenderer.openingOffset(alpha);
         java.util.ArrayList<com.mouseclient.util.time.ExtendedTargetHud.TrackedItem> items = new ArrayList<>(snapshot.trackedItems.values());

         for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            ExtendedTargetHud.TrackedItem trackedItem = (ExtendedTargetHud.TrackedItem)items.get(items.size() - 1 - itemIndex);
            float itemX = x + itemIndex * (15.0F) - openingOffset;
            HudPanelRenderer.drawPanel(itemX, y, (13.0F), (13.0F), alpha, (3.0F));
            GuiItemRenderer.render(
               trackedItem.stack,
               Math.round((itemX + (2.1F)) * 2.0F) / 2.0F,
               Math.round((y + (2.1F)) * 2.0F) / 2.0F,
               (8.8F),
               ItemRenderOptions.noDecorations(alpha).glint(GlintMode.OFF)
            );
            if (trackedItem.stack.getCount() > 1) {
               GuiTextRenderer.draw(
                  GuiFont.SEMI_BOLD,
                  String.valueOf(trackedItem.stack.getCount()),
                  itemX + (13.0F) - (1.5F) - GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, String.valueOf(trackedItem.stack.getCount()), (5.0F)),
                  y + (7.0F),
                  ITEM_COUNT_FONT_SIZE,
                  GuiVisuals.multiplyAlpha(HudPanelRenderer.primaryTextColor(1.0F), alpha)
               );
            }

            int remainingSeconds = trackedItem.remainingCooldownSeconds(System.currentTimeMillis());
            if (remainingSeconds > 0) {
               String cooldownText = remainingSeconds > 99 ? ("99") : String.valueOf(remainingSeconds);
               float cooldownTextWidth = GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, cooldownText, (6.5F));
               GuiTextRenderer.draw(
                  GuiFont.SEMI_BOLD,
                  cooldownText,
                  itemX + ((13.0F) - cooldownTextWidth) / 2.0F,
                  y + (11.0F),
                  (6.5F),
                  GuiVisuals.multiplyAlpha((-50384), alpha * (0.9F))
               );
            }
         }
      }
   }

   public static void observeItemSound(PlayerEntity localPlayer, double x, double y, double z, String soundId) {
      if (targetUuid != null && targetSnapshots.get(targetUuid) != null) {
         PlayerEntity targetPlayer = null;

         for (PlayerEntity candidate : localPlayer.getEntityWorld().getPlayers()) {
            if (candidate.getUuid().equals(targetUuid)) {
               targetPlayer = candidate;
            }
         }

         if (targetPlayer != null) {
            Item item = localSoundItems.get(soundId);
            if (item == null) {
               Item targetSoundItem = targetSoundItems.get(soundId);
               if (targetSoundItem != null) {
                  if (System.currentTimeMillis() - localItemUseTimes.getOrDefault(targetSoundItem, 0L) >= (2500L)) {
                     if (!(targetPlayer.squaredDistanceTo(x, y, z) > (36.0D))
                        && !(targetPlayer.squaredDistanceTo(x, y, z) > localPlayer.squaredDistanceTo(x, y, z))) {
                        targetSnapshots.get(targetUuid).recordItemUse(targetSoundItem, System.currentTimeMillis());
                     }
                  }
               }
            } else if (System.currentTimeMillis() - localItemUseTimes.getOrDefault(item, 0L) >= (2500L)) {
               if (!(localPlayer.squaredDistanceTo(x, y, z) > (36.0D))
                  && !(localPlayer.squaredDistanceTo(x, y, z) > targetPlayer.squaredDistanceTo(x, y, z))) {
                  targetSnapshots.get(targetUuid).recordItemUse(item, System.currentTimeMillis());
               }
            }
         }
      }
   }

   private static int serverItemPriority(ItemStack stack) {
      for (ServerHelper.ServerItemBind serverItem : ServerHelper.getInstance().itemBinds()) {
         if (serverItem.binding().isVisible() && serverItem.matches(stack)) {
            return 3;
         }
      }

      return 0;
   }

   public static void render(MinecraftClient client, float anchorX, float anchorY) {
      updateDisplayTarget(client);
      ExtendedTargetHud.TargetSnapshot snapshot = targetUuid == null ? null : targetSnapshots.get(targetUuid);
      if (snapshot != null) {
         lastSnapshot = snapshot;
      }

      playerRowAnimation.update();
      playerRowAnimation.animateTo(snapshot != null ? 1.0 : 0.0, (0.6D), Easings.QUART_OUT);
      itemRowAnimation.update();
      itemRowAnimation.animateTo(snapshot != null && !snapshot.trackedItems.isEmpty() ? 1.0 : 0.0, (0.6D), Easings.QUART_OUT);
      visibilityAnimation.update();
      visibilityAnimation.animateTo(snapshot == null && !(playerRowAnimation.floatValue() > (0.001F)) && !(itemRowAnimation.floatValue() > (0.001F)) ? 0.0 : 1.0, (0.4D), Easings.QUART_OUT);
      if (lastSnapshot != null) {
         ExtendedTargetHud.TargetSnapshot displaySnapshot = lastSnapshot;
         float rowWidth = playerRowWidth(displaySnapshot);
         float healthWidth = healthChipWidth(displaySnapshot);
         float contentWidth = Math.max(rowWidth + 2.0F + healthWidth, displaySnapshot.trackedItems.size() * (15.0F) - 2.0F);
         float desiredWidth = Math.max(Math.max(HudPanelRenderer.headerWidth(("Target")), contentWidth), 1.0F);
         panelWidthAnimation.update();
         panelWidthAnimation.animateTo(desiredWidth, (0.6D), Easings.QUART_OUT);
         headerWidthAnimation.update();
         headerWidthAnimation.animateTo(desiredWidth, (0.6D), Easings.QUART_OUT);
         healthWidthAnimation.update();
         healthWidthAnimation.animateTo(healthWidth, HEALTH_WIDTH_DURATION, Easings.QUART_OUT);
         float panelWidth = panelWidthAnimation.floatValue();
         float panelHeight = Math.max((13.0F) + (14.0F) * playerRowAnimation.floatValue() + (17.0F) * itemRowAnimation.floatValue(), 1.0F);
         float visibility = visibilityAnimation.floatValue();
         if (!(visibility <= (0.001F))) {
            float[] anchor = HudLayout.resolveAnchor(("Target"), anchorX, anchorY);
            HudLayout.registerBounds(("Target"), anchor[0], anchor[1], panelWidth, panelHeight);
            HudRenderContext.pushIdentityScale(anchor[0] + panelWidth / 2.0F, anchor[1] + panelHeight / 2.0F, visibility);
            HudPanelRenderer.drawHeader(anchor[0], anchor[1], Math.max(headerWidthAnimation.floatValue(), 1.0F), visibility, HudIcon.ENVIRONMENT, ("Target"));
            drawPlayerRow(client, displaySnapshot, anchor[0], anchor[1] + (15.0F), rowWidth, healthWidth, playerRowAnimation.floatValue() * visibility);
            drawTrackedItems(displaySnapshot, anchor[0], anchor[1] + (15.0F) + (13.0F) * playerRowAnimation.floatValue() - 1.0F, itemRowAnimation.floatValue() * visibility);
            HudRenderContext.popTransform();
         }
      }
   }

   private static int armorCount(ExtendedTargetHud.TargetSnapshot snapshot) {
      int count = 0;

      for (int var2 = 0; var2 < 4; var2++) {
         if (!snapshot.equipment[var2].isEmpty()) {
            count++;
         }
      }

      return count;
   }

   private static int cooldownSeconds(ItemStack stack) {
      return isHealingPotion(stack) ? 15 : itemCooldownSeconds.getOrDefault(stack.getItem(), 0);
   }

   private static boolean isHealingPotion(ItemStack stack) {
      for (StatusEffectInstance effect : stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT).getEffects()) {
         if (effect.getEffectType().getIdAsString().equals(("minecraft:instant_health"))) {
            return true;
         }
      }

      return false;
   }

   private static float healthChipWidth(ExtendedTargetHud.TargetSnapshot snapshot) {
      return HudPanelRenderer.textChipWidth(GuiTextRenderer.getWidth(GuiFont.SEMI, (int)snapshot.health + "HP", (6.0F)));
   }

   private static void drawPlayerRow(MinecraftClient client, ExtendedTargetHud.TargetSnapshot snapshot, float x, float y, float rowWidth, float healthWidth, float alpha) {
      if (!(alpha <= (0.001F))) {
         float openingOffset = HudPanelRenderer.openingOffset(alpha);
         float healthX = x - openingOffset;
         float rowX = x + healthWidthAnimation.floatValue() + 2.0F - openingOffset;
         rowWidth = Math.max(rowWidth, headerWidthAnimation.floatValue() - healthWidthAnimation.floatValue() - 2.0F);
         float textY = HudPanelRenderer.rowTextY(y);
         float iconY = Math.round((y + (1.5F)) * 2.0F) / 2.0F;
         HudPanelRenderer.drawPanel(rowX, y, rowWidth, (11.0F), alpha);
         if (snapshot.skinTexture != null) {
            Render2D.drawNearestImageRegion(
               snapshot.skinTexture.toString(),
               Math.round((rowX + 2.0F) * 2.0F) / 2.0F,
               iconY,
               (8.0F),
               (8.0F),
               (3.0F),
               (0.25F),
               (0.125F),
               (0.125F),
               (0.25F),
               (0.25F),
               GuiVisuals.multiplyAlpha(-1, alpha)
            );
         }

         GuiTextRenderer.draw(
            GuiFont.SEMI, snapshot.name, rowX + (12.5F), textY, (6.0F), GuiVisuals.multiplyAlpha(HudPanelRenderer.primaryTextColor(1.0F), alpha)
         );
         float equipmentX = rowX + (12.5F) + GuiTextRenderer.getWidth(GuiFont.SEMI, snapshot.name, (6.0F)) + EQUIPMENT_SPACING;

         for (int slotIndex = 0; slotIndex < 4; slotIndex++) {
            if (!snapshot.equipment[slotIndex].isEmpty()) {
               GuiItemRenderer.render(
                  snapshot.equipment[slotIndex], Math.round((equipmentX - (0.5F)) * 2.0F) / 2.0F, iconY, (8.0F), ItemRenderOptions.noDecorations(alpha).glint(GlintMode.OFF)
               );
               equipmentX += (7.0F);
            }
         }

         HudPanelRenderer.drawPanel(healthX, y, healthWidth, (11.0F), alpha);
         GuiTextRenderer.draw(
            GuiFont.SEMI, (int)snapshot.health + "HP", healthX + (4.0F), textY, (6.0F), GuiVisuals.multiplyAlpha(HudPanelRenderer.primaryTextColor(1.0F), alpha)
         );
      }
   }

   private ExtendedTargetHud() {
   }

   private static float playerRowWidth(ExtendedTargetHud.TargetSnapshot snapshot) {
      int equippedArmorCount = armorCount(snapshot);
      return (12.5F)
         + GuiTextRenderer.getWidth(GuiFont.SEMI, snapshot.name, (6.0F))
         + (equippedArmorCount > 0 ? (3.0F) + equippedArmorCount * (7.0F) - 1.0F : 0.0F)
         + (4.0F);
   }

   private static final class TrackedItem {
      public final ItemStack stack;
      public long usedAtMillis;
      public long removedAtMillis;
      public int equipmentIndex;
      public int cooldownOverrideSeconds;

      public int remainingCooldownSeconds(long nowMillis) {
         int cooldownSeconds = this.cooldownOverrideSeconds > 0 ? this.cooldownOverrideSeconds : ExtendedTargetHud.cooldownSeconds(this.stack);
         return cooldownSeconds != 0 && this.usedAtMillis != 0L ? Math.max((int)Math.ceil((cooldownSeconds * (1000L) - (nowMillis - this.usedAtMillis)) / (1000.0D)), 0) : 0;
      }

      public TrackedItem(ItemStack stack, int equipmentIndex) {
         this.stack = stack.copy();
         this.stack.setDamage(0);
         this.equipmentIndex = equipmentIndex;
      }
   }

   private static final class TargetSnapshot {
      public final LinkedHashMap<Item, ExtendedTargetHud.TrackedItem> trackedItems = new LinkedHashMap<>();
      public final ItemStack[] equipment = new ItemStack[6];
      public String name = "";
      public Identifier skinTexture;
      public float health;
      public Vec3d position;
      public boolean consumptionRecorded;
      public int unusedCounter;
      public ItemStack unusedStack = ItemStack.EMPTY;

      public void update(MinecraftClient client, PlayerEntity player, long nowMillis) {
         this.name = player.getName().getString();
         if (player instanceof AbstractClientPlayerEntity clientPlayer) {
            this.skinTexture = clientPlayer.getSkin().body().texturePath();
         }

         this.health = CombatTargeting.displayedHealth(player);
         this.position = player.getEntityPos();

         for (int armorIndex = 0; armorIndex < 4; armorIndex++) {
            this.equipment[armorIndex] = player.getEquippedStack(ExtendedTargetHud.ARMOR_SLOTS[armorIndex]);
         }

         for (ExtendedTargetHud.TrackedItem trackedItem : this.trackedItems.values()) {
            if (trackedItem.removedAtMillis == 0L
               && trackedItem.stack.getCount() == 1
               && this.equipment[trackedItem.equipmentIndex].isOf(trackedItem.stack.getItem())
               && (trackedItem.equipmentIndex == 4 ? player.getMainHandStack() : player.getOffHandStack()).isEmpty()) {
               trackedItem.removedAtMillis = nowMillis;
            }
         }

         this.equipment[4] = player.getMainHandStack();
         this.equipment[5] = player.getOffHandStack();
         if (player.isUsingItem() && !player.getActiveItem().isEmpty()) {
            if (player.getItemUseTimeLeft() <= 1 && !this.consumptionRecorded) {
               this.consumptionRecorded = true;
               this.recordConsumedStack(player.getActiveItem().copyWithCount(1), nowMillis);
            }
         } else {
            this.consumptionRecorded = false;
         }

         for (int handIndex = 4; handIndex < 6; handIndex++) {
            if (ExtendedTargetHud.serverItemPriority(this.equipment[handIndex]) > 0 || ExtendedTargetHud.isTrackedConsumable(this.equipment[handIndex])) {
               this.trackHeldStack(client, player, this.equipment[handIndex], handIndex, nowMillis);
            }
         }

         this.trackedItems.values().removeIf(trackedItemToRemove -> trackedItemToRemove.removedAtMillis != 0L && nowMillis - trackedItemToRemove.removedAtMillis > (10000L) && trackedItemToRemove.remainingCooldownSeconds(nowMillis) <= 0);
      }

      public TargetSnapshot(UUID uuid) {
         Arrays.fill(this.equipment, ItemStack.EMPTY);
      }

      private void trackHeldStack(MinecraftClient client, PlayerEntity player, ItemStack stack, int equipmentIndex, long nowMillis) {
         ExtendedTargetHud.TrackedItem trackedItem = this.trackedItems.remove(stack.getItem());
         if (trackedItem != null) {
            if (stack.getCount() < trackedItem.stack.getCount()
               && ExtendedTargetHud.cooldownSeconds(stack) > 0
               && client.world
                  .getEntitiesByClass(ItemEntity.class, player.getBoundingBox().expand((2.0D)), droppedItem -> droppedItem.getStack().isOf(stack.getItem()))
                  .isEmpty()) {
               trackedItem.usedAtMillis = nowMillis;
            }

            trackedItem.stack.setCount(stack.getCount());
            trackedItem.equipmentIndex = equipmentIndex;
            trackedItem.removedAtMillis = 0L;
            this.trackedItems.put(stack.getItem(), trackedItem);
         } else {
            if (this.trackedItems.size() >= 9) {
               for (Item item : new ArrayList<>(this.trackedItems.keySet())) {
                  if (ExtendedTargetHud.serverItemPriority(this.trackedItems.get(item).stack) < ExtendedTargetHud.serverItemPriority(stack)) {
                     this.trackedItems.remove(item);
                     break;
                  }
               }

               if (this.trackedItems.size() >= 9) {
                  return;
               }
            }

            this.trackedItems.put(stack.getItem(), new ExtendedTargetHud.TrackedItem(stack, equipmentIndex));
         }
      }

      public void recordItemUse(Item item, long nowMillis) {
         this.recordConsumedStack(new ItemStack(item), nowMillis);
      }

      public void recordConsumedStack(ItemStack stack, long nowMillis) {
         if (ExtendedTargetHud.isHealingPotion(stack) && ExtendedTargetHud.serverItemPriority(stack) <= 0
            || stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)
            || stack.isOf(Items.GOLDEN_APPLE)
            || stack.isOf(Items.GOLDEN_CARROT)
            || ExtendedTargetHud.serverItemPriority(stack) > 0 && !stack.isOf(Items.POTION) && !stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) {
            ExtendedTargetHud.TrackedItem trackedItem = this.trackedItems.remove(stack.getItem());
            if (trackedItem == null) {
               trackedItem = new ExtendedTargetHud.TrackedItem(stack, 4);
            } else if (ExtendedTargetHud.isTrackedConsumable(trackedItem.stack) && trackedItem.stack.getCount() > 1) {
               trackedItem.stack.setCount(trackedItem.stack.getCount() - 1);
            }

            trackedItem.usedAtMillis = nowMillis;
            trackedItem.removedAtMillis = nowMillis;
            if (this.trackedItems.size() >= 9) {
               this.trackedItems.remove(this.trackedItems.keySet().iterator().next());
            }

            this.trackedItems.put(stack.getItem(), trackedItem);
         }
      }
   }
}
