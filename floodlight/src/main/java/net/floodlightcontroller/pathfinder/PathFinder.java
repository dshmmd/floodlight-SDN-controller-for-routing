package net.floodlightcontroller.pathfinder;

import java.util.*;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.restserver.IRestApiService;

import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class PathFinder implements IFloodlightModule, ILinkDiscoveryListener, IOFSwitchListener, IOFMessageListener {
    private static PathFinder instance;
    protected static Logger logger;

    protected IFloodlightProviderService floodlightProvider;
    protected ILinkDiscoveryService linkDiscoveryService;
    protected IOFSwitchService ofSwitchService;
    protected IDeviceService deviceService;
    protected IRestApiService restApiService;

    public static PathFinder getInstance() {
        return instance;
    }


    @Override
    public String getName() {
        return PathFinder.class.getSimpleName();
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<>();
        l.add(IFloodlightProviderService.class);
        l.add(ILinkDiscoveryService.class);
        l.add(IOFSwitchService.class);
        l.add(IDeviceService.class);
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        linkDiscoveryService = context.getServiceImpl(ILinkDiscoveryService.class);
        ofSwitchService = context.getServiceImpl(IOFSwitchService.class);
        deviceService = context.getServiceImpl(IDeviceService.class);
        restApiService = context.getServiceImpl(IRestApiService.class);

        logger = LoggerFactory.getLogger(PathFinder.class);
        instance = this;
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        linkDiscoveryService.addListener(this);
        ofSwitchService.addOFSwitchListener(this);
        restApiService.addRestletRoutable(new PathFinderWebRoutable());
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        return Command.CONTINUE;
    }


    @Override
    public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
        for (LDUpdate ldu : updateList) {
            if (ldu.getOperation().equals(UpdateOperation.LINK_UPDATED)) {
                TopologyGraph.getInstance().addNodeOrEdge(ldu.getSrc(), ldu.getDst());
            }
        }
    }

    @Override
    public void switchAdded(DatapathId switchId) {
        TopologyGraph.getInstance().addNodeOrEdge(switchId);
    }

    public MacAddress getHostMacFromDPID(DatapathId datapathId) {
        Collection<? extends IDevice> allDevices = deviceService.getAllDevices();
        for (IDevice device : allDevices) {
            if (device.getAttachmentPoints().length != 0) {
                SwitchPort switchPort = device.getAttachmentPoints()[0];
                if (switchPort.getNodeId().equals(datapathId)) {
                    return device.getMACAddress();
                }
            }
        }
        return null;
    }

    public String findPath(String start, String end) {

        DatapathId srcDPID = DatapathId.of(start);
        DatapathId dstDPID = DatapathId.of(end);
        logger.info("Finding path between {} and {}", srcDPID, dstDPID);

        List<DatapathId> bestPathList = TopologyGraph.getInstance().findShortestPath(srcDPID, dstDPID);
        String bestPathStr = pathListToStr(bestPathList);
        logger.info("Path found: {}", bestPathStr);

        MacAddress srcMacAddress = getHostMacFromDPID(srcDPID);
        MacAddress dstMacAddress = getHostMacFromDPID(dstDPID);

        IPv4Address srcIpv4Addr = IPv4Address.of("10.0.0." + start);
        IPv4Address dstIpv4Addr = IPv4Address.of("10.0.0." + end);
        Map<DatapathId, Set<Link>> allLinks = linkDiscoveryService.getSwitchLinks();

        logger.info("Printing links of switches in the path...");
        for (DatapathId datapathId:bestPathList) {
            System.out.println("Switch " + datapathId.toString());
            for (Link link:allLinks.get(datapathId)) {
                System.out.println(link.toString());
            }
        }

        logger.info("Trying to update switches forward");
        int len = bestPathList.size();
        for (int i = 0; i < len; i++) {
            DatapathId currentDPID = bestPathList.get(i);
            IOFSwitch currentSwitch = ofSwitchService.getSwitch(currentDPID);
            Set<Link> currentSwitchLinks = allLinks.get(currentDPID);

            logger.info("Set forwarding rule for switch {}", currentSwitch.toString());
            if (i < len - 1) {
                DatapathId nextDPID = bestPathList.get(i + 1);
                OFPort outPort = getPortOut(currentSwitchLinks, currentDPID, nextDPID);
                setOFRule(dstMacAddress, dstIpv4Addr, currentSwitch, outPort);
            } else {
                setOFRule(dstMacAddress, dstIpv4Addr, currentSwitch, OFPort.of(1));
            }
            logger.info("Rule added");
        }

        logger.info("Trying to update switches backwards");
        for (int i = len - 1; i >= 0; i--) {
            DatapathId currentDPID = bestPathList.get(i);
            IOFSwitch currentSwitch = ofSwitchService.getSwitch(currentDPID);
            Set<Link> currentSwitchLinks = allLinks.get(currentDPID);

            logger.info("Trying to update forwarding rule for switch {}", currentSwitch.toString());
            if (i > 0) {
                DatapathId previousDPID = bestPathList.get(i - 1);
                OFPort outPort = getPortOut(currentSwitchLinks, currentDPID, previousDPID);
                setOFRule(srcMacAddress, srcIpv4Addr, currentSwitch, outPort);
            } else {
                setOFRule(srcMacAddress, srcIpv4Addr, currentSwitch, OFPort.of(1));
            }
            logger.info("Rule added");
        }
        return bestPathStr;
    }

    private OFPort getPortOut(Set<Link> switchLinks, DatapathId currentDPID, DatapathId nextDPID) {
        for (Link link : switchLinks) {
            if (link.getSrc().equals(currentDPID) && link.getDst().equals(nextDPID)) {
                return link.getSrcPort();
            }
        }
        return null;
    }

    private void setOFRule(MacAddress dstMacAddress, IPv4Address dstIPv4Address, IOFSwitch iofSwitch, OFPort outPort) {
        OFFactory myFactory = iofSwitch.getOFFactory();

        ArrayList<OFAction> actionList = new ArrayList<>();
        OFActions actions = myFactory.actions();

        OFActionOutput output = actions.buildOutput().setMaxLen(0xFFffFFff).setPort(outPort).build();
        actionList.add(output);

        OFInstructions instructions = myFactory.instructions();
        OFInstructionApplyActions applyActions = instructions.buildApplyActions()
                .setActions(actionList)
                .build();

        ArrayList<OFInstruction> instructionList = new ArrayList<>();
        instructionList.add(applyActions);

        Match match = myFactory.buildMatch()
                .setExact(MatchField.ETH_DST, dstMacAddress)
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setMasked(MatchField.IPV4_DST, dstIPv4Address.withMaskOfLength(32))
                .build();

        OFFlowAdd flowAdd = myFactory.buildFlowAdd()
                .setBufferId(OFBufferId.NO_BUFFER)
                .setPriority(32768)
                .setMatch(match)
                .setInstructions(instructionList)
                .setTableId(TableId.of(0))
                .build();
        iofSwitch.write(flowAdd);
    }


    private String pathListToStr(List<DatapathId> list) {
        StringBuilder stringBuilder = new StringBuilder();
        for (DatapathId datapathId : list) {
            stringBuilder.append(datapathId.toString()).append(" --> ");
        }
        return stringBuilder.append("DONE").toString();
    }


    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void switchRemoved(DatapathId switchId) {
        // TODO Auto-generated method stub
    }

    @Override
    public void switchActivated(DatapathId switchId) {
        // TODO Auto-generated method stub
    }

    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
        // TODO Auto-generated method stub
    }

    @Override
    public void switchChanged(DatapathId switchId) {
        // TODO Auto-generated method stub
    }

    @Override
    public void switchDeactivated(DatapathId switchId) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

}
