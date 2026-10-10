package com.mouseclient.network;

import java.util.ArrayList;
import java.util.Arrays;
import lombok.Generated;

public class ClientBrandTemplate {
   private String name;
   private String brandFormat;
   private ArrayList<String> versions;

   @Generated
   public String name() {
      return this.name;
   }

   @Generated
   public String brandFormat() {
      return this.brandFormat;
   }

   public String formatBrand(String version) {
      return this.brandFormat.formatted(version);
   }

   public ClientBrandTemplate(String name, String brandFormat, String... versions) {
      this.name = name;
      this.brandFormat = brandFormat;
      this.versions = new ArrayList<>(Arrays.asList(versions));
   }

   @Generated
   public ArrayList<String> versions() {
      return this.versions;
   }
}
