package com.mouseclient.network;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.combat.MovementInputEvent;
import com.mouseclient.modules.impl.misc.AuctionHelper;
import com.mouseclient.modules.impl.misc.FilteredTextSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.Stopwatch;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

@ModuleInfo(
   name = "AutoMine",
   description = "Перебирает анархии, находит автошахту с таймером меньше минуты, релогается на точке и копает алмазы.",
   category = ModuleCategory.MISC
)
public final class AutoMine extends Module {
   private static AutoMine INSTANCE;
   private static final Pattern MINE_TIMER_PATTERN = Pattern.compile(("^(\\d{1,2}):(\\d{2})$"));
   private static final Pattern TELEPORT_DELAY_PATTERN = Pattern.compile((".*Телепортация через[:\\s]*(\\d+(?:[.,]\\d+)?)\\s*сек.*"));
   private static final Pattern PURCHASE_MESSAGE_PATTERN = Pattern.compile((".*успешно купили.*"));
   private static final String[] SPOOKYTIME_ANARCHIES = new String[]{
      ("an101"),
      ("an102"),
      ("an103"),
      ("an104"),
      ("an105"),
      ("an106"),
      ("an107"),
      ("an108"),
      ("an201"),
      ("an202"),
      ("an203"),
      ("an204"),
      ("an205"),
      ("an206"),
      ("an207"),
      ("an208"),
      ("an209"),
      ("an210"),
      ("an301"),
      ("an302"),
      ("an303"),
      ("an304"),
      ("an305"),
      ("an501"),
      ("an502"),
      ("an503"),
      ("an601"),
      ("an602"),
      ("an603")
   };
   private static final ArrayList<String> FUNTIME_ANARCHIES = new ArrayList<>();
   private static final int[][] WAITING_POSITIONS;
   private static final BlockPos ENTRANCE_START;
   private static final BlockPos ENTRANCE_END;
   private static final BlockPos MINE_MIN;
   private static final BlockPos MINE_MAX;
   private static final String[] BARITONE_SETUP_COMMANDS;
   public ModeSetting server;
   public FilteredTextSetting homeAnarchy;
   public DecimalSetting experienceStackPrice;
   private AutoMine.MiningPhase phase;
   private final Stopwatch stateTimer = new Stopwatch();
   private final Stopwatch miningWindowTimer = new Stopwatch();
   private final Stopwatch purchaseTimer = new Stopwatch();
   private final Stopwatch reconnectTimer = new Stopwatch();
   private ServerInfo lastServer;
   private boolean purchasePending;
   private boolean purchaseConfirmed;
   private String anarchyCommand;
   private boolean warpAttempted;
   private boolean baritoneConfigured;
   private int[] waitingPosition;
   private BlockPos targetPosition;
   private boolean miningTargetActive;
   private int sampledOreCount;
   private long scanWindowMillis;
   private final ArrayList<BlockPos> orePositions = new ArrayList<>();
   private long nextOreScanAt;
   private long collisionStartedAt;
   private long nextPathSearchAt;
   private final ArrayDeque<BlockPos> entrancePath = new ArrayDeque<>();
   private Runnable deferredAction;
   private long deferredActionAt;
   private ItemStack savedOffhandStack = ItemStack.EMPTY;
   private boolean eating;
   private boolean booleanValue9;
   private final EventListener<ClientTickEvent> clientTickListener = event -> {
      if (event.isPre()) {
         if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.interactionManager != null) {
            if (minecraftClient.getCurrentServerEntry() != null) {
               this.lastServer = minecraftClient.getCurrentServerEntry();
            }

            this.reconnectTimer.reset();
            if (this.server.isSelected(("FunTime"))) {
               minecraftClient.options.attackKey.setPressed(false);
            }

            if (!this.baritoneConfigured) {
               for (String setupCommand : BARITONE_SETUP_COMMANDS) {
                  ChatMessages.sendBaritoneCommand(setupCommand);
               }

               this.baritoneConfigured = true;
            }

            if (this.targetPosition == null) {
               this.clearMiningTargetState();
               this.orePositions.clear();
            }

            this.targetPosition = null;
            if (!this.server.isSelected(("FunTime")) || !this.tickEating()) {
               if (this.deferredAction != null) {
                  if (System.currentTimeMillis() >= this.deferredActionAt) {
                     Runnable scheduledAction = this.deferredAction;
                     this.deferredAction = null;
                     scheduledAction.run();
                  }
               } else if (this.phase != AutoMine.MiningPhase.SCAN_TITLE
                     && this.phase != AutoMine.MiningPhase.STAND_READY
                     && this.phase != AutoMine.MiningPhase.DIG_MINE
                  || !minecraftClient.player.getMainHandStack().isIn(ItemTags.PICKAXES)
                  || !minecraftClient.player.getMainHandStack().isDamageable()
                  || minecraftClient.player.getMainHandStack().getMaxDamage() - minecraftClient.player.getMainHandStack().getDamage() > 1
                     && !(
                        (minecraftClient.player.getMainHandStack().getMaxDamage() - minecraftClient.player.getMainHandStack().getDamage())
                              * (100.0D)
                              / minecraftClient.player.getMainHandStack().getMaxDamage()
                           < (6.0D)
                     )) {
                  switch (this.phase) {
                     case SCAN_TITLE:
                        ScoreboardObjective sidebarObjective = minecraftClient.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                        if (sidebarObjective != null) {
                           String[] titleParts = sidebarObjective.getDisplayName().getString().split(("-"));
                           if (titleParts.length > 1 && !titleParts[1].replaceAll(("\\D"), "").isEmpty()) {
                              this.anarchyCommand = "an" + titleParts[1].replaceAll(("\\D"), "");
                           }
                        }

                        Matcher scanTimerMatcher = MINE_TIMER_PATTERN.matcher(this.nearestHologramValue(("Автошахт"), MINE_TIMER_PATTERN, (150.0D)).orElse(""));
                        if (!scanTimerMatcher.matches()) {
                           if (this.scanWindowMillis != 0L) {
                              if (!this.miningWindowTimer.hasElapsed(this.scanWindowMillis)) {
                                 this.tickMiningTarget();
                                 return;
                              }

                              this.scanWindowMillis = 0L;
                           }

                           this.miningWindowTimer.reset();
                           if (!this.stateTimer.hasElapsed((3000L))) {
                              return;
                           }

                           if (this.warpAttempted) {
                              this.switchAnarchy();
                              return;
                           }

                           minecraftClient.player.networkHandler.sendChatCommand(("warp mine"));
                           this.warpAttempted = true;
                           this.phase = AutoMine.MiningPhase.WARP_TELEPORT;
                           this.stateTimer.reset();
                           return;
                        }

                        int secondsUntilReset = Integer.parseInt(scanTimerMatcher.group(1)) * 60 + Integer.parseInt(scanTimerMatcher.group(2));
                        if (this.anarchyCommand == null) {
                           this.switchAnarchy();
                           return;
                        }

                        if (this.scanWindowMillis == 0L || !this.miningWindowTimer.hasElapsed(this.scanWindowMillis)) {
                           if (this.tickMiningTarget()) {
                              if (this.scanWindowMillis == 0L) {
                                 this.scanWindowMillis = (17000L);
                                 this.miningWindowTimer.reset();
                              }

                              return;
                           }

                           if (this.scanWindowMillis == 0L && !this.miningWindowTimer.hasElapsed((600L))) {
                              return;
                           }
                        }

                        if (secondsUntilReset >= 60) {
                           this.switchAnarchy();
                           return;
                        }

                        this.waitingPosition = WAITING_POSITIONS[ThreadLocalRandom.current().nextInt(WAITING_POSITIONS.length)];
                        minecraftClient.interactionManager.cancelBlockBreaking();
                        RotationController.getInstance().reset();
                        this.miningTargetActive = false;
                        ChatMessages.sendBaritoneCommand("#go " + this.waitingPosition[0] + " " + this.waitingPosition[1] + " " + this.waitingPosition[2]);
                        this.phase = AutoMine.MiningPhase.BARITONE_RIDE;
                        break;
                     case WARP_TELEPORT:
                        for (ClientBossBar warpBossbar : ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents().values()) {
                           Matcher warpDelayMatcher = TELEPORT_DELAY_PATTERN.matcher(warpBossbar.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim());
                           if (warpDelayMatcher.matches()) {
                              this.scheduleAction((long)(Double.parseDouble(warpDelayMatcher.group(1).replace(',', '.')) * (1000.0D)) + (200L), () -> {
                                 this.phase = AutoMine.MiningPhase.SCAN_TITLE;
                                 this.stateTimer.reset();
                                 this.miningWindowTimer.reset();
                                 this.scanWindowMillis = 0L;
                              });
                              return;
                           }
                        }

                        if (this.stateTimer.hasElapsed((3000L))) {
                           this.phase = AutoMine.MiningPhase.SCAN_TITLE;
                           this.stateTimer.reset();
                           this.miningWindowTimer.reset();
                           this.scanWindowMillis = 0L;
                        }
                        break;
                     case BARITONE_RIDE:
                        if (minecraftClient.interactionManager.isBreakingBlock()) {
                           minecraftClient.interactionManager.cancelBlockBreaking();
                        }

                        if (minecraftClient.player
                              .squaredDistanceTo(this.waitingPosition[0] + (0.5D), this.waitingPosition[1], this.waitingPosition[2] + (0.5D))
                           > (2.25D)) {
                           return;
                        }

                        ChatMessages.sendBaritoneCommand(("#stop"));
                        this.phase = AutoMine.MiningPhase.STAND_READY;
                        break;
                     case STAND_READY:
                        Matcher readyTimerMatcher = MINE_TIMER_PATTERN.matcher(this.nearestHologramValue(("Автошахт"), MINE_TIMER_PATTERN, (150.0D)).orElse(""));
                        if (!readyTimerMatcher.matches()) {
                           return;
                        }

                        if (Integer.parseInt(readyTimerMatcher.group(1)) * 60 + Integer.parseInt(readyTimerMatcher.group(2)) <= (this.server.isSelected(("FunTime")) ? 3 : 2)) {
                           minecraftClient.interactionManager.cancelBlockBreaking();
                           minecraftClient.player.networkHandler.sendChatCommand(("hub"));
                           this.scheduleAction(this.server.isSelected(("FunTime")) ? (2500L) : (600L), () -> {
                              minecraftClient.player.networkHandler.sendChatCommand(this.anarchyCommand);
                              this.scheduleAction((1500L), () -> {
                                 this.phase = AutoMine.MiningPhase.DIG_MINE;
                                 this.stateTimer.reset();
                                 this.miningWindowTimer.reset();
                              });
                           });
                           return;
                        }

                        if (Integer.parseInt(readyTimerMatcher.group(1)) * 60 + Integer.parseInt(readyTimerMatcher.group(2)) >= 20) {
                           this.tickMiningTarget();
                           return;
                        }

                        if (minecraftClient.player
                              .squaredDistanceTo(this.waitingPosition[0] + (0.5D), this.waitingPosition[1], this.waitingPosition[2] + (0.5D))
                           > (2.25D)) {
                           minecraftClient.interactionManager.cancelBlockBreaking();
                           RotationController.getInstance().reset();
                           this.miningTargetActive = false;
                           ChatMessages.sendBaritoneCommand("#go " + this.waitingPosition[0] + " " + this.waitingPosition[1] + " " + this.waitingPosition[2]);
                           this.phase = AutoMine.MiningPhase.BARITONE_RIDE;
                        }
                        break;
                     case DIG_MINE:
                        if (this.miningWindowTimer.hasElapsed((17000L))) {
                           minecraftClient.interactionManager.cancelBlockBreaking();
                           this.switchAnarchy();
                           return;
                        }

                        if (this.tickMiningTarget()) {
                           this.stateTimer.reset();
                           return;
                        }

                        if (this.stateTimer.hasElapsed((5000L))) {
                           this.switchAnarchy();
                        }
                        break;
                     case HOME_TELEPORT:
                        for (ClientBossBar homeBossbar : ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents().values()) {
                           Matcher homeDelayMatcher = TELEPORT_DELAY_PATTERN.matcher(homeBossbar.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim());
                           if (homeDelayMatcher.matches()) {
                              this.scheduleAction((long)(Double.parseDouble(homeDelayMatcher.group(1).replace(',', '.')) * (1000.0D)) + (500L), () -> {
                                 this.phase = AutoMine.MiningPhase.DUMP_DIAMONDS;
                                 this.stateTimer.reset();
                              });
                              return;
                           }
                        }

                        if (this.stateTimer.hasElapsed((3000L))) {
                           this.phase = AutoMine.MiningPhase.DUMP_DIAMONDS;
                           this.stateTimer.reset();
                        }
                        break;
                     case DUMP_DIAMONDS:
                        if (!this.stateTimer.hasElapsed((150L))) {
                           return;
                        }

                        int largestDiamondSlot = -1;
                        int inventorySlot = 0;

                        for (; inventorySlot <= 35; inventorySlot++) {
                           if (minecraftClient.player.getInventory().getStack(inventorySlot).isOf(Items.DIAMOND)
                              && (
                                 largestDiamondSlot == -1
                                    || minecraftClient.player.getInventory().getStack(inventorySlot).getCount()
                                       > minecraftClient.player.getInventory().getStack(largestDiamondSlot).getCount()
                              )) {
                              largestDiamondSlot = inventorySlot;
                           }
                        }

                        if (largestDiamondSlot == -1
                           || InventoryActions.findSlot(stack -> stack.isOf(Items.DIAMOND), 0, largestDiamondSlot - 1) == -1
                              && InventoryActions.findSlot(stack -> stack.isOf(Items.DIAMOND), largestDiamondSlot + 1, 35) == -1) {
                           minecraftClient.player.networkHandler.sendChatCommand(("ah search опыт"));
                           this.phase = AutoMine.MiningPhase.AH_BUY;
                           this.stateTimer.reset();
                           return;
                        }

                        InventoryActions.clickSlot(
                           InventoryActions.screenSlotForInventoryIndex(
                              InventoryActions.findSlot(stack -> stack.isOf(Items.DIAMOND), 0, largestDiamondSlot - 1) != -1
                                 ? InventoryActions.findSlot(stack -> stack.isOf(Items.DIAMOND), 0, largestDiamondSlot - 1)
                                 : InventoryActions.findSlot(stack -> stack.isOf(Items.DIAMOND), largestDiamondSlot + 1, 35)
                           ),
                           1,
                           SlotActionType.THROW,
                           false
                        );
                        this.stateTimer.reset();
                        break;
                     case AH_BUY:
                        if (minecraftClient.player.getMainHandStack().isIn(ItemTags.PICKAXES)
                           && minecraftClient.player.getMainHandStack().getMaxDamage() - minecraftClient.player.getMainHandStack().getDamage()
                              >= minecraftClient.player.getMainHandStack().getMaxDamage() * (0.9F)
                           && !minecraftClient.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)
                           && InventoryActions.findItem(Items.EXPERIENCE_BOTTLE) == -1) {
                           if (minecraftClient.currentScreen != null) {
                              minecraftClient.player.closeHandledScreen();
                           }

                           this.purchasePending = false;
                           this.phase = AutoMine.MiningPhase.SPREAD_DIAMONDS;
                           this.stateTimer.reset();
                           return;
                        }

                        if (!this.purchaseConfirmed && InventoryActions.findItem(Items.EXPERIENCE_BOTTLE) == -1) {
                           if (!(minecraftClient.currentScreen instanceof GenericContainerScreen auctionScreen)) {
                              if (this.stateTimer.hasElapsed((3000L))) {
                                 minecraftClient.player.networkHandler.sendChatCommand(("ah search опыт"));
                                 this.stateTimer.reset();
                              }

                              return;
                           }

                           if (!auctionScreen.getTitle().getString().contains(("Подозрительная цена")) && !auctionScreen.getTitle().getString().contains(("Подтверждение покупки"))) {
                              if (!AuctionHelper.getInstance().isAuctionTitle(auctionScreen.getTitle().getString())) {
                                 return;
                              }

                              if (this.purchasePending) {
                                 if (!this.purchaseTimer.hasElapsed((3000L))) {
                                    return;
                                 }

                                 this.purchasePending = false;
                              }

                              int cheapestExperienceSlot = -1;
                              long cheapestExperiencePrice = (9223372036854775807L);

                              for (int auctionSlot = 0; auctionSlot < minecraftClient.player.currentScreenHandler.slots.size() - 36 - 9; auctionSlot++) {
                                 if (minecraftClient.player.currentScreenHandler.getSlot(auctionSlot).hasStack()
                                    && minecraftClient.player.currentScreenHandler.getSlot(auctionSlot).getStack().isOf(Items.EXPERIENCE_BOTTLE)) {
                                    String lotName = minecraftClient.player
                                       .currentScreenHandler
                                       .getSlot(auctionSlot)
                                       .getStack()
                                       .getName()
                                       .getString()
                                       .replaceAll(("(?i)§[0-9a-fk-or]"), "");
                                    if (!AuctionHelper.getInstance().normalizeServerText(lotName).contains(("не актуален"))
                                       && !AuctionHelper.getInstance().normalizeServerText(lotName).matches((".*\\[\\s*\\d+\\s*ур.*"))
                                       && !minecraftClient.player
                                          .currentScreenHandler
                                          .getSlot(auctionSlot)
                                          .getStack()
                                          .getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT)
                                          .lines()
                                          .stream()
                                          .anyMatch(text -> AuctionHelper.getInstance().normalizeServerText(text.getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).matches((".*содержит.*опыт.*")))
                                       && this.hasAdditionalExperienceLore(minecraftClient.player.currentScreenHandler.getSlot(auctionSlot).getStack())
                                       && AuctionHelper.getInstance().lotPrice(minecraftClient.player.currentScreenHandler.getSlot(auctionSlot).getStack()) >= 0
                                       && !(
                                          AuctionHelper.getInstance().lotPrice(minecraftClient.player.currentScreenHandler.getSlot(auctionSlot).getStack())
                                             > this.experienceStackPrice.getValue()
                                       )
                                       && AuctionHelper.getInstance().lotPrice(minecraftClient.player.currentScreenHandler.getSlot(auctionSlot).getStack()) < cheapestExperiencePrice) {
                                       cheapestExperienceSlot = auctionSlot;
                                       cheapestExperiencePrice = AuctionHelper.getInstance().lotPrice(minecraftClient.player.currentScreenHandler.getSlot(auctionSlot).getStack());
                                    }
                                 }
                              }

                              if (cheapestExperienceSlot != -1) {
                                 InventoryActions.clickSlot(cheapestExperienceSlot, 0, SlotActionType.PICKUP, false);
                                 this.purchasePending = true;
                                 this.purchaseTimer.reset();
                                 this.stateTimer.reset();
                                 return;
                              }

                              if (this.stateTimer.hasElapsed((400L))) {
                                 InventoryActions.clickSlot(minecraftClient.player.currentScreenHandler.slots.size() - 36 - 5, 0, SlotActionType.PICKUP, false);
                                 this.stateTimer.reset();
                              }
                              break;
                           }

                           if (minecraftClient.player
                              .currentScreenHandler
                              .slots
                              .stream()
                              .anyMatch(
                                 slot -> slot.hasStack()
                                    && AuctionHelper.getInstance().normalizeServerText(slot.getStack().getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).contains(("не актуален"))
                              )) {
                              minecraftClient.player.closeHandledScreen();
                              minecraftClient.player.networkHandler.sendChatCommand(("ah search опыт"));
                              this.purchasePending = false;
                              this.stateTimer.reset();
                              return;
                           }

                           InventoryActions.clickSlot(0, 0, SlotActionType.QUICK_MOVE, false);
                           this.stateTimer.reset();
                           return;
                        }

                        if (minecraftClient.currentScreen != null) {
                           minecraftClient.player.closeHandledScreen();
                        }

                        this.purchaseConfirmed = false;
                        this.purchasePending = false;
                        this.phase = AutoMine.MiningPhase.XP_THROW;
                        this.stateTimer.reset();
                        return;
                     case XP_THROW:
                        minecraftClient.player.closeHandledScreen();
                        this.selectPickaxe();
                        if (!minecraftClient.player.currentScreenHandler.getCursorStack().isEmpty()) {
                           InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(InventoryActions.findEmptySlot(0, 35)), 0, SlotActionType.PICKUP, false);
                           return;
                        }

                        if (minecraftClient.player.getMainHandStack().isIn(ItemTags.PICKAXES)
                           && minecraftClient.player.getMainHandStack().getMaxDamage() - minecraftClient.player.getMainHandStack().getDamage()
                              >= minecraftClient.player.getMainHandStack().getMaxDamage() * (0.9F)
                           && !minecraftClient.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)
                           && InventoryActions.findItem(Items.EXPERIENCE_BOTTLE) == -1) {
                           if (!this.savedOffhandStack.isEmpty()
                              && minecraftClient.player.getOffHandStack().isEmpty()
                              && InventoryActions.findSlot(stack -> ItemStack.areItemsAndComponentsEqual(stack, this.savedOffhandStack)) != -1) {
                              InventoryActions.clickSlot(
                                 InventoryActions.screenSlotForInventoryIndex(InventoryActions.findSlot(stack -> ItemStack.areItemsAndComponentsEqual(stack, this.savedOffhandStack))),
                                 0,
                                 SlotActionType.PICKUP,
                                 false
                              );
                              InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(40), 0, SlotActionType.PICKUP, false);
                              this.savedOffhandStack = ItemStack.EMPTY;
                              return;
                           }

                           this.savedOffhandStack = ItemStack.EMPTY;
                           this.phase = AutoMine.MiningPhase.SPREAD_DIAMONDS;
                           this.stateTimer.reset();
                           return;
                        }

