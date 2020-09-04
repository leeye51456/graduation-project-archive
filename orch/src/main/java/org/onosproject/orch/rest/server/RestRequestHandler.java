package org.onosproject.orch.rest.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.onlab.packet.MacAddress;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.orch.Orchestration;
import org.onosproject.orch.monitor.TopologyInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Set;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;


public class RestRequestHandler extends ChannelInboundHandlerAdapter {

    private final Logger log = LoggerFactory.getLogger(getClass());


    private Orchestration orch;
    private TopologyInformation info;

    private HttpRequest httpRequest;
    private HttpMethod method;
    private String uri;

    private StringBuffer contentBuffer;
    private String content;

    private HttpResponseStatus responseStatus;


    public RestRequestHandler(Orchestration orch) {
        this.orch = orch;
        this.info = orch.getTopologyInformation();
        contentBuffer = new StringBuffer();
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof DefaultHttpRequest) {
            httpRequest = (DefaultHttpRequest)msg;
            method = httpRequest.method();
            uri = httpRequest.uri();

            log.info("method, uri: {}, {}", method, uri);
        }

        if (msg instanceof HttpContent) {
            HttpContent content = (HttpContent)msg;
            contentBuffer.append(content.content().toString(CharsetUtil.UTF_8));
        }

        if (msg instanceof LastHttpContent) {
            content = contentBuffer.toString();
            log.info("content: {}", content);

            String resp = buildResponseForRestRequest(); // responseStatus will be changed with returning

            if (HttpUtil.is100ContinueExpected(httpRequest)) {
                ctx.write(new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.CONTINUE));
            }

