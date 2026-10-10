package com.mouseclient.modules.impl.combat;

import com.mouseclient.modules.impl.misc.MultiChoiceSetting;

public final class MultiSelectSetting extends MultiChoiceSetting {
   public MultiSelectSetting(String name, String description, String[] options, String... selectedOptions) {
      super(name, description, options, selectedOptions);
   }
}
