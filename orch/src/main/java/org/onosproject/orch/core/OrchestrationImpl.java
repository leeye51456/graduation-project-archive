package org.onosproject.orch.core;

import io.netty.handler.codec.http.HttpMethod;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.onlab.packet.ChassisId;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.codec.CodecService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.*;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.host.*;
import org.onosproject.net.link.*;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.orch.Orchestration;
import org.onosproject.orch.core.explicit.FlowRuleDecoder;
import org.onosproject.orch.core.explicit.FlowRuleJsonConstructor;
import org.onosproject.orch.monitor.*;
import org.onosproject.orch.rest.client.RestClient;
import org.onosproject.orch.rest.client.explicit.RestClientForExplicitElements;
import org.onosproject.orch.rest.client.implicit.RestClientForImplicitElements;
import org.onosproject.orch.rest.server.RestServer;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;


@Component(immediate = true)
public class OrchestrationImpl implements Orchestration {

    private static final String SCHEME_FOR_EXPLICIT = "exvs"; // EXplicit Virtual Switch
    private static final String SCHEME_FOR_IMPLICIT = "imvs"; // IMplicit Virtual Switch

    private static final String APP_NAME = "org.onosproject.orch";

    private static final ProviderId PROVIDER_ID_FOR_EXPLICIT = new ProviderId(SCHEME_FOR_EXPLICIT, APP_NAME);
    private static final ProviderId PROVIDER_ID_FOR_IMPLICIT = new ProviderId(SCHEME_FOR_IMPLICIT, APP_NAME);

    private static final String IP_OF_LOCALHOST = "127.0.0.1";
    private static final int PORT_OF_ORCHESTRATION_SERVER = 8888;

    private static final String URI_OF_EXPLICIT_DEVICES = "/devices";
    private static final String URI_OF_EXPLICIT_LINKS = "/links";
    private static final String URI_OF_EXPLICIT_HOSTS = "/hosts";
    private static final String URI_OF_EXPLICIT_EDGE_UPDATES = "/edgeUpdates";

    private static final String URI_OF_IMPLICIT_DEVICES = "/devices/summary";
    private static final String URI_OF_IMPLICIT_LINKS = "/links/implicit";
    private static final String URI_OF_IMPLICIT_HOSTS = "/hosts";
    private static final String URI_OF_IMPLICIT_EDGE_UPDATES = "/edgeUpdates/implicit";

    private static final String URI_OF_PROVISIONING = "/provisioning";
    private static final String URI_OF_FLOWS = "/flows";


    private final Logger log = LoggerFactory.getLogger(getClass());


    private ApplicationId appId;


    private DeviceProviderService explicitDeviceProviderService;
    private LinkProviderService explicitLinkProviderService;
    private HostProviderService explicitHostProviderService;

    private DeviceProviderService implicitDeviceProviderService;
    private LinkProviderService implicitLinkProviderService;
    private HostProviderService implicitHostProviderService;

    private final InternalElementProvider explicitElementProvider
            = new InternalElementProvider(PROVIDER_ID_FOR_EXPLICIT);
    private final DeviceProvider explicitDeviceProvider = explicitElementProvider;
    private final LinkProvider explicitLinkProvider = explicitElementProvider;
    private final HostProvider explicitHostProvider = explicitElementProvider;

    private final InternalElementProvider implicitElementProvider
            = new InternalElementProvider(PROVIDER_ID_FOR_IMPLICIT);
    private final DeviceProvider implicitDeviceProvider = implicitElementProvider;
    private final LinkProvider implicitLinkProvider = implicitElementProvider;
    private final HostProvider implicitHostProvider = implicitElementProvider;


    private ResourceTables res;
    private TopologyInformation info;

    private FlowRuleJsonConstructor flowRuleJsonConstructor;
    private FlowRuleDecoder flowRuleDecoder;

    private ArrayList<String> topologyDiscoveryUri;
    private ConcurrentHashMap<String, Boolean> children;

    private DeviceListener deviceListener;
    private LinkListener linkListener;
    private HostListener hostListener;

    private boolean orchestrationStarted;
    private boolean implicitOrchestration;


    private ExecutorService orchestrationServerExecutor;
    private ExecutorService topologyDiscoveryExecutor;
    private ScheduledExecutorService faultMonitoringExecutor;
    private ExecutorService provisioningExecutor;


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DeviceProviderRegistry deviceProviderRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private LinkProviderRegistry linkProviderRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private HostProviderRegistry hostProviderRegistry;


    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private LinkService linkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    private CodecService codecService;


