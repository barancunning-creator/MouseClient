package com.mouseclient.network;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.internal.ClientMath;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.WardenHelper;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.MovementFreezeController;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.util.entity.SmoothedAimVector;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.WatermarkHud;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import com.mouseclient.util.time.Stopwatch;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(
   name = "AutoWarden",
   description = "Летит на арену по клан-хому, лутает сундуки с таймером под инвизом и сдаёт лут в двойной сундук на базе.",
   category = ModuleCategory.MISC
)
public final class AutoWarden extends Module {
   private static AutoWarden INSTANCE;
   private static final Pattern CHEST_TIMER_PATTERN = Pattern.compile(("(?:(\\d{1,2}):(\\d{2})|(\\d+)\\s*(?:сек|сек\\.|с|s)|(\\d+)\\s*(?:мин|мин\\.|m))"));
   private static final Pattern ANARCHY_NUMBER_PATTERN = Pattern.compile(("\\d+"));
   private static final Pattern TELEPORT_DELAY_PATTERN = Pattern.compile((".*Телепортация через[:\\s]*(\\d+(?:[.,]\\d+)?)\\s*сек.*"));

   private static final String[] BARITONE_SETUP_COMMANDS = new String[]{
      ("#set allowBreak false"),
      ("#set allowPlace false"),
      ("#set allowSprint true"),
      ("#set sprintInWater true"),
      ("#set allowParkour true"),
      ("#set allowDiagonalDescend true"),
      ("#set allowDownward true"),
      ("#set avoidance true"),
      ("#set allowVines false"),
      ("#set blocksToAvoid twisting_vines,twisting_vines_plant,weeping_vines,weeping_vines_plant,sculk_sensor,sculk_shrieker"),
      ("#set costHeuristic 3.5"),
      ("#set primaryTimeoutMS 100"),
      ("#set movementTimeoutTicks 40"),
      ("#set freeLook true"),
      ("#set renderPath true"),
      ("#set renderPathAsLine true"),
      ("#set renderGoal false")
   };
   private static final String[] BARITONE_RESTORE_COMMANDS = new String[]{
      ("#set allowBreak true"), ("#set allowPlace true"), ("#set renderPath true"), ("#set renderGoal true"), ("#set renderPathAsLine false"), ("#set colorCurrentPath 255,0,0"), ("#set colorNextPath 255,0,255")
   };
   public DecimalSetting clanHome;
   public DecimalSetting openThreshold;
   private final Stopwatch phaseTimer = new Stopwatch();
   private final Stopwatch roamingTimer = new Stopwatch();
   private final ArrayList<BlockPos> visitedLootChests = new ArrayList<>();
   private final ArrayList<BlockPos> usedBaseChests = new ArrayList<>();
   public final ArrayList<Integer> travelAnarchies = new ArrayList<>();
   private AutoWarden.WardenLootPhase phase;
   private AutoWarden.WardenLootPhase phaseAfterTeleport;
   private String teleportCommand;
   private int teleportAnarchy;
   private boolean teleportCommandSent;
   private boolean teleportObserved;
   private BlockPos chestPosition;
   private BlockPos approachPosition;
   private BlockPos pathGoal;
   private final Stopwatch pathRefreshTimer = new Stopwatch();
   private final ArrayList<BlockPos> recentRoamingGoals = new ArrayList<>();
   private final HashMap<BlockPos, Long> chestActivationTimes = new HashMap<>();
   private boolean baritoneActive;
   private boolean timerInitiallyMissing;
   private boolean consumingItem;
   private boolean deathObserved;
   private boolean chestItemsTransferred;
   private int approachTicks;
   private int chestOpenTicks;
   private int stationaryTicks;
   private int emptyTransferTicks;
   private int supplySplitClicks;
   private int bottleThrowAttempts;
   private int cursorSourceSlot = -1;
   private Vec3d previousPosition = Vec3d.ZERO;
   private long evadeUntilMillis;
   private long longValue4;
   private long workStartedAt;
   private long lookParametersChangedAt;
   private long lookParameterIntervalMillis;
   private long pursuitStartedAt;
   private long lastPursuitObservedAt;
   private float movementYawOffset;
   private float movementPitch;
   private float movementLookSpeed;
   private int homeAnarchy;
   private int travelAnarchyIndex;
   private int depositCycles;
   private int lootedChests;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> this.tickLootCycle();
   private final EventListener<PacketEvent> packetListener = event -> this.handleServerPacket(event);
   private final EventListener<HudRenderEvent> hudRenderListener = event -> this.renderStatus(event);

   private static final double MAX_CLAN_HOME = 9.0;

   private static final double MAX_OPEN_THRESHOLD_SECONDS = 300.0;

   private static final String HOME_COMMAND = "home";
   
   private static final float HEAL_BEFORE_WARP_HEALTH = 16.0F;

   private static final long RETURN_TO_ARENA_DELAY_MILLIS = 3000L;

   private static final float CHEST_JITTER_PHASE = 1.1F;

   private static final float MIN_PITCH = -90.0F;
   
   private static final float CHEST_YAW_SPEED = 40.0F;

   private static final double AIM_SAMPLE_MIN_OFFSET = -0.4;

   private static final double AIM_SAMPLE_STEP = 0.4;

   private static final double EVASION_RADIUS = 25.0;

   private static final float PURSUIT_YAW_TOLERANCE = 25.0F;

   private boolean canStandAt(BlockPos position) {
      return minecraftClient.world.getBlockState(position).getCollisionShape(minecraftClient.world, position).isEmpty()
         && minecraftClient.world.getBlockState(position.up()).getCollisionShape(minecraftClient.world, position.up()).isEmpty()
         && !minecraftClient.world.getBlockState(position.down()).getCollisionShape(minecraftClient.world, position.down()).isEmpty()
         && !this.isAvoidedBlock(position)
         && !this.isAvoidedBlock(position.up())
         && !this.isAvoidedBlock(position.down());
   }

   public static boolean shouldHideBaritoneChat() {
      return INSTANCE != null && INSTANCE.isEnabled();
   }

