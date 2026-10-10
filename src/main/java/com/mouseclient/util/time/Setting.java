package com.mouseclient.util.time;

import java.util.function.Supplier;

public abstract class Setting<T extends Setting<T>> {
   public final String name;
   public final String description;
   private Supplier<Boolean> visibilityCondition = () -> Boolean.TRUE;
   private Runnable changeListener;

   public T onChange(Runnable changeListener) {
      this.changeListener = changeListener;
      return this.self();
   }

   protected Setting(String name) {
      this(name, "");
   }

   public boolean isVisible() {
      Boolean conditionResult = this.visibilityCondition.get();
      return conditionResult == null || conditionResult;
   }

   protected final T self() {
      return (T)this;
   }

   public T visibleWhen(Supplier<Boolean> visibility) {
      return this.setVisibility(visibility);
   }

   protected void notifyChanged() {
      if (this.changeListener != null) {
         this.changeListener.run();
      }
   }

   protected Setting(String name, String description) {
      this.name = name;
      this.description = description == null ? "" : description;
   }

   public T setVisibility(Supplier<Boolean> visibility) {
      if (visibility != null) {
         this.visibilityCondition = visibility;
      }

      return this.self();
   }
}
