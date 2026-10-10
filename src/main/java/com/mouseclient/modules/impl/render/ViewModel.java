package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.events.impl.HeldItemRenderEvent;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.util.Arm;

@ModuleInfo(name = "ViewModel", description = "Позиция рук в кадре и поведение вьюмодели.", category = ModuleCategory.RENDER)
public final class ViewModel extends Module {
   private static ViewModel INSTANCE;
   public DecimalSetting rightX;
   public DecimalSetting rightY;
   public DecimalSetting rightZ;
   public DecimalSetting leftX;
   public DecimalSetting leftY;
   public DecimalSetting leftZ;
   public BooleanSetting removeUseAnimation;
   public BooleanSetting freezeHandMotion;
   public BooleanSetting auraOnly;
   private final EventListener<HeldItemRenderEvent> heldItemRenderListener = event -> {
      if (this.allowsHandOffset() && !event.getStack().isEmpty() && !event.getStack().contains(DataComponentTypes.MAP_ID)) {
         this.applyHandOffset(event.getMatrices(), event.getArm());
      }
   };

   private ViewModel() {
      this.addSetting(new SettingSeparator(("Правая рука")));
      this.rightX = this.addSetting(new DecimalSetting(("Правая X"), ("Смещение правой руки по X."), 0.0, (-1.0D), 1.0, (0.05D)));
      this.rightY = this.addSetting(new DecimalSetting(("Правая Y"), ("Смещение правой руки по Y."), 0.0, (-1.0D), 1.0, (0.05D)));
      this.rightZ = this.addSetting(new DecimalSetting(("Правая Z"), ("Смещение правой руки по Z."), 0.0, (-1.0D), 1.0, (0.05D)));
      this.addSetting(new SettingSeparator(("Левая рука")));
      this.leftX = this.addSetting(new DecimalSetting(("Левая X"), ("Смещение левой руки по X."), 0.0, (-1.0D), 1.0, (0.05D)));
      this.leftY = this.addSetting(new DecimalSetting(("Левая Y"), ("Смещение левой руки по Y."), 0.0, (-1.0D), 1.0, (0.05D)));
      this.leftZ = this.addSetting(new DecimalSetting(("Левая Z"), ("Смещение левой руки по Z."), 0.0, (-1.0D), 1.0, (0.05D)));
      this.removeUseAnimation = this.addSetting(new BooleanSetting(("Убрать анимацию юза"), ("Убирает тряску предмета при еде и питье."), false));
      this.freezeHandMotion = this.addSetting(new BooleanSetting(("Не двигать руки"), ("Отключает отставание рук при повороте камеры."), false));
      this.auraOnly = this.addSetting(new BooleanSetting(("Только с киллаурой"), ("Смещения рук работают только при включённой AttackAura."), false));
   }

   public boolean allowsHandOffset() {
      return !this.auraOnly.getValue() || AttackAura.getInstance().isEnabled();
   }

   public void applyHandOffset(MatrixStack matrices, Arm arm) {
      if (arm == Arm.RIGHT) {
         matrices.translate(this.rightX.getValue(), this.rightY.getValue(), this.rightZ.getValue());
      } else {
         matrices.translate(-this.leftX.getValue(), this.leftY.getValue(), this.leftZ.getValue());
      }
   }

   public static ViewModel getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ViewModel();
      }

      return INSTANCE;
   }

   public boolean removesUseAnimation() {
      return this.isEnabled() && this.removeUseAnimation.getValue();
   }

   public boolean freezesHandMotion() {
      return this.isEnabled() && this.freezeHandMotion.getValue();
   }
}
