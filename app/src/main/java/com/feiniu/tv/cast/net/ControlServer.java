package com.feiniu.tv.cast.net;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import fi.iki.elonen.NanoHTTPD;
import com.feiniu.tv.cast.core.CastSession;
import com.feiniu.tv.cast.core.DeviceDescription;
import com.feiniu.tv.cast.core.DidlLite;
import com.feiniu.tv.cast.core.Scpd;
import com.feiniu.tv.cast.core.SeekTarget;
import com.feiniu.tv.cast.core.SoapRequest;
import com.feiniu.tv.cast.core.SoapResponse;
import com.feiniu.tv.cast.core.SsdpTargets;

/**
 * The device's HTTP endpoint: device/SCPD documents, the SOAP control URL and
 * the GENA subscription URL.
 *
 * <p>Runs on NanoHTTPD, one request per thread, and turns control points'
 * requests into calls on the shared {@link CastSession}. It holds no Android
 * types so the whole control plane is exercised on the JVM; the player side
 * observes the same session and reacts to the changes made here.
 *
 * <p>Eventing is a stub: a subscription is answered with a SID that is never
 * notified, because nothing in the UI needs the event stream yet. The verbs
 * themselves must succeed, otherwise control points abandon the device.
 */
public class ControlServer extends NanoHTTPD {

    private static final String MIME_XML = "text/xml; charset=utf-8";
    private static final String CONTROL_PATH = "/control";
    private static final String DESC_PATH = "/desc.xml";
    private static final String AVT_PATH = "/avt.xml";
    private static final String RCS_PATH = "/rcs.xml";
    private static final String CMS_PATH = "/cms.xml";
    private static final String EVENT_TIMEOUT = "Second-1800";

    /**
     * The logger NanoHTTPD itself writes to. Its {@code LOG} field is private,
     * and JUL hands out one instance per name, so obtaining it by class name
     * gives the same logger: errors from here land wherever NanoHTTPD's do.
     */
    private static final Logger LOG = Logger.getLogger(NanoHTTPD.class.getName());

    private final String friendlyName;
    private final String uuid;
    private final CastSession session;

    /** Binds an ephemeral port, which is what the tests use. */
    public ControlServer(String friendlyName, String uuid, CastSession session) {
        this(friendlyName, uuid, session, 0);
    }

    /** Binds {@code port}; 0 picks an ephemeral one. */
    public ControlServer(String friendlyName, String uuid, CastSession session, int port) {
        super(port);
        this.friendlyName = friendlyName == null ? "" : friendlyName;
        this.uuid = uuid == null ? "" : uuid;
        this.session = session;
    }

    public int port() {
        return getListeningPort();
    }

    /**
     * URL to advertise in SSDP for this server. The caller supplies the
     * interface address it advertised on, so the host always matches the
     * network the control point can actually reach.
     */
    public String location(String host) {
        return "http://" + host + ":" + port() + DESC_PATH;
    }

    // ---------------------------------------------------------------- routing

    @Override
    public Response serve(IHTTPSession http) {
        try {
            // Field diagnosis: a sender that discovers us but never lists the
            // device either skipped the description fetch or rejects it, and
            // those two look identical from the phone.
            String ua = http.getHeaders().get("user-agent");
            // /control POSTs are the sender's 1s polling loop; logging them
            // floods the log buffer and hides the actions that matter.
            if (!CONTROL_PATH.equals(http.getUri())) {
                LOG.info("http " + http.getMethod() + " " + http.getUri()
                        + (ua == null ? "" : " ua=" + ua));
            }
            return route(http);
        } catch (RuntimeException e) {
            // A control plane that throws at a sender is worse than one that
            // answers a fault: the sender at least retries or moves on. Log it,
            // though - without the stack trace there is nothing to go on when
            // this happens on a TV in the field.
            LOG.log(Level.SEVERE, "control error", e);
            return xml(Response.Status.INTERNAL_ERROR, SoapResponse.fault(501, "Action Failed"));
        }
    }

    private Response route(IHTTPSession http) {
        String uri = http.getUri();
        Method method = http.getMethod();
        if (Method.GET.equals(method)) {
            if (DESC_PATH.equals(uri)) {
                return xml(DeviceDescription.build(friendlyName, uuid, ""));
            }
            if (AVT_PATH.equals(uri)) {
                return xml(Scpd.avTransport());
            }
            if (RCS_PATH.equals(uri)) {
                return xml(Scpd.renderingControl());
            }
            if (CMS_PATH.equals(uri)) {
                return xml(Scpd.connectionManager());
            }
            return plain(Response.Status.NOT_FOUND, "");
        }
        if (CONTROL_PATH.equals(uri)) {
            if (Method.POST.equals(method)) {
                return control(http);
            }
            if (Method.SUBSCRIBE.equals(method) || Method.UNSUBSCRIBE.equals(method)) {
                return event(http);
            }
        }
        return plain(Response.Status.METHOD_NOT_ALLOWED, "");
    }

    // ---------------------------------------------------------------- control

