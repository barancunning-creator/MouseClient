package com.mouseclient.modules.impl.combat;

import com.mouseclient.util.entity.MinecraftAccess;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

public final class ExplosionHelper implements MinecraftAccess {

   private ExplosionHelper() {
   }

   public static boolean canSee(Vec3d from, Vec3d to, BlockPos ignorePos) {
      if (minecraftClient.world == null) {
         return false;
      }

      return BlockView.raycast(from, to, null, (context, blockPos) -> {
         if (ignorePos != null && blockPos.equals(ignorePos)) {
            return null;
         }

         BlockState state = minecraftClient.world.getBlockState(blockPos);
         if (state.isAir()) {
            return null;
         }

         VoxelShape shape = state.getCollisionShape(minecraftClient.world, blockPos);
         if (shape.isEmpty()) {
            return null;
         }

         BlockHitResult hit = shape.raycast(from, to, blockPos);
         return hit != null ? Boolean.FALSE : null;
      }, context -> Boolean.TRUE);
   }

   public static float getExposure(Vec3d source, LivingEntity entity, BlockPos ignorePos) {
      if (minecraftClient.world == null || entity == null) {
         return 0.0F;
      }

      Box box = entity.getBoundingBox();
      double stepX = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
      double stepY = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
      double stepZ = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
      double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
      double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;

      if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
         return 0.0F;
      }

      int missed = 0;
      int total = 0;

      for (double x = 0.0; x <= 1.0; x += stepX) {
         for (double y = 0.0; y <= 1.0; y += stepY) {
            for (double z = 0.0; z <= 1.0; z += stepZ) {
               double targetX = MathHelper.lerp(x, box.minX, box.maxX);
               double targetY = MathHelper.lerp(y, box.minY, box.maxY);
               double targetZ = MathHelper.lerp(z, box.minZ, box.maxZ);
               Vec3d targetPoint = new Vec3d(targetX + offsetX, targetY, targetZ + offsetZ);

               if (canSee(source, targetPoint, ignorePos)) {
                  missed++;
               }
               total++;
            }
         }
      }

      return total == 0 ? 0.0F : (float) missed / (float) total;
   }

   public static float calculateDamage(Vec3d explosion, LivingEntity entity, float power, BlockPos ignorePos) {
      if (minecraftClient.world == null || entity == null) {
         return 0.0F;
      }

      double radius = power * 2.0;
      double dist = Math.sqrt(entity.squaredDistanceTo(explosion));
      double d = dist / radius;
      if (d > 1.0) {
         return 0.0F;
      }

      float exposure = getExposure(explosion, entity, ignorePos);
      if (exposure <= 0.0F) {
         return 0.0F;
      }

      double impact = (1.0 - d) * exposure;
      float rawDamage = (float) ((int) ((impact * impact + impact) / 2.0 * 7.0 * radius + 1.0));

      // Multiplayer Hard difficulty multiplier (1.5x)
      rawDamage = rawDamage * 1.5F;

      // Armor and Armor Toughness reduction (Vanilla DamageUtil formula)
      float armor = (float) entity.getAttributeValue(EntityAttributes.ARMOR);
      float toughness = (float) entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
      float afterArmor = getDamageLeft(rawDamage, armor, toughness);

      // Protection & Blast Protection enchantment reduction
      float afterProtection = getProtectionReduction(entity, afterArmor);

      // Resistance potion effect reduction
      float afterResistance = getResistanceReduction(entity, afterProtection);

      return Math.max(0.0F, afterResistance);
   }

   public static float getDamageLeft(float damage, float armor, float armorToughness) {
      float f = 2.0F + armorToughness / 4.0F;
      float g = MathHelper.clamp(armor - damage / f, armor * 0.2F, 20.0F);
      return damage * (1.0F - g / 25.0F);
   }

   private static final net.minecraft.entity.EquipmentSlot[] ARMOR_SLOTS = new net.minecraft.entity.EquipmentSlot[]{
      net.minecraft.entity.EquipmentSlot.HEAD,
      net.minecraft.entity.EquipmentSlot.CHEST,
      net.minecraft.entity.EquipmentSlot.LEGS,
      net.minecraft.entity.EquipmentSlot.FEET
   };

   public static float getProtectionReduction(LivingEntity entity, float damage) {
      int epf = 0;
      for (net.minecraft.entity.EquipmentSlot slot : ARMOR_SLOTS) {
         ItemStack stack = entity.getEquippedStack(slot);
         if (stack == null || stack.isEmpty()) {
            continue;
         }
         var enchantments = stack.getEnchantments();
         if (enchantments == null) {
            continue;
         }
         for (var entry : enchantments.getEnchantmentEntries()) {
            String id = entry.getKey().getIdAsString();
            int level = entry.getIntValue();
            if (id.contains("blast_protection")) {
               epf += level * 2;
            } else if (id.contains("protection")) {
               epf += level;
            }
         }
      }
      epf = Math.min(20, epf);
      return damage * (1.0F - epf / 25.0F);
   }

   public static float getResistanceReduction(LivingEntity entity, float damage) {
      if (entity.hasStatusEffect(StatusEffects.RESISTANCE)) {
         var effect = entity.getStatusEffect(StatusEffects.RESISTANCE);
         int amplifier = effect != null ? effect.getAmplifier() + 1 : 1;
         float factor = Math.max(0.0F, 1.0F - amplifier * 0.2F);
         return damage * factor;
      }
      return damage;
   }

   public static float score(Vec3d explosion, float power, LivingEntity target, float minDamage, float maxSelfDamage, BlockPos ignorePos) {
      if (minecraftClient.player == null || target == null) {
         return 0.0F;
      }

      float targetDamage = calculateDamage(explosion, target, power, ignorePos);
      if (targetDamage < minDamage) {
         return 0.0F;
      }

      float selfDamage = calculateDamage(explosion, minecraftClient.player, power, ignorePos);
      if (selfDamage > maxSelfDamage) {
         return 0.0F;
      }

      float currentHp = minecraftClient.player.getHealth() + minecraftClient.player.getAbsorptionAmount();
      if (selfDamage >= currentHp - 0.5F) {
         return 0.0F;
      }

      return targetDamage - selfDamage * 0.5F;
   }
}
