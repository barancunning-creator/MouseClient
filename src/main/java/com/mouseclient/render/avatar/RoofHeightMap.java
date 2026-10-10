package com.mouseclient.render.avatar;

import com.mouseclient.util.entity.MinecraftAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap.Type;

public final class RoofHeightMap implements MinecraftAccess {
   public static final int MAP_SIZE = 64;
   public static final int HEIGHT_BIAS = 128;
   public static int originX = (-2147483648);
   public static int originZ;
   public static int originY;
   private static NativeImage heightImage;
   private static GpuTexture heightTexture;
   private static GpuTextureView heightTextureView;
   private static long lastUpdateMillis;

   private RoofHeightMap() {
   }

   public static void update() {
      if (minecraftClient.world != null) {
         if (heightTexture == null || heightTexture.isClosed()) {
            heightImage = new NativeImage(64, 64, false);
            heightTexture = RenderSystem.getDevice().createTexture(() -> ("mouseclient_roof_map"), 5, TextureFormat.RGBA8, 64, 64, 1, 1);
            heightTextureView = RenderSystem.getDevice().createTextureView(heightTexture);
            originX = (-2147483648);
         }

         int nextOriginX = (MathHelper.floor(minecraftClient.gameRenderer.getCamera().getCameraPos().x) & -8) - 32;
         int nextOriginZ = (MathHelper.floor(minecraftClient.gameRenderer.getCamera().getCameraPos().z) & -8) - 32;
         int nextOriginY = MathHelper.floor(minecraftClient.gameRenderer.getCamera().getCameraPos().y) & -8;
         if (nextOriginX != originX || nextOriginZ != originZ || nextOriginY != originY || System.currentTimeMillis() - lastUpdateMillis > (500L)) {
            for (int var3 = 0; var3 < 64; var3++) {
               for (int var4 = 0; var4 < 64; var4++) {
                  int encodedHeight = MathHelper.clamp(minecraftClient.world.getTopY(Type.MOTION_BLOCKING, nextOriginX + var4, nextOriginZ + var3) - nextOriginY + 128, 0, 255);
                  heightImage.setColor(var4, var3, (-16777216) | encodedHeight << 16 | encodedHeight << 8 | encodedHeight);
               }
            }

            RenderSystem.getDevice().createCommandEncoder().writeToTexture(heightTexture, heightImage);
            originX = nextOriginX;
            originZ = nextOriginZ;
            originY = nextOriginY;
            lastUpdateMillis = System.currentTimeMillis();
         }
      }
   }

   public static GpuTextureView textureView() {
      return heightTextureView;
   }
}
