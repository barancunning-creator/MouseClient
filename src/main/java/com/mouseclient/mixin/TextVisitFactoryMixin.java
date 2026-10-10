package com.mouseclient.mixin;

import com.mouseclient.modules.impl.misc.NameProtect;
import net.minecraft.text.CharacterVisitor;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(TextVisitFactory.class)
public abstract class TextVisitFactoryMixin {
   @ModifyArgs(
      method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/text/TextVisitFactory;visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z"
      )
   )
   private static void maskLocalNickname(Args args) {
      if (NameProtect.getInstance().isEnabled()) {
         args.set(0, NameProtect.getInstance().replaceNickname((String)args.get(0)));
         args.set(4, NameProtect.getInstance().nicknameColorVisitor((String)args.get(0), (CharacterVisitor)args.get(4)));
      }
   }
}
