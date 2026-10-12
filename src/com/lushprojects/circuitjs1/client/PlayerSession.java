package com.lushprojects.circuitjs1.client;

/** Small session state machine. Tokens revoke callbacks when their screen/owner changes. */
final class PlayerSession {
    enum Screen { MENU, PREPARING, TICKET, WORKBENCH, RETEST, RESULTS, ERROR }
    private Screen screen = Screen.MENU;
    private int token = 1;
    private Object owner;
    private PlayerLaunchRequest request;
    private PlayerLaunchRequest pending;
    private Screen savedScreen;
    private String message = "";
    private boolean qualification;
    private boolean qualificationRestorePending;

    int token() { return token; }
    Screen screen() { return screen; }
    Object owner() { return owner; }
    PlayerLaunchRequest request() { return request; }
    String message() { return message; }
    boolean accepts(int expected) { return token == expected; }
    boolean isQualification() { return qualification; }

    int begin(PlayerLaunchRequest next) {
        if (qualification || next == null || screen == Screen.PREPARING || screen == Screen.RETEST)
            throw new IllegalStateException("Session is busy");
        savedScreen = owner == null ? Screen.MENU : Screen.WORKBENCH;
        pending = next;
        screen = Screen.PREPARING; message = "Preparing and checking the board…";
        return ++token;
    }

    boolean prepared(int expected, PlayerLaunchRequest next, Object nextOwner) {
        return prepared(expected, next, next, nextOwner);
    }
    boolean prepared(int expected, PlayerLaunchRequest launched, PlayerLaunchRequest accepted, Object nextOwner) {
        if (qualificationRestorePending || !accepts(expected) || screen != Screen.PREPARING ||
                launched != pending || nextOwner == null || !launched.accepts(accepted)) return false;
        pending = null;
        owner = nextOwner; request = accepted; screen = Screen.TICKET; message = ""; token++; return true;
    }

    int beginQualificationRestore(Object expectedOwner) {
        if (!qualification || expectedOwner == null || owner != expectedOwner)
            throw new IllegalStateException("Qualification restore belongs to a different owner");
        if (screen != Screen.MENU && screen != Screen.WORKBENCH &&
                screen != Screen.RESULTS && screen != Screen.ERROR)
            throw new IllegalStateException("Qualification session is busy");
        savedScreen = Screen.WORKBENCH;
        qualificationRestorePending = true;
        screen = Screen.PREPARING; message = "Preparing and checking the board.";
        return ++token;
    }

    boolean restoredQualification(int expected, Object expectedOwner, Object nextOwner) {
        if (!accepts(expected) || !qualification || !qualificationRestorePending ||
                screen != Screen.PREPARING || expectedOwner == null || owner != expectedOwner ||
                nextOwner == null || nextOwner == expectedOwner) return false;
        qualificationRestorePending = false;
        pending = null; owner = nextOwner; request = null;
        screen = Screen.WORKBENCH; message = ""; token++; return true;
    }

    boolean failed(int expected, boolean cancelled, String publicMessage) {
        if (!accepts(expected) || screen != Screen.PREPARING) return false;
        pending = null; qualificationRestorePending = false;
        screen = cancelled ? savedScreen : Screen.ERROR;
        message = publicMessage; token++; return true;
    }

    boolean enter(int expected, Screen next) {
        if (!accepts(expected) || screen == Screen.PREPARING || screen == Screen.RETEST || next == null) return false;
        if (next != Screen.MENU && next != Screen.WORKBENCH) return false;
        if (next == Screen.WORKBENCH && owner == null) return false;
        screen = next; message = ""; token++; return true;
    }

    boolean resumeQualification(int expected, Object currentOwner, boolean completed) {
        if (!qualification || currentOwner == null || owner != currentOwner ||
                !enter(expected, Screen.WORKBENCH)) return false;
        if (completed) screen = Screen.RESULTS;
        return true;
    }

    int retest(int expected, Object currentOwner) {
        if (!accepts(expected) || currentOwner != owner || screen != Screen.WORKBENCH)
            throw new IllegalStateException("Retest belongs to a different session");
        screen = Screen.RETEST; return ++token;
    }

    boolean retested(int expected, Object currentOwner, boolean completed, String publicMessage) {
        if (!accepts(expected) || screen != Screen.RETEST || owner != currentOwner) return false;
        screen = completed ? Screen.RESULTS : Screen.WORKBENCH;
        message = publicMessage; token++; return true;
    }

    void adopt(Object currentOwner, PlayerLaunchRequest launch) {
        if (currentOwner == null || launch == null) throw new IllegalArgumentException("Missing public session owner");
        if (qualification) throw new IllegalStateException("Qualification owns this session");
        owner = currentOwner; request = launch; screen = Screen.WORKBENCH; message = ""; token++;
    }

    /** An already qualified board has no normal replay or available difficulty. */
    void adoptQualification(Object currentOwner) {
        if (currentOwner == null) throw new IllegalArgumentException("Missing qualified session owner");
        if (qualification || owner != null || screen == Screen.PREPARING || screen == Screen.RETEST)
            throw new IllegalStateException("Session already belongs to an owner");
        owner = currentOwner; request = null; pending = null; qualification = true;
        qualificationRestorePending = false;
        screen = Screen.TICKET; message = ""; token++;
    }

    /** Release only the captured owner and invalidate every retained callback. */
    void revoke(Object expectedOwner) {
        if (owner == null || owner != expectedOwner)
            throw new IllegalStateException("Session release belongs to a different owner");
        owner = null; request = null; pending = null; savedScreen = null; qualification = false;
        qualificationRestorePending = false;
        screen = Screen.MENU; message = ""; token++;
    }
}