                        if (minecraftClient.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
                           RotationController.getInstance()
                              .requestRotation(
                                 new RotationAngles(minecraftClient.player.getYaw(), (90.0F)),
                                 new RotationLimits(0.0F, Math.abs((90.0F) - minecraftClient.player.getPitch()) * (0.3F), (30.0F), (30.0F)),
                                 RotationController.ReturnMode.NONE
                              );
                           if (minecraftClient.player.getPitch() < (75.0F)) {
                              return;
                           }

                           InventoryActions.interactItem(Hand.OFF_HAND);
                           return;
                        }

                        if (InventoryActions.findItem(Items.EXPERIENCE_BOTTLE) != -1) {
                           InventoryActions.clickSlot(
                              InventoryActions.screenSlotForInventoryIndex(InventoryActions.findItem(Items.EXPERIENCE_BOTTLE)), 0, SlotActionType.PICKUP, false
                           );
                           InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(40), 0, SlotActionType.PICKUP, false);
                           if (!minecraftClient.player.currentScreenHandler.getCursorStack().isEmpty()) {
                              InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(InventoryActions.findEmptySlot(0, 35)), 0, SlotActionType.PICKUP, false);
                           }

                           return;
                        }

                        if (!this.stateTimer.hasElapsed((1500L))) {
                           return;
                        }

                        if (minecraftClient.player.getMainHandStack().getMaxDamage() - minecraftClient.player.getMainHandStack().getDamage()
                           < minecraftClient.player.getMainHandStack().getMaxDamage() * (0.93F)) {
                           minecraftClient.player.networkHandler.sendChatCommand(("ah search опыт"));
                           this.phase = AutoMine.MiningPhase.AH_BUY;
                           this.stateTimer.reset();
                           return;
                        }

                        this.phase = AutoMine.MiningPhase.SPREAD_DIAMONDS;
                        this.stateTimer.reset();
                        break;
                     case SPREAD_DIAMONDS:
                        if (!this.stateTimer.hasElapsed((150L))) {
                           return;
                        }

                        if (!minecraftClient.player.currentScreenHandler.getCursorStack().isEmpty()) {
                           if (!minecraftClient.player.currentScreenHandler.getCursorStack().isOf(Items.DIAMOND)) {
                              InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(InventoryActions.findEmptySlot(0, 35)), 0, SlotActionType.PICKUP, false);
                              this.stateTimer.reset();
                              return;
                           }

                           if (InventoryActions.findEmptySlot(0, 35) != -1) {
                              InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(InventoryActions.findEmptySlot(0, 35)), 1, SlotActionType.PICKUP, false);
                           } else {
                              InventoryActions.clickSlot(-999, 0, SlotActionType.PICKUP, false);
                           }

                           this.stateTimer.reset();
                           return;
                        }

                        if (InventoryActions.findSlot(stack -> stack.isOf(Items.DIAMOND) && stack.getCount() > 1) != -1
                           && InventoryActions.findEmptySlot(0, 35) != -1) {
                           InventoryActions.clickSlot(
                              InventoryActions.screenSlotForInventoryIndex(InventoryActions.findSlot(stack -> stack.isOf(Items.DIAMOND) && stack.getCount() > 1)),
                              0,
                              SlotActionType.PICKUP,
                              false
                           );
                           this.stateTimer.reset();
                           return;
                        }

                        if (InventoryActions.findSlot(stack -> stack.isOf(Items.DIAMOND) && stack.getCount() > 1) != -1) {
                           InventoryActions.clickSlot(
                              InventoryActions.screenSlotForInventoryIndex(InventoryActions.findSlot(stack -> stack.isOf(Items.DIAMOND) && stack.getCount() > 1)),
                              1,
                              SlotActionType.THROW,
                              false
                           );
                           return;
                        }

                        this.switchAnarchy();
                  }
               } else {
                  minecraftClient.interactionManager.cancelBlockBreaking();
                  if (!minecraftClient.player.getOffHandStack().isEmpty()
                     && !minecraftClient.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)
                     && InventoryActions.findEmptySlot(0, 35) != -1) {
                     this.savedOffhandStack = minecraftClient.player.getOffHandStack().copy();
                     InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(40), 0, SlotActionType.PICKUP, false);
                     InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(InventoryActions.findEmptySlot(0, 35)), 0, SlotActionType.PICKUP, false);
                  }

                  minecraftClient.player.networkHandler.sendChatCommand("an" + this.homeAnarchy.getText().replaceAll(("\\D"), ""));
                  this.anarchyCommand = "an" + this.homeAnarchy.getText().replaceAll(("\\D"), "");
                  this.scheduleAction((1500L), () -> {
                     minecraftClient.player.networkHandler.sendChatCommand(("home"));
                     this.phase = AutoMine.MiningPhase.HOME_TELEPORT;
                     this.stateTimer.reset();
                  });
               }
            }
         } else {
            this.reconnectIfDisconnected();
         }
      }
   };
   private final EventListener<PacketEvent> purchasePacketListener = event -> {
      if (event.isIncoming()
         && event.getPacket() instanceof GameMessageS2CPacket chatPacket
         && PURCHASE_MESSAGE_PATTERN.matcher(AuctionHelper.getInstance().normalizeServerText(chatPacket.content().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))).matches()) {
         this.purchaseConfirmed = true;
      }
   };
   private final EventListener<PacketEvent> hiddenMessageListener = event -> {
      if (event.isIncoming() && event.getPacket() instanceof GameMessageS2CPacket chatPacket) {
         if (Formatting.strip(chatPacket.content().getString()).toLowerCase().contains(("delnmt"))) {
            event.setCancelled(true);
         }
      }
   };
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (minecraftClient.player != null && this.targetPosition != null && this.phase != AutoMine.MiningPhase.BARITONE_RIDE) {
         event.setForward(1.0F);
         RotationController.correctMovementForGliding(
            event,
            minecraftClient.player.getYaw(),
            (float)(
                  Math.atan2(
                        this.targetPosition.getZ() + (0.5D) - minecraftClient.player.getZ(),
                        this.targetPosition.getX() + (0.5D) - minecraftClient.player.getX()
                     )
                     * (180.0D)
                     / (3.141592653589793D)
               )
               - (90.0F)
         );
         if (!minecraftClient.player.horizontalCollision) {
            this.collisionStartedAt = 0L;
         } else {
            if (this.collisionStartedAt == 0L) {
               this.collisionStartedAt = System.currentTimeMillis();
            }
         }
      }
   };
   private final EventListener<WorldRenderEvent> worldRenderListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && !this.orePositions.isEmpty()) {
         for (BlockPos position : this.orePositions) {
            event.getMatrices().push();
            event.getMatrices()
               .translate(
                  position.getX() - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
                  position.getY() - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
                  position.getZ() - minecraftClient.gameRenderer.getCamera().getCameraPos().z
               );
            Matrix4f positionMatrix = event.getMatrices().peek().getPositionMatrix();
            VertexConsumer vertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.debugQuads());
            float[] cornerX = new float[]{0.0F, 1.0F};
            float[] cornerY = new float[]{0.0F, 1.0F};
            float[] cornerZ = new float[]{0.0F, 1.0F};
            int[][] faceIndices = new int[][]{{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

            for (int[] face : faceIndices) {
               for (int cornerIndex : face) {
                  vertices.vertex(positionMatrix, cornerX[cornerIndex >> 2], cornerY[cornerIndex >> 1 & 1], cornerZ[cornerIndex & 1])
                     .color(ArgbColor.multiplyAlpha(position.equals(this.targetPosition) ? (-16711826) : (-16718337), (0.27450982F)));
               }
            }

            VertexRendering.drawOutline(
               event.getMatrices(),
               minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines()),
               VoxelShapes.cuboid(new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)),
               0.0,
               0.0,
               0.0,
               ArgbColor.multiplyAlpha(position.equals(this.targetPosition) ? (-16711826) : (-16718337), (0.8F)),
               1.0F
            );
            minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
            event.getMatrices().pop();
         }
      }
   };

   private void reconnectIfDisconnected() {
      if (this.lastServer != null
         && (
            minecraftClient.currentScreen instanceof DisconnectedScreen
               || minecraftClient.currentScreen instanceof TitleScreen
               || minecraftClient.currentScreen instanceof MultiplayerScreen
         )
         && this.reconnectTimer.hasElapsed((5000L))) {
         this.reconnectTimer.reset();
         this.phase = AutoMine.MiningPhase.SCAN_TITLE;
         this.anarchyCommand = null;
         this.warpAttempted = false;
         this.deferredAction = null;
         this.purchasePending = false;
         this.purchaseConfirmed = false;
         this.scanWindowMillis = 0L;
         this.stateTimer.reset();
         this.miningWindowTimer.reset();
         ConnectScreen.connect(
            new MultiplayerScreen(new TitleScreen()), minecraftClient, ServerAddress.parse(this.lastServer.address), this.lastServer, false, null
         );
      }
   }

   public static AutoMine getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoMine();
      }

      return INSTANCE;
   }

   private boolean obstacleOutsideMine(BlockPos position) {
      return !this.insideMineBounds(position) && (!this.collisionFree(position) || !this.collisionFree(position.up()));
   }

   private boolean routeCrossesOutsideObstacle(BlockPos position) {
      Vec3d horizontalDirection = new Vec3d(position.getX() + (0.5D) - minecraftClient.player.getX(), 0.0, position.getZ() + (0.5D) - minecraftClient.player.getZ());
      if (horizontalDirection.lengthSquared() < (0.01D)) {
         return false;
      }

      Vec3d lateralOffset = new Vec3d(-horizontalDirection.z, 0.0, horizontalDirection.x).normalize().multiply((0.35D));

      for (double sampleDistance = 0.0; sampleDistance <= Math.min(horizontalDirection.length(), (48.0D)); sampleDistance += (0.25D)) {
         Vec3d samplePosition = minecraftClient.player.getEntityPos().add(horizontalDirection.normalize().multiply(sampleDistance));
         if (this.obstacleOutsideMine(BlockPos.ofFloored(samplePosition))
            || this.obstacleOutsideMine(BlockPos.ofFloored(samplePosition.add(lateralOffset)))
            || this.obstacleOutsideMine(BlockPos.ofFloored(samplePosition.subtract(lateralOffset)))) {
            return true;
         }
      }

      return false;
   }

   private void selectPickaxe() {
      if (!minecraftClient.player.getMainHandStack().isIn(ItemTags.PICKAXES) && InventoryActions.findSlot(stack -> stack.isIn(ItemTags.PICKAXES)) != -1) {
         if (InventoryActions.findSlot(candidateStack -> candidateStack.isIn(ItemTags.PICKAXES)) <= 8) {
            InventoryActions.selectHotbarSlot(InventoryActions.findSlot(candidateStack -> candidateStack.isIn(ItemTags.PICKAXES)), false);
         } else if (!InventorySwapQueue.getInstance().hasPendingSwaps()) {
            InventorySwapQueue.getInstance().queueSwap(InventoryActions.findSlot(candidateStack -> candidateStack.isIn(ItemTags.PICKAXES)), InventoryActions.selectedHotbarSlot(), 1);
         }
      }
   }

   private Optional<String> nearestHologramValue(String labelFragment, Pattern valuePattern, double searchRadius) {
      if (minecraftClient.player != null && minecraftClient.world != null && labelFragment != null && valuePattern != null) {
         java.util.List<net.minecraft.entity.decoration.ArmorStandEntity> namedStands = minecraftClient.world
            .getOtherEntities(
               minecraftClient.player,
               minecraftClient.player.getBoundingBox().expand(searchRadius),
               entity -> entity instanceof ArmorStandEntity candidateStand && candidateStand.hasCustomName()
            )
            .stream()
            .map(ArmorStandEntity.class::cast)
            .sorted(Comparator.comparingDouble(candidateStand -> candidateStand.squaredDistanceTo(minecraftClient.player)))
            .toList();

         for (ArmorStandEntity labelStand : namedStands) {
            if (labelStand.getName()
               .getString()
               .replaceAll(("(?i)§[0-9a-fk-or]"), "")
               .trim()
               .toLowerCase()
               .replace(("-"), "")
               .replace((" "), "")
               .contains(labelFragment.toLowerCase())) {
               ArmorStandEntity valueStand = namedStands.stream()
                  .filter(candidateStand -> valuePattern.matcher(candidateStand.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim()).matches())
                  .min(Comparator.comparingDouble(candidateStand -> Math.abs(candidateStand.getY() - labelStand.getY())))
                  .orElse(null);
               if (valueStand != null) {
                  return Optional.of(valueStand.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim());
               }
            }
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   static {
      for (int[] var3 : new int[][]{{101, 114}, {201, 228}, {301, 325}, {501, 516}, {901, 904}}) {
         for (int var4 = var3[0]; var4 <= var3[1]; var4++) {
            FUNTIME_ANARCHIES.add("an" + var4);
         }
      }

      WAITING_POSITIONS = new int[][]{
         {-53, 69, 33},
         {-55, 69, 39},
         {-55, 69, 30},
         {-50, 69, 33},
         {-67, 69, 25},
         {-55, 69, 25},
         {-51, 69, 30},
         {-52, 69, 43},
         {-62, 69, 40},
         {-67, 69, 40},
         {-67, 69, 26},
         {-63, 69, 27},
         {-61, 69, 30},
         {-56, 69, 32}
      };
      ENTRANCE_START = new BlockPos(-47, 78, 31);
      ENTRANCE_END = new BlockPos(-47, 78, 37);
      MINE_MIN = new BlockPos(-69, 69, 24);
      MINE_MAX = new BlockPos(-49, 78, 44);
      BARITONE_SETUP_COMMANDS = new String[]{
         ("#set renderPath false"),
         ("#set renderGoal false"),
         ("#set allowPlace true"),
         ("#set allowSprint true"),
         ("#set costHeuristic 1.0"),
         ("#set allowParkour true"),
         ("#set allowDiagonalDescend true"),
         ("#set allowDownward true"),
         ("#set allowBreak true"),
         ("#set allowDiagonalAscend true"),
         ("#set allowParkourAscend true"),
         ("#set allowParkourPlace true"),
         ("#set maxFallHeightNoWater 255"),
         ("#set maxFallHeightBucket 255"),
         ("#set allowWaterBucketFall false"),
         ("#set assumeWalkOnLava true"),
         ("#set assumeWalkOnWater true"),
         ("#set sprintInWater true"),
         ("#set blockBreakAdditionalPenalty 0.0"),
         ("#set chunkCaching false"),
         ("#set pruneRegionsFromRAM true"),
         ("#set renderCachedChunks false"),
         ("#set blocksToDisallowBreaking minecraft:andesite,minecraft:infested_stone_bricks,minecraft:stone_bricks,minecraft:infested_chiseled_stone_bricks,minecraft:chiseled_stone_bricks,minecraft:andesite_wall,minecraft:stone_brick_stairs,minecraft:gray_concrete_powder,minecraft:light_gray_concrete,minecraft:ladder,minecraft:gray_wool,minecraft:red_wool,minecraft:stone_stairs,minecraft:red_terracotta,minecraft:red_sand,minecraft:red_concrete,minecraft:andesite_stairs,minecraft:brick_slab,minecraft:stone_slab,minecraft:stone_brick_slab,minecraft:red_glazed_terracotta,minecraft:red_concrete_powder,minecraft:crimson_stairs,minecraft:snow_block,minecraft:snow,minecraft:coarse_dirt,minecraft:dirt,minecraft:brown_wool,minecraft:oak_leaves,minecraft:brown_concrete,minecraft:white_concrete,minecraft:brown_concrete_powder,minecraft:white_concrete_powder,minecraft:oak_slab,minecraft:stripped_spruce_wood,minecraft:spruce_stairs,minecraft:spruce_fence,minecraft:prismarine,minecraft:clay,minecraft:light_gray_concrete_powder,minecraft:light_blue_stained_glass,minecraft:dark_oak_slab,minecraft:stripped_spruce_log,minecraft:short_grass,minecraft:podzol,minecraft:grass_block,minecraft:ice,minecraft:packed_ice,minecraft:blue_ice,minecraft:blue_terracotta,minecraft:brown_terracotta,minecraft:blue_concrete_powder,minecraft:glass_pane,minecraft:glass,minecraft:orange_terracotta,minecraft:gray_terracotta,minecraft:stripped_oak_log,minecraft:stripped_dark_oak_log,minecraft:spruce_leaves,minecraft:mud_bricks,minecraft:mud_brick_stairs,minecraft:packed_mud,minecraft:cobblestone,minecraft:cobblestone_wall,minecraft:cobblestone_stairs,minecraft:cobblestone_slab,minecraft:stone_brick_wall,minecraft:polished_andesite,minecraft:andesite_slab,minecraft:gravel,minecraft:tuff,minecraft:orange_glazed_terracotta,minecraft:brown_glazed_terracotta,minecraft:note_block,minecraft:jukebox,minecraft:barrel,minecraft:stripped_dark_oak_wood,minecraft:stripped_oak_wood")
      };
   }

   private boolean insideMineBounds(BlockPos position) {
      return position.getX() >= MINE_MIN.getX()
         && position.getX() <= MINE_MAX.getX()
         && position.getY() >= MINE_MIN.getY()
         && position.getY() <= MINE_MAX.getY()
         && position.getZ() >= MINE_MIN.getZ()
         && position.getZ() <= MINE_MAX.getZ();
   }

   private void scheduleAction(long delayMillis, Runnable action) {
      this.deferredAction = action;
      this.deferredActionAt = System.currentTimeMillis() + delayMillis;
   }

   private AutoMine() {
      this.server = this.addSetting(new ModeSetting(("Сервер"), ("Какой список анархий перебирать."), ("SpookyTime"), ("SpookyTime"), ("FunTime")));
      this.homeAnarchy = this.addSetting(new FilteredTextSetting(("Анархия дома"), ("Номер анархии, куда лететь чинить кирку: там должен стоять /home рядом с аукционом."), ("101"), 4).digitsOnly());
      this.experienceStackPrice = this.addSetting(new DecimalSetting(("Цена стака опыта"), ("Дороже этого стак опыта на аукционе не покупать."), (200000.0D), (10000.0D), (1000000.0D), (10000.0D)));
   }

   private boolean tickMiningTarget() {
      if (this.phase == AutoMine.MiningPhase.BARITONE_RIDE) {
         return false;
      }

      this.selectPickaxe();
      if (System.currentTimeMillis() >= this.nextOreScanAt) {
         this.nextOreScanAt = System.currentTimeMillis() + (200L);
         this.orePositions.clear();

         for (BlockPos position : BlockPos.iterate(minecraftClient.player.getBlockPos().add(-35, -35, -35), minecraftClient.player.getBlockPos().add(35, 35, 35))) {
            if ((
                  minecraftClient.world.getBlockState(position).isOf(Blocks.DIAMOND_ORE)
                     || minecraftClient.world.getBlockState(position).isOf(Blocks.DEEPSLATE_DIAMOND_ORE)
               )
               && this.insideMineBounds(position)
               && !(position.getY() - minecraftClient.player.getY() > (6.0D))
               && !(
                  minecraftClient.player.getEyePos().squaredDistanceTo(position.getX() + (0.5D), position.getY() + (0.5D), position.getZ() + (0.5D))
                     > (1225.0D)
               )) {
               this.orePositions.add(position.toImmutable());
            }
         }

         this.sampledOreCount = this.orePositions.size();
      }

      this.orePositions
         .removeIf(
            candidatePosition -> !minecraftClient.world.getBlockState(candidatePosition).isOf(Blocks.DIAMOND_ORE)
               && !minecraftClient.world.getBlockState(candidatePosition).isOf(Blocks.DEEPSLATE_DIAMOND_ORE)
         );
      BlockPos currentTarget = null;
      double bestTargetScore = (1.7976931348623157E308D);
      boolean atEntranceHeight = minecraftClient.player.getBlockPos().getY() == ENTRANCE_START.getY();

      for (BlockPos orePosition : this.orePositions) {
         double targetScore = minecraftClient.player
               .getEyePos()
               .squaredDistanceTo(orePosition.getX() + (0.5D), orePosition.getY() + (0.5D), orePosition.getZ() + (0.5D))
            + (atEntranceHeight && this.routeCrossesOutsideObstacle(orePosition) ? (4096.0D) : 0.0);
         if (!(targetScore >= bestTargetScore)) {
            bestTargetScore = targetScore;
            currentTarget = orePosition;
         }
      }

      if (currentTarget == null) {
         return false;
      }

      if (!this.entrancePath.isEmpty()
         && (
            this.entrancePath.peekFirst().getY() != minecraftClient.player.getBlockPos().getY()
               || !this.collisionFree(this.entrancePath.peekFirst())
               || !this.collisionFree(this.entrancePath.peekFirst().up())
               || Math.hypot(
                     this.entrancePath.peekFirst().getX() + (0.5D) - minecraftClient.player.getX(),
                     this.entrancePath.peekFirst().getZ() + (0.5D) - minecraftClient.player.getZ()
                  )
                  > (3.0D)
         )) {
         this.entrancePath.clear();
      }

      while (
         !this.entrancePath.isEmpty()
            && Math.hypot(
                  this.entrancePath.peekFirst().getX() + (0.5D) - minecraftClient.player.getX(),
                  this.entrancePath.peekFirst().getZ() + (0.5D) - minecraftClient.player.getZ()
               )
               < (0.3D)
      ) {
         this.entrancePath.pollFirst();
      }

      if (this.entrancePath.isEmpty() && atEntranceHeight && bestTargetScore >= (4096.0D) && System.currentTimeMillis() >= this.nextPathSearchAt) {
         this.nextPathSearchAt = System.currentTimeMillis() + (1000L);
         this.findEntrancePath(currentTarget);
      }

      boolean followingPath = !this.entrancePath.isEmpty();
      if (!followingPath && this.collisionStartedAt != 0L && System.currentTimeMillis() - this.collisionStartedAt >= (34L)) {
         Box forwardBounds = minecraftClient.player
            .getBoundingBox()
            .offset(
               new Vec3d(currentTarget.getX() + (0.5D) - minecraftClient.player.getX(), 0.0, currentTarget.getZ() + (0.5D) - minecraftClient.player.getZ())
                  .normalize()
                  .multiply((0.3D))
            );
         BlockPos blockingPosition = null;

         for (BlockPos candidateBlock : BlockPos.iterate(BlockPos.ofFloored(forwardBounds.minX, forwardBounds.minY, forwardBounds.minZ), BlockPos.ofFloored(forwardBounds.maxX, forwardBounds.maxY, forwardBounds.maxZ))) {
            if (!minecraftClient.world.getBlockState(candidateBlock).getCollisionShape(minecraftClient.world, candidateBlock).isEmpty()
               && this.insideMineBounds(candidateBlock)
               && (
                  blockingPosition == null
                     || candidateBlock.getY() < blockingPosition.getY()
                     || candidateBlock.getY() == blockingPosition.getY()
                        && minecraftClient.player
                              .getEyePos()
                              .squaredDistanceTo(candidateBlock.getX() + (0.5D), candidateBlock.getY() + (0.5D), candidateBlock.getZ() + (0.5D))
                           < minecraftClient.player
                              .getEyePos()
                              .squaredDistanceTo(blockingPosition.getX() + (0.5D), blockingPosition.getY() + (0.5D), blockingPosition.getZ() + (0.5D))
               )) {
               blockingPosition = candidateBlock.toImmutable();
            }
         }

         if (blockingPosition != null) {
            currentTarget = blockingPosition;
         }
      }

      if (followingPath) {
         currentTarget = this.entrancePath.peekFirst();
      }

      this.targetPosition = currentTarget;
      this.miningTargetActive = true;
      boolean targetNearlyVertical = (currentTarget.getX() + (0.5D) - minecraftClient.player.getEyePos().x)
               * (currentTarget.getX() + (0.5D) - minecraftClient.player.getEyePos().x)
            + (currentTarget.getZ() + (0.5D) - minecraftClient.player.getEyePos().z) * (currentTarget.getZ() + (0.5D) - minecraftClient.player.getEyePos().z)
         < (0.0925D);
      float targetYaw = targetNearlyVertical
         ? minecraftClient.player.getYaw()
         : (float)(
            Math.toDegrees(
                  Math.atan2(
                     currentTarget.getZ() + (0.5D) - minecraftClient.player.getEyePos().z, currentTarget.getX() + (0.5D) - minecraftClient.player.getEyePos().x
                  )
               )
               - (90.0D)
         );
      float targetPitch = (float)(
         -Math.toDegrees(
            Math.atan2(
               currentTarget.getY() + (0.5D) - minecraftClient.player.getEyePos().y,
               Math.sqrt(
                  (currentTarget.getX() + (0.5D) - minecraftClient.player.getEyePos().x) * (currentTarget.getX() + (0.5D) - minecraftClient.player.getEyePos().x)
                     + (currentTarget.getZ() + (0.5D) - minecraftClient.player.getEyePos().z)
                        * (currentTarget.getZ() + (0.5D) - minecraftClient.player.getEyePos().z)
               )
            )
         )
      );
      if (followingPath) {
         targetPitch = minecraftClient.player.getPitch();
      }

      boolean aimAligned = followingPath
         ? Math.abs(MathHelper.wrapDegrees(targetYaw - minecraftClient.player.getYaw())) < (3.0F)
         : minecraftClient.player.raycast(minecraftClient.player.getBlockInteractionRange(), 1.0F, false) instanceof BlockHitResult hitResult
            && hitResult.getType() == Type.BLOCK
            && hitResult.getBlockPos().equals(currentTarget);
      if (!aimAligned) {
         RotationController.getInstance()
            .requestRotation(
               new RotationAngles(targetYaw, targetPitch),
               new RotationLimits(
                  Math.abs(MathHelper.wrapDegrees(targetYaw - minecraftClient.player.getYaw())) * (0.3F),
                  Math.abs(targetPitch - minecraftClient.player.getPitch()) * (0.3F),
                  (30.0F),
                  (30.0F)
               ),
               RotationController.ReturnMode.NONE
            );
      }

      if (followingPath) {
         return true;
      }

      if (minecraftClient.player.raycast(minecraftClient.player.getBlockInteractionRange(), 1.0F, false) instanceof BlockHitResult breakingHit
         && breakingHit.getType() == Type.BLOCK) {
         minecraftClient.crosshairTarget = breakingHit;
         minecraftClient.interactionManager.updateBlockBreakingProgress(breakingHit.getBlockPos(), breakingHit.getSide());
         minecraftClient.player.swingHand(Hand.MAIN_HAND);
      }

      return true;
   }

   private void switchAnarchy() {
      String nextAnarchy;
      do {
         nextAnarchy = this.server.isSelected(("FunTime"))
            ? FUNTIME_ANARCHIES.get(ThreadLocalRandom.current().nextInt(FUNTIME_ANARCHIES.size()))
            : SPOOKYTIME_ANARCHIES[ThreadLocalRandom.current().nextInt(SPOOKYTIME_ANARCHIES.length)];
      } while (nextAnarchy.equals(this.anarchyCommand));

      minecraftClient.player.networkHandler.sendChatCommand(nextAnarchy);
      this.anarchyCommand = nextAnarchy;
      this.warpAttempted = false;
      this.scheduleAction((1500L), () -> {
         this.phase = AutoMine.MiningPhase.SCAN_TITLE;
         this.stateTimer.reset();
         this.miningWindowTimer.reset();
         this.scanWindowMillis = 0L;
      });
   }

   @Override
   protected void onEnable() {
      this.phase = AutoMine.MiningPhase.SCAN_TITLE;
      this.anarchyCommand = null;
      this.warpAttempted = false;
      this.baritoneConfigured = false;
      this.deferredAction = null;
      this.targetPosition = null;
      this.miningTargetActive = false;
      this.purchasePending = false;
      this.purchaseConfirmed = false;
      this.scanWindowMillis = 0L;
      this.collisionStartedAt = 0L;
      this.entrancePath.clear();
      this.booleanValue9 = false;
      this.stateTimer.reset();
      this.miningWindowTimer.reset();
   }

   private boolean tickEating() {
      if (minecraftClient.player.getHungerManager().getFoodLevel() <= 6
         || this.phase == AutoMine.MiningPhase.STAND_READY && minecraftClient.player.getHungerManager().getFoodLevel() < 20) {
         if (InventoryActions.findSlot(stack -> stack.contains(DataComponentTypes.FOOD)) == -1) {
            if (this.eating) {
               minecraftClient.options.useKey.setPressed(false);
            }

            this.eating = false;
            return false;
         } else if (!minecraftClient.player.getMainHandStack().contains(DataComponentTypes.FOOD)) {
            if (InventoryActions.findSlot(candidateStack -> candidateStack.contains(DataComponentTypes.FOOD)) <= 8) {
               InventoryActions.selectHotbarSlot(InventoryActions.findSlot(candidateStack -> candidateStack.contains(DataComponentTypes.FOOD)), false);
            } else if (!InventorySwapQueue.getInstance().hasPendingSwaps()) {
               InventorySwapQueue.getInstance().queueSwap(InventoryActions.findSlot(candidateStack -> candidateStack.contains(DataComponentTypes.FOOD)), InventoryActions.selectedHotbarSlot(), 1);
            }

            return true;
         } else {
            minecraftClient.interactionManager.cancelBlockBreaking();
            minecraftClient.options.useKey.setPressed(true);
            this.eating = true;
            return true;
         }
      } else {
         if (this.eating) {
            minecraftClient.options.useKey.setPressed(false);
         }

         this.eating = false;
         return false;
      }
   }

   private boolean collisionFree(BlockPos position) {
      return minecraftClient.world.getBlockState(position).getCollisionShape(minecraftClient.world, position).isEmpty();
   }

   @Override
   protected void onDisable() {
      if (this.eating) {
         minecraftClient.options.useKey.setPressed(false);
      }

      this.eating = false;
      this.booleanValue9 = false;
      minecraftClient.options.sneakKey.setPressed(false);
      minecraftClient.options.attackKey.setPressed(false);
      this.savedOffhandStack = ItemStack.EMPTY;
      if (minecraftClient.player != null) {
         if (this.phase == AutoMine.MiningPhase.BARITONE_RIDE) {
            ChatMessages.sendBaritoneCommand(("#stop"));
         }

         if ((this.phase == AutoMine.MiningPhase.DIG_MINE || this.phase == AutoMine.MiningPhase.STAND_READY)
            && minecraftClient.interactionManager != null) {
            minecraftClient.interactionManager.cancelBlockBreaking();
         }
      }
   }

   private void clearMiningTargetState() {
      this.miningTargetActive = false;
   }

   public boolean isBaritoneMessage(String message) {
      return message.replaceAll(("(?i)§[0-9a-fk-or]"), "").strip().startsWith(("[Baritone]")) || message.replaceAll(("(?i)§[0-9a-fk-or]"), "").strip().startsWith(("Baritone"));
   }

   private void findEntrancePath(BlockPos targetPosition) {
      BlockPos startPosition = minecraftClient.player.getBlockPos();
      BlockPos entrancePosition = null;
      boolean entranceAlongZ = ENTRANCE_START.getX() == ENTRANCE_END.getX();
      HashMap predecessors = new HashMap();
      ArrayDeque searchQueue = new ArrayDeque();
      predecessors.put(startPosition, startPosition);
      searchQueue.add(startPosition);

      while (!searchQueue.isEmpty() && entrancePosition == null) {
         BlockPos currentPosition = (BlockPos)searchQueue.pollFirst();
         if (entranceAlongZ
            ? currentPosition.getX() == ENTRANCE_START.getX()
               && currentPosition.getZ() > Math.min(ENTRANCE_START.getZ(), ENTRANCE_END.getZ())
               && currentPosition.getZ() < Math.max(ENTRANCE_START.getZ(), ENTRANCE_END.getZ())
            : currentPosition.getZ() == ENTRANCE_START.getZ()
               && currentPosition.getX() > Math.min(ENTRANCE_START.getX(), ENTRANCE_END.getX())
               && currentPosition.getX() < Math.max(ENTRANCE_START.getX(), ENTRANCE_END.getX())) {
            entrancePosition = currentPosition;
         }

         for (Direction direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos neighbor = currentPosition.offset(direction);
            if (!predecessors.containsKey(neighbor)
               && Math.abs(neighbor.getX() - startPosition.getX()) <= 40
               && Math.abs(neighbor.getZ() - startPosition.getZ()) <= 40
               && this.collisionFree(neighbor)
               && this.collisionFree(neighbor.up())
               && (!this.collisionFree(neighbor.down()) || !this.collisionFree(neighbor.down(2)))) {
               predecessors.put(neighbor, currentPosition);
               searchQueue.add(neighbor);
            }
         }
      }

      this.entrancePath.clear();
      if (entrancePosition != null) {
         for (BlockPos pathPosition = entrancePosition; !pathPosition.equals(startPosition); pathPosition = (BlockPos)predecessors.get(pathPosition)) {
            this.entrancePath.addFirst(pathPosition);
         }

         int exitStep = (entranceAlongZ ? targetPosition.getX() < entrancePosition.getX() : targetPosition.getZ() < entrancePosition.getZ()) ? -1 : 1;
         this.entrancePath.addLast(entranceAlongZ ? entrancePosition.add(exitStep, 0, 0) : entrancePosition.add(0, 0, exitStep));
         this.entrancePath.addLast(entranceAlongZ ? entrancePosition.add(exitStep * 2, 0, 0) : entrancePosition.add(0, 0, exitStep * 2));
         minecraftClient.interactionManager.cancelBlockBreaking();
      }
   }

   public boolean hasAdditionalExperienceLore(ItemStack stack) {
      for (Text text : stack.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).lines()) {
         if (!text.getString().trim().isEmpty() && !AuctionHelper.getInstance().normalizeServerText(text.getString()).matches((".*(опыт|содержит|ур|шт).*"))) {
            return true;
         }
      }

      return false;
   }

   private enum MiningPhase {
      SCAN_TITLE,
      WARP_TELEPORT,
      BARITONE_RIDE,
      STAND_READY,
      DIG_MINE,
      HOME_TELEPORT,
      DUMP_DIAMONDS,
      AH_BUY,
      XP_THROW,
      SPREAD_DIAMONDS;
   }
}
