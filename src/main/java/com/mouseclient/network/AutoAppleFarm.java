package com.mouseclient.network;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.mixin.MinecraftCooldownAccessor;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.AuctionHelper;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.modules.impl.render.HudRenderEvent;
import com.mouseclient.util.entity.SmoothedAimVector;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;
import com.mouseclient.util.time.Stopwatch;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(
   name = "AutoAppleFarm",
   description = "Сажает дуб, растит костной мукой, ломает крону мотыгой и ствол топором, чинит мотыгу опытом с аукциона.",
   category = ModuleCategory.MISC
)
public final class AutoAppleFarm extends Module {
   private static AutoAppleFarm INSTANCE;

   public ModeSetting height;
   public DecimalSetting experienceStackPrice;
   private AutoAppleFarm.FarmingPhase phase = AutoAppleFarm.FarmingPhase.PLACE_SAPLING;
   private BlockPos saplingPosition;
   private BlockPos breakingPosition;
   private BlockPos pendingSaplingPosition;
   private int plantingDelayTicks;
   private int growthDelayTicks;
   private int plantingConfirmationTicks;
   private int plantingFailures;
   private Boolean previousPauseOnLostFocus;
   private long nextMissingItemsWarningAt;
   private BlockPos restockChest;
   private int restockStage;
   private int restockTicks;
   private int targetBoneMealCount;
   private Set<BlockPos> exhaustedSupplyChests = new HashSet<>();
   private BlockPos unloadingChest;
   private int unloadStage;
   private int unloadTicks;
   private int repairStage;
   private boolean repairingAxe;
   private final Stopwatch auctionTimer = new Stopwatch();
   private final Stopwatch purchaseTimer = new Stopwatch();
   private boolean purchasePending;
   private long nextHotbarSwitchAt;
   private long hotbarReadyAt;
   private long startedAt;
   private int observedAppleCount;
   private int initialAppleCount;
   private Set<Integer> observedAppleEntityIds = new HashSet<>();
   private long nextHotbarCleanupAt;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (this.isEnabled()) {
         if (minecraftClient.player != null && minecraftClient.world != null) {
            if (minecraftClient.currentScreen instanceof GameMenuScreen) {
               this.stopBreaking();
            } else {
               this.countAppleDrops();
               this.clearUnneededHotbarItems();
               if (!this.tickRepairState()) {
                  if (this.phase != AutoAppleFarm.FarmingPhase.PLACE_SAPLING || !this.tickSupplies()) {
                     if (this.ensureRequiredItems()) {
                        switch (this.phase) {
                           case PLACE_SAPLING:
                              this.placeSapling();
                              break;
                           case GROW:
                              this.growSapling();
                              break;
                           case BREAK_LEAVES:
                              this.breakCanopy();
                              break;
                           case BREAK_LOG:
                              this.breakTrunk();
                        }
                     }
                  }
               }
            }
         }
      }
   };
   private final EventListener<HudRenderEvent> hudRenderListener = event -> this.renderStatus(event);

   private boolean withinInteractionRange(BlockPos position) {
      return minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(position)) <= minecraftClient.player.getBlockInteractionRange() - (0.1D);
   }

   private BlockPos findPlantingPosition() {
      BlockPos position = null;
      double nearestDistance = (1.7976931348623157E308D);

      for (int offsetX = -1; offsetX <= 1; offsetX++) {
         for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            BlockPos soilPosition = new BlockPos(
               (int)Math.floor(minecraftClient.player.getX()) + offsetX,
               (int)Math.floor(minecraftClient.player.getY()) - 1,
               (int)Math.floor(minecraftClient.player.getZ()) + offsetZ
            );
            if (this.validPlantingSoil(soilPosition)
               && (minecraftClient.world.getBlockState(soilPosition.up()).isAir() || minecraftClient.world.getBlockState(soilPosition.up()).isOf(Blocks.OAK_SAPLING))
               && minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(soilPosition)) <= minecraftClient.player.getBlockInteractionRange()
               && minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(soilPosition)) < nearestDistance) {
               nearestDistance = minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(soilPosition));
               position = soilPosition.up().toImmutable();
            }
         }
      }

      return position;
   }

   private float hudTop() {
      return 2.0F + ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents().size() * (19.0F);
   }

   private static String formatElapsedTime(long elapsedMillis) {
      return String.format(("%02d:%02d:%02d"), elapsedMillis / (1000L) / (3600L), elapsedMillis / (1000L) % (3600L) / (60L), elapsedMillis / (1000L) % (60L));
   }

   private void aimAtPosition(Vec3d position, float rotationSpeed) {
      this.requestAim(SmoothedAimVector.anglesForDirection(position.subtract(minecraftClient.player.getEyePos())), rotationSpeed);
   }

   private boolean hasNearbyBlock(Block block) {
      for (int offsetX = -5; offsetX <= 5; offsetX++) {
         for (int offsetY = -5; offsetY <= 5; offsetY++) {
            for (int offsetZ = -5; offsetZ <= 5; offsetZ++) {
               if (minecraftClient.world
                  .getBlockState(
                     new BlockPos(
                        (int)Math.floor(minecraftClient.player.getX()) + offsetX,
                        (int)Math.floor(minecraftClient.player.getY()) + offsetY,
                        (int)Math.floor(minecraftClient.player.getZ()) + offsetZ
                     )
                  )
                  .isOf(block)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public static AutoAppleFarm getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoAppleFarm();
      }

      return INSTANCE;
   }

   private void growSapling() {
      if (this.saplingPosition == null || !minecraftClient.world.getBlockState(this.saplingPosition).isOf(Blocks.OAK_SAPLING)) {
         this.phase = AutoAppleFarm.FarmingPhase.BREAK_LEAVES;
      } else if (this.growthDelayTicks > 0) {
         this.growthDelayTicks--;
      } else if (this.withinInteractionRange(this.saplingPosition)) {
         if (this.growthItemSlot() != -1) {
            boolean growthItemReady = this.selectReadyHotbarSlot(this.growthItemSlot());
            this.aimAtContainer(Vec3d.ofCenter(this.saplingPosition));
            if (growthItemReady && this.alignedWithPosition(Vec3d.ofCenter(this.saplingPosition))) {
               this.interactReachableBlock(this.faceHitResult(this.saplingPosition, Direction.UP));
               this.growthDelayTicks = 1;
            }
         }
      }
   }

   private BlockPos nearestReachableBlock(Block block) {
      BlockPos position = null;
      double nearestDistance = (1.7976931348623157E308D);

      for (int offsetX = -5; offsetX <= 5; offsetX++) {
         for (int offsetY = -5; offsetY <= 5; offsetY++) {
            for (int offsetZ = -5; offsetZ <= 5; offsetZ++) {
               BlockPos candidatePosition = new BlockPos(
                  (int)Math.floor(minecraftClient.player.getX()) + offsetX,
                  (int)Math.floor(minecraftClient.player.getY()) + offsetY,
                  (int)Math.floor(minecraftClient.player.getZ()) + offsetZ
               );
               if (minecraftClient.world.getBlockState(candidatePosition).isOf(block)
                  && minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(candidatePosition)) <= minecraftClient.player.getBlockInteractionRange() - (0.1D)
                  && minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(candidatePosition)) < nearestDistance) {
                  nearestDistance = minecraftClient.player.getEyePos().distanceTo(Vec3d.ofCenter(candidatePosition));
                  position = candidatePosition;
               }
            }
         }
      }

      return position;
   }

   private void resetFarmingCycle() {
      this.phase = AutoAppleFarm.FarmingPhase.PLACE_SAPLING;
      this.saplingPosition = null;
      this.breakingPosition = null;
      this.pendingSaplingPosition = null;
      this.plantingDelayTicks = 0;
      this.growthDelayTicks = 0;
      this.plantingConfirmationTicks = 0;
      this.plantingFailures = 0;
   }

   private void breakTrunk() {
      if (this.nearestReachableBlock(Blocks.OAK_LOG) == null) {
         this.stopBreaking();
         this.resetFarmingCycle();
         this.plantingDelayTicks = 16;
      } else {
         this.breakTreeBlock(this.nearestReachableBlock(Blocks.OAK_LOG), (16.0F));
      }
   }

   private int largestSaplingHotbarSlot() {
      int bestSlot = -1;

      for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
         if (minecraftClient.player.getInventory().getStack(hotbarSlot).isOf(Items.OAK_SAPLING)
            && (bestSlot == -1 || minecraftClient.player.getInventory().getStack(hotbarSlot).getCount() > minecraftClient.player.getInventory().getStack(bestSlot).getCount())
            )
          {
            bestSlot = hotbarSlot;
         }
      }

      return bestSlot;
   }

   @Override
   protected void onDisable() {
      this.stopBreaking();
      this.repairStage = 0;
      this.purchasePending = false;
      this.restockStage = 0;
      this.restockTicks = 0;
      this.restockChest = null;
      this.unloadStage = 0;
      this.unloadTicks = 0;
      this.unloadingChest = null;
      this.startedAt = 0L;
      this.restoreCameraRotation();
      if (this.previousPauseOnLostFocus != null && minecraftClient.options != null) {
         minecraftClient.options.pauseOnLostFocus = this.previousPauseOnLostFocus;
         this.previousPauseOnLostFocus = null;
      }
   }

   private void clearUnneededHotbarItems() {
      if (minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
         if (System.currentTimeMillis() >= this.nextHotbarCleanupAt) {
            if (InventoryActions.findEmptySlot(9, 35) != -1) {
               for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
                  if (!this.keepHotbarSlot(hotbarSlot)) {
                     InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(hotbarSlot), 0, SlotActionType.QUICK_MOVE, false);
                     this.nextHotbarCleanupAt = System.currentTimeMillis() + (250L);
                     return;
                  }
               }
            }
         }
      }
   }

   private boolean containerOpen() {
      return minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler;
   }

   private String statusLabel() {
      if (this.repairStage == 1) {
         return ("BUYING XP");
      } else if (this.repairStage == 2) {
         return ("REPAIRING");
      } else if (this.restockStage != 0) {
         return ("RESTOCKING");
      } else if (this.unloadStage != 0) {
         return ("UNLOADING");
      } else {
         return !this.hasRequiredItems() ? ("NO ITEMS") : ("WORKING");
      }
   }

   private void releaseAttackKey() {
      if (minecraftClient.options != null) {
         minecraftClient.options.attackKey.setPressed(false);
      }
   }

   private boolean interactReachableBlock(BlockHitResult hitResult) {
      if (!this.withinInteractionRange(hitResult.getBlockPos())) {
         return false;
      }

      minecraftClient.crosshairTarget = hitResult;
      if (!minecraftClient.interactionManager.interactBlock(minecraftClient.player, Hand.MAIN_HAND, hitResult).isAccepted()) {
         return false;
      }

      minecraftClient.player.swingHand(Hand.MAIN_HAND);
      return true;
   }

   private void openContainer(BlockPos position) {
      minecraftClient.crosshairTarget = this.faceHitResult(position, this.interactionFace(position));
      minecraftClient.interactionManager.interactBlock(minecraftClient.player, Hand.MAIN_HAND, this.faceHitResult(position, this.interactionFace(position)));
      minecraftClient.player.swingHand(Hand.MAIN_HAND);
   }

   private void placeSapling() {
      this.stopBreaking();
      if (this.plantingDelayTicks > 0) {
         this.plantingDelayTicks--;
      } else if (this.pendingSaplingPosition != null) {
         if (this.plantingConfirmationTicks > 0) {
            this.plantingConfirmationTicks--;
         } else if (minecraftClient.world.getBlockState(this.pendingSaplingPosition).isOf(Blocks.OAK_SAPLING)) {
            this.saplingPosition = this.pendingSaplingPosition.toImmutable();
            this.pendingSaplingPosition = null;
            this.plantingFailures = 0;
            this.phase = AutoAppleFarm.FarmingPhase.GROW;
         } else {
            this.pendingSaplingPosition = null;
            this.plantingFailures++;
            this.plantingDelayTicks = 2;
            if (this.plantingFailures >= 6) {
               this.plantingFailures = 0;
               this.plantingDelayTicks = 20;
            }
         }
      } else {
         BlockPos position = this.findPlantingPosition();
         if (position != null) {
            if (minecraftClient.world.getBlockState(position).isOf(Blocks.OAK_LOG) || minecraftClient.world.getBlockState(position).isOf(Blocks.OAK_LEAVES)) {
               this.phase = AutoAppleFarm.FarmingPhase.BREAK_LEAVES;
            } else if (minecraftClient.world.getBlockState(position).isOf(Blocks.OAK_SAPLING)) {
               this.saplingPosition = position.toImmutable();
               this.phase = AutoAppleFarm.FarmingPhase.GROW;
            } else if (minecraftClient.world.getBlockState(position).isAir()) {
               if (this.validPlantingSoil(position.down()) && this.withinInteractionRange(position.down())) {
                  if (InventoryActions.findSlot(stack -> stack.isOf(Items.OAK_SAPLING), 0, 8) != -1) {
                     boolean saplingItemReady = this.selectReadyHotbarSlot(InventoryActions.findSlot(candidateStack -> candidateStack.isOf(Items.OAK_SAPLING), 0, 8));
                     this.aimAtContainer(Vec3d.ofCenter(position.down()).add(0.0, (0.5D), 0.0));
                     if (saplingItemReady && this.alignedWithPosition(Vec3d.ofCenter(position.down()).add(0.0, (0.5D), 0.0))) {
                        if (!this.interactReachableBlock(this.faceHitResult(position.down(), Direction.UP))) {
                           this.plantingFailures++;
                           this.plantingDelayTicks = 2;
                           if (this.plantingFailures >= 6) {
                              this.plantingFailures = 0;
                              this.plantingDelayTicks = 20;
                           }
                        } else {
                           this.pendingSaplingPosition = position.toImmutable();
                           this.plantingConfirmationTicks = 2;
                           this.plantingDelayTicks = 1;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean validPlantingSoil(BlockPos position) {
      return minecraftClient.world.getBlockState(position).isOf(Blocks.GRASS_BLOCK)
         || minecraftClient.world.getBlockState(position).isOf(Blocks.DIRT)
         || minecraftClient.world.getBlockState(position).isOf(Blocks.COARSE_DIRT)
         || minecraftClient.world.getBlockState(position).isOf(Blocks.ROOTED_DIRT);
   }

   private void requestAim(RotationAngles angles, float rotationSpeed) {
      RotationController.getInstance().requestForDuration(angles, new RotationLimits(rotationSpeed, rotationSpeed, (45.0F), (45.0F)), (300L));
   }

   private void finishToolRepair() {
      this.repairStage = 0;
      this.purchasePending = false;
      if (minecraftClient.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE) && InventoryActions.findEmptySlot(0, 35) != -1) {
         InventorySwapQueue.getInstance().queueSwap(InventoryActions.findEmptySlot(0, 35), 40, 1);
      }

      this.restoreCameraRotation();
   }

   private int repairToolSlot() {
      return this.repairingAxe ? this.axeHotbarSlot() : this.hoeHotbarSlot();
   }

   private int growthItemSlot() {
      return this.height.isSelected(("Озеленитель")) ? this.hoeHotbarSlot() : InventoryActions.findSlot(stack -> stack.isOf(Items.BONE_MEAL), 0, 8);
   }

   private int emptyInventorySlots() {
      int emptyCount = 0;

      for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
         if (minecraftClient.player.getInventory().getStack(inventorySlot).isEmpty()) {
            emptyCount++;
         }
      }

      return emptyCount;
   }

   private void countAppleDrops() {
      for (ItemEntity appleEntity : minecraftClient.world
         .getEntitiesByClass(ItemEntity.class, minecraftClient.player.getBoundingBox().expand((10.0D)), droppedItem -> droppedItem.getStack().isOf(Items.APPLE))) {
         if (this.observedAppleEntityIds.add(appleEntity.getId())) {
            this.observedAppleCount = this.observedAppleCount + appleEntity.getStack().getCount();
         }
      }
   }

   private boolean keepHotbarSlot(int inventorySlot) {
      if (minecraftClient.player.getInventory().getStack(inventorySlot).isEmpty()) {
         return true;
      } else if (minecraftClient.player.getInventory().getStack(inventorySlot).isIn(ItemTags.HOES)
         || minecraftClient.player.getInventory().getStack(inventorySlot).isIn(ItemTags.AXES)) {
         return true;
      } else {
         return minecraftClient.player.getInventory().getStack(inventorySlot).isOf(Items.BONE_MEAL)
            ? !this.height.isSelected(("Озеленитель"))
            : minecraftClient.player.getInventory().getStack(inventorySlot).isOf(Items.OAK_SAPLING) && inventorySlot == this.largestSaplingHotbarSlot();
      }
   }

   private boolean tickUnloading() {
      if (this.unloadStage == 0) {
         if (InventoryActions.findEmptySlot(0, 35) != -1 && this.emptyInventorySlots() > 4) {
            return false;
         }

         if (!this.hasUnloadableItems()) {
            return false;
         }

         this.unloadingChest = this.findUnloadingChest();
         if (this.unloadingChest == null) {
            return false;
         }

         this.stopBreaking();
         this.unloadStage = 1;
         this.unloadTicks = 0;
         return true;
      } else if (this.unloadStage == 1) {
         if (this.containerOpen()) {
            this.unloadStage = 2;
            this.unloadTicks = 0;
            return true;
         }

         this.aimAtContainer(Vec3d.ofCenter(this.unloadingChest));
         if (this.unloadTicks % 5 == 0) {
            this.openContainer(this.unloadingChest);
         }

         if (++this.unloadTicks > 60) {
            this.unloadStage = 0;
         }

         return true;
      } else if (this.unloadStage == 2) {
         if (!this.containerOpen()) {
            this.unloadStage = 0;
            return true;
         }

         if (this.hasUnloadableItems() && this.unloadTicks <= 200) {
            if (this.unloadTicks++ % 2 == 0 && this.unloadableInventorySlot() != null) {
               InventoryActions.clickSlot(this.unloadableInventorySlot(), 0, SlotActionType.QUICK_MOVE, false);
            }

            return true;
         } else {
            this.unloadStage = 3;
            return true;
         }
      } else {
         InventoryActions.closeInventory(false);
         this.unloadingChest = null;
         this.unloadStage = 0;
         return true;
      }
   }

   private boolean tickRestocking() {
      if (this.restockStage == 0) {
         if (this.height.isSelected(("Озеленитель"))) {
            return false;
         }

         if (InventoryActions.countItems(Items.BONE_MEAL) > 64) {
            return false;
         }

         this.restockChest = this.findSupplyChest(this.exhaustedSupplyChests, null);
         if (this.restockChest == null) {
            return false;
         }

         this.stopBreaking();
         this.targetBoneMealCount = InventoryActions.countItems(Items.BONE_MEAL) + 64 * ThreadLocalRandom.current().nextInt(3, 6);
         this.restockStage = 1;
         this.restockTicks = 0;
         return true;
      } else if (this.restockStage == 1) {
         if (this.containerOpen()) {
            this.restockStage = 2;
            this.restockTicks = 0;
            return true;
         }

         this.aimAtContainer(Vec3d.ofCenter(this.restockChest));
         if (this.restockTicks % 5 == 0) {
            this.openContainer(this.restockChest);
         }

         if (++this.restockTicks > 60) {
            this.exhaustedSupplyChests.add(this.restockChest);
            this.restockStage = 0;
         }

         return true;
      } else if (this.restockStage == 2) {
         if (!this.containerOpen()) {
            this.restockStage = 0;
            return true;
         }

         if (InventoryActions.countItems(Items.BONE_MEAL) >= this.targetBoneMealCount || this.restockTicks > 200) {
            this.restockStage = 3;
            return true;
         }

         if (this.containerItemSlot(Items.BONE_MEAL) == null) {
            if (InventoryActions.countItems(Items.BONE_MEAL) <= 64) {
               this.exhaustedSupplyChests.add(this.restockChest);
            }

            this.restockStage = 3;
            return true;
         } else {
            if (this.restockTicks++ % 2 == 0) {
               InventoryActions.clickSlot(this.containerItemSlot(Items.BONE_MEAL), 0, SlotActionType.QUICK_MOVE, false);
            }

            return true;
         }
      } else {
         InventoryActions.closeInventory(false);
         this.restockChest = null;
         this.restockStage = 0;
         return true;
      }
   }

   private boolean alignedWithPosition(Vec3d position) {
      return this.alignedWithAngles(SmoothedAimVector.anglesForDirection(position.subtract(minecraftClient.player.getEyePos())));
   }

   private void breakTreeBlock(BlockPos position, float rotationSpeed) {
      if (!this.withinInteractionRange(position)) {
         this.releaseAttackKey();
      } else {
         this.aimAtPosition(Vec3d.ofCenter(position), rotationSpeed);
         BlockHitResult hitResult = this.raycastTreeBlock();
         if (hitResult == null) {
            this.releaseAttackKey();
         } else {
            int toolSlot;
            if (minecraftClient.world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.OAK_LEAVES)) {
               toolSlot = this.hoeHotbarSlot();
            } else {
               if (!minecraftClient.world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.OAK_LOG)) {
                  this.releaseAttackKey();
                  return;
               }

               toolSlot = this.axeHotbarSlot();
            }

            if (toolSlot == -1) {
               this.releaseAttackKey();
            } else if (!this.selectReadyHotbarSlot(toolSlot)) {
               this.releaseAttackKey();
            } else {
               this.breakingPosition = hitResult.getBlockPos().toImmutable();
               if (minecraftClient.currentScreen == null && minecraftClient.isWindowFocused()) {
                  minecraftClient.options.attackKey.setPressed(true);
               } else {
                  minecraftClient.options.attackKey.setPressed(false);
                  minecraftClient.interactionManager.updateBlockBreakingProgress(hitResult.getBlockPos(), hitResult.getSide());
                  minecraftClient.player.swingHand(Hand.MAIN_HAND);
               }
            }
         }
      }
   }

   private String missingItemNames() {
      String missingItems = "";
      if (InventoryActions.findSlot(stack -> stack.isIn(ItemTags.HOES), 0, 8) == -1) {
         missingItems = missingItems + "мотыга, ";
      }

      if (InventoryActions.findSlot(candidateStack -> candidateStack.isIn(ItemTags.AXES), 0, 8) == -1) {
         missingItems = missingItems + "топор, ";
      }

      if (InventoryActions.findSlot(candidateStack -> candidateStack.isOf(Items.OAK_SAPLING), 0, 8) == -1) {
         missingItems = missingItems + "саженец дуба, ";
      }

      if (!this.height.isSelected(("Озеленитель")) && InventoryActions.findSlot(candidateStack -> candidateStack.isOf(Items.BONE_MEAL), 0, 8) == -1) {
         missingItems = missingItems + "костная мука, ";
      }

      return missingItems.isEmpty() ? ("-") : missingItems.substring(0, missingItems.length() - 2);
   }

   private boolean alignedWithAngles(RotationAngles angles) {
      return Math.abs(MathHelper.wrapDegrees(angles.getYaw() - minecraftClient.player.getYaw())) <= (12.0F)
         && Math.abs(angles.getPitch() - minecraftClient.player.getPitch()) <= (12.0F);
   }

   private long elapsedFarmingTime() {
      return this.startedAt > 0L ? System.currentTimeMillis() - this.startedAt : 0L;
   }

   private boolean tickExperiencePurchase() {
      if (InventoryActions.findItem(Items.EXPERIENCE_BOTTLE) == -1 && !minecraftClient.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
         if (minecraftClient.currentScreen instanceof GenericContainerScreen auctionScreen) {
            if (!auctionScreen.getTitle().getString().contains(("Подозрительная цена")) && !auctionScreen.getTitle().getString().contains(("Подтверждение покупки"))) {
               if (!AuctionHelper.getInstance().isAuctionTitle(auctionScreen.getTitle().getString())) {
                  return true;
               }

               if (this.purchasePending) {
                  if (!this.purchaseTimer.hasElapsed((3000L))) {
                     return true;
                  }

                  this.purchasePending = false;
               }

               int cheapestSlot = -1;
               long cheapestPrice = (9223372036854775807L);

               for (int lotSlot = 0; lotSlot < minecraftClient.player.currentScreenHandler.slots.size() - 36 - 9; lotSlot++) {
                  if (minecraftClient.player.currentScreenHandler.getSlot(lotSlot).hasStack()
                     && minecraftClient.player.currentScreenHandler.getSlot(lotSlot).getStack().isOf(Items.EXPERIENCE_BOTTLE)
                     && !AuctionHelper.getInstance()
                        .normalizeServerText(minecraftClient.player.currentScreenHandler.getSlot(lotSlot).getStack().getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))
                        .contains(("не актуален"))
                     && !AuctionHelper.getInstance()
                        .normalizeServerText(minecraftClient.player.currentScreenHandler.getSlot(lotSlot).getStack().getName().getString().replaceAll(("(?i)§[0-9a-fk-or]"), ""))
                        .matches((".*\\[\\s*\\d+\\s*ур.*"))
                     && !minecraftClient.player
                        .currentScreenHandler
                        .getSlot(lotSlot)
                        .getStack()
                        .getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT)
                        .lines()
                        .stream()
                        .anyMatch(text -> AuctionHelper.getInstance().normalizeServerText(text.getString().replaceAll(("(?i)§[0-9a-fk-or]"), "")).matches((".*содержит.*опыт.*")))
                     && AutoMine.getInstance().hasAdditionalExperienceLore(minecraftClient.player.currentScreenHandler.getSlot(lotSlot).getStack())
                     && AuctionHelper.getInstance().lotPrice(minecraftClient.player.currentScreenHandler.getSlot(lotSlot).getStack()) >= 0
                     && !(
                        AuctionHelper.getInstance().lotPrice(minecraftClient.player.currentScreenHandler.getSlot(lotSlot).getStack())
                           > this.experienceStackPrice.getValue()
                     )
                     && AuctionHelper.getInstance().lotPrice(minecraftClient.player.currentScreenHandler.getSlot(lotSlot).getStack()) < cheapestPrice) {
                     cheapestSlot = lotSlot;
                     cheapestPrice = AuctionHelper.getInstance().lotPrice(minecraftClient.player.currentScreenHandler.getSlot(lotSlot).getStack());
                  }
               }

               if (cheapestSlot != -1) {
                  InventoryActions.clickSlot(cheapestSlot, 0, SlotActionType.PICKUP, false);
                  this.purchasePending = true;
                  this.purchaseTimer.reset();
                  this.auctionTimer.reset();
                  return true;
               }

               if (this.auctionTimer.hasElapsed((400L))) {
                  InventoryActions.clickSlot(minecraftClient.player.currentScreenHandler.slots.size() - 36 - 5, 0, SlotActionType.PICKUP, false);
                  this.auctionTimer.reset();
               }

               return true;
            } else if (minecraftClient.player
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
               this.auctionTimer.reset();
               return true;
            } else {
               InventoryActions.clickSlot(0, 0, SlotActionType.QUICK_MOVE, false);
               this.auctionTimer.reset();
               return true;
            }
         } else {
            if (this.auctionTimer.hasElapsed((3000L))) {
               minecraftClient.player.networkHandler.sendChatCommand(("ah search опыт"));
               this.auctionTimer.reset();
            }

            return true;
         }
      } else {
         if (minecraftClient.currentScreen != null) {
            minecraftClient.player.closeHandledScreen();
         }

         this.purchasePending = false;
         this.repairStage = 2;
         this.auctionTimer.reset();
         return true;
      }
   }

   private boolean ensureRequiredItems() {
      if (this.hasRequiredItems()) {
         return true;
      }

      this.stopBreaking();
      if (System.currentTimeMillis() < this.nextMissingItemsWarningAt) {
         return false;
      }

      this.nextMissingItemsWarningAt = System.currentTimeMillis() + (8000L);
      ChatMessages.print("&8[&bмаусклиент&8] &fВ хотбаре не хватает: &c" + this.missingItemNames());
      return false;
   }

   private Direction interactionFace(BlockPos position) {
      if (Math.abs(minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).y)
            > Math.abs(minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).x)
         && Math.abs(minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).y)
            > Math.abs(minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).z)) {
         return minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).y >= 0.0 ? Direction.UP : Direction.DOWN;
      } else if (Math.abs(minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).x)
         >= Math.abs(minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).z)) {
         return minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).x >= 0.0 ? Direction.EAST : Direction.WEST;
      } else {
         return minecraftClient.player.getEyePos().subtract(Vec3d.ofCenter(position)).z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
      }
   }

   private void renderStatus(HudRenderEvent event) {
      if (minecraftClient.player != null && minecraftClient.world != null && this.isEnabled()) {
         GuiTextRenderer.drawCentered(GuiFont.SEMI, ("AutoAppleFarm — Информация"), event.getWidth() / 2.0F, this.hudTop(), (7.0F), HudStyle.textColor);
         GuiTextRenderer.drawCentered(
            GuiFont.SEMI,
            "Текущее состояние: " + this.statusLabel(),
            event.getWidth() / 2.0F,
            this.hudTop() + (10.0F),
            (7.0F),
            HudStyle.textColor
         );
         GuiTextRenderer.drawCentered(
            GuiFont.SEMI,
            "Налутано яблок: " + this.observedAppleCount,
            event.getWidth() / 2.0F,
            this.hudTop() + (20.0F),
            (7.0F),
            HudStyle.textColor
         );
         GuiTextRenderer.drawCentered(
            GuiFont.SEMI,
            "Время работы: " + formatElapsedTime(this.elapsedFarmingTime()),
            event.getWidth() / 2.0F,
            this.hudTop() + (30.0F),
            (7.0F),
            HudStyle.textColor
         );
      }
   }

   private boolean hasUnloadableItems() {
      return InventoryActions.countItems(Items.OAK_LOG) > 0
         || InventoryActions.countItems(Items.OAK_LEAVES) > 0
         || InventoryActions.countItems(Items.STICK) > 0
         || InventoryActions.countItems(Items.APPLE) > 0
         || InventoryActions.countItems(Items.OAK_SAPLING) > 64;
   }

   private BlockPos findUnloadingChest() {
      return this.findSupplyChest(null, Blocks.HOPPER);
   }

   @Override
   protected void onEnable() {
      if (minecraftClient.options != null) {
         this.previousPauseOnLostFocus = minecraftClient.options.pauseOnLostFocus;
         minecraftClient.options.pauseOnLostFocus = false;
      }

      this.startedAt = System.currentTimeMillis();
      this.observedAppleCount = 0;
      this.initialAppleCount = minecraftClient.player != null ? InventoryActions.countItems(Items.APPLE) : 0;
      this.nextMissingItemsWarningAt = 0L;
      this.nextHotbarSwitchAt = 0L;
      this.hotbarReadyAt = 0L;
      this.nextHotbarCleanupAt = 0L;
      this.observedAppleEntityIds.clear();
      this.exhaustedSupplyChests.clear();
      this.resetFarmingCycle();
   }

   private void breakCanopy() {
      if (this.nearestReachableBlock(Blocks.OAK_LEAVES) != null) {
         this.breakTreeBlock(this.nearestReachableBlock(Blocks.OAK_LEAVES), (32.0F));
      } else if (this.hasNearbyBlock(Blocks.OAK_LEAVES) && this.nearestReachableBlock(Blocks.OAK_LOG) != null) {
         this.breakTreeBlock(this.nearestReachableBlock(Blocks.OAK_LOG), (16.0F));
      } else {
         this.stopBreaking();
         this.phase = AutoAppleFarm.FarmingPhase.BREAK_LOG;
      }
   }

   private int axeHotbarSlot() {
      return InventoryActions.findSlot(stack -> stack.isIn(ItemTags.AXES), 0, 8);
   }

   private void aimAtContainer(Vec3d position) {
      this.aimAtPosition(position, (32.0F));
   }

   private boolean isTreeDrop(ItemStack stack) {
      return stack.isOf(Items.OAK_LEAVES) || stack.isOf(Items.OAK_LOG) || stack.isOf(Items.STICK) || stack.isOf(Items.APPLE);
   }

   private boolean selectReadyHotbarSlot(int hotbarSlot) {
      if (hotbarSlot < 0) {
         return false;
      }

      if (minecraftClient.player.getInventory().getSelectedSlot() != hotbarSlot) {
         if (System.currentTimeMillis() < this.nextHotbarSwitchAt) {
            return false;
         }

         this.stopBreaking();
         InventoryActions.selectHotbarSlot(hotbarSlot, false);
         this.nextHotbarSwitchAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong((300L), (501L));
         this.hotbarReadyAt = System.currentTimeMillis() + (150L);
         return false;
      } else {
         return System.currentTimeMillis() >= this.hotbarReadyAt;
      }
   }

   private Slot containerItemSlot(Item item) {
      if (minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler) {
         for (Slot slot : containerHandler.slots) {
            if (slot.id < containerHandler.getRows() * 9 && slot.hasStack() && slot.getStack().isOf(item)) {
               return slot;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void restoreCameraRotation() {
      RotationController.getInstance().reset();
      if (minecraftClient.player != null) {
         minecraftClient.player.setYaw(RotationAngles.cameraYaw());
         minecraftClient.player.setPitch(RotationAngles.cameraPitch());
         minecraftClient.player.headYaw = RotationAngles.cameraYaw();
         minecraftClient.player.bodyYaw = RotationAngles.cameraYaw();
         minecraftClient.player.lastYaw = RotationAngles.cameraYaw();
         minecraftClient.player.lastPitch = RotationAngles.cameraPitch();
      }
   }

   private BlockHitResult faceHitResult(BlockPos position, Direction face) {
      return new BlockHitResult(
         Vec3d.ofCenter(position).add(face.getOffsetX() * (0.5D), face.getOffsetY() * (0.5D), face.getOffsetZ() * (0.5D)), face, position, false
      );
   }

   private float durabilityFraction(ItemStack stack) {
      return !stack.isEmpty() && stack.isDamageable() && stack.getMaxDamage() > 0 ? 1.0F - (float)stack.getDamage() / stack.getMaxDamage() : 1.0F;
   }

   private BlockHitResult raycastTreeBlock() {
      BlockHitResult hitResult = minecraftClient.world
         .raycast(
            new RaycastContext(
               minecraftClient.player.getEyePos(),
               minecraftClient.player
                  .getEyePos()
                  .add(
                     new RotationAngles(minecraftClient.player.getYaw(), minecraftClient.player.getPitch())
                        .direction()
                        .multiply(minecraftClient.player.getBlockInteractionRange())
                  ),
               ShapeType.OUTLINE,
               FluidHandling.NONE,
               minecraftClient.player
            )
         );
      if (hitResult.getType() != Type.BLOCK) {
         return null;
      } else {
         return !minecraftClient.world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.OAK_LEAVES)
               && !minecraftClient.world.getBlockState(hitResult.getBlockPos()).isOf(Blocks.OAK_LOG)
            ? null
            : hitResult;
      }
   }

   private boolean tickToolRepair() {
      if (minecraftClient.currentScreen != null) {
         minecraftClient.player.closeHandledScreen();
      }

      if (!minecraftClient.player.currentScreenHandler.getCursorStack().isEmpty() && InventoryActions.findEmptySlot(0, 35) != -1) {
         InventoryActions.clickSlot(InventoryActions.screenSlotForInventoryIndex(InventoryActions.findEmptySlot(0, 35)), 0, SlotActionType.PICKUP, false);
         return true;
      }

      if (this.repairToolSlot() == -1 || this.durabilityFraction(minecraftClient.player.getInventory().getStack(this.repairToolSlot())) >= (0.9F)) {
         this.finishToolRepair();
         return false;
      }

      if (!this.selectReadyHotbarSlot(this.repairToolSlot())) {
         return true;
      }

      if (minecraftClient.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
         this.aimWithDefaultSpeed(new RotationAngles(minecraftClient.player.getYaw(), (90.0F)));
         if (minecraftClient.player.getPitch() < (75.0F)) {
            return true;
         }

         ((MinecraftCooldownAccessor)(Object)minecraftClient).setRightClickDelay(0);
         InventoryActions.interactItem(Hand.OFF_HAND);
         minecraftClient.player.swingHand(Hand.OFF_HAND);
         return true;
      } else if (InventoryActions.findItem(Items.EXPERIENCE_BOTTLE) != -1) {
         if (!InventorySwapQueue.getInstance().hasPendingSwaps()) {
            InventorySwapQueue.getInstance().queueSwap(InventoryActions.findItem(Items.EXPERIENCE_BOTTLE), 40, 1);
         }

         return true;
      } else {
         this.repairStage = 1;
         this.purchasePending = false;
         this.auctionTimer.reset();
         return true;
      }
   }

   private boolean tickRepairState() {
      if (this.repairStage != 0) {
         this.stopBreaking();
         return this.repairStage == 1 ? this.tickExperiencePurchase() : this.tickToolRepair();
      }

      if (this.hoeHotbarSlot() != -1 && this.durabilityFraction(minecraftClient.player.getInventory().getStack(this.hoeHotbarSlot())) < (0.15F)) {
         this.repairingAxe = false;
      } else {
         if (this.axeHotbarSlot() == -1 || !(this.durabilityFraction(minecraftClient.player.getInventory().getStack(this.axeHotbarSlot())) < (0.15F))) {
            return false;
         }

         this.repairingAxe = true;
      }

      this.repairStage = InventoryActions.findItem(Items.EXPERIENCE_BOTTLE) == -1 && !minecraftClient.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)
         ? 1
         : 2;
      this.purchasePending = false;
      this.auctionTimer.reset();
      return true;
   }

   private AutoAppleFarm() {
      this.height = this.addSetting(new ModeSetting(("Рост"), ("Чем растить саженец: костной мукой или зачарованием Озеленитель на мотыге."), ("Костная мука"), ("Костная мука"), ("Озеленитель")));
      this.experienceStackPrice = this.addSetting(new DecimalSetting(("Цена стака опыта"), ("Дороже этого стак опыта на аукционе не покупать."), (200000.0D), (10000.0D), (1000000.0D), (10000.0D)));
   }

   private int hoeHotbarSlot() {
      return InventoryActions.findSlot(stack -> stack.isIn(ItemTags.HOES), 0, 8);
   }

   private void aimWithDefaultSpeed(RotationAngles angles) {
      this.requestAim(angles, (32.0F));
   }

   private Slot unloadableInventorySlot() {
      if (minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler) {
         for (Slot slot : containerHandler.slots) {
            if (slot.id >= containerHandler.getRows() * 9 && slot.hasStack()) {
               if (this.isTreeDrop(slot.getStack())) {
                  return slot;
               }

               if (slot.getStack().isOf(Items.OAK_SAPLING) && InventoryActions.countItems(Items.OAK_SAPLING) - slot.getStack().getCount() >= 64) {
                  return slot;
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private void stopBreaking() {
      if (minecraftClient.interactionManager != null && minecraftClient.interactionManager.isBreakingBlock()) {
         minecraftClient.interactionManager.cancelBlockBreaking();
      }

      if (minecraftClient.options != null) {
         minecraftClient.options.attackKey.setPressed(false);
      }

      this.breakingPosition = null;
   }

   private boolean hasRequiredItems() {
      return InventoryActions.findSlot(stack -> stack.isIn(ItemTags.HOES), 0, 8) != -1
         && InventoryActions.findSlot(candidateStack -> candidateStack.isIn(ItemTags.AXES), 0, 8) != -1
         && InventoryActions.findSlot(candidateStack -> candidateStack.isOf(Items.OAK_SAPLING), 0, 8) != -1
         && (this.height.isSelected(("Озеленитель")) || InventoryActions.findSlot(candidateStack -> candidateStack.isOf(Items.BONE_MEAL), 0, 8) != -1);
   }

   private BlockPos findSupplyChest(Set<BlockPos> excludedChests, Block requiredBlockBelow) {
      BlockPos position = null;
      double nearestDistanceSquared = (1.7976931348623157E308D);

      for (int offsetX = -5; offsetX <= 5; offsetX++) {
         for (int offsetY = -5; offsetY <= 5; offsetY++) {
            for (int offsetZ = -5; offsetZ <= 5; offsetZ++) {
               BlockPos candidateChest = minecraftClient.player.getBlockPos().add(offsetX, offsetY, offsetZ);
               if ((minecraftClient.world.getBlockState(candidateChest).isOf(Blocks.CHEST) || minecraftClient.world.getBlockState(candidateChest).isOf(Blocks.TRAPPED_CHEST))
                  && (excludedChests == null || !excludedChests.contains(candidateChest))
                  && (requiredBlockBelow == null || minecraftClient.world.getBlockState(candidateChest.down()).isOf(requiredBlockBelow))
                  && this.withinInteractionRange(candidateChest)
                  && minecraftClient.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(candidateChest)) < nearestDistanceSquared) {
                  nearestDistanceSquared = minecraftClient.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(candidateChest));
                  position = candidateChest.toImmutable();
               }
            }
         }
      }

      return position;
   }

   private boolean tickSupplies() {
      return this.tickUnloading() ? true : this.tickRestocking();
   }

   private enum FarmingPhase {
      PLACE_SAPLING,
      GROW,
      BREAK_LEAVES,
      BREAK_LOG;
   }
}