    @Activate
    public void activate() {
        appId = coreService.registerApplication(APP_NAME);


        explicitDeviceProviderService = deviceProviderRegistry.register(explicitDeviceProvider);
        explicitLinkProviderService = linkProviderRegistry.register(explicitLinkProvider);
        explicitHostProviderService = hostProviderRegistry.register(explicitHostProvider);

        implicitDeviceProviderService = deviceProviderRegistry.register(implicitDeviceProvider);
        implicitLinkProviderService = linkProviderRegistry.register(implicitLinkProvider);
        implicitHostProviderService = hostProviderRegistry.register(implicitHostProvider);


        res = new ResourceTables();
        info = new TopologyInformation();

        flowRuleJsonConstructor = new FlowRuleJsonConstructor(this);
        flowRuleDecoder = new FlowRuleDecoder(this);

        topologyDiscoveryUri = new ArrayList<>();
        children = new ConcurrentHashMap<>();

        orchestrationStarted = false;
        implicitOrchestration = true;
        changeOrchestrationToExplicit(); // trick for setting topologyDiscoveryUri

        deviceListener = new DeviceEventListener(this);
        linkListener = new LinkEventListener(this);
        hostListener = new HostEventListener(this);

        deviceService.addListener(deviceListener);
        linkService.addListener(linkListener);
        hostService.addListener(hostListener);


        orchestrationServerExecutor = Executors.newSingleThreadExecutor();
        orchestrationServerExecutor.execute(() -> {
            RestServer server = new RestServer(PORT_OF_ORCHESTRATION_SERVER, this);
            try {
                server.run();
            } catch (Exception e) {
                log.warn("cannot run orchestration server: {}", e.toString());
            }
        });

        topologyDiscoveryExecutor = Executors.newFixedThreadPool(128);
        faultMonitoringExecutor = Executors.newSingleThreadScheduledExecutor();
        provisioningExecutor = Executors.newFixedThreadPool(128);


        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        provisioningExecutor.shutdownNow();
        faultMonitoringExecutor.shutdownNow();
        topologyDiscoveryExecutor.shutdownNow();
        orchestrationServerExecutor.shutdownNow();


        deviceProviderRegistry.unregister(explicitDeviceProvider);
        linkProviderRegistry.unregister(explicitLinkProvider);
        hostProviderRegistry.unregister(explicitHostProvider);

        deviceProviderRegistry.unregister(implicitDeviceProvider);
        linkProviderRegistry.unregister(implicitLinkProvider);
        hostProviderRegistry.unregister(implicitHostProvider);

        explicitDeviceProviderService = null;
        explicitLinkProviderService = null;
        explicitHostProviderService = null;

        implicitDeviceProviderService = null;
        implicitLinkProviderService = null;
        implicitHostProviderService = null;


        deviceService.removeListener(deviceListener);
        linkService.removeListener(linkListener);
        hostService.removeListener(hostListener);

        deviceListener = null;
        linkListener = null;
        hostListener = null;

        topologyDiscoveryUri = null;
        children = null;

        flowRuleJsonConstructor = null;
        flowRuleDecoder = null;

        res = null;
        info = null;


        log.info("Stopped");
    }


    @Override
    public ApplicationId getApplicationId() {
        return appId;
    }
    @Override
    public TopologyService getTopologyService() {
        return topologyService;
    }
    @Override
    public DeviceService getDeviceService() {
        return deviceService;
    }
    @Override
    public LinkService getLinkService() {
        return linkService;
    }
    @Override
    public HostService getHostService() {
        return hostService;
    }
    @Override
    public CodecService getCodecService() {
        return codecService;
    }

    @Override
    public ResourceTables getResourceTables() {
        return res;
    }

    @Override
    public TopologyInformation getTopologyInformation() {
        return info;
    }


    @Override
    public String getSchemeForExplicit() {
        return SCHEME_FOR_EXPLICIT;
    }
    @Override
    public String getSchemeForImplicit() {
        return SCHEME_FOR_IMPLICIT;
    }


    @Override
    public boolean isImplicitOrchestration() {
        return implicitOrchestration;
    }
    @Override
    public boolean changeOrchestrationToExplicit() {
        if (orchestrationStarted) {
            // invalid operation
            return false;
        }
        if (!implicitOrchestration) {
            // nothing to change
            return true;
        }
        implicitOrchestration = false;

        topologyDiscoveryUri.clear();
        topologyDiscoveryUri.add(URI_OF_EXPLICIT_DEVICES);
        topologyDiscoveryUri.add(URI_OF_EXPLICIT_LINKS);
        topologyDiscoveryUri.add(URI_OF_EXPLICIT_HOSTS);

        return true;
    }
    @Override
    public boolean changeOrchestrationToImplicit() {
        if (orchestrationStarted) {
            // invalid operation
            return false;
        }
        if (implicitOrchestration) {
            // nothing to change
            return true;
        }
        implicitOrchestration = true;

        topologyDiscoveryUri.clear();
        topologyDiscoveryUri.add(URI_OF_IMPLICIT_DEVICES);
        topologyDiscoveryUri.add(URI_OF_IMPLICIT_LINKS);
        topologyDiscoveryUri.add(URI_OF_IMPLICIT_HOSTS);

        return true;
    }


