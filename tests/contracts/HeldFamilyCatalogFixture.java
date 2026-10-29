package com.lushprojects.circuitjs1.client;

/** Native-process fixture for the real static parser; never part of GWT output. */
final class HeldFamilyCatalogFixture implements AutoCloseable {
    private final PlayerFamilyCatalog.RegistrationBoundary registration;
    private final String family;
    private final boolean previous;

    HeldFamilyCatalogFixture(String family) {
        this.family = family;
        try {
            java.lang.reflect.Field current = PlayerFamilyCatalog.class.getDeclaredField("CURRENT");
            current.setAccessible(true);
            registration = (PlayerFamilyCatalog.RegistrationBoundary) current.get(null);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot establish the native held-catalog fixture", failure);
        }
        if (!registration.isRegistered(family)) throw new AssertionError("Fixture requires a registered family");
        previous = registration.isNormalPlayerEnabled(family);
        registration.setNormalPlayerEnabled(family, false);
    }

    public void close() {
        registration.setNormalPlayerEnabled(family, previous);
        if (PlayerFamilyCatalog.isNormalPlayerEnabled(family) != previous)
            throw new AssertionError("Native held-catalog fixture failed to restore its exact prior state");
    }
}
