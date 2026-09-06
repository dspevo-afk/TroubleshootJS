package com.lushprojects.circuitjs1.client;

/**
 * Typed failure raised while reading or constructing a Task 46 challenge
 * descriptor or its generation constraints.
 *
 * <p>The contract is deliberately independent of any generator or solver.
 * Callers can inspect the stable code and field without parsing a message.</p>
 */
final class ChallengeContractException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    enum Code {
        INVALID_ID,
        INVALID_VERSION,
        UNSUPPORTED_VERSION,
        MISSING_FIELD,
        DUPLICATE_DECLARATION,
        INVALID_ENCODING,
        UNKNOWN_FIELD,
        INVALID_RANGE,
        CONTRADICTORY_CONSTRAINT,
        UNSUPPORTED_ID,
        UNSUPPORTED_CONSTRAINT,
        INVALID_SCOPE,
        EMPTY_CANDIDATES,
        INVALID_BOUND
    }

    private final Code code;
    private final String fieldId;

    ChallengeContractException(Code code, String fieldId, String detail) {
        super(formatMessage(code, fieldId, detail));
        this.code = code;
        this.fieldId = fieldId;
    }

    Code getCode() {
        return code;
    }

    String getFieldId() {
        return fieldId;
    }

    static String id(String value, String fieldId) {
        try {
            return FunctionalBlockDescriptor.requireId(value, fieldId);
        } catch (BlockContractException ex) {
            throw new ChallengeContractException(Code.INVALID_ID, fieldId,
                    "ID must match [A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
        }
    }

    static void positiveVersion(int version, String fieldId) {
        if (version <= 0) {
            throw new ChallengeContractException(Code.INVALID_VERSION, fieldId,
                    "Version must be positive");
        }
    }

    static <T> T required(T value, String fieldId) {
        if (value == null) {
            throw new ChallengeContractException(Code.MISSING_FIELD, fieldId,
                    "Required declaration missing");
        }
        return value;
    }

    private static String formatMessage(Code code, String fieldId,
            String detail) {
        StringBuilder message = new StringBuilder();
        message.append(code == null ? "null" : code.name());
        if (fieldId != null) {
            message.append(" field=").append(fieldId);
        }
        if (detail != null && detail.length() != 0) {
            message.append(": ").append(detail);
        }
        return message.toString();
    }
}
