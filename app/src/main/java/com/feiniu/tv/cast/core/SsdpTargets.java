package com.feiniu.tv.cast.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SsdpTargets {

    public static final String DEVICE_TYPE = "urn:schemas-upnp-org:device:MediaRenderer:1";
    public static final String AVT = "urn:schemas-upnp-org:service:AVTransport:1";
    public static final String RCS = "urn:schemas-upnp-org:service:RenderingControl:1";
    public static final String CMS = "urn:schemas-upnp-org:service:ConnectionManager:1";

    public static final class Target {
        private final String st;
        private final String usn;

        Target(String st, String usn) {
            this.st = st;
            this.usn = usn;
        }

        public String st() { return st; }

        public String usn() { return usn; }
    }

    private SsdpTargets() {
    }

    public static List<Target> all(String uuid) {
        List<Target> list = new ArrayList<>();
        list.add(new Target("upnp:rootdevice", "uuid:" + uuid + "::upnp:rootdevice"));
        list.add(new Target("uuid:" + uuid, "uuid:" + uuid));
        list.add(new Target(DEVICE_TYPE, "uuid:" + uuid + "::" + DEVICE_TYPE));
        list.add(new Target(AVT, "uuid:" + uuid + "::" + AVT));
        list.add(new Target(RCS, "uuid:" + uuid + "::" + RCS));
        list.add(new Target(CMS, "uuid:" + uuid + "::" + CMS));
        return list;
    }

    public static List<Target> matching(String st, String uuid) {
        if (st == null || st.isEmpty()) {
            return Collections.emptyList();
        }
        if ("ssdp:all".equalsIgnoreCase(st)) {
            return all(uuid);
        }
        for (Target t : all(uuid)) {
            if (t.st().equalsIgnoreCase(st)) {
                return Collections.singletonList(t);
            }
        }
        return Collections.emptyList();
    }
}
