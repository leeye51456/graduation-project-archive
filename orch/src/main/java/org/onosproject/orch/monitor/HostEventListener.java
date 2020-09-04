package org.onosproject.orch.monitor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.orch.Orchestration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HostEventListener implements HostListener {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final TopologyInformation info;
    private final HostService hostService;


    public HostEventListener(Orchestration orch) {
        info = orch.getTopologyInformation();
        hostService = orch.getHostService();
    }


    @Override
    public void event(HostEvent event) {

        Host host = event.subject();

        switch (event.type()) {
            case HOST_ADDED:
                log.info("A host is added! id is {}, and location is {}", host.id(), host.location());
                JSONObject hostJson = buildHostJson(host);
                info.putHost(host.id(), hostJson);
                break;

            case HOST_REMOVED:
                log.info("A host is removed! id is {}", host.id());
                info.removeHost(host.id());
                break;

            default:
                break;
        }
    }


    private JSONObject buildHostJson(Host host) {
        JSONObject root = new JSONObject();

        try {
            root.put("id", host.id().toString());
            root.put("mac", host.mac().toString());
            root.put("vlan", host.vlan().toString());

            JSONArray ipAddressNode = new JSONArray(host.ipAddresses());
            root.put("ipAddress", ipAddressNode);

            JSONObject locationNode = new JSONObject();
            locationNode.put("elementId", host.location().elementId());
            locationNode.put("port", host.location().port());
            root.put("location", locationNode);

            return root;

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            return new JSONObject(); // empty json object
        }
    }

}
