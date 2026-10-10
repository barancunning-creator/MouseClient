package com.mouseclient.modules.impl.player;

import com.mouseclient.util.entity.MinecraftAccess;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class ServerItemCatalog implements MinecraftAccess {
   private static final ArrayList<ServerItemDefinition> items = new ArrayList<>();
   private static boolean worldCatalogReady;
   public static final BiPredicate<ItemStack, ServerItemDefinition> SPHERE_CHECKER = (stack, definition) -> {
      if (stack.getItem() != Items.PLAYER_HEAD || definition.item() != Items.PLAYER_HEAD) {
         return false;
      } else if (definition.attributes().isEmpty()) {
         return false;
      } else {
         return !ServerItemDefinition.CHECKER.test(stack, definition) ? false : sameHeadTexture(stack, definition.displayStack());
      }
   };

   public static boolean sameHeadTexture(ItemStack stack, ItemStack referenceStack) {
      ProfileComponent profile = stack.get(DataComponentTypes.PROFILE);
      ProfileComponent referenceProfile = referenceStack.get(DataComponentTypes.PROFILE);
      if (profile != null && referenceProfile != null) {
         String textureValue = headTextureValue(profile);
         String referenceTexture = headTextureValue(referenceProfile);
         return textureValue != null && textureValue.equals(referenceTexture);
      } else {
         return false;
      }
   }

   public static ArrayList<ServerItemCatalog.ItemCategory> categories() {
      ArrayList groups = new ArrayList();
      appendCategory(groups, ("Броня"));
      appendCategory(groups, ("Оружие"));
      appendCategory(groups, ("Сферы"));
      appendCategory(groups, ("Талисманы"));
      appendCategory(groups, ("Зелья"));
      appendCategory(groups, ("Прочее"));
      return groups;
   }

   private static ServerItemDefinition.Builder sphereBuilder(String textureValue, int uuidMostHigh, int uuidMostLow, int uuidLeastHigh, int uuidLeastLow) {
      return new ServerItemDefinition.Builder()
         .withCategory(("Сферы"))
         .withDisplayStack(createTexturedHead(textureValue, uuidMostHigh, uuidMostLow, uuidLeastHigh, uuidLeastLow))
         .withItem(Items.PLAYER_HEAD);
   }

   public static ServerItemDefinition findByName(String name) {
      return items.stream().filter(candidateDefinition -> candidateDefinition.name().equals(name)).findFirst().orElse(null);
   }

   public static void ensureInitialized() {
      if (!worldCatalogReady) {
         items.clear();
         register(
            armorBuilder(Items.NETHERITE_HELMET, ("Броня"))
               .withName(("Шлем Крушителя"))
               .withEnchantments(EnchantmentRequirement.of(Enchantments.RESPIRATION, 3), EnchantmentRequirement.of(Enchantments.AQUA_AFFINITY, 1))
               .build()
         );
         register(armorBuilder(Items.NETHERITE_CHESTPLATE, ("Броня")).withName(("Нагрудник Крушителя")).build());
         register(armorBuilder(Items.NETHERITE_LEGGINGS, ("Броня")).withName(("Поножи Крушителя")).build());
         register(
            armorBuilder(Items.NETHERITE_BOOTS, ("Броня"))
               .withName(("Ботинки Крушителя"))
               .withEnchantments(
                  EnchantmentRequirement.of(Enchantments.FEATHER_FALLING, 4),
                  EnchantmentRequirement.of(Enchantments.SOUL_SPEED, 3),
                  EnchantmentRequirement.of(Enchantments.DEPTH_STRIDER, 3)
               )
               .build()
         );
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Оружие"))
               .withName(("Меч Крушителя"))
               .withItem(Items.NETHERITE_SWORD)
               .withDisplayStack(createGlintStack(Items.NETHERITE_SWORD))
               .withEnchantments(
                  EnchantmentRequirement.of(Enchantments.SHARPNESS, 7),
                  EnchantmentRequirement.of(Enchantments.SMITE, 7),
                  EnchantmentRequirement.of(Enchantments.BANE_OF_ARTHROPODS, 7),
                  EnchantmentRequirement.of(Enchantments.SWEEPING_EDGE, 3),
                  EnchantmentRequirement.of(Enchantments.FIRE_ASPECT, 2),
                  EnchantmentRequirement.of(Enchantments.LOOTING, 5),
                  EnchantmentRequirement.of(Enchantments.UNBREAKING, 5),
                  EnchantmentRequirement.of(Enchantments.MENDING, 1)
               )
               .withTooltip(("Детекция III"), ("Окисление II"), ("Вампиризм II"), ("Опытный III"), ("Яд III"))
               .build()
         );
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Оружие"))
               .withName(("Булава Крушителя"))
               .withItem(Items.MACE)
               .withDisplayStack(createGlintStack(Items.MACE))
               .withEnchantments(
                  EnchantmentRequirement.of(Enchantments.SHARPNESS, 7),
                  EnchantmentRequirement.of(Enchantments.SMITE, 7),
                  EnchantmentRequirement.of(Enchantments.BANE_OF_ARTHROPODS, 7),
                  EnchantmentRequirement.of(Enchantments.DENSITY, 5),
                  EnchantmentRequirement.of(Enchantments.BREACH, 3),
                  EnchantmentRequirement.of(Enchantments.SWEEPING_EDGE, 3),
                  EnchantmentRequirement.of(Enchantments.FIRE_ASPECT, 2),
                  EnchantmentRequirement.of(Enchantments.LOOTING, 5),
                  EnchantmentRequirement.of(Enchantments.UNBREAKING, 5),
                  EnchantmentRequirement.of(Enchantments.MENDING, 1)
               )
               .withTooltip(("Опытный III"), ("Вампиризм II"), ("Окисление II"), ("Яд III"), ("Детекция III"))
               .build()
         );
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Оружие"))
               .withName(("Кирка Крушителя"))
               .withItem(Items.NETHERITE_PICKAXE)
               .withDisplayStack(createGlintStack(Items.NETHERITE_PICKAXE))
               .withEnchantments(
                  EnchantmentRequirement.of(Enchantments.FORTUNE, 5),
                  EnchantmentRequirement.of(Enchantments.EFFICIENCY, 10),
                  EnchantmentRequirement.of(Enchantments.UNBREAKING, 5),
                  EnchantmentRequirement.of(Enchantments.MENDING, 1)
               )
               .withTooltip(("Паутина"), ("Магнит"), ("Пингер"), ("Опытный III"), ("Авто-Плавка"), ("Бульдозер II"))
               .build()
         );
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Оружие"))
               .withName(("Трезубец Крушителя"))
               .withItem(Items.TRIDENT)
               .withDisplayStack(createGlintStack(Items.TRIDENT))
               .withEnchantments(
                  EnchantmentRequirement.of(Enchantments.SHARPNESS, 7),
                  EnchantmentRequirement.of(Enchantments.IMPALING, 5),
                  EnchantmentRequirement.of(Enchantments.LOYALTY, 3),
                  EnchantmentRequirement.of(Enchantments.CHANNELING, 1),
                  EnchantmentRequirement.of(Enchantments.FIRE_ASPECT, 2),
                  EnchantmentRequirement.of(Enchantments.UNBREAKING, 5),
                  EnchantmentRequirement.of(Enchantments.MENDING, 1)
               )
               .withTooltip(("Детекция III"), ("Яд III"), ("Возвращение"), ("Вампиризм II"), ("Опытный III"), ("Окисление II"), ("Ступор III"), ("Притяжение II"), ("Подрывник"), ("Скаут III"))
               .build()
         );
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Оружие"))
               .withName(("Арбалет Крушителя"))
               .withItem(Items.CROSSBOW)
               .withDisplayStack(createGlintStack(Items.CROSSBOW))
               .withEnchantments(
                  EnchantmentRequirement.of(Enchantments.MULTISHOT, 1),
                  EnchantmentRequirement.of(Enchantments.PIERCING, 5),
                  EnchantmentRequirement.of(Enchantments.QUICK_CHARGE, 3),
                  EnchantmentRequirement.of(Enchantments.UNBREAKING, 3),
                  EnchantmentRequirement.of(Enchantments.MENDING, 1)
               )
               .build()
         );
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Оружие"))
               .withName(("Заострённый меч"))
               .withItem(Items.NETHERITE_SWORD)
               .withDisplayStack(createGlintStack(Items.NETHERITE_SWORD))
               .withEnchantments(EnchantmentRequirement.of(Enchantments.SHARPNESS, 8))
               .build()
         );
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Сферы"))
               .withName(("Отмычка к сферам"))
               .withItem(Items.TRIPWIRE_HOOK)
               .withDisplayStack(createGlintStack(Items.TRIPWIRE_HOOK))
               .withTags(Pair.of(("spookystash:stash_id"), ("spheres")))
               .build()
         );
         register(
            sphereBuilder(("ewogICJ0aW1lc3RhbXAiIDogMTc1MDM0Mzg2MTE4NywKICAicHJvZmlsZUlkIiA6ICJlZGUyYzdhMGFjNjM0MTNiYjA5ZDNmMGJlZTllYzhlYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJTcGhlcmVBdGhlbmEiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTNmOWVlZGEzYmEyM2ZlMTQyM2M0MDM2ZTdkZDBhNzQ0NjFkZmY5NmJhZGM1YjJmMmI5ZmFhN2NjMTZmMzgyZiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"), (694031243), (1593913619), (-1794784623), (596248838))
               .withName(("Сфера Афины"))
               .withAttributes(
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_SPEED, (0.15D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.MOVEMENT_SPEED, (0.15D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (3.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (-2.0D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            sphereBuilder(("ewogICJ0aW1lc3RhbXAiIDogMTc1MDI3ODY0MTkwMCwKICAicHJvZmlsZUlkIiA6ICIxNzRjZmRiNGEzY2I0M2I1YmZjZGU0MjRjM2JiMmM2ZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJtYXJhZWwxOCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9lN2E3YWU3Y2RjZjYxNmU4YjdhNDIyMWE2MjFiMjQzNTc1M2M2MGVkNmEyNThlYTA2MGRhZTMwMDJmZmU5ZTI4IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="), (-1582985800), (1375286202), (-1125478243), (595287099))
               .withName(("Сфера Хаоса"))
               .withAttributes(
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (2.5D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MOVEMENT_SPEED, (0.07D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_SPEED, (0.13D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.ARMOR, (1.5D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (-4.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.GRAVITY, (0.09D), Operation.ADD_MULTIPLIED_BASE)
               )
               .build()
         );
         register(
            sphereBuilder(("ewogICJ0aW1lc3RhbXAiIDogMTc1MDI3ODYwODUyOCwKICAicHJvZmlsZUlkIiA6ICJkMTQ4NjFiM2UwZmM0Njk5OTFlMTcyNTllMzdiZjZhZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJyYXhpdG9jbCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83NzFhOWE0OThiNGZhNWVjNDkzNjJmOWJjODhlZGE0ZjUyYjA0ZGU0OWQ3NWFhM2NhMzMyYTFmZWExYWEwZTU3IiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="), (-2101547208), (2058105556), (-1495491604), (1400184240))
               .withName(("Сфера Сатира"))
               .withAttributes(
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_SPEED, (0.15D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.JUMP_STRENGTH, (-0.1D), Operation.ADD_MULTIPLIED_BASE)
               )
               .build()
         );
         register(
            sphereBuilder(("ewogICJ0aW1lc3RhbXAiIDogMTc1MDM0MzgzNDkzMCwKICAicHJvZmlsZUlkIiA6ICI1MzUzNWIxN2M0ZDY0NWQ0YWUwY2U2ZjM4Zjk0NTFjYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJVYml2aXMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTQxMWFjMTczODFiOWZjZTliYWIzYzcyYWZkYjdmMTk4NTcwZGFmNDczMmJkODExZDMxYzIyN2Q4MGZhMzliMSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"), (-1896649230), (-398640094), (-1320063783), (945063190))
               .withName(("Сфера Бестии"))
               .withAttributes(
                  AttributeModifierSpec.of(EntityAttributes.ARMOR, 1.0, Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MOVEMENT_SPEED, (0.1D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_SPEED, (0.1D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (4.0D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            sphereBuilder(("ewogICJ0aW1lc3RhbXAiIDogMTc1MDM0Mzc3NDI1NSwKICAicHJvZmlsZUlkIiA6ICJhYWMxYjA2OWNkMjE0NWE2ODNlNzQxNzE4MDcxMGU4MiIsCiAgInByb2ZpbGVOYW1lIiA6ICJqdXNhbXUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzE2YWRjNmJhZmNiNTdmZDcwN2RlZTdkZDZhNzM2ZmUxMjY3MTFkNTNhMWZkNmNlNzg5ZGE0MWIzYmUxM2YyYSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"), (2124864246), (465122563), (-1511078600), (-555424585))
               .withName(("Сфера Ареса"))
               .withAttributes(
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (-2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ARMOR, (-2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (6.0D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            sphereBuilder(("ewogICJ0aW1lc3RhbXAiIDogMTc1MDI3ODUzMjE4MywKICAicHJvZmlsZUlkIiA6ICI1OGZmZWI5NTMxNGQ0ODcwYTQwYjVjYjQyZDRlYTU5OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJTa2luREJuZXQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2UzYzExOGQ2OTZkOTEwZTU0ZGUwMmNhNGQ4MDc1NDNmOWIxOGMwMDhjOTgzOGQyZmY2OTM3NzYyMmZiMWQzMiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9"), (-1308798306), (1128020291), (-1307054059), (-1317369961))
               .withName(("Сфера Гидры"))
               .withAttributes(
                  AttributeModifierSpec.of(EntityAttributes.ARMOR, (2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (4.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.SUBMERGED_MINING_SPEED, (0.5D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.OXYGEN_BONUS, (0.5D), Operation.ADD_MULTIPLIED_BASE)
               )
               .build()
         );
         register(
            sphereBuilder(("ewogICJ0aW1lc3RhbXAiIDogMTc1MDI3ODU4MjQ5MSwKICAicHJvZmlsZUlkIiA6ICJhZWNkODIxZTQyYzE0ZDJlOThmNTA1OTg1MWI5OWMzNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJSb2RyaVgyMDc1IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2M2ODAzZTZkNTY2N2EyZDYxMDYyOGJjM2IzMmY4NjNjZGE0OTVjNDY1NjE2ZGU2NTVjYjMyOTkzM2I2MWFmNzciLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="), (1858435695), (-517000716), (-1346102858), (1389869685))
               .withName(("Сфера Икара"))
               .withAttributes(
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (2.0D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            sphereBuilder(("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODFlOTY5ODQ1OGI3ODQxYzk2YWU0ZjI0ZWM4NGFlMDE3MjQxMDA2NDFjNTY0ZTJhN2IxODVmNDA2ZThlZDIzIn19fQ=="), (654012711), (1596536861), (-2049048826), (-850597571))
               .withName(("Сфера Титана"))
               .withAttributes(
                  AttributeModifierSpec.of(EntityAttributes.ARMOR, (2.5D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ARMOR_TOUGHNESS, (2.5D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MOVEMENT_SPEED, (-0.15D), Operation.ADD_MULTIPLIED_BASE)
               )
               .build()
         );
         register(
            sphereBuilder(("ewogICJ0aW1lc3RhbXAiIDogMTc1MDM0Mzg2MTE4NywKICAicHJvZmlsZUlkIiA6ICJlZGUyYzdhMGFjNjM0MTNiYjA5ZDNmMGJlZTllYzhlYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJ0aGVEZXZKYWRlIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzZlNGUyZjEwNDdmM2VjNmU5ZTQ1OTE4NDczOWUzM2I3YzFmYzYzYWQ4MjAyYmRhYjlmMDI0NTA4YWRkMjNlNWIiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ=="), (-122635583), (2062758360), (-2035264015), (874042263))
               .withName(("Сфера Эрида"))
               .withAttributes(
                  AttributeModifierSpec.of(EntityAttributes.LUCK, 1.0, Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.BLOCK_INTERACTION_RANGE, 1.0, Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            talismanBuilder(
                  ("Талисман Крушителя"),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (4.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (3.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ARMOR, (2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ARMOR_TOUGHNESS, (2.0D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            talismanBuilder(
                  ("Талисман Карателя"),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (-4.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MOVEMENT_SPEED, (0.1D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (7.0D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            talismanBuilder(
                  ("Талисман Раздора"),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.MOVEMENT_SPEED, (0.1D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_SPEED, (0.1D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (4.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ARMOR, (-3.0D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            talismanBuilder(
                  ("Талисман Тирана"),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (-4.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ARMOR, (2.0D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            talismanBuilder(
                  ("Талисман Ярости"),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (-4.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (5.0D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            talismanBuilder(
                  ("Талисман Вихря"),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (2.0D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_SPEED, (0.15D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.MOVEMENT_SPEED, (0.15D), Operation.ADD_MULTIPLIED_BASE)
               )
               .build()
         );
         register(
            talismanBuilder(
                  ("Талисман Мрака"),
                  AttributeModifierSpec.of(EntityAttributes.MAX_HEALTH, (1.5D), Operation.ADD_VALUE),
                  AttributeModifierSpec.of(EntityAttributes.ARMOR, (1.5D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            talismanBuilder(
                  ("Талисман Демона"),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_SPEED, (0.1D), Operation.ADD_MULTIPLIED_BASE),
                  AttributeModifierSpec.of(EntityAttributes.ATTACK_DAMAGE, (2.5D), Operation.ADD_VALUE)
               )
               .build()
         );
         register(
            potionBuilder(
                  ("Хлопушка"),
                  PotionEffectSpec.of(StatusEffects.SLOWNESS, 10, 8),
                  PotionEffectSpec.of(StatusEffects.SPEED, 20, 4),
                  PotionEffectSpec.of(StatusEffects.BLINDNESS, 5, 8),
                  PotionEffectSpec.of(StatusEffects.GLOWING, 180, 0)
               )
               .build()
         );
         register(
            potionBuilder(
                  ("Святая вода"),
                  PotionEffectSpec.of(StatusEffects.REGENERATION, 45, 0),
                  PotionEffectSpec.of(StatusEffects.INVISIBILITY, 600, 1),
                  PotionEffectSpec.instant(StatusEffects.INSTANT_HEALTH, 1)
               )
               .build()
         );
         register(potionBuilder(("Зелье гнева"), PotionEffectSpec.of(StatusEffects.STRENGTH, 30, 4), PotionEffectSpec.of(StatusEffects.SLOWNESS, 30, 3)).build());
         register(
            potionBuilder(
                  ("Зелье палладина"),
                  PotionEffectSpec.of(StatusEffects.RESISTANCE, 600, 0),
                  PotionEffectSpec.of(StatusEffects.FIRE_RESISTANCE, 600, 0),
                  PotionEffectSpec.of(StatusEffects.HEALTH_BOOST, 60, 2),
                  PotionEffectSpec.of(StatusEffects.INVISIBILITY, 900, 0)
               )
               .build()
         );
         register(
            potionBuilder(
                  ("Зелье ассасина"),
                  PotionEffectSpec.of(StatusEffects.STRENGTH, 60, 3),
                  PotionEffectSpec.of(StatusEffects.SPEED, 300, 2),
                  PotionEffectSpec.of(StatusEffects.HASTE, 60, 0),
                  PotionEffectSpec.instant(StatusEffects.INSTANT_DAMAGE, 1)
               )
               .build()
         );
         register(
            potionBuilder(
                  ("Зелье радиации"),
                  PotionEffectSpec.of(StatusEffects.POISON, 20, 0),
                  PotionEffectSpec.of(StatusEffects.WITHER, 20, 0),
                  PotionEffectSpec.of(StatusEffects.SLOWNESS, 20, 2),
                  PotionEffectSpec.of(StatusEffects.HUNGER, 20, 4),
                  PotionEffectSpec.of(StatusEffects.GLOWING, 20, 0)
               )
               .build()
         );
         register(
            potionBuilder(
                  ("Снотворное"),
                  PotionEffectSpec.of(StatusEffects.WEAKNESS, 90, 0),
                  PotionEffectSpec.of(StatusEffects.MINING_FATIGUE, 10, 0),
                  PotionEffectSpec.of(StatusEffects.WITHER, 90, 2),
                  PotionEffectSpec.of(StatusEffects.BLINDNESS, 10, 0)
               )
               .build()
         );
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Чарка")).withItem(Items.ENCHANTED_GOLDEN_APPLE).build());
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Золотое яблоко")).withItem(Items.GOLDEN_APPLE).build());
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Золотая морковь")).withItem(Items.GOLDEN_CARROT).build());
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Дезориентация")).withItem(Items.ENDER_EYE).build());
         register(taggedItemBuilder(("Прочее"), Items.PHANTOM_MEMBRANE, ("Божья Аура"), ("effect-item-god")).build());
         register(taggedItemBuilder(("Прочее"), Items.PAPER, ("Драконий скин"), ("trap-skin-item-dragon")).withDisplayStack(createGlintStack(Items.PAPER)).build());
         register(taggedItemBuilder(("Прочее"), Items.PAPER, ("Неизбежный скин"), ("trap-skin-item-inevitable")).withDisplayStack(createGlintStack(Items.PAPER)).build());
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Прочее"))
               .withName(("Серебро"))
               .withItem(Items.IRON_NUGGET)
               .withTags(Pair.of(("spookystash:currency"), ("silver")))
               .build()
         );
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Алмаз")).withItem(Items.DIAMOND).build());
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Прочее"))
               .withName(("Книга починка"))
               .withItem(Items.ENCHANTED_BOOK)
               .withEnchantments(EnchantmentRequirement.of(Enchantments.MENDING, 1))
               .build()
         );
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Прочее"))
               .withName(("Элитры Крушителя"))
               .withItem(Items.ELYTRA)
               .withEnchantments(EnchantmentRequirement.of(Enchantments.UNBREAKING, 5), EnchantmentRequirement.of(Enchantments.MENDING, 1))
               .build()
         );
         register(taggedItemBuilder(("Прочее"), Items.GOLDEN_PICKAXE, ("Божье касание"), ("spawner-item-spawner-break")).withDisplayStack(createGlintStack(Items.GOLDEN_PICKAXE)).build());
         register(taggedItemBuilder(("Прочее"), Items.GOLDEN_PICKAXE, ("Мощный удар"), ("bedrock-item-bedrock-break")).withDisplayStack(createGlintStack(Items.GOLDEN_PICKAXE)).build());
         register(taggedItemBuilder(("Прочее"), Items.NETHERITE_PICKAXE, ("Молот Тора"), ("radius-item-mega-buldozer")).withDisplayStack(createGlintStack(Items.NETHERITE_PICKAXE)).build());
         register(taggedItemBuilder(("Прочее"), Items.NETHERITE_SCRAP, ("Трапка"), ("schematic-item-trap")).build());
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Спавнер")).withItem(Items.SPAWNER).build());
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Маяк")).withItem(Items.BEACON).build());
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Череп визер-скелета")).withItem(Items.WITHER_SKELETON_SKULL).build());
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Голова дракона")).withItem(Items.DRAGON_HEAD).build());
         register(taggedItemBuilder(("Прочее"), Items.TNT, ("Таер блэк"), ("tnt-item-black")).build());
         register(taggedItemBuilder(("Прочее"), Items.TNT, ("Таер вайт"), ("tnt-item-white")).build());
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Незеритовый слиток")).withItem(Items.NETHERITE_INGOT).build());
         register(new ServerItemDefinition.Builder().withCategory(("Прочее")).withName(("Изумрудная руда")).withItem(Items.EMERALD_ORE).build());
         register(taggedItemBuilder(("Прочее"), Items.SNOWBALL, ("Снежок заморозка"), ("effect-item-snowball")).build());
         register(taggedItemBuilder(("Прочее"), Items.FIRE_CHARGE, ("Огненный смерч"), ("effect-item-smerch")).build());
         register(taggedItemBuilder(("Прочее"), Items.SUGAR, ("Явная пыль"), ("effect-item-dust")).build());
         register(taggedItemBuilder(("Прочее"), Items.DRIED_KELP, ("Пласт"), ("schematic-item-plast")).build());
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Прочее"))
               .withName(("Кровавая стрела"))
               .withItem(Items.TIPPED_ARROW)
               .withEffects(
                  PotionEffectSpec.of(StatusEffects.WEAKNESS, 3, 2),
                  PotionEffectSpec.of(StatusEffects.BLINDNESS, 2, 0),
                  PotionEffectSpec.of(StatusEffects.MINING_FATIGUE, 2, 0),
                  PotionEffectSpec.of(StatusEffects.NAUSEA, 5, 0)
               )
               .build()
         );
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Прочее"))
               .withName(("Мучительная стрела"))
               .withItem(Items.TIPPED_ARROW)
               .withEffects(
                  PotionEffectSpec.of(StatusEffects.SLOWNESS, 5, 2),
                  PotionEffectSpec.of(StatusEffects.WITHER, 5, 2),
                  PotionEffectSpec.of(StatusEffects.POISON, 5, 2)
               )
               .build()
         );
         register(taggedItemBuilder(("Прочее"), Items.STRUCTURE_BLOCK, ("Чанкер 1x1"), ("executable-block-chunker-1")).build());
         register(taggedItemBuilder(("Прочее"), Items.JIGSAW, ("Дамагер"), ("executable-block-damager")).build());
         register(taggedItemBuilder(("Прочее"), Items.POPPED_CHORUS_FRUIT, ("Модификатор варпов"), ("modifier-item-setwarp-day")).build());
         register(
            new ServerItemDefinition.Builder()
               .withCategory(("Прочее"))
               .withItem(Items.FEATHER)
               .withName(("Модификатор полёта"))
               .withTags(Pair.of(("spookyitems:spooky-item"), ("modifier-item-fly-day")))
               .build()
         );
         register(taggedItemBuilder(("Прочее"), Items.ENDER_EYE, ("Модификатор эндер-сундука"), ("modifier-item-ec-day")).build());
         register(taggedItemBuilder(("Прочее"), Items.BUBBLE_CORAL, ("Модификатор починки"), ("modifier-item-fix-day")).build());
         worldCatalogReady = minecraftClient.world != null;
      }
   }

   public static boolean matches(ItemStack stack, ServerItemDefinition definition) {
      return isSphere(definition) ? SPHERE_CHECKER.test(stack, definition) : ServerItemDefinition.CHECKER.test(stack, definition);
   }

   private static String headTextureValue(ProfileComponent profile) {
      for (Property property : profile.getGameProfile().properties().get(("textures"))) {
         if (("textures").equals(property.name())) {
            return property.value();
         }
      }

      return null;
   }

   private ServerItemCatalog() {
   }

   private static ServerItemDefinition.Builder potionBuilder(String name, PotionEffectSpec... effects) {
      return new ServerItemDefinition.Builder()
         .withCategory(("Зелья"))
         .withName(name)
         .withItem(Items.SPLASH_POTION)
         .withDisplayStack(createPotionStack(effects))
         .withEffects(effects);
   }

   private static ItemStack createTexturedHead(String textureValue, int uuidMostHigh, int uuidMostLow, int uuidLeastHigh, int uuidLeastLow) {
      ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
      stack.set(
         DataComponentTypes.PROFILE,
         ProfileComponent.ofStatic(
            new GameProfile(
               new UUID((long)uuidMostHigh << 32 | uuidMostLow & (4294967295L), (long)uuidLeastHigh << 32 | uuidLeastLow & (4294967295L)),
               "",
               new PropertyMap(ImmutableMultimap.of(("textures"), new Property(("textures"), textureValue)))
            )
         )
      );
      return stack;
   }

   public static float categoryListHeight() {
      float height = 0.0F;

      for (ServerItemCatalog.ItemCategory category : categories()) {
         height += (8.5F) + category.items().size() * (17.5F) + (4.0F);
      }

      return height;
   }

   private static ItemStack createGlintStack(Item item) {
      ItemStack stack = item.getDefaultStack();
      stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
      return stack;
   }

   private static ServerItemDefinition register(ServerItemDefinition definition) {
      items.add(definition);
      return definition;
   }

   private static ServerItemDefinition.Builder taggedItemBuilder(String category, Item item, String name, String tagValue) {
      return new ServerItemDefinition.Builder().withCategory(category).withItem(item).withName(name).withTags(Pair.of(("spookyitems:spooky-item"), tagValue));
   }

   private static ServerItemDefinition.Builder armorBuilder(Item item, String category) {
      return new ServerItemDefinition.Builder()
         .withCategory(category)
         .withItem(item)
         .withDisplayStack(createGlintStack(item))
         .withEnchantments(
            EnchantmentRequirement.of(Enchantments.PROTECTION, 5),
            EnchantmentRequirement.of(Enchantments.BLAST_PROTECTION, 5),
            EnchantmentRequirement.of(Enchantments.FIRE_PROTECTION, 5),
            EnchantmentRequirement.of(Enchantments.PROJECTILE_PROTECTION, 5),
            EnchantmentRequirement.of(Enchantments.MENDING, 1),
            EnchantmentRequirement.of(Enchantments.UNBREAKING, 5)
         );
   }

   private static void appendCategory(ArrayList<ServerItemCatalog.ItemCategory> categories, String title) {
      java.util.ArrayList<com.mouseclient.modules.impl.player.ServerItemDefinition> categoryItems = items.stream().filter(candidateDefinition -> candidateDefinition.category().equals(title)).collect(Collectors.toCollection(ArrayList::new));
      if (!categoryItems.isEmpty()) {
         categories.add(new ServerItemCatalog.ItemCategory(title, categoryItems));
      }
   }

   private static ItemStack createPotionStack(PotionEffectSpec... effects) {
      ItemStack stack = Items.SPLASH_POTION.getDefaultStack();
      ArrayList effectInstances = new ArrayList();

      for (PotionEffectSpec spec : effects) {
         effectInstances.add(spec.effectInstance());
      }

      stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.empty(), effectInstances, Optional.empty()));
      return stack;
   }

   private static ServerItemDefinition.Builder talismanBuilder(String name, AttributeModifierSpec... attributes) {
      return new ServerItemDefinition.Builder().withCategory(("Талисманы")).withName(name).withItem(Items.TOTEM_OF_UNDYING).withAttributes(attributes);
   }

   public static boolean isShulkerBox(Item item) {
      return item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
   }

   public static float twoColumnRowsHeight(int itemCount) {
      if (itemCount <= 0) {
         return 0.0F;
      }

      int rows = (itemCount + 1) / 2;
      return rows * (115.0F);
   }

   public static ServerItemDefinition findMatching(ItemStack stack) {
      if (stack.getItem() == Items.PLAYER_HEAD) {
         for (ServerItemDefinition sphere : items) {
            if (hasSphereProfile(sphere) && SPHERE_CHECKER.test(stack, sphere)) {
               return sphere;
            }
         }
      }

      return items.stream().filter(candidateDefinition -> {
         if (isShulkerBox(candidateDefinition.item()) && isShulkerBox(stack.getItem())) {
            return true;
         } else {
            return hasSphereProfile(candidateDefinition) ? false : ServerItemDefinition.CHECKER.test(stack, candidateDefinition);
         }
      }).findFirst().orElse(null);
   }

   public static boolean isSphere(ServerItemDefinition definition) {
      return hasSphereProfile(definition);
   }

   public static ArrayList<ServerItemDefinition> items() {
      return items;
   }

   private static boolean hasSphereProfile(ServerItemDefinition definition) {
      return definition != null && definition.item() == Items.PLAYER_HEAD && !definition.attributes().isEmpty() && definition.displayStack().get(DataComponentTypes.PROFILE) != null;
   }

   public record ItemCategory(String title, ArrayList<ServerItemDefinition> items) {
   }
}
