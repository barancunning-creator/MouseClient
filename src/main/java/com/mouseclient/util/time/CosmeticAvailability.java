package com.mouseclient.util.time;

import com.mouseclient.render.CosmeticEntry;

public final class CosmeticAvailability {
   public static boolean isAvailable(CosmeticEntry entry) {
      return entry != null;
   }

   private CosmeticAvailability() {
   }

   public static boolean isEnabled() {
      return true;
   }
}
