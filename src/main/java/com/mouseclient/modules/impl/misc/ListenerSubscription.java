package com.mouseclient.modules.impl.misc;

import lombok.Generated;

public class ListenerSubscription<T> {
   private final Object owner;
   private final EventListener<T> listener;
   private final int priority;

   @Generated
   public ListenerSubscription(Object owner, EventListener<T> listener, int priority) {
      this.owner = owner;
      this.listener = listener;
      this.priority = priority;
   }

   @Generated
   public EventListener<T> getListener() {
      return this.listener;
   }

   @Generated
   public int getPriority() {
      return this.priority;
   }

   @Generated
   public Object getOwner() {
      return this.owner;
   }
}
