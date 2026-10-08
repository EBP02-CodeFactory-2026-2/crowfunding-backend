package com.crowdfunding.backend.exception;

public class ReplayAttackException extends RuntimeException {
    public ReplayAttackException(String message) { super(message); }
}
