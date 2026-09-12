package com.enterpriseorders.backend.exception;

/**
 * Technical database exception (SRS §54, FT-001).
 *
 * Wraps low-level Oracle SQL/JDBC errors. Internal ORA-* codes are never
 * exposed to the API consumer; they are mapped by GlobalExceptionHandler
 * to a safe, consistent ApiError with a generic technical code.
 */
public class DatabaseException extends RuntimeException {

    private final String sqlState;

    public DatabaseException(String message, Throwable cause, String sqlState) {
        super(message, cause);
        this.sqlState = sqlState;
    }

    public String getSqlState() {
        return sqlState;
    }
}