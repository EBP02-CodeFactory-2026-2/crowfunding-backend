package com.crowdfunding.backend.exception;

public class InvalidContributionAmountException extends RuntimeException {
    public InvalidContributionAmountException(String message) { super(message); }
}
