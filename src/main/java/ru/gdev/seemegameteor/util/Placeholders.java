package ru.gdev.seemegameteor.util;

import ru.gdev.seemegameteor.event.MegaMeteorEventManager;

public class Placeholders {
    public static String apply(String s, MegaMeteorEventManager mgr) {
        String out = s;
        String[] keys = new String[]{"{status}","{time_befote_start}","{time_before_start}","{time_before_open}","{time_before_end}","{x}","{y}","{z}","{activator}"};
        for (String k : keys) {
            out = out.replace(k, mgr.placeholder(k));
        }
        return out;
    }
}
