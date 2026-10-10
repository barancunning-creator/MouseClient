package ru.dreamix.protection.api.exceptions;

public class ApiException extends RuntimeException {
   public ApiException(String message) {
      super(message);
   }
}
