package com.mouseclient.util.time;

import java.util.ArrayList;
import java.util.List;

public class ChoiceSetting extends Setting<ChoiceSetting> {
   private final List<String> options = new ArrayList<>();
   private int selectedIndex;
   public boolean expanded;

   public void selectNext() {
      if (!this.options.isEmpty()) {
         this.setSelectedIndex((this.selectedIndex + 1) % this.options.size());
      }
   }

   public ChoiceSetting withSelectedIndex(int selectedIndex) {
      this.selectedIndex = this.clampIndex(selectedIndex);
      return this;
   }

   public String getSelectedOption() {
      return this.options.isEmpty() ? "" : this.options.get(this.selectedIndex);
   }

   public void setSelectedIndex(int selectedIndex) {
      int clampedIndex = this.clampIndex(selectedIndex);
      if (clampedIndex != this.selectedIndex) {
         this.selectedIndex = clampedIndex;
         this.notifyChanged();
      }
   }

   public ChoiceSetting withSelectedOption(String selectedOption) {
      for (int selectedIndex = 0; selectedIndex < this.options.size(); selectedIndex++) {
         if (this.options.get(selectedIndex).equalsIgnoreCase(selectedOption)) {
            this.selectedIndex = selectedIndex;
            break;
         }
      }

      return this;
   }

   public List<String> getOptions() {
      return this.options;
   }

   public ChoiceSetting(String name, String description) {
      super(name, description);
   }

   public boolean isSelected(String option) {
      return this.getSelectedOption().equalsIgnoreCase(option);
   }

   public void selectOption(String option) {
      for (int optionIndex = 0; optionIndex < this.options.size(); optionIndex++) {
         if (this.options.get(optionIndex).equalsIgnoreCase(option)) {
            this.setSelectedIndex(optionIndex);
            return;
         }
      }
   }

   public int getSelectedIndex() {
      return this.selectedIndex;
   }

   public boolean matchesOption(String option) {
      return this.isSelected(option);
   }

   public String getValue() {
      return this.getSelectedOption();
   }

   private int clampIndex(int index) {
      return this.options.isEmpty() ? 0 : Math.max(0, Math.min(this.options.size() - 1, index));
   }

   public ChoiceSetting withOptions(String... options) {
      this.options.clear();

      for (String option : options) {
         this.options.add(option);
      }

      this.selectedIndex = this.clampIndex(this.selectedIndex);
      return this;
   }

   protected ChoiceSetting(String name, String description, String selectedOption, String... options) {
      super(name, description);

      for (String option : options) {
         this.options.add(option);
      }

      this.withSelectedOption(selectedOption);
   }
}
