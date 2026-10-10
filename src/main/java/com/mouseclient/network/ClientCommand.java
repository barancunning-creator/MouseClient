package com.mouseclient.network;

import java.util.ArrayList;

public interface ClientCommand {
   public void execute(String[] arguments);

   public String name();

   public default ArrayList<String> suggestions(String[] arguments) {
      return new ArrayList<>();
   }

   public String description();
}
