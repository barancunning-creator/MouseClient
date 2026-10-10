package ru.dreamix.protection.api.bridge;

import ru.dreamix.protection.api.impl.profile.objects.RoleObject;

public class ProfileNativeBridge {
   public static native String getUsername();

   public static native String getSubscriptionTill();

   public static native int getUserIdentifier();

   public static native RoleObject getRole();

   public static native String getLaunchedProduct();
}
