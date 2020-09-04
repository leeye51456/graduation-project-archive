package org.onosproject.orch.monitor;

import org.json.JSONException;
import org.json.JSONObject;
import org.onosproject.net.Annotations;
import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.orch.Orchestration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DeviceEventListener implements DeviceListener {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final TopologyInformation info;
    private final DeviceService deviceService;


    public DeviceEventListener(Orchestration orch) {
        info = orch.getTopologyInformation();
        deviceService = orch.getDeviceService();
    }


    @Override
    public void event(DeviceEvent event) {

        Device device = event.subject();

        switch (event.type()) {
            case DEVICE_ADDED:
                log.info("A device is added! id is {}", device.id());
                JSONObject explicitDeviceJson = buildExplicitDeviceJson(device);
                JSONObject summaryDeviceJson = buildSummaryDeviceJson(device);
                info.putDevice(device.id(), explicitDeviceJson, summaryDeviceJson);
                break;

            case DEVICE_REMOVED:
                log.info("A device is removed! id is {}", device.id());
                info.removeDevice(device.id());
                break;

            default:
                break;
        }
    }


    private JSONObject buildExplicitDeviceJson(Device device) {
        JSONObject root = buildSummaryDeviceJson(device);

        try {
            // additional properties for explicit devices
            root.put("type", device.type().toString());
            root.put("available", deviceService.isAvailable(device.id()));
            root.put("role", deviceService.getRole(device.id()).name());
            root.put("mfr", device.manufacturer());
            root.put("hw", device.hwVersion());
            root.put("sw", device.swVersion());
            root.put("serial", device.serialNumber());
            root.put("chassisId", device.chassisId().toString());

            JSONObject annotationsNode = new JSONObject();
            Annotations annotations = device.annotations();
            for (String key : annotations.keys()) {
                annotationsNode.put(key, annotations.value(key));
            }
            root.put("annotations", annotationsNode);

            return root;

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            return new JSONObject(); // empty json object
        }
    }


    private JSONObject buildSummaryDeviceJson(Device device) {
        JSONObject root = new JSONObject();

        try {
            root.put("id", device.id().toString());
            return root;

        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            return new JSONObject(); // empty json object
        }
    }

}
