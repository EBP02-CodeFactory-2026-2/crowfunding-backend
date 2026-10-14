package com.crowdfunding.backend.exception;

import java.util.List;

public class MissingProjectFieldsException extends RuntimeException {

    private final List<String> missingFields;

    public MissingProjectFieldsException(String message, List<String> missingFields) {
        super(message);
        this.missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }

    public List<String> getMissingFields() {
        return missingFields;
    }

}
