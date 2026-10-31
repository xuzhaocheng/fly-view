package com.feiniu.tv.cast.core;

/**
 * Builds the UPnP service descriptions (SCPD) for the three services a
 * MediaRenderer must expose. Documents are generated from small builders so
 * tags cannot end up unbalanced.
 */
public final class Scpd {

    private static final String NS = "urn:schemas-upnp-org:service-1-0";

    /** Formats this renderer can pull over HTTP. Advertised via GetProtocolInfo. */
    private static final String SINK_PROTOCOLS = join(
            "http-get:*:video/mp4:*",
            "http-get:*:video/x-matroska:*",
            "http-get:*:video/mpeg:*",
            "http-get:*:application/vnd.apple.mpegurl:*",
            "http-get:*:application/x-mpegurl:*",
            "http-get:*:audio/mpeg:*",
            "http-get:*:audio/mp4:*",
            "http-get:*:image/jpeg:*");

    private Scpd() {
    }

    public static String sinkProtocols() {
        return SINK_PROTOCOLS;
    }

    public static String avTransport() {
        return document(
                actions(
                        action("SetAVTransportURI")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID")
                                .in("CurrentURI", "AVTransportURI")
                                .in("CurrentURIMetaData", "AVTransportURIMetaData"),
                        action("Play")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID")
                                .in("Speed", "TransportPlaySpeed"),
                        action("Pause")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID"),
                        action("Stop")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID"),
                        action("Seek")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID")
                                .in("Unit", "A_ARG_TYPE_SeekMode")
                                .in("Target", "A_ARG_TYPE_SeekTarget"),
                        action("GetPositionInfo")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID")
                                .out("Track", "CurrentTrack")
                                .out("TrackDuration", "CurrentTrackDuration")
                                .out("TrackMetaData", "AVTransportURIMetaData")
                                .out("TrackURI", "AVTransportURI")
                                .out("RelTime", "RelativeTimePosition")
                                .out("AbsTime", "AbsoluteTimePosition"),
                        action("GetTransportInfo")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID")
                                .out("CurrentTransportState", "TransportState")
                                .out("CurrentTransportStatus", "TransportStatus")
                                .out("CurrentSpeed", "TransportPlaySpeed")),
                stateWithValues("TransportState", "string", "STOPPED", "PLAYING", "PAUSED_PLAYBACK", "TRANSITIONING")
                        + stateWithValues("TransportStatus", "string", "OK", "ERROR_OCCURRED")
                        + stateWithValues("TransportPlaySpeed", "string", "1")
                        + state("AVTransportURI", "string")
                        + state("AVTransportURIMetaData", "string")
                        + state("CurrentTrack", "ui4")
                        + state("CurrentTrackDuration", "string")
                        + state("RelativeTimePosition", "string")
                        + state("AbsoluteTimePosition", "string")
                        + state("A_ARG_TYPE_InstanceID", "ui4")
                        + stateWithValues("A_ARG_TYPE_SeekMode", "string", "REL_TIME", "ABS_TIME")
                        + state("A_ARG_TYPE_SeekTarget", "string")
                        + eventedState("LastChange", "string"));
    }

    public static String renderingControl() {
        return document(
                actions(
                        action("GetVolume")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID")
                                .in("Channel", "A_ARG_TYPE_Channel")
                                .out("CurrentVolume", "Volume"),
                        action("SetVolume")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID")
                                .in("Channel", "A_ARG_TYPE_Channel")
                                .in("DesiredVolume", "Volume"),
                        action("GetMute")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID")
                                .in("Channel", "A_ARG_TYPE_Channel")
                                .out("CurrentMute", "Mute"),
                        action("SetMute")
                                .in("InstanceID", "A_ARG_TYPE_InstanceID")
                                .in("Channel", "A_ARG_TYPE_Channel")
                                .in("DesiredMute", "Mute")),
                state("A_ARG_TYPE_InstanceID", "ui4")
                        + stateWithValues("A_ARG_TYPE_Channel", "string", "Master")
                        + stateWithRange("Volume", "ui2", 0, 100, 1)
                        + state("Mute", "boolean")
                        + eventedState("LastChange", "string"));
    }

