package com.mouseclient.network;

import lombok.Generated;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.util.Arm;

public class ClientIdentity {
   private ClientBrandTemplate brandTemplate;
   private String version;
   private SyncedClientOptions options;

   public String description() {
      return ("%s %s (%s) · %s · %d чанков · %s").formatted(
         this.brandTemplate.name(),
         this.version,
         this.brandTemplate.formatBrand(this.version),
         this.options.language(),
         this.options.viewDistance(),
         this.options.mainArm() == Arm.LEFT ? ("левша") : ("правша")
      );
   }

   @Generated
   public SyncedClientOptions options() {
      return this.options;
   }

   @Generated
   public String version() {
      return this.version;
   }

   @Generated
   public ClientIdentity(ClientBrandTemplate brandTemplate, String version, SyncedClientOptions options) {
      this.brandTemplate = brandTemplate;
      this.version = version;
      this.options = options;
   }

   @Generated
   public ClientBrandTemplate brandTemplate() {
      return this.brandTemplate;
   }
}
