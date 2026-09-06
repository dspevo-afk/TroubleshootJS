package com.lushprojects.circuitjs1.client;

/** Malformed declaration, distinct from a well-formed but incompatible proposal. */
final class ElectricalContractException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    enum Code {
        INVALID_ID, MISSING_FIELD, INVALID_RANGE, INVALID_SCALAR,
        INVALID_THRESHOLD, CONTRADICTORY_FIELD, INVALID_REFERENCE,
        INVALID_ATTACHMENT, DUPLICATE_DECLARATION, INVALID_CONNECTION, INVALID_ADAPTER
    }
    private final Code code;
    private final String fieldId;
    private final String entityId;

    ElectricalContractException(Code code, String fieldId, String entityId, String detail) {
        super(code + " field=" + fieldId + " entity=" + entityId + ": " + detail);
        this.code = code;
        this.fieldId = fieldId;
        this.entityId = entityId;
    }
    Code getCode() { return code; }
    String getFieldId() { return fieldId; }
    String getEntityId() { return entityId; }

    static String id(String value, String field) {
        try { return FunctionalBlockDescriptor.requireId(value, field); }
        catch (BlockContractException ex) {
            throw new ElectricalContractException(Code.INVALID_ID, field, value, "Invalid semantic ID");
        }
    }
    static <T> T required(T value, String field, String entity) {
        if (value == null) throw new ElectricalContractException(Code.MISSING_FIELD, field, entity, "Required declaration missing");
        return value;
    }
}
