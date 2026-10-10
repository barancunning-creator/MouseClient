package com.mouseclient.network;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.gui.MouseClientClickGUI;
import com.mouseclient.internal.ClientMath;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.MinecraftCooldownAccessor;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.mixin.MinecraftActionInvoker;
import com.mouseclient.access.MerchantSelectionAccess;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.render.avatar.Easings;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.SmoothedAimVector;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.entity.EntityRaycast;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.LookAndOnGround;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import net.minecraft.world.chunk.ChunkSection;

@ModuleInfo(
   name = "AutoVillager",
   description = "Обходит жителей, скупает товар за изумруды, крафтит блоки и сдаёт их в сундук.",
   category = ModuleCategory.MISC
)
public final class AutoVillager extends Module {
   private static AutoVillager INSTANCE;
   private static final Pattern ANARCHY_NUMBER_PATTERN = Pattern.compile(("\\d+"));

   private static final String[] BARITONE_SETUP_COMMANDS = new String[]{
      ("#set allowBreak false"),
      ("#set allowPlace false"),
      ("#set allowSprint true"),
      ("#set sprintInWater true"),
      ("#set allowParkour true"),
      ("#set allowDiagonalDescend true"),
      ("#set allowDownward true"),
      ("#set costHeuristic 3.5"),
      ("#set primaryTimeoutMS 100"),
      ("#set movementTimeoutTicks 40"),
      ("#set freeLook true")
   };
   private static final String[] BARITONE_RESTORE_COMMANDS = new String[]{("#set allowBreak true"), ("#set allowPlace true")};
   public ModeSetting exchange;
   private int anarchyNumber;
   public BooleanSetting useFood;
   private EventSequenceRunner sequenceRunner = new EventSequenceRunner();
   private BlockPos craftingTablePosition;
   private BlockPos craftingApproachPosition;
   private BlockPos storageChestPosition;
   private BlockPos storageApproachPosition;
   private LivingEntity currentMerchant;
   private boolean exchangingEmeralds;
   private boolean craftingGold;
   private boolean unloadingGoods;
   private boolean desiredTradeAvailable;
   private int blockOpenTicks;
   private int craftAttempts;
   private int craftDelayTicks;
   private int emptyUnloadTicks;
   private int unloadTicks;
   private int unloadStallTicks;
   private int lowestRemainingGoods;
   private int reconnectGoodsWaitTicks;
   private boolean waitingToReconnect;
   private boolean waitingToRestart;
   private boolean restartPending;
   private boolean rejoiningAnarchy;
   private boolean eatingUseKeyHeld;
   private BlockPos cycleStartPosition;
   private long reconnectAtMillis;
   private long restartAtMillis;
   private int emeraldsBeforePurchase;
   private int goodsBeforePurchase;
   private int purchaseRetryClicks;
   private int nearAimWaitTicks;
   private int aimWaitTicks;
   private int merchantOpenTicks;
   private int merchantApproachTicks;
   private int movedMerchantRepaths;
   private int merchantSearchTicks;
   private int recipeWaitTicks;
   private int tradeSelectionTicks;
   private int exchangePhase;
   private int exchangeWaitTicks;
   private int emeraldStackClicks;
   private int missingEmeraldTicks;
   private int exchangeRetries;
   private int blockApproachTicks;
   private int blockPathRefreshes;
   private int returnPathTicks;
   private int anarchyJoinTicks;
   private boolean baritoneActive;
   private int stationaryTicks;
   private int stuckRepaths;
   private Vec3d previousPathPosition;
   private float yawVelocity;
   private float pitchVelocity;
   private float aimSpeed;
   private float aimEaseAngle;
   private float aimAcceleration;
   private float yawPitchCoupling;
   private float aimStopAngle;
   private long aimParametersChangedAt;
   private long aimParameterIntervalMillis;
   private long aimStartedAt;
   private boolean returningToStart;
   private long workStartedAt;
   private long cycleStartedAt;
   private Set<UUID> visitedMerchants;
   private Set<UUID> evaluatedMerchants;
   private HashMap<UUID, Integer> merchantTradeYields;
   private boolean preserveTradeEvaluations;
   private boolean merchantApproachReady;
   private BlockPos merchantPathPosition;
   private int shardReadyPolls;
   private int sequenceDelayTicks;
   private final EventListener<PlayerTickEvent> playerTickListener;
   private final EventListener<HudRenderEvent> hudRenderListener;
   private final EventListener<PacketEvent> packetListener;

   private void sendCurrentRotation() {
      minecraftClient.player
         .networkHandler
         .sendPacket(
            new LookAndOnGround(
               minecraftClient.player.getYaw(),
               minecraftClient.player.getPitch(),
               minecraftClient.player.isOnGround(),
               minecraftClient.player.horizontalCollision
            )
         );
   }

   private EventSequenceRunner.EventSequence buildTradingCycle(EventSequenceRunner.EventSequence sequence) {
      sequence.then(PlayerTickEvent.class, event -> {
         for (String setupCommand : BARITONE_SETUP_COMMANDS) {
            ChatMessages.sendBaritoneCommand(setupCommand);
         }

         return true;
      });
      this.appendTradeAvailabilityCheck(sequence);
      this.appendShardReconnect(sequence, () -> !this.desiredTradeAvailable);
      this.appendReturnToStart(sequence);
      this.appendEmeraldExchange(sequence, true);
      int visitCount = this.nearbyMerchantCount() + 2;

      for (int visitIndex = 0; visitIndex < visitCount; visitIndex++) {
         this.appendGoldCrafting(sequence, true);
         this.appendChestUnloading(sequence, true);
         this.appendMerchantPurchase(sequence);
      }

      this.appendGoldCrafting(sequence, false);
      this.appendChestUnloading(sequence, false);
      this.appendShardReconnect(sequence, () -> this.desiredTradeAvailable);
      return sequence;
   }

   private void startBaritonePath(BlockPos position) {
      RotationController.getInstance().reset();
      this.baritoneActive = true;
      ChatMessages.sendBaritoneCommand("#go " + position.getX() + " " + position.getY() + " " + position.getZ());
   }

   private boolean tickBlockApproach(BlockPos position, BlockPos approachPosition) {
      if (!this.blockWithinReach(position) && (approachPosition == null || !(minecraftClient.player.squaredDistanceTo(Vec3d.ofBottomCenter(approachPosition)) < (6.0D)))) {
         this.blockApproachTicks++;
         if (minecraftClient.player.getEntityPos().squaredDistanceTo(this.previousPathPosition) < (0.0025D)) {
            this.stationaryTicks++;
         } else {
            this.stationaryTicks = 0;
         }

         this.previousPathPosition = minecraftClient.player.getEntityPos();
         if (this.blockApproachTicks % 20 == 0 && this.blockPathRefreshes < 6) {
            this.blockPathRefreshes++;
            this.startBaritonePath(approachPosition != null ? approachPosition : position);
         }

         if (this.blockApproachTicks > 12) {
            this.retryStuckPath(approachPosition != null ? approachPosition : position);
         }

         if (this.blockApproachTicks > 12 && this.stationaryTicks > 12 && this.stuckRepaths >= 3) {
            this.stopBaritone();
            return true;
         } else if (this.blockApproachTicks > 600) {
            this.stopBaritone();
            return true;
         } else {
            return false;
         }
      } else {
         this.blockApproachTicks = 0;
         return true;
      }
   }

   private void appendReturnToStart(EventSequenceRunner.EventSequence sequence) {
      sequence.then(PlayerTickEvent.class, event -> this.tickReturnToStart());
   }

   private void restoreUseKey() {
      if (this.eatingUseKeyHeld) {
         minecraftClient.options
            .useKey
            .setPressed(InputUtil.isKeyPressed(minecraftClient.getWindow(), minecraftClient.options.useKey.getDefaultKey().getCode()));
         this.eatingUseKeyHeld = false;
      }
   }

   private boolean hasGoldBlocks() {
      return InventoryActions.countItems(Items.GOLD_BLOCK) > 0;
   }

   private boolean purchaseObserved() {
      return this.purchasedItemCount() > this.goodsBeforePurchase || InventoryActions.countItems(Items.EMERALD) < this.emeraldsBeforePurchase;
   }

   private boolean selectedTradeHasOutput(MerchantScreen merchantScreen, int tradeIndex) {
      return ((MerchantSelectionAccess)merchantScreen).mouseclient$getSelectedIndex() != tradeIndex
         ? false
         : merchantScreen.getScreenHandler().getSlot(2) != null
            && merchantScreen.getScreenHandler().getSlot(2).hasStack()
            && merchantScreen.getScreenHandler().getSlot(2).getStack().isOf(this.purchasedItem());
   }

   private void startEmeraldExchange() {
      this.exchangePhase = 1;
      this.exchangeWaitTicks = 0;
      this.emeraldStackClicks = 0;
      this.missingEmeraldTicks = 0;
   }

   private void stopBaritone() {
      if (this.baritoneActive) {
         this.baritoneActive = false;
         ChatMessages.sendBaritoneCommand(("#stop"));
      }
   }

   private LivingEntity nearestMerchant(boolean excludeVisited) {
      return minecraftClient.world
         .getEntitiesByClass(
            MerchantEntity.class,
            minecraftClient.player.getBoundingBox().expand((128.0D)),
            candidateMerchant -> candidateMerchant.isAlive() && (!excludeVisited || !this.visitedMerchants.contains(candidateMerchant.getUuid()))
         )
         .stream()
         .min(Comparator.comparingDouble(distanceCandidate -> minecraftClient.player.squaredDistanceTo(distanceCandidate)))
         .orElse(null);
   }

