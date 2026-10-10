package ru.dreamix.protection.api.impl.profile.models;

import lombok.Generated;
import ru.dreamix.protection.api.impl.profile.objects.RoleObject;
import ru.dreamix.protection.api.impl.profile.objects.SubscriptionObject;

public class ProfileInformationModel {
   private final String name;
   private final SubscriptionObject subscriptionObject;
   private final int userIdentifier;
   private final RoleObject role;
   private final String launchedProduct;

   @Override
   public String toString() {
      return "ProfileInformationModel{name='"
         + this.name
         + '\''
         + ", subscriptionObject='"
         + this.subscriptionObject
         + '\''
         + ", userIdentifier="
         + this.userIdentifier
         + ", role="
         + this.role
         + ", launchedProduct="
         + this.launchedProduct
         + '}';
   }

   @Generated
   public String getName() {
      return this.name;
   }

   @Generated
   public SubscriptionObject getSubscriptionObject() {
      return this.subscriptionObject;
   }

   @Generated
   public int getUserIdentifier() {
      return this.userIdentifier;
   }

   @Generated
   public RoleObject getRole() {
      return this.role;
   }

   @Generated
   public String getLaunchedProduct() {
      return this.launchedProduct;
   }

   @Generated
   public ProfileInformationModel(String name, SubscriptionObject subscriptionObject, int userIdentifier, RoleObject role, String launchedProduct) {
      this.name = name;
      this.subscriptionObject = subscriptionObject;
      this.userIdentifier = userIdentifier;
      this.role = role;
      this.launchedProduct = launchedProduct;
   }
}
