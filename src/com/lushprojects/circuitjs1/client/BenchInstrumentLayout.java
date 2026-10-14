package com.lushprojects.circuitjs1.client;

/** Presentation furniture only; no changes to PCB geometry or electrical identity. */
final class BenchInstrumentLayout {
    static Rectangle meterHome(Rectangle board) {
        int height = Math.max(454, (int)Math.round(Math.max(board.height * .75, board.width * .30)));
        int width = (int)Math.round(height * 252.0 / 454);
        int gap = Math.max(32, (int)Math.round(height * 32.0 / 454));
        return new Rectangle(board.x - gap - width,
            board.y + Math.max(0, (board.height - height) / 2), width, height);
    }
}
