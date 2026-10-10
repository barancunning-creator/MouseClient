package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.modules.impl.combat.AutoSwap;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.FilteredTextSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.player.ServerItemDefinition;
import com.mouseclient.modules.impl.player.AttributeModifierSpec;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.player.ServerItemCatalog;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.entity.CombatTargeting;
import java.util.HashMap;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.AttributeModifiersComponent.Entry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.MathHelper;

@ModuleInfo(name = "AutoTotem", description = "Автоматически берёт тотем в левую руку по триггерам.", category = ModuleCategory.COMBAT)
public final class AutoTotem extends Module {
   private static AutoTotem INSTANCE;
   public MultiSelectSetting triggers;
   public DecimalSetting healthThreshold;
   public DecimalSetting fallDistance;
   public DecimalSetting tntRadius;
   public DecimalSetting tridentRadius;
   public DecimalSetting maceRadius;
   public DecimalSetting crystalRadius;
   public BooleanSetting preserveEnchantedTotem;
   public BooleanSetting useEnchantedWhilePreserving;
   public FilteredTextSetting totemName;
   public BooleanSetting showTotemPop;
   private ItemStack savedOffhand = ItemStack.EMPTY;
   private boolean usingEmergencyTotem;
   private long dangerUntilMillis;
   private int savedSlot = -1;
   private long lastSwapAtMillis;
   private final HashMap<Integer, Double> fallPeakHeights = new HashMap<>();
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null) {
         if (minecraftClient.world != this.trackedWorld) {
            this.resetSwapState();
            this.dangerUntilMillis = 0L;
            this.fallPeakHeights.clear();
            this.trackedWorld = minecraftClient.world;
         }

         this.updateFallPeakHeights();
         if (minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
            if (System.currentTimeMillis() - this.lastSwapAtMillis >= (250L)) {
               if (this.usingEmergencyTotem && minecraftClient.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING && !this.canRestoreIntoEmptyOffhand()) {
                  this.resetSwapState();
               }

               if (this.updateDanger()) {
                  if (!this.hasSuitableOffhandTotem() && this.findTotemSlot() != -1) {
                     this.equipTotem();
                  }
               } else if (this.usingEmergencyTotem && System.currentTimeMillis() >= this.dangerUntilMillis) {
                  if (this.findRestoreSlot() != -1) {
                     this.restoreOffhand();
                  }
               }
            }
         }
      }
   };
   private ClientWorld trackedWorld;

   private boolean tntNearby() {
      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof TntEntity
            && entity.squaredDistanceTo(minecraftClient.player) < this.tntRadius.getValue() * this.tntRadius.getValue()) {
            return true;
         }
      }

      return false;
   }

   public static AutoTotem getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoTotem();
      }

      return INSTANCE;
   }

   private boolean tridentNearby() {
      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof TridentEntity
            && entity.squaredDistanceTo(minecraftClient.player) < this.tridentRadius.getValue() * this.tridentRadius.getValue()) {
            return true;
         }
      }

      return false;
   }

   public boolean matchesConfiguredTotem(ItemStack stack) {
      ServerItemCatalog.ensureInitialized();
      boolean catalogNameMatched = false;

      for (ServerItemDefinition definition : ServerItemCatalog.items()) {
         if (definition.item() == Items.TOTEM_OF_UNDYING && definition.name().toLowerCase().contains(this.totemName.getText().toLowerCase())) {
            catalogNameMatched = true;
            if (ServerItemDefinition.CHECKER.test(stack, definition)) {
               return true;
            }
         }
      }

      return !catalogNameMatched && stack.getName().getString().toLowerCase().contains(this.totemName.getText().toLowerCase());
   }

   public boolean isProtecting() {
      return this.isEnabled() && this.usingEmergencyTotem;
   }

   private boolean hasSuitableOffhandTotem() {
      if (minecraftClient.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
         return false;
      } else if (this.shouldUseNamedTotem()
         && !this.matchesConfiguredTotem(minecraftClient.player.getOffHandStack())
         && this.findTotemSlot() != -1
         && this.matchesConfiguredTotem(minecraftClient.player.getInventory().getStack(this.findTotemSlot()))) {
         return false;
      } else {
         return (this.preserveEnchantedTotem.getValue() || this.useEnchantedWhilePreserving.getValue()) && this.isProtectedTotem(minecraftClient.player.getOffHandStack())
            ? this.findTotemSlot() == -1 || this.isProtectedTotem(minecraftClient.player.getInventory().getStack(this.findTotemSlot()))
            : true;
      }
   }

   private AutoTotem() {
      this.triggers = this.addSetting(new MultiSelectSetting(("Триггеры"), ("Когда автоматически брать тотем в левую руку."), new String[]{("Здоровье"), ("Падение"), ("Динамит"), ("Трезубец"), ("Булава"), ("Кристалл")}, ("Здоровье")));
      this.healthThreshold = this.addSetting(new DecimalSetting(("Здоровье"), ("При каком здоровье брать тотем."), (8.0D), 1.0, (19.0D), (0.5D)));
      this.fallDistance = this.addSetting(new DecimalSetting(("Дистанция падения"), ("С какой высоты падения брать тотем."), (6.0D), (3.0D), (40.0D), (0.5D)));
      this.tntRadius = this.addSetting(new DecimalSetting(("Радиус динамита"), ("На каком расстоянии поджённый динамит берёт тотем."), (8.0D), 1.0, (16.0D), (0.5D)));
      this.tridentRadius = this.addSetting(new DecimalSetting(("Радиус трезубца"), ("На каком расстоянии летящий трезубец берёт тотем."), (8.0D), 1.0, (16.0D), (0.5D)));
      this.crystalRadius = this.addSetting(new DecimalSetting(("Радиус кристалла"), ("На каком расстоянии кристалл края берёт тотем."), (6.0D), 1.0, (16.0D), (0.5D)));
      this.maceRadius = this.addSetting(new DecimalSetting(("Радиус булавы"), ("На каком расстоянии по горизонтали игрок с булавой берёт тотем."), (7.0D), 1.0, (16.0D), (0.5D)));
      this.preserveEnchantedTotem = this.addSetting(new BooleanSetting(("Сохранять зачарованный тотем"), ("Берёт обычные тотемы, зачарованный только если обычных нет."), true));
      this.useEnchantedWhilePreserving = this.addSetting(new BooleanSetting(("Брать зачарованный с сохранением"), ("Дон-тотем в руке сохраняется, на замену берётся зачарованный тотем с выбранным названием для перебива."), false));
      this.totemName = this.addSetting(new FilteredTextSetting(("Название тотема"), ("Часть названия зачарованного тотема на замену."), ("Тирана"), 32));
      this.showTotemPop = this.addSetting(new BooleanSetting(("Отображать снесение тотема"), ("Рисует призрака игрока на месте лопнувшего тотема."), false));
      this.totemName.setVisibility(() -> this.useEnchantedWhilePreserving.getValue());
      this.healthThreshold.setVisibility(() -> this.triggers.matchesOption(("Здоровье")));
      this.fallDistance.setVisibility(() -> this.triggers.matchesOption(("Падение")));
      this.tntRadius.setVisibility(() -> this.triggers.matchesOption(("Динамит")));
      this.tridentRadius.setVisibility(() -> this.triggers.matchesOption(("Трезубец")));
      this.maceRadius.setVisibility(() -> this.triggers.matchesOption(("Булава")));
      this.crystalRadius.setVisibility(() -> this.triggers.matchesOption(("Кристалл")));
   }

   private boolean isProtectedTotem(ItemStack stack) {
      return this.useEnchantedWhilePreserving.getValue() && !this.totemName.getText().isEmpty() && !CombatTargeting.isDonationTotem(stack) && this.matchesConfiguredTotem(stack)
         ? false
         : CombatTargeting.isDonationTotem(stack) || stack.hasEnchantments();
   }

   private boolean hasDangerousFall() {
      return minecraftClient.player.fallDistance >= this.fallDistance.getValue();
   }

   private float additiveAttackDamage(ItemStack stack) {
      if (stack.isEmpty()) {
         return 0.0F;
      }

      float damage = 0.0F;
      ServerItemCatalog.ensureInitialized();

      for (ServerItemDefinition definition : ServerItemCatalog.items()) {
         if (!definition.attributes().isEmpty() && ServerItemDefinition.CHECKER.test(stack, definition)) {
            for (AttributeModifierSpec spec : definition.attributes()) {
               if (spec.attribute().matches(EntityAttributes.ATTACK_DAMAGE) && spec.operation() == Operation.ADD_VALUE) {
                  damage += (float)spec.amount();
               }
            }

            return damage;
         }
      }

      for (Entry modifier : stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT).modifiers()) {
         if (modifier.attribute().matches(EntityAttributes.ATTACK_DAMAGE) && modifier.modifier().operation() == Operation.ADD_VALUE) {
            damage += (float)modifier.modifier().value();
         }
      }

      return damage;
   }

   private boolean crystalNearby() {
      for (Entity entity : minecraftClient.world.getEntities()) {
         if (entity instanceof EndCrystalEntity
            && entity.squaredDistanceTo(minecraftClient.player) < this.crystalRadius.getValue() * this.crystalRadius.getValue()) {
            return true;
         }
      }

      return false;
   }

   private float estimatedAttackDamage(AbstractClientPlayerEntity player) {
      float baseDamage = 1.0F;

      for (Entry modifier : player.getMainHandStack().getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT).modifiers()) {
         if (modifier.attribute().matches(EntityAttributes.ATTACK_DAMAGE)
            && modifier.slot().matches(EquipmentSlot.MAINHAND)
            && modifier.modifier().operation() == Operation.ADD_VALUE) {
            baseDamage += (float)modifier.modifier().value();
         }
      }

      return Math.max(baseDamage, (6.0F)) + this.additiveAttackDamage(player.getOffHandStack());
   }

   private int findSavedOffhandSlot() {
      if (this.savedOffhand.isEmpty()) {
         return -1;
      }

      if (this.savedSlot != -1 && ItemStack.areItemsAndComponentsEqual(minecraftClient.player.getInventory().getStack(this.savedSlot), this.savedOffhand)) {
         return this.savedSlot;
      }

      int exactSlot = InventoryActions.findSlot(stack -> ItemStack.areItemsAndComponentsEqual(stack, this.savedOffhand), 0, 35);
      return exactSlot != -1 ? exactSlot : InventoryActions.findSlot(candidate -> candidate.getItem() == this.savedOffhand.getItem(), 0, 35);
   }

   private float estimateMaceDamageToPlayer(AbstractClientPlayerEntity player, float fallDistance) {
      ItemStack stack = player.getMainHandStack();
      int densityLevel = EnchantmentHelper.getLevel(
         minecraftClient.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.DENSITY), stack
      );
      int breachLevel = EnchantmentHelper.getLevel(
         minecraftClient.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.BREACH), stack
      );
      float fallBonus = fallDistance <= (3.0F)
         ? (4.0F) * fallDistance
         : (fallDistance <= (8.0F) ? (12.0F) + 2.0F * (fallDistance - (3.0F)) : (22.0F) + fallDistance - (8.0F));
      float rawDamage = (this.estimatedAttackDamage(player) + fallBonus + (0.5F) * densityLevel * fallDistance) * (1.5F);
      float armor = minecraftClient.player.getArmor();
      float toughness = (float)minecraftClient.player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
      float armorReduction = MathHelper.clamp(
         MathHelper.clamp(armor - rawDamage / (2.0F + toughness / (4.0F)), armor * (0.2F), (20.0F)) / (25.0F) - (0.15F) * breachLevel, 0.0F, 1.0F
      );
      float damageAfterArmor = rawDamage * (1.0F - armorReduction);
      StatusEffectInstance effect = minecraftClient.player.getStatusEffect(StatusEffects.RESISTANCE);
      if (effect != null) {
         damageAfterArmor *= Math.max(0.0F, 1.0F - (effect.getAmplifier() + 1) * (0.2F));
      }

      int protectionLevel = 0;

      for (EquipmentSlot armorSlot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         protectionLevel += EnchantmentHelper.getLevel(
            minecraftClient.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),
            minecraftClient.player.getEquippedStack(armorSlot)
         );
      }

      return damageAfterArmor * (1.0F - Math.min(protectionLevel, 20) / (25.0F));
   }

   private void resetSwapState() {
      this.usingEmergencyTotem = false;
      this.savedOffhand = ItemStack.EMPTY;
      this.savedSlot = -1;
   }

   private boolean shouldUseNamedTotem() {
      return this.useEnchantedWhilePreserving.getValue()
         && !this.totemName.getText().isEmpty()
         && this.triggers.matchesOption(("Здоровье"))
         && minecraftClient.player.getHealth() + minecraftClient.player.getAbsorptionAmount() <= this.healthThreshold.getValue();
   }

   private void equipTotem() {
      if (minecraftClient.player != null
         && minecraftClient.interactionManager != null
         && minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
         if ((this.updateDanger() || System.currentTimeMillis() < this.dangerUntilMillis) && !this.hasSuitableOffhandTotem()) {
            int totemSlot = this.findTotemSlot();
            if (totemSlot != -1) {
               if (!this.usingEmergencyTotem) {
                  this.savedOffhand = minecraftClient.player.getOffHandStack().copy();
                  this.savedSlot = totemSlot;
               }

               this.usingEmergencyTotem = true;
               this.lastSwapAtMillis = System.currentTimeMillis();
               InventorySwapQueue.getInstance().queueTrackedSwap(totemSlot, 40, 1);
            }
         }
      }
   }

   private int findTotemSlot() {
      if (this.shouldUseNamedTotem()) {
         int namedTotemSlot = InventoryActions.findSlot(
            stack -> stack.getItem() == Items.TOTEM_OF_UNDYING
               && stack.hasEnchantments()
               && !CombatTargeting.isDonationTotem(stack)
               && this.matchesConfiguredTotem(stack)
               && !minecraftClient.player.getItemCooldownManager().isCoolingDown(stack),
            0,
            35
         );
         if (namedTotemSlot != -1) {
            return namedTotemSlot;
         }
      }

      if (!this.preserveEnchantedTotem.getValue() && !this.useEnchantedWhilePreserving.getValue()) {
         return InventoryActions.findSlot(
            anyTotem -> anyTotem.getItem() == Items.TOTEM_OF_UNDYING
               && !AutoSwap.getInstance().isSpecialTalisman(anyTotem)
               && !minecraftClient.player.getItemCooldownManager().isCoolingDown(anyTotem),
            0,
            35
         );
      }

      int unprotectedSlot = InventoryActions.findSlot(
         unprotectedTotem -> unprotectedTotem.getItem() == Items.TOTEM_OF_UNDYING
            && !this.isProtectedTotem(unprotectedTotem)
            && !AutoSwap.getInstance().isSpecialTalisman(unprotectedTotem)
            && !minecraftClient.player.getItemCooldownManager().isCoolingDown(unprotectedTotem),
         0,
         35
      );
      return unprotectedSlot != -1
         ? unprotectedSlot
         : InventoryActions.findSlot(
            fallbackTotem -> fallbackTotem.getItem() == Items.TOTEM_OF_UNDYING
               && !AutoSwap.getInstance().isSpecialTalisman(fallbackTotem)
               && !minecraftClient.player.getItemCooldownManager().isCoolingDown(fallbackTotem),
            0,
            35
         );
   }

   private boolean updateDanger() {
      if ((
            !this.triggers.matchesOption(("Здоровье"))
               || !(minecraftClient.player.getHealth() + minecraftClient.player.getAbsorptionAmount() <= this.healthThreshold.getValue())
         )
         && (!this.triggers.matchesOption(("Падение")) || !this.hasDangerousFall())
         && (!this.triggers.matchesOption(("Динамит")) || !this.tntNearby())
         && (!this.triggers.matchesOption(("Трезубец")) || !this.tridentNearby())
         && (!this.triggers.matchesOption(("Булава")) || !this.maceThreatNearby())
         && (!this.triggers.matchesOption(("Кристалл")) || !this.crystalNearby())) {
         return false;
      }

      this.dangerUntilMillis = System.currentTimeMillis() + (100L);
      return true;
   }

   private boolean canRestoreIntoEmptyOffhand() {
      return minecraftClient.player.getOffHandStack().isEmpty() && !this.savedOffhand.isEmpty() && this.findSavedOffhandSlot() != -1;
   }

   private void updateFallPeakHeights() {
      this.fallPeakHeights.keySet().removeIf(entityId -> minecraftClient.world.getEntityById(entityId) == null);

      for (AbstractClientPlayerEntity player : minecraftClient.world.getPlayers()) {
         if (player != minecraftClient.player) {
            if (!player.isOnGround() && !(player.getY() >= player.lastY) && !player.isGliding() && !player.isTouchingWater() && !player.isClimbing()) {
               this.fallPeakHeights.merge(player.getId(), player.getY(), Math::max);
            } else {
               this.fallPeakHeights.put(player.getId(), player.getY());
            }
         }
      }
   }

   private int findRestoreSlot() {
      if (!this.savedOffhand.isEmpty()) {
         int savedItemSlot = this.findSavedOffhandSlot();
         return savedItemSlot != -1 ? savedItemSlot : InventoryActions.findEmptySlot(0, 35);
      } else {
         return this.savedSlot != -1 && minecraftClient.player.getInventory().getStack(this.savedSlot).isEmpty()
            ? this.savedSlot
            : InventoryActions.findEmptySlot(0, 35);
      }
   }

   private void restoreOffhand() {
      if (minecraftClient.player != null
         && minecraftClient.interactionManager != null
         && minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
         if (this.usingEmergencyTotem && !this.updateDanger() && System.currentTimeMillis() >= this.dangerUntilMillis) {
            if (minecraftClient.player.getOffHandStack().isEmpty()) {
               int savedItemSlot = this.findSavedOffhandSlot();
               if (savedItemSlot != -1) {
                  this.lastSwapAtMillis = System.currentTimeMillis();
                  this.resetSwapState();
                  InventorySwapQueue.getInstance().queueTrackedSwap(savedItemSlot, 40, 1);
               }
            } else if (minecraftClient.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) {
               int restoreSlot = this.findRestoreSlot();
               if (restoreSlot != -1) {
                  this.lastSwapAtMillis = System.currentTimeMillis();
                  this.resetSwapState();
                  InventorySwapQueue.getInstance().queueTrackedSwap(restoreSlot, 40, 1);
               }
            }
         }
      }
   }

   private boolean maceThreatNearby() {
      if (((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud()).getEvents().isEmpty()) {
         return false;
      }

      for (AbstractClientPlayerEntity player : minecraftClient.world.getPlayers()) {
         if (!ServerHelper.getInstance().server.isSelected(("SpookyTime"))) {
            if (player != minecraftClient.player
               && !FriendManager.contains(player.getGameProfile().name())
               && player.getMainHandStack().getItem() == Items.MACE
               && player.getY() - minecraftClient.player.getY() >= (2.0D)
               && player.getY() - player.lastY < (-0.1D)
               && minecraftClient.player.squaredDistanceTo(player.getX(), minecraftClient.player.getY(), player.getZ())
                  <= this.maceRadius.getValue() * this.maceRadius.getValue()
               && (player.getX() - player.lastX) * (minecraftClient.player.getX() - player.getX())
                     + (player.getZ() - player.lastZ) * (minecraftClient.player.getZ() - player.getZ())
                  >= 0.0) {
               return true;
            }
         } else if (player != minecraftClient.player
            && !FriendManager.contains(player.getGameProfile().name())
            && player.getMainHandStack().getItem() == Items.MACE
            && !(player.getY() - minecraftClient.player.getY() < (2.0D))
            && !(player.getY() - player.lastY >= (-0.1D))
            && !(
               minecraftClient.player.squaredDistanceTo(player.getX(), minecraftClient.player.getY(), player.getZ())
                  > this.maceRadius.getValue() * this.maceRadius.getValue()
            )
            && !(
               (player.getX() - player.lastX) * (minecraftClient.player.getX() - player.getX())
                     + (player.getZ() - player.lastZ) * (minecraftClient.player.getZ() - player.getZ())
                  < 0.0
            )) {
            double fallHeightAbovePlayer = this.fallPeakHeights.getOrDefault(player.getId(), player.getY()) - (minecraftClient.player.getY() + minecraftClient.player.getHeight());
            if (!(fallHeightAbovePlayer <= (1.5D)) && this.estimateMaceDamageToPlayer(player, (float)fallHeightAbovePlayer) > (2.5F)) {
               return true;
            }
         }
      }

      return false;
   }
}
