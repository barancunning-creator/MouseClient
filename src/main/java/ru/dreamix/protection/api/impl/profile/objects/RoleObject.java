package ru.dreamix.protection.api.impl.profile.objects;

import lombok.Generated;

public enum RoleObject {
   DEFAULT("User", 0),
   BETA("Beta", 1),
   YOUTUBE("YouTube", 2),
   ADMIN("Administrator", 3);

   private final String translatedName;
   private final int priority;

   @Override
   public String toString() {
      return this.translatedName;
   }

   public boolean priorityHigherOrEquals(RoleObject minimumRole) {
      return this.priority >= minimumRole.priority;
   }

   @Generated
   RoleObject(String translatedName, int priority) {
      this.translatedName = translatedName;
      this.priority = priority;
   }
}
