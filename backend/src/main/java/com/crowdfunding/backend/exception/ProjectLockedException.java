package com.crowdfunding.backend.exception;

public class ProjectLockedException extends RuntimeException {
    public ProjectLockedException(String message) { super(message); }
}
