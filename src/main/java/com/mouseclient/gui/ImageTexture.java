package com.mouseclient.gui;

import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

public record ImageTexture(Identifier id, TextureSetup linearSetup, TextureSetup nearestSetup, int width, int height) {
   public TextureSetup setup(boolean nearest) {
      return nearest ? this.nearestSetup : this.linearSetup;
   }

   public float drawWidth(float size) {
      return this.width > 0 && this.height > 0 && this.width < this.height ? size * ((float)this.width / this.height) : size;
   }

   public float drawHeight(float size) {
      return this.width > 0 && this.height > 0 && this.height < this.width ? size * ((float)this.height / this.width) : size;
   }
}
