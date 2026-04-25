package android.util;

public class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    private static boolean shouldSilence(String tag, String msg) {
        String t = tag == null ? "" : tag.toLowerCase();
        String m = msg == null ? "" : msg.toLowerCase();
        return t.contains("packageparser") || m.contains("packageparser") ||
               t.contains("intent filter") || m.contains("intent filter") ||
               t.contains("intent-filter") || m.contains("intent-filter") ||
               t.contains("assetmanager") || t.contains("resources") ||
               m.contains("invalid id 0x00000000") || m.contains("attribute") ||
               m.contains("no action");
    }

    public static int v(String tag, String msg) { return 0; }
    public static int v(String tag, String msg, Throwable tr) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable tr) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int i(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, String msg) {
        if (shouldSilence(tag, msg)) return 0;
        // System.out.println("W/" + tag + ": " + msg);
        return 0;
    }
    public static int w(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, Throwable tr) { return 0; }
    public static int e(String tag, String msg) {
        if (shouldSilence(tag, msg)) return 0;
        // System.err.println("E/" + tag + ": " + msg);
        return 0;
    }
    public static int e(String tag, String msg, Throwable tr) { return 0; }
    public static int wtf(String tag, String msg) { return 0; }
    public static int wtf(String tag, Throwable tr) { return 0; }
    public static int wtf(String tag, String msg, Throwable tr) { return 0; }
    public static int println(int priority, String tag, String msg) {
        return 0;
    }
    public static boolean isLoggable(String tag, int level) { return level >= INFO; }
    public static String getStackTraceString(Throwable tr) { return ""; }
}
