package com.feiniu.tv.cast.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.feiniu.tv.cast.core.SsdpMessage;
import com.feiniu.tv.cast.core.SsdpResponse;
import com.feiniu.tv.cast.core.SsdpTargets;
import com.feiniu.tv.cast.util.LocalIp;

/**
 * Listens on the SSDP multicast group and answers M-SEARCH requests so that
 * phones and other control points can find this renderer.
 *
 * <p>Plain Java on purpose: the service owns the Android multicast lock and
 * only needs a thread, so keeping {@code android.*} out lets the reply logic be
 * tested on the JVM. The socket loop is deliberately thin - all the protocol
 * decisions live in {@link #handlePacket}, which takes the sender's address and
 * a sink instead of a socket.
 *
 * <p>Replies are unicast and immediate: a renderer has nothing to serialize, so
 * the randomized MX delay that a busy media server needs would only make the
 * TV show up later.
 */
public final class SsdpAgent implements Runnable {

    /** Where a reply goes. Lets the tests capture responses without a socket. */
    public interface ReplySink {
        void send(byte[] payload, InetAddress to, int port);
    }

    private static final Logger LOG = Logger.getLogger(SsdpAgent.class.getName());

    /** Throttle for the "who is probing" line: one per second is plenty. */
    private volatile long lastProbeLog;
    private volatile long lastReplyLog;

    private static final String GROUP = "239.255.255.250";
    private static final int DEFAULT_PORT = 1900;
    private static final int BUFFER_SIZE = 2048;
    /** Bounds how long {@link #stop()} can wait on a receive. */
    private static final int RECEIVE_TIMEOUT_MS = 1000;
    /** Back-off before re-binding / re-joining after a multicast failure. */
    private static final int RETRY_MS = 5000;
    /** UPnP says repeat announcements a few times; three is the usual number. */
    private static final int NOTIFY_REPEATS = 3;

    private final String uuid;
    private final int httpPort;
    private final int port;

    private volatile boolean running = true;
    private volatile MulticastSocket socket;
    /** Busy LANs produce noise; only the first real failure is worth a trace. */
    private volatile boolean failureLogged;

    public SsdpAgent(String uuid, int httpPort) {
        this(uuid, httpPort, DEFAULT_PORT);
    }

    /** {@code port} exists for tests; the TV always uses 1900. */
    public SsdpAgent(String uuid, int httpPort, int port) {
        this.uuid = uuid == null ? "" : uuid;
        this.httpPort = httpPort;
        this.port = port;
    }

    // ------------------------------------------------------------- socket loop

    @Override
    public void run() {
        while (running) {
            MulticastSocket active = null;
            try {
                active = open();
                socket = active;
                if (!join(active)) {
                    // Wired TVs and restrictive APs get here. Keep the thread
                    // alive: a network that comes up later is worth retrying.
                    logFailure("cannot join " + GROUP + ", will retry", null);
                    pause(RETRY_MS);
                    continue;
                }
                notifyAlive(active);
                receive(active);
            } catch (IOException e) {
                if (running) {
                    logFailure("ssdp socket failed, will retry", e);
                    pause(RETRY_MS);
                }
            } finally {
                close(active);
            }
        }
    }

    /**
     * Closes the socket, which is what unblocks {@code receive()}; the timeout
     * alone would take up to a second, closing makes it immediate.
     */
    public void stop() {
        running = false;
        MulticastSocket active = socket;
        if (active != null) {
            close(active);
        }
    }

    /**
     * Reuse address must be set before the bind, otherwise two renderers on the
     * same LAN cannot both hold 1900 - which is legal and normal for SSDP.
     */
    private MulticastSocket open() throws IOException {
        MulticastSocket fresh = new MulticastSocket((SocketAddress) null);
        try {
            fresh.setReuseAddress(true);
            fresh.bind(new InetSocketAddress(port));
            fresh.setSoTimeout(RECEIVE_TIMEOUT_MS);
            return fresh;
        } catch (IOException e) {
            close(fresh);
            throw e;
        }
    }

    /**
     * Joins on the platform's default interface and, when that is refused,
     * tries each interface explicitly. A TV with both Wi-Fi and Ethernet often
     * has the default pointing at the wrong one.
     */
    private boolean join(MulticastSocket active) {
        InetAddress group;
        try {
            group = InetAddress.getByName(GROUP);
        } catch (IOException e) {
            return false;
        }
        try {
            active.joinGroup(group);
            return true;
        } catch (IOException first) {
            // Fall through to the per-interface attempts.
        }
        for (NetworkInterface nif : interfaces()) {
            try {
                active.joinGroup(new InetSocketAddress(group, port), nif);
                return true;
            } catch (IOException ignored) {
                // Try the next one.
            }
        }
        return false;
    }

