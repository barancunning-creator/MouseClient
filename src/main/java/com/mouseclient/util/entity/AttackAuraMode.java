package com.mouseclient.util.entity;

import com.mouseclient.modules.impl.combat.AttackAura;

public final class AttackAuraMode {
   private AttackAuraMode() {
   }

   public static String selected() {
      AttackAura aura = AttackAura.getInstance();
      return aura == null || aura.rotationMode == null ? "builder" : aura.rotationMode.getSelectedOption();
   }
}
