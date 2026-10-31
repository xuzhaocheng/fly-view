package com.feiniu.tv.cast.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/**
 * The address this device is reachable at from the LAN.
 *
 * <p>Control points fetch the device description over HTTP from the address
 * advertised in SSDP, so this must be a real interface address and not the
 * loopback. The first up, non-loopback, site-local IPv4 address wins; when
 * there is none (no network yet, or a weird AP setup) loopback is the fallback
 * so the caller still has something to log rather than a null.
 */
public final class LocalIp {

    private LocalIp() {
    }

    public static String get() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nif.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // No interfaces, or the platform refused to enumerate them.
        }
        return "127.0.0.1";
    }
}
