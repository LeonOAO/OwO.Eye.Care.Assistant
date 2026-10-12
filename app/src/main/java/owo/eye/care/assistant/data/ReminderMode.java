package owo.eye.care.assistant.data;

/** Persisted values 0 and 1 retain their original meaning for existing installations. */
public final class ReminderMode {
    public static final int PIN_LOCK = 0;
    public static final int VOICE_AND_BANNER = 1;
    public static final int VOICE = 2;
    public static final int BANNER = 3;

    private ReminderMode() { }

    public static int normalize(int mode) {
        return mode >= PIN_LOCK && mode <= BANNER ? mode : PIN_LOCK;
    }

    public static boolean usesPin(int mode) { return normalize(mode) == PIN_LOCK; }
    public static boolean usesVoice(int mode) {
        int value = normalize(mode);
        return value == VOICE || value == VOICE_AND_BANNER;
    }
    public static boolean usesBanner(int mode) {
        int value = normalize(mode);
        return value == BANNER || value == VOICE_AND_BANNER;
    }
    public static String label(int mode) {
        switch (normalize(mode)) {
            case VOICE: return "語音";
            case BANNER: return "上橫幅";
            case VOICE_AND_BANNER: return "語音及上橫幅";
            default: return "全螢幕 PIN 鎖定";
        }
    }
    /** Display order requested by the user, independent of persisted identifiers. */
    public static int[] selectionOrder() {
        return new int[] { VOICE, BANNER, PIN_LOCK, VOICE_AND_BANNER };
    }
}
