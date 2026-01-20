package pe.nanamochi.poc_osu_server.utils;

public class Validation {
    private static final String USERNAME_REGEX = "^[\\w \\[\\]-]{2,15}$";
    private static final String EMAIL_REGEX = "\"^[^@\\s]{1,200}@[^@\\s\\.]{1,30}(?:\\.[^@\\.\\s]{2,24})+$";

    private Validation() {
    }

    public static boolean isValidUsername(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }
        if (username.contains(" ") && username.contains("_")) {
            return false;
        }
        return username.matches(USERNAME_REGEX);
    }

    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return email.matches(EMAIL_REGEX);
    }

    public static boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        if (password.length() < 8 || password.length() > 32) {
            return false;
        }
        long uniqueChars = password.chars().distinct().count();
        return uniqueChars > 3;
    }
}
