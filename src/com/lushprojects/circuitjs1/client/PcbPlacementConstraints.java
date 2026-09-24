package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.TreeMap;
import java.util.Vector;

/** Role-supplied physical demands. No coordinates, selected fault or solver state. */
final class PcbPlacementConstraints {
    /** The qualified one-face identity used by every existing caller. */
    static final String DEFAULT_PHYSICAL_POLICY_ID = "THT_SINGLE_FACE";
    static final int DEFAULT_PHYSICAL_POLICY_VERSION = 1;

    enum Anchor { NONE, LEFT, RIGHT, EDGE }
    static final class Part {
        final String componentId, regionId, regionLabel, domainId;
        final Anchor anchor;
        final int accessMargin;
        Part(String componentId, String regionId, String regionLabel, String domainId, Anchor anchor, int accessMargin) {
            if (empty(componentId) || empty(regionId) || empty(regionLabel) || empty(domainId) || anchor == null ||
                    accessMargin < 16 || accessMargin > 500) throw new IllegalArgumentException("Invalid physical demand");
            this.componentId=componentId; this.regionId=regionId; this.regionLabel=regionLabel;
            this.domainId=domainId; this.anchor=anchor; this.accessMargin=accessMargin;
        }
    }
    static final class Barrier {
        final String firstDomain, secondDomain;
        final int clearance;
        Barrier(String first, String second, int clearance) {
            if (empty(first) || empty(second) || first.equals(second) || clearance < 20 || clearance > 1000)
                throw new IllegalArgumentException("Invalid physical domain barrier");
            firstDomain=first.compareTo(second)<0?first:second;
            secondDomain=first.compareTo(second)<0?second:first;
            this.clearance=clearance;
        }
        boolean separates(String a, String b) {
            return firstDomain.equals(a) && secondDomain.equals(b) || firstDomain.equals(b) && secondDomain.equals(a);
        }
    }
    private final TreeMap<String,Part> parts = new TreeMap<String,Part>();
    private final Vector<Barrier> barriers;
    final PcbCopperLayer routingLayer;
    final String physicalPolicyId;
    final int physicalPolicyVersion;
    PcbPlacementConstraints(Vector<Part> declarations, Vector<Barrier> barriers) {
        this(declarations,barriers,PcbCopperLayer.TOP);
    }
    PcbPlacementConstraints(Vector<Part> declarations, Vector<Barrier> barriers,PcbCopperLayer routingLayer) {
        this(declarations,barriers,routingLayer,DEFAULT_PHYSICAL_POLICY_ID,
            DEFAULT_PHYSICAL_POLICY_VERSION);
    }
    PcbPlacementConstraints(Vector<Part> declarations, Vector<Barrier> barriers,
            PcbCopperLayer routingLayer, String policyId, int policyVersion) {
        if (declarations == null || barriers == null) throw new IllegalArgumentException("Missing placement declarations");
        if(routingLayer==null) throw new IllegalArgumentException("Missing single copper layer");
        if (empty(policyId) || policyVersion < 1)
            throw new IllegalArgumentException("Invalid physical placement policy identity");
        this.routingLayer=routingLayer;
        this.physicalPolicyId=policyId;
        this.physicalPolicyVersion=policyVersion;
        this.barriers=new Vector<Barrier>(barriers);
        for (Part part : declarations) {
            if (part == null || parts.put(part.componentId, part) != null) throw new IllegalArgumentException("Duplicate placement part");
        }
        for (Barrier barrier : barriers) if (barrier == null) throw new IllegalArgumentException("Missing barrier");
        Collections.sort(this.barriers,new java.util.Comparator<Barrier>() {
            public int compare(Barrier a,Barrier b) {
                int first=a.firstDomain.compareTo(b.firstDomain);
                return first!=0?first:a.secondDomain.compareTo(b.secondDomain);
            }
        });
        for(int i=1;i<this.barriers.size();i++) {
            Barrier a=this.barriers.get(i-1),b=this.barriers.get(i);
            if(a.firstDomain.equals(b.firstDomain)&&a.secondDomain.equals(b.secondDomain))
                throw new IllegalArgumentException("Duplicate physical domain barrier");
        }
    }
    String getPhysicalPolicyId() { return physicalPolicyId; }
    int getPhysicalPolicyVersion() { return physicalPolicyVersion; }
    String getPhysicalPolicyIdentity() {
        return physicalPolicyId + "@" + physicalPolicyVersion;
    }
    String physicalPolicyIdentity() { return getPhysicalPolicyIdentity(); }
    boolean usesDefaultPhysicalPolicy() {
        return DEFAULT_PHYSICAL_POLICY_ID.equals(physicalPolicyId) &&
            DEFAULT_PHYSICAL_POLICY_VERSION == physicalPolicyVersion;
    }
    Part get(String id) { return parts.get(id); }
    Vector<Part> getParts() { return new Vector<Part>(parts.values()); }
    Vector<Barrier> getBarriers() { return new Vector<Barrier>(barriers); }
    int margin(String domain) {
        int margin=0;
        for (Barrier barrier : barriers) if (barrier.firstDomain.equals(domain) || barrier.secondDomain.equals(domain))
            margin=Math.max(margin, (barrier.clearance+1)/2);
        return margin;
    }
    void validate(TroubleshootBoard board) {
        if (parts.size()!=board.getComponentIds().size()) throw new IllegalArgumentException("Incomplete physical demand");
        TreeMap<String,String> labels=new TreeMap<String,String>();
        for (String id : board.getComponentIds()) {
            Part part=get(id);
            if (part==null) throw new IllegalArgumentException("Missing physical demand: "+id);
            String label=labels.put(part.regionId,part.regionLabel);
            if (label!=null && !label.equals(part.regionLabel)) throw new IllegalArgumentException("Conflicting public region label");
            if (part.anchor!=Anchor.NONE && !board.getComponent(id).getPhysicalPackage().isConnector())
                throw new IllegalArgumentException("An edge anchor must be a declared connector");
        }
        for (Barrier barrier : barriers) {
            boolean first=false,second=false;
            for (Part part : parts.values()) { first |= barrier.firstDomain.equals(part.domainId); second |= barrier.secondDomain.equals(part.domainId); }
            if (!first || !second) throw new IllegalArgumentException("Barrier references an absent domain");
            for (String netId : board.getNetIds()) {
                first=false; second=false;
                for (String pad : board.getNet(netId).getPadIds()) {
                    String domain=get(board.getPad(pad).getComponentId()).domainId;
                    first |= barrier.firstDomain.equals(domain); second |= barrier.secondDomain.equals(domain);
                }
                if (first && second) throw new IllegalArgumentException("One conductive net crosses isolated physical domains");
            }
        }
    }
    static PcbPlacementConstraints standard(TroubleshootBoard board) {
        Vector<String> ids=board.getComponentIds(); Collections.sort(ids);
        Vector<Part> parts=new Vector<Part>();
        for (String id : ids) {
            boolean connector=board.getComponent(id).getPhysicalPackage().isConnector();
            Anchor anchor=connector ? Anchor.EDGE : Anchor.NONE;
            parts.add(new Part(id,"circuit","Circuit","board",anchor,20));
        }
        return new PcbPlacementConstraints(parts,new Vector<Barrier>());
    }
    private static boolean empty(String s) { return s==null || s.length()==0; }
}
