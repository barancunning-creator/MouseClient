package com.mouseclient.modules.impl.render;

import com.mouseclient.util.time.Setting;

public final class BooleanSetting extends Setting<BooleanSetting> {
   private boolean value;
   private final boolean defaultValue;

   public BooleanSetting setValue(boolean value) {
      if (value != this.value) {
         this.value = value;
         this.notifyChanged();
      }

      return this;
   }

   public boolean getDefaultValue() {
      return this.defaultValue;
   }

   public boolean isEnabled() {
      return this.value;
   }

   public BooleanSetting(String name, String description) {
      this(name, description, false);
   }

   public boolean getValue() {
      return this.value;
   }

   public BooleanSetting(String name, String description, boolean value) {
      super(name, description);
      this.value = value;
      this.defaultValue = value;
   }

   public BooleanSetting toggle() {
      return this.setValue(!this.value);
   }
}
