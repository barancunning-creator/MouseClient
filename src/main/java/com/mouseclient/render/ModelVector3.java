package com.mouseclient.render;

public class ModelVector3 {
   public static final ModelVector3 ZERO = new ModelVector3(0.0F, 0.0F, 0.0F);
   public float x;
   public float y;
   public float z;

   public void set(float x, float y, float z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public float getX() {
      return this.x;
   }

   public void setZ(float z) {
      this.z = z;
   }

   public ModelVector3(float x, float y, float z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public void setX(float x) {
      this.x = x;
   }

   public void setY(float y) {
      this.y = y;
   }

   public float getZ() {
      return this.z;
   }

   @Override
   public String toString() {
      return this.x + "," + this.y + "," + this.z;
   }

   public ModelVector3 cross(ModelVector3 other) {
      return new ModelVector3(
         this.y * other.z - this.z * other.y,
         this.z * other.x - this.x * other.z,
         this.x * other.y - this.y * other.x
      );
   }

   public float getY() {
      return this.y;
   }
}