    private void receive(MulticastSocket active) {        ReplySink sink = socketSink(active);
        byte[] buffer = new byte[BUFFER_SIZE];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                active.receive(packet);
            } catch (SocketTimeoutException e) {
                continue; // idle second: re-check running, keep waiting
            } catch (IOException e) {
                if (running) {
                    logFailure("receive failed", e);
                }
                return;
            }
            handlePacket(packet.getData(), packet.getLength(),
                    packet.getAddress(), packet.getPort(), sink);
        }
    }

    private static InetAddress groupAddress() {
        try {
            return InetAddress.getByName(GROUP);
        } catch (IOException e) {
            return null;
        }
    }

    private ReplySink socketSink(final MulticastSocket active) {
        return new ReplySink() {
            @Override
            public void send(byte[] payload, InetAddress to, int toPort) {
                try {
                    active.send(new DatagramPacket(payload, payload.length, to, toPort));
                    // Some iOS stacks (fnOS / MRDLNA forks) bind their search
                    // socket to the multicast address, so a unicast reply never
                    // reaches them. A copy to the group costs one packet and
                    // is ignored by everybody else.
                    if (toPort == DEFAULT_PORT) {
                        InetAddress group = groupAddress();
                        if (group != null) {
                            active.send(new DatagramPacket(payload, payload.length,
                                    group, DEFAULT_PORT));
                        }
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastReplyLog > 1000) {
                        lastReplyLog = now;
                        LOG.info("reply sent to " + to.getHostAddress() + ":" + toPort
                                + " bytes=" + payload.length
                                + " head=" + new String(payload, 0, Math.min(90, payload.length),
                                        StandardCharsets.UTF_8).replace("\r\n", " | "));
                    }
                } catch (IOException e) {
                    logFailure("reply failed to " + to.getHostAddress() + ":" + toPort, e);
                }
            }
        };
    }

    // ------------------------------------------------------------- reply logic

    /**
     * Parses one datagram and sends one unicast reply per matching target.
     *
     * <p>Public and socket-free so the protocol behaviour is testable. Foreign
     * packets (NOTIFY from other devices, HTTP, garbage) are dropped silently:
     * on a busy LAN they are the majority and are not errors.
     */
    public void handlePacket(byte[] data, int len, InetAddress from, int fromPort, ReplySink sink) {
        if (data == null || from == null || sink == null || len <= 0 || len > data.length) {
            return;
        }
        SsdpMessage message;
        try {
            message = SsdpMessage.parse(new String(data, 0, len, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return;
        }
        if (message == null) {
            return;
        }
        // Which apps actually look for us: without this the only symptom is an
        // empty device list on the phone, which is indistinguishable from a
        // network problem.
        long now = System.currentTimeMillis();
        if (now - lastProbeLog > 1000) {
            lastProbeLog = now;
            LOG.info("probe st=" + message.searchTarget()
                    + " ua=" + message.headers().get("user-agent")
                    + " from=" + from.getHostAddress());
        }
        String location = location();
        for (SsdpTargets.Target target : SsdpTargets.matching(message.searchTarget(), uuid)) {
            byte[] payload = SsdpResponse.build(target.st(), target.usn(), location)
                    .getBytes(StandardCharsets.UTF_8);
            try {
                sink.send(payload, from, fromPort);
            } catch (RuntimeException e) {
                logFailure("reply failed", e);
            }
        }
    }

    private String location() {
        return "http://" + LocalIp.get() + ":" + httpPort + "/desc.xml";
    }

    // ------------------------------------------------------------- announce

    /**
     * Best-effort {@code ssdp:alive} burst on start. Send only caches benefit,
     * and losing a packet is harmless, so failures here are not retried - the
     * search reply path is what actually has to work.
     */
    private void notifyAlive(MulticastSocket active) {
        InetSocketAddress destination;
        try {
            destination = new InetSocketAddress(InetAddress.getByName(GROUP), port);
        } catch (IOException e) {
            return;
        }
        List<SsdpTargets.Target> targets = new ArrayList<SsdpTargets.Target>();
        targets.addAll(SsdpTargets.matching("upnp:rootdevice", uuid));
        targets.addAll(SsdpTargets.matching(SsdpTargets.DEVICE_TYPE, uuid));
        String location = location();
        for (int repeat = 0; repeat < NOTIFY_REPEATS; repeat++) {
            for (SsdpTargets.Target target : targets) {
                byte[] payload = notify(target.st(), target.usn(), location)
                        .getBytes(StandardCharsets.UTF_8);
                try {
                    active.send(new DatagramPacket(payload, payload.length, destination));
                } catch (IOException e) {
                    return;
                }
            }
        }
    }

    private String notify(String nt, String usn, String location) {
        return "NOTIFY * HTTP/1.1\r\n"
                + "HOST: " + GROUP + ":" + port + "\r\n"
                + "CACHE-CONTROL: max-age=1800\r\n"
                + "LOCATION: " + location + "\r\n"
                + "NT: " + nt + "\r\n"
                + "NTS: ssdp:alive\r\n"
                + "SERVER: " + SsdpResponse.SERVER + "\r\n"
                + "USN: " + usn + "\r\n"
                + "\r\n";
    }

    // ------------------------------------------------------------- utilities

    private static List<NetworkInterface> interfaces() {
        try {
            List<NetworkInterface> usable = new ArrayList<NetworkInterface>();
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nif.isUp() && !nif.isLoopback()) {
                    usable.add(nif);
                }
            }
            return usable;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** Sleeps in slices so {@link #stop()} is never held up by a retry. */
    private void pause(int millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (running && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void close(MulticastSocket active) {
        if (active == null) {
            return;
        }
        if (socket == active) {
            socket = null;
        }
        try {
            active.close();
        } catch (RuntimeException ignored) {
            // Already closed, or the stack is in a bad state; nothing to do.
        }
    }

    private void logFailure(String message, Throwable error) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        LOG.log(Level.WARNING, message, error);
    }
}
