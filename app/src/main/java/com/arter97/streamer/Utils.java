package com.arter97.streamer;

public class Utils {
    public static void sleep(long mili) {
        try {
            Thread.sleep(mili);
        } catch (Exception e) {
            // Do nothing
        }
    }
}