    @Override
    public void executeTopologyDiscoveryAndAddChildren(ArrayList<String> newChildren) {
        boolean firstExecution = !orchestrationStarted;
        orchestrationStarted = true;

        topologyDiscoveryExecutor.execute(() -> {
            executeTopologyDiscovery(newChildren);

            for (String child : newChildren) {
                children.put(child, true);
            }
            log.info("children is added to the list for fault monitoring: now these children are {}",
                    children.keySet().toString());

            if (firstExecution) {
                scheduleFaultMonitoring();
            }
        });
    }

    private void executeTopologyDiscovery(ArrayList<String> newChildren) {
        for (String uri : topologyDiscoveryUri) {
            for (String child : newChildren) {
                RestClient client;
                if (implicitOrchestration) {
                    client = new RestClientForImplicitElements(
                            child, PORT_OF_ORCHESTRATION_SERVER, uri, this);
                } else {
                    client = new RestClientForExplicitElements(
                            child, PORT_OF_ORCHESTRATION_SERVER, uri, this);
                }
                client.connect();
            }
            try {
                // wait for handling response, to prevent the core from ignoring links and hosts
                // FIXME continue next loop after handling response of prior request, not after some seconds
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.warn("exception: {}", e.toString());
            }
        }
        log.info("topology discovery is done with children: {}", newChildren.toString());
    }

