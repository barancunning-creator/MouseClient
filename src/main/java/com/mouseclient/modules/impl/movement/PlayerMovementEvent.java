package com.mouseclient.modules.impl.movement;

import lombok.Generated;

public final class PlayerMovementEvent extends CancelableEvent {
   private double x;
   private double y;
   private double z;
   private boolean onGround;

   @Generated
   public PlayerMovementEvent(double x, double y, double z, boolean onGround) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.onGround = onGround;
   }

   @Generated
   public double getZ() {
      return this.z;
   }

   @Generated
   public void setZ(double z) {
      this.z = z;
   }

   @Generated
   public double getX() {
      return this.x;
   }

   @Generated
   public void setX(double x) {
      this.x = x;
   }

   @Generated
   public void setY(double y) {
      this.y = y;
   }

   @Generated
   public void setOnGround(boolean onGround) {
      this.onGround = onGround;
   }

   @Generated
   public boolean isOnGround() {
      return this.onGround;
   }

   @Generated
   public double getY() {
      return this.y;
   }
}
