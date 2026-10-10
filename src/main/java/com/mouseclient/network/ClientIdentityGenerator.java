package com.mouseclient.network;

import com.mouseclient.util.entity.MinecraftAccess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.util.Arm;

public class ClientIdentityGenerator implements MinecraftAccess {
   private final Random random = new Random();
   private final ArrayList<ClientBrandTemplate> brandTemplates;
   private final ArrayList<String> languages;

   public ClientIdentity generate() {
      ClientBrandTemplate brandTemplate = this.brandTemplates.get(this.random.nextInt(this.brandTemplates.size()));
      SyncedClientOptions originalOptions = minecraftClient.options == null ? SyncedClientOptions.createDefault() : minecraftClient.options.getSyncedOptions();
      int modelParts = originalOptions.playerModelParts();
      if (this.random.nextInt(3) == 0) {
         for (PlayerModelPart part : PlayerModelPart.values()) {
            modelParts = this.random.nextInt(4) == 0 ? modelParts & ~part.getBitFlag() : modelParts | part.getBitFlag();
         }
      }

      return new ClientIdentity(
         brandTemplate,
         brandTemplate.versions().get(this.random.nextInt(brandTemplate.versions().size())),
         new SyncedClientOptions(
            this.languages.get(this.random.nextInt(this.languages.size())),
            Math.min(32, originalOptions.viewDistance() + this.random.nextInt(5)),
            originalOptions.chatVisibility(),
            originalOptions.chatColorsEnabled(),
            modelParts,
            this.random.nextInt(9) == 0 ? Arm.LEFT : Arm.RIGHT,
            this.random.nextBoolean(),
            this.random.nextBoolean(),
            ParticlesMode.values()[this.random.nextInt(ParticlesMode.values().length)]
         )
      );
   }

   public ClientIdentityGenerator() {
      this.brandTemplates = new ArrayList<>(
         Arrays.asList(
            new ClientBrandTemplate(("Vanilla"), ("vanilla"), ("1.21.11"), ("1.21.10"), ("1.21.9"), ("1.21.8")),
            new ClientBrandTemplate(("OptiFine"), ("vanilla"), ("1.21.11_HD_U_J7"), ("1.21.9_HD_U_J6"), ("1.21.8_HD_U_J5")),
            new ClientBrandTemplate(("Fabric"), ("fabric"), ("0.18.0"), ("0.17.2"), ("0.16.14")),
            new ClientBrandTemplate(("Forge"), ("forge"), ("58.0.12"), ("57.1.4"), ("56.0.34")),
            new ClientBrandTemplate(("NeoForge"), ("neoforge"), ("21.11.14"), ("21.11.7"), ("21.10.29")),
            new ClientBrandTemplate(("Quilt"), ("quilt"), ("0.29.1"), ("0.28.3")),
            new ClientBrandTemplate(("Lunar Client"), ("lunarclient:v%s"), ("3.4.11-8f21a0c"), ("3.4.9-b7d1a02"), ("3.3.7-c40e91d")),
            new ClientBrandTemplate(("LabyMod"), ("labymod:%s"), ("4.2.41"), ("4.2.35"), ("4.2.28")),
            new ClientBrandTemplate(("Feather"), ("feather:%s"), ("1.9.4"), ("1.9.1"), ("1.8.7")),
            new ClientBrandTemplate(("CheatBreaker"), ("cheatbreaker"), ("1.8.9"), ("1.12.2"))
         )
      );
      this.languages = new ArrayList<>(
         Arrays.asList(("ru_ru"), ("ru_ru"), ("ru_ru"), ("ru_ru"), ("ru_ru"), ("ru_ru"), ("ru_ru"), ("en_us"), ("en_us"), ("uk_ua"), ("be_by"), ("kk_kz"), ("en_gb"))
      );
   }
}