    private Response control(IHTTPSession http) {
        // NanoHTTPD 按 Content-Type 里的 charset 解码请求体,没写 charset 就按
        // US-ASCII,中文标题会整条变成替换字符。自己读字节再判断编码。
        String body = readBody(http);
        if (body == null) {
            return invalidAction();
        }

        // The body is authoritative; the header is what some senders rely on
        // when they post the envelope without a namespace on the action tag.
        SoapRequest request = SoapRequest.parse(body);
        String action = request.action();
        String serviceType = request.serviceType();
        if (action.isEmpty()) {
            String[] fromHeader = soapActionHeader(http.getHeaders().get("soapaction"));
            if (fromHeader != null) {
                action = fromHeader[1];
                if (serviceType.isEmpty()) {
                    serviceType = fromHeader[0];
                }
            }
        }
        if (action.isEmpty()) {
            return invalidAction();
        }
        if ("SetAVTransportURI".equals(action)) {
            LOG.info("soap SetAVTransportURI uri=" + request.arg("CurrentURI"));
        } else if (!"GetTransportInfo".equals(action) && !"GetPositionInfo".equals(action)
                && !"GetVolume".equals(action)) {
            LOG.info("soap " + action);
        }
        return dispatch(action, serviceTypeFor(action, serviceType), request);
    }

    /**
     * 读原始请求体:有 Content-Length 就自己按字节读,没有则退回 NanoHTTPD 的解析。
     */
    private static String readBody(IHTTPSession http) {
        String lenHeader = http.getHeaders().get("content-length");
        int size;
        try {
            size = Integer.parseInt(lenHeader == null ? "" : lenHeader.trim());
        } catch (NumberFormatException e) {
            size = -1;
        }
        if (size <= 0) {
            Map<String, String> files = new HashMap<String, String>();
            try {
                http.parseBody(files);
            } catch (IOException e) {
                return null;
            } catch (ResponseException e) {
                return null;
            }
            return files.get("postData");
        }
        byte[] data = new byte[size];
        int read = 0;
        try {
            InputStream in = http.getInputStream();
            while (read < size) {
                int n = in.read(data, read, size - read);
                if (n <= 0) {
                    break;
                }
                read += n;
            }
        } catch (IOException e) {
            return null;
        }
        return decodeBody(data, read);
    }

    /**
     * 有的发送端把中文按 GBK 发出来又不在 Content-Type 里写 charset,按 UTF-8 解
     * 会整段变成 U+FFFD(片名就是这样丢的)。先试 UTF-8,出现替换字符再按 GB18030 解。
     */
    private static String decodeBody(byte[] data, int len) {
        String utf8 = new String(data, 0, len, Charset.forName("UTF-8"));
        if (utf8.indexOf('\uFFFD') < 0) {
            return utf8;
        }
        return new String(data, 0, len, Charset.forName("GB18030"));
    }

    private Response dispatch(String action, String serviceType, SoapRequest request) {        if ("SetAVTransportURI".equals(action)) {
            session.setUri(request.arg("CurrentURI"), DidlLite.title(request.arg("CurrentURIMetaData")));
            return ok(serviceType, action);
        }
        if ("Play".equals(action)) {
            session.play();
            return ok(serviceType, action);
        }
        if ("Pause".equals(action)) {
            session.pause();
            return ok(serviceType, action);
        }
        if ("Stop".equals(action)) {
            session.stop();
            return ok(serviceType, action);
        }
        if ("Seek".equals(action)) {
            long ms = SeekTarget.parseMs(request.arg("Unit"), request.arg("Target"));
            // Units we cannot honour (TRACK_NR) and positions before any media
            // are answered successfully: senders resend, and the session has
            // nothing to move yet.
            if (ms >= 0 && session.hasMedia()) {
                session.seekTo(ms);
            }
            return ok(serviceType, action);
        }
        if ("GetPositionInfo".equals(action)) {
            String time = hhmmss(session.positionMs());
            return xml(SoapResponse.success(serviceType, action,
                    "<Track>1</Track>",
                    "<TrackDuration>" + hhmmss(session.durationMs()) + "</TrackDuration>",
                    // Declared by the SCPD, so it must be present even when empty.
                    "<TrackMetaData></TrackMetaData>",
                    "<TrackURI>" + esc(session.uri()) + "</TrackURI>",
                    "<RelTime>" + time + "</RelTime>",
                    "<AbsTime>" + time + "</AbsTime>",
                    "<RelCount>0</RelCount>",
                    "<AbsCount>0</AbsCount>"));
        }
        if ("GetTransportInfo".equals(action)) {
            return xml(SoapResponse.success(serviceType, action,
                    "<CurrentTransportState>" + transportState(session.state()) + "</CurrentTransportState>",
                    "<CurrentTransportStatus>OK</CurrentTransportStatus>",
                    "<CurrentSpeed>1</CurrentSpeed>"));
        }
        if ("GetVolume".equals(action)) {
            return xml(SoapResponse.success(serviceType, action,
                    "<CurrentVolume>" + session.volume() + "</CurrentVolume>"));
        }
        if ("SetVolume".equals(action)) {
            // Senders occasionally send garbage; that is not worth a fault,
            // the volume simply stays where it was.
            try {
                session.setVolume(Integer.parseInt(request.arg("DesiredVolume").trim()));
            } catch (NumberFormatException ignored) {
                // keep the current volume
            }
            return ok(serviceType, action);
        }
        if ("GetMute".equals(action)) {
            return xml(SoapResponse.success(serviceType, action, "<CurrentMute>0</CurrentMute>"));
        }
        if ("SetMute".equals(action)) {
            // No mute path in the session yet; report success so senders keep
            // trusting the other RenderingControl actions.
            return ok(serviceType, action);
        }
        if ("GetProtocolInfo".equals(action)) {
            return xml(SoapResponse.success(serviceType, action,
                    "<Source></Source>",
                    "<Sink>" + Scpd.sinkProtocols() + "</Sink>"));
        }
        if ("GetCurrentConnectionIDs".equals(action)) {
            return xml(SoapResponse.success(serviceType, action, "<ConnectionIDs>0</ConnectionIDs>"));
        }
        return unknownAction(action);
    }

