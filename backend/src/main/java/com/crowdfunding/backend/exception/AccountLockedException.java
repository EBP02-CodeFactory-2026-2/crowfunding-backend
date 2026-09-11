package com.crowdfunding.backend.exception;

public class AccountLockedException extends RuntimeException {

    private final long remainingSeconds;

    public AccountLockedException(long remainingSeconds) {
        super("Cuenta bloqueada temporalmente. Intenta de nuevo en " + formatTime(remainingSeconds));
        this.remainingSeconds = remainingSeconds;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }

    private static String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;

        return minutes + " min " + secs + " segs";
    }
}
