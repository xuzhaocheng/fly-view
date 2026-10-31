package com.feiniu.tv.cast.core;

public final class SsdpResponse {

    public static final String SERVER = "Linux/3.14 UPnP/1.0 HiCast/0.1";

    private SsdpResponse() {
    }

    public static String build(String st, String usn, String location) {
        return "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "LOCATION: " + location + "\r\n" +
                "SERVER: " + SERVER + "\r\n" +
                "ST: " + st + "\r\n" +
                "USN: " + usn + "\r\n" +
                "\r\n";
    }
}
