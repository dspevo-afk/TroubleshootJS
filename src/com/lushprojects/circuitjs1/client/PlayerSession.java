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

    int token() { return token; }
    Screen screen() { return screen; }
    Object owner() { return owner; }
    PlayerLaunchRequest request() { return request; }
    String message() { return message; }
    boolean accepts(int expected) { return token == expected; }

    int begin(PlayerLaunchRequest next) {
        if (next == null || screen == Screen.PREPARING || screen == Screen.RETEST)
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
        if (!accepts(expected) || screen != Screen.PREPARING || launched != pending || nextOwner == null ||
                !launched.accepts(accepted)) return false;
        pending = null;
        owner = nextOwner; request = accepted; screen = Screen.TICKET; message = ""; token++; return true;
    }

    boolean failed(int expected, boolean cancelled, String publicMessage) {
        if (!accepts(expected) || screen != Screen.PREPARING) return false;
        pending = null;
        screen = cancelled ? savedScreen : Screen.ERROR;
        message = publicMessage; token++; return true;
    }

    boolean enter(int expected, Screen next) {
        if (!accepts(expected) || screen == Screen.PREPARING || screen == Screen.RETEST || next == null) return false;
        if (next != Screen.MENU && next != Screen.WORKBENCH) return false;
        if (next == Screen.WORKBENCH && owner == null) return false;
        screen = next; message = ""; token++; return true;
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
        owner = currentOwner; request = launch; screen = Screen.WORKBENCH; message = ""; token++;
    }
}
