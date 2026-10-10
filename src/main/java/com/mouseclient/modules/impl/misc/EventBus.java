package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.EventPriority;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class EventBus<T> implements EventSubscriptionRegistry {
   private static final EventBus<Object> INSTANCE = new EventBus<>();
   private final Map<Field, Type> eventTypesByField = new ConcurrentHashMap<>();
   private final Map<Long, EventListener<T>> listenersByOwnerAndField = new ConcurrentHashMap<>();
   private final Map<Type, Set<ListenerSubscription<T>>> subscriptionsByEventType = new ConcurrentHashMap<>();
   private final Map<Type, Set<ListenerSubscription<T>>> dispatchCache = new ConcurrentHashMap<>();
   private boolean dispatching;
   private boolean dispatchCacheInvalidated;

   public boolean hasListeners(Class<?> eventType) {
      java.util.Set<com.mouseclient.modules.impl.misc.ListenerSubscription<T>> subscriptions = this.dispatchCache.get(eventType);
      return subscriptions != null && !subscriptions.isEmpty();
   }

   @Override
   public void subscribe(Object owner) {
      if (owner != null) {
         this.removeOwnerSubscriptions(owner);
         Arrays.stream(owner.getClass().getDeclaredFields())
            .filter(field -> field.getType() == EventListener.class)
            .forEach(
               listenerField -> {
                  EventListener listener = this.resolveListener(owner, listenerField);
                  if (listener != null) {
                     this.subscriptionsByEventType
                        .computeIfAbsent(this.resolveEventType(listenerField), eventType -> new CopyOnWriteArraySet<>())
                        .add(
                           new ListenerSubscription<>(
                              owner, listener, listenerField.isAnnotationPresent(EventPriority.class) ? listenerField.getAnnotation(EventPriority.class).value() : 0
                           )
                        );
                  }
               }
            );
         this.invalidateDispatchCache();
      }
   }

   @Override
   public void rebuildDispatchCache() {
      this.dispatchCache.clear();
      this.subscriptionsByEventType
         .forEach(
            (eventType, subscriptions) -> this.dispatchCache
               .put(
                  eventType,
                  (Set)subscriptions.stream()
                     .sorted(Comparator.<ListenerSubscription<T>>comparingInt(subscription -> subscription.getPriority()).reversed())
                     .filter(subscription -> subscription.getListener() != null)
                     .collect(Collectors.toCollection(LinkedHashSet::new))
               )
         );
   }

   public static EventBus<Object> getInstance() {
      return INSTANCE;
   }

   private EventListener<T> resolveListener(Object owner, Field listenerField) {
      long ownerFieldKey = (long)System.identityHashCode(owner) << 32 | listenerField.hashCode() & (4294967295L);
      return this.listenersByOwnerAndField.computeIfAbsent(ownerFieldKey, cacheKey -> {
         boolean accessible = listenerField.canAccess(owner);
         if (!accessible) {
            listenerField.setAccessible(true);
         }

         try {
            if (listenerField.get(owner) instanceof EventListener listener) {
               return listener;
            }
         } catch (IllegalAccessException error) {
            throw new RuntimeException(("Error getting field value"), error);
         } finally {
            listenerField.setAccessible(accessible);
         }

         return null;
      });
   }

   @Override
   public void unsubscribe(Object owner) {
      if (owner != null) {
         this.removeOwnerSubscriptions(owner);
         this.listenersByOwnerAndField.keySet().removeIf(ownerFieldKey -> ownerFieldKey >>> 32 == System.identityHashCode(owner));
         this.invalidateDispatchCache();
      }
   }

   public void post(T event) {
      if (event != null) {
         java.util.Set<com.mouseclient.modules.impl.misc.ListenerSubscription<T>> subscriptions = this.dispatchCache.get(event.getClass());
         if (subscriptions != null && !subscriptions.isEmpty()) {
            this.dispatching = true;

            try {
               for (ListenerSubscription subscription : subscriptions) {
                  subscription.getListener().onEvent(event);
               }
            } finally {
               this.dispatching = false;
               if (this.dispatchCacheInvalidated) {
                  this.dispatchCacheInvalidated = false;
                  this.rebuildDispatchCache();
               }
            }
         }
      }
   }

   private void removeOwnerSubscriptions(Object owner) {
      this.subscriptionsByEventType.values().forEach(subscriptions -> subscriptions.removeIf(subscription -> subscription.getOwner() == owner));
   }

   private void invalidateDispatchCache() {
      if (this.dispatching) {
         this.dispatchCacheInvalidated = true;
      } else {
         this.rebuildDispatchCache();
      }
   }

   private Type resolveEventType(Field listenerField) {
      return this.eventTypesByField.computeIfAbsent(listenerField, field -> ((ParameterizedType)field.getGenericType()).getActualTypeArguments()[0]);
   }
}