    public static String connectionManager() {
        return document(
                actions(
                        action("GetProtocolInfo")
                                .out("Source", "SourceProtocolInfo")
                                .out("Sink", "SinkProtocolInfo"),
                        action("GetCurrentConnectionIDs")
                                .out("ConnectionIDs", "CurrentConnectionIDs")),
                eventedState("SourceProtocolInfo", "string")
                        + eventedState("SinkProtocolInfo", "string")
                        + eventedState("CurrentConnectionIDs", "string"));
    }

    private static String document(String actionList, String stateTable) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<scpd:scpd xmlns:scpd=\"" + NS + "\">\n"
                + "<scpd:specVersion><scpd:major>1</scpd:major><scpd:minor>0</scpd:minor></scpd:specVersion>\n"
                + "<scpd:actionList>\n" + actionList + "</scpd:actionList>\n"
                + "<scpd:serviceStateTable>\n" + stateTable + "</scpd:serviceStateTable></scpd:scpd>\n";
    }

    private static String actions(Action... actions) {
        StringBuilder sb = new StringBuilder();
        for (Action a : actions) {
            sb.append(a.xml());
        }
        return sb.toString();
    }

    private static Action action(String name) {
        return new Action(name);
    }

    private static String state(String name, String dataType) {
        return state(name, dataType, false, "");
    }

    private static String eventedState(String name, String dataType) {
        return state(name, dataType, true, "");
    }

    private static String stateWithValues(String name, String dataType, String... allowedValues) {
        StringBuilder list = new StringBuilder("<scpd:allowedValueList>");
        for (String value : allowedValues) {
            list.append("<scpd:allowedValue>").append(value).append("</scpd:allowedValue>");
        }
        list.append("</scpd:allowedValueList>");
        return state(name, dataType, false, list.toString());
    }

    private static String stateWithRange(String name, String dataType, int min, int max, int step) {
        String range = "<scpd:allowedValueRange>"
                + "<scpd:minimum>" + min + "</scpd:minimum>"
                + "<scpd:maximum>" + max + "</scpd:maximum>"
                + "<scpd:step>" + step + "</scpd:step>"
                + "</scpd:allowedValueRange>";
        return state(name, dataType, false, range);
    }

    private static String state(String name, String dataType, boolean sendsEvents, String extra) {
        return "<scpd:stateVariable sendEvents=\"" + (sendsEvents ? "yes" : "no") + "\">"
                + "<scpd:name>" + name + "</scpd:name>"
                + "<scpd:dataType>" + dataType + "</scpd:dataType>"
                + extra
                + "</scpd:stateVariable>\n";
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /** One {@code <scpd:action>}; arguments accumulate in declaration order. */
    private static final class Action {
        private final String name;
        private final StringBuilder arguments = new StringBuilder();

        private Action(String name) {
            this.name = name;
        }

        Action in(String argumentName, String relatedStateVariable) {
            return argument(argumentName, "in", relatedStateVariable);
        }

        Action out(String argumentName, String relatedStateVariable) {
            return argument(argumentName, "out", relatedStateVariable);
        }

        private Action argument(String argumentName, String direction, String relatedStateVariable) {
            arguments.append("<scpd:argument>")
                    .append("<scpd:name>").append(argumentName).append("</scpd:name>")
                    .append("<scpd:direction>").append(direction).append("</scpd:direction>")
                    .append("<scpd:relatedStateVariable>").append(relatedStateVariable)
                    .append("</scpd:relatedStateVariable>")
                    .append("</scpd:argument>");
            return this;
        }

        String xml() {
            return "<scpd:action>"
                    + "<scpd:name>" + name + "</scpd:name>"
                    + "<scpd:argumentList>" + arguments + "</scpd:argumentList>"
                    + "</scpd:action>\n";
        }
    }
}
