package com.mouseclient.render.pipeline;

import com.mouseclient.util.time.Setting;
import java.util.function.Supplier;

public class SliderSetting extends Setting<SliderSetting> {
   private float value;
   private float minimum = 0.0F;
   private float maximum = 1.0F;
   private float step;
   private boolean integer;
   private String suffix;
   private Supplier<Boolean> textEntryCondition;

   public SliderSetting withStep(float step) {
      if (step > 0.0F) {
         this.step = step;
         this.value = this.clampAndSnap(this.value);
      }

      return this;
   }

   public SliderSetting withSuffix(String suffix) {
      this.suffix = suffix == null ? "" : suffix;
      return this;
   }

   public double doubleValue() {
      return this.value;
   }

   public float getStep() {
      return this.step;
   }

   public float getNormalizedValue() {
      return this.maximum <= this.minimum ? 0.0F : (this.value - this.minimum) / (this.maximum - this.minimum);
   }

   public void setNormalizedValue(float normalizedValue) {
      float clampedProgress = Math.max(0.0F, Math.min(1.0F, normalizedValue));
      this.setValue(this.minimum + (this.maximum - this.minimum) * clampedProgress);
   }

   private int getDecimalPlaces() {
      return !this.integer && !(this.step >= 1.0F) ? Math.max(1, (int)Math.ceil(-Math.log10(this.step))) : 0;
   }

   public int intValue() {
      return Math.round(this.value);
   }

   public String getSuffix() {
      return this.suffix;
   }

   public SliderSetting withIntegerStep(int integerStep) {
      return this.withStep(integerStep);
   }

   public float getMinimum() {
      return this.minimum;
   }

   public float getMaximum() {
      return this.maximum;
   }

   public SliderSetting(String name, String description) {
      super(name, description);
      this.step = (0.01F);
      this.suffix = "";
      this.textEntryCondition = () -> Boolean.FALSE;
      this.value = 0.0F;
   }

   public SliderSetting withTextEntryCondition(Supplier<Boolean> textEntryCondition) {
      if (textEntryCondition != null) {
         this.textEntryCondition = textEntryCondition;
      }

      return this;
   }

   public boolean isTextEntryEnabled() {
      return Boolean.TRUE.equals(this.textEntryCondition.get());
   }

   public boolean isInteger() {
      return this.integer;
   }

   public SliderSetting withIntegerRange(int minimum, int maximum) {
      this.minimum = minimum;
      this.maximum = maximum;
      this.integer = true;
      this.step = 1.0F;
      this.value = this.clampAndSnap(this.value);
      return this;
   }

   public SliderSetting withRange(float minimum, float maximum) {
      this.minimum = minimum;
      this.maximum = maximum;
      this.integer = false;
      this.value = this.clampAndSnap(this.value);
      return this;
   }

   private float clampAndSnap(float value) {
      float snappedValue = Math.max(this.minimum, Math.min(this.maximum, value));
      if (this.step > 0.0F) {
         snappedValue = this.minimum + Math.round((snappedValue - this.minimum) / this.step) * this.step;
         snappedValue = Math.max(this.minimum, Math.min(this.maximum, snappedValue));
      }

      return this.integer ? Math.round(snappedValue) : snappedValue;
   }

   public float floatValue() {
      return this.value;
   }

   public String getFormattedValue() {
      int decimalPlaces = this.getDecimalPlaces();
      String formattedValue = decimalPlaces <= 0 ? Integer.toString(Math.round(this.value)) : String.format("%." + decimalPlaces + "f", this.value);
      return this.suffix.isEmpty() ? formattedValue : formattedValue + this.suffix;
   }

   protected SliderSetting(String name, String description, float value, float minimum, float maximum, float step, boolean integer) {
      super(name, description);
      this.step = (0.01F);
      this.suffix = "";
      this.textEntryCondition = () -> Boolean.FALSE;
      this.minimum = minimum;
      this.maximum = maximum;
      this.step = step <= 0.0F ? (integer ? 1.0F : (0.01F)) : step;
      this.integer = integer;
      this.value = this.clampAndSnap(value);
   }

   public SliderSetting setValue(float value) {
      float snappedValue = this.clampAndSnap(value);
      if (snappedValue != this.value) {
         this.value = snappedValue;
         this.notifyChanged();
      }

      return this;
   }

   public float getValue() {
      return this.value;
   }
}
