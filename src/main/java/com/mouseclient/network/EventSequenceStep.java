package com.mouseclient.network;

import lombok.Generated;

public class EventSequenceStep<E> {
   public final Class<E> eventType;
   private final EventStepPredicate<E> predicate;

   @Generated
   public EventSequenceStep(Class<E> eventType, EventStepPredicate<E> predicate) {
      this.eventType = eventType;
      this.predicate = predicate;
   }

   public boolean test(Object event) {
      return this.predicate.test(this.eventType.cast(event));
   }
}
