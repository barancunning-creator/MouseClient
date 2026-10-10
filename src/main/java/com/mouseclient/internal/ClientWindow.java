package com.mouseclient.internal;

import java.nio.IntBuffer;
import java.util.Locale;
import net.minecraft.SharedConstants;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.windows.WindowsLibrary;

public final class ClientWindow {

   public static final String TITLE = title();
   private static WindowsLibrary dwmLibrary;
   private static long dwmSetWindowAttribute;

   private static boolean isWindows() {
      return System.getProperty(("os.name"), "").toLowerCase(Locale.ROOT).contains(("win"));
   }

   public static String title() {
      return "маусклиент #1.0 - " + minecraftVersion();
   }

   public static String minecraftVersion() {
      return SharedConstants.getGameVersion().name();
   }

   public static String alternateTitle() {
      return "маусклиент · " + minecraftVersion();
   }

   private static long darkModeFunction() {
      if (dwmSetWindowAttribute != 0L) {
         return dwmSetWindowAttribute;
      }

      if (dwmLibrary == null) {
         dwmLibrary = new WindowsLibrary(("dwmapi.dll"));
      }

      dwmSetWindowAttribute = dwmLibrary.getFunctionAddress(("DwmSetWindowAttribute"));
      return dwmSetWindowAttribute;
   }

   public static void applyDarkTitleBar(long windowHandle) {
      if (isWindows() && windowHandle != 0L) {
         try {
            long nativeWindowHandle = GLFWNativeWin32.glfwGetWin32Window(windowHandle);
            long setWindowAttribute = darkModeFunction();
            if (nativeWindowHandle == 0L || setWindowAttribute == 0L) {
               return;
            }

            MemoryStack memoryStack = MemoryStack.stackPush();

            try {
               IntBuffer enabledFlag = memoryStack.ints(1);
               long flagAddress = MemoryUtil.memAddress(enabledFlag);
               int nativeResult = JNI.callPPI(nativeWindowHandle, 20, flagAddress, 4, setWindowAttribute);
               if (nativeResult != 0) {
                  JNI.callPPI(nativeWindowHandle, 19, flagAddress, 4, setWindowAttribute);
               }
            } catch (Throwable callFailure) {
               if (memoryStack != null) {
                  try {
                     memoryStack.close();
                  } catch (Throwable closeFailure) {
                     callFailure.addSuppressed(closeFailure);
                  }
               }

               throw callFailure;
            }

            if (memoryStack != null) {
               memoryStack.close();
            }
         } catch (Throwable platformFailure) {
         }
      }
   }

   private ClientWindow() {
   }
}
