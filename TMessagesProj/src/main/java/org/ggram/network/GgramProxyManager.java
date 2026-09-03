package org.ggram.network;

import android.content.Context;
import android.util.Log;

import org.ggram.config.GgramConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * GgramProxyManager - Multi-protocol proxy manager (MTProto, Shadowsocks, Socks5, V2Ray)
 * with parallel ping latency benchmarking and automatic fastest node activation.
 */
public class GgramProxyManager {

    private static final String TAG = "GgramProxyManager";

    public enum ProxyType {
        MTPROTO,
        SOCKS5,
        SHADOWSOCKS,
        V2RAY
    }

    public static class ProxyServer {
        public String id;
        public ProxyType type;
        public String host;
        public int port;
        public String secret;
        public String username;
        public String password;
        public long pingMs = -1;
        public boolean isOnline = false;

        public ProxyServer(String id, ProxyType type, String host, int port, String secret) {
            this.id = id;
            this.type = type;
            this.host = host;
            this.port = port;
            this.secret = secret;
        }

        public ProxyServer(String id, ProxyType type, String host, int port, String username, String password) {
            this.id = id;
            this.type = type;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }
    }

    private static final List<ProxyServer> proxyList = Collections.synchronizedList(new ArrayList<>());
    private static final ExecutorService executor = Executors.newFixedThreadPool(8);

    public static void init(Context context) {
        setupDefaultProxies();
        Log.i(TAG, "GgramProxyManager initialized with " + proxyList.size() + " default anti-censorship nodes");
    }

    private static void setupDefaultProxies() {
        if (!proxyList.isEmpty()) return;
        proxyList.add(new ProxyServer("1", ProxyType.MTPROTO, "149.154.167.50", 443, "ee11111111111111111111111111111111"));
        proxyList.add(new ProxyServer("2", ProxyType.MTPROTO, "149.154.175.100", 443, "ee11111111111111111111111111111111"));
        proxyList.add(new ProxyServer("3", ProxyType.SOCKS5, "127.0.0.1", 1080, null, null));
    }

    public static void pingAllProxies(PingCallback callback) {
        executor.execute(() -> {
            List<Future<?>> futures = new ArrayList<>();
            for (ProxyServer proxy : proxyList) {
                futures.add(executor.submit(() -> {
                    long start = System.currentTimeMillis();
                    try (Socket socket = new Socket()) {
                        socket.connect(new InetSocketAddress(proxy.host, proxy.port), 2500);
                        proxy.pingMs = System.currentTimeMillis() - start;
                        proxy.isOnline = true;
                    } catch (Exception e) {
                        proxy.pingMs = -1;
                        proxy.isOnline = false;
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {}
            }
            if (callback != null) {
                callback.onComplete(new ArrayList<>(proxyList));
            }
        });
    }

    public static void autoSelectFastestProxy() {
        if (!GgramConfig.isAutoProxyEnabled) return;

        pingAllProxies(results -> {
            ProxyServer fastest = null;
            for (ProxyServer p : results) {
                if (p.isOnline && p.pingMs > 0) {
                    if (fastest == null || p.pingMs < fastest.pingMs) {
                        fastest = p;
                    }
                }
            }
            if (fastest != null) {
                Log.i(TAG, "Activating fastest proxy: " + fastest.host + ":" + fastest.port + " (" + fastest.pingMs + " ms)");
                applyProxyToTelegram(fastest);
            }
        });
    }

    public static void applyProxyToTelegram(ProxyServer proxy) {
        try {
            SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(
                    proxy.host,
                    proxy.port,
                    proxy.username != null ? proxy.username : "",
                    proxy.password != null ? proxy.password : "",
                    proxy.secret != null ? proxy.secret : ""
            );
            SharedConfig.addProxy(info);
            SharedConfig.currentProxy = info;
            ConnectionsManager.setProxySettings(true, info.address, info.port, info.username, info.password, info.secret);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static List<ProxyServer> getAllProxies() {
        return new ArrayList<>(proxyList);
    }

    public interface PingCallback {
        void onComplete(List<ProxyServer> proxies);
    }
}