   private void tickLootCycle() {
      if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.interactionManager != null) {
         if (this.homeAnarchy <= 0) {
            this.homeAnarchy = this.sidebarAnarchy();
         }

         if (minecraftClient.player.getEntityPos().squaredDistanceTo(this.previousPosition) < (0.0025D)) {
            this.stationaryTicks++;
         } else {
            this.stationaryTicks = 0;
         }

         this.previousPosition = minecraftClient.player.getEntityPos();
         if (!this.deathObserved && !(minecraftClient.currentScreen instanceof DeathScreen)) {
            this.updatePursuitState();
            this.updateSneakingOnSteps();
            this.updateMovementLook();
            if (this.playerPursuing()) {
               this.releaseUseKey();
            } else if (this.tickFoodAndBuffs() || this.clearMainHandAndBottles()) {
               return;
            }

            if (!this.inOverworld() && this.phase != AutoWarden.WardenLootPhase.WARP) {
               if (this.homeAnarchy > 0) {
                  ChatMessages.printPrefixed(
                     "Измерение «" + minecraftClient.world.getRegistryKey().getValue().getPath() + "» вместо оверворлда — ухожу домой сдаваться."
                  );
                  this.beginTeleport(HOME_COMMAND, this.homeAnarchy, AutoWarden.WardenLootPhase.DEPOSIT);
               }
            } else {
               switch (this.phase) {
                  case SUPPLY:
                     if (this.hasContainerScreen()) {
                        if (this.tickSupplyCollection() || this.phaseTimer.hasElapsed((15000L)) && minecraftClient.player.currentScreenHandler.getCursorStack().isEmpty()) {
                           this.closeInventoryAndReleaseMovement();
                           if (this.chestPosition != null) {
                              this.usedBaseChests.add(this.chestPosition);
                           }

                           this.setPhase(
                              InventoryActions.findSlot(this::isGoldenCarrot) == -1
                                    && this.nearestMatchingChest(position -> this.chestMatchesType(position, ChestType.SINGLE) && !this.usedBaseChests.contains(position)) != null
                                 ? AutoWarden.WardenLootPhase.SUPPLY
                                 : AutoWarden.WardenLootPhase.BUFF
                           );
                           return;
                        }

                        return;
                     }

                     if (minecraftClient.player.age % 20 == 0
                        && this.hasDepositItems()
                        && this.nearestMatchingChest(
                              candidate -> this.chestMatchesType(candidate, ChestType.LEFT)
                                 && !this.usedBaseChests.contains(candidate)
                                 && minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(candidate)) < (1024.0D)
                           )
                           != null) {
                        this.setPhase(AutoWarden.WardenLootPhase.DEPOSIT);
                        return;
                     }

                     if (!this.missingRequiredSupplies()) {
                        this.setPhase(AutoWarden.WardenLootPhase.BUFF);
                        return;
                     }

                     this.tickBaseChest(ChestType.SINGLE, AutoWarden.WardenLootPhase.BUFF);
                     break;
                  case BUFF:
                     if (minecraftClient.player.getHealth() < HEAL_BEFORE_WARP_HEALTH && minecraftClient.player.getHungerManager().getFoodLevel() >= 18) {
                        return;
                     }

                     if (this.lootAnarchy() > 0 && this.sidebarAnarchy() > 0 && this.sidebarAnarchy() != this.lootAnarchy()) {
                        this.beginTeleport("clan home " + this.clanHome.intValue(), this.lootAnarchy(), AutoWarden.WardenLootPhase.HUNT);
                        return;
                     }

                     this.beginTeleport("clan home " + this.clanHome.intValue(), 0, AutoWarden.WardenLootPhase.HUNT);
                     break;
                  case WARP:
                     if (this.teleportAnarchy > 0) {
                        if (this.sidebarAnarchy() != this.teleportAnarchy) {
                           if (this.teleportCommandSent && !this.phaseTimer.hasElapsed((20000L))) {
                              return;
                           }

                           this.closeInventoryAndReleaseMovement();
                           this.stopBaritone();
                           minecraftClient.player.networkHandler.sendChatCommand("an" + this.teleportAnarchy);
                           this.teleportCommandSent = true;
                           this.phaseTimer.reset();
                           return;
                        }

                        if (this.teleportCommandSent && !this.phaseTimer.hasElapsed((4000L))) {
                           return;
                        }

                        this.teleportAnarchy = 0;
                        this.teleportCommandSent = false;
                        this.teleportObserved = false;
                        this.phaseTimer.reset();
                        return;
                     }

                     if (this.teleportCommand == null) {
                        this.setPhase(this.phaseAfterTeleport);
                        return;
                     }

                     if (!this.teleportCommandSent) {
                        this.closeInventoryAndReleaseMovement();
                        this.stopBaritone();
                        minecraftClient.player.networkHandler.sendChatCommand(this.teleportCommand);
                        this.teleportCommandSent = true;
                        this.phaseTimer.reset();
                        return;
                     }

                     if (!this.teleportObserved) {
                        if (this.teleportCountdownVisible()) {
                           this.phaseTimer.reset();
                        }

                        if (this.phaseTimer.hasElapsed((8000L))) {
                           this.teleportCommandSent = false;
                        }

                        return;
                     }

                     if (this.teleportCountdownVisible()) {
                        return;
                     }

                     if (this.discardInventoryBottles()) {
                        return;
                     }

                     if (this.phaseAfterTeleport == AutoWarden.WardenLootPhase.HUNT) {
                        if (this.sidebarAnarchy() > 0 && this.lootAnarchy() > 0 && this.sidebarAnarchy() != this.lootAnarchy()) {
                           ChatMessages.printPrefixed(
                              "Клан-хом утащил на анархию " + this.sidebarAnarchy() + " вместо " + this.lootAnarchy() + " — убираю её из списка выездных."
                           );
                           this.travelAnarchies.remove(Integer.valueOf(this.lootAnarchy()));
                        }

                        this.visitedLootChests.clear();
                        this.lootedChests = 0;
                        this.chestActivationTimes.clear();
                     }

                     this.setPhase(this.phaseAfterTeleport);
                     break;
                  case HUNT:
                     if (!this.insideArenaBounds(minecraftClient.player.getBlockPos())) {
                        if (minecraftClient.player.age % 20 == 0
                           && this.hasDepositItems()
                           && this.nearestMatchingChest(
                                 candidate -> this.chestMatchesType(candidate, ChestType.LEFT)
                                    && !this.usedBaseChests.contains(candidate)
                                    && minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(candidate)) < (1024.0D)
                              )
                              != null) {
                           this.stopBaritone();
                           this.setPhase(AutoWarden.WardenLootPhase.DEPOSIT);
                           return;
                        }

                        if (!this.roamingTimer.hasElapsed(RETURN_TO_ARENA_DELAY_MILLIS)) {
                           return;
                        }

                        this.roamingTimer.reset();
                        this.startBaritonePath(this.clampToRoamingBounds(minecraftClient.player.getBlockPos()));
                        return;
                     }

                     if (this.hasContainerScreen()) {
                        if (!this.transferMatchingSlots(true, slot -> !this.excludedChestLoot(slot.getStack()))) {
                           return;
                        }

                        this.closeInventoryAndReleaseMovement();
                        if (this.chestItemsTransferred) {
                           this.lootedChests++;
                        }

                        this.chestItemsTransferred = false;
                        return;
                     }

                     if (this.lootedChests < 2
                        && !this.inventoryHasLootLimit()
                        && !(minecraftClient.player.getHealth() < (7.0F))
                        && minecraftClient.player.getHungerManager().getFoodLevel() > 6) {
                        if (minecraftClient.player.age % 5 == 0) {
                           BlockPos selectedChest = this.nearestLootChest();
                           if (selectedChest == null) {
                              selectedChest = this.earliestCachedChest();
                           }

                           if (selectedChest != null) {
                              this.timerInitiallyMissing = this.chestTimerSeconds(selectedChest) < 0;
                              this.chestPosition = selectedChest;
                              this.approachPosition = this.chestApproachPosition(selectedChest);
                              this.startBaritonePath(this.approachPosition);
                              this.setPhase(AutoWarden.WardenLootPhase.CHEST);
                              return;
                           }
                        }

                        this.chooseRoamingGoal();
                        break;
                     }

                     if (!((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents().isEmpty()) {
                        this.chooseRoamingGoal();
                        return;
                     }

                     this.stopBaritone();
                     this.setPhase(AutoWarden.WardenLootPhase.ARENA);
                     return;
                  case CHEST:
                     if (this.chestPosition == null || !(minecraftClient.world.getBlockEntity(this.chestPosition) instanceof ChestBlockEntity)) {
                        this.stopBaritone();
                        this.setPhase(AutoWarden.WardenLootPhase.HUNT);
                        return;
                     }

                     if (this.hasContainerScreen()) {
                        if (!this.transferMatchingSlots(true, candidate -> !this.excludedChestLoot(candidate.getStack())) && !this.phaseTimer.hasElapsed((10000L))) {
                           return;
                        }

                        this.closeInventoryAndReleaseMovement();
                        this.visitedLootChests.add(this.chestPosition);
                        if (this.chestItemsTransferred) {
                           this.lootedChests++;
                        }

                        this.setPhase(AutoWarden.WardenLootPhase.HUNT);
                        return;
                     }

                     if (this.timerInitiallyMissing && this.chestTimerSeconds(this.chestPosition) >= 0) {
                        this.visitedLootChests.add(this.chestPosition);
                        this.stopBaritone();
                        this.setPhase(AutoWarden.WardenLootPhase.HUNT);
                        return;
                     }

                     if (!this.tickChestApproach(this.chestPosition, this.approachPosition)) {
                        return;
                     }

                     if (this.chestTimerSeconds(this.chestPosition) > 0 && !this.phaseTimer.hasElapsed((360000L))) {
                        this.stopBaritone();
                        return;
                     }

                     if (this.chestOpenTicks++ > 200) {
                        this.visitedLootChests.add(this.chestPosition);
                        this.setPhase(AutoWarden.WardenLootPhase.HUNT);
                        return;
                     }

                     if (!this.chestWithinReach(this.chestPosition)) {
                        if (this.chestOpenTicks % 20 == 1) {
                           this.startBaritonePath(this.approachPosition != null ? this.approachPosition : this.chestPosition);
                        }

                        return;
                     }

                     this.stopBaritone();
                     this.interactChest(this.chestPosition);
                     break;
                  case ARENA:
                     if (this.discardInventoryBottles()) {
                        return;
                     }

                     if (this.arenaSpectatorSlot() != -1) {
                        if (minecraftClient.player.age % 2 != 0) {
                           return;
                        }

                        InventoryActions.clickSlot(this.arenaSpectatorSlot(), 0, SlotActionType.PICKUP, false);
                        this.phaseTimer.reset();
                        return;
                     }

                     if (this.teleportObserved) {
                        if (!this.phaseTimer.hasElapsed((1500L))) {
                           return;
                        }

                        this.beginTeleport(("home home"), this.homeAnarchy, AutoWarden.WardenLootPhase.DEPOSIT);
                        return;
                     }

                     if (!this.teleportCommandSent) {
                        minecraftClient.player.networkHandler.sendChatCommand(("darena"));
                        this.teleportCommandSent = true;
                        this.phaseTimer.reset();
                        return;
                     }

                     if (!this.phaseTimer.hasElapsed((4000L))) {
                        return;
                     }

                     this.closeInventoryAndReleaseMovement();
                     this.teleportCommandSent = false;
                     break;
                  case DEPOSIT:
                     if (this.hasContainerScreen()) {
                        if (!this.transferMatchingSlots(false, this::shouldDepositSlot)) {
                           if (this.emptyChestSlot((GenericContainerScreenHandler)minecraftClient.player.currentScreenHandler) != -1) {
                              return;
                           }

                           ChatMessages.printPrefixed(("Двойной сундук заполнен, ищу следующий."));
                           this.usedBaseChests.add(this.chestPosition);
                           this.closeInventoryAndReleaseMovement();
                           this.setPhase(AutoWarden.WardenLootPhase.DEPOSIT);
                           return;
                        }

                        if (!this.depositSurplusCarrots()) {
                           return;
                        }

                        this.closeInventoryAndReleaseMovement();
                        if (++this.depositCycles > 2) {
                           this.travelAnarchyIndex++;
                        }

                        this.setPhase(AutoWarden.WardenLootPhase.SUPPLY);
                        return;
                     }

                     this.tickBaseChest(ChestType.LEFT, AutoWarden.WardenLootPhase.DEPOSIT);
               }
            }
         } else {
            this.recoverAfterDeath();
         }
      }
   }

   private boolean potionHasEffect(ItemStack stack, RegistryEntry<StatusEffect> effectType) {
      if (stack.isOf(Items.POTION) && stack.get(DataComponentTypes.POTION_CONTENTS) != null) {
         for (StatusEffectInstance effect : stack.get(DataComponentTypes.POTION_CONTENTS).getEffects()) {
            if (effect.getEffectType().value() == effectType.value()) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private double distanceFromRecentGoals(BlockPos position) {
      double nearestHorizontalDistance = (1.7976931348623157E308D);

      for (BlockPos recentGoal : this.recentRoamingGoals) {
         nearestHorizontalDistance = Math.min(nearestHorizontalDistance, Math.hypot(recentGoal.getX() - position.getX(), recentGoal.getZ() - position.getZ()));
      }

      return nearestHorizontalDistance;
   }

   private boolean chestMatchesType(BlockPos position, ChestType chestType) {
      if (!minecraftClient.world.getBlockState(position).contains(ChestBlock.CHEST_TYPE)) {
         return false;
      } else {
         return chestType == ChestType.SINGLE
            ? minecraftClient.world.getBlockState(position).get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE
            : minecraftClient.world.getBlockState(position).get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE;
      }
   }

   private AutoWarden() {
      this.clanHome = this.addSetting(new DecimalSetting(("Дом клана"), ("Номер клан-хома, на который телепортироваться в начале круга."), 1.0, 1.0, MAX_CLAN_HOME, 1.0));
      this.openThreshold = this.addSetting(new DecimalSetting(("Открывать от"), ("Идти к сундуку, если на его таймере осталось не больше стольких секунд."), (45.0D), (5.0D), MAX_OPEN_THRESHOLD_SECONDS, 1.0));
   }

   private boolean isInvisibilityPotion(ItemStack stack) {
      return this.potionHasEffect(stack, StatusEffects.INVISIBILITY);
   }

   private boolean tickChestApproach(BlockPos position, BlockPos standingPosition) {
      if ((standingPosition == null || !(minecraftClient.player.squaredDistanceTo(Vec3d.ofBottomCenter(standingPosition)) < (0.6D)))
         && !(minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(position)) <= (2.5D))
         && (!this.chestWithinReach(position) || standingPosition != null && this.approachTicks <= 80)) {
         this.approachTicks++;
         if (this.approachTicks % 20 == 0) {
            this.startBaritonePath(standingPosition != null ? standingPosition : position);
         }

         if (this.approachTicks > 300 || this.approachTicks > 12 && this.stationaryTicks > 20) {
            this.stopBaritone();
            return true;
         } else {
            return false;
         }
      } else {
         this.approachTicks = 0;
         return true;
      }
   }

   private void setPhase(AutoWarden.WardenLootPhase nextPhase) {
      this.phase = nextPhase;
      this.phaseTimer.reset();
      this.approachTicks = 0;
      this.chestOpenTicks = 0;
      this.emptyTransferTicks = 0;
      this.supplySplitClicks = 0;
      this.cursorSourceSlot = -1;
      this.chestItemsTransferred = false;
      this.teleportCommandSent = false;
      this.teleportObserved = false;
      this.releaseUseKey();
      if (nextPhase != AutoWarden.WardenLootPhase.CHEST) {
         this.chestPosition = null;
         this.approachPosition = null;
      }

      if (nextPhase == AutoWarden.WardenLootPhase.BUFF) {
         this.usedBaseChests.clear();
      }
   }

   private boolean tickSupplyCollection() {
      if (minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler) {
         MovementFreezeController.getInstance().freezeForTicks(1);
         if (minecraftClient.player.age % 2 != 0) {
            return false;
         }

         if (!containerHandler.getCursorStack().isEmpty()) {
            if (this.neededSupply(containerHandler.getCursorStack()) && InventoryActions.findEmptySlot(0, 35) != -1) {
               InventoryActions.clickSlot(
                  InventoryActions.screenSlotForInventoryIndex(InventoryActions.findEmptySlot(0, 35)), this.supplySplitClicks++ < 4 ? 1 : 0, SlotActionType.PICKUP, false
               );
               return false;
            } else {
               InventoryActions.clickSlot(this.cursorSourceSlot != -1 ? this.cursorSourceSlot : this.cursorReturnSlot(containerHandler), 0, SlotActionType.PICKUP, false);
               this.cursorSourceSlot = -1;
               return false;
            }
         } else {
            this.cursorSourceSlot = -1;
            this.supplySplitClicks = 0;
            if (InventoryActions.findEmptySlot(0, 35) == -1) {
               return true;
            }

            for (Slot slot : containerHandler.slots) {
               if (slot.id < containerHandler.getRows() * 9 && slot.hasStack() && this.neededSupply(slot.getStack())) {
                  this.emptyTransferTicks = 0;
                  if (slot.getStack().getCount() == 1) {
                     InventoryActions.clickSlot(slot, 0, SlotActionType.QUICK_MOVE, false);
                     return false;
                  }

                  this.cursorSourceSlot = slot.id;
                  InventoryActions.clickSlot(slot, 0, SlotActionType.PICKUP, false);
                  return false;
               }
            }

            return ++this.emptyTransferTicks >= 3;
         }
      } else {
         return true;
      }
   }

   private BlockPos nearestLootChest() {
      BlockPos position = null;
      double nearestDistanceSquared = (1.7976931348623157E308D);

      for (int chunkX = minecraftClient.player.getChunkPos().x - 9; chunkX <= minecraftClient.player.getChunkPos().x + 9; chunkX++) {
         for (int chunkZ = minecraftClient.player.getChunkPos().z - 9; chunkZ <= minecraftClient.player.getChunkPos().z + 9; chunkZ++) {
            if (minecraftClient.world.isChunkLoaded(chunkX, chunkZ)) {
               for (BlockEntity blockEntity : minecraftClient.world.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                  if (blockEntity instanceof ChestBlockEntity && !(blockEntity instanceof TrappedChestBlockEntity)) {
                     BlockPos candidatePosition = blockEntity.getPos().toImmutable();
                     if (this.insideArenaBounds(candidatePosition) && this.chestMatchesType(candidatePosition, ChestType.SINGLE)) {
                        int timerSeconds = this.chestTimerSeconds(candidatePosition);
                        if (timerSeconds >= 0) {
                           this.chestActivationTimes.put(candidatePosition, System.currentTimeMillis() + timerSeconds * (1000L));
                        } else {
                           this.chestActivationTimes.remove(candidatePosition);
                        }

                        if (!this.visitedLootChests.contains(candidatePosition)
                           && timerSeconds <= this.openThreshold.intValue()
                           && !this.chestUnsafe(candidatePosition)
                           && (timerSeconds >= 0 || !(minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(candidatePosition)) > (225.0D)))
                           && !(minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(candidatePosition)) >= nearestDistanceSquared)) {
                           nearestDistanceSquared = minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(candidatePosition));
                           position = candidatePosition;
                        }
                     }
                  }
               }
            }
         }
      }

      return position;
   }

   private boolean isGoldenCarrot(ItemStack stack) {
      return stack.isOf(Items.GOLDEN_CARROT);
   }

   private int chestTimerSeconds(BlockPos position) {
      if (position != null && minecraftClient.world != null) {
         for (Entity entity : minecraftClient.world
            .getOtherEntities(
               minecraftClient.player,
               new Box(position).expand((3.0D), (4.0D), (3.0D)),
               candidate -> candidate instanceof ArmorStandEntity armorStand && armorStand.hasCustomName() || candidate instanceof TextDisplayEntity
            )) {
            Matcher timerMatcher = CHEST_TIMER_PATTERN.matcher(
               (entity instanceof TextDisplayEntity textDisplay ? textDisplay.getText() : entity.getName()).getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim()
            );
            if (timerMatcher.find()) {
               if (timerMatcher.group(1) != null) {
                  return Integer.parseInt(timerMatcher.group(1)) * 60 + Integer.parseInt(timerMatcher.group(2));
               }

               if (timerMatcher.group(3) != null) {
                  return Integer.parseInt(timerMatcher.group(3));
               }

               return Integer.parseInt(timerMatcher.group(4)) * 60;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private boolean inOverworld() {
      return minecraftClient.world != null && minecraftClient.world.getRegistryKey().getValue().getPath().equals(("overworld"));
   }

   private boolean hasContainerScreen() {
      return minecraftClient.currentScreen instanceof HandledScreen && minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler;
   }

   @Override
   protected void onDisable() {
      this.releaseUseKey();
      MovementFreezeController.getInstance().freezeForTicks(-1);
      this.workStartedAt = 0L;
      WardenHelper.getInstance().setSubscribed(false);
      if (minecraftClient.player != null) {
         this.stopBaritone();

         for (String restoreCommand : BARITONE_RESTORE_COMMANDS) {
            ChatMessages.sendBaritoneCommand(restoreCommand);
         }

         RotationController.getInstance().reset();
         minecraftClient.player.setSneaking(false);
      }
   }

   private boolean discardInventoryBottles() {
      if (minecraftClient.player.currentScreenHandler != minecraftClient.player.playerScreenHandler || InventoryActions.findItem(Items.GLASS_BOTTLE) == -1) {
         this.bottleThrowAttempts = 0;
         return false;
      }

      if (this.bottleThrowAttempts >= 5) {
         return false;
      }

      if (minecraftClient.player.age % 5 != 0) {
         return true;
      }

      this.bottleThrowAttempts++;
      InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(InventoryActions.findItem(Items.GLASS_BOTTLE)), 1, SlotActionType.THROW, false);
      return true;
   }

   private int lastNonSupplyHotbarSlot() {
      for (int hotbarIndex = 8; hotbarIndex >= 0; hotbarIndex--) {
         if (!minecraftClient.player.getInventory().getStack(hotbarIndex).isEmpty() && !this.isSupplyItem(minecraftClient.player.getInventory().getStack(hotbarIndex))) {
            return hotbarIndex;
         }
      }

      return -1;
   }

   private int arenaSpectatorSlot() {
      if (minecraftClient.currentScreen instanceof HandledScreen arenaScreen) {
         if (arenaScreen.getScreenHandler().slots.size() > 24
            && arenaScreen.getScreenHandler().getSlot(24).hasStack()
            && arenaScreen.getScreenHandler().getSlot(24).getStack().isOf(Items.PUFFERFISH)) {
            return 24;
         }

         for (Slot slot : arenaScreen.getScreenHandler().slots) {
            if (slot.hasStack() && slot.getStack().getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").contains(("На Смотровую"))) {
               return slot.id;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private long invisibilityRemainingMillis() {
      return minecraftClient.player.getStatusEffect(StatusEffects.INVISIBILITY) == null
         ? 0L
         : minecraftClient.player.getStatusEffect(StatusEffects.INVISIBILITY).getDuration() * (50L);
   }

   private int cursorReturnSlot(GenericContainerScreenHandler handler) {
      return this.emptyChestSlot(handler) != -1 ? this.emptyChestSlot(handler) : InventoryActions.screenSlotForInventoryIndex(InventoryActions.findEmptySlot(0, 35));
   }

   private boolean playerWithinRadius(double radius) {
      for (PlayerEntity player : minecraftClient.world.getPlayers()) {
         if (player != minecraftClient.player && minecraftClient.player.squaredDistanceTo(player) <= radius * radius) {
            return true;
         }
      }

      return false;
   }

   private void closeInventoryAndReleaseMovement() {
      if (minecraftClient.currentScreen != null) {
         minecraftClient.player.closeHandledScreen();
      }

      InventoryActions.pickupAllPacket();
      MovementFreezeController.getInstance().freezeForTicks(-1);
   }

   private boolean isAvoidedBlock(BlockPos position) {
      return minecraftClient.world.getBlockState(position).isOf(Blocks.TWISTING_VINES)
         || minecraftClient.world.getBlockState(position).isOf(Blocks.TWISTING_VINES_PLANT)
         || minecraftClient.world.getBlockState(position).isOf(Blocks.WEEPING_VINES)
         || minecraftClient.world.getBlockState(position).isOf(Blocks.WEEPING_VINES_PLANT)
         || minecraftClient.world.getBlockState(position).isOf(Blocks.SCULK_SENSOR)
         || minecraftClient.world.getBlockState(position).isOf(Blocks.SCULK_SHRIEKER);
   }

   private boolean chestUnsafe(BlockPos position) {
      if (!minecraftClient.world.getEntitiesByClass(WardenEntity.class, new Box(position).expand((6.0D)), warden -> warden.isAlive()).isEmpty()) {
         return true;
      }

      for (PlayerEntity player : minecraftClient.world.getPlayers()) {
         if (player != minecraftClient.player && player.getEntityPos().squaredDistanceTo(Vec3d.ofCenter(position)) <= (25.0D) && this.playerHasArmor(player)) {
            return true;
         }
      }

      return false;
   }

   private void interactChest(BlockPos position) {
      if (minecraftClient.player.isSneaking()) {
         minecraftClient.player.setSneaking(false);
      }

      Vec3d chestCenter = Vec3d.ofCenter(position);
      RotationAngles chestAngles = SmoothedAimVector.anglesForDirection(chestCenter.subtract(minecraftClient.player.getEyePos()));
      float angleJitter = (float)(
         (
               Math.sin(minecraftClient.player.age * (0.31F)) * (0.5D)
                  + Math.sin(minecraftClient.player.age * (0.73F) + CHEST_JITTER_PHASE) * (0.3D)
                  + Math.sin(minecraftClient.player.age * (1.7F) + (2.6F)) * (0.2D)
            )
            * (8.0D)
      );
      RotationController.getInstance()
         .requestForDuration(
            new RotationAngles(chestAngles.getYaw() + angleJitter, MathHelper.clamp(chestAngles.getPitch() + angleJitter / (4.0F), MIN_PITCH, (90.0F))),
            new RotationLimits(CHEST_YAW_SPEED, (40.0F), (30.0F), (30.0F)),
            (300L)
         );
      if (minecraftClient.player.age % 2 == 0
         && (
            this.chestOpenTicks > 60
               || !(Math.abs(MathHelper.wrapDegrees(chestAngles.getYaw() + angleJitter - minecraftClient.player.getYaw())) > (5.0F))
                  && !(Math.abs(chestAngles.getPitch() + angleJitter / (4.0F) - minecraftClient.player.getPitch()) > (5.0F))
         )) {
         BlockHitResult hitResult = minecraftClient.world
            .raycast(new RaycastContext(minecraftClient.player.getEyePos(), chestCenter, ShapeType.COLLIDER, FluidHandling.NONE, minecraftClient.player));
         if (!this.sameChestHalf(hitResult.getBlockPos(), position)) {
            hitResult = new BlockHitResult(chestCenter, Direction.UP, position, false);
         }

         minecraftClient.interactionManager.interactBlock(minecraftClient.player, Hand.MAIN_HAND, hitResult);
         minecraftClient.player.swingHand(Hand.MAIN_HAND);
         this.phaseTimer.reset();
      }
   }

   private boolean shouldDepositSlot(Slot slot) {
      if (this.isInvisibilityPotion(slot.getStack())) {
         return slot.getIndex() != InventoryActions.findSlot(this::isInvisibilityPotion);
      } else if (this.isSpeedPotion(slot.getStack())) {
         return slot.getIndex() != InventoryActions.findSlot(this::isSpeedPotion);
      } else {
         return this.isGoldenCarrot(slot.getStack()) ? slot.getIndex() != InventoryActions.findSlot(this::isGoldenCarrot) : true;
      }
   }

   private void beginTeleport(String command, int anarchy, AutoWarden.WardenLootPhase destinationPhase) {
      this.teleportCommand = command;
      this.teleportAnarchy = anarchy;
      this.phaseAfterTeleport = destinationPhase;
      this.setPhase(AutoWarden.WardenLootPhase.WARP);
   }

   @Override
   protected void onEnable() {
      this.visitedLootChests.clear();
      this.usedBaseChests.clear();
      this.recentRoamingGoals.clear();
      this.chestActivationTimes.clear();
      this.chestPosition = null;
      this.approachPosition = null;
      this.teleportAnarchy = 0;
      this.teleportCommand = null;
      this.baritoneActive = true;
      this.timerInitiallyMissing = false;
      this.consumingItem = false;
      this.deathObserved = false;
      this.previousPosition = Vec3d.ZERO;
      this.evadeUntilMillis = 0L;
      this.pursuitStartedAt = 0L;
      this.lastPursuitObservedAt = 0L;
      this.longValue4 = 0L;
      this.homeAnarchy = 0;
      this.travelAnarchyIndex = 0;
      this.depositCycles = 0;
      this.lootedChests = 0;
      this.stationaryTicks = 0;
      this.workStartedAt = System.currentTimeMillis();
      this.roamingTimer.reset();
      WardenHelper.getInstance().setSubscribed(true);
      this.beginTeleport(("home"), 0, AutoWarden.WardenLootPhase.DEPOSIT);
      if (minecraftClient.player != null && minecraftClient.world != null) {
         for (String setupCommand : BARITONE_SETUP_COMMANDS) {
            ChatMessages.sendBaritoneCommand(setupCommand);
         }

         ChatMessages.sendBaritoneCommand(
            "#set colorCurrentPath "
               + (HudStyle.accentColor >> 16 & 0xFF)
               + ","
               + (HudStyle.accentColor >> 8 & 0xFF)
               + ","
               + (HudStyle.accentColor & 0xFF)
         );
         ChatMessages.sendBaritoneCommand(
            "#set colorNextPath "
               + (HudStyle.accentColor >> 16 & 0xFF)
               + ","
               + (HudStyle.accentColor >> 8 & 0xFF)
               + ","
               + (HudStyle.accentColor & 0xFF)
         );
      } else {
         this.setEnabled(false);
      }
   }

   private Vec3d visibleChestAimPoint(BlockPos position) {
      Vec3d chestCenter = Vec3d.ofCenter(position);
      Vec3d visiblePoint = null;
      double nearestCenterDistanceSquared = (1.7976931348623157E308D);

      for (double offsetX = (-0.4D); offsetX <= (0.41D); offsetX += AIM_SAMPLE_STEP) {
         for (double offsetY = (-0.4D); offsetY <= (0.41D); offsetY += (0.4D)) {
            for (double offsetZ = AIM_SAMPLE_MIN_OFFSET; offsetZ <= (0.41D); offsetZ += (0.4D)) {
               Vec3d samplePoint = chestCenter.add(offsetX, offsetY, offsetZ);
               if (!(samplePoint.squaredDistanceTo(chestCenter) >= nearestCenterDistanceSquared)
                  && this.sameChestHalf(
                     minecraftClient.world
                        .raycast(new RaycastContext(minecraftClient.player.getEyePos(), samplePoint, ShapeType.COLLIDER, FluidHandling.NONE, minecraftClient.player))
                        .getBlockPos(),
                     position
                  )) {
                  nearestCenterDistanceSquared = samplePoint.squaredDistanceTo(chestCenter);
                  visiblePoint = samplePoint;
               }
            }
         }
      }

      return visiblePoint;
   }

   private void updateSneakingOnSteps() {
      if (!(minecraftClient.world.getBlockState(minecraftClient.player.getBlockPos().down()).getBlock() instanceof SlabBlock)
         && !(minecraftClient.world.getBlockState(minecraftClient.player.getBlockPos().down()).getBlock() instanceof StairsBlock)) {
         if (minecraftClient.player.isSneaking()) {
            minecraftClient.player.setSneaking(false);
         }
      } else {
         minecraftClient.player.setSneaking(true);
      }
   }

   private void startBaritonePath(BlockPos goal) {
      if (goal != null) {
         if (!this.baritoneActive || !goal.equals(this.pathGoal) || this.pathRefreshTimer.hasElapsed((3000L))) {
            RotationController.getInstance().reset();
            this.baritoneActive = true;
            this.pathGoal = goal;
            this.pathRefreshTimer.reset();
            ChatMessages.sendBaritoneCommand("#go " + goal.getX() + " " + goal.getY() + " " + goal.getZ());
         }
      }
   }

   private BlockPos earliestCachedChest() {
      BlockPos position = null;
      long earliestActivationAt = System.currentTimeMillis() + this.openThreshold.intValue() * (1000L);

      for (BlockPos candidatePosition : this.chestActivationTimes.keySet()) {
         if (minecraftClient.world.getBlockEntity(candidatePosition) instanceof ChestBlockEntity && !this.visitedLootChests.contains(candidatePosition) && this.chestActivationTimes.get(candidatePosition) < earliestActivationAt) {
            earliestActivationAt = this.chestActivationTimes.get(candidatePosition);
            position = candidatePosition;
         }
      }

      return position;
   }

   private void handleServerPacket(PacketEvent event) {
      if (event.isIncoming()) {
         if (event.getPacket() instanceof DeathMessageS2CPacket) {
            this.deathObserved = true;
         }

         if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            this.teleportObserved = true;
         }
      }
   }

   private void updatePursuitState() {
      for (PlayerEntity player : minecraftClient.world.getPlayers()) {
         if (player != minecraftClient.player
            && !(minecraftClient.player.squaredDistanceTo(player) > (900.0D))
            && !(
               Math.abs(
                     MathHelper.wrapDegrees(
                        player.getHeadYaw()
                           - (float)Math.toDegrees(Math.atan2(player.getX() - minecraftClient.player.getX(), minecraftClient.player.getZ() - player.getZ()))
                     )
                  )
                  > PURSUIT_YAW_TOLERANCE
            )
            && !(
               (minecraftClient.player.getX() - player.getX()) * (player.getX() - player.lastX)
                     + (minecraftClient.player.getZ() - player.getZ()) * (player.getZ() - player.lastZ)
                  <= (0.01D)
            )) {
            this.lastPursuitObservedAt = System.currentTimeMillis();
            if (this.pursuitStartedAt == 0L) {
               this.pursuitStartedAt = System.currentTimeMillis();
            }

            return;
         }
      }

      if (System.currentTimeMillis() - this.lastPursuitObservedAt > (1500L)) {
         this.pursuitStartedAt = 0L;
      }
   }

   private boolean neededSupply(ItemStack stack) {
      return this.isInvisibilityPotion(stack) && InventoryActions.findSlot(this::isInvisibilityPotion) == -1
         || this.isSpeedPotion(stack) && InventoryActions.findSlot(this::isSpeedPotion) == -1
         || this.isGoldenCarrot(stack) && InventoryActions.findSlot(this::isGoldenCarrot) == -1;
   }

   public static AutoWarden getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoWarden();
      }

      return INSTANCE;
   }

   private boolean inventoryHasLootLimit() {
      int occupiedSlots = 0;

      for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
         if (!minecraftClient.player.getInventory().getStack(inventoryIndex).isEmpty()) {
            if (++occupiedSlots >= 24) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean insideArenaBounds(BlockPos position) {
      return this.inOverworld() && position.getX() >= -2060 && position.getX() <= -1928 && position.getZ() >= -2069 && position.getZ() <= -1936;
   }

   private long speedRemainingMillis() {
      return minecraftClient.player.getStatusEffect(StatusEffects.SPEED) == null
         ? 0L
         : minecraftClient.player.getStatusEffect(StatusEffects.SPEED).getDuration() * (50L);
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

   private BlockPos nearestMatchingChest(Predicate<BlockPos> matchesChest) {
      BlockPos position = null;
      double nearestDistanceSquared = (1.7976931348623157E308D);

      for (int chunkX = minecraftClient.player.getChunkPos().x - 9; chunkX <= minecraftClient.player.getChunkPos().x + 9; chunkX++) {
         for (int chunkZ = minecraftClient.player.getChunkPos().z - 9; chunkZ <= minecraftClient.player.getChunkPos().z + 9; chunkZ++) {
            if (minecraftClient.world.isChunkLoaded(chunkX, chunkZ)) {
               for (BlockEntity blockEntity : minecraftClient.world.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                  if (blockEntity instanceof ChestBlockEntity
                     && !(blockEntity instanceof TrappedChestBlockEntity)
                     && !(minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(blockEntity.getPos())) > (16384.0D))
                     && !(minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(blockEntity.getPos())) >= nearestDistanceSquared)
                     && matchesChest.test(blockEntity.getPos())) {
                     nearestDistanceSquared = minecraftClient.player.squaredDistanceTo(Vec3d.ofCenter(blockEntity.getPos()));
                     position = blockEntity.getPos().toImmutable();
                  }
               }
            }
         }
      }

      return position;
   }

   private boolean sameChestHalf(BlockPos position, BlockPos targetPosition) {
      return position.equals(targetPosition)
         || position.getSquaredDistance(targetPosition) <= 1.0
            && minecraftClient.world.getBlockEntity(position) instanceof ChestBlockEntity
            && !this.chestMatchesType(targetPosition, ChestType.SINGLE);
   }

   private void renderPathGoal(WorldRenderEvent event) {
      if (minecraftClient.player != null && minecraftClient.world != null && this.baritoneActive && this.pathGoal != null) {
         Vec3d lineOrigin = minecraftClient.player.getEntityPos().add(0.0, (0.9D), 0.0);
         Vec3d goalCenter = Vec3d.ofCenter(this.pathGoal);
         Vec3d lineDirection = goalCenter.subtract(lineOrigin).normalize();
         event.getMatrices().push();
         event.getMatrices()
            .translate(
               lineOrigin.x - minecraftClient.gameRenderer.getCamera().getCameraPos().x,
               lineOrigin.y - minecraftClient.gameRenderer.getCamera().getCameraPos().y,
               lineOrigin.z - minecraftClient.gameRenderer.getCamera().getCameraPos().z
            );
         VertexConsumer vertices = minecraftClient.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayers.lines());
         vertices.vertex(event.getMatrices().peek(), 0.0F, 0.0F, 0.0F)
            .color(ArgbColor.multiplyAlpha(HudStyle.accentColor, (0.35F)))
            .normal(event.getMatrices().peek(), (float)lineDirection.x, (float)lineDirection.y, (float)lineDirection.z)
            .lineWidth((2.5F));
         vertices.vertex(event.getMatrices().peek(), (float)(goalCenter.x - lineOrigin.x), (float)(goalCenter.y - lineOrigin.y), (float)(goalCenter.z - lineOrigin.z))
            .color(HudStyle.accentColor)
            .normal(event.getMatrices().peek(), (float)lineDirection.x, (float)lineDirection.y, (float)lineDirection.z)
            .lineWidth((2.5F));
         minecraftClient.getBufferBuilders().getEntityVertexConsumers().draw();
         event.getMatrices().pop();
      }
   }

   private boolean excludedChestLoot(ItemStack stack) {
      return stack.isOf(Items.PIGLIN_HEAD)
         || stack.isOf(Items.ZOMBIE_HEAD)
         || stack.isOf(Items.DISC_FRAGMENT_5)
         || stack.isOf(Items.ENDER_CHEST)
         || stack.isOf(Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE)
         || stack.isOf(Items.PUFFERFISH)
         || stack.isOf(Items.ECHO_SHARD)
         || stack.isOf(Items.SCULK)
         || stack.isOf(Items.CREEPER_HEAD)
         || stack.isOf(Items.SKULL_BANNER_PATTERN)
         || stack.isOf(Items.NAUTILUS_SHELL)
         || stack.isOf(Items.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE)
         || stack.isOf(Items.COOKED_MUTTON)
         || stack.isOf(Items.SCULK_SENSOR)
         || stack.isOf(Items.GHAST_TEAR)
         || stack.isOf(Items.BLAZE_SPAWN_EGG)
         || stack.isOf(Items.ENCHANTING_TABLE)
         || stack.contains(DataComponentTypes.JUKEBOX_PLAYABLE);
   }

   private boolean depositSurplusCarrots() {
      if (minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler) {
         if (minecraftClient.player.age % 2 != 0) {
            return false;
         }

         if (!containerHandler.getCursorStack().isEmpty()) {
            if (!this.isGoldenCarrot(containerHandler.getCursorStack())) {
               return true;
            } else if (this.cursorSourceSlot != -1 && !containerHandler.slots.get(this.cursorSourceSlot).hasStack()) {
               InventoryActions.clickSlot(this.cursorSourceSlot, 1, SlotActionType.PICKUP, false);
               return false;
            } else if (this.emptyChestSlot(containerHandler) == -1) {
               InventoryActions.clickSlot(this.cursorSourceSlot, 0, SlotActionType.PICKUP, false);
               this.cursorSourceSlot = -1;
               ChatMessages.printPrefixed(("Двойной сундук полон — излишек морковки остаётся с собой."));
               return true;
            } else {
               InventoryActions.clickSlot(this.emptyChestSlot(containerHandler), 0, SlotActionType.PICKUP, false);
               return false;
            }
         } else {
            int carrotCount = 0;

            for (Slot slot : containerHandler.slots) {
               if (slot.id >= containerHandler.getRows() * 9 && slot.hasStack() && this.isGoldenCarrot(slot.getStack())) {
                  carrotCount += slot.getStack().getCount();
               }
            }

            if (carrotCount <= 1) {
               return true;
            }

            for (Slot carrotSlot : containerHandler.slots) {
               if (carrotSlot.id >= containerHandler.getRows() * 9 && carrotSlot.hasStack() && this.isGoldenCarrot(carrotSlot.getStack())) {
                  this.cursorSourceSlot = carrotSlot.id;
                  InventoryActions.clickSlot(carrotSlot, 0, SlotActionType.PICKUP, false);
                  return false;
               }
            }

            return true;
         }
      } else {
         return true;
      }
   }

   private boolean playerPursuing() {
      return this.pursuitStartedAt != 0L && System.currentTimeMillis() - this.pursuitStartedAt > (2500L);
   }

   private void chooseRoamingGoal() {
      if (this.roamingTimer.hasElapsed(this.stationaryTicks >= 8 ? (1500L) : (7000L))) {
         this.stationaryTicks = 0;
         this.roamingTimer.reset();
         BlockPos position = null;
         double bestClearance = (-1.0D);

         for (int sampleIndex = 0; sampleIndex < 24; sampleIndex++) {
            BlockPos candidatePosition = new BlockPos(
               ThreadLocalRandom.current().nextInt(-2051, -1937),
               minecraftClient.player.getBlockPos().getY(),
               ThreadLocalRandom.current().nextInt(-2060, -1945)
            );
            if (!(Math.min(this.distanceFromRecentGoals(candidatePosition), this.distanceFromThreats(candidatePosition)) <= bestClearance)) {
               bestClearance = Math.min(this.distanceFromRecentGoals(candidatePosition), this.distanceFromThreats(candidatePosition));
               position = candidatePosition;
               if (bestClearance >= (50.0D)) {
                  break;
               }
            }
         }

         this.recentRoamingGoals.add(position);
         if (this.recentRoamingGoals.size() > 5) {
            this.recentRoamingGoals.remove(0);
         }

         this.startBaritonePath(position);
      }
   }

   private boolean tickPlayerEvasion() {
      if (minecraftClient.currentScreen instanceof HandledScreen) {
         return false;
      }

      if (this.playerPursuing()) {
         this.evadeUntilMillis = System.currentTimeMillis() + (6000L);
      }

      if (System.currentTimeMillis() > this.evadeUntilMillis) {
         return false;
      }

      if (!this.roamingTimer.hasElapsed(this.playerPursuing() ? (1000L) : (2500L))) {
         return this.phase == AutoWarden.WardenLootPhase.HUNT || this.phase == AutoWarden.WardenLootPhase.CHEST;
      }

      this.roamingTimer.reset();
      this.releaseUseKey();
      BlockPos position = null;
      double bestThreatDistance = (-1.0D);

      for (int angleDegrees = 0; angleDegrees < 360; angleDegrees += 30) {
         BlockPos candidatePosition = minecraftClient.player
            .getBlockPos()
            .add(
               (int)(Math.cos(Math.toRadians(angleDegrees)) * (this.playerPursuing() ? (32.0D) : (25.0D))),
               0,
               (int)(Math.sin(Math.toRadians(angleDegrees)) * (this.playerPursuing() ? (32.0D) : EVASION_RADIUS))
            );
         if (this.phase == AutoWarden.WardenLootPhase.HUNT || this.phase == AutoWarden.WardenLootPhase.CHEST) {
            candidatePosition = this.clampToRoamingBounds(candidatePosition);
         }

         if (!(this.distanceFromThreats(candidatePosition) <= bestThreatDistance)) {
            bestThreatDistance = this.distanceFromThreats(candidatePosition);
            position = candidatePosition;
         }
      }

      this.startBaritonePath(position);
      return this.phase == AutoWarden.WardenLootPhase.HUNT || this.phase == AutoWarden.WardenLootPhase.CHEST;
   }

   private boolean missingRequiredSupplies() {
      return InventoryActions.findSlot(this::isInvisibilityPotion) == -1 || InventoryActions.findSlot(this::isGoldenCarrot) == -1;
   }

   private boolean threateningPlayerNearby() {
      for (PlayerEntity player : minecraftClient.world.getPlayers()) {
         if (player != minecraftClient.player
            && !(minecraftClient.player.squaredDistanceTo(player) > (625.0D))
            && (
               minecraftClient.player.squaredDistanceTo(player) < (100.0D)
                  || this.playerHasArmor(player)
                  || Math.abs(
                        MathHelper.wrapDegrees(
                           player.getHeadYaw()
                              - (float)Math.toDegrees(Math.atan2(player.getX() - minecraftClient.player.getX(), minecraftClient.player.getZ() - player.getZ()))
                        )
                     )
                     < (20.0F)
            )) {
            return true;
         }
      }

      return false;
   }

   private boolean useMatchingItem(Predicate<ItemStack> matchesItem, float pitch) {
      this.consumingItem = true;
      if (minecraftClient.player.getVelocity().horizontalLengthSquared() > (0.001D)) {
         this.stopBaritone();
         return true;
      }

      RotationController.getInstance().requestForDuration(new RotationAngles(minecraftClient.player.getYaw(), pitch), new RotationLimits((35.0F), (35.0F), (30.0F), (30.0F)), (500L));
      if (Math.abs(minecraftClient.player.getPitch() - pitch) > (15.0F)) {
         return true;
      }

      if (InventoryActions.findSlot(matchesItem, 0, 8) == -1) {
         if (!InventorySwapQueue.getInstance().hasPendingSwaps()) {
            InventorySwapQueue.getInstance().queueSwap(InventoryActions.findSlot(matchesItem), 8, 1);
         }

         return true;
      } else {
         if (InventoryActions.selectedHotbarSlot() != InventoryActions.findSlot(matchesItem, 0, 8)) {
            InventoryActions.selectHotbarSlot(InventoryActions.findSlot(matchesItem, 0, 8), false);
            return true;
         }

         if (minecraftClient.currentScreen == null) {
            minecraftClient.options.useKey.setPressed(true);
            return true;
         }

         if (!minecraftClient.player.isUsingItem()) {
            InventoryActions.interactItem(Hand.MAIN_HAND);
         }

         return true;
      }
   }

   private void tickBaseChest(ChestType chestType, AutoWarden.WardenLootPhase nextPhase) {
      if (this.chestPosition == null) {
         if (minecraftClient.player.age % 5 == 0) {
            this.chestPosition = this.nearestMatchingChest(position -> this.chestMatchesType(position, chestType) && !this.usedBaseChests.contains(position));
            if (this.chestPosition == null) {
               if (this.phaseTimer.hasElapsed((20000L))) {
                  if (!this.usedBaseChests.isEmpty()) {
                     this.usedBaseChests.clear();
                     this.phaseTimer.reset();
                  } else {
                     if (chestType == ChestType.SINGLE) {
                        ChatMessages.printPrefixed(("Рядом нет сундука с ресурсами."));
                        ChatMessages.printPrefixed(("Поставь маленький сундук рядом с собой и заполни его: &7Зелье невидимости&r, &7золотые морковки&r, по желанию &7Зелье скорости&r."));
                        ChatMessages.printPrefixed(("Ещё на Warden оставь &7/clan home&r с любым названием, а дома — большой сундук, куда бот будет складывать ресурсы."));
                     } else if (nextPhase == this.phase) {
                        ChatMessages.printPrefixed(("Не вижу двойного сундука для сдачи — он должен стоять там, куда ведёт /home home. Жду."));
                        this.phaseTimer.reset();
                        return;
                     }

                     this.setPhase(nextPhase);
                  }
               }
            } else {
               this.approachPosition = this.chestApproachPosition(this.chestPosition);
               if (this.phase == AutoWarden.WardenLootPhase.DEPOSIT) {
                  ChatMessages.printPrefixed("Сдаю лут: двойной сундук " + this.chestPosition.toShortString() + ".");
               }

               this.startBaritonePath(this.approachPosition != null ? this.approachPosition : this.chestPosition);
            }
         }
      } else if (this.tickChestApproach(this.chestPosition, this.approachPosition)) {
         if (this.chestOpenTicks++ > 200) {
            ChatMessages.printPrefixed(
               "Не смог открыть сундук " + this.chestPosition.toShortString() + " за 10с" + (this.chestWithinReach(this.chestPosition) ? (" (стою рядом, но клик не проходит)") : (" (не могу подойти)")) + "."
            );
            this.usedBaseChests.add(this.chestPosition);
            this.setPhase(nextPhase);
         } else if (!this.chestWithinReach(this.chestPosition)) {
            if (this.chestOpenTicks % 20 == 1) {
               this.startBaritonePath(this.approachPosition != null ? this.approachPosition : this.chestPosition);
            }
         } else {
            this.stopBaritone();
            this.interactChest(this.chestPosition);
         }
      }
   }

   private void releaseUseKey() {
      if (minecraftClient.options != null) {
         minecraftClient.options.useKey.setPressed(false);
      }
   }

   private void stopBaritone() {
      if (this.baritoneActive) {
         this.baritoneActive = false;
         this.pathGoal = null;
         ChatMessages.sendBaritoneCommand(("#stop"));
      }
   }

   private int emptyChestSlot(GenericContainerScreenHandler handler) {
      for (Slot slot : handler.slots) {
         if (slot.id < handler.getRows() * 9 && !slot.hasStack()) {
            return slot.id;
         }
      }

      return -1;
   }

   private double distanceFromThreats(BlockPos position) {
      double nearestHorizontalDistance = (1.7976931348623157E308D);

      for (Entity entity : minecraftClient.world
         .getOtherEntities(minecraftClient.player, new Box(position).expand((64.0D)), candidate -> candidate instanceof PlayerEntity || candidate instanceof WardenEntity)) {
         nearestHorizontalDistance = Math.min(nearestHorizontalDistance, Math.hypot(entity.getX() - position.getX(), entity.getZ() - position.getZ()));
      }

      return nearestHorizontalDistance;
   }

   private boolean teleportCountdownVisible() {
      for (ClientBossBar bossbar : ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents().values()) {
         if (TELEPORT_DELAY_PATTERN.matcher(bossbar.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").trim()).matches()) {
            return true;
         }
      }

      return false;
   }

   private boolean isSupplyItem(ItemStack stack) {
      return this.isGoldenCarrot(stack) || this.isInvisibilityPotion(stack) || this.isSpeedPotion(stack);
   }

   private boolean chestWithinReach(BlockPos position) {
      return position != null && minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(position)) <= minecraftClient.player.getBlockInteractionRange() - 1.0;
   }

   private void renderStatus(HudRenderEvent event) {
      if (minecraftClient.player != null && minecraftClient.world != null && this.isEnabled()) {
         float statusTop = HudSettingsModule.getInstance().isElementVisible(("watermark")) && WatermarkHud.bottomFraction() > 0.0F
            ? WatermarkHud.bottomFraction() * event.getHeight() + (6.0F)
            : 2.0F + ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents().size() * (19.0F);
         long elapsedMillis = this.workStartedAt > 0L ? System.currentTimeMillis() - this.workStartedAt : 0L;
         GuiTextRenderer.drawCentered(GuiFont.SEMI, ("AutoWarden — Информация"), event.getWidth() / 2.0F, statusTop, (7.0F), HudStyle.textColor);
         GuiTextRenderer.drawCentered(
            GuiFont.SEMI,
            System.currentTimeMillis() <= this.evadeUntilMillis ? ("Стадия: УБЕГАЮ ОТ ИГРОКА") : "Стадия: " + this.phase.name(),
            event.getWidth() / 2.0F,
            statusTop + (10.0F),
            (7.0F),
            HudStyle.textColor
         );
         GuiTextRenderer.drawCentered(
            GuiFont.SEMI,
            "Время работы: " + String.format(("%02d:%02d:%02d"), elapsedMillis / (3600000L), elapsedMillis / (60000L) % (60L), elapsedMillis / (1000L) % (60L)),
            event.getWidth() / 2.0F,
            statusTop + (20.0F),
            (7.0F),
            HudStyle.textColor
         );
         if (this.phase == AutoWarden.WardenLootPhase.CHEST && this.chestPosition != null && this.chestTimerSeconds(this.chestPosition) >= 0) {
            GuiTextRenderer.drawCentered(
               GuiFont.SEMI,
               "Жду сундук: " + this.chestTimerSeconds(this.chestPosition) + "с",
               event.getWidth() / 2.0F,
               statusTop + (31.0F),
               (7.0F),
               HudStyle.textColor
            );
         }

         if (this.phase == AutoWarden.WardenLootPhase.HUNT) {
            GuiTextRenderer.drawCentered(
               GuiFont.SEMI, "Слутано сундуков: " + this.lootedChests, event.getWidth() / 2.0F, statusTop + (31.0F), (7.0F), HudStyle.textColor
            );
         }
      }
   }

   private boolean valuableNamedItem(ItemStack stack) {
      return !stack.isOf(Items.PHANTOM_MEMBRANE) && !stack.isOf(Items.ELYTRA)
         ? stack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase().contains(("снотворное"))
            || stack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase().contains(("ассасина"))
            || stack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase().contains(("святая"))
            || stack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase().contains(("радиации"))
            || stack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase().contains(("драконья"))
            || stack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase().contains(("неизбежная"))
            || stack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase().contains(("обледенения"))
            || stack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase().contains(("световая"))
            || stack.getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), "").toLowerCase().contains(("палладина"))
         : true;
   }

   private boolean transferMatchingSlots(boolean fromChest, Predicate<Slot> matchesSlot) {
      if (minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler) {
         MovementFreezeController.getInstance().freezeForTicks(1);

         for (Slot slot : containerHandler.slots) {
            if (slot.id >= containerHandler.getRows() * 9 != fromChest && slot.hasStack() && matchesSlot.test(slot)) {
               this.emptyTransferTicks = 0;
               this.chestItemsTransferred = true;
               if (minecraftClient.player.age % 2 == 0) {
                  InventoryActions.clickSlot(slot, 0, SlotActionType.QUICK_MOVE, false);
               }

               return false;
            }
         }

         return ++this.emptyTransferTicks >= 3;
      } else {
         return true;
      }
   }

   private BlockPos clampToRoamingBounds(BlockPos position) {
      return new BlockPos(MathHelper.clamp(position.getX(), -2051, -1937), position.getY(), MathHelper.clamp(position.getZ(), -2060, -1945));
   }

   private int lootAnarchy() {
      return !this.travelAnarchies.isEmpty() && this.depositCycles >= 2 ? this.travelAnarchies.get(this.travelAnarchyIndex % this.travelAnarchies.size()) : this.homeAnarchy;
   }

   private void updateMovementLook() {
      if (this.phase == AutoWarden.WardenLootPhase.HUNT || this.phase == AutoWarden.WardenLootPhase.CHEST) {
         if (!(minecraftClient.currentScreen instanceof HandledScreen) && !(minecraftClient.player.getVelocity().horizontalLengthSquared() < (0.004D))) {
            if (System.currentTimeMillis() - this.lookParametersChangedAt > this.lookParameterIntervalMillis) {
               this.lookParametersChangedAt = System.currentTimeMillis();
               this.lookParameterIntervalMillis = (long)ClientMath.randomFloat((420.0F), (950.0F));
               this.movementLookSpeed = ClientMath.randomFloat((9.0F), (17.0F));
               this.movementYawOffset = ClientMath.randomFloat((-14.0F), (14.0F));
               this.movementPitch = ClientMath.randomFloat((-8.0F), (16.0F));
            }

            float desiredYaw = (float)Math.toDegrees(Math.atan2(-minecraftClient.player.getVelocity().x, minecraftClient.player.getVelocity().z)) + this.movementYawOffset;
            float angleJitter = (float)(
               (
                     Math.sin(minecraftClient.player.age * (0.11F)) * (0.6D)
                        + Math.sin(minecraftClient.player.age * (0.27F) + (1.4F)) * (0.4D)
                  )
                  * (3.0D)
            );
            RotationController.getInstance()
               .requestForDuration(
                  new RotationAngles(desiredYaw + angleJitter, this.movementPitch + angleJitter / (3.0F)),
                  new RotationLimits(this.movementLookSpeed, this.movementLookSpeed * (0.6F), (30.0F), (30.0F)),
                  0L,
                  RotationController.ReturnMode.NONE
               );
         }
      }
   }

   private boolean clearMainHandAndBottles() {
      if (!(minecraftClient.currentScreen instanceof HandledScreen)
         && !this.consumingItem
         && !minecraftClient.player.isUsingItem()
         && !InventorySwapQueue.getInstance().hasPendingSwaps()
         && !ItemUseQueue.getInstance().hasPendingUses()
         && minecraftClient.player.age % 5 == 0) {
         if ((this.phase == AutoWarden.WardenLootPhase.HUNT || this.phase == AutoWarden.WardenLootPhase.CHEST)
            && InventoryActions.findHotbarItem(Items.GLASS_BOTTLE) != -1) {
            if (InventoryActions.selectedHotbarSlot() != InventoryActions.findHotbarItem(Items.GLASS_BOTTLE)) {
               InventoryActions.selectHotbarSlot(InventoryActions.findHotbarItem(Items.GLASS_BOTTLE), false);
               return false;
            } else {
               minecraftClient.player.dropSelectedItem(true);
               return false;
            }
         } else {
            if ((this.phase == AutoWarden.WardenLootPhase.HUNT || this.phase == AutoWarden.WardenLootPhase.CHEST)
               && InventoryActions.findItem(Items.GLASS_BOTTLE) != -1
               && InventoryActions.findEmptySlot(0, 8) != -1) {
               InventorySwapQueue.getInstance().queueSwap(InventoryActions.findItem(Items.GLASS_BOTTLE), InventoryActions.findEmptySlot(0, 8), 1);
               return false;
            }

            if (minecraftClient.player.getMainHandStack().isEmpty()) {
               return false;
            }

            if (InventoryActions.findEmptySlot(0, 8) != -1) {
               InventoryActions.selectHotbarSlot(InventoryActions.findEmptySlot(0, 8), false);
               return false;
            }

            if (this.lastNonSupplyHotbarSlot() != -1 && InventoryActions.findEmptySlot(9, 35) != -1) {
               this.stopBaritone();
               if (minecraftClient.player.getVelocity().horizontalLengthSquared() > (0.001D)) {
                  return true;
               }

               InventorySwapQueue.getInstance().queueSwap(this.lastNonSupplyHotbarSlot(), InventoryActions.findEmptySlot(9, 35), 1);
               return true;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private void recoverAfterDeath() {
      this.deathObserved = false;
      this.releaseUseKey();
      MovementFreezeController.getInstance().freezeForTicks(-1);
      this.stopBaritone();
      this.visitedLootChests.clear();
      if (minecraftClient.currentScreen instanceof DeathScreen) {
         minecraftClient.player.requestRespawn();
         minecraftClient.setScreen(null);
      }

      this.beginTeleport(("home"), this.homeAnarchy, AutoWarden.WardenLootPhase.DEPOSIT);
   }

   private boolean isSpeedPotion(ItemStack stack) {
      return this.potionHasEffect(stack, StatusEffects.SPEED);
   }

   private BlockPos chestApproachPosition(BlockPos position) {
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

   private boolean hasDepositItems() {
      int carrotCount = 0;

      for (int inventoryIndex = 0; inventoryIndex < 36; inventoryIndex++) {
         if (!minecraftClient.player.getInventory().getStack(inventoryIndex).isEmpty()) {
            if (this.isGoldenCarrot(minecraftClient.player.getInventory().getStack(inventoryIndex))) {
               carrotCount += minecraftClient.player.getInventory().getStack(inventoryIndex).getCount();
            } else if (!this.isSupplyItem(minecraftClient.player.getInventory().getStack(inventoryIndex))) {
               return true;
            }
         }
      }

      return carrotCount > 1;
   }

   private boolean playerHasArmor(PlayerEntity player) {
      for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         if (!player.getEquippedStack(equipmentSlot).isEmpty()) {
            return true;
         }
      }

      return false;
   }

   private boolean tickFoodAndBuffs() {
      this.consumingItem = false;
      if (minecraftClient.currentScreen instanceof HandledScreen) {
         this.releaseUseKey();
         return false;
      }

      boolean shouldMaintainBuffs = this.phase == AutoWarden.WardenLootPhase.HUNT
         || this.phase == AutoWarden.WardenLootPhase.BUFF && this.sidebarAnarchy() == this.lootAnarchy() && !this.phaseTimer.hasElapsed((20000L));
      if (shouldMaintainBuffs && this.invisibilityRemainingMillis() <= (240000L) && InventoryActions.findSlot(this::isInvisibilityPotion) != -1) {
         return this.useMatchingItem(this::isInvisibilityPotion, (90.0F));
      }

      if (shouldMaintainBuffs && this.speedRemainingMillis() <= (50000L) && InventoryActions.findSlot(this::isSpeedPotion) != -1) {
         return this.useMatchingItem(this::isSpeedPotion, (90.0F));
      }

      if (this.phase != AutoWarden.WardenLootPhase.HUNT
         && this.phase != AutoWarden.WardenLootPhase.CHEST
         && minecraftClient.player.getHungerManager().getFoodLevel() < 19
         && InventoryActions.findSlot(this::isGoldenCarrot) != -1) {
         return this.useMatchingItem(this::isGoldenCarrot, (-90.0F));
      }

      this.releaseUseKey();
      return false;
   }

   private enum WardenLootPhase {
      SUPPLY,
      BUFF,
      WARP,
      HUNT,
      CHEST,
      ARENA,
      DEPOSIT;
   }
}
