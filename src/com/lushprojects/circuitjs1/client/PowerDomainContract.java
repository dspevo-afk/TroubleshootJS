package com.lushprojects.circuitjs1.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Range;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Scalar;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.State;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;

/** Immutable declarations over already resolved nets, never a connectivity solver. */
final class PowerDomainContract {
    static final int VERSION = 1;
    enum StorageRequirement { NONE, OBSERVATION_REQUIRED, UNKNOWN }
    interface Identified { String getId(); }

    static final class Reference implements Identified {
        private final String id, isolationId, earthId;
        private final boolean earthBondPermitted;
        Reference(String id, String isolationId, String earthId, boolean permitted) {
            this.id = id(id); this.isolationId = optionalId(isolationId);
            this.earthId = optionalId(earthId); this.earthBondPermitted = permitted;
            if (permitted && earthId == null)
                throw new IllegalArgumentException("Earth bond requires an explicit earth identity");
        }
        public String getId() { return id; }
        String getIsolationId() { return isolationId; }
        String getEarthId() { return earthId; }
        boolean isEarthBondPermitted() { return earthBondPermitted; }
    }
    static final class Rail implements Identified {
        private final String id, referenceId;
        private final StorageRequirement storageRequirement;
        Rail(String id, String referenceId, StorageRequirement storage) {
            this.id = id(id); this.referenceId = id(referenceId);
            if (storage == null) throw new IllegalArgumentException("Missing storage obligation");
            storageRequirement = storage;
        }
        public String getId() { return id; }
        String getReferenceId() { return referenceId; }
        StorageRequirement getStorageRequirement() { return storageRequirement; }
    }
    static final class Source implements Identified {
        private final String id, railId;
        private final Range voltageEnvelope;
        private final Scalar declaredCapacityAmps, seriesResistanceOhms, implementedCurrentLimitAmps;
        private final Drive drive;
        Source(String id, String railId, Range envelope, Scalar capacity,
                Scalar resistance, Scalar implementedLimit, Drive drive) {
            this.id = id(id); this.railId = id(railId);
            if (envelope == null || drive == null)
                throw new IllegalArgumentException("Missing source envelope/drive");
            nonnegative(capacity); nonnegative(resistance); nonnegative(implementedLimit);
            voltageEnvelope = envelope; declaredCapacityAmps = capacity;
            seriesResistanceOhms = resistance; implementedCurrentLimitAmps = implementedLimit;
            this.drive = drive;
        }
        public String getId() { return id; }
        String getRailId() { return railId; }
        Range getVoltageEnvelope() { return voltageEnvelope; }
        Scalar getDeclaredCapacityAmps() { return declaredCapacityAmps; }
        Scalar getSeriesResistanceOhms() { return seriesResistanceOhms; }
        Scalar getImplementedCurrentLimitAmps() { return implementedCurrentLimitAmps; }
        Drive getDrive() { return drive; }
    }
    static final class BackfeedPath implements Identified {
        private final String id, fromRailId, toRailId;
        private final Scalar maximumCurrentAmps;
        private final boolean permitted;
        BackfeedPath(String id, String from, String to, Scalar maximum, boolean permitted) {
            this.id = id(id); fromRailId = id(from); toRailId = id(to);
            nonnegative(maximum); maximumCurrentAmps = maximum; this.permitted = permitted;
            if (from.equals(to)) throw new IllegalArgumentException("Backfeed requires distinct rails");
        }
        public String getId() { return id; }
        String getFromRailId() { return fromRailId; }
        String getToRailId() { return toRailId; }
        Scalar getMaximumCurrentAmps() { return maximumCurrentAmps; }
        boolean isPermitted() { return permitted; }
    }

