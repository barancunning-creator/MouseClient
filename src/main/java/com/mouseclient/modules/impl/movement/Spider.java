package com.mouseclient.modules.impl.movement;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.ClientCommonNetworkHandlerAccessor;
import com.mouseclient.mixin.ClientInteractionManagerInvoker;
import com.mouseclient.mixin.LivingEntityAccessor;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.combat.MovementInputEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.ClientTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.entity.PlayerMovementSimulation;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.Stopwatch;
import java.util.ArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

@ModuleInfo(name = "Spider", description = "Позволяет лазить по стенам.", category = ModuleCategory.MOVEMENT)
public final class Spider extends Module {
   private static Spider INSTANCE;
   public ModeSetting mode;
   private final Stopwatch climbingTimer = new Stopwatch();
   private final Stopwatch swapTimer = new Stopwatch();
   private Integer ladderInventorySlot;
   private BlockHitResult pendingPlacement;
   private int swappedHeadSlot = -1;
   private boolean jumpAfterPlacement;
   private boolean sneaking;
   private volatile KeepAliveC2SPacket pendingKeepAlive;
   private volatile ClientConnection keepAliveConnection;
   private volatile long keepAliveQueuedAtMillis;
   private final EventListener<PlayerMovementEvent> playerMovementListener = event -> {
      if (minecraftClient.player != null
         && minecraftClient.world != null
         && minecraftClient.interactionManager != null
         && this.mode.isSelected(("Ladders"))) {
         this.climbWithLadders(event);
      }
   };
   private final EventListener<ClientTickEvent> clientTickListener = event -> {
      if (event.isPre()) {
         if (this.pendingKeepAlive != null
            && System.currentTimeMillis() - this.keepAliveQueuedAtMillis >= MathHelper.clamp((480L) - (3L) * this.latencyMillis(), 0L, (800L))) {
            this.flushKeepAlive();
         }

         if (minecraftClient.player != null && minecraftClient.world != null && minecraftClient.interactionManager != null) {
            if (this.sneaking && (!this.mode.isSelected(("FunTime")) || this.climbingTimer.hasElapsed((100L)))) {
               this.setSneaking(false);
            }

            if (this.mode.isSelected(("FunTime"))) {
               if (this.climbingTimer.hasElapsed((100L))) {
                  int headInventorySlot = this.headSlot();
                  if (headInventorySlot != -1) {
                     if (!minecraftClient.player.getMainHandStack().isOf(Items.PLAYER_HEAD)) {
                        Box predictedBox = this.climbingContactBox(PlayerMovementSimulation.predictLocalPlayer(1).boundingBox.contract((1.0E-5D)));
                        if (!this.findPlacementHits(predictedBox).isEmpty() && this.swappedHeadSlot == -1 && !InventorySwapQueue.getInstance().hasPendingSwaps() && this.swapTimer.hasElapsed((400L))) {
                           this.swappedHeadSlot = headInventorySlot;
                           InventorySwapQueue.getInstance().queueSwap(headInventorySlot, InventoryActions.selectedHotbarSlot(), 1);
                           this.swapTimer.reset();
                        }
                     } else {
                        java.util.ArrayList<net.minecraft.util.hit.BlockHitResult> placementHits = this.findPlacementHits(this.climbingContactBox(minecraftClient.player.getBoundingBox().contract((1.0E-5D))));
                        if (!placementHits.isEmpty()) {
                           this.pendingPlacement = (BlockHitResult)placementHits.getFirst();
                           float[] targetAngles = CombatTargeting.anglesBetween(this.pendingPlacement.getPos(), minecraftClient.player.getEyePos());
                           RotationController.getInstance()
                              .requestRotation(
                                 new RotationAngles(RotationAngles.cameraYaw(), targetAngles[1]), new RotationLimits((360.0F), (360.0F), (20.0F), (20.0F)), RotationController.ReturnMode.SMOOTH
                              );
                           this.climbingTimer.reset();
                           this.setSneaking(true);
                        } else {
                           if (this.swappedHeadSlot != -1 && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
                              InventorySwapQueue.getInstance().queueSwap(this.swappedHeadSlot, InventoryActions.selectedHotbarSlot(), 1);
                              this.swappedHeadSlot = -1;
                              this.swapTimer.reset();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (this.pendingPlacement != null && minecraftClient.player != null && minecraftClient.world != null && minecraftClient.interactionManager != null) {
         BlockHitResult hitResult = this.pendingPlacement;
         this.pendingPlacement = null;
         this.swapOffhandForSidebar();
         ((ClientInteractionManagerInvoker)(Object)minecraftClient.interactionManager)
            .invokeStartPrediction(minecraftClient.world, sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, sequence));
         this.jumpAfterPlacement = true;
      }
   };
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (this.mode.isSelected(("FunTime")) && !this.climbingTimer.hasElapsed((100L))) {
         event.setJumping(true);
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null && this.mode.isSelected(("FunTime"))) {
         if (event.isOutgoing()) {
            if (event.getPacket() instanceof KeepAliveC2SPacket keepAlive && this.pendingKeepAlive == null && minecraftClient.getNetworkHandler() != null) {
               this.keepAliveQueuedAtMillis = System.currentTimeMillis();
               this.keepAliveConnection = ((ClientCommonNetworkHandlerAccessor)(Object)minecraftClient.getNetworkHandler()).connection();
               this.pendingKeepAlive = keepAlive;
               event.markCancelled();
            }
         } else {
            if (event.getPacket() instanceof ScreenHandlerSlotUpdateS2CPacket slotUpdate
               && slotUpdate.getSlot() == minecraftClient.player.getInventory().getSelectedSlot() + 36
               && minecraftClient.player.getOffHandStack().getItem() == slotUpdate.getStack().getItem()
               && !this.climbingTimer.hasElapsed((300L))) {
               event.markCancelled();
            }
         }
      }
   };

   public static Spider getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Spider();
      }

      return INSTANCE;
   }

   @Override
   protected void onEnable() {
      this.pendingPlacement = null;
      this.jumpAfterPlacement = false;
      if (this.mode.isSelected(("FunTime")) && minecraftClient.player != null) {
         StatusEffectInstance effect = minecraftClient.player.getStatusEffect(StatusEffects.JUMP_BOOST);
         if (effect == null || effect.getDuration() <= 200) {
            int bootsSlot = InventoryActions.findSlot(
               stack -> {
                  EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
                  return equippable != null
                     && equippable.slot() == EquipmentSlot.FEET
                     && stack.getOrDefault(DataComponentTypes.LORE, LoreComponent.DEFAULT).toString().contains(("Попрыгун"));
               }
            );
            if (bootsSlot != -1) {
               InventorySwapQueue.getInstance().queueScreenSlotSwap(bootsSlot, 8, 1);
            }
         }
      }
   }

   private ArrayList<BlockHitResult> findPlacementHits(Box bounds) {
      ArrayList hits = new ArrayList();

      for (BlockPos position : BlockPos.iterate(bounds)) {
         for (Direction direction : Direction.values()) {
            if (direction.getOffsetY() == 0 && this.isClimbSupport(position.offset(direction))) {
               BlockHitResult hitResult = Flight.getInstance().placementHit(position.toImmutable(), direction);
               if (hitResult != null) {
                  hits.add(hitResult);
               }
            }
         }
      }

      return hits;
   }

   private int headSlot() {
      return minecraftClient.player.getOffHandStack().isOf(Items.PLAYER_HEAD)
         ? minecraftClient.player.playerScreenHandler.slots.getLast().id
         : InventoryActions.findItem(Items.PLAYER_HEAD);
   }

   private void climbWithLadders(PlayerMovementEvent event) {
      int ladderSlot = InventoryActions.findItem(Items.LADDER);
      if (ladderSlot != -1) {
         if (!minecraftClient.player.getMainHandStack().isOf(Items.LADDER)) {
            this.ladderInventorySlot = ladderSlot;
            InventoryActions.swapWithHand(ladderSlot, Hand.MAIN_HAND);
         } else if (minecraftClient.options.jumpKey.isPressed()) {
            Vec3d rayOrigin = new Vec3d(minecraftClient.player.getX(), minecraftClient.player.getY() + 1.0, minecraftClient.player.getZ());
            BlockHitResult hitResult = minecraftClient.world
               .raycast(
                  new RaycastContext(
                     rayOrigin,
                     rayOrigin.add(Vec3d.of(minecraftClient.player.getHorizontalFacing().getVector()).multiply((0.5D))),
                     ShapeType.COLLIDER,
                     FluidHandling.NONE,
                     minecraftClient.player
                  )
               );
            if (hitResult.getType() != Type.BLOCK) {
               return;
            }

            RotationController.getInstance()
               .requestRotation(
                  new RotationAngles(CombatTargeting.anglesBetween(hitResult.getPos(), minecraftClient.player.getEyePos())[0], (-59.0F)),
                  new RotationLimits((60.0F), (60.0F), (10.0F), (10.0F)),
                  RotationController.ReturnMode.NONE
               );
            if (!minecraftClient.player.horizontalCollision && minecraftClient.world.getBlockState(hitResult.getBlockPos().offset(hitResult.getSide())).isAir()) {
               minecraftClient.interactionManager.interactBlock(minecraftClient.player, Hand.MAIN_HAND, hitResult);
               minecraftClient.player.swingHand(Hand.MAIN_HAND);
               this.climbingTimer.reset();
            }

            if (minecraftClient.world
               .getBlockState(
                  BlockPos.ofFloored(
                     minecraftClient.player.getX(),
                     (minecraftClient.player.getBoundingBox().minY + minecraftClient.player.getBoundingBox().maxY) / (2.0D),
                     minecraftClient.player.getZ()
                  )
               )
               .isOf(Blocks.LADDER)) {
               event.setOnGround(true);
               minecraftClient.player.setOnGround(true);
               event.setY((0.6D));
               event.setCancelled(true);
            }
         }
      }
   }

   private void swapOffhandForSidebar() {
      if (minecraftClient.world != null && minecraftClient.getNetworkHandler() != null) {
         ScoreboardObjective sidebar = minecraftClient.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
         if (sidebar != null) {
            String[] titleParts = sidebar.getDisplayName().getString().split(("-"));
            if (titleParts.length >= 2 && !titleParts[1].replaceAll(("\\D"), "").isEmpty() && Integer.parseInt(titleParts[1].replaceAll(("\\D"), "")) > 100) {
               ((ClientCommonNetworkHandlerAccessor)(Object)minecraftClient.getNetworkHandler())
                  .connection()
                  .send(new PlayerActionC2SPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN), null);
            }
         }
      }
   }

   private Box climbingContactBox(Box bounds) {
      return bounds.withMaxY(bounds.minY + 1.0).withMinY(bounds.minY + (0.5D));
   }

   private Spider() {
      this.mode = this.addSetting(new ModeSetting(("Режим"), ("Под какую проверку подгонять залаз."), ("FunTime"), ("FunTime"), ("Ladders")));
   }

   private void setSneaking(boolean pressed) {
      this.sneaking = pressed;
      minecraftClient.options.sneakKey.setPressed(pressed);
   }

   @Override
   protected void onDisable() {
      if (this.ladderInventorySlot != null) {
         InventoryActions.swapWithHand(this.ladderInventorySlot, Hand.MAIN_HAND);
         this.ladderInventorySlot = null;
      }

      if (this.swappedHeadSlot != -1) {
         InventorySwapQueue.getInstance().queueSwap(this.swappedHeadSlot, InventoryActions.selectedHotbarSlot(), 1);
         this.swappedHeadSlot = -1;
      }

      this.pendingPlacement = null;
      this.jumpAfterPlacement = false;
      if (this.sneaking) {
         this.setSneaking(false);
      }

      this.flushKeepAlive();
   }

   private boolean isClimbSupport(BlockPos position) {
      BlockState blockState = minecraftClient.world.getBlockState(position);
      return Flight.getInstance().isNonInteractiveSupportBlock(blockState) && !blockState.getOutlineShape(minecraftClient.world, BlockPos.ORIGIN).isEmpty();
   }

   private void flushKeepAlive() {
      KeepAliveC2SPacket packet = this.pendingKeepAlive;
      ClientConnection connection = this.keepAliveConnection;
      this.pendingKeepAlive = null;
      this.keepAliveConnection = null;
      if (packet != null && connection != null && minecraftClient.getNetworkHandler() != null && ((ClientCommonNetworkHandlerAccessor)(Object)minecraftClient.getNetworkHandler()).connection() == connection) {
         connection.send(packet, null);
      }
   }

   public void applyQueuedJump() {
      if (this.isEnabled() && this.mode.isSelected(("FunTime")) && minecraftClient.player != null && this.jumpAfterPlacement) {
         this.jumpAfterPlacement = false;
         this.swapOffhandForSidebar();
         minecraftClient.player
            .setVelocity(
               minecraftClient.player.getVelocity().x, ((LivingEntityAccessor)(Object)minecraftClient.player).invokeGetJumpPower(), minecraftClient.player.getVelocity().z
            );
         minecraftClient.player.setOnGround(true);
      }
   }

   private int latencyMillis() {
      return minecraftClient.player != null
            && minecraftClient.getNetworkHandler() != null
            && minecraftClient.getNetworkHandler().getPlayerListEntry(minecraftClient.player.getUuid()) != null
         ? minecraftClient.getNetworkHandler().getPlayerListEntry(minecraftClient.player.getUuid()).getLatency()
         : 0;
   }

   public boolean shouldSuppressJump() {
      return this.isEnabled() && this.mode.isSelected(("FunTime")) ? !this.climbingTimer.hasElapsed((100L)) : false;
   }
}
