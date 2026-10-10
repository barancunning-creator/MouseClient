package com.mouseclient.util.time;

public final class ActionSetting extends Setting<ActionSetting> {
   private String label = "";
   private Runnable action;

   public ActionSetting withLabel(String label) {
      this.label = label == null ? "" : label;
      return this;
   }

   public void execute() {
      if (this.action != null) {
         this.action.run();
      }

      this.notifyChanged();
   }

   public String getLabel() {
      return this.label.isEmpty() ? this.name : this.label;
   }

   public ActionSetting(String name, String description) {
      super(name, description);
      this.label = name;
   }

   public ActionSetting withAction(Runnable action) {
      this.action = action;
      return this;
   }
}
