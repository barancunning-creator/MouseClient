package com.mouseclient.util.time;

import com.mouseclient.gui.SmoothScrollState;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.util.entity.MinecraftAccess;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class PotionPresetStore implements MinecraftAccess {
   private static PotionPresetStore INSTANCE;
   public TextSetting draftName;
   public TextSetting search;
   public BindSetting hotkey;
   public ItemStack[] draftStacks;
   public String[] draftPotionNames;
   public int selectedDraftSlot;
   public ArrayList<PotionPresetStore.PotionPreset> presets;
   public SmoothScrollState pickerScroll;
   public SmoothScrollState presetScroll;
   public float[] pickerBounds;
   public float[] presetListBounds;

   public ArrayList<ServerHelper.ServerItemBind> getMatchingPotions() {
      ArrayList matchingBindings = new ArrayList();

      for (ServerHelper.ServerItemBind itemBinding : ServerHelper.getInstance().itemBinds()) {
         if (itemBinding.item() == null
            && (
               this.search.getValue().trim().isEmpty()
                  || itemBinding.binding().name.toLowerCase(Locale.ROOT).contains(this.search.getValue().toLowerCase(Locale.ROOT).trim())
            )) {
            matchingBindings.add(itemBinding);
         }
      }

      return matchingBindings;
   }

   public void setDraftPotion(int slot, String potionName) {
      if (slot >= 0 && slot < 6) {
         this.draftStacks[slot] = createPreviewStack(potionName);
         this.draftPotionNames[slot] = potionName;
         this.selectedDraftSlot = -1;
         this.search.setValue("");
      }
   }

   public int firstEmptyDraftSlot() {
      for (int var1 = 0; var1 < 6; var1++) {
         if (this.draftStacks[var1].isEmpty()) {
            return var1;
         }
      }

      return -1;
   }

   public static PotionPresetStore getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new PotionPresetStore();
      }

      return INSTANCE;
   }

   public void save() {
      JsonArray jsonArray = new JsonArray();

      for (PotionPresetStore.PotionPreset preset : this.presets) {
         JsonObject json = new JsonObject();
         json.addProperty(("name"), preset.name);
         json.addProperty(("active"), preset.enabled);
         json.addProperty(("key"), preset.hotkey.getKeyCode());
         JsonArray potionsJson = new JsonArray();

         for (PotionPresetStore.PresetPotion potion : preset.potions) {
            JsonObject potionJson = new JsonObject();
            potionJson.addProperty(("kind"), potion.kind);
            potionJson.addProperty(("name"), potion.name);
            potionsJson.add(potionJson);
         }

         json.add(("potions"), potionsJson);
         jsonArray.add(json);
      }

      try {
         Files.createDirectories(FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")));
         Files.writeString(
            FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("potioncombiner.json")), new GsonBuilder().setPrettyPrinting().create().toJson(jsonArray)
         );
      } catch (Exception error) {
      }
   }

   public static ItemStack createPreviewStack(String potionName) {
      ItemStack stack = new ItemStack(Items.SPLASH_POTION);
      if (SpecialPotion.fromName(potionName) != null) {
         stack.set(
            DataComponentTypes.POTION_CONTENTS,
            new PotionContentsComponent(Optional.empty(), Optional.of(SpecialPotion.fromName(potionName).colorRgb() | (-16777216)), List.of(), Optional.empty())
         );
      } else if (potionName.toLowerCase(Locale.ROOT).contains(("прыгуч"))) {
         stack.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Optional.empty(), Optional.of((-131196)), List.of(), Optional.empty()));
      }

      return stack;
   }

   public void saveDraft() {
      PotionPresetStore.PotionPreset preset = new PotionPresetStore.PotionPreset();
      preset.name = this.draftName.getValue().trim().isEmpty()
         ? "Прес " + (this.presets.size() + 1)
         : this.draftName.getValue().trim().substring(0, Math.min(7, this.draftName.getValue().trim().length()));
      preset.hotkey.setBinding(this.hotkey.getBinding());

      for (String draftPotionName : this.draftPotionNames) {
         if (draftPotionName != null) {
            preset.potions.add(new PotionPresetStore.PresetPotion(("splash"), draftPotionName));
         }
      }

      if (!preset.potions.isEmpty()) {
         this.presets.add(preset);
         this.draftName.setValue("");
         this.hotkey.setBinding(null);

         for (int var6 = 0; var6 < 6; var6++) {
            this.draftStacks[var6] = ItemStack.EMPTY;
         }

         for (int var7 = 0; var7 < 6; var7++) {
            this.draftPotionNames[var7] = null;
         }

         this.save();
      }
   }

   private PotionPresetStore() {
      this.draftName = (new TextSetting(("Имя пресета"), ("Уникальное имя данного пресета"), "", 7) {}).withPlaceholder(("Введите текст..."));
      this.search = new TextSetting(("Поиск"), "").withPlaceholder(("Поиск"));
      this.hotkey = new BindSetting(("Горячая клавиша"), ("Зелье выбрасываются при нажатии бинда"));
      this.draftStacks = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
      this.draftPotionNames = new String[6];
      this.selectedDraftSlot = -1;
      this.presets = new ArrayList<>();
      this.pickerScroll = new SmoothScrollState();
      this.presetScroll = new SmoothScrollState();
      this.load();
   }

   private void load() {
      try {
         if (!Files.exists(FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("potioncombiner.json")))) {
            return;
         }

         for (JsonElement jsonElement : JsonParser.parseString(Files.readString(FabricLoader.getInstance().getConfigDir().resolve(("mouseclient")).resolve(("potioncombiner.json"))))
            .getAsJsonArray()) {
            PotionPresetStore.PotionPreset preset = new PotionPresetStore.PotionPreset();
            preset.name = jsonElement.getAsJsonObject().get(("name")).getAsString();
            preset.enabled = jsonElement.getAsJsonObject().get(("active")).getAsBoolean();
            preset.hotkey.withKeyCode(jsonElement.getAsJsonObject().get(("key")).getAsInt());

            for (JsonElement potionElement : jsonElement.getAsJsonObject().getAsJsonArray(("potions"))) {
               preset.potions
                  .add(new PotionPresetStore.PresetPotion(potionElement.getAsJsonObject().get(("kind")).getAsString(), potionElement.getAsJsonObject().get(("name")).getAsString()));
            }

            this.presets.add(preset);
         }
      } catch (Exception error) {
      }
   }

   public static final class PotionPreset {
      public String name = "";
      public boolean enabled = true;
      public BindSetting hotkey;
      public ArrayList<PotionPresetStore.PresetPotion> potions;
      public boolean keyWasPressed;

      public PotionPreset() {
         this.hotkey = new BindSetting(("Горячая клавиша"), ("Зелье выбрасываются при нажатии бинда"));
         this.potions = new ArrayList<>();
      }
   }

   public static final class PresetPotion {
      public String kind;
      public String name;

      public PresetPotion(String kind, String name) {
         this.kind = kind;
         this.name = name;
      }

      public ItemStack previewStack() {
         return PotionPresetStore.createPreviewStack(this.name);
      }

      public static String kindOf(ItemStack stack) {
         return stack.isOf(Items.SPLASH_POTION) ? ("splash") : (stack.isOf(Items.LINGERING_POTION) ? ("lingering") : ("potion"));
      }
   }
}
