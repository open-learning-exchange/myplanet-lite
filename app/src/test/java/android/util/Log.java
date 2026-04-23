package android.util;

public class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    public static int v(String tag, String msg) { return 0; }
    public static int v(String tag, String msg, Throwable tr) { return 0; }
    public static int d(String tag, String msg) { return 0; }
    public static int d(String tag, String msg, Throwable tr) { return 0; }
    public static int i(String tag, String msg) { return 0; }
    public static int i(String tag, String msg, Throwable tr) { return 0; }
    public static int w(String tag, String msg) {
        if (msg != null && (msg.contains("Invalid ID 0x00000000") || msg.contains("No actions in intent filter"))) {
            return 0;
        }
        System.out.println("W/" + tag + ": " + msg);
        return 0;
    }
    public static int w(String tag, String msg, Throwable tr) { return w(tag, msg); }
    public static int w(String tag, Throwable tr) { return 0; }
    public static int e(String tag, String msg) {
        System.err.println("E/" + tag + ": " + msg);
        return 0;
    }
    public static int e(String tag, String msg, Throwable tr) { return e(tag, msg); }
    public static int println(int priority, String tag, String msg) {
        if (priority >= WARN) {
            return w(tag, msg);
        }
        return 0;
    }
    public static boolean isLoggable(String tag, int level) { return level >= INFO; }
    public static String getStackTraceString(Throwable tr) { return ""; }
}
