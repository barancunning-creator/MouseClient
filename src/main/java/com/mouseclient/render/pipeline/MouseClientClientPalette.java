package com.mouseclient.render.pipeline;

import com.mouseclient.internal.PaletteTransition;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryStack;

public final class MouseClientClientPalette {
   public static final int MAX_COLORS = 6;
   
   private static final int[] BASE_COLORS = new int[]{(8150271), (6069759), (16739229)};

   private static GpuBuffer paramsBuffer;
   private static int[] activeColors = new int[]{(16777215)};
   private static int activeColorCount = 1;
   private static float phase;
   private static long unusedPaletteUpdatedAtMillis;
   private static float styleMode;
   private static float previousStyleMode;
   private static float lastStyleMode = (-1.0F);
   private static float cycleMix;

   private static int[] resampleColors(int[] colors, int count) {
      int[] sampledColors = new int[count];
      if (colors.length != 1) {
         for (int sampleIndex = 0; sampleIndex < count; sampleIndex++) {
            float normalizedPosition = count <= 1 ? 0.0F : (float)sampleIndex / (count - 1);
            float colorPosition = normalizedPosition * (colors.length - 1);
            int leftIndex = (int)colorPosition;
            if (leftIndex > colors.length - 2) {
               leftIndex = colors.length - 2;
            }

            sampledColors[sampleIndex] = mixRgb(colors[leftIndex], colors[leftIndex + 1], colorPosition - leftIndex);
         }

         return sampledColors;
      } else {
         int rgb = colors[0] & (16777215);

         for (int var4 = 0; var4 < count; var4++) {
            sampledColors[var4] = rgb;
         }

         return sampledColors;
      }
   }

   public static int[] colors() {
      return activeColors;
   }

   private MouseClientClientPalette() {
   }

   public static float styleMode() {
      return styleMode;
   }

