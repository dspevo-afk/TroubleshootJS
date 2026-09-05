package com.lushprojects.circuitjs1.client;

/**
 * Stable validation failure for the package-local functional block contract.
 *
 * <p>The exception deliberately carries field and entity identity separately
 * from its human-readable message.  Callers can therefore inspect validation
 * failures without parsing text.</p>
 */
final class BlockContractException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    enum Code {
        INVALID_ID,
        INVALID_VERSION,
        MISSING_DECLARATION,
        DUPLICATE_DECLARATION,
        DANGLING_REFERENCE,
        DUPLICATE_TERMINAL_ENDPOINT,
        CONTRADICTORY_ROLE,
        INVALID_ROLE,
        INVALID_ATTACHMENT,
        INVALID_PARAMETER
    }

    private final Code code;
    private final String fieldId;
    private final String entityId;

    BlockContractException(Code code, String fieldId, String entityId,
            String detail) {
        super(formatMessage(code, fieldId, entityId, detail));
        this.code = code;
        this.fieldId = fieldId;
        this.entityId = entityId;
    }

    Code getCode() {
        return code;
    }

    String getFieldId() {
        return fieldId;
    }

    String getEntityId() {
        return entityId;
    }

    private static String formatMessage(Code code, String fieldId,
            String entityId, String detail) {
        StringBuilder message = new StringBuilder();
        message.append(code == null ? "null" : code.name());
        if (fieldId != null) {
            message.append(" field=").append(fieldId);
        }
        if (entityId != null) {
            message.append(" entity=").append(entityId);
        }
        if (detail != null && detail.length() != 0) {
            message.append(": ").append(detail);
        }
        return message.toString();
    }
}
