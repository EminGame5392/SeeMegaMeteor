package ru.gdev.seemegameteor.util;

public class TimeUtil {
    public static String format(int seconds) {
        int s = Math.max(0, seconds);
        int h = s / 3600;
        int m = (s % 3600) / 60;
        int sec = s % 60;
        if (h > 0) return String.format("%02d:%02d:%02d", h, m, sec);
        return String.format("%02d:%02d", m, sec);
    }
}
