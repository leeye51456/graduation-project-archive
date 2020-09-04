package org.onosproject.orch.monitor;

import org.json.JSONException;
import org.json.JSONObject;
import org.onosproject.net.Annotations;
import org.onosproject.net.Device;
import org.onosproject.net.Link;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.link.LinkListener;
import org.onosproject.net.link.LinkService;
import org.onosproject.orch.Orchestration;
import org.onosproject.orch.adt.elem.DirectedLink;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LinkEventListener implements LinkListener {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final TopologyInformation info;
    private final LinkService linkService;


    public LinkEventListener(Orchestration orch) {
        info = orch.getTopologyInformation();
        linkService = orch.getLinkService();
    }


    @Override
    public void event(LinkEvent event) {

        Link link = event.subject();

        JSONObject explicitLinkJson = buildExplicitLinkJson(link);
        JSONObject summaryLinkJson = buildSummaryLinkJson(link);
        JSONObject implicitLinkJson = buildImplicitLinkJson(link);

        switch (event.type()) {
            case LINK_ADDED:
                log.info("A link is added! between {} and {}", link.src(), link.dst());
                info.putLink(new DirectedLink(link.src(), link.dst()),
                        explicitLinkJson, summaryLinkJson, implicitLinkJson);

                break;

            case LINK_REMOVED:
                log.info("A link is removed! between {} and {}", link.src(), link.dst());
                info.removeLink(new DirectedLink(link.src(), link.dst()),
                        explicitLinkJson, summaryLinkJson, implicitLinkJson);

                break;

            default:
                break;
        }
    }


    private JSONObject buildExplicitLinkJson(Link link) {
        JSONObject root = buildSummaryLinkJson(link);

        try {
            // additional properties for explicit links
            root.put("type", link.type().name());
            root.put("state", link.state().name());

            return root;

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            return new JSONObject(); // empty json object
        }
    }


    private JSONObject buildSummaryLinkJson(Link link) {
        JSONObject root = new JSONObject();

        try {
            JSONObject srcNode = new JSONObject();
            srcNode.put("device", link.src().deviceId().toString());
            srcNode.put("port", link.src().port());
            root.put("src", srcNode);

            JSONObject dstNode = new JSONObject();
            dstNode.put("device", link.dst().deviceId().toString());
            dstNode.put("port", link.dst().port());
            root.put("dst", dstNode);

            return root;

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            return new JSONObject(); // empty json object
        }
    }


    private JSONObject buildImplicitLinkJson(Link link) {
        if (info.hasDevice(link.src().deviceId()) && info.hasDevice(link.dst().deviceId())) {
            return null;
        }

        // topology doesn't contain one of devices in it -> interlink
        return buildSummaryLinkJson(link);
    }

}
