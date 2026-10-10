package com.mouseclient.util.time;

import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.render.SettingSeparator;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class SettingSection {
   public final String title;
   public final Object expansionKey;
   public final List<Setting<?>> settings;
   private static final Map<Module, SettingSection.SectionCacheEntry> sectionCache = new IdentityHashMap<>();

   private SettingSection(String title, Object expansionKey, List<Setting<?>> settings) {
      this.title = title;
      this.expansionKey = expansionKey;
      this.settings = settings;
   }

   public boolean hasHeader() {
      return this.title != null;
   }

   public static List<SettingSection> getSections(Module module) {
      java.util.ArrayList<com.mouseclient.util.time.Setting<?>> moduleSettings = module.getSettings();
      SettingSection.SectionCacheEntry cachedSections = sectionCache.get(module);
      int settingCount = moduleSettings.size();
      Setting setting = settingCount == 0 ? null : (Setting)moduleSettings.get(settingCount - 1);
      if (cachedSections == null || cachedSections.size != settingCount || cachedSections.last != setting) {
         cachedSections = new SettingSection.SectionCacheEntry(List.copyOf(buildSections(module)), settingCount, setting);
         sectionCache.put(module, cachedSections);
      }

      return cachedSections.sections;
   }

   public static boolean hasVisibleSettings(List<SettingSection> sections) {
      for (SettingSection section : sections) {
         for (Setting setting : section.settings) {
            if (setting.isVisible()) {
               return true;
            }
         }
      }

      return false;
   }

   public static List<SettingSection> buildSections(Module module) {
      ArrayList sections = new ArrayList();
      String sectionTitle = null;
      Object sectionExpansionKey = module;
      ArrayList sectionSettings = new ArrayList();

      for (Setting setting : module.getSettings()) {
         if (setting instanceof SettingSeparator separator) {
            if (!sectionSettings.isEmpty() || sectionTitle != null) {
               sections.add(new SettingSection(sectionTitle, sectionExpansionKey, sectionSettings));
            }

            sectionTitle = separator.name;
            sectionExpansionKey = separator;
            sectionSettings = new ArrayList();
         } else {
            sectionSettings.add(setting);
         }
      }

      if (!sectionSettings.isEmpty() || sectionTitle != null) {
         sections.add(new SettingSection(sectionTitle, sectionExpansionKey, sectionSettings));
      }

      return sections;
   }

   private record SectionCacheEntry(List<SettingSection> sections, int size, Setting<?> last) {
   }
}