    private void scheduleFaultMonitoring() {
        faultMonitoringExecutor.scheduleAtFixedRate(() -> {
            if (!orchestrationStarted) {
                return;
            }
            for (String child : children.keySet()) {
                RestClient client;
                if (implicitOrchestration) {
                    client = new RestClientForImplicitElements(child, PORT_OF_ORCHESTRATION_SERVER,
                            URI_OF_IMPLICIT_EDGE_UPDATES, this);
                } else {
                    client = new RestClientForExplicitElements(child, PORT_OF_ORCHESTRATION_SERVER,
                            URI_OF_EXPLICIT_EDGE_UPDATES, this);
                }
                client.connect();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }


    @Override
    public void addDevice(DeviceId deviceId) {
        String schemeSpecificPart = deviceId.uri().getSchemeSpecificPart();
        ChassisId chassisId = new ChassisId(schemeSpecificPart);
        SparseAnnotations annotations = DefaultAnnotations.builder().build(); // empty annotations
        DeviceDescription description = new DefaultDeviceDescription(deviceId.uri(), Device.Type.SWITCH,
                "", "", "", "", chassisId, annotations);

        String scheme = deviceId.uri().getScheme();

        if (SCHEME_FOR_EXPLICIT.equals(scheme)) {
            explicitDeviceProviderService.deviceConnected(deviceId, description);
            log.info("Notified DeviceProviderService to add an explicit device {}", deviceId);

        } else if (SCHEME_FOR_IMPLICIT.equals(scheme)) {
            implicitDeviceProviderService.deviceConnected(deviceId, description);
            log.info("Notified DeviceProviderService to add an implicit device {}", deviceId);

        } else {
            log.warn("Cannot add a device {}, which has an invalid scheme", deviceId);
        }
    }

    @Override
    public void addLink(ConnectPoint src, ConnectPoint dst) {
        String scheme = src.deviceId().uri().getScheme();
        if (!scheme.equals(dst.deviceId().uri().getScheme())) {
            log.warn("Cannot add a link between devices which don't have same abstraction");
            return;
        }

        DefaultLinkDescription linkDescription = new DefaultLinkDescription(src, dst, Link.Type.DIRECT);

        if (SCHEME_FOR_EXPLICIT.equals(scheme)) {
            explicitLinkProviderService.linkDetected(linkDescription);
            log.info("Notified LinkProviderService to add an explicit link from {} to {}", src, dst);

        } else if (SCHEME_FOR_IMPLICIT.equals(scheme)) {
            implicitLinkProviderService.linkDetected(linkDescription);
            log.info("Notified LinkProviderService to add an implicit link from {} to {}", src, dst);

        } else {
            log.warn("Cannot add a link from {} to {}, which have (an) invalid scheme(s)", src, dst);
        }
    }

    @Override
    public void deleteLink(ConnectPoint src, ConnectPoint dst) {
        DefaultLinkDescription linkDescription = new DefaultLinkDescription(src, dst, Link.Type.DIRECT);
        String scheme = src.deviceId().uri().getScheme();

        if (SCHEME_FOR_EXPLICIT.equals(scheme)) {
            explicitLinkProviderService.linkVanished(linkDescription);
            log.info("Notified LinkProviderService to delete an explicit link from {} to {}", src, dst);

        } else if (SCHEME_FOR_IMPLICIT.equals(scheme)) {
            implicitLinkProviderService.linkVanished(linkDescription);
            log.info("Notified LinkProviderService to delete an implicit link from {} to {}", src, dst);

        } else {
            log.warn("Cannot delete a link from {} to {}, which have (an) invalid scheme(s)", src, dst);
        }
    }

    @Override
    public void addHost(MacAddress macAddress, VlanId vlanId, HostLocation location) {
        // HostId and HostDescription must have the same MacAddress and VlanId
        HostId hostId = HostId.hostId(macAddress, vlanId);
        HostDescription description = new DefaultHostDescription(macAddress, vlanId, location);

        String scheme = location.deviceId().uri().getScheme();

        if (SCHEME_FOR_EXPLICIT.equals(scheme)) {
            explicitHostProviderService.hostDetected(hostId, description, false);
            log.info("Notified HostProviderService to add an explicit host {}/{} to {}", macAddress, vlanId, location);

        } else if (SCHEME_FOR_IMPLICIT.equals(scheme)) {
            implicitHostProviderService.hostDetected(hostId, description, false);
            log.info("Notified HostProviderService to add an implicit host {}/{} to {}", macAddress, vlanId, location);

        } else {
            log.warn("Cannot add a host {}/{} to {}, which have an invalid scheme", macAddress, vlanId, location);
        }
    }


    @Override
    public void divideProvisioning(ConnectPoint src, ConnectPoint dst) {
        MacAddress srcMac = getMacAddressFromLocation(src);
        MacAddress dstMac = getMacAddressFromLocation(dst);

        if (srcMac == null || dstMac == null) {
            log.warn("failed to divide provisioning: src or dst is not host");
            return;
        }

        divideProvisioning(src, dst, srcMac, dstMac);
    }

    @Override
    public void divideProvisioning(ConnectPoint src, ConnectPoint dst, MacAddress srcMac, MacAddress dstMac) {
        Topology topology = topologyService.currentTopology();
        Set<Path> paths = topologyService.getPaths(topology, src.deviceId(), dst.deviceId());

        if (paths.size() <= 0) {
            if (!src.deviceId().equals(dst.deviceId())) {
                log.warn("no path from src {} to dst {}", src.toString(), dst.toString());
                return;
            }

            // same device -> provisioning request to the child that has that device
            String domain = res.getFromDeviceOwnerTable(src.deviceId());
            if (domain == null) {
                // physical device -> build and add flow rules
                buildAndApplyFlowRulesForMyself(src, dst, srcMac, dstMac);
            } else {
                sendProvisioningRequestToChild(src, dst, srcMac, dstMac, domain);
            }
            return;
        }

        // traversing links on path,
        // build and send provisioning request whenever owner controller of dst becomes different from one of src
        List<Link> links = paths.iterator().next().links();
        String currentDomain = res.getFromDeviceOwnerTable(src.deviceId());
        ConnectPoint srcOfCurrentDomain = src;

        for (Link link : links) {
            String dstDomain = res.getFromDeviceOwnerTable(link.dst().deviceId());
            // owner controller of dst becomes different from one of src
            if (!Objects.equals(currentDomain, dstDomain)) { // two strings are can be null
                if (currentDomain == null) {
                    // physical device -> apply flow rules and clear flow rule list
                    buildAndApplyFlowRulesForMyself(srcOfCurrentDomain, link.src(), srcMac, dstMac);
                } else {
                    sendProvisioningRequestToChild(srcOfCurrentDomain, link.src(), srcMac, dstMac, currentDomain);
                }
                currentDomain = dstDomain;
                srcOfCurrentDomain = link.dst();
            }
        }

        // postprocessing: apply final path segment
        if (currentDomain == null) {
            // physical device -> apply flow rules and clear flow rule list
            buildAndApplyFlowRulesForMyself(srcOfCurrentDomain, dst, srcMac, dstMac);
        } else {
            sendProvisioningRequestToChild(srcOfCurrentDomain, dst, srcMac, dstMac, currentDomain);
        }
    }

    private void buildAndApplyFlowRulesForMyself(ConnectPoint src, ConnectPoint dst,
                                                 MacAddress srcMac, MacAddress dstMac) {
        JSONObject root = new JSONObject();
        JSONArray flowsNode = new JSONArray();

        Set<JSONObject> rulesPerDevice = flowRuleJsonConstructor.buildFlowRuleJson(src, dst, srcMac, dstMac);
        rulesPerDevice.addAll(flowRuleJsonConstructor.buildFlowRuleJson(dst, src, dstMac, srcMac));
        for (JSONObject rule : rulesPerDevice) {
            flowsNode.put(rule);
        }

        try {
            root.put("flows", flowsNode);
        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
            return;
        }

        // json decoders of onos use the different json library -> toString() -> re-parse
        applyFlowRulesToMyself(root.toString());
    }

    private void sendProvisioningRequestToChild(ConnectPoint src, ConnectPoint dst,
                                                MacAddress srcMac, MacAddress dstMac, String domain) {
        try {
            String jsonString = buildProvisioningRequestJson(src, dst, srcMac, dstMac).toString();
            RestClient client;
            if (implicitOrchestration) {
                client = new RestClientForImplicitElements(domain, PORT_OF_ORCHESTRATION_SERVER,
                        HttpMethod.POST, URI_OF_PROVISIONING, jsonString, this);
            } else {
                client = new RestClientForExplicitElements(domain, PORT_OF_ORCHESTRATION_SERVER,
                        HttpMethod.POST, URI_OF_PROVISIONING, jsonString, this);
            }
            client.connect();
        } catch (JSONException e) {
            log.warn("exception: {}", e.toString());
        }
    }

    private JSONObject buildProvisioningRequestJson(ConnectPoint src, ConnectPoint dst,
                                                    MacAddress srcMac, MacAddress dstMac) throws JSONException {
        JSONObject root = new JSONObject();
        JSONObject provisioningNode = new JSONObject();

        JSONObject srcNode = new JSONObject();
        JSONObject dstNode = new JSONObject();

        if (implicitOrchestration) {
            ConnectPoint explicitSrc = res.getExplicitConnectPoint(src.deviceId(), src.port().toLong());
            srcNode.put("device", explicitSrc.deviceId());
            srcNode.put("port", explicitSrc.port().toLong());

            ConnectPoint explicitDst = res.getExplicitConnectPoint(dst.deviceId(), dst.port().toLong());
            dstNode.put("device", explicitDst.deviceId());
            dstNode.put("port", explicitDst.port().toLong());

        } else {
            srcNode.put("device", res.getActualDeviceId(src.deviceId()));
            srcNode.put("port", src.port());

            dstNode.put("device", res.getActualDeviceId(dst.deviceId()));
            dstNode.put("port", dst.port());
        }

        provisioningNode.put("src", srcNode);
        provisioningNode.put("dst", dstNode);

        JSONObject terminalNode = new JSONObject();
        terminalNode.put("src", srcMac.toString());
        terminalNode.put("dst", dstMac.toString());
        provisioningNode.put("terminal", terminalNode);

        root.put("provisioning", provisioningNode);

        return root;
    }


    @Override
    public void applyFlowRulesToMyself(String jsonString) {
        FlowRule[] rules;
        try {
            rules = flowRuleDecoder.decodeFlowRules(jsonString);
        } catch (Exception e) {
            log.warn("exception: {}", e.toString());
            return;
        }
        flowRuleService.applyFlowRules(rules);
    }

    private MacAddress getMacAddressFromLocation(ConnectPoint location) {
        Set<Host> hosts = hostService.getConnectedHosts(location);

        if (hosts != null && hosts.size() > 0) {
            return hosts.iterator().next().mac();
        }

        log.warn("host {} not found", location.toString());
        return null; // failed to find host by location
    }



    // nothing to do with InternalElementProvider
    private class InternalElementProvider
            extends AbstractProvider implements DeviceProvider, LinkProvider, HostProvider {
        public InternalElementProvider(ProviderId pid) {
            super(pid);
        }

        @Override
        public void triggerProbe(DeviceId deviceId) {
        }
        @Override
        public void roleChanged(DeviceId deviceId, MastershipRole newRole) {
        }
        @Override
        public boolean isReachable(DeviceId deviceId) {
            return true;
        }
        @Override
        public void changePortState(DeviceId deviceId, PortNumber portNumber, boolean enable) {
        }
        @Override
        public void triggerProbe(Host host) {
        }
    }

}
