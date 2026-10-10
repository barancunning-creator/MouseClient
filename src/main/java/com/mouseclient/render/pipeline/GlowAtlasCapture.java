package com.mouseclient.render.pipeline;

import net.minecraft.client.texture.TextureSetup;

public final class GlowAtlasCapture {
   public float u = 0.0F;
   public float v = 0.0F;
   public float uSize = 1.0F;
   public float vSize = 1.0F;
   public int submissionIndex = 0;
   public TextureSetup textureSetup = TextureSetup.empty();

   public void reset() {
      this.u = 0.0F;
      this.v = 0.0F;
      this.uSize = 1.0F;
      this.vSize = 1.0F;
      this.textureSetup = TextureSetup.empty();
   }
}