   public static void updateAndUpload() {
      int[] sampledColors = resampleColors(BASE_COLORS, 6);
      activeColors = sampledColors;
      activeColorCount = 6;
      cycleMix = 0.0F;
      unusedPaletteUpdatedAtMillis = System.currentTimeMillis();
      styleMode = 1.0F;
      if (lastStyleMode >= 0.0F && styleMode != lastStyleMode) {
         previousStyleMode = lastStyleMode;
         PaletteTransition.restart();
      }

      lastStyleMode = styleMode;
      GpuBuffer buffer = ensureBuffer();
      if (buffer != null) {
         try {
            MemoryStack stack = MemoryStack.stackPush();

            try {
               ByteBuffer paramsData = stack.calloc(128);
               paramsData.putFloat(0, activeColorCount);
               paramsData.putFloat(4, phase);
               paramsData.putFloat(8, styleMode);
               paramsData.putFloat(12, PaletteTransition.getProgress());
               paramsData.putFloat(112, previousStyleMode);
               paramsData.putFloat(116, cycleMix);

               for (int colorIndex = 0; colorIndex < activeColorCount; colorIndex++) {
                  int rgb = sampledColors[colorIndex] & (16777215);
                  int byteOffset = (1 + colorIndex) * 4 * 4;
                  paramsData.putFloat(byteOffset, (rgb >>> 16 & 0xFF) / (255.0F));
                  paramsData.putFloat(byteOffset + 4, (rgb >>> 8 & 0xFF) / (255.0F));
                  paramsData.putFloat(byteOffset + 8, (rgb & 0xFF) / (255.0F));
                  paramsData.putFloat(byteOffset + 12, 1.0F);
               }

               paramsData.position(0);
               RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, (128L)), paramsData);
            } catch (Throwable uploadFailure) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable closeFailure) {
                     uploadFailure.addSuppressed(closeFailure);
                  }
               }

               throw uploadFailure;
            }

            if (stack != null) {
               stack.close();
            }
         } catch (RuntimeException error) {
         }
      }
   }

   public static boolean unusedFalseFlag() {
      return false;
   }

   private static float channel(int[] colors, int index, int channelIndex) {
      int rgb = colors[Math.max(0, Math.min(colors.length - 1, index))];
      return (rgb >> 16 - channelIndex * 8 & 0xFF) / (255.0F);
   }

   public static float previousStyleMode() {
      return previousStyleMode;
   }

   public static int colorCount() {
      return activeColorCount;
   }

   public static float phase() {
      return phase;
   }

   private static GpuBuffer ensureBuffer() {
      if (paramsBuffer != null && !paramsBuffer.isClosed() && paramsBuffer.size() >= (128L)) {
         return paramsBuffer;
      }

      close();

      try {
         paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_client_palette"), 136, (128L));
         return paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private static int mixRgb(int start, int end, float progress) {
      float blend = progress < 0.0F ? 0.0F : Math.min(progress, 1.0F);
      int startRed = start >> 16 & 0xFF;
      int startGreen = start >> 8 & 0xFF;
      int startBlue = start & 0xFF;
      int endRed = end >> 16 & 0xFF;
      int endGreen = end >> 8 & 0xFF;
      int endBlue = end & 0xFF;
      int red = Math.round(startRed + (endRed - startRed) * blend);
      int green = Math.round(startGreen + (endGreen - startGreen) * blend);
      int blue = Math.round(startBlue + (endBlue - startBlue) * blend);
      return red << 16 | green << 8 | blue;
   }

   public static float cycleMix() {
      return cycleMix;
   }

   public static GpuBuffer buffer() {
      return ensureBuffer();
   }

   public static void close() {
      if (paramsBuffer != null) {
         paramsBuffer.close();
         paramsBuffer = null;
      }
   }

   private static float[] sampleRamp(float progress) {
      int[] palette = activeColors;
      int colorCount = activeColorCount;
      if (colorCount <= 1) {
         return new float[]{channel(palette, 0, 0), channel(palette, 0, 1), channel(palette, 0, 2)};
      }

      float clampedProgress = Math.max(0.0F, Math.min(1.0F, progress));
      float scaledIndex = clampedProgress * (colorCount - 1);
      int leftIndex = Math.max(0, Math.min(colorCount - 1, (int)Math.floor(scaledIndex)));
      int rightIndex = Math.min(leftIndex + 1, colorCount - 1);
      float blend = Math.max(0.0F, Math.min(1.0F, scaledIndex - leftIndex));
      blend = blend * blend * ((3.0F) - 2.0F * blend);
      float[] channels = new float[3];

      for (int channelIndex = 0; channelIndex < 3; channelIndex++) {
         float leftChannel = channel(palette, leftIndex, channelIndex);
         float rightChannel = channel(palette, rightIndex, channelIndex);
         channels[channelIndex] = leftChannel + (rightChannel - leftChannel) * blend;
      }

      return channels;
   }

   public static int sampleColor(float phase) {
      int[] palette = activeColors;
      int colorCount = activeColorCount;
      if (palette == null || palette.length == 0) {
         return (16777215);
      }

      if (colorCount <= 1) {
         return palette[0] & (16777215);
      }

      float wrappedPhase = phase - (float)Math.floor(phase);
      float scaledPhase = wrappedPhase * colorCount;
      int centerIndex = Math.max(0, Math.min(colorCount - 1, (int)Math.floor(scaledPhase)));
      float blend = Math.max(0.0F, Math.min(1.0F, scaledPhase - centerIndex));
      int previousIndex = centerIndex - 1;
      if (previousIndex < 0) {
         previousIndex += colorCount;
      }

      int nextIndex = centerIndex + 1;
      if (nextIndex >= colorCount) {
         nextIndex -= colorCount;
      }

      int followingIndex = centerIndex + 2;
      if (followingIndex >= colorCount) {
         followingIndex -= colorCount;
      }

      float blendSquared = blend * blend;
      float blendCubed = blendSquared * blend;
      float rampProgress = (0.5F) - (0.5F) * (float)Math.cos((6.2831855F) * wrappedPhase);
      float[] rampChannels = sampleRamp(rampProgress);
      float cycleWeight = Math.max(0.0F, Math.min(1.0F, cycleMix));
      int rgb = 0;

      for (int channelIndex = 0; channelIndex < 3; channelIndex++) {
         float previousChannel = channel(palette, previousIndex, channelIndex);
         float centerChannel = channel(palette, centerIndex, channelIndex);
         float nextChannel = channel(palette, nextIndex, channelIndex);
         float followingChannel = channel(palette, followingIndex, channelIndex);
         float cycleChannel = (0.5F)
            * (
               2.0F * centerChannel
                  + (-previousChannel + nextChannel) * blend
                  + (2.0F * previousChannel - (5.0F) * centerChannel + (4.0F) * nextChannel - followingChannel) * blendSquared
                  + (-previousChannel + (3.0F) * centerChannel - (3.0F) * nextChannel + followingChannel) * blendCubed
            );
         float mixedChannel = rampChannels[channelIndex] + (cycleChannel - rampChannels[channelIndex]) * cycleWeight;
         int byteChannel = Math.round(Math.max(0.0F, Math.min(1.0F, mixedChannel)) * (255.0F));
         rgb |= byteChannel << 16 - channelIndex * 8;
      }

      return rgb;
   }
}
