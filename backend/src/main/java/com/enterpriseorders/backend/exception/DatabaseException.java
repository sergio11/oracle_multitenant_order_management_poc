package com.enterpriseorders.backend.exception;

public class DatabaseException extends RuntimeException {

    private final String code;
    private final String sqlState;

    public DatabaseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.sqlState = null;
    }

    public DatabaseException(String message, Throwable cause, String sqlState) {
        super(message, cause);
        this.code = "DATABASE_ERROR";
        this.sqlState = sqlState;
    }

    public String getCode() {
        return code;
    }

    public String getSqlState() {
        return sqlState;
    }
}
