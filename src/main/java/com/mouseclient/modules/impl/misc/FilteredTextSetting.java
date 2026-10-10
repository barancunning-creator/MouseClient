package com.mouseclient.modules.impl.misc;

import com.mouseclient.util.time.TextSetting;

public final class FilteredTextSetting extends TextSetting {
   private boolean digitsOnly;

   public FilteredTextSetting digitsOnly() {
      this.digitsOnly = true;
      return this;
   }

   public FilteredTextSetting(String name, String description, String value, int maxLength) {
      super(name, description, value, maxLength);
   }

   @Override
   public void insertText(String text) {
      super.insertText(text != null && this.digitsOnly ? text.replaceAll(("\\D"), "") : text);
   }
}
