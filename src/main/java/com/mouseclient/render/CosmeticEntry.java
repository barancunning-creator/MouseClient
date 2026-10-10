package com.mouseclient.render;

import com.mouseclient.util.time.CosmeticPlacement;
import com.mouseclient.util.time.CosmeticCategory;
import net.minecraft.util.Identifier;

public record CosmeticEntry(int id, CosmeticCategory category, String name, boolean beta, String modelResource, String textureResource, String previewResource, CosmeticPlacement placement) {
   public Identifier textureId() {
      return this.textureResource == null ? null : Identifier.of("mouseclient", "cosmetics/" + this.textureResource);
   }

   public Identifier previewId() {
      return Identifier.of("mouseclient", "cosmetics/" + this.previewResource);
   }
}
