package org.onosproject.orch.monitor;

import org.json.JSONObject;
import org.onosproject.net.DeviceId;
import org.onosproject.net.HostId;
import org.onosproject.orch.adt.elem.DirectedLink;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class TopologyInformation {

    private final ConcurrentHashMap<DeviceId, JSONObject> explicitDevices;
    private final ConcurrentHashMap<DeviceId, JSONObject> summaryDevices;

    private final ConcurrentHashMap<DirectedLink, JSONObject> explicitLinks;
    private final ConcurrentHashMap<DirectedLink, JSONObject> summaryLinks;
    private final ConcurrentHashMap<DirectedLink, JSONObject> implicitLinks;

    private final ConcurrentHashMap<HostId, JSONObject> hosts;

    private final ConcurrentHashMap<DirectedLink, JSONObject> addedExplicitLinks;
    private final ConcurrentHashMap<DirectedLink, JSONObject> addedSummaryLinks;
    private final ConcurrentHashMap<DirectedLink, JSONObject> addedImplicitLinks;

    private final ConcurrentHashMap<DirectedLink, JSONObject> deletedExplicitLinks;
    private final ConcurrentHashMap<DirectedLink, JSONObject> deletedSummaryLinks;
    private final ConcurrentHashMap<DirectedLink, JSONObject> deletedImplicitLinks;


    public TopologyInformation() {
        explicitDevices = new ConcurrentHashMap<>();
        summaryDevices = new ConcurrentHashMap<>();

        explicitLinks = new ConcurrentHashMap<>();
        summaryLinks = new ConcurrentHashMap<>();
        implicitLinks = new ConcurrentHashMap<>();

        hosts = new ConcurrentHashMap<>();

        addedExplicitLinks = new ConcurrentHashMap<>();
        addedSummaryLinks = new ConcurrentHashMap<>();
        addedImplicitLinks = new ConcurrentHashMap<>();

        deletedExplicitLinks = new ConcurrentHashMap<>();
        deletedSummaryLinks = new ConcurrentHashMap<>();
        deletedImplicitLinks = new ConcurrentHashMap<>();
    }


    public void putDevice(DeviceId deviceId, JSONObject explicitJson, JSONObject summaryJson) {
        explicitDevices.put(deviceId, explicitJson);
        summaryDevices.put(deviceId, summaryJson);
    }
    public boolean hasDevice(DeviceId deviceId) {
        return explicitDevices.containsKey(deviceId);
    }
    public void removeDevice(DeviceId deviceId) {
        explicitDevices.remove(deviceId);
        summaryDevices.remove(deviceId);
    }

    public synchronized void putLink(DirectedLink link,
                                     JSONObject explicitJson, JSONObject summaryJson, JSONObject implicitJson) {
        explicitLinks.put(link, explicitJson);
        summaryLinks.put(link, summaryJson);
        if (deletedExplicitLinks.containsKey(link)) {
            deletedExplicitLinks.remove(link);
            deletedSummaryLinks.remove(link);
        } else {
            addedExplicitLinks.put(link, explicitJson);
            addedSummaryLinks.put(link, summaryJson);
        }

        // interlink
        if (implicitJson != null) {
            implicitLinks.put(link, implicitJson);
            if (deletedImplicitLinks.containsKey(link)) {
                deletedImplicitLinks.remove(link);
            } else {
                addedImplicitLinks.put(link, implicitJson);
            }
        }
    }
    public synchronized void removeLink(DirectedLink link,
                                        JSONObject explicitJson, JSONObject summaryJson, JSONObject implicitJson) {
        explicitLinks.remove(link);
        summaryLinks.remove(link);
        if (addedExplicitLinks.containsKey(link)) {
            addedExplicitLinks.remove(link);
            addedSummaryLinks.remove(link);
        } else {
            deletedExplicitLinks.put(link, explicitJson);
            deletedSummaryLinks.put(link, summaryJson);
        }

        // interlink
        if (implicitJson != null) {
            implicitLinks.remove(link);
            if (addedImplicitLinks.containsKey(link)) {
                addedImplicitLinks.remove(link);
            } else {
                deletedImplicitLinks.put(link, implicitJson);
            }
        }
    }

    public void putHost(HostId hostId, JSONObject json) {
        hosts.put(hostId, json);
    }
    public void removeHost(HostId hostId) {
        hosts.remove(hostId);
    }


    public Set<JSONObject> getExplicitDevicesJson() {
        return new HashSet<>(explicitDevices.values());
    }
    public Set<JSONObject> getSummaryDevicesJson() {
        return new HashSet<>(summaryDevices.values());
    }


    public void clearChangesOfLinks() {
        addedExplicitLinks.clear();
        addedSummaryLinks.clear();
        addedImplicitLinks.clear();

        deletedExplicitLinks.clear();
        deletedSummaryLinks.clear();
        deletedImplicitLinks.clear();
    }

    public Set<JSONObject> getExplicitLinksJsonAndClearChanges() {
        clearChangesOfLinks();
        return new HashSet<>(explicitLinks.values());
    }
    public Set<JSONObject> getSummaryLinksJsonAndClearChanges() {
        clearChangesOfLinks();
        return new HashSet<>(summaryLinks.values());
    }
    public Set<JSONObject> getImplicitLinksJsonAndClearChanges() {
        clearChangesOfLinks();
        return new HashSet<>(implicitLinks.values());
    }

    public Set<JSONObject> getAddedExplicitLinksJson() {
        return new HashSet<>(addedExplicitLinks.values());
    }
    public Set<JSONObject> getAddedSummaryLinksJson() {
        return new HashSet<>(addedSummaryLinks.values());
    }
    public Set<JSONObject> getAddedImplicitLinksJson() {
        return new HashSet<>(addedImplicitLinks.values());
    }

    public Set<JSONObject> getDeletedExplicitLinksJson() {
        return new HashSet<>(deletedExplicitLinks.values());
    }
    public Set<JSONObject> getDeletedSummaryLinksJson() {
        return new HashSet<>(deletedSummaryLinks.values());
    }
    public Set<JSONObject> getDeletedImplicitLinksJson() {
        return new HashSet<>(deletedImplicitLinks.values());
    }


    public Set<JSONObject> getHostsJson() {
        return new HashSet<>(hosts.values());
    }

}
