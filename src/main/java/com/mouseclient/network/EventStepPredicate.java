package com.mouseclient.network;

@FunctionalInterface
public interface EventStepPredicate<E> {
   public boolean test(E event);
}
