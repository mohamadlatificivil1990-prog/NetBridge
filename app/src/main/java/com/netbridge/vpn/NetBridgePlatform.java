package com.netbridge.vpn;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
import android.net.VpnService;
import android.os.Build;
import android.system.OsConstants;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.InterfaceAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import io.nekohasekai.libbox.*;

public class NetBridgePlatform implements PlatformInterface {

    private final NetBridgeVpnService service;

    public NetBridgePlatform(NetBridgeVpnService service) {
        this.service = service;
    }

    private StringIterator strings(List<String> values) {
        return new StringIterator() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < values.size();
            }

            @Override
            public int len() {
                return values.size() - index;
            }

            @Override
            public String next() {
                return values.get(index++);
            }
        };
    }

    @Override
    public void autoDetectInterfaceControl(int fd) throws Exception {
        if (!service.protect(fd)) {
            throw new Exception("Failed to protect socket from VPN");
        }
    }

    @Override
    public boolean usePlatformAutoDetectInterfaceControl() {
        return true;
    }

    @Override
    public int openTun(TunOptions options) throws Exception {
        return service.openTunInterface(options);
    }

    @Override
    public NetworkInterfaceIterator getInterfaces() throws Exception {
        List<io.nekohasekai.libbox.NetworkInterface> result =
                new ArrayList<>();

        ConnectivityManager cm =
                (ConnectivityManager) service.getSystemService(
                        VpnService.CONNECTIVITY_SERVICE);

        Enumeration<NetworkInterface> systemInterfaces =
                NetworkInterface.getNetworkInterfaces();

        List<NetworkInterface> available =
                Collections.list(systemInterfaces);

        for (Network network : cm.getAllNetworks()) {
            LinkProperties lp = cm.getLinkProperties(network);
            NetworkCapabilities nc = cm.getNetworkCapabilities(network);

            if (lp == null || nc == null || lp.getInterfaceName() == null) {
                continue;
            }

            NetworkInterface systemInterface = null;

            for (NetworkInterface item : available) {
                if (item.getName().equals(lp.getInterfaceName())) {
                    systemInterface = item;
                    break;
                }
            }

            if (systemInterface == null) continue;

            io.nekohasekai.libbox.NetworkInterface item =
                    new io.nekohasekai.libbox.NetworkInterface();

            item.setName(systemInterface.getName());
            item.setIndex(systemInterface.getIndex());

            try {
                item.setMTU(systemInterface.getMTU());
            } catch (Exception ignored) {
                item.setMTU(1500);
            }

            List<String> addresses = new ArrayList<>();

            for (InterfaceAddress address :
                    systemInterface.getInterfaceAddresses()) {
                if (address.getAddress() != null) {
                    addresses.add(
                            address.getAddress().getHostAddress()
                                    + "/" + address.getNetworkPrefixLength());
                }
            }

            item.setAddresses(strings(addresses));

            List<String> dns = new ArrayList<>();
            for (InetAddress address : lp.getDnsServers()) {
                dns.add(address.getHostAddress());
            }
            item.setDNSServer(strings(dns));

            List<String> gateways = new ArrayList<>();
            lp.getRoutes().forEach(route -> {
                if (route.isDefaultRoute() && route.getGateway() != null
                        && !route.getGateway().isAnyLocalAddress()) {
                    gateways.add(route.getGateway().getHostAddress());
                }
            });
            item.setGateway(strings(gateways));

            int type = Libbox.InterfaceTypeOther;

            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                type = Libbox.InterfaceTypeWIFI;
            } else if (nc.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR)) {
                type = Libbox.InterfaceTypeCellular;
            } else if (nc.hasTransport(
                    NetworkCapabilities.TRANSPORT_ETHERNET)) {
                type = Libbox.InterfaceTypeEthernet;
            }

            item.setType(type);

            int flags = 0;
            if (nc.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                flags = OsConstants.IFF_UP | OsConstants.IFF_RUNNING;
            }

            if (systemInterface.isLoopback()) {
                flags |= OsConstants.IFF_LOOPBACK;
            }

            if (systemInterface.isPointToPoint()) {
                flags |= OsConstants.IFF_POINTOPOINT;
            }

            if (systemInterface.supportsMulticast()) {
                flags |= OsConstants.IFF_MULTICAST;
            }

            item.setFlags(flags);

            item.setMetered(!nc.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED));

            result.add(item);
        }

        return new NetworkInterfaceIterator() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < result.size();
            }

            @Override
            public io.nekohasekai.libbox.NetworkInterface next() {
                return result.get(index++);
            }
        };
    }

    @Override
    public boolean includeAllNetworks() {
        return false;
    }

    @Override
    public boolean underNetworkExtension() {
        return false;
    }

    @Override
    public boolean usePlatformBridge() {
        return false;
    }

    @Override
    public boolean usePlatformShell() {
        return false;
    }

    @Override
    public boolean useProcFS() {
        return false;
    }

    @Override
    public void checkPlatformShell() {
        throw new UnsupportedOperationException("Root shell is disabled");
    }

    @Override
    public BridgeSession createBridge(BridgeOptions options) {
        throw new UnsupportedOperationException("Bridge is unavailable");
    }

    @Override
    public ShellSession openShellSession(
            PlatformUser user,
            String command,
            StringIterator environ,
            String term,
            int rows,
            int cols) {
        throw new UnsupportedOperationException("Shell is unavailable");
    }

    @Override
    public PlatformUser lookupUser(String username) {
        throw new UnsupportedOperationException("User lookup is unavailable");
    }

    @Override
    public String lookupSFTPServer() {
        return "";
    }

    @Override
    public String readSystemSSHHostKey() {
        return "";
    }

    @Override
    public String tailscaleHostname() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    @Override
    public WIFIState readWIFIState() {
        return null;
    }

    @Override
    public LocalDNSTransport localDNSTransport() {
        return null;
    }

    @Override
    public ConnectionOwner findConnectionOwner(
            int ipProtocol,
            String sourceAddress,
            int sourcePort,
            String destinationAddress,
            int destinationPort) throws Exception {

        if (Build.VERSION.SDK_INT < 29) return new ConnectionOwner();

        ConnectivityManager cm =
                (ConnectivityManager) service.getSystemService(
                        VpnService.CONNECTIVITY_SERVICE);

        int uid = cm.getConnectionOwnerUid(
                ipProtocol,
                new InetSocketAddress(sourceAddress, sourcePort),
                new InetSocketAddress(destinationAddress, destinationPort));

        ConnectionOwner owner = new ConnectionOwner();
        owner.setUserId(uid);
        return owner;
    }

    @Override
    public void startDefaultInterfaceMonitor(
            InterfaceUpdateListener listener) {
    }

    @Override
    public void closeDefaultInterfaceMonitor(
            InterfaceUpdateListener listener) {
    }

    @Override
    public void startNeighborMonitor(NeighborUpdateListener listener) {
    }

    @Override
    public void closeNeighborMonitor(NeighborUpdateListener listener) {
    }

    @Override
    public void clearDNSCache() {
    }

    @Override
    public void registerMyInterface(String name) {
    }

    @Override
    public void sendNotification(Notification notification) {
        Log.d("NetBridge", "sing-box notification: "
                + notification.getTitle());
    }

    @Override
    public void cancelNotification(String identifier, int typeID) {
    }
}
