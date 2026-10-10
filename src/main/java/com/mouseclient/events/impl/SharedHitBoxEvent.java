package com.mouseclient.events.impl;

import com.mouseclient.network.Event;
import lombok.Generated;
import net.minecraft.entity.Entity;

public class SharedHitBoxEvent extends Event {
   private Entity entity;
   private float expansion;

   @Generated
   public Entity getEntity() {
      return this.entity;
   }

   @Generated
   public float getExpansion() {
      return this.expansion;
   }

   @Generated
   public SharedHitBoxEvent(Entity entity, float expansion) {
      this.entity = entity;
      this.expansion = expansion;
   }

   @Generated
   public void setEntity(Entity entity) {
      this.entity = entity;
   }

   @Generated
   public void setExpansion(float expansion) {
      this.expansion = expansion;
   }
}
