package com.mouseclient.modules.impl.combat;

import com.mouseclient.internal.LuaClientTypes;
import com.mouseclient.internal.LuaClientBindings;
import com.mouseclient.util.entity.MinecraftAccess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.minecraft.entity.LivingEntity;
import org.luaj.vm2.LuaFunction;
import org.luaj.vm2.LuaValue;

public final class LuaRotationRegistry implements MinecraftAccess {
   private static final LinkedHashMap<String, LuaFunction> callbacks = new LinkedHashMap<>();
   private final ArrayList<String> registeredNames = new ArrayList<>();

   public static boolean isRegistered(String name) {
      return callbacks.get(name) != null;
   }

   public boolean isHorizontallyAligned(LuaClientTypes.LivingEntityView target, float maxDistanceSquared) {
      return target != null && minecraftClient.player != null
         ? target.getLivingEntity()
               .getEntityPos()
               .add(0.0, target.getLivingEntity().getStandingEyeHeight() - (0.3F), 0.0)
               .subtract(minecraftClient.player.getEyePos())
               .horizontalLengthSquared()
            < maxDistanceSquared
         : false;
   }

   public void unregisterAll() {
      for (String var2 : new ArrayList<>(this.registeredNames)) {
         this.unregister(var2);
      }
   }

   public boolean isHorizontallyAligned(LuaClientTypes.LivingEntityView target) {
      return target != null && minecraftClient.player != null
         ? target.getLivingEntity()
               .getEntityPos()
               .add(0.0, target.getLivingEntity().getStandingEyeHeight() - (0.3F), 0.0)
               .subtract(minecraftClient.player.getEyePos())
               .horizontalLengthSquared()
            < (0.0925D)
         : false;
   }

   public static void invoke(String name, LivingEntity entity, boolean attackReady, float attackRange) {
      if (callbacks.get(name) != null && entity != null) {
         callbacks.get(name).call(LuaClientBindings.coerce(new LuaClientTypes.LivingEntityView(entity)), LuaValue.valueOf(attackReady), LuaValue.valueOf(attackRange));
      }
   }

   public void unregister(String name) {
      callbacks.remove(name);
      this.registeredNames.remove(name);
      if (AttackAura.getInstance().rotationMode.isSelected(name)) {
         AttackAura.getInstance().rotationMode.withSelectedOption(("FunTime"));
      }

      AttackAura.getInstance().rotationMode.getOptions().remove(name);
   }

   public void register(String name, LuaFunction callback) {
      callbacks.put(name, callback);
      if (!this.registeredNames.contains(name)) {
         this.registeredNames.add(name);
      }

      if (!AttackAura.getInstance().rotationMode.getOptions().contains(name)) {
         AttackAura.getInstance().rotationMode.getOptions().add(name);
      }
   }
}
