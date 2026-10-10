package com.mouseclient.annotation;

import com.mouseclient.modules.impl.misc.ModuleCategory;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleInfo {
   public String name();

   public ModuleCategory category();

   public String description() default "";
}
