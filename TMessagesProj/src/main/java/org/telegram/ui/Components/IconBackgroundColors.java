package org.telegram.ui.Components;

public enum IconBackgroundColors {
    BLUE(0xFF01BA53, 0xFF0E381F),
    BLUE_ALT(0xFF01BA53, 0xFF0E381F),
    BLUE_DEEP(0xFF10D067, 0xFF01BA53),
    BLUE_LIGHT(0xFF01BA53, 0xFF10D067),

    ORANGE(0xFFF09F1B, 0xFFE18A11),
    ORANGE_DEEP(0xFFF28B31, 0xFFE26314),

    GREEN(0xFF55CA47, 0xFF27B434),
    RED(0xFFF45255, 0xFFDF3955),
    CYAN(0xFF10D067, 0xFF01BA53),
    PURPLE(0xFFC46EF4, 0xFF9F55DF),
    GRAY(0xFF8699AA, 0xFF6E8397);

    public final int top;
    public final int bottom;

    private IconBackgroundColors(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
    }
}