    private final String designId;
    private final Map<String, Reference> references;
    private final Map<String, Rail> rails;
    private final Map<String, Source> sources;
    private final Map<String, BackfeedPath> backfeedPaths;
    PowerDomainContract(String designId, Collection<Reference> references,
            Collection<Rail> rails, Collection<Source> sources, Collection<BackfeedPath> paths) {
        this.designId = id(designId);
        this.references = unique(references); this.rails = unique(rails);
        this.sources = unique(sources); this.backfeedPaths = unique(paths);
        if (this.references.isEmpty() || this.rails.isEmpty() || this.sources.isEmpty())
            throw new IllegalArgumentException("Power contract requires references, rails and sources");
        for (Rail rail : this.rails.values())
            if (!this.references.containsKey(rail.referenceId) || this.references.containsKey(rail.id))
                throw new IllegalArgumentException("Dangling or contradictory rail reference: " + rail.id);
        for (Source source : this.sources.values())
            if (!this.rails.containsKey(source.railId))
                throw new IllegalArgumentException("Source names an undeclared rail: " + source.id);
        for (BackfeedPath path : backfeedPaths.values()) {
            Rail from = this.rails.get(path.fromRailId), to = this.rails.get(path.toRailId);
            if (from == null || to == null) throw new IllegalArgumentException("Dangling backfeed path");
            Reference a = this.references.get(from.referenceId), b = this.references.get(to.referenceId);
            if (a.isolationId != null && b.isolationId != null && !a.isolationId.equals(b.isolationId))
                throw new IllegalArgumentException("Conductive backfeed contradicts isolation boundary");
        }
    }
    String getDesignId() { return designId; }
    Map<String, Reference> getReferences() { return references; }
    Map<String, Rail> getRails() { return rails; }
    Map<String, Source> getSources() { return sources; }
    Map<String, BackfeedPath> getBackfeedPaths() { return backfeedPaths; }
    Reference referenceForNet(String netId) {
        Reference direct = references.get(netId);
        Rail rail = rails.get(netId);
        return direct != null ? direct : rail == null ? null : references.get(rail.referenceId);
    }
    void requireSourceCoverage(Collection<String> actual) {
        if (actual == null) throw new IllegalArgumentException("Missing source inventory");
        TreeSet<String> ids = new TreeSet<String>();
        for (String value : actual)
            if (value == null || !ids.add(value)) throw new IllegalArgumentException("Duplicate source inventory");
        if (!ids.equals(sources.keySet())) throw new IllegalArgumentException("Power source coverage differs");
    }
    String toCanonical() {
        StringBuilder out = new StringBuilder("power-domain/1;");
        token(out, designId);
        for (Reference r : references.values()) {
            token(out, "reference"); token(out, r.id); token(out, r.isolationId);
            token(out, r.earthId); token(out, Boolean.toString(r.earthBondPermitted));
        }
        for (Rail r : rails.values()) {
            token(out, "rail"); token(out, r.id); token(out, r.referenceId);
            token(out, r.storageRequirement.name());
        }
        for (Source s : sources.values()) {
            token(out, "source"); token(out, s.id); token(out, s.railId);
            token(out, range(s.voltageEnvelope)); token(out, scalar(s.declaredCapacityAmps));
            token(out, scalar(s.seriesResistanceOhms)); token(out, scalar(s.implementedCurrentLimitAmps));
            token(out, s.drive.name());
        }
        for (BackfeedPath b : backfeedPaths.values()) {
            token(out, "backfeed"); token(out, b.id); token(out, b.fromRailId); token(out, b.toRailId);
            token(out, scalar(b.maximumCurrentAmps)); token(out, Boolean.toString(b.permitted));
        }
        return out.toString();
    }
    static String id(String value) {
        if (value == null || value.trim().length() == 0 || value.length() > 2048)
            throw new IllegalArgumentException("Invalid power identity");
        for (int i = 0; i < value.length(); i++)
            if (value.charAt(i) < 32 || value.charAt(i) == 127)
                throw new IllegalArgumentException("Control character in power identity");
        return value;
    }
    private static String optionalId(String value) { return value == null ? null : id(value); }
    static boolean finite(double x) { return !Double.isNaN(x) && !Double.isInfinite(x); }
    static void nonnegative(Scalar s) {
        if (s == null || (s.getState() == State.KNOWN && s.getValue() < 0))
            throw new IllegalArgumentException("Missing or negative power bound");
    }
    static String scalar(Scalar s) {
        return s.getState().name() + (s.getState() == State.KNOWN ? ":" +
            Long.toHexString(Double.doubleToLongBits(s.getValue())) : "");
    }
    static String range(Range r) {
        return r.getState().name() + (r.getState() == State.KNOWN ? ":" +
            Long.toHexString(Double.doubleToLongBits(r.getMinimum())) + ":" +
            Long.toHexString(Double.doubleToLongBits(r.getMaximum())) : "");
    }
    static void token(StringBuilder out, String value) {
        if (value == null) out.append("-1:");
        else out.append(value.length()).append(':').append(value);
        out.append(';');
    }
    private static <T extends Identified> Map<String,T> unique(Collection<T> values) {
        if (values == null) throw new IllegalArgumentException("Missing power declarations");
        TreeMap<String,T> result = new TreeMap<String,T>();
        for (T item : values)
            if (item == null || result.put(item.getId(), item) != null)
                throw new IllegalArgumentException("Duplicate/null power declaration");
        return Collections.unmodifiableMap(result);
    }
}
