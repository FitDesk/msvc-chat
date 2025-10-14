package com.msvcchat.helpers;

public class ChatHelper {
    public static String generateInitials(String firstName, String lastName) {
        StringBuilder initials = new StringBuilder();

        if (firstName != null && !firstName.isBlank()) {
            initials.append(firstName.charAt(0));
        }

        if (lastName != null && !lastName.isBlank()) {
            initials.append(lastName.charAt(0));
        }

        return !initials.isEmpty() ? initials.toString().toUpperCase() : "?";
    }


    public static String generateInitialsFromEmail(String email) {
        if (email != null && !email.isBlank()) {
            return email.substring(0, 1).toUpperCase();
        }
        return "?";
    }

    public static String buildDisplayName(String firstName, String lastName) {
        if (firstName != null && !firstName.isBlank() &&
                lastName != null && !lastName.isBlank()) {
            return firstName + " " + lastName;
        }

        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }

        if (lastName != null && !lastName.isBlank()) {
            return lastName;
        }

        return "Usuario sin nombre";
    }
}
