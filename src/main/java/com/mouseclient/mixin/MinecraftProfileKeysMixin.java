package com.mouseclient.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MinecraftClient.class)
public class MinecraftProfileKeysMixin {
   @Redirect(
      method = "<init>",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/session/ProfileKeys;create(Lcom/mojang/authlib/minecraft/UserApiService;Lnet/minecraft/client/session/Session;Ljava/nio/file/Path;)Lnet/minecraft/client/session/ProfileKeys;"
      )
   )
   private ProfileKeys skipKeyPairForOfflineSession(UserApiService userApiService, Session session, Path path) {
      return !session.getAccessToken().isBlank() && session.getUuidOrNull() != null && session.getUuidOrNull().version() == 4
         ? ProfileKeys.create(userApiService, session, path)
         : ProfileKeys.MISSING;
   }
}
