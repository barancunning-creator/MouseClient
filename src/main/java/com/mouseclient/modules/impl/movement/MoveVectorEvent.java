package com.mouseclient.modules.impl.movement;

import lombok.Generated;
import net.minecraft.util.math.Vec3d;

public final class MoveVectorEvent extends CancelableEvent {
   private Vec3d vec3d;

   @Generated
   public MoveVectorEvent(Vec3d vec3d) {
      this.vec3d = vec3d;
   }

   @Generated
   public Vec3d getMovement() {
      return this.vec3d;
   }

   @Generated
   public void setMovement(Vec3d movement) {
      this.vec3d = movement;
   }
}
