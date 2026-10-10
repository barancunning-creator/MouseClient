package com.mouseclient.render.pipeline;

import net.minecraft.client.texture.TextureSetup;

public final class BlurTextureRegion {
   public float x;
   public float y;
   public float width = 1.0F;
   public float height = 1.0F;
   public TextureSetup textureSetup = TextureSetup.empty();

   public void reset() {
      this.x = 0.0F;
      this.y = 0.0F;
      this.width = 1.0F;
      this.height = 1.0F;
      this.textureSetup = TextureSetup.empty();
   }
}
