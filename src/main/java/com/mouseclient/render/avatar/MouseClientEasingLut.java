package com.mouseclient.render.avatar;

import com.mouseclient.util.time.EasingFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.math.MathHelper;

public final class MouseClientEasingLut {
   
   private static final Map<EasingFunction, NativeImageBackedTexture> texturesByEasing = new IdentityHashMap<>();

   public static TextureSetup textureSetup(EasingFunction easing) {
      if (easing == null) {
         return null;
      }

      NativeImageBackedTexture texture = texturesByEasing.computeIfAbsent(easing, MouseClientEasingLut::createTexture);
      GpuTextureView textureView = texture.getGlTextureView();
      return textureView == null ? null : TextureSetup.of(textureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
   }

   private MouseClientEasingLut() {
   }

   private static NativeImageBackedTexture createTexture(EasingFunction easing) {
      NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> ("mouseclient_easing_lut"), 256, 1, false);
      NativeImage image = texture.getImage();

      for (int var3 = 0; var3 < 256; var3++) {
         float progress = var3 / (255.0F);
         int channel = MathHelper.clamp(Math.round((float)easing.apply(progress) * (255.0F)), 0, 255);
         image.setColorArgb(var3, 0, (-16777216) | channel << 16 | channel << 8 | channel);
      }

      texture.upload();
      return texture;
   }
}
