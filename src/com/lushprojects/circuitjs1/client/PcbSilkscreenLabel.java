package com.lushprojects.circuitjs1.client;

class PcbSilkscreenLabel {
    private final String id;
    private final String text;
    private final Rectangle bounds;
    private final int baselineX;
    private final int baselineY;
    private final int fontSize;
    private final boolean bold;
    private final String targetPadId;

    PcbSilkscreenLabel(String id, String text, Rectangle bounds, int fontSize,
            boolean bold, String targetPadId) {
        if (id == null || id.length() == 0 || text == null || text.length() == 0 ||
                bounds == null || bounds.width <= 0 || bounds.height <= 0 || fontSize <= 0)
            throw new IllegalArgumentException("Invalid PCB silkscreen label: " + id);
        this.id = id;
        this.text = text;
        this.bounds = new Rectangle(bounds);
        this.baselineX = bounds.x;
        this.baselineY = bounds.y + bounds.height - 3;
        this.fontSize = fontSize;
        this.bold = bold;
        this.targetPadId = targetPadId;
    }

    String getId() { return id; }
    String getText() { return text; }
    Rectangle getBounds() { return new Rectangle(bounds); }
    int getBaselineX() { return baselineX; }
    int getBaselineY() { return baselineY; }
    int getFontSize() { return fontSize; }
    boolean isBold() { return bold; }
    String getTargetPadId() { return targetPadId; }
}
