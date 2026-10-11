package com.pharmacy.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for validating user input on forms.
 * Collect all errors and return them at once for better UX.
 */
public class InputValidator {

    private final List<String> errors = new ArrayList<>();

    public InputValidator requireNonEmpty(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            errors.add(fieldName + " is required.");
        }
        return this;
    }

    public InputValidator requirePositiveDouble(String value, String fieldName) {
        try {
            double d = Double.parseDouble(value.trim());
            if (d < 0) errors.add(fieldName + " must be a positive number.");
        } catch (NumberFormatException e) {
            errors.add(fieldName + " must be a valid number.");
        }
        return this;
    }

    public InputValidator requirePositiveInt(String value, String fieldName) {
        try {
            int i = Integer.parseInt(value.trim());
            if (i < 0) errors.add(fieldName + " must be a positive integer.");
        } catch (NumberFormatException e) {
            errors.add(fieldName + " must be a valid integer.");
        }
        return this;
    }

    public InputValidator requireMinLength(String value, String fieldName, int min) {
        if (value == null || value.trim().length() < min) {
            errors.add(fieldName + " must be at least " + min + " characters.");
        }
        return this;
    }

    public InputValidator requireMaxValue(double value, String fieldName, double max) {
        if (value > max) {
            errors.add(fieldName + " cannot exceed " + max + ".");
        }
        return this;
    }

    public InputValidator requireEmail(String value, String fieldName) {
        if (value != null && !value.trim().isEmpty() && !value.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            errors.add(fieldName + " is not a valid email address.");
        }
        return this;
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public String getErrorMessage() {
        return String.join("\n", errors);
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public void throwIfInvalid() throws IllegalArgumentException {
        if (!isValid()) throw new IllegalArgumentException(getErrorMessage());
    }
}
