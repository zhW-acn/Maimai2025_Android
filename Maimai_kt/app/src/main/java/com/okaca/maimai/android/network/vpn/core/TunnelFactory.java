package com.okaca.maimai.android.network.vpn.core;

import android.util.Log;

import com.okaca.maimai.android.network.server.HttpRedirectServer;
import com.okaca.maimai.android.network.vpn.tunnel.HttpCapturerTunnel;
import com.okaca.maimai.android.network.vpn.tunnel.RawTunnel;
import com.okaca.maimai.android.network.vpn.tunnel.Tunnel;

import java.net.InetSocketAddress;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class TunnelFactory {
    private final static String TAG = "TunnelFactory";

    public static Tunnel wrap(SocketChannel channel, Selector selector) throws Exception {
        return new RawTunnel(channel, selector);
    }

    public static Tunnel createTunnelByConfig(InetSocketAddress destAddress, Selector selector) throws Exception {
        Log.d(TAG, destAddress.getHostName() + ":" + destAddress.getPort());
        if (destAddress.getAddress() != null) {
            Log.d(TAG, destAddress.getAddress().toString());
        }
        if (destAddress.getHostName().endsWith("wahlap.com") && destAddress.getPort() == 80) {
            Log.d(TAG, "Request for wahlap.com caught");
            return new HttpCapturerTunnel(
                    new InetSocketAddress("127.0.0.1", HttpRedirectServer.Port), selector);
        } else {
            if (destAddress.isUnresolved())
                return new RawTunnel(new InetSocketAddress(destAddress.getHostName(), destAddress.getPort()), selector);
            else
                return new RawTunnel(destAddress, selector);
        }
    }


}