    // ------------------------------------------------------------------ GENA

    private Response event(IHTTPSession http) {
        String sid = http.getHeaders().get("sid");
        if (sid == null || sid.trim().isEmpty()) {
            sid = "uuid:" + UUID.randomUUID().toString();
        } else {
            sid = sid.trim();
        }
        Response response = plain(Response.Status.OK, "");
        response.addHeader("SID", sid);
        response.addHeader("TIMEOUT", EVENT_TIMEOUT);
        return response;
    }

    // --------------------------------------------------------------- helpers

    /**
     * Splits a {@code SOAPACTION} header into service type and action. Accepts
     * the quoted form senders use ({@code "urn:#Play"}) as well as a bare one.
     * Returns null when the header is absent or carries no action.
     */
    private static String[] soapActionHeader(String header) {
        if (header == null) {
            return null;
        }
        String value = header.trim();
        if (value.length() > 1 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).trim();
        }
        int hash = value.lastIndexOf('#');
        if (hash < 0 || hash == value.length() - 1) {
            return null;
        }
        return new String[] {value.substring(0, hash).trim(), value.substring(hash + 1).trim()};
    }

    /**
     * Namespace to answer in when the request did not carry one. Without it the
     * response cannot bind its {@code u:} prefix and some senders drop the reply.
     *
     * <p>The value can come straight from a request header, so it is
     * attribute-escaped before it is spliced into {@code xmlns:u="..."}: a
     * malformed or hostile {@code SOAPACTION} must not be able to break the
     * response document.
     */
    private static String serviceTypeFor(String action, String declared) {
        if (declared != null && !declared.isEmpty()) {
            return escAttr(declared);
        }
        return escAttr(defaultServiceType(action));
    }

    private static String defaultServiceType(String action) {
        if ("GetVolume".equals(action) || "SetVolume".equals(action)
                || "GetMute".equals(action) || "SetMute".equals(action)) {
            return SsdpTargets.RCS;
        }
        if ("GetProtocolInfo".equals(action) || "GetCurrentConnectionIDs".equals(action)) {
            return SsdpTargets.CMS;
        }
        return SsdpTargets.AVT;
    }

    private static String transportState(CastSession.State state) {
        switch (state) {
            case PLAYING:
                return "PLAYING";
            case PAUSED:
                return "PAUSED_PLAYBACK";
            case PREPARING:
                return "TRANSITIONING";
            default:
                return "STOPPED";
        }
    }

    /** UPnP time format; negative or unknown values clamp to zero. */
    static String hhmmss(long ms) {
        long totalSeconds = ms / 1000L;
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return pad(hours) + ":" + pad(minutes) + ":" + pad(seconds);
    }

    private static String pad(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    /**
     * Escapes text for element content. Only the three characters that can
     * end or open markup are handled; quotes are legal here and left alone.
     */
    private static String esc(String text) {
        return escape(text, false);
    }

    /** Escapes text for a quoted attribute value, quotes included. */
    private static String escAttr(String text) {
        return escape(text, true);
    }

    private static String escape(String text, boolean attribute) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&') {
                out.append("&amp;");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else if (attribute && c == '"') {
                out.append("&quot;");
            } else if (attribute && c == '\'') {
                out.append("&apos;");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private Response ok(String serviceType, String action) {
        return xml(SoapResponse.success(serviceType, action));
    }

    private Response invalidAction() {
        LOG.warning("soap body unparseable -> fault 501");
        return xml(Response.Status.INTERNAL_ERROR, SoapResponse.fault(501, "Action Failed"));
    }

    private Response unknownAction(String action) {
        LOG.warning("unsupported action " + action + " -> fault 401");
        return xml(Response.Status.INTERNAL_ERROR, SoapResponse.fault(401, "Invalid Action"));
    }

    private Response xml(String body) {
        return xml(Response.Status.OK, body);
    }

    private Response xml(Response.IStatus status, String body) {
        return newFixedLengthResponse(status, MIME_XML, body);
    }

    private Response plain(Response.IStatus status, String body) {
        return newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, body);
    }
}
