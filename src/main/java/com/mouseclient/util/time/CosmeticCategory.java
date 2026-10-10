package com.mouseclient.util.time;

public enum CosmeticCategory {
   BODYWEAR("Рюкзаки", "Рюкзак", "bodywear", "cos_bodywear.png"),
   WINGS("Крылья", "Крылья", "wings", "cos_wings.png"),
   HAT("Головные уборы", "Убор", "hat", "cos_hat.png"),
   ACCESSORY("Аксессуары", "Аксессуар", "accessory", "cos_accessory.png"),
   PET("Питомцы", "Питомец", "pet", "cos_pet.png"),
   SWORD("Мечи", "Меч", "sword", "cos_sword.png", true),
   MACE("Булавы", "Булава", "mace", "cos_mace.png", true),
   SHIELD("Щиты", "Щит", "shield", "cos_shield.png", true);

   private final String displayName;
   private final String singularName;
   private final String resourceDirectory;
   private final String iconTexture;
   private final boolean itemCategory;

   CosmeticCategory(String displayName, String singularName, String resourceDirectory, String iconFile) {
      this(displayName, singularName, resourceDirectory, iconFile, false);
   }

   CosmeticCategory(String displayName, String singularName, String resourceDirectory, String iconFile, boolean itemCategory) {
      this.displayName = displayName;
      this.singularName = singularName;
      this.resourceDirectory = resourceDirectory;
      this.iconTexture = "assets/mouseclient/textures/gui/mainmenu/icons/" + iconFile;
      this.itemCategory = itemCategory;
   }

   public boolean isItemCategory() {
      return this.itemCategory;
   }

   public String displayName() {
      return this.displayName;
   }

   public String singularName() {
      return this.singularName;
   }

   public String resourceDirectory() {
      return this.resourceDirectory;
   }

   public String iconTexture() {
      return this.iconTexture;
   }
}
