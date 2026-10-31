package com.feiniu.tv.cast.core;

/**
 * Builds SOAP 1.1 responses for the control endpoint.
 *
 * <p>Success responses repeat the action name with a {@code Response} suffix
 * inside the service namespace, which is what every control point matches on;
 * the out arguments are already-rendered {@code <Name>value</Name>} fragments
 * produced by the action implementations, so they are inserted verbatim
 * (the callers escape their text values).
 *
 * <p>Failures use the UPnP fault shape: a SOAP Fault whose detail carries the
 * {@code UPnPError} code, which is how control points tell a UPnP error from a
 * transport-level one.
 */
public final class SoapResponse {

    private static final String ENVELOPE_NS = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String ENCODING_STYLE = "http://schemas.xmlsoap.org/soap/encoding/";
    private static final String CONTROL_NS = "urn:schemas-upnp-org:control-1-0";

    private SoapResponse() {
    }

    /**
     * Successful action reply.
     *
     * @param serviceType namespace of the action, e.g. {@code urn:...:AVTransport:1}
     * @param action      action name without the {@code Response} suffix;
     *                    null or empty degrades to an empty element name
     * @param out1        first out argument fragment, inserted verbatim; may be null
     * @param out2        second out argument fragment, inserted verbatim; may be null
     */
    public static String success(String serviceType, String action, String out1, String out2) {
        return success(serviceType, action, new String[] {out1, out2});
    }

    /**
     * Successful action reply with any number of out arguments.
     *
     * <p>GetPositionInfo has six and GetTransportInfo three, so the fixed-arity
     * form above only exists for call sites that predate this overload.
     *
     * @param outs out argument fragments, inserted verbatim in order; null entries skipped
     */
    public static String success(String serviceType, String action, String... outs) {
        String type = serviceType == null ? "" : serviceType;
        String name = (action == null ? "" : action) + "Response";
        // Without a service type there is nothing to bind the u: prefix to, and
        // an empty namespace declaration would make the document malformed.
        String element = type.isEmpty() ? name : "u:" + name;
        String namespace = type.isEmpty() ? "" : " xmlns:u=\"" + type + "\"";
        StringBuilder args = new StringBuilder();
        if (outs != null) {
            for (String out : outs) {
                args.append(nullToEmpty(out));
            }
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<s:Envelope xmlns:s=\"" + ENVELOPE_NS + "\""
                + " s:encodingStyle=\"" + ENCODING_STYLE + "\">"
                + "<s:Body>"
                + "<" + element + namespace + ">"
                + args
                + "</" + element + ">"
                + "</s:Body>"
                + "</s:Envelope>\n";
    }

    /**
     * SOAP fault carrying a UPnP error.
     *
     * @param code        UPnP error code, e.g. 701 for "transition not available"
     * @param description human-readable text, XML-escaped; may be null
     */
    public static String fault(int code, String description) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<s:Envelope xmlns:s=\"" + ENVELOPE_NS + "\""
                + " s:encodingStyle=\"" + ENCODING_STYLE + "\">"
                + "<s:Body>"
                + "<s:Fault>"
                + "<faultcode>s:Client</faultcode>"
                + "<faultstring>UPnPError</faultstring>"
                + "<detail>"
                + "<UPnPError xmlns=\"" + CONTROL_NS + "\">"
                + "<errorCode>" + code + "</errorCode>"
                + "<errorDescription>" + DeviceDescription.escape(description) + "</errorDescription>"
                + "</UPnPError>"
                + "</detail>"
                + "</s:Fault>"
                + "</s:Body>"
                + "</s:Envelope>\n";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
