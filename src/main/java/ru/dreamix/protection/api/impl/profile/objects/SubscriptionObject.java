package ru.dreamix.protection.api.impl.profile.objects;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.Generated;

public class SubscriptionObject {
   private final String stringTill;
   private final long unixTimeStampTill;

   public SubscriptionObject(String stringTill) {
      this.stringTill = stringTill;
      this.unixTimeStampTill = LocalDate.parse(stringTill, DateTimeFormatter.ofPattern("dd.MM.yyyy")).atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
   }

   @Override
   public String toString() {
      return "SubscriptionObject{stringTill='" + this.stringTill + '\'' + ", unixTimeStampTill=" + this.unixTimeStampTill + '}';
   }

   @Generated
   public String getStringTill() {
      return this.stringTill;
   }

   @Generated
   public long getUnixTimeStampTill() {
      return this.unixTimeStampTill;
   }
}
