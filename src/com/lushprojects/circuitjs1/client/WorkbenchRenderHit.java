package com.lushprojects.circuitjs1.client;

/** A renderer reports identity, never an electrical endpoint or a gameplay command. */
final class WorkbenchRenderHit {
    enum Kind { PAD, LEAD, LOOSE_TERMINAL, COMPONENT, PART, SLOT, TRAY }
    final Object boardIdentity, attachment;
    final Kind kind;
    final String id, secondaryId;
    final int terminal;
    WorkbenchRenderHit(Object boardIdentity, Object attachment, Kind kind, String id, String secondaryId, int terminal) {
        this.boardIdentity=boardIdentity; this.attachment=attachment; this.kind=kind;
        this.id=id; this.secondaryId=secondaryId; this.terminal=terminal;
    }
}