   private void interactMerchant(LivingEntity targetedEntity) {
      if (targetedEntity != null && targetedEntity.isAlive()) {
         if (this.merchantVisible(targetedEntity)) {
            ((MinecraftCooldownAccessor)(Object)minecraftClient).setRightClickDelay(0);
            this.releaseSneaking();
            this.aimAtMerchant(targetedEntity);
            if (CombatTargeting.rayHitsEntityBounds(
               targetedEntity, minecraftClient.player.getEntityInteractionRange() + 1.0, minecraftClient.player.getYaw(), minecraftClient.player.getPitch()
            )) {
               EntityHitResult crosshairTarget = EntityRaycast.raycastFromCamera(
                  minecraftClient.player.getEntityInteractionRange() + 1.0,
                  new RotationAngles(minecraftClient.player.getYaw(), minecraftClient.player.getPitch()),
                  entity -> entity.getId() == targetedEntity.getId()
               );
               if (crosshairTarget == null) {
                  crosshairTarget = new EntityHitResult(targetedEntity, targetedEntity.getBoundingBox().getCenter());
               }

               minecraftClient.crosshairTarget = crosshairTarget;
               minecraftClient.targetedEntity = targetedEntity;
               minecraftClient.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.interactAt(targetedEntity, false, Hand.MAIN_HAND, crosshairTarget.getPos()));
               minecraftClient.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.interact(targetedEntity, false, Hand.MAIN_HAND));
               minecraftClient.interactionManager.interactEntity(minecraftClient.player, targetedEntity, Hand.MAIN_HAND);
               ((MinecraftActionInvoker)(Object)minecraftClient).invokeStartUseItem();
            }
         }
      }
   }

   private String reconnectCountdownText() {
      if (this.waitingToReconnect) {
         return String.format(
            ("Перезаход через: %d:%02d"),
            (int)((Math.max(0L, this.reconnectAtMillis - System.currentTimeMillis()) + (999L)) / (1000L)) / 60,
            (int)((Math.max(0L, this.reconnectAtMillis - System.currentTimeMillis()) + (999L)) / (1000L)) % 60
         );
      } else {
         return this.waitingToRestart
            ? String.format(
               ("Перезапуск через: %d:%02d"),
               (int)((Math.max(0L, this.restartAtMillis - System.currentTimeMillis()) + (999L)) / (1000L)) / 60,
               (int)((Math.max(0L, this.restartAtMillis - System.currentTimeMillis()) + (999L)) / (1000L)) % 60
            )
            : null;
      }
   }

   private void closeClickGuiAndInventory() {
      if (minecraftClient.currentScreen instanceof MouseClientClickGUI) {
         minecraftClient.setScreen(null);
      }

      this.closeHandledScreenIfReady();
   }

   private void closeHandledScreenIfReady() {
      if (clientReady()) {
         if (minecraftClient.currentScreen instanceof HandledScreen) {
            InventoryActions.closeInventory(true);
            minecraftClient.player.closeHandledScreen();
         } else if (minecraftClient.currentScreen != null) {
            minecraftClient.setScreen(null);
         }
      }
   }

   private Slot largestGoldIngotSlot(ScreenHandler handler) {
      Slot slot = null;

      for (Slot candidateSlot : handler.slots) {
         if (candidateSlot.id >= 10
            && candidateSlot.hasStack()
            && candidateSlot.getStack().isOf(Items.GOLD_INGOT)
            && (slot == null || candidateSlot.getStack().getCount() > slot.getStack().getCount())) {
            slot = candidateSlot;
         }
      }

      return slot;
   }

   private void appendEmeraldExchange(EventSequenceRunner.EventSequence sequence, boolean onlyWhenNeeded) {
      sequence.then(PlayerTickEvent.class, event -> {
         if (this.exchangePhase == 0 && !this.exchangingEmeralds) {
            this.exchangeRetries = 0;
         }

         return this.tickEmeraldExchange(onlyWhenNeeded);
      });
   }

   public static boolean shouldHideBaritoneChat() {
      return INSTANCE != null && (INSTANCE.workStartedAt > 0L || INSTANCE.isEnabled() || INSTANCE.restartPending);
   }

   private LivingEntity bestEvaluatedMerchant() {
      return minecraftClient.world
         .getEntitiesByClass(
            MerchantEntity.class,
            minecraftClient.player.getBoundingBox().expand((128.0D)),
            candidateMerchant -> candidateMerchant.isAlive() && !this.visitedMerchants.contains(candidateMerchant.getUuid()) && this.merchantTradeYields.getOrDefault(candidateMerchant.getUuid(), -1) > 0
         )
         .stream()
         .max(Comparator.comparingInt(candidateMerchant -> this.merchantTradeYields.getOrDefault(candidateMerchant.getUuid(), -1)))
         .orElse(null);
   }

   private void appendShardReconnect(EventSequenceRunner.EventSequence sequence, BooleanSupplier condition) {
      BooleanSupplier shouldReconnect = () -> condition.getAsBoolean() && this.targetAnarchy() > 0;
      sequence.then(PlayerTickEvent.class, event -> {
         if (!shouldReconnect.getAsBoolean()) {
            return true;
         }

         if (!this.isEnabled()) {
            return true;
         }

         this.closeCurrentScreenIfReady();
         this.stopBaritone();
         this.reconnectGoodsWaitTicks = 0;
         return true;
      });
      sequence.then(PlayerTickEvent.class, reconnectEvent -> {
         if (!shouldReconnect.getAsBoolean()) {
            return true;
         } else {
            return this.isEnabled() && this.desiredTradeAvailable ? this.purchasedItemCount() <= 0 || ++this.reconnectGoodsWaitTicks >= 200 : true;
         }
      });
      sequence.then(PlayerTickEvent.class, reconnectEvent -> {
         if (!shouldReconnect.getAsBoolean()) {
            return true;
         }

         if (!this.isEnabled()) {
            return true;
         }

         this.closeClickGuiAndInventory();
         minecraftClient.player.networkHandler.sendChatCommand(("hub"));
         this.waitingToReconnect = true;
         this.reconnectAtMillis = System.currentTimeMillis() + (90000L);
         return true;
      });
      sequence.then(PlayerTickEvent.class, reconnectEvent -> {
         if (!shouldReconnect.getAsBoolean()) {
            return true;
         } else {
            return !this.isEnabled() ? true : System.currentTimeMillis() >= this.reconnectAtMillis;
         }
      });
      sequence.then(PlayerTickEvent.class, reconnectEvent -> {
         if (!shouldReconnect.getAsBoolean()) {
            return true;
         }

         if (!this.isEnabled()) {
            return true;
         }

         this.waitingToReconnect = false;
         this.closeClickGuiAndInventory();
         this.restoreBaritoneSettings();
         this.rejoiningAnarchy = true;
         this.anarchyJoinTicks = 0;
         minecraftClient.player.networkHandler.sendChatCommand("an" + this.targetAnarchy());
         return true;
      });
      sequence.then(PlayerTickEvent.class, reconnectEvent -> {
         if (!shouldReconnect.getAsBoolean()) {
            return true;
         }

         if (!this.isEnabled()) {
            return true;
         }

         if (this.sidebarAnarchy() == this.targetAnarchy()) {
            return true;
         }

         if (++this.anarchyJoinTicks % 400 == 0) {
            minecraftClient.player.networkHandler.sendChatCommand("an" + this.targetAnarchy());
         }

         return this.anarchyJoinTicks >= 1200;
      });
      this.appendTickDelay(sequence, 3);
      sequence.then(PlayerTickEvent.class, reconnectEvent -> {
         if (!shouldReconnect.getAsBoolean()) {
            return true;
         }

         this.waitingToReconnect = false;
         this.restartPending = true;
         this.waitingToRestart = true;
         this.restartAtMillis = System.currentTimeMillis() + (5000L);
         this.restoreBaritoneSettings();
         this.setEnabled(false);
         return true;
      });
   }

   private BlockPos nearestMatchingBlock(Predicate<BlockState> matchesBlock) {
      BlockPos position = null;
      double nearestDistanceSquared = (1.7976931348623157E308D);

      for (int chunkX = minecraftClient.player.getChunkPos().x - 9; chunkX <= minecraftClient.player.getChunkPos().x + 9; chunkX++) {
         for (int chunkZ = minecraftClient.player.getChunkPos().z - 9; chunkZ <= minecraftClient.player.getChunkPos().z + 9; chunkZ++) {
            if (minecraftClient.world.isChunkLoaded(chunkX, chunkZ)) {
               for (int sectionIndex = 0; sectionIndex < minecraftClient.world.getChunk(chunkX, chunkZ).getSectionArray().length; sectionIndex++) {
                  ChunkSection section = minecraftClient.world.getChunk(chunkX, chunkZ).getSectionArray()[sectionIndex];
                  if (!section.isEmpty() && section.hasAny(matchesBlock)) {
                     for (int localX = 0; localX < 16; localX++) {
                        for (int localY = 0; localY < 16; localY++) {
                           for (int localZ = 0; localZ < 16; localZ++) {
                              if (matchesBlock.test(section.getBlockState(localX, localY, localZ))
                                 && !(
                                    minecraftClient.player
                                          .squaredDistanceTo(
                                             Vec3d.ofCenter(
                                                new BlockPos(chunkX * 16 + localX, minecraftClient.world.sectionIndexToCoord(sectionIndex) * 16 + localY, chunkZ * 16 + localZ)
                                             )
                                          )
                                       > (16384.0D)
                                 )
                                 && !(
                                    minecraftClient.player
                                          .squaredDistanceTo(
                                             Vec3d.ofCenter(
                                                new BlockPos(chunkX * 16 + localX, minecraftClient.world.sectionIndexToCoord(sectionIndex) * 16 + localY, chunkZ * 16 + localZ)
                                             )
                                          )
                                       >= nearestDistanceSquared
                                 )) {
                                 nearestDistanceSquared = minecraftClient.player
                                    .squaredDistanceTo(
                                       Vec3d.ofCenter(
                                          new BlockPos(chunkX * 16 + localX, minecraftClient.world.sectionIndexToCoord(sectionIndex) * 16 + localY, chunkZ * 16 + localZ)
                                       )
                                    );
                                 position = new BlockPos(chunkX * 16 + localX, minecraftClient.world.sectionIndexToCoord(sectionIndex) * 16 + localY, chunkZ * 16 + localZ);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return position;
   }

   private void tickRestartAndFood(PlayerTickEvent event) {
      if (this.sidebarAnarchy() > 0) {
         this.anarchyNumber = this.sidebarAnarchy();
      }

      if (!this.useFood.getValue() || !this.rejoiningAnarchy && (!this.restartPending || System.currentTimeMillis() >= this.restartAtMillis) && !this.isEnabled()) {
         if (this.eatingUseKeyHeld) {
            this.restoreUseKey();
         }
      } else {
         this.tickOffhandEating();
      }

      if (this.restartPending) {
         if (System.currentTimeMillis() >= this.restartAtMillis) {
            if (minecraftClient.player != null && minecraftClient.world != null) {
               if (this.sidebarAnarchy() == this.targetAnarchy()) {
                  if (this.nearbyMerchantCount() != 0 || ++this.shardReadyPolls >= 200) {
                     this.restartTradingCycle();
                  }
               }
            }
         }
      }
   }

   private boolean offerSellsItem(TradeOffer offer, Item item) {
      return !offer.getSellItem().isEmpty() && offer.getSellItem().isOf(item);
   }

   private boolean isUnloadItem(Item item) {
      return item == Items.GOLD_INGOT || item == Items.GOLD_BLOCK || item == this.purchasedItem();
   }

   private boolean shouldEat(ItemStack stack) {
      if (stack.get(DataComponentTypes.FOOD) == null) {
         return false;
      } else {
         return stack.get(DataComponentTypes.FOOD).canAlwaysEat()
            ? minecraftClient.player.getHungerManager().isNotFull()
            : minecraftClient.player.getHungerManager().getFoodLevel() <= 17;
      }
   }

   @Override
   protected void onDisable() {
      this.restoreUseKey();
      this.sequenceRunner.clear();
      this.restoreBaritoneSettings();
      if (!this.restartPending && !this.waitingToRestart && !this.rejoiningAnarchy) {
         this.restartPending = false;
         this.rejoiningAnarchy = false;
         this.workStartedAt = 0L;
         this.cycleStartedAt = 0L;
         this.reconnectGoodsWaitTicks = 0;
         this.anarchyNumber = 0;
         this.cycleStartPosition = null;
         this.waitingToReconnect = false;
         this.waitingToRestart = false;
         this.reconnectAtMillis = 0L;
         this.restartAtMillis = 0L;
         this.craftingTablePosition = null;
         this.craftingApproachPosition = null;
         this.storageChestPosition = null;
         this.storageApproachPosition = null;
         this.currentMerchant = null;
         this.exchangingEmeralds = false;
         this.craftingGold = false;
         this.unloadingGoods = false;
         this.desiredTradeAvailable = true;
         this.blockOpenTicks = 0;
         this.blockApproachTicks = 0;
         this.blockPathRefreshes = 0;
         this.resetAimState();
         this.craftAttempts = 0;
         this.craftDelayTicks = 0;
         this.emeraldsBeforePurchase = 0;
         this.goodsBeforePurchase = 0;
         this.purchaseRetryClicks = 0;
         this.nearAimWaitTicks = 0;
         this.aimWaitTicks = 0;
         this.merchantOpenTicks = 0;
         this.merchantApproachTicks = 0;
         this.movedMerchantRepaths = 0;
         this.merchantSearchTicks = 0;
         this.recipeWaitTicks = 0;
         this.tradeSelectionTicks = 0;
         this.resetExchangeState();
         this.returningToStart = false;
         this.visitedMerchants.clear();
         this.evaluatedMerchants.clear();
         this.merchantTradeYields.clear();
         this.preserveTradeEvaluations = false;
      } else {
         EventBus.getInstance().subscribe(this);
      }
   }

   private void appendTradeAvailabilityCheck(EventSequenceRunner.EventSequence sequence) {
      sequence.then(PlayerTickEvent.class, event -> {
         if (!this.isEnabled()) {
            return true;
         }

         this.desiredTradeAvailable = true;
         this.merchantSearchTicks = 0;
         this.nearAimWaitTicks = 0;
         this.aimWaitTicks = 0;
         this.merchantOpenTicks = 0;
         this.merchantApproachTicks = 0;
         this.movedMerchantRepaths = 0;
         this.recipeWaitTicks = 0;
         this.closeCurrentScreenIfReady();
         this.currentMerchant = null;
         return minecraftClient.currentScreen == null || !(minecraftClient.currentScreen instanceof HandledScreen);
      });
      sequence.then(PlayerTickEvent.class, checkEvent -> {
         if (!this.isEnabled()) {
            return true;
         } else {
            this.currentMerchant = this.nearestMerchant(false);
            if (this.currentMerchant != null) {
               this.resetAimState();
               this.merchantSearchTicks = 0;
               return true;
            } else {
               return ++this.merchantSearchTicks >= 5;
            }
         }
      });
      this.appendMerchantApproach(sequence);
      this.appendMerchantOpen(sequence);
      sequence.then(PlayerTickEvent.class, checkEvent -> {
         if (minecraftClient.currentScreen instanceof MerchantScreen checkScreen) {
            if (checkScreen.getScreenHandler().getRecipes() != null && !checkScreen.getScreenHandler().getRecipes().isEmpty()) {
               this.recipeWaitTicks = 0;
               return true;
            } else {
               return ++this.recipeWaitTicks >= 10;
            }
         } else {
            return true;
         }
      });
      sequence.then(
         PlayerTickEvent.class,
         checkEvent -> {
            if (minecraftClient.currentScreen instanceof MerchantScreen checkScreen
               && checkScreen.getScreenHandler().getRecipes() != null
               && !checkScreen.getScreenHandler().getRecipes().isEmpty()) {
               this.desiredTradeAvailable = this.bestTradeIndex(checkScreen.getScreenHandler()) >= 0;
               if (!this.desiredTradeAvailable) {
                  ChatMessages.printPrefixed(("У проверочного жителя нет нужного трейда — цикл уйдёт в хаб на перезаход."));
               }
            }

            this.closeHandledScreenIfReady();
            return true;
         }
      );
   }

   private void openServerShop() {
      this.stopBaritone();
      minecraftClient.player.networkHandler.sendChatCommand(("shop"));
   }

   private Item shopExchangeItem() {
      return this.exchange.isSelected(("Слеза Гаста")) ? null : this.purchasedItem();
   }

   private Item purchasedItem() {
      return this.exchange.isSelected(("Слеза Гаста")) ? Items.GHAST_TEAR : Items.GOLD_INGOT;
   }

   private boolean inventoryNearlyFull() {
      int emptySlots = 0;

      for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
         if (minecraftClient.player.getInventory().getStack(inventoryIndex).isEmpty()) {
            if (++emptySlots > 2) {
               return false;
            }
         }
      }

      return emptySlots <= 2;
   }

   private void releaseSneaking() {
      if (minecraftClient.player.isSneaking()) {
         minecraftClient.player.setSneaking(false);
      }
   }

   private void appendMerchantOpen(EventSequenceRunner.EventSequence sequence) {
      sequence.then(PlayerTickEvent.class, event -> {
         if (!this.isEnabled() || this.currentMerchant == null) {
            return true;
         }

         if (!this.merchantApproachReady) {
            return true;
         }

         if (minecraftClient.currentScreen instanceof MerchantScreen) {
            this.merchantOpenTicks = 0;
            return true;
         }

         if (this.refreshMerchant() == null) {
            return true;
         }

         this.releaseSneaking();
         if (this.merchantOpenTicks % 3 == 0) {
            this.interactMerchant(this.refreshMerchant());
         }

         return minecraftClient.currentScreen instanceof MerchantScreen || ++this.merchantOpenTicks >= 30;
      });
      sequence.then(PlayerTickEvent.class, openEvent -> {
         if (!this.isEnabled() || this.currentMerchant == null) {
            return true;
         } else if (minecraftClient.currentScreen instanceof MerchantScreen) {
            RotationController.getInstance().reset();
            this.recipeWaitTicks = 0;
            return true;
         } else {
            return ++this.recipeWaitTicks >= 10;
         }
      });
   }

   private Slot findContainerItem(Item item) {
      if (minecraftClient.currentScreen instanceof HandledScreen containerScreen) {
         for (int containerIndex = 0; containerIndex < this.containerSlotCount(containerScreen.getScreenHandler()); containerIndex++) {
            if (containerScreen.getScreenHandler().getSlot(containerIndex) != null
               && containerScreen.getScreenHandler().getSlot(containerIndex).hasStack()
               && containerScreen.getScreenHandler().getSlot(containerIndex).getStack().isOf(item)) {
               return containerScreen.getScreenHandler().getSlot(containerIndex);
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void appendGoldCrafting(EventSequenceRunner.EventSequence sequence, boolean onlyWhenNeeded) {
      sequence.then(PlayerTickEvent.class, event -> {
         if (!this.isEnabled()) {
            return true;
         }

         this.closeCurrentScreenIfReady();
         this.craftAttempts = 0;
         this.craftDelayTicks = 0;
         this.blockOpenTicks = 0;
         this.craftingGold = this.goldIngotCount() >= 9 && (!onlyWhenNeeded || this.inventoryNeedsCompaction());
         if (!this.craftingGold) {
            this.craftingTablePosition = null;
            this.craftingApproachPosition = null;
            return minecraftClient.currentScreen == null || !(minecraftClient.currentScreen instanceof HandledScreen);
         }

         this.craftingTablePosition = this.nearestMatchingBlock(blockState -> blockState.isOf(Blocks.CRAFTING_TABLE));
         this.craftingApproachPosition = this.craftingTablePosition != null ? this.blockApproachPosition(this.craftingTablePosition) : null;
         this.craftingGold = this.craftingTablePosition != null;
         if (this.craftingTablePosition == null && !onlyWhenNeeded) {
            ChatMessages.printPrefixed(("Верстак не найден в радиусе 128 блоков — крафт блоков пропускаю."));
         }

         return minecraftClient.currentScreen == null || !(minecraftClient.currentScreen instanceof HandledScreen);
      });
      sequence.then(PlayerTickEvent.class, craftEvent -> {
         if (this.isEnabled() && this.craftingGold) {
            this.blockApproachTicks = 0;
            this.blockPathRefreshes = 0;
            this.stationaryTicks = 0;
            this.stuckRepaths = 0;
            this.previousPathPosition = minecraftClient.player.getEntityPos();
            this.startBaritonePath(this.craftingApproachPosition != null ? this.craftingApproachPosition : this.craftingTablePosition);
            return true;
         } else {
            return true;
         }
      });
      sequence.then(
         PlayerTickEvent.class,
         craftEvent -> this.isEnabled() && this.craftingGold && this.craftingTablePosition != null ? this.tickBlockApproach(this.craftingTablePosition, this.craftingApproachPosition) : true
      );
      sequence.then(PlayerTickEvent.class, craftEvent -> {
         if (this.craftingGold) {
            this.stopBaritone();
         }

         return true;
      });
      sequence.then(PlayerTickEvent.class, craftEvent -> {
         if (!this.isEnabled() || !this.craftingGold || this.craftingTablePosition == null) {
            return true;
         }

         if (minecraftClient.currentScreen instanceof CraftingScreen) {
            this.blockOpenTicks = 0;
            return true;
         }

         if (!this.blockWithinReach(this.craftingTablePosition)) {
            if (this.blockOpenTicks % 20 == 1) {
               this.startBaritonePath(this.craftingApproachPosition != null ? this.craftingApproachPosition : this.craftingTablePosition);
            }

            return ++this.blockOpenTicks >= 30;
         } else {
            if (this.blockOpenTicks % 5 == 0) {
               this.interactTargetBlock(this.craftingTablePosition, this.craftingApproachPosition);
            }

            return ++this.blockOpenTicks >= 30;
         }
      });
      sequence.then(PlayerTickEvent.class, craftEvent -> this.tickGoldCrafting());
      sequence.then(PlayerTickEvent.class, craftEvent -> {
         this.closeHandledScreenIfReady();
         this.craftingGold = false;
         InventoryActions.pickupAllPacket();
         return true;
      });
   }

   private void appendMerchantEvaluation(EventSequenceRunner.EventSequence sequence) {
      sequence.then(PlayerTickEvent.class, event -> {
         this.nearAimWaitTicks = 0;
         this.aimWaitTicks = 0;
         this.merchantOpenTicks = 0;
         this.merchantSearchTicks = 0;
         this.recipeWaitTicks = 0;
         this.closeCurrentScreenIfReady();
         this.currentMerchant = null;
         return minecraftClient.currentScreen == null || !(minecraftClient.currentScreen instanceof HandledScreen);
      });
      sequence.then(PlayerTickEvent.class, evaluationEvent -> {
         if (!this.isEnabled()) {
            return true;
         }

         if (this.desiredTradeAvailable && !this.cycleExpired()) {
            this.currentMerchant = this.nearestUnevaluatedMerchant();
            if (this.currentMerchant != null) {
               this.resetAimState();
               this.merchantSearchTicks = 0;
               return true;
            } else {
               return !this.evaluatedMerchants.isEmpty() ? true : ++this.merchantSearchTicks >= 5;
            }
         } else {
            this.currentMerchant = null;
            return true;
         }
      });
      this.appendMerchantApproach(sequence);
      this.appendMerchantOpen(sequence);
      sequence.then(PlayerTickEvent.class, evaluationEvent -> {
         if (minecraftClient.currentScreen instanceof MerchantScreen evaluationScreen) {
            if (evaluationScreen.getScreenHandler().getRecipes() != null && !evaluationScreen.getScreenHandler().getRecipes().isEmpty()) {
               this.recipeWaitTicks = 0;
               return true;
            } else {
               return ++this.recipeWaitTicks >= 10;
            }
         } else {
            return true;
         }
      });
      sequence.then(PlayerTickEvent.class, evaluationEvent -> {
         if (this.currentMerchant != null) {
            if (minecraftClient.currentScreen instanceof MerchantScreen evaluationScreen) {
               this.merchantTradeYields.put(this.currentMerchant.getUuid(), this.bestEmeraldTradeYield(evaluationScreen.getScreenHandler()));
            }

            this.evaluatedMerchants.add(this.currentMerchant.getUuid());
         }

         this.closeHandledScreenIfReady();
         return true;
      });
   }

   private boolean blockWithinReach(BlockPos position) {
      return position != null
         && minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(position)) <= minecraftClient.player.getBlockInteractionRange() - (0.1D);
   }

   private int containerSlotCount(ScreenHandler handler) {
      return handler instanceof GenericContainerScreenHandler containerHandler ? containerHandler.getRows() * 9 : Math.max(0, handler.slots.size() - 36);
   }

   private boolean tickEmeraldExchange(boolean onlyWhenNeeded) {
      if (!this.isEnabled() || !this.desiredTradeAvailable || this.inventoryNearlyFull() || this.cycleExpired()) {
         this.resetExchangeState();
         this.exchangingEmeralds = false;
         return true;
      }

      if (onlyWhenNeeded && this.exchangePhase == 0 && this.hasEmeraldReserve()) {
         this.resetExchangeState();
         this.exchangingEmeralds = false;
         return true;
      }

      this.exchangingEmeralds = true;
      switch (this.exchangePhase) {
         case 0:
            if (this.exchangeRetries >= 8) {
               this.resetExchangeState();
               this.exchangingEmeralds = false;
               return true;
            }

            this.stopBaritone();
            this.closeCurrentScreenIfReady();
            this.startEmeraldExchange();
            return false;
         case 1:
            if (minecraftClient.currentScreen instanceof HandledScreen) {
               this.exchangeWaitTicks = 0;
               this.exchangePhase = 2;
               return false;
            } else {
               this.exchangeWaitTicks++;
               if (this.exchangeWaitTicks <= 3) {
                  return false;
               } else {
                  if (this.exchangeWaitTicks - 3 == 1 || (this.exchangeWaitTicks - 3) % 80 == 0) {
                     this.openServerShop();
                  }

                  if (this.exchangeWaitTicks - 3 >= 640) {
                     this.exchangeRetries++;
                     this.exchangePhase = 0;
                     this.exchangeWaitTicks = 0;
                  }

                  return false;
               }
            }
         case 2:
            if (!(minecraftClient.currentScreen instanceof HandledScreen)) {
               this.exchangePhase = 0;
               return false;
            } else {
               this.exchangeWaitTicks++;
               if (this.exchangeWaitTicks <= 2) {
                  return false;
               } else if (this.shopExchangeItem() == null) {
                  this.exchangePhase = 4;
                  this.exchangeWaitTicks = 0;
                  return false;
               } else {
                  if (this.findContainerItem(this.shopExchangeItem()) == null) {
                     if (this.exchangeWaitTicks >= 62) {
                        this.exchangeRetries++;
                        this.exchangePhase = 0;
                        this.exchangeWaitTicks = 0;
                     }

                     return false;
                  }

                  InventoryActions.clickSlot(this.findContainerItem(this.shopExchangeItem()), 0, SlotActionType.PICKUP, false);
                  this.exchangeWaitTicks = 0;
                  this.exchangePhase = 4;
                  return false;
               }
            }
         case 3:
         default:
            this.exchangePhase = 0;
            return false;
         case 4:
            if (this.emeraldStackClicks >= 4) {
               this.exchangePhase = 5;
               this.exchangeWaitTicks = 0;
               return false;
            } else if (!(minecraftClient.currentScreen instanceof HandledScreen)) {
               this.exchangePhase = 0;
               this.exchangeWaitTicks = 0;
               return false;
            } else if (this.exchangeWaitTicks < 10) {
               this.exchangeWaitTicks++;
               return false;
            } else {
               if (this.findContainerItem(Items.EMERALD) == null) {
                  this.missingEmeraldTicks++;
                  if (this.missingEmeraldTicks >= 30) {
                     this.missingEmeraldTicks = 0;
                     this.exchangeRetries++;
                     this.exchangePhase = 0;
                     this.exchangeWaitTicks = 0;
                  }

                  return false;
               }

               this.missingEmeraldTicks = 0;
               this.exchangeWaitTicks = 0;
               InventoryActions.clickSlot(this.findContainerItem(Items.EMERALD), 1, SlotActionType.QUICK_MOVE, false);
               this.emeraldStackClicks++;
               return false;
            }
         case 5:
            if (minecraftClient.currentScreen != null) {
               this.closeHandledScreenIfReady();
               return false;
            } else {
               InventoryActions.pickupAllPacket();
               if (this.hasEmeraldReserve()) {
                  this.resetExchangeState();
                  this.exchangingEmeralds = false;
                  return true;
               } else {
                  this.exchangeRetries++;
                  this.emeraldStackClicks = 0;
                  this.exchangePhase = 0;
                  this.exchangeWaitTicks = 0;
                  return false;
               }
            }
      }
   }

   private boolean inventoryNeedsCompaction() {
      int emptySlots = 0;

      for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
         if (minecraftClient.player.getInventory().getStack(inventoryIndex).isEmpty()) {
            if (++emptySlots > 10) {
               return false;
            }
         }
      }

      return emptySlots <= 10;
   }

   private int targetAnarchy() {
      return this.anarchyNumber;
   }

   private static boolean clientReady() {
      return minecraftClient.player != null && minecraftClient.world != null && minecraftClient.interactionManager != null;
   }

   private boolean tradeOutputUnavailable(MerchantScreen merchantScreen) {
      return merchantScreen.getScreenHandler().getSlot(2) == null
         || !merchantScreen.getScreenHandler().getSlot(2).hasStack()
         || !merchantScreen.getScreenHandler().getSlot(2).getStack().isOf(this.purchasedItem());
   }

   private void resetAimState() {
      this.yawVelocity = this.pitchVelocity = 0.0F;
      this.aimParametersChangedAt = this.aimParameterIntervalMillis = 0L;
      this.aimStartedAt = 0L;
   }

   private boolean ensureTradeSelected(MerchantScreen merchantScreen) {
      if (this.bestTradeIndex(merchantScreen.getScreenHandler()) < 0) {
         return false;
      }

      if (this.selectedTradeHasOutput(merchantScreen, this.bestTradeIndex(merchantScreen.getScreenHandler()))) {
         return true;
      }

      ((MerchantSelectionAccess)merchantScreen).mouseclient$selectTrade(this.bestTradeIndex(merchantScreen.getScreenHandler()));
      return false;
   }

   private int sidebarAnarchy() {
      if (minecraftClient.world == null) {
         return -1;
      }

      ScoreboardObjective sidebarObjective = minecraftClient.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
      if (sidebarObjective == null) {
         return -1;
      }

      Matcher numberMatcher = ANARCHY_NUMBER_PATTERN.matcher(sidebarObjective.getDisplayName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""));
      int lastAnarchyNumber = -1;

      while (numberMatcher.find()) {
         lastAnarchyNumber = Integer.parseInt(numberMatcher.group());
      }

      return lastAnarchyNumber;
   }

   private boolean merchantVisible(LivingEntity entity) {
      return this.merchantVisibleFrom(minecraftClient.player.getEyePos(), entity);
   }

   private AutoVillager() {
      this.lowestRemainingGoods = (2147483647);
      this.previousPathPosition = Vec3d.ZERO;
      this.visitedMerchants = new HashSet<>();
      this.evaluatedMerchants = new HashSet<>();
      this.merchantTradeYields = new HashMap<>();
      this.playerTickListener = event -> this.tickRestartAndFood(event);
      this.hudRenderListener = event -> this.renderStatus(event);
      this.packetListener = event -> this.handleServerPosition(event);
      this.exchange = this.addSetting(new ModeSetting(("Обмен"), ("Что выкупать у жителей."), ("Золото"), ("Золото"), ("Слеза Гаста")));
      this.useFood = this.addSetting(new BooleanSetting(("Использовать еду"), ("Есть из левой руки во время работы и ожидания перезахода."), true));
   }

   private BlockPos merchantApproachPosition(LivingEntity entity) {
      BlockPos visiblePosition = null;
      double visibleScore = (1.7976931348623157E308D);
      BlockPos occludedPosition = null;
      double occludedScore = (1.7976931348623157E308D);

      for (int offsetX = -2; offsetX <= 2; offsetX++) {
         for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
            if (offsetX != 0 || offsetZ != 0) {
               for (int offsetY = -1; offsetY <= 1; offsetY++) {
                  if (this.canStandAt(entity.getBlockPos().add(offsetX, offsetY, offsetZ))) {
                     if (this.merchantVisibleFrom(
                        Vec3d.ofBottomCenter(entity.getBlockPos().add(offsetX, offsetY, offsetZ)).add(0.0, minecraftClient.player.getStandingEyeHeight(), 0.0), entity
                     )) {
                        if (entity.getBlockPos().add(offsetX, offsetY, offsetZ).getSquaredDistance(entity.getBlockPos()) * (100.0D)
                              + minecraftClient.player.squaredDistanceTo(Vec3d.ofBottomCenter(entity.getBlockPos().add(offsetX, offsetY, offsetZ)))
                           < visibleScore) {
                           visibleScore = entity.getBlockPos().add(offsetX, offsetY, offsetZ).getSquaredDistance(entity.getBlockPos()) * (100.0D)
                              + minecraftClient.player.squaredDistanceTo(Vec3d.ofBottomCenter(entity.getBlockPos().add(offsetX, offsetY, offsetZ)));
                           visiblePosition = entity.getBlockPos().add(offsetX, offsetY, offsetZ);
                        }
                     } else if (entity.getBlockPos().add(offsetX, offsetY, offsetZ).getSquaredDistance(entity.getBlockPos()) * (100.0D)
                           + minecraftClient.player.squaredDistanceTo(Vec3d.ofBottomCenter(entity.getBlockPos().add(offsetX, offsetY, offsetZ)))
                        < occludedScore) {
                        occludedScore = entity.getBlockPos().add(offsetX, offsetY, offsetZ).getSquaredDistance(entity.getBlockPos()) * (100.0D)
                           + minecraftClient.player.squaredDistanceTo(Vec3d.ofBottomCenter(entity.getBlockPos().add(offsetX, offsetY, offsetZ)));
                        occludedPosition = entity.getBlockPos().add(offsetX, offsetY, offsetZ);
                     }
                  }
               }
            }
         }
      }

      if (visiblePosition != null) {
         return visiblePosition;
      } else {
         return occludedPosition != null ? occludedPosition : entity.getBlockPos();
      }
   }

   private float hudTop() {
      return 2.0F + ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents().size() * (19.0F);
   }

   private void resetExchangeState() {
      this.exchangePhase = 0;
      this.exchangeWaitTicks = 0;
      this.emeraldStackClicks = 0;
      this.missingEmeraldTicks = 0;
      this.exchangeRetries = 0;
   }

   private void handleServerPosition(PacketEvent event) {
      if (event.isIncoming() && event.getPacket() instanceof PlayerPositionLookS2CPacket) {
         if (this.rejoiningAnarchy || this.restartPending) {
            if (minecraftClient.player != null && minecraftClient.world != null) {
               if (this.sidebarAnarchy() == this.targetAnarchy()) {
                  if (this.nearbyMerchantCount() != 0 || ++this.shardReadyPolls >= 200) {
                     this.restartTradingCycle();
                  }
               }
            }
         }
      }
   }

   private LivingEntity refreshMerchant() {
      if (this.currentMerchant == null) {
         return null;
      } else if (minecraftClient.world.getEntityById(this.currentMerchant.getId()) instanceof LivingEntity entity && entity.isAlive()) {
         this.currentMerchant = entity;
         return entity;
      } else {
         this.currentMerchant = minecraftClient.world
            .getEntitiesByClass(
               MerchantEntity.class,
               minecraftClient.player.getBoundingBox().expand((128.0D)),
               candidateMerchant -> candidateMerchant.isAlive() && candidateMerchant.getUuid().equals(this.currentMerchant.getUuid())
            )
            .stream()
            .findFirst()
            .orElse(null);
         return this.currentMerchant;
      }
   }

   private void restartTradingCycle() {
      if (this.rejoiningAnarchy || this.restartPending) {
         if (this.isEnabled()) {
            this.setEnabled(false);
         }

         this.restartPending = false;
         this.waitingToRestart = false;
         this.restartAtMillis = 0L;
         this.rejoiningAnarchy = false;
         this.waitingToReconnect = false;
         this.restoreUseKey();
         this.sequenceRunner.clear();
         this.restoreBaritoneSettings();
         EventBus.getInstance().unsubscribe(this);
         this.preserveTradeEvaluations = true;
         this.shardReadyPolls = 0;
         this.setEnabled(true);
      }
   }

   private static String formatElapsedTime(long elapsedMillis) {
      return String.format(("%02d:%02d:%02d"), elapsedMillis / (1000L) / (3600L), elapsedMillis / (1000L) % (3600L) / (60L), elapsedMillis / (1000L) % (60L));
   }

   private int bestEmeraldTradeYield(MerchantScreenHandler handler) {
      if (handler.getRecipes() == null) {
         return -1;
      }

      int bestYield = -1;

      for (TradeOffer candidateOffer : handler.getRecipes()) {
         if (candidateOffer != null
            && !candidateOffer.isDisabled()
            && this.offerSellsItem(candidateOffer, this.purchasedItem())
            && this.offerUsesEmeralds(candidateOffer)
            && candidateOffer.getSellItem().getCount() * 100 / Math.max(1, candidateOffer.getDisplayedFirstBuyItem().getCount()) > bestYield) {
            bestYield = candidateOffer.getSellItem().getCount() * 100 / Math.max(1, candidateOffer.getDisplayedFirstBuyItem().getCount());
         }
      }

      return bestYield;
   }

   private boolean retryStuckPath(BlockPos position) {
      if (position != null && this.stationaryTicks > 12 && this.stuckRepaths < 3) {
         this.stuckRepaths++;
         this.stationaryTicks = 0;
         this.startBaritonePath(position);
         return true;
      } else {
         return false;
      }
   }

   private void tickOffhandEating() {
      if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.currentScreen == null) {
         if (this.isFood(minecraftClient.player.getOffHandStack()) && this.shouldEat(minecraftClient.player.getOffHandStack())
            || minecraftClient.player.isUsingItem() && minecraftClient.player.getActiveHand() == Hand.OFF_HAND) {
            if (minecraftClient.player.isUsingItem() && minecraftClient.player.getActiveHand() == Hand.OFF_HAND) {
               minecraftClient.options.useKey.setPressed(true);
               this.eatingUseKeyHeld = true;
            } else {
               InventoryActions.interactItem(Hand.OFF_HAND);
               minecraftClient.options.useKey.setPressed(true);
               this.eatingUseKeyHeld = true;
            }
         } else {
            this.restoreUseKey();
         }
      }
   }

   private boolean tradeReady(MerchantScreen merchantScreen) {
      return this.bestTradeIndex(merchantScreen.getScreenHandler()) >= 0 && this.selectedTradeHasOutput(merchantScreen, this.bestTradeIndex(merchantScreen.getScreenHandler()));
   }

   private void finishMerchantVisit() {
      if (this.currentMerchant != null) {
         this.visitedMerchants.add(this.currentMerchant.getUuid());
      }

      this.closeHandledScreenIfReady();
      InventoryActions.pickupAllPacket();
   }

   private boolean hasEmptyCollision(BlockPos position) {
      return minecraftClient.world.getBlockState(position).getCollisionShape(minecraftClient.world, position).isEmpty();
   }

   private boolean canStandAt(BlockPos position) {
      return this.hasEmptyCollision(position)
         && this.hasEmptyCollision(position.up())
         && !minecraftClient.world.getBlockState(position.down()).getCollisionShape(minecraftClient.world, position.down()).isEmpty();
   }

   private void returnCursorGold(ScreenHandler handler) {
      if (this.cursorGoldCount() > 0) {
         for (Slot slot : handler.slots) {
            if (slot.id >= 10 && slot.hasStack() && slot.getStack().isOf(Items.GOLD_INGOT) && slot.getStack().getCount() < slot.getStack().getMaxCount()) {
               InventoryActions.clickSlot(slot, 0, SlotActionType.PICKUP, false);
               if (this.cursorGoldCount() <= 0) {
                  return;
               }
            }
         }

         for (Slot emptySlot : handler.slots) {
            if (emptySlot.id >= 10 && !emptySlot.hasStack()) {
               InventoryActions.clickSlot(emptySlot, 0, SlotActionType.PICKUP, false);
               return;
            }
         }
      }
   }

   private boolean interactTargetBlock(BlockPos position, BlockPos approachPosition) {
      this.releaseSneaking();
      RotationController.getInstance().reset();
      Direction face = this.interactionFace(position, approachPosition);
      this.applyDirectRotation(
         SmoothedAimVector.anglesForDirection(
            Vec3d.ofCenter(position)
               .add(face.getOffsetX() * (0.5D), face.getOffsetY() * (0.5D), face.getOffsetZ() * (0.5D))
               .subtract(minecraftClient.player.getEyePos())
         )
      );
      this.sendCurrentRotation();
      minecraftClient.crosshairTarget = new BlockHitResult(
         Vec3d.ofCenter(position).add(face.getOffsetX() * (0.5D), face.getOffsetY() * (0.5D), face.getOffsetZ() * (0.5D)), face, position, false
      );
      ActionResult interactionResult = minecraftClient.interactionManager
         .interactBlock(minecraftClient.player, Hand.MAIN_HAND, (BlockHitResult)minecraftClient.crosshairTarget);
      minecraftClient.player.swingHand(Hand.MAIN_HAND);
      return interactionResult.isAccepted();
   }

   private int purchasedItemCount() {
      return InventoryActions.countItems(this.purchasedItem());
   }

   private LivingEntity nearestUnevaluatedMerchant() {
      return minecraftClient.world
         .getEntitiesByClass(
            MerchantEntity.class, minecraftClient.player.getBoundingBox().expand((128.0D)), candidateMerchant -> candidateMerchant.isAlive() && !this.evaluatedMerchants.contains(candidateMerchant.getUuid())
         )
         .stream()
         .min(Comparator.comparingDouble(distanceCandidate -> minecraftClient.player.squaredDistanceTo(distanceCandidate)))
         .orElse(null);
   }

   private long elapsedWorkMillis() {
      return this.workStartedAt > 0L ? System.currentTimeMillis() - this.workStartedAt : 0L;
   }

   private void craftGoldWithSplitStacks(ScreenHandler handler) {
      for (int splitPass = 0; splitPass < 9 && this.largestGoldIngotSlot(handler) != null && this.largestGoldIngotSlot(handler).getStack().getCount() >= 9; splitPass++) {
         InventoryActions.clickSlot(this.largestGoldIngotSlot(handler), 0, SlotActionType.PICKUP, false);
         InventoryActions.clickSlot(-999, 0, SlotActionType.QUICK_CRAFT, false);

         for (int gridSlot = 1; gridSlot <= 9; gridSlot++) {
            InventoryActions.clickSlot(gridSlot, 1, SlotActionType.QUICK_CRAFT, false);
         }

         InventoryActions.clickSlot(-999, 2, SlotActionType.QUICK_CRAFT, false);
         this.returnCursorGold(handler);
      }

      InventoryActions.clickSlot(0, 0, SlotActionType.QUICK_MOVE, false);

      for (int cleanupSlot = 1; cleanupSlot <= 9; cleanupSlot++) {
         if (handler.getSlot(cleanupSlot) != null && handler.getSlot(cleanupSlot).hasStack()) {
            InventoryActions.clickSlot(cleanupSlot, 0, SlotActionType.QUICK_MOVE, false);
         }
      }
   }

   private boolean tickChestUnloading() {
      if (!this.isEnabled()) {
         this.unloadingGoods = false;
         return true;
      }

      if (minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler) {
         if (++this.unloadTicks > 200) {
            return true;
         }

         if (InventoryActions.countItems(Items.GOLD_INGOT) + InventoryActions.countItems(Items.GOLD_BLOCK) + this.purchasedItemCount() < this.lowestRemainingGoods) {
            this.lowestRemainingGoods = InventoryActions.countItems(Items.GOLD_INGOT) + InventoryActions.countItems(Items.GOLD_BLOCK) + this.purchasedItemCount();
            this.unloadStallTicks = 0;
         }

         if (++this.unloadStallTicks > 20) {
            return true;
         }

         for (Slot slot : containerHandler.slots) {
            if (slot.id >= containerHandler.getRows() * 9 && slot.hasStack() && this.isUnloadItem(slot.getStack().getItem())) {
               this.emptyUnloadTicks = 0;
               if (this.unloadTicks % 2 == 0) {
                  InventoryActions.clickSlot(slot, 0, SlotActionType.QUICK_MOVE, false);
               }

               return false;
            }
         }

         if (this.hasUnloadItems()) {
            this.emptyUnloadTicks = 0;
            return false;
         } else {
            return ++this.emptyUnloadTicks >= 3;
         }
      } else {
         if (this.unloadingGoods && this.unloadTicks == 0) {
            ChatMessages.printPrefixed("Сундук нашёлся, но не открылся (" + (this.blockWithinReach(this.storageChestPosition) ? ("стою рядом, клик не открывает") : ("не смог подойти")) + ") — сдача пропущена.");
         }

         return true;
      }
   }

   private boolean tickGoldCrafting() {
      if (!this.isEnabled()) {
         this.craftingGold = false;
         return true;
      }

      if (!(minecraftClient.currentScreen instanceof CraftingScreen)) {
         if (this.craftingGold) {
            ChatMessages.printPrefixed("Верстак нашёлся, но не открылся (" + (this.blockWithinReach(this.craftingTablePosition) ? ("стою рядом, клик не открывает") : ("не смог подойти")) + ") — крафт пропущен.");
         }

         return true;
      } else {
         if (!(minecraftClient.player.currentScreenHandler instanceof CraftingScreenHandler)) {
            return true;
         }

         if (this.goldIngotCount() < 9) {
            return true;
         }

         if (this.craftAttempts >= 256) {
            return true;
         }

         if (this.craftDelayTicks > 0) {
            this.craftDelayTicks--;
            return false;
         }

         if (this.goldIngotSlotCount(minecraftClient.player.currentScreenHandler) >= 9) {
            this.craftGoldWithWholeStacks(minecraftClient.player.currentScreenHandler);
         } else {
            this.craftGoldWithSplitStacks(minecraftClient.player.currentScreenHandler);
         }

         this.craftAttempts++;
         this.craftDelayTicks = 4;
         return false;
      }
   }

   private void applyDirectRotation(RotationAngles angles) {
      minecraftClient.player.setYaw(angles.getYaw());
      minecraftClient.player.setPitch(MathHelper.clamp(angles.getPitch(), (-90.0F), (90.0F)));
      minecraftClient.player.headYaw = angles.getYaw();
      minecraftClient.player.bodyYaw = angles.getYaw();
      minecraftClient.player.lastYaw = angles.getYaw();
      minecraftClient.player.lastPitch = MathHelper.clamp(angles.getPitch(), (-90.0F), (90.0F));
   }

   private boolean hasEmeraldReserve() {
      return InventoryActions.countItems(Items.EMERALD) >= 16;
   }

   private int goldIngotSlotCount(ScreenHandler handler) {
      int ingotSlots = 0;

      for (Slot slot : handler.slots) {
         if (slot.id >= 10 && slot.hasStack() && slot.getStack().isOf(Items.GOLD_INGOT)) {
            ingotSlots++;
         }
      }

      return ingotSlots;
   }

   private int bestTradeIndex(MerchantScreenHandler handler) {
      if (handler.getRecipes() == null) {
         return -1;
      }

      int bestIndex = -1;
      int bestScore = -1;

      for (int offerIndex = 0; offerIndex < handler.getRecipes().size(); offerIndex++) {
         TradeOffer candidateOffer = handler.getRecipes().get(offerIndex);
         if (candidateOffer != null
            && !candidateOffer.isDisabled()
            && this.offerSellsItem(candidateOffer, this.purchasedItem())
            && (
               candidateOffer.getSellItem().getCount() + (this.offerUsesEmeralds(candidateOffer) ? 10000 : 0) > bestScore
                  || candidateOffer.getSellItem().getCount() + (this.offerUsesEmeralds(candidateOffer) ? 10000 : 0) == bestScore && offerIndex < bestIndex
            )) {
            bestScore = candidateOffer.getSellItem().getCount() + (this.offerUsesEmeralds(candidateOffer) ? 10000 : 0);
            bestIndex = offerIndex;
         }
      }

      return bestIndex;
   }

   private void appendTickDelay(EventSequenceRunner.EventSequence sequence, int delayTicks) {
      if (delayTicks > 0) {
         sequence.then(PlayerTickEvent.class, event -> {
            this.sequenceDelayTicks = 0;
            return true;
         });
         sequence.then(PlayerTickEvent.class, delayEvent -> ++this.sequenceDelayTicks >= delayTicks);
      }
   }

   private int cursorGoldCount() {
      return minecraftClient.player.currentScreenHandler.getCursorStack().isOf(Items.GOLD_INGOT)
         ? minecraftClient.player.currentScreenHandler.getCursorStack().getCount()
         : 0;
   }

   private boolean merchantVisibleFrom(Vec3d position, LivingEntity entity) {
      for (Vec3d sightPoint : new Vec3d[]{entity.getEyePos(), entity.getBoundingBox().getCenter()}) {
         if (minecraftClient.world.raycast(new RaycastContext(position, sightPoint, ShapeType.COLLIDER, FluidHandling.NONE, minecraftClient.player)).getType()
            == Type.MISS) {
            return true;
         }
      }

      return false;
   }

   private void closeCurrentScreenIfReady() {
      if (clientReady()) {
         if (minecraftClient.currentScreen instanceof HandledScreen) {
            InventoryActions.closeInventory(true);
            minecraftClient.player.closeHandledScreen();
         } else if (minecraftClient.currentScreen != null) {
            minecraftClient.setScreen(null);
         }
      }
   }

   private void appendMerchantApproach(EventSequenceRunner.EventSequence sequence) {
      sequence.then(PlayerTickEvent.class, event -> {
         if (!this.isEnabled() || this.currentMerchant == null) {
            return true;
         }

         if (this.refreshMerchant() == null) {
            return true;
         }

         this.merchantApproachTicks = 0;
         this.movedMerchantRepaths = 0;
         this.stationaryTicks = 0;
         this.stuckRepaths = 0;
         this.merchantApproachReady = false;
         this.previousPathPosition = minecraftClient.player.getEntityPos();
         this.merchantPathPosition = this.merchantApproachPosition(this.refreshMerchant());
         this.startBaritonePath(this.merchantPathPosition);
         return true;
      });
      sequence.then(
         PlayerTickEvent.class,
         approachEvent -> {
            if (!this.isEnabled() || this.currentMerchant == null) {
               return true;
            }

            if (this.refreshMerchant() == null) {
               return true;
            }

            if (minecraftClient.player.squaredDistanceTo(this.refreshMerchant()) < (4.0D)) {
               this.merchantApproachTicks = 0;
               this.merchantApproachReady = true;
               this.stopBaritone();
               return true;
            }

            if (minecraftClient.player.squaredDistanceTo(this.refreshMerchant()) < (9.0D) && this.merchantVisible(this.refreshMerchant())) {
               this.merchantApproachTicks = 0;
               this.merchantApproachReady = true;
               this.stopBaritone();
               return true;
            }

            this.merchantApproachTicks++;
            if (minecraftClient.player.getEntityPos().squaredDistanceTo(this.previousPathPosition) < (0.0025D)) {
               this.stationaryTicks++;
            } else {
               this.stationaryTicks = 0;
            }

            this.previousPathPosition = minecraftClient.player.getEntityPos();
            if (this.merchantApproachTicks % 20 == 0 && !this.merchantApproachPosition(this.refreshMerchant()).equals(this.merchantPathPosition)) {
               this.movedMerchantRepaths++;
               this.merchantPathPosition = this.merchantApproachPosition(this.refreshMerchant());
               this.startBaritonePath(this.merchantPathPosition);
            }

            if (this.merchantApproachTicks > 12) {
               this.retryStuckPath(this.merchantPathPosition);
            }

            if (minecraftClient.player.distanceTo(this.refreshMerchant()) < minecraftClient.player.getEntityInteractionRange() + 1.0
               && this.merchantVisible(this.refreshMerchant())
               && this.merchantApproachTicks > 100) {
               this.merchantApproachReady = true;
               this.stopBaritone();
               return true;
            }

            if (this.merchantApproachTicks <= 600 && (this.merchantApproachTicks <= 12 || this.stationaryTicks <= 12 || this.stuckRepaths < 3)) {
               return false;
            }

            this.stopBaritone();
            return true;
         }
      );
      sequence.then(
         PlayerTickEvent.class,
         approachEvent -> {
            if (!this.isEnabled() || this.currentMerchant == null) {
               return true;
            } else if (!this.merchantApproachReady) {
               return true;
            } else if (minecraftClient.currentScreen instanceof MerchantScreen) {
               return true;
            } else if (this.refreshMerchant() == null) {
               return true;
            } else {
               this.releaseSneaking();
               this.aimAtMerchant(this.refreshMerchant());
               if (minecraftClient.currentScreen instanceof MerchantScreen) {
                  return true;
               } else if (CombatTargeting.rayHitsEntityBounds(
                  this.refreshMerchant(),
                  minecraftClient.player.getEntityInteractionRange() + 1.0,
                  minecraftClient.player.getYaw(),
                  minecraftClient.player.getPitch()
               )) {
                  this.nearAimWaitTicks = 0;
                  return true;
               } else if (minecraftClient.player.distanceTo(this.refreshMerchant()) < (3.0F) && this.merchantVisible(this.refreshMerchant())) {
                  this.nearAimWaitTicks++;
                  return this.nearAimWaitTicks >= 6;
               } else {
                  this.nearAimWaitTicks = 0;
                  return ++this.aimWaitTicks >= 30;
               }
            }
         }
      );
   }

   private void aimAtMerchant(LivingEntity entity) {
      if (this.aimStartedAt == 0L) {
         this.aimStartedAt = System.currentTimeMillis();
      }

      if (System.currentTimeMillis() - this.aimParametersChangedAt > this.aimParameterIntervalMillis) {
         this.aimParametersChangedAt = System.currentTimeMillis();
         this.aimParameterIntervalMillis = (long)ClientMath.randomFloat((260.0F), (620.0F));
         this.aimSpeed = ClientMath.randomFloat((252.0F), (274.0F));
         this.aimEaseAngle = ClientMath.randomFloat((14.0F), (24.0F));
         this.aimAcceleration = ClientMath.randomFloat((13.0F), (21.0F));
         this.yawPitchCoupling = ClientMath.randomFloat((0.04F), (0.12F)) * (ThreadLocalRandom.current().nextBoolean() ? 1.0F : (-1.0F));
         this.aimStopAngle = ClientMath.randomFloat((0.4F), (0.9F));
      }

      RotationAngles desiredAngles = SmoothedAimVector.anglesForDirection(
         entity.getEntityPos()
            .add(0.0, MathHelper.clamp(entity.getHeight() * (0.62D), (0.1D), Math.max((0.1D), entity.getHeight() - (0.1D))), 0.0)
            .subtract(minecraftClient.player.getEyePos())
      );
      float yawError = MathHelper.wrapDegrees(desiredAngles.getYaw() - minecraftClient.player.getYaw());
      float pitchError = MathHelper.clamp(desiredAngles.getPitch(), (-90.0F), (90.0F)) - minecraftClient.player.getPitch();
      float angularDistance = (float)Math.hypot(yawError, pitchError);
      float easedSpeed = this.aimSpeed
         * (float)Easings.CIRC_OUT.apply(ClientMath.clampUnit(angularDistance / this.aimEaseAngle))
         * MathHelper.lerp(MathHelper.clamp((float)(System.currentTimeMillis() - this.aimStartedAt) / (220.0F), 0.0F, 1.0F), (0.78F), 1.0F);
      this.yawVelocity = this.yawVelocity
         + MathHelper.clamp(
            easedSpeed * (angularDistance < (0.001F) ? 0.0F : yawError / angularDistance) * (1.25F) - this.yawVelocity,
            -this.aimAcceleration * (1.2F),
            this.aimAcceleration * (1.2F)
         );
      this.pitchVelocity = this.pitchVelocity
         + MathHelper.clamp(
            easedSpeed * (angularDistance < (0.001F) ? 0.0F : pitchError / angularDistance) + this.yawVelocity * this.yawPitchCoupling - this.pitchVelocity,
            -this.aimAcceleration * (0.68F),
            this.aimAcceleration * (0.68F)
         );
      this.yawVelocity = MathHelper.clamp(this.yawVelocity, -Math.abs(yawError), Math.abs(yawError));
      this.pitchVelocity = MathHelper.clamp(
         this.pitchVelocity, -Math.abs(pitchError) - Math.abs(this.yawVelocity * this.yawPitchCoupling), Math.abs(pitchError) + Math.abs(this.yawVelocity * this.yawPitchCoupling)
      );
      if (angularDistance < this.aimStopAngle) {
         this.yawVelocity = this.pitchVelocity = 0.0F;
      }

      RotationController.getInstance()
         .requestForDuration(
            desiredAngles,
            new RotationLimits(
               Math.min(Math.abs(this.yawVelocity), CombatTargeting.yawStepWithRandomDivisor(desiredAngles, 1.0F, (1.5F))),
               Math.min(Math.abs(this.pitchVelocity), CombatTargeting.pitchStepWithRandomDivisor(desiredAngles, (2.5F), (4.0F))),
               (30.0F),
               (30.0F)
            ),
            (1000L)
         );
   }

   private int nearbyMerchantCount() {
      return minecraftClient.world
         .getEntitiesByClass(MerchantEntity.class, minecraftClient.player.getBoundingBox().expand((128.0D)), Entity::isAlive)
         .size();
   }

   private boolean offerUsesEmeralds(TradeOffer offer) {
      return offer.getDisplayedFirstBuyItem().isOf(Items.EMERALD) ? true : offer.getDisplayedSecondBuyItem().isOf(Items.EMERALD);
   }

   private void appendMerchantPurchase(EventSequenceRunner.EventSequence sequence) {
      this.appendEmeraldExchange(sequence, true);
      sequence.then(PlayerTickEvent.class, event -> {
         this.nearAimWaitTicks = 0;
         this.aimWaitTicks = 0;
         this.merchantOpenTicks = 0;
         this.merchantSearchTicks = 0;
         this.recipeWaitTicks = 0;
         this.tradeSelectionTicks = 0;
         this.closeCurrentScreenIfReady();
         this.currentMerchant = null;
         return minecraftClient.currentScreen == null || !(minecraftClient.currentScreen instanceof HandledScreen);
      });
      sequence.then(PlayerTickEvent.class, purchaseEvent -> {
         if (!this.isEnabled()) {
            return true;
         }

         if (this.desiredTradeAvailable && !this.inventoryNearlyFull() && !this.cycleExpired()) {
            this.currentMerchant = this.nearestMerchant(true);
            if (this.currentMerchant != null) {
               this.resetAimState();
               this.merchantSearchTicks = 0;
               return true;
            } else {
               return true;
            }
         } else {
            this.currentMerchant = null;
            return true;
         }
      });
      this.appendMerchantApproach(sequence);
      this.appendMerchantOpen(sequence);
      sequence.then(PlayerTickEvent.class, purchaseEvent -> {
         if (minecraftClient.currentScreen instanceof MerchantScreen purchaseScreen) {
            if (this.bestTradeIndex(purchaseScreen.getScreenHandler()) < 0) {
               return true;
            } else if (this.ensureTradeSelected(purchaseScreen)) {
               this.tradeSelectionTicks = 0;
               return true;
            } else {
               return ++this.tradeSelectionTicks >= 6;
            }
         } else {
            return true;
         }
      });
      sequence.then(PlayerTickEvent.class, purchaseEvent -> {
         if (minecraftClient.currentScreen instanceof MerchantScreen purchaseScreen) {
            if (this.bestTradeIndex(purchaseScreen.getScreenHandler()) < 0) {
               return true;
            } else if (this.ensureTradeSelected(purchaseScreen) && this.tradeReady(purchaseScreen)) {
               this.emeraldsBeforePurchase = InventoryActions.countItems(Items.EMERALD);
               this.goodsBeforePurchase = this.purchasedItemCount();
               this.purchaseRetryClicks = 0;
               InventoryActions.clickSlot(purchaseScreen.getScreenHandler().getSlot(2), 0, SlotActionType.QUICK_MOVE, false);
               return true;
            } else {
               return false;
            }
         } else {
            return true;
         }
      });
      sequence.then(PlayerTickEvent.class, purchaseEvent -> {
         if (this.purchaseObserved()) {
            return true;
         }

         if (minecraftClient.currentScreen instanceof MerchantScreen purchaseScreen) {
            if (this.bestTradeIndex(purchaseScreen.getScreenHandler()) < 0) {
               return true;
            }

            if (this.tradeOutputUnavailable(purchaseScreen)) {
               return true;
            }

            if (this.purchaseRetryClicks < 3) {
               this.purchaseRetryClicks++;
               if (this.ensureTradeSelected(purchaseScreen) && this.tradeReady(purchaseScreen)) {
                  InventoryActions.clickSlot(purchaseScreen.getScreenHandler().getSlot(2), 0, SlotActionType.QUICK_MOVE, false);
                  return false;
               } else {
                  return this.purchaseRetryClicks >= 3;
               }
            } else {
               return true;
            }
         } else {
            return true;
         }
      });
      sequence.then(PlayerTickEvent.class, purchaseEvent -> {
         this.finishMerchantVisit();
         return true;
      });
   }

   private BlockPos blockApproachPosition(BlockPos position) {
      BlockPos nearestPosition = null;
      double nearestDistanceSquared = (1.7976931348623157E308D);

      for (int offsetX = -1; offsetX <= 1; offsetX++) {
         for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
               if ((offsetX != 0 || offsetZ != 0)
                  && this.canStandAt(position.add(offsetX, offsetY, offsetZ))
                  && !(minecraftClient.player.squaredDistanceTo(Vec3d.ofBottomCenter(position.add(offsetX, offsetY, offsetZ))) >= nearestDistanceSquared)) {
                  nearestDistanceSquared = minecraftClient.player.squaredDistanceTo(Vec3d.ofBottomCenter(position.add(offsetX, offsetY, offsetZ)));
                  nearestPosition = position.add(offsetX, offsetY, offsetZ);
               }
            }
         }
      }

      return nearestPosition;
   }

   private boolean hasContainerScreen() {
      return minecraftClient.currentScreen instanceof HandledScreen && minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler;
   }

   private boolean tickReturnToStart() {
      if (this.isEnabled() && this.desiredTradeAvailable && this.cycleStartPosition != null) {
         if (minecraftClient.player.squaredDistanceTo(Vec3d.ofBottomCenter(this.cycleStartPosition)) < (2.5D)) {
            if (this.returningToStart) {
               this.stopBaritone();
            }

            this.returningToStart = false;
            return true;
         } else {
            if (!this.returningToStart) {
               this.returnPathTicks = 0;
               this.stationaryTicks = 0;
               this.stuckRepaths = 0;
               this.previousPathPosition = minecraftClient.player.getEntityPos();
               this.startBaritonePath(this.cycleStartPosition);
               this.returningToStart = true;
               return false;
            }

            this.returnPathTicks++;
            if (minecraftClient.player.getEntityPos().squaredDistanceTo(this.previousPathPosition) < (0.0025D)) {
               this.stationaryTicks++;
            } else {
               this.stationaryTicks = 0;
            }

            this.previousPathPosition = minecraftClient.player.getEntityPos();
            if (this.returnPathTicks > 12) {
               this.retryStuckPath(this.cycleStartPosition);
            }

            if ((this.returnPathTicks <= 12 || this.stationaryTicks <= 12 || this.stuckRepaths < 3) && this.returnPathTicks <= 600) {
               return false;
            }

            this.stopBaritone();
            this.returningToStart = false;
            return true;
         }
      } else {
         this.returningToStart = false;
         return true;
      }
   }

   private void restoreBaritoneSettings() {
      if (minecraftClient.player != null) {
         this.stopBaritone();

         for (String restoreCommand : BARITONE_RESTORE_COMMANDS) {
            ChatMessages.sendBaritoneCommand(restoreCommand);
         }

         minecraftClient.player.setSneaking(false);
      }
   }

   private void craftGoldWithWholeStacks(ScreenHandler handler) {
      for (int gridSlot = 1; gridSlot <= 9 && this.largestGoldIngotSlot(handler) != null; gridSlot++) {
         InventoryActions.clickSlot(this.largestGoldIngotSlot(handler), 0, SlotActionType.PICKUP, false);
         InventoryActions.clickSlot(gridSlot, 0, SlotActionType.PICKUP, false);
      }

      this.returnCursorGold(handler);
      InventoryActions.clickSlot(0, 0, SlotActionType.QUICK_MOVE, false);

      for (int cleanupSlot = 1; cleanupSlot <= 9; cleanupSlot++) {
         if (handler.getSlot(cleanupSlot) != null && handler.getSlot(cleanupSlot).hasStack()) {
            InventoryActions.clickSlot(cleanupSlot, 0, SlotActionType.QUICK_MOVE, false);
         }
      }
   }

   private BlockPos nearestStorageChest() {
      BlockPos position = null;
      double nearestDistanceSquared = (1.7976931348623157E308D);

      for (int chunkX = minecraftClient.player.getChunkPos().x - 9; chunkX <= minecraftClient.player.getChunkPos().x + 9; chunkX++) {
         for (int chunkZ = minecraftClient.player.getChunkPos().z - 9; chunkZ <= minecraftClient.player.getChunkPos().z + 9; chunkZ++) {
            if (minecraftClient.world.isChunkLoaded(chunkX, chunkZ)) {
               for (BlockEntity blockEntity : minecraftClient.world.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                  if (blockEntity instanceof ChestBlockEntity
                     && !(blockEntity instanceof TrappedChestBlockEntity)
                     && !(minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(blockEntity.getPos())) > (16384.0D))
                     && !(minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(blockEntity.getPos())) >= nearestDistanceSquared)) {
                     nearestDistanceSquared = minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(blockEntity.getPos()));
                     position = blockEntity.getPos().toImmutable();
                  }
               }
            }
         }
      }

      return position;
   }

   public static AutoVillager getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoVillager();
      }

      return INSTANCE;
   }

   @Override
   protected void onEnable() {
      this.craftingTablePosition = null;
      this.craftingApproachPosition = null;
      this.storageChestPosition = null;
      this.storageApproachPosition = null;
      this.currentMerchant = null;
      this.exchangingEmeralds = false;
      this.craftingGold = false;
      this.unloadingGoods = false;
      this.desiredTradeAvailable = true;
      this.blockOpenTicks = 0;
      this.blockApproachTicks = 0;
      this.blockPathRefreshes = 0;
      this.returnPathTicks = 0;
      this.baritoneActive = true;
      this.resetAimState();
      this.craftAttempts = 0;
      this.craftDelayTicks = 0;
      this.waitingToReconnect = false;
      this.waitingToRestart = false;
      this.restartPending = false;
      this.rejoiningAnarchy = false;
      this.restoreUseKey();
      this.reconnectAtMillis = 0L;
      this.restartAtMillis = 0L;
      this.emeraldsBeforePurchase = 0;
      this.goodsBeforePurchase = 0;
      this.purchaseRetryClicks = 0;
      this.nearAimWaitTicks = 0;
      this.aimWaitTicks = 0;
      this.merchantOpenTicks = 0;
      this.merchantApproachTicks = 0;
      this.movedMerchantRepaths = 0;
      this.merchantSearchTicks = 0;
      this.recipeWaitTicks = 0;
      this.tradeSelectionTicks = 0;
      this.resetExchangeState();
      this.returningToStart = false;
      this.visitedMerchants.clear();
      if (!this.preserveTradeEvaluations) {
         this.evaluatedMerchants.clear();
         this.merchantTradeYields.clear();
      }

      this.preserveTradeEvaluations = false;
      if (minecraftClient.player != null && minecraftClient.world != null) {
         if (this.cycleStartPosition == null) {
            this.cycleStartPosition = minecraftClient.player.getBlockPos().toImmutable();
         }

         if (this.workStartedAt == 0L) {
            this.workStartedAt = System.currentTimeMillis();
         }

         this.cycleStartedAt = System.currentTimeMillis();
         this.sequenceRunner.replaceSequence(this.buildTradingCycle(new EventSequenceRunner.EventSequence().timeoutEvents(12000)));
      } else {
         this.setEnabled(false);
      }
   }

   private int goldIngotCount() {
      return InventoryActions.countItems(Items.GOLD_INGOT);
   }

   private void renderStatus(HudRenderEvent event) {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         if (this.isEnabled() || this.restartPending || this.workStartedAt != 0L) {
            GuiTextRenderer.drawCentered(GuiFont.SEMI, ("AutoVillager — Информация"), event.getWidth() / 2.0F, this.hudTop(), (7.0F), HudStyle.textColor);
            GuiTextRenderer.drawCentered(
               GuiFont.SEMI, ("Текущее состояние: WORKING"), event.getWidth() / 2.0F, this.hudTop() + (10.0F), (7.0F), HudStyle.textColor
            );
            GuiTextRenderer.drawCentered(
               GuiFont.SEMI,
               "Время работы: " + formatElapsedTime(this.elapsedWorkMillis()),
               event.getWidth() / 2.0F,
               this.hudTop() + (20.0F),
               (7.0F),
               HudStyle.textColor
            );
            if (this.reconnectCountdownText() != null) {
               GuiTextRenderer.drawCentered(
                  GuiFont.SEMI, this.reconnectCountdownText(), event.getWidth() / 2.0F, this.hudTop() + (31.0F), (7.0F), HudStyle.textColor
               );
            }
         }
      }
   }

   private boolean cycleExpired() {
      return this.cycleStartedAt > 0L && System.currentTimeMillis() - this.cycleStartedAt >= (300000L);
   }

   private boolean isFood(ItemStack stack) {
      return !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
   }

   private boolean hasUnloadItems() {
      return InventoryActions.countItems(Items.GOLD_INGOT) > 0 || InventoryActions.countItems(Items.GOLD_BLOCK) > 0 || this.purchasedItemCount() > 0;
   }

   private void appendChestUnloading(EventSequenceRunner.EventSequence sequence, boolean onlyWhenNeeded) {
      sequence.then(PlayerTickEvent.class, event -> {
         if (!this.isEnabled()) {
            return true;
         }

         this.closeCurrentScreenIfReady();
         this.blockOpenTicks = 0;
         this.emptyUnloadTicks = 0;
         this.unloadTicks = 0;
         this.unloadStallTicks = 0;
         this.lowestRemainingGoods = (2147483647);
         this.unloadingGoods = this.hasUnloadItems() && (!onlyWhenNeeded || this.inventoryNeedsCompaction() || this.hasGoldBlocks());
         if (!this.unloadingGoods) {
            this.storageChestPosition = null;
            this.storageApproachPosition = null;
            return minecraftClient.currentScreen == null || !(minecraftClient.currentScreen instanceof HandledScreen);
         }

         this.storageChestPosition = this.nearestStorageChest();
         this.storageApproachPosition = this.storageChestPosition != null ? this.blockApproachPosition(this.storageChestPosition) : null;
         this.unloadingGoods = this.storageChestPosition != null;
         if (this.storageChestPosition == null && !onlyWhenNeeded) {
            ChatMessages.printPrefixed(("Сундук не найден в радиусе 128 блоков — сдача пропускается."));
         }

         return minecraftClient.currentScreen == null || !(minecraftClient.currentScreen instanceof HandledScreen);
      });
      sequence.then(PlayerTickEvent.class, unloadEvent -> {
         if (this.isEnabled() && this.unloadingGoods) {
            this.blockApproachTicks = 0;
            this.blockPathRefreshes = 0;
            this.stationaryTicks = 0;
            this.stuckRepaths = 0;
            this.previousPathPosition = minecraftClient.player.getEntityPos();
            this.startBaritonePath(this.storageApproachPosition != null ? this.storageApproachPosition : this.storageChestPosition);
            return true;
         } else {
            return true;
         }
      });
      sequence.then(
         PlayerTickEvent.class,
         unloadEvent -> this.isEnabled() && this.unloadingGoods && this.storageChestPosition != null ? this.tickBlockApproach(this.storageChestPosition, this.storageApproachPosition) : true
      );
      sequence.then(PlayerTickEvent.class, unloadEvent -> {
         if (this.unloadingGoods) {
            this.stopBaritone();
         }

         return true;
      });
      sequence.then(PlayerTickEvent.class, unloadEvent -> {
         if (!this.isEnabled() || !this.unloadingGoods || this.storageChestPosition == null) {
            return true;
         }

         if (this.hasContainerScreen()) {
            this.blockOpenTicks = 0;
            return true;
         }

         if (!this.blockWithinReach(this.storageChestPosition)) {
            if (this.blockOpenTicks % 20 == 1) {
               this.startBaritonePath(this.storageApproachPosition != null ? this.storageApproachPosition : this.storageChestPosition);
            }

            return ++this.blockOpenTicks >= 30;
         } else {
            if (this.blockOpenTicks % 5 == 0) {
               this.interactTargetBlock(this.storageChestPosition, this.storageApproachPosition);
            }

            return ++this.blockOpenTicks >= 30;
         }
      });
      sequence.then(PlayerTickEvent.class, unloadEvent -> this.tickChestUnloading());
      sequence.then(PlayerTickEvent.class, unloadEvent -> {
         this.closeHandledScreenIfReady();
         this.unloadingGoods = false;
         InventoryActions.pickupAllPacket();
         return true;
      });
   }

   private Direction interactionFace(BlockPos position, BlockPos approachPosition) {
      if (approachPosition == null || approachPosition.getX() - position.getX() == 0 && approachPosition.getY() - position.getY() == 0 && approachPosition.getZ() - position.getZ() == 0) {
         if (Math.abs(minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).x)
            >= Math.abs(minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).z)) {
            return minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).x >= 0.0 ? Direction.EAST : Direction.WEST;
         } else {
            return minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
         }
      } else if (Math.abs(approachPosition.getX() - position.getX()) >= Math.abs(approachPosition.getY() - position.getY())
         && Math.abs(approachPosition.getX() - position.getX()) >= Math.abs(approachPosition.getZ() - position.getZ())) {
         return approachPosition.getX() - position.getX() >= 0 ? Direction.EAST : Direction.WEST;
      } else if (Math.abs(approachPosition.getZ() - position.getZ()) >= Math.abs(approachPosition.getY() - position.getY())) {
         return approachPosition.getZ() - position.getZ() >= 0 ? Direction.SOUTH : Direction.NORTH;
      } else {
         return approachPosition.getY() - position.getY() >= 0 ? Direction.UP : Direction.DOWN;
      }
   }
}
