package com.mouseclient.gui;

public record OutlineColorRange(float startDegrees, float endDegrees, int color) {
   public static OutlineColorRange of(float startDegrees, float endDegrees, int color) {
      return new OutlineColorRange(startDegrees, endDegrees, color);
   }
}