            log.info("response: {}", resp);
            FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, responseStatus,
                    Unpooled.wrappedBuffer(resp.getBytes(CharsetUtil.UTF_8)));
            res.headers().set(CONTENT_TYPE, "text/plain");
            res.headers().set(CONTENT_LENGTH, res.content().readableBytes());

            if (HttpUtil.isKeepAlive(httpRequest)) {
                res.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                ctx.write(res);
            } else {
                ctx.write(res).addListener(ChannelFutureListener.CLOSE);
            }
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("Crap!");
        ctx.close();
    }


    private String buildResponseForRestRequest() {
		if (method.equals(HttpMethod.HEAD)) { // request for headers
            responseStatus = HttpResponseStatus.OK; // 200
			return "";

		} else if (method.equals(HttpMethod.POST)) { // Create
			return buildResponseForPostMessage();

		} else if (method.equals(HttpMethod.GET)) { // Read
			return buildResponseForGetMessage();

		} else if (method.equals(HttpMethod.PUT)) { // Update
		    return buildResponseForPutMessage();

		} else if (method.equals(HttpMethod.DELETE)) { // Delete
            responseStatus = HttpResponseStatus.NOT_FOUND; // 404
			return "";

		} else {
            responseStatus = HttpResponseStatus.METHOD_NOT_ALLOWED; // 405
			return "";
		}
	}


	// POST
    private String buildResponseForPostMessage() {
        if (uri.startsWith("/provisioning")) {
            return buildResponseForPostProvisioningMessage();

        } else if (uri.startsWith("/flows")) {
            return buildResponseForPostFlowsMessage();

        } else if (uri.startsWith("/children")) {
            return buildResponseForPostChildrenMessage();

        }

        responseStatus = HttpResponseStatus.NOT_FOUND; // 404
        return "";
    }

    // POST /provisioning
    private String buildResponseForPostProvisioningMessage() {
        ConnectPoint src, dst;
        MacAddress srcMac, dstMac;
        boolean hasTerminalNode;
        try {
            JSONObject root = new JSONObject(content);

            JSONObject provisioningNode = root.getJSONObject("provisioning");

            JSONObject srcNode = provisioningNode.getJSONObject("src");
            DeviceId srcId = DeviceId.deviceId(srcNode.getString("device"));
            PortNumber srcPort = PortNumber.portNumber(srcNode.getLong("port"));
            src = new ConnectPoint(srcId, srcPort);

            JSONObject dstNode = provisioningNode.getJSONObject("dst");
            DeviceId dstId = DeviceId.deviceId(dstNode.getString("device"));
            PortNumber dstPort = PortNumber.portNumber(dstNode.getLong("port"));
            dst = new ConnectPoint(dstId, dstPort);

            hasTerminalNode = provisioningNode.has("terminal");
            if (hasTerminalNode) {
                JSONObject terminalNode = provisioningNode.getJSONObject("terminal");
                srcMac = MacAddress.valueOf(terminalNode.getString("src"));
                dstMac = MacAddress.valueOf(terminalNode.getString("dst"));
            } else {
                srcMac = null;
                dstMac = null;
            }

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            responseStatus = HttpResponseStatus.BAD_REQUEST; // 400
            return "";
        }

        if (hasTerminalNode) {
            orch.divideProvisioning(src, dst, srcMac, dstMac);
        } else {
            orch.divideProvisioning(src, dst);
        }

        responseStatus = HttpResponseStatus.OK; // 200
        return "";
    }

    // POST /flows
    private String buildResponseForPostFlowsMessage() {
        orch.applyFlowRulesToMyself(content);

        responseStatus = HttpResponseStatus.OK; // 200
        return "";
    }

    // POST /children
    private String buildResponseForPostChildrenMessage() {
        JSONObject root;
        JSONArray childrenNode;
        try {
            root = new JSONObject(content);
            childrenNode = root.getJSONArray("children");
        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            responseStatus = HttpResponseStatus.BAD_REQUEST; // 400
            return "";
        }

        ArrayList<String> children = new ArrayList<>();
        int length = childrenNode.length();
        for (int i = 0; i < length; i++) {
            String child;
            try {
                child = childrenNode.getString(i);
            } catch (JSONException e) {
                log.warn("exception: {}", e.toString());
                continue;
            }
            children.add(child);
        }
        orch.executeTopologyDiscoveryAndAddChildren(children);

        responseStatus = HttpResponseStatus.OK; // 200
        return "";
    }


    // GET
    private String buildResponseForGetMessage() {
        if (uri.startsWith("/devices")) {
            return buildResponseForGetDevicesMessage();

        } else if (uri.startsWith("/links")) {
            return buildResponseForGetLinksMessage();

        } else if (uri.startsWith("/hosts")) {
            return buildResponseForGetHostsMessage();

        } else if (uri.startsWith("/edgeUpdates")) {
            return buildResponseForGetEdgeUpdatesMessage();

        }

        responseStatus = HttpResponseStatus.NOT_FOUND; // 404
        return "";
    }

    // GET /devices, GET /devices/explicit, GET /devices/summary
    private String buildResponseForGetDevicesMessage() {
        String subUri = uri.substring(8); // "/devices..."

        JSONObject root = new JSONObject();
        JSONArray devices = new JSONArray();

        switch (subUri) {
            case "":
            case "/":
            case "/explicit":
            case "/explicit/":
                for (JSONObject deviceJson : info.getExplicitDevicesJson()) {
                    devices.put(deviceJson);
                }

                break;

            case "/summary":
            case "/summary/":
                for (JSONObject deviceJson : info.getSummaryDevicesJson()) {
                    devices.put(deviceJson);
                }

                break;

            default:
                responseStatus = HttpResponseStatus.NOT_FOUND; // 404
                return "";
        }

        try {
            root.put("devices", devices);

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            responseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR; // 500
            return "";
        }

        responseStatus = HttpResponseStatus.OK; // 200
        return root.toString();
    }

    // GET /links, GET /links/explicit, GET /links/summary, GET /links/implicit
    private synchronized String buildResponseForGetLinksMessage() {
        String subUri = uri.substring(6); // "/links..."

        JSONObject root = new JSONObject();
        JSONArray links = new JSONArray();

        switch (subUri) {
            case "":
            case "/":
            case "/explicit":
            case "/explicit/":
                for (JSONObject linkJson : info.getExplicitLinksJsonAndClearChanges()) {
                    links.put(linkJson);
                }

                break;

            case "/summary":
            case "/summary/":
                for (JSONObject linkJson : info.getSummaryLinksJsonAndClearChanges()) {
                    links.put(linkJson);
                }

                break;

            case "/implicit":
            case "/implicit/":
                for (JSONObject linkJson : info.getImplicitLinksJsonAndClearChanges()) {
                    links.put(linkJson);
                }

                break;

            default:
                responseStatus = HttpResponseStatus.NOT_FOUND; // 404
                return "";
        }

        try {
            root.put("links", links);

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            responseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR; // 500
            return "";
        }

        responseStatus = HttpResponseStatus.OK; // 200
        return root.toString();
    }

    // GET /hosts, GET /hosts/...
    private String buildResponseForGetHostsMessage() {
        JSONObject root = new JSONObject();
        JSONArray hosts = new JSONArray();

        for (JSONObject hostJson : info.getHostsJson()) {
            hosts.put(hostJson);
        }

        try {
            root.put("hosts", hosts);

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            responseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR; // 500
            return "";
        }

        responseStatus = HttpResponseStatus.OK; // 200
        return root.toString();
    }

    // GET /edgeUpdates, GET /edgeUpdates/explicit, GET /edgeUpdates/summary, GET /edgeUpdates/implicit
    private synchronized String buildResponseForGetEdgeUpdatesMessage() {
        String subUri = uri.substring(12); // "/edgeUpdates..."

        JSONObject root = new JSONObject();
        JSONObject edgeUpdates = new JSONObject();
        JSONArray added = new JSONArray();
        JSONArray deleted = new JSONArray();

        Set<JSONObject> addedLinksJson;
        Set<JSONObject> deletedLinksJson;

        switch (subUri) {
            case "":
            case "/":
            case "/explicit":
            case "/explicit/":
                addedLinksJson = info.getAddedExplicitLinksJson();
                deletedLinksJson = info.getDeletedExplicitLinksJson();

                break;

            case "/summary":
            case "/summary/":
                addedLinksJson = info.getAddedSummaryLinksJson();
                deletedLinksJson = info.getDeletedSummaryLinksJson();

                break;

            case "/implicit":
            case "/implicit/":
                addedLinksJson = info.getAddedImplicitLinksJson();
                deletedLinksJson = info.getDeletedImplicitLinksJson();

                break;

            default:
                responseStatus = HttpResponseStatus.NOT_FOUND; // 404
                return "";
        }

        for (JSONObject linkJson : addedLinksJson) {
            added.put(linkJson);
        }
        for (JSONObject linkJson : deletedLinksJson) {
            deleted.put(linkJson);
        }

        try {
            edgeUpdates.put("added", added);
            edgeUpdates.put("deleted", deleted);
            root.put("edgeUpdates", edgeUpdates);

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            responseStatus = HttpResponseStatus.INTERNAL_SERVER_ERROR; // 500
            return "";
        }

        info.clearChangesOfLinks();

        responseStatus = HttpResponseStatus.OK; // 200
        return root.toString();
    }


    // PUT
    private String buildResponseForPutMessage() {
        if (uri.startsWith("/abstraction")) {
            return buildResponseForPutAbstractionMessage();

        }

        responseStatus = HttpResponseStatus.NOT_FOUND; // 404
        return "";
    }

    // PUT /abstraction
    private String buildResponseForPutAbstractionMessage() {
        JSONObject root;
        String abstractionNode;
        try {
            root = new JSONObject(content);
            abstractionNode = root.getString("abstraction");
        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            responseStatus = HttpResponseStatus.BAD_REQUEST; // 400
            return "";
        }

        boolean isValidRequest;

        switch (abstractionNode) {
            case "explicit":
                isValidRequest = orch.changeOrchestrationToExplicit();

                break;

            case "implicit":
                isValidRequest = orch.changeOrchestrationToImplicit();

                break;

            default:
                responseStatus = HttpResponseStatus.BAD_REQUEST; // 400
                return "";
        }

        if (isValidRequest) {
            responseStatus = HttpResponseStatus.OK; // 200
        } else {
            responseStatus = HttpResponseStatus.FORBIDDEN; // 403
        }
        return "";
    }

}
