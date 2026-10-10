package com.mouseclient.gui;

public record EffectIconQuad(float x, float y, float size, float uMin, float vMin, float uMax, float vMax, int color) {
   public float uMin() {
      return this.uMin;
   }

   public float uMax() {
      return this.uMax;
   }

   public float vMax() {
      return this.vMax;
   }

   public float vMin() {
      return this.vMin;
   }
}
