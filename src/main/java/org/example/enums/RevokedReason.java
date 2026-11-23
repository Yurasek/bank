package org.example.enums;

public enum RevokedReason {
    LOGOUT,
    PASSWORD_CHANGE,
    NEW_LOGIN,           // user logged in elsewhere
    SUSPICIOUS_ACTIVITY, // rotation attack detected
    ADMIN_ACTION,        // manual revocation
    EXPIRED              // cleanup job
}
