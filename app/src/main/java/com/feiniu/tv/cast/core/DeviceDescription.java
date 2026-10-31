package com.feiniu.tv.cast.core;

/**
 * Builds the UPnP device description served at /desc.xml.
 *
 * <p>All service URLs are relative; senders resolve them against the LOCATION
 * they got from SSDP, which keeps the document valid for whatever interface
 * address the device happened to advertise on.
 */
public final class DeviceDescription {

    private static final String DEVICE_NS = "urn:schemas-upnp-org:device-1-0";
    private static final String DLNA_NS = "urn:schemas-dlna-org:device-1-0";

    private DeviceDescription() {
    }

    /**
     * @param friendlyName shown to the user, XML-escaped
     * @param uuid         device UUID, without the {@code uuid:} prefix
     * @param baseUrl      currently unused: every URL in the document is relative
     */
    public static String build(String friendlyName, String uuid, String baseUrl) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<root xmlns=\"" + DEVICE_NS + "\" xmlns:dlna=\"" + DLNA_NS + "\">\n"
                + "<specVersion><major>1</major><minor>0</minor></specVersion>\n"
                + "<device>\n"
                + "<deviceType>" + SsdpTargets.DEVICE_TYPE + "</deviceType>\n"
                + "<friendlyName>" + escape(friendlyName) + "</friendlyName>\n"
                + "<manufacturer>HiCast</manufacturer>\n"
                + "<modelDescription>HiCast DLNA MediaRenderer</modelDescription>\n"
                + "<modelName>HiCast DMR</modelName>\n"
                + "<modelNumber>0.1</modelNumber>\n"
                + "<serialNumber>1</serialNumber>\n"
                + "<UDN>uuid:" + escape(uuid) + "</UDN>\n"
                + "<dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>\n"
                + "<serviceList>\n"
                + service(SsdpTargets.AVT, "urn:upnp-org:serviceId:AVTransport", "/avt.xml")
                + service(SsdpTargets.RCS, "urn:upnp-org:serviceId:RenderingControl", "/rcs.xml")
                + service(SsdpTargets.CMS, "urn:upnp-org:serviceId:ConnectionManager", "/cms.xml")
                + "</serviceList>\n"
                + "</device>\n"
                + "</root>\n";
    }

    private static String service(String serviceType, String serviceId, String scpdUrl) {
        return "<service>\n"
                + "<serviceType>" + serviceType + "</serviceType>\n"
                + "<serviceId>" + serviceId + "</serviceId>\n"
                + "<SCPDURL>" + scpdUrl + "</SCPDURL>\n"
                + "<controlURL>/control</controlURL>\n"
                + "<eventSubURL>/control</eventSubURL>\n"
                + "</service>\n";
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
