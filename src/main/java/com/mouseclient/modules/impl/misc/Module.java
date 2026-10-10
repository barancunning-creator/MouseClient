package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.player.InputBinding;
import com.mouseclient.util.entity.ModuleSoundPlayer;
import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.time.Setting;
import java.util.ArrayList;

public abstract class Module implements MinecraftAccess {
   public String name;
   public String description;
   public ModuleCategory category;
   private boolean enabled;
   private int keyCode = -1;
   public boolean expanded;
   private final ArrayList<Setting<?>> settings = new ArrayList<>();

   public void setSubscribed(boolean subscribed) {
      if (subscribed != this.enabled) {
         this.enabled = subscribed;
         if (subscribed) {
            EventBus.getInstance().subscribe(this);
         } else {
            EventBus.getInstance().unsubscribe(this);
         }
      }
   }

   protected <S extends Setting<?>> S addSetting(S setting) {
      this.settings.add(setting);
      return (S)setting;
   }

   public Module withKeyCode(int keyCode) {
      this.keyCode = keyCode;
      return this;
   }

   public boolean isBound() {
      return this.keyCode != -1;
   }

   public int getKeyCode() {
      return this.keyCode;
   }

   protected Module() {
      ModuleInfo metadata = this.getClass().getAnnotation(ModuleInfo.class);
      this.name = metadata.name();
      this.description = metadata.description();
      this.category = metadata.category();
      ModuleRegistry.register(this);
   }

   public String getBindingName() {
      return !this.isBound() ? "" : InputBinding.keyboard(this.keyCode).getDisplayName();
   }

   public void toggle() {
      this.setEnabled(!this.enabled);
   }

   protected void addSettings(Setting<?>... settings) {
      for (Setting setting : settings) {
         this.settings.add(setting);
      }
   }

   public boolean hasSettings() {
      return !this.settings.isEmpty();
   }

   protected void onEnable() {
   }

   public ArrayList<Setting<?>> getSettings() {
      return this.settings;
   }

   protected Module(String name, String description, ModuleCategory category) {
      this.name = name;
      this.description = description == null ? "" : description;
      this.category = category;
      ModuleRegistry.register(this);
   }

   protected void onDisable() {
   }

   public void setEnabledFlag(boolean enabledFlag) {
      this.enabled = enabledFlag;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean enabled) {
      if (enabled != this.enabled) {
         this.enabled = enabled;
         if (enabled) {
            EventBus.getInstance().subscribe(this);
            this.onEnable();
         } else {
            EventBus.getInstance().unsubscribe(this);
            this.onDisable();
         }

         ModuleSoundPlayer.play(enabled ? ("notify_type1_enabled") : ("notify_type1_disabled"));
      }
   }
}
