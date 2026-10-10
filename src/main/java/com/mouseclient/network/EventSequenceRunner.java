package com.mouseclient.network;

import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.EventBus;
import java.util.LinkedList;
import java.util.Queue;

public class EventSequenceRunner {
   private Queue<EventSequenceRunner.EventSequence> sequences = new LinkedList<>();
   private final EventListener<PlayerTickEvent> playerTickListener = event -> this.onEvent(event);

   public void replaceSequence(EventSequenceRunner.EventSequence sequence) {
      this.sequences.clear();
      this.sequences.add(sequence);
      this.onEvent(new PlayerTickEvent());
   }

   public void clear() {
      this.sequences.clear();
   }

   public void onEvent(Object event) {
      if (this.sequences.peek() != null) {
         if (this.sequences.peek().advance(event)) {
            this.sequences.poll();
         }
      }
   }

   public EventSequenceRunner() {
      EventBus.getInstance().subscribe(this);
   }

   public static class EventSequence {
      private Queue<EventSequenceStep<?>> steps = new LinkedList<>();
      private int stalledEvents = 0;
      private int timeoutEvents = 400;

      public boolean advance(Object event) {
         if (this.steps.peek() == null) {
            return true;
         }

         for (int completedSteps = 0;
            completedSteps < 64 && this.steps.peek() != null && this.steps.peek().eventType.isInstance(event) && this.steps.peek().test(event);
            completedSteps++
         ) {
            this.steps.poll();
            this.stalledEvents = 0;
         }

         if (this.steps.isEmpty()) {
            return true;
         } else if (++this.stalledEvents > this.timeoutEvents) {
            this.steps.clear();
            return true;
         } else {
            return false;
         }
      }

      public EventSequenceRunner.EventSequence timeoutEvents(int timeoutEvents) {
         this.timeoutEvents = Math.max(1, timeoutEvents);
         return this;
      }

      public <E> EventSequenceRunner.EventSequence then(Class<E> eventType, EventStepPredicate<E> predicate) {
         this.steps.add(new EventSequenceStep(eventType, predicate));
         return this;
      }
   }
}
