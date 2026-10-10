package com.mouseclient.render;

import com.mouseclient.modules.impl.misc.Globals;
import com.mouseclient.util.time.CosmeticPlacement;
import com.mouseclient.util.time.CosmeticCategory;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderState.LayerRenderState;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import org.joml.Vector3fc;

public final class ItemCosmeticsRenderer {
   private static final CosmeticCategory[] ITEM_CATEGORIES = new CosmeticCategory[]{CosmeticCategory.SWORD, CosmeticCategory.MACE, CosmeticCategory.SHIELD};
   private static final ItemCosmeticsRenderer.ItemCosmeticSpecialRenderer SPECIAL_RENDERER = new ItemCosmeticsRenderer.ItemCosmeticSpecialRenderer();

   public static boolean applyCosmetic(ItemRenderState state, ItemStack stack, ItemDisplayContext displayContext, LivingEntity entity) {
      CosmeticEntry entry = findCosmetic(stack, entity);
      if (entry == null) {
         return false;
      }

      state.markAnimated();
      JsonCosmeticModel cosmeticModel = JsonCosmeticModel.getOrRequest(entry);
      if (cosmeticModel == null) {
         return false;
      }

      boolean heldShield = stack.isOf(Items.SHIELD)
         && displayContext != ItemDisplayContext.GUI
         && displayContext != ItemDisplayContext.GROUND
         && displayContext != ItemDisplayContext.FIXED
         && displayContext != ItemDisplayContext.ON_SHELF;
      state.addModelKey(entry);
      LayerRenderState itemLayer = state.newLayer();
      itemLayer.setUseLight(true);
      itemLayer.setVertices(cosmeticModel::boundingVertices);
      itemLayer.setTransform(heldShield ? Transformation.IDENTITY : cosmeticModel.displayTransform(displayContext));
      itemLayer.setSpecialModel(SPECIAL_RENDERER, new ItemCosmeticsRenderer.ItemCosmeticRenderData(cosmeticModel, heldShield));
      return true;
   }

   private static boolean matchesTarget(CosmeticPlacement placement, ItemStack stack) {
      for (String targetKind : placement.targets()) {
         boolean matches = switch (targetKind) {
            case ("swords") -> stack.isIn(ItemTags.SWORDS);
            case ("axes") -> stack.isIn(ItemTags.AXES);
            case ("pickaxes") -> stack.isIn(ItemTags.PICKAXES);
            case ("shovels") -> stack.isIn(ItemTags.SHOVELS);
            case ("hoes") -> stack.isIn(ItemTags.HOES);
            case ("mace") -> stack.isOf(Items.MACE);
            case ("shield") -> stack.isOf(Items.SHIELD);
            case ("trident") -> stack.isOf(Items.TRIDENT);
            default -> false;
         };
         if (matches) {
            return true;
         }
      }

      return false;
   }

   public static float animationTime() {
      MinecraftClient client = MinecraftClient.getInstance();
      return client.player == null ? 0.0F : (client.player.age + client.getRenderTickCounter().getTickProgress(false)) / (20.0F);
   }

   private ItemCosmeticsRenderer() {
   }

   public static CosmeticEntry findCosmetic(ItemStack stack, LivingEntity entity) {
      if (!stack.isEmpty() && (stack.isIn(ItemTags.SWORDS) || stack.isOf(Items.MACE) || stack.isOf(Items.SHIELD))) {
         MinecraftClient client = MinecraftClient.getInstance();
         boolean localPlayerIdentity = entity == null || entity == client.player;
         if (localPlayerIdentity || entity instanceof AbstractClientPlayerEntity && Globals.getInstance().hasSyncSnapshot()) {
            for (CosmeticCategory category : ITEM_CATEGORIES) {
               CosmeticEntry entry = localPlayerIdentity ? EquippedCosmetics.equippedEntry(category) : Globals.getInstance().syncedCosmetic(((AbstractClientPlayerEntity)entity).getGameProfile().name(), category);
               if (entry != null && entry.placement() != null && matchesTarget(entry.placement(), stack)) {
                  return entry;
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private record ItemCosmeticRenderData(JsonCosmeticModel model, boolean heldShield) {
   }

   private static final class ItemCosmeticSpecialRenderer implements SpecialModelRenderer<ItemCosmeticsRenderer.ItemCosmeticRenderData> {

      @Override
      public ItemCosmeticsRenderer.ItemCosmeticRenderData getData(ItemStack stack) {
         return null;
      }

      @Override
      public void render(
         ItemCosmeticsRenderer.ItemCosmeticRenderData data, ItemDisplayContext displayContext, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, boolean glint, int outlineColor
      ) {
         if (data != null) {
            if (!data.heldShield()) {
               data.model().submit(matrices, queue, light, ItemCosmeticsRenderer.animationTime(), 0.0F);
            } else {
               matrices.push();
               matrices.translate((0.5F), (0.5F), (0.5F));
               data.model().applyHeldShieldTransform(matrices, displayContext);
               data.model().submit(matrices, queue, light, ItemCosmeticsRenderer.animationTime(), 0.0F);
               matrices.pop();
            }
         }
      }

      @Override
      public void collectVertices(Consumer<Vector3fc> consumer) {
      }
   }
}
