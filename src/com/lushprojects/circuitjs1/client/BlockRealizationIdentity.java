package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.ChallengeDescriptor.VersionedId;

/**
 * Immutable identity for one realized functional block.
 *
 * <p>The identity separates the semantic role selected by the device from
 * the provider and descriptor variant that currently implements it.</p>
 */
final class BlockRealizationIdentity {
    static final int VERSION = 1;
    static final int VERSION1 = 1;
    static final String ENVELOPE = "tsj-realization-v1";

    private final String instanceKey;
    private final VersionedId role;
    private final VersionedId provider;
    private final VersionedId variant;

    BlockRealizationIdentity(String instanceKey, VersionedId role,
            VersionedId provider, VersionedId variant) {
        this.instanceKey = FunctionalBlockDescriptor.requireId(
                instanceKey, "instanceKey");
        if (role == null || provider == null || variant == null) {
            throw new BlockContractException(
                    BlockContractException.Code.MISSING_DECLARATION,
                    "realization", this.instanceKey,
                    "Role, provider, and variant identities are required");
        }
        this.role = role;
        this.provider = provider;
        this.variant = variant;
    }

    String getInstanceKey() {
        return instanceKey;
    }

    VersionedId getRole() {
        return role;
    }

    VersionedId getProvider() {
        return provider;
    }

    VersionedId getVariant() {
        return variant;
    }

    String toCanonical() {
        return ENVELOPE + "|" + instanceKey + "|" + role.toCanonical()
                + "|" + provider.toCanonical() + "|"
                + variant.toCanonical();
    }
    static BlockRealizationIdentity parse(String value) {
        if (value == null) {
            throw invalidEncoding("realization", "Identity is required");
        }
        String[] fields = splitFields(value);
        if (fields.length != 5 || !ENVELOPE.equals(fields[0])) {
            throw invalidEncoding("realization", "Expected " + ENVELOPE
                    + "|instance|role@version|provider@version|variant@version");
        }
        try {
            return new BlockRealizationIdentity(
                    FunctionalBlockDescriptor.requireId(fields[1], "instanceKey"),
                    VersionedId.parse(fields[2], "role"),
                    VersionedId.parse(fields[3], "provider"),
                    VersionedId.parse(fields[4], "variant"));
        } catch (IllegalArgumentException invalid) {
            if (invalid instanceof ChallengeContractException) {
                throw invalid;
            }
            throw invalidEncoding("realization", invalid.getMessage());
        }
    }

    private static String[] splitFields(String value) {
        java.util.ArrayList<String> fields =
                new java.util.ArrayList<String>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == '|') {
                fields.add(value.substring(start, index));
                start = index + 1;
            }
        }
        return fields.toArray(new String[fields.size()]);
    }

    private static ChallengeContractException invalidEncoding(String field,
            String detail) {
        return new ChallengeContractException(
                ChallengeContractException.Code.INVALID_ENCODING,
                field, detail);
    }

    @Override
    public String toString() {
        return toCanonical();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockRealizationIdentity)) {
            return false;
        }
        BlockRealizationIdentity that = (BlockRealizationIdentity) other;
        return instanceKey.equals(that.instanceKey)
                && role.equals(that.role)
                && provider.equals(that.provider)
                && variant.equals(that.variant);
    }

    @Override
    public int hashCode() {
        int result = instanceKey.hashCode();
        result = 31 * result + role.hashCode();
        result = 31 * result + provider.hashCode();
        result = 31 * result + variant.hashCode();
        return result;
    }
}
