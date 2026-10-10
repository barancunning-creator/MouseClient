package com.mouseclient.render.pipeline;

public record LineSpec(float startX, float startY, float endX, float endY, float thickness, int color, float fadeStart, float fadeEnd) {
   public float[] corners() {
      float deltaX = this.endX - this.startX;
      float deltaY = this.endY - this.startY;
      float lineLength = (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY);
      if (lineLength < 1.0E-4F) {
         lineLength = 1.0E-4F;
      }

      float normalOffsetX = -deltaY / lineLength * this.thickness * 0.5F;
      float normalOffsetY = deltaX / lineLength * this.thickness * 0.5F;
      return new float[]{
         this.startX + normalOffsetX,
         this.startY + normalOffsetY,
         this.startX - normalOffsetX,
         this.startY - normalOffsetY,
         this.endX - normalOffsetX,
         this.endY - normalOffsetY,
         this.endX + normalOffsetX,
         this.endY + normalOffsetY
      };
   }

   public float endY() {
      return this.endY;
   }

   public float startX() {
      return this.startX;
   }

   public float endX() {
      return this.endX;
   }

   public float startY() {
      return this.startY;
   }

   public float length() {
      float deltaX = this.endX - this.startX;
      float deltaY = this.endY - this.startY;
      return (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY);
   }

   public LineSpec(float startX, float startY, float endX, float endY, float thickness, int color) {
      this(startX, startY, endX, endY, thickness, color, 1.0F, 1.0F);
   }

   public boolean visible() {
      if (!(this.thickness <= 0.0F) && (this.color >>> 24 & 0xFF) != 0) {
         float deltaX = this.endX - this.startX;
         float deltaY = this.endY - this.startY;
         return deltaX * deltaX + deltaY * deltaY > 1.0E-4F;
      } else {
         return false;
      }
   }
}
