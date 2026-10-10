package com.mouseclient.gui;

import com.mouseclient.internal.GuiScale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;

public final class HudRenderContext {
   private static boolean chatOpen;
   private static boolean previousChatOpen;
   private static float chatProgress;
   private static float transitionStartProgress;
   private static long transitionStartTime;
   private static long lastSecondTime = System.currentTimeMillis();
   private static int elapsedSeconds;
   private static DrawContext drawContext;

   public static void setDrawContext(DrawContext context) {
      drawContext = context;
   }

   public static void update(MinecraftClient client, float deltaSeconds) {
      chatOpen = client.currentScreen instanceof ChatScreen;
      long nowMillis = System.currentTimeMillis();
      if (chatOpen != previousChatOpen) {
         transitionStartProgress = chatProgress;
         transitionStartTime = nowMillis;
         previousChatOpen = chatOpen;
      }

      float transitionProgress = Math.min(1.0F, (float)(nowMillis - transitionStartTime) / (chatOpen ? (180.0F) : (120.0F)));
      chatProgress = transitionStartProgress + ((chatOpen ? 1.0F : 0.0F) - transitionStartProgress) * (1.0F - (float)Math.pow(1.0F - transitionProgress, (3.0D)));
      if (nowMillis - lastSecondTime >= (1000L)) {
         lastSecondTime = nowMillis;
         elapsedSeconds++;
      }
   }

   public static void pushScale(float x, float y, float scaleX, float scaleY) {
      if (drawContext != null) {
         float guiX = GuiScale.toGuiUnits(x);
         float guiY = GuiScale.toGuiUnits(y);
         drawContext.getMatrices().pushMatrix();
         drawContext.getMatrices().translate(guiX, guiY);
         drawContext.getMatrices().scale(scaleX, scaleY);
         drawContext.getMatrices().translate(-guiX, -guiY);
      }
   }

   public static void pushScale(float x, float y, float scale) {
      pushScale(x, y, scale, scale);
   }

   public static void popTransform() {
      if (drawContext != null) {
         drawContext.getMatrices().popMatrix();
      }
   }

   public static float chatProgress() {
      return chatProgress;
   }

   public static int elapsedSeconds() {
      return elapsedSeconds;
   }

   public static DrawContext getDrawContext() {
      return drawContext;
   }

   public static void pushIdentityScale(float x, float y, float unusedScale) {
      pushScale(x, y, 1.0F);
   }

   public static boolean isChatVisible() {
      return chatProgress > (0.01F);
   }

   public static boolean isChatOpen() {
      return chatOpen;
   }

   private HudRenderContext() {
   }
}
