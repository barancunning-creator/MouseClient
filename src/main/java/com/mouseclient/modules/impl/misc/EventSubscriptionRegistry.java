package com.mouseclient.modules.impl.misc;

public interface EventSubscriptionRegistry {
   public void subscribe(Object owner);

   public void unsubscribe(Object owner);

   public void rebuildDispatchCache();
}
