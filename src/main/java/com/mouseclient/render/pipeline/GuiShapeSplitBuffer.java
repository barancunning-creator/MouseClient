package com.mouseclient.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.lwjgl.system.MemoryStack;

public final class GuiShapeSplitBuffer {
   public static final int MAX_SPLITS = 16;

   private static final float[] splitData = new float[192];
   private static int splitCount;
   private static GpuBuffer paramsBuffer;

   public static void reset() {
      splitCount = 0;
      Arrays.fill(splitData, 0.0F);
   }

   public static GpuBuffer buffer() {
      return ensureBuffer();
   }

   public static void upload() {
      GpuBuffer buffer = ensureBuffer();
      if (buffer != null) {
         try {
            MemoryStack stack = MemoryStack.stackPush();

            try {
               ByteBuffer paramsData = stack.calloc(768);

               for (int var3 = 0; var3 < 192; var3++) {
                  paramsData.putFloat(var3 * 4, splitData[var3]);
               }

               paramsData.position(0);
               RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, (768L)), paramsData);
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

   private GuiShapeSplitBuffer() {
   }

   public static void close() {
      if (paramsBuffer != null) {
         paramsBuffer.close();
         paramsBuffer = null;
      }
   }

   private static GpuBuffer ensureBuffer() {
      if (paramsBuffer != null && !paramsBuffer.isClosed() && paramsBuffer.size() >= (768L)) {
         return paramsBuffer;
      }

      close();

      try {
         paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_client_splits"), 136, (768L));
         return paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   public static int addSplit(float parentCenterX, float parentCenterY, float parentHalfWidth, float parentHalfHeight, float childCenterX, float childCenterY, float childHalfWidth, float childHalfHeight, float childRadius, float blendRadius) {
      if (splitCount >= 16) {
         return 0;
      }

      int splitIndex = splitCount++;
      int baseOffset = splitIndex * 3 * 4;
      splitData[baseOffset] = parentCenterX;
      splitData[baseOffset + 1] = parentCenterY;
      splitData[baseOffset + 2] = parentHalfWidth;
      splitData[baseOffset + 3] = parentHalfHeight;
      splitData[baseOffset + 4] = childCenterX;
      splitData[baseOffset + 5] = childCenterY;
      splitData[baseOffset + 6] = childHalfWidth;
      splitData[baseOffset + 7] = childHalfHeight;
      splitData[baseOffset + 8] = childRadius;
      splitData[baseOffset + 9] = blendRadius;
      return splitIndex + 1;
   }
}
