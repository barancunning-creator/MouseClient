package com.mouseclient.modules.impl.player;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.ContainerComponentAccessor;
import com.mouseclient.access.ItemCooldownAccess;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.MovementFreezeController;
import com.mouseclient.modules.impl.movement.ScreenWalk;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.SettingSeparator;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.ItemCooldownHud;
import com.mouseclient.util.time.ToastNotifications;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.ExtendedTargetHud;
import com.mouseclient.util.time.GuiFont;
import com.mouseclient.util.time.WaypointManager;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Generated;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.BundleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BundleItemSelectedC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

@ModuleInfo(name = "ServerHelper", description = "Бинды предметов для SpookyTime, FunTime и HolyWorld.", category = ModuleCategory.PLAYER)
public final class ServerHelper extends Module {
   private static ServerHelper INSTANCE;
   public ModeSetting server;
   public BooleanSetting smartScroll;
   public MultiSelectSetting blockedScrollSlots;
   public BooleanSetting autoPitch;
   public BooleanSetting showArrows;
   public BooleanSetting eventWaypoint;
   private static final Pattern EVENT_NAME_PATTERN = Pattern.compile(("(?m)\\|\\|\\|\\s+\\[([^\\]]+)]|^\\s*\\[\\d+]([^:\\n]+):"));
   private static final Pattern EVENT_COORDINATES_PATTERN = Pattern.compile(("(?:Координаты: \\[|/gps )(-?\\d{1,7})[ ,]+(-?\\d{1,7})[ ,]+(-?\\d{1,7})"));
   private static final Pattern EVENT_SUMMONER_PATTERN = Pattern.compile(("Призван игроком: ([^\\n|]+)"));
   private static final Pattern EVENT_LOOT_PATTERN = Pattern.compile(("Уровень лута: ([^\\n|]+)"));
   private final ArrayList<ServerHelper.ServerItemBind> itemBinds = new ArrayList<>();
   private long lastCooldownToastMillis;
   private long pitchRequestDeadlineMillis;
   private ServerHelper.ServerItemBind shulkerRequest;
   private int shulkerSlot;
   private int shulkerPhase;
   private int shulkerPhaseTicks;
   private final EventListener<PlayerTickEvent> playerBindListener = event -> this.updateBinds();
   private final EventListener<HudRenderEvent> hudBindListener = event -> this.updateBinds();
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isOutgoing()
         && minecraftClient.player != null
         && ScreenWalk.getInstance().isSpookyTimeConfigured()
         && System.currentTimeMillis() < this.pitchRequestDeadlineMillis
         && event.getPacket() instanceof PlayerInteractItemC2SPacket usePacket) {
         event.setPacket(new PlayerInteractItemC2SPacket(usePacket.getHand(), usePacket.getSequence(), usePacket.getYaw(), (90.0F)));
         this.pitchRequestDeadlineMillis = 0L;
      } else {
         if (event.isIncoming() && this.eventWaypoint.getValue() && event.getPacket() instanceof GameMessageS2CPacket chatPacket) {
            Matcher eventName = EVENT_NAME_PATTERN.matcher(Formatting.strip(chatPacket.content().getString()));
            Matcher coordinates = EVENT_COORDINATES_PATTERN.matcher(Formatting.strip(chatPacket.content().getString() + chatPacket.content().toString()));
            Matcher summoner = EVENT_SUMMONER_PATTERN.matcher(Formatting.strip(chatPacket.content().getString()));
            Matcher loot = EVENT_LOOT_PATTERN.matcher(Formatting.strip(chatPacket.content().getString()));
            if (eventName.find()) {
               minecraftClient.execute(
                  () -> this.addEventWaypoint(
                     (eventName.group(1) == null ? eventName.group(2) : eventName.group(1)).trim(),
                     coordinates.find() ? new BlockPos(Integer.parseInt(coordinates.group(1)), Integer.parseInt(coordinates.group(2)), Integer.parseInt(coordinates.group(3))) : null,
                     summoner.find() ? "Призван: " + summoner.group(1).trim() : null,
                     loot.find() ? "Лут: " + loot.group(1).trim() : null
                  )
               );
            }
         }

         if (event.isIncoming() && event.getPacket() instanceof OverlayMessageS2CPacket overlayPacket && overlayPacket.text().getString().contains(("будет доступн"))) {
            Matcher cooldownMessage = Pattern.compile(("(.*через )(\\d[\\d.,]*)( ?сек.*)")).matcher(Formatting.strip(overlayPacket.text().getString()));
            if (cooldownMessage.matches() && System.currentTimeMillis() - this.lastCooldownToastMillis >= (2000L)) {
               this.lastCooldownToastMillis = System.currentTimeMillis();
               minecraftClient.execute(
                  () -> {
                     ToastNotifications.showStyledToast(
                        this.category.iconGlyph(),
                        ("Кулдаун"),
                        Text.literal(cooldownMessage.group(1))
                           .setStyle(Style.EMPTY.withColor(Formatting.WHITE))
                           .append(Text.literal(cooldownMessage.group(2) + cooldownMessage.group(3)).setStyle(Style.EMPTY.withColor(Formatting.RED))),
                        true
                     );
                     ItemCooldownHud.recordReportedCooldown(("Исцел/Святая вода"), Items.SPLASH_POTION, Float.parseFloat(cooldownMessage.group(2).replace(',', '.')));
                  }
               );
            }
         }
      }
   };
   private final EventListener<HudRenderEvent> arrowRenderListener = event -> {
      if (minecraftClient.player != null && this.showArrows.getValue()) {
         int arrowCount = 0;

         for (int inventorySlot = 0; inventorySlot < minecraftClient.player.getInventory().size(); inventorySlot++) {
            if (minecraftClient.player.getInventory().getStack(inventorySlot).isIn(ItemTags.ARROWS)) {
               arrowCount += minecraftClient.player.getInventory().getStack(inventorySlot).getCount();
            }
         }

         String countText = String.valueOf(arrowCount);
         float scaleRatio = GuiScale.getWindowScale() / GuiScale.getBaseScale();
         float fontSize = (6.0F) * scaleRatio;
         float outlineOffset = (0.7F) * scaleRatio;

         for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            if (minecraftClient.player.getInventory().getStack(hotbarSlot).isOf(Items.BOW)
               || minecraftClient.player.getInventory().getStack(hotbarSlot).isOf(Items.CROSSBOW)) {
               float x = (event.getWidth() / 2.0F - (96.0F) + hotbarSlot * (20.0F) + (16.0F)) * scaleRatio
                  - GuiTextRenderer.getWidth(GuiFont.BOLD, countText, fontSize);
               float y = (event.getHeight() - (19.0F) + (6.0F)) * scaleRatio - fontSize;
               GuiTextRenderer.draw(GuiFont.BOLD, countText, x - outlineOffset, y, fontSize, (-16777216));
               GuiTextRenderer.draw(GuiFont.BOLD, countText, x + outlineOffset, y, fontSize, (-16777216));
               GuiTextRenderer.draw(GuiFont.BOLD, countText, x, y - outlineOffset, fontSize, (-16777216));
               GuiTextRenderer.draw(GuiFont.BOLD, countText, x, y + outlineOffset, fontSize, (-16777216));
               GuiTextRenderer.draw(GuiFont.BOLD, countText, x, y, fontSize, -1);
            }
         }
      }
   };
   private final EventListener<PlayerTickEvent> shulkerExtractListener = event -> {
      if (this.shulkerRequest != null && minecraftClient.player != null && minecraftClient.interactionManager != null) {
         this.shulkerPhaseTicks++;
         switch (this.shulkerPhase) {
            case 0:
               if (MovementFreezeController.getInstance().getRemainingTicks() >= 1) {
                  return;
               }

               if (minecraftClient.player.currentScreenHandler != minecraftClient.player.playerScreenHandler
                  || !this.shulkerContainsItem(minecraftClient.player.getInventory().getStack(this.shulkerSlot), this.shulkerRequest)) {
                  this.shulkerRequest = null;
                  return;
               }

               minecraftClient.interactionManager
                  .clickSlot(
                     minecraftClient.player.playerScreenHandler.syncId,
                     this.shulkerSlot < 9 ? this.shulkerSlot + 36 : this.shulkerSlot,
                     1,
                     SlotActionType.PICKUP,
                     minecraftClient.player
                  );
               this.shulkerPhase = 1;
               this.shulkerPhaseTicks = 0;
               break;
            case 1:
               ScreenHandler openedHandler = minecraftClient.player.currentScreenHandler;
               if (openedHandler != minecraftClient.player.playerScreenHandler && openedHandler.slots.size() > 36) {
                  if (!minecraftClient.player.playerScreenHandler.getCursorStack().isEmpty()
                     && minecraftClient.player.getInventory().getStack(this.shulkerSlot).isEmpty()) {
                     minecraftClient.player.getInventory().setStack(this.shulkerSlot, minecraftClient.player.playerScreenHandler.getCursorStack());
                     minecraftClient.player.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
                  }

                  if (this.findContainerItem(openedHandler) == -1) {
                     if (this.shulkerPhaseTicks <= 5) {
                        return;
                     }

                     minecraftClient.player.closeHandledScreen();
                     this.shulkerRequest = null;
                     return;
                  }

                  MovementFreezeController.getInstance().freezeForTicks(1);
                  this.shulkerPhase = 2;
                  break;
               }

               if (this.shulkerPhaseTicks <= 20) {
                  return;
               }

               if (!minecraftClient.player.playerScreenHandler.getCursorStack().isEmpty()) {
                  minecraftClient.interactionManager
                     .clickSlot(
                        minecraftClient.player.playerScreenHandler.syncId,
                        this.shulkerSlot < 9 ? this.shulkerSlot + 36 : this.shulkerSlot,
                        0,
                        SlotActionType.PICKUP,
                        minecraftClient.player
                     );
               }

               ToastNotifications.showStyledToast(this.category.iconGlyph(), ("Шалкер"), Text.literal(("не открылся")).setStyle(Style.EMPTY.withColor(Formatting.RED)), true);
               this.shulkerRequest = null;
               return;
            case 2:
               if (MovementFreezeController.getInstance().getRemainingTicks() >= 1) {
                  return;
               }

               ScreenHandler extractHandler = minecraftClient.player.currentScreenHandler;
               int containerSlotCount = extractHandler.slots.size() - 36;
               int itemSlot = this.findContainerItem(extractHandler);
               int emptySlot = InventoryActions.findEmptySlot(9, 35) != -1 ? InventoryActions.findEmptySlot(9, 35) : InventoryActions.findEmptySlot(0, 8);
               if (extractHandler != minecraftClient.player.playerScreenHandler && itemSlot != -1 && emptySlot != -1 && extractHandler.getCursorStack().isEmpty()) {
                  boolean directItem = this.shulkerRequest.matches(extractHandler.getSlot(itemSlot).getStack());
                  int bundleIndex = directItem ? -1 : this.findBundleItem(extractHandler.getSlot(itemSlot).getStack(), this.shulkerRequest);
                  if (!directItem && BundleItem.getSelectedStackIndex(extractHandler.getSlot(itemSlot).getStack()) != bundleIndex) {
                     extractHandler.selectBundleStack(itemSlot, bundleIndex);
                     minecraftClient.player.networkHandler.sendPacket(new BundleItemSelectedC2SPacket(itemSlot, bundleIndex));
                  }

                  minecraftClient.interactionManager.clickSlot(extractHandler.syncId, itemSlot, directItem ? 0 : 1, SlotActionType.PICKUP, minecraftClient.player);
                  extractHandler.nextRevision();
                  minecraftClient.interactionManager
                     .clickSlot(extractHandler.syncId, emptySlot < 9 ? containerSlotCount + 27 + emptySlot : containerSlotCount + emptySlot - 9, 0, SlotActionType.PICKUP, minecraftClient.player);
                  minecraftClient.player.closeHandledScreen();
                  this.shulkerPhase = 3;
                  break;
               }

               if (extractHandler != minecraftClient.player.playerScreenHandler) {
                  minecraftClient.player.closeHandledScreen();
               }

               this.shulkerRequest = null;
               return;
            default:
               ServerHelper.ServerItemBind completedRequest = this.shulkerRequest;
               this.shulkerRequest = null;
               this.useBoundItem(completedRequest);
         }
      }
   };

   private int findBundleItem(ItemStack bundle, ServerHelper.ServerItemBind itemBinding) {
      if (bundle.get(DataComponentTypes.BUNDLE_CONTENTS) == null) {
         return -1;
      }

      for (int bundleIndex = 0; bundleIndex < bundle.get(DataComponentTypes.BUNDLE_CONTENTS).size(); bundleIndex++) {
         if (itemBinding.matches(bundle.get(DataComponentTypes.BUNDLE_CONTENTS).get(bundleIndex))) {
            return bundleIndex;
         }
      }

      return -1;
   }

   private void addEventWaypoint(String eventName, BlockPos position, String summoner, String loot) {
      if (position == null) {
         switch (eventName.toLowerCase()) {
            case ("сундук смерти"):
               WaypointManager.getInstance().addEventWaypoint(eventName, new BlockPos(-155, 64, 205), 300, 0, summoner, loot);
               break;
            case ("адская резня"):
               WaypointManager.getInstance().addEventWaypoint(eventName, new BlockPos(48, 87, 73), 180, 120, summoner, loot);
         }
      } else {
         switch (eventName.toLowerCase()) {
            case ("мистический сундук"):
               WaypointManager.getInstance().addEventWaypoint(eventName, position, 300, 0, summoner, loot);
               break;
            case ("вулкан"):
               WaypointManager.getInstance().addEventWaypoint(eventName, position, 300, 120, summoner, loot);
               break;
            case ("метеоритный дождь"):
               WaypointManager.getInstance().addEventWaypoint(eventName, position, 360, 0, summoner, loot);
               break;
            case ("маяк убийца"):
               WaypointManager.getInstance().addEventWaypoint(eventName, position, 360, 0, summoner, loot);
               break;
            case ("мистический алтарь"):
               WaypointManager.getInstance().addEventWaypoint(eventName, position, 360, 0, summoner, loot);
               break;
            case ("загадочный маяк"):
               WaypointManager.getInstance().addEventWaypoint(eventName, position, 60, 180, summoner, loot);
         }
      }
   }

   public int scrollSlot(int previousSlot, int requestedSlot) {
      if (this.isEnabled() && this.smartScroll.getValue()) {
         int direction = Math.floorMod(requestedSlot - previousSlot, 9) <= 4 ? 1 : -1;
         int candidateSlot = requestedSlot;

         for (int checkedSlots = 0; checkedSlots < 9; checkedSlots++) {
            if (!this.blockedScrollSlots.isSelectedAt(candidateSlot)) {
               return candidateSlot;
            }

            candidateSlot = Math.floorMod(candidateSlot + direction, 9);
         }

         return previousSlot;
      } else {
         return requestedSlot;
      }
   }

   private void notifyCooldown(ItemStack stack) {
      ToastNotifications.showStyledToast(
         this.category.iconGlyph(),
         Formatting.strip(stack.getName().getString()),
         Text.literal(("в кд ещё "))
            .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
            .append(
               Text.literal(
                     String.format(
                        Locale.US,
                        ("%.1fс"),
                        ((ItemCooldownAccess)minecraftClient.player.getItemCooldownManager()).remainingCooldownTicks(stack) / (20.0F)
                     )
                  )
                  .setStyle(Style.EMPTY.withColor(Formatting.RED))
            ),
         true
      );
   }

   private int findContainerItem(ScreenHandler handler) {
      for (int containerSlot = 0; containerSlot < handler.slots.size() - 36; containerSlot++) {
         if (this.shulkerRequest.matches(handler.getSlot(containerSlot).getStack())
            || this.findBundleItem(handler.getSlot(containerSlot).getStack(), this.shulkerRequest) != -1) {
            return containerSlot;
         }
      }

      return -1;
   }

   public void preparePotionPitch(String potionName) {
      if (this.isEnabled() && this.autoPitch.getValue() && minecraftClient.player != null && !potionName.toLowerCase().matches((".*(радиац|снотворн).*"))) {
         this.pitchRequestDeadlineMillis = System.currentTimeMillis() + (ScreenWalk.getInstance().isSpookyTimeConfigured() ? 500 : 200);
         if (!ScreenWalk.getInstance().isSpookyTimeConfigured()) {
            RotationController.getInstance()
               .requestForDuration(new RotationAngles(minecraftClient.player.getYaw(), (90.0F)), new RotationLimits((355.0F), (355.0F), (28.0F), (28.0F)), (300L));
         }
      }
   }

   private void prepareBoundItemPitch(ServerHelper.ServerItemBind itemBinding) {
      if (itemBinding.nameFragment != null && itemBinding.item == null) {
         this.preparePotionPitch(itemBinding.nameFragment);
      }
   }

   private void updateBinds() {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null && minecraftClient.currentScreen == null) {
         for (ServerHelper.ServerItemBind activeBinding : this.itemBinds) {
            if (activeBinding.binding.isVisible() && activeBinding.binding.getBinding().isPressed(minecraftClient.getWindow().getHandle()) && !activeBinding.keyWasPressed) {
               this.useBoundItem(activeBinding);
            }

            activeBinding.keyWasPressed = activeBinding.binding.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         }
      } else {
         for (ServerHelper.ServerItemBind inactiveBinding : this.itemBinds) {
            inactiveBinding.keyWasPressed = inactiveBinding.binding.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         }
      }
   }

   public ArrayList<ServerHelper.ServerItemBind> itemBinds() {
      return this.itemBinds;
   }

   private void notifyItemUsed(ItemStack stack) {
      ToastNotifications.showStyledToast(
         this.category.iconGlyph(),
         Formatting.strip(stack.getName().getString()),
         Text.literal(("использовано")).setStyle(Style.EMPTY.withColor(Formatting.GREEN)),
         true
      );
   }

   private void addPotionBind(String name, String... effectIds) {
      BindSetting bindSetting = this.addSetting(new BindSetting(name, ("Использует предмет по нажатию кнопки.")));
      bindSetting.setVisibility(() -> !this.server.isSelected(("HolyWorld")));
      this.itemBinds.add(new ServerHelper.ServerItemBind(name, bindSetting, effectIds));
   }

   private void addHolyWorldBind(String name, Item item, String nameFragment) {
      BindSetting bindSetting = this.addSetting(new BindSetting(name, ("Использует предмет по нажатию кнопки.")));
      bindSetting.setVisibility(() -> this.server.isSelected(("HolyWorld")));
      this.itemBinds.add(new ServerHelper.ServerItemBind(nameFragment, item, bindSetting));
   }

   private void addItemBind(String name, Item item, String unusedItemId) {
      BindSetting bindSetting = this.addSetting(new BindSetting(name, ("Использует предмет по нажатию кнопки.")));
      bindSetting.setVisibility(() -> !this.server.isSelected(("HolyWorld")));
      this.itemBinds.add(new ServerHelper.ServerItemBind(item, bindSetting));
   }

   public void useBoundItem(ServerHelper.ServerItemBind itemBinding) {
      if (!ItemUseQueue.getInstance().hasPendingUses() && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
         int inventorySlot = InventoryActions.findSlot(stack -> itemBinding.matches(stack));
         if (inventorySlot != -1) {
            if (minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getInventory().getStack(inventorySlot))) {
               this.notifyCooldown(minecraftClient.player.getInventory().getStack(inventorySlot));
            } else {
               this.prepareBoundItemPitch(itemBinding);
               ExtendedTargetHud.recordLocalItemUse(minecraftClient.player.getInventory().getStack(inventorySlot).getItem());
               this.notifyItemUsed(minecraftClient.player.getInventory().getStack(inventorySlot));
               ItemUseQueue.getInstance().use(minecraftClient.player.getInventory().getStack(inventorySlot).copy(), true);
            }
         } else {
            int bundleSlot = InventoryActions.findSlot(bundleCandidate -> this.findBundleItem(bundleCandidate, itemBinding) != -1);
            if (bundleSlot == -1) {
               int containerSlot = InventoryActions.findSlot(shulkerCandidate -> this.shulkerContainsItem(shulkerCandidate, itemBinding));
               if (containerSlot != -1) {
                  if (InventoryActions.findEmptySlot(0, 35) == -1) {
                     ToastNotifications.showStyledToast(
                        this.category.iconGlyph(), ("Шалкер"), Text.literal(("Нет свободного слота под предмет из мешка")).setStyle(Style.EMPTY.withColor(Formatting.RED)), true
                     );
                  } else if (this.shulkerRequest == null
                     && minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
                     this.shulkerRequest = itemBinding;
                     this.shulkerSlot = containerSlot;
                     this.shulkerPhase = 0;
                     this.shulkerPhaseTicks = 0;
                     MovementFreezeController.getInstance().freezeForTicks(1);
                  }
               } else {
                  ToastNotifications.showStyledToast(
                     this.category.iconGlyph(), itemBinding.binding().name, Text.literal(("нет в инвентаре")).setStyle(Style.EMPTY.withColor(Formatting.RED)), true
                  );
               }
            } else {
               ItemStack bundledStack = minecraftClient.player
                  .getInventory()
                  .getStack(bundleSlot)
                  .get(DataComponentTypes.BUNDLE_CONTENTS)
                  .get(this.findBundleItem(minecraftClient.player.getInventory().getStack(bundleSlot), itemBinding));
               if (minecraftClient.player.getItemCooldownManager().isCoolingDown(bundledStack)) {
                  this.notifyCooldown(bundledStack);
               } else if (InventoryActions.findEmptySlot(0, 35) == -1) {
                  ToastNotifications.showStyledToast(this.category.iconGlyph(), ("Мешок"), Text.literal(("Нет свободного слота под предмет из мешка")).setStyle(Style.EMPTY.withColor(Formatting.RED)), true);
               } else {
                  this.prepareBoundItemPitch(itemBinding);
                  ExtendedTargetHud.recordLocalItemUse(bundledStack.getItem());
                  this.notifyItemUsed(bundledStack);
                  ItemUseQueue.getInstance().use(bundledStack.copy(), true);
               }
            }
         }
      }
   }

   public void requestDownwardPitch() {
      if (minecraftClient.player != null) {
         this.pitchRequestDeadlineMillis = System.currentTimeMillis() + (ScreenWalk.getInstance().isSpookyTimeConfigured() ? 500 : 200);
         if (!ScreenWalk.getInstance().isSpookyTimeConfigured()) {
            RotationController.getInstance()
               .requestForDuration(new RotationAngles(minecraftClient.player.getYaw(), (90.0F)), new RotationLimits((355.0F), (355.0F), (28.0F), (28.0F)), (300L));
         }
      }
   }

   private boolean shulkerContainsItem(ItemStack shulker, ServerHelper.ServerItemBind itemBinding) {
      if (Block.getBlockFromItem(shulker.getItem()) instanceof ShulkerBoxBlock && shulker.get(DataComponentTypes.CONTAINER) != null) {
         for (ItemStack containedStack : ((ContainerComponentAccessor)(Object)shulker.get(DataComponentTypes.CONTAINER)).getItems()) {
            if (itemBinding.matches(containedStack) || this.findBundleItem(containedStack, itemBinding) != -1) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private ServerHelper() {
      this.server = this.addSetting(new ModeSetting(("Сервер"), ("Для какого сервера предметы."), ("SpookyTime"), ("SpookyTime"), ("FunTime"), ("HolyWorld")));
      this.smartScroll = this.addSetting(new BooleanSetting(("Умный скролл"), ("Скролл колёсиком пропускает выбранные слоты, выбор цифрами работает как обычно."), false));
      this.blockedScrollSlots = this.addSetting(
         new MultiSelectSetting(("Слоты без скролла"), ("Какие слоты хотбара пропускать при скролле."), new String[]{("1"), ("2"), ("3"), ("4"), ("5"), ("6"), ("7"), ("8"), ("9")})
      );
      this.blockedScrollSlots.setVisibility(() -> this.smartScroll.getValue());
      this.autoPitch = this.addSetting(new BooleanSetting(("Авто питч"), ("Кидает зелья по бинду себе под ноги, глядя вниз. Радиацию и Снотворное не трогает."), false));
      this.showArrows = this.addSetting(new BooleanSetting(("Показывать стрелы"), ("У лука и арбалета в хотбаре рисует общее число стрел в инвентаре."), false));
      this.eventWaypoint = this.addSetting(new BooleanSetting(("Точка на ивент"), ("Ставит метку по координатам ивента из чата."), false));
      this.addSetting(new SettingSeparator(("Предметы")));
      this.addItemBind(("Снежок"), Items.SNOWBALL, ("snowball"));
      this.addItemBind(("Божья аура"), Items.PHANTOM_MEMBRANE, ("phantom membrane"));
      this.addItemBind(("Трапка"), Items.NETHERITE_SCRAP, ("netherite scrap"));
      this.addItemBind(("Пласт"), Items.DRIED_KELP, ("dried kelp"));
      this.addItemBind(("Явная пыль"), Items.SUGAR, ("sugar"));
      this.addItemBind(("Огненный смерч"), Items.FIRE_CHARGE, ("fire charge"));
      this.addItemBind(("Дезориентация"), Items.ENDER_EYE, ("ender eye"));
      this.addHolyWorldBind(("Взрывная трапка"), Items.PRISMARINE_SHARD, ("взрывная трапка"));
      this.addHolyWorldBind(("Обычная трапка"), Items.POPPED_CHORUS_FRUIT, ("трапка"));
      this.addHolyWorldBind(("Стан"), Items.NETHER_STAR, ("стан"));
      this.addHolyWorldBind(("Взрывная штучка"), Items.FIRE_CHARGE, ("взрывная штучка"));
      this.addHolyWorldBind(("Ком снега"), Items.SNOWBALL, ("ком снега"));
      this.addSetting(new SettingSeparator(("Зелья"))).setVisibility(() -> !this.server.isSelected(("HolyWorld")));
      this.addPotionBind(("Зелье Палладина"), ("minecraft:resistance"), ("minecraft:fire_resistance"), ("minecraft:health_boost"), ("minecraft:invisibility"));
      this.addPotionBind(("Зелье Гнева"), ("minecraft:strength"), ("minecraft:slowness"));
      this.addPotionBind(("Хлопушка"), ("minecraft:slowness"), ("minecraft:speed"), ("minecraft:blindness"), ("minecraft:glowing"));
      this.addPotionBind(("Святая вода"), ("minecraft:regeneration"), ("minecraft:invisibility"), ("minecraft:instant_health"));
      this.addPotionBind(("Зелье Ассасина"), ("minecraft:strength"), ("minecraft:speed"), ("minecraft:haste"), ("minecraft:instant_damage"));
      this.addPotionBind(("Зелье Радиации"), ("minecraft:poison"), ("minecraft:wither"), ("minecraft:slowness"), ("minecraft:hunger"), ("minecraft:glowing"));
      this.addPotionBind(("Снотворное"), ("minecraft:weakness"), ("minecraft:mining_fatigue"), ("minecraft:wither"), ("minecraft:blindness"));
      this.addPotionBind(("Зелье Прыгучести"));
   }

   public static ServerHelper getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ServerHelper();
      }

      return INSTANCE;
   }

   public static final class ServerItemBind {
      private final Item item;
      private final BindSetting binding;
      private final String nameFragment;
      private String[] requiredEffectIds;
      private boolean keyWasPressed;

      private ServerItemBind(Item item, BindSetting binding) {
         this.item = item;
         this.binding = binding;
         this.nameFragment = null;
      }

      @Generated
      public Item item() {
         return this.item;
      }

      @Generated
      public BindSetting binding() {
         return this.binding;
      }

      public boolean matches(ItemStack stack) {
         if (stack.isEmpty()) {
            return false;
         } else if (this.item != null && stack.getItem() != this.item) {
            return false;
         } else {
            return this.requiredEffectIds != null && this.requiredEffectIds.length > 0
               ? this.matchesEffects(stack)
               : this.nameFragment == null || Formatting.strip(stack.getName().getString()).toLowerCase().contains(this.nameFragment);
         }
      }

      private ServerItemBind(String nameFragment, Item item, BindSetting binding) {
         this.item = item;
         this.binding = binding;
         this.nameFragment = nameFragment.toLowerCase();
      }

      private boolean matchesEffects(ItemStack stack) {
         for (String effectId : this.requiredEffectIds) {
            boolean foundEffect = false;

            for (StatusEffectInstance effect : stack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT).getEffects()) {
               if (effect.getEffectType().getIdAsString().equals(effectId)) {
                  foundEffect = true;
               }
            }

            if (!foundEffect) {
               return false;
            }
         }

         return true;
      }

      private ServerItemBind(String nameFragment, BindSetting binding, String... effectIds) {
         this.item = null;
         this.binding = binding;
         this.nameFragment = nameFragment.toLowerCase();
         this.requiredEffectIds = effectIds;
      }
   }
}
