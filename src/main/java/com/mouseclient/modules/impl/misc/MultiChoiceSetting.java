package com.mouseclient.modules.impl.misc;

import com.mouseclient.util.time.Setting;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MultiChoiceSetting extends Setting<MultiChoiceSetting> {
   private final List<String> options = new ArrayList<>();
   private final Set<String> selectedOptions = new LinkedHashSet<>();
   private int minimumSelection;
   public boolean expanded;

   private String resolveOption(String option) {
      for (String candidate : this.options) {
         if (candidate.equalsIgnoreCase(option)) {
            return candidate;
         }
      }

      return null;
   }

   protected MultiChoiceSetting(String name, String description, String[] options, String... selectedOptions) {
      super(name, description);

      for (String option : options) {
         this.options.add(option);
      }

      for (String selectedOption : selectedOptions) {
         String resolvedOption = this.resolveOption(selectedOption);
         if (resolvedOption != null) {
            this.selectedOptions.add(resolvedOption);
         }
      }
   }

   public boolean isSelected(String option) {
      String resolvedOption = this.resolveOption(option);
      return resolvedOption != null && this.selectedOptions.contains(resolvedOption);
   }

   public MultiChoiceSetting(String name, String description) {
      super(name, description);
   }

   public MultiChoiceSetting withOptions(String... options) {
      this.options.clear();

      for (String option : options) {
         this.options.add(option);
      }

      return this;
   }

   public int getSelectedCount() {
      return this.selectedOptions.size();
   }

   public List<String> getSelectedOptions() {
      return new ArrayList<>(this.selectedOptions);
   }

   public MultiChoiceSetting withSelectedOptions(String... selectedOptions) {
      this.selectedOptions.clear();

      for (String selectedOption : selectedOptions) {
         String resolvedOption = this.resolveOption(selectedOption);
         if (resolvedOption != null) {
            this.selectedOptions.add(resolvedOption);
         }
      }

      return this;
   }

   public void toggleOption(String option) {
      String resolvedOption = this.resolveOption(option);
      if (resolvedOption != null) {
         if (this.selectedOptions.contains(resolvedOption)) {
            if (this.getSelectedCount() > this.minimumSelection) {
               this.selectedOptions.remove(resolvedOption);
               this.notifyChanged();
            }
         } else {
            this.selectedOptions.add(resolvedOption);
            this.notifyChanged();
         }
      }
   }

   public Set<String> getSelection() {
      return new LinkedHashSet<>(this.selectedOptions);
   }

   public void toggleOptionAt(int index) {
      if (index >= 0 && index < this.options.size()) {
         this.toggleOption(this.options.get(index));
      }
   }

   public MultiChoiceSetting withMinimumSelection(int minimumSelection) {
      this.minimumSelection = Math.max(0, minimumSelection);
      return this;
   }

   public boolean isSelectedAt(int index) {
      return index >= 0 && index < this.options.size() && this.selectedOptions.contains(this.options.get(index));
   }

   public List<String> getOptions() {
      return this.options;
   }

   public String getSelectionSummary() {
      return this.getSelectedCount() + "/" + this.options.size();
   }

   public boolean matchesOption(String option) {
      return this.isSelected(option);
   }
}
