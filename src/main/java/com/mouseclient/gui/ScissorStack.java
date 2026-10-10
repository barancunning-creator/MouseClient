package com.mouseclient.gui;

import com.mouseclient.internal.GuiScale;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.ScreenRect;
import org.joml.Matrix3x2f;

public final class ScissorStack {
   private static final ScreenRect EMPTY = new ScreenRect(0, 0, 0, 0);
   private static final Deque<ScreenRect> stack = new ArrayDeque<>();

   public static void pushTransformed(Matrix3x2f transform, float x, float y, float width, float height) {
      if (transform == null) {
         push(x, y, width, height);
      } else {
         ScreenRect bounds = new ScreenRect(Math.round(x), Math.round(y), Math.max(0, Math.round(width)), Math.max(0, Math.round(height)))
            .transformEachVertex(transform);
         pushIntersection(bounds);
      }
   }

   public static void push(float x, float y, float width, float height) {
      int left = GuiScale.toGuiPixels(x);
      int top = GuiScale.toGuiPixels(y);
      int right = GuiScale.toGuiPixels(x + width);
      int bottom = GuiScale.toGuiPixels(y + height);
      pushIntersection(new ScreenRect(left, top, Math.max(0, right - left), Math.max(0, bottom - top)));
   }

   public static ScreenRect current() {
      return stack.peek();
   }

   private ScissorStack() {
   }

   private static void pushIntersection(ScreenRect bounds) {
      ScreenRect parentBounds = stack.peek();
      ScreenRect intersection = parentBounds == null ? bounds : parentBounds.intersection(bounds);
      stack.push(intersection == null ? EMPTY : intersection);
   }

   public static void clear() {
      stack.clear();
   }

   public static void pop() {
      if (!stack.isEmpty()) {
         stack.pop();
      }
   }
}
