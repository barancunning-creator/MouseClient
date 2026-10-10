package com.mouseclient.network;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class FrameRenderGate {
   private long intervalNanos;
   private long lastFrameNanos;

   private int monitorRefreshRate() {
      try {
         if (GLFW.glfwGetPrimaryMonitor() != 0L && GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor()) != null) {
            return GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor()).refreshRate();
         }
      } catch (Exception error) {
      }

      return 60;
   }

   public boolean shouldRenderFrame() {
      if (this.intervalNanos == 0L) {
         if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().getWindow() != null) {
            this.intervalNanos = (1000000000L) / Math.max(this.monitorRefreshRate(), 30);
            this.lastFrameNanos = System.nanoTime();
            return true;
         } else {
            return true;
         }
      } else {
         if (System.nanoTime() - this.lastFrameNanos < this.intervalNanos) {
            return false;
         }

         this.lastFrameNanos = System.nanoTime();
         return true;
      }
   }
}
