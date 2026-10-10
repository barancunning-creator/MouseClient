package com.mouseclient.modules.impl.render;

import com.mouseclient.render.pipeline.SliderSetting;

public final class DecimalSetting extends SliderSetting {
   public DecimalSetting(String name, String description, double value, double minimum, double maximum, double step) {
      super(name, description, (float)value, (float)minimum, (float)maximum, (float)step, false);
   }
}
