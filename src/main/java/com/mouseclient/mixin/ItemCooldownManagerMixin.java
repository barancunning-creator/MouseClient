package com.mouseclient.mixin;

import com.mouseclient.access.ItemCooldownAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemCooldownManager.class)
public abstract class ItemCooldownManagerMixin implements ItemCooldownAccess {
   @Shadow
   private int tick;
   @Shadow
   @Final
   private Map<Identifier, ?> entries;
   @Unique
   private final HashMap<Identifier, Integer> cooldownEndTickByGroup = new HashMap<>();

   @Shadow
   public abstract Identifier getGroup(ItemStack stack);

   @Shadow
   public abstract void remove(Identifier id);

   @Inject(method = "set(Lnet/minecraft/util/Identifier;I)V", at = @At("HEAD"))
   private void trackCooldownEndTick(Identifier id, int duration, CallbackInfo callback) {
      this.cooldownEndTickByGroup.put(id, this.tick + duration);
   }

   @Inject(method = "remove(Lnet/minecraft/util/Identifier;)V", at = @At("HEAD"))
   private void dropCooldownEndTick(Identifier id, CallbackInfo callback) {
      this.cooldownEndTickByGroup.remove(id);
   }

   @Override
   public int remainingCooldownTicks(ItemStack stack) {
      Integer endTick = this.cooldownEndTickByGroup.get(this.getGroup(stack));
      return endTick == null ? 0 : Math.max(0, endTick - this.tick);
   }

   @Override
   public void wipeAllCooldowns() {
      for (Identifier id : new ArrayList<>(this.entries.keySet())) {
         this.remove(id);
      }
   }
}
