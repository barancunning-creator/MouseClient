package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.Render2D;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.ContainerComponentAccessor;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.entity.WorldProjection;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.entity.data.DataTracker.SerializedEntry;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "ShulkerPreview", description = "Превью содержимого шалкера на земле и при наведении.", category = ModuleCategory.RENDER)
public final class ShulkerPreview extends Module {
   private static ShulkerPreview INSTANCE;
   private static final Identifier CONTAINER_TEXTURE = Identifier.of(("mouseclient"), ("textures/container/container.png"));
   public BooleanSetting onGround;
   public BooleanSetting onHover;
   public BooleanSetting bundleOnGround;
   private final HashMap<TypedEntityData<BlockEntityType<?>>, ArrayList<ItemStack>> blockEntityItemCache = new HashMap<>();
   private final HashMap<Integer, ArrayList<ItemStack>> matchedDroppedBundleItems = new HashMap<>();
   private final HashMap<Integer, ArrayList<ItemStack>> trackedBundleItems = new HashMap<>();
   private final HashMap<Integer, ArrayList<ItemStack>> heldBundleItems = new HashMap<>();
   private final HashMap<Integer, ArrayList<ItemStack>> releasedBundleItems = new HashMap<>();
   private final HashMap<Integer, Long> bundleReleaseTimes = new HashMap<>();
   private final HashMap<Integer, ArrayList<ItemStack>> inventoryBundleItems = new HashMap<>();
   private ArrayList<ItemStack> lastRemovedInventoryBundle;
   private long inventoryBundleRemovedAtMillis;
   private final EventListener<HudRenderEvent> hudRenderListener = event -> {
      if (minecraftClient.player != null
         && minecraftClient.world != null
         && minecraftClient.gameRenderer != null
         && (this.onGround.getValue() || this.bundleOnGround.getValue())) {
         for (Entity entity : minecraftClient.world.getEntities()) {
            if (entity instanceof ItemEntity itemEntity && !itemEntity.getStack().isEmpty()) {
               if (this.bundleOnGround.getValue() && itemEntity.getStack().getItem() instanceof BundleItem && !this.droppedBundleItems(itemEntity).isEmpty()) {
                  Vec3d position = WorldProjection.projectNearViewport(
                     itemEntity.getLerpedPos(event.getTickProgress()).add(0.0, (0.25D), 0.0),
                     minecraftClient.getWindow().getScaledWidth(),
                     minecraftClient.getWindow().getScaledHeight()
                  );
                  if (position != null) {
                     this.drawBundleGrid(
                        event.getDrawContext(),
                        (float)position.x,
                        (float)position.y
                           - Tags.getInstance()
                              .itemTagVerticalOffset((float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(itemEntity.getLerpedPos(event.getTickProgress())))
                           - 2.0F,
                        Math.max(
                              (0.5F),
                              (1.1F)
                                 - (float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(itemEntity.getLerpedPos(event.getTickProgress()))
                                    * (0.006F)
                           )
                           / minecraftClient.getWindow().getScaleFactor(),
                        this.droppedBundleItems(itemEntity)
                     );
                  }

                  Render2D.nextLayer(event.getDrawContext());
               } else if (this.onGround.getValue() && Block.getBlockFromItem(itemEntity.getStack().getItem()) instanceof ShulkerBoxBlock shulkerBlock) {
                  Vec3d shulkerScreenPosition = WorldProjection.projectNearViewport(
                     itemEntity.getLerpedPos(event.getTickProgress()).add(0.0, (0.25D), 0.0),
                     minecraftClient.getWindow().getScaledWidth(),
                     minecraftClient.getWindow().getScaledHeight()
                  );
                  if (shulkerScreenPosition != null) {
                     this.drawShulkerGrid(
                        event.getDrawContext(),
                        (float)shulkerScreenPosition.x,
                        (float)shulkerScreenPosition.y
                           - Tags.getInstance()
                              .itemTagVerticalOffset((float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(itemEntity.getLerpedPos(event.getTickProgress())))
                           - 2.0F,
                        Math.max(
                              (0.5F),
                              (1.1F)
                                 - (float)minecraftClient.gameRenderer.getCamera().getCameraPos().distanceTo(itemEntity.getLerpedPos(event.getTickProgress()))
                                    * (0.006F)
                           )
                           / minecraftClient.getWindow().getScaleFactor(),
                        this.shulkerItems(itemEntity.getStack()),
                        this.shulkerColor(shulkerBlock)
                     );
                  }

                  Render2D.nextLayer(event.getDrawContext());
               }
            }
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         for (int var2 = 0; var2 < 36; var2++) {
            ItemStack stack = minecraftClient.player.getInventory().getStack(var2);
            boolean hasBundleItems = !stack.isEmpty()
               && stack.getItem() instanceof BundleItem
               && stack.get(DataComponentTypes.BUNDLE_CONTENTS) != null
               && !stack.get(DataComponentTypes.BUNDLE_CONTENTS).isEmpty();
            if (!hasBundleItems) {
               if (this.inventoryBundleItems.containsKey(var2)) {
                  this.lastRemovedInventoryBundle = this.inventoryBundleItems.remove(var2);
                  this.inventoryBundleRemovedAtMillis = System.currentTimeMillis();
               }
            } else {
               java.util.ArrayList<net.minecraft.item.ItemStack> cachedItems = this.inventoryBundleItems.get(var2);
               if (cachedItems == null || cachedItems.size() != stack.get(DataComponentTypes.BUNDLE_CONTENTS).size()) {
                  ArrayList copiedItems = new ArrayList();

                  for (ItemStack bundleItem : stack.get(DataComponentTypes.BUNDLE_CONTENTS).iterateCopy()) {
                     copiedItems.add(bundleItem);
                  }

                  this.inventoryBundleItems.put(var2, copiedItems);
               }
            }
         }
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isIncoming()) {
         if (event.getPacket() instanceof EntityEquipmentUpdateS2CPacket equipmentPacket) {
            for (Pair equipmentEntry : equipmentPacket.getEquipmentList()) {
               if (equipmentEntry.getFirst() == EquipmentSlot.MAINHAND || equipmentEntry.getFirst() == EquipmentSlot.OFFHAND) {
                  int entityHandKey = equipmentPacket.getEntityId() * 2 + (equipmentEntry.getFirst() == EquipmentSlot.OFFHAND ? 1 : 0);
                  ItemStack stack = (ItemStack)equipmentEntry.getSecond();
                  boolean hasBundleContents = !stack.isEmpty()
                     && stack.getItem() instanceof BundleItem
                     && stack.get(DataComponentTypes.BUNDLE_CONTENTS) != null
                     && !stack.get(DataComponentTypes.BUNDLE_CONTENTS).isEmpty();
                  if (hasBundleContents) {
                     if (this.heldBundleItems.size() >= 256) {
                        this.heldBundleItems.clear();
                     }

                     ArrayList heldItems = new ArrayList();

                     for (ItemStack heldItem : stack.get(DataComponentTypes.BUNDLE_CONTENTS).iterateCopy()) {
                        heldItems.add(heldItem);
                     }

                     this.heldBundleItems.put(entityHandKey, heldItems);
                  } else if (this.heldBundleItems.containsKey(entityHandKey)) {
                     if (this.releasedBundleItems.size() >= 256) {
                        this.releasedBundleItems.clear();
                        this.bundleReleaseTimes.clear();
                     }

                     this.releasedBundleItems.put(entityHandKey, this.heldBundleItems.remove(entityHandKey));
                     this.bundleReleaseTimes.put(entityHandKey, System.currentTimeMillis());
                  }
               }
            }
         } else if (event.getPacket() instanceof EntityTrackerUpdateS2CPacket trackerPacket) {
            for (SerializedEntry trackedEntry : trackerPacket.trackedValues()) {
               if (trackedEntry.value() instanceof ItemStack bundleStack && !bundleStack.isEmpty() && bundleStack.getItem() instanceof BundleItem) {
                  if (this.matchedDroppedBundleItems.size() >= 256) {
                     this.matchedDroppedBundleItems.clear();
                  }

                  if (this.lastRemovedInventoryBundle != null && System.currentTimeMillis() - this.inventoryBundleRemovedAtMillis < (3000L)) {
                     this.matchedDroppedBundleItems.put(trackerPacket.id(), this.lastRemovedInventoryBundle);
                  } else {
                     if (this.trackedBundleItems.size() >= 256) {
                        this.trackedBundleItems.clear();
                     }

                     if (!this.trackedBundleItems.containsKey(trackerPacket.id())
                        && bundleStack.get(DataComponentTypes.BUNDLE_CONTENTS) != null
                        && !bundleStack.get(DataComponentTypes.BUNDLE_CONTENTS).isEmpty()) {
                        ArrayList trackedItems = new ArrayList();

                        for (ItemStack trackedItem : bundleStack.get(DataComponentTypes.BUNDLE_CONTENTS).iterateCopy()) {
                           trackedItems.add(trackedItem);
                        }

                        this.trackedBundleItems.put(trackerPacket.id(), trackedItems);
                     }
                  }
               }
            }
         }
      }
   };

   private int shulkerColor(ShulkerBoxBlock block) {
      return block.getColor() != null ? (-16777216) | block.getColor().getEntityColor() : (-6660960);
   }

   private ArrayList<ItemStack> droppedBundleItems(ItemEntity itemEntity) {
      java.util.ArrayList<net.minecraft.item.ItemStack> matchedItems = this.matchedDroppedBundleItems.get(itemEntity.getId());
      if (matchedItems != null) {
         return matchedItems;
      }

      int nearestHandKey = -1;
      double nearestDistanceSquared = (64.0D);

      for (Integer releasedHandKey : this.releasedBundleItems.keySet()) {
         if (System.currentTimeMillis() - this.bundleReleaseTimes.getOrDefault(releasedHandKey, 0L) <= (5000L)) {
            Entity entity = minecraftClient.world.getEntityById(releasedHandKey / 2);
            if (entity != null && !(entity.squaredDistanceTo(itemEntity) >= nearestDistanceSquared)) {
               nearestDistanceSquared = entity.squaredDistanceTo(itemEntity);
               nearestHandKey = releasedHandKey;
            }
         }
      }

      if (nearestHandKey != -1) {
         java.util.ArrayList<net.minecraft.item.ItemStack> releasedItems = this.releasedBundleItems.remove(nearestHandKey);
         this.bundleReleaseTimes.remove(nearestHandKey);
         this.matchedDroppedBundleItems.put(itemEntity.getId(), releasedItems);
         return releasedItems;
      }

      java.util.ArrayList<net.minecraft.item.ItemStack> trackedItems = this.trackedBundleItems.get(itemEntity.getId());
      if (trackedItems != null) {
         return trackedItems;
      }

      ArrayList componentItems = new ArrayList();
      if (itemEntity.getStack().get(DataComponentTypes.BUNDLE_CONTENTS) != null) {
         for (ItemStack stack : itemEntity.getStack().get(DataComponentTypes.BUNDLE_CONTENTS).iterate()) {
            componentItems.add(stack);
         }
      }

      return componentItems;
   }

   private ArrayList<ItemStack> loadShulkerItems(ItemStack stack) {
      DefaultedList<ItemStack> contents = DefaultedList.ofSize(27, ItemStack.EMPTY);
      if (stack.get(DataComponentTypes.CONTAINER) != null) {
         DefaultedList componentContents = ((ContainerComponentAccessor)(Object)stack.get(DataComponentTypes.CONTAINER)).getItems();

         for (int var4 = 0; var4 < componentContents.size() && var4 < 27; var4++) {
            contents.set(var4, (ItemStack)componentContents.get(var4));
         }
      }

      if (contents.stream().allMatch(candidateStack -> candidateStack.isEmpty()) && stack.<TypedEntityData<BlockEntityType<?>>>get(DataComponentTypes.BLOCK_ENTITY_DATA) != null) {
         java.util.ArrayList<net.minecraft.item.ItemStack> cachedItems = this.blockEntityItemCache.get(stack.get(DataComponentTypes.BLOCK_ENTITY_DATA));
         if (cachedItems != null) {
            return cachedItems;
         }

         ShulkerBoxBlockEntity shulkerBlockEntity = new ShulkerBoxBlockEntity(BlockPos.ORIGIN, Blocks.SHULKER_BOX.getDefaultState());
         if (stack.get(DataComponentTypes.BLOCK_ENTITY_DATA).applyToBlockEntity(shulkerBlockEntity, minecraftClient.world.getRegistryManager())) {
            for (int var5 = 0; var5 < 27; var5++) {
               contents.set(var5, shulkerBlockEntity.getStack(var5));
            }
         }

         if (this.blockEntityItemCache.size() >= 256) {
            this.blockEntityItemCache.clear();
         }

         this.blockEntityItemCache.put(stack.get(DataComponentTypes.BLOCK_ENTITY_DATA), new ArrayList<>(contents));
      }

      return new ArrayList<>(contents);
   }

   private void drawShulkerGrid(DrawContext context, float x, float y, float scale, ArrayList<ItemStack> items, int color) {
      context.getMatrices().pushMatrix();
      context.getMatrices().translate(x, y);
      context.getMatrices().scale(scale, scale);
      context.drawTexture(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, -88, -73, 0.0F, 0.0F, 176, 67, 176, 67, color);

      for (int var7 = 0; var7 < Math.min(items.size(), 27); var7++) {
         if (!((ItemStack)items.get(var7)).isEmpty()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((-72.0F) + var7 % 9 * (18.0F), (-57.0F) + var7 / 9 * (18.0F));
            context.getMatrices().scale((0.85F), (0.85F));
            context.drawItem((ItemStack)items.get(var7), -8, -8);
            context.drawStackOverlay(minecraftClient.textRenderer, (ItemStack)items.get(var7), -8, -8);
            context.getMatrices().popMatrix();
         }
      }

      context.getMatrices().popMatrix();
   }

   public static ShulkerPreview getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ShulkerPreview();
      }

      return INSTANCE;
   }

   private ShulkerPreview() {
      this.onGround = this.addSetting(new BooleanSetting(("На земле"), ("Показывать содержимое шалкера, лежащего на земле."), true));
      this.onHover = this.addSetting(new BooleanSetting(("При наведении"), ("Показывать содержимое при наведении на шалкер в инвентаре."), true));
      this.bundleOnGround = this.addSetting(new BooleanSetting(("Мешок на земле"), ("Показывать содержимое мешка, лежащего на земле."), true));
   }

   public void renderHoveredShulker(DrawContext context, ItemStack stack, int mouseX, int mouseY) {
      if (this.isEnabled() && this.onHover.getValue() && Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock shulkerBlock) {
         java.util.ArrayList<net.minecraft.item.ItemStack> items = this.shulkerItems(stack);
         if (items.stream().anyMatch(candidateStack -> !candidateStack.isEmpty())) {
            this.drawShulkerGrid(context, mouseX, mouseY - (6.0F), (0.75F), items, this.shulkerColor(shulkerBlock));
         }
      }
   }

   private ArrayList<ItemStack> shulkerItems(ItemStack stack) {
      return this.loadShulkerItems(stack);
   }

   private void drawBundleGrid(DrawContext context, float x, float y, float scale, ArrayList<ItemStack> items) {
      int columns = Math.max(2, (int)Math.ceil(Math.sqrt(items.size() + 1.0)));
      int rows = (int)Math.ceil((double)items.size() / columns);
      int width = columns * 18 + 8;
      int height = rows * 18 + 8;
      context.getMatrices().pushMatrix();
      context.getMatrices().translate(x, y);
      context.getMatrices().scale(scale, scale);
      context.fill(-width / 2 - 1, -height - 1, width / 2 + 1, 1, (1347420415));
      context.fill(-width / 2, -height, width / 2, 0, (-267386864));

      for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
         if (!((ItemStack)items.get(itemIndex)).isEmpty()) {
            int itemX = -width / 2 + 4 + itemIndex % columns * 18;
            int itemY = -height + 4 + itemIndex / columns * 18;
            context.fill(itemX, itemY, itemX + 18, itemY + 18, (1720421259));
            context.drawItem((ItemStack)items.get(itemIndex), itemX + 1, itemY + 1);
            context.drawStackOverlay(minecraftClient.textRenderer, (ItemStack)items.get(itemIndex), itemX + 1, itemY + 1);
         }
      }

      context.getMatrices().popMatrix();
   }
}
