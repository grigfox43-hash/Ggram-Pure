package org.ggram.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import org.ggram.config.GgramConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * GgramProxyManager - Multi-protocol proxy manager with remote dynamic GitLab sync,
 * latency benchmarking, and automatic fastest node activation.
 */
public class GgramProxyManager {

    private static final String TAG = "GgramProxyManager";

    public static final String GITLAB_SNIPPET_URL = "https://gitlab.com/-/snippets/6051835/raw";
    public static final String GITLAB_REPO_URL = "https://gitlab.com/grigfox43/main/-/raw/main/proxies.json";

    public enum ProxyType {
        MTPROTO,
        SOCKS5,
        SHADOWSOCKS,
        V2RAY
    }

    public static class ProxyServer {
        public String id;
        public String name;
        public ProxyType type;
        public String host;
        public int port;
        public String secret;
        public String username;
        public String password;
        public long pingMs = -1;
        public boolean isOnline = false;

        public ProxyServer(String id, String name, ProxyType type, String host, int port, String secret) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.host = host;
            this.port = port;
            this.secret = secret;
        }

        public ProxyServer(String id, String name, ProxyType type, String host, int port, String username, String password) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }
    }

    private static final List<ProxyServer> proxyList = Collections.synchronizedList(new ArrayList<>());
    private static final ExecutorService executor = Executors.newFixedThreadPool(8);
    private static volatile boolean isInitialized = false;

    private static Runnable rotateRunnable = null;
    private static int currentProxyIndex = 0;

    public static void init(Context context) {
        if (isInitialized) return;
        isInitialized = true;

        setupDefaultProxies();
        Log.i(TAG, "GgramProxyManager initialized with " + proxyList.size() + " default anti-censorship nodes");

        // First launch & authorization fix:
        // If proxy is not enabled or not set, activate node #1 immediately so first login/SMS request works!
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean proxyEnabled = preferences.getBoolean("proxy_enabled", false);
        String currentAddress = preferences.getString("proxy_ip", "");
        if (!proxyEnabled || TextUtils.isEmpty(currentAddress) || SharedConfig.currentProxy == null) {
            if (!proxyList.isEmpty()) {
                Log.i(TAG, "First launch/login: Auto-activating Russian Fake-TLS MTProto proxy immediately");
                applyProxyToTelegram(proxyList.get(0));
            }
        }

        // Sync from GitLab dynamically in background
        fetchRemoteProxies();
    }

    private static void setupDefaultProxies() {
        if (!proxyList.isEmpty()) return;

        // Verified Russian-ready Fake-TLS (ee) and low latency MTProto nodes
        addDefaultNode("1", "⚡ Russia Fast 1 (Port 443)", ProxyType.MTPROTO, "194.117.64.10", 443, "ee1603010200010001fc030386e24c3add626973636f7474692e79656b74616e65742e636f6d");
        addDefaultNode("2", "⚡ Russia Fast 2 (Port 443)", ProxyType.MTPROTO, "194.117.64.5", 443, "ee1603010200010001fc030386e24c3add626973636f7474692e79656b74616e65742e636f6d");
        addDefaultNode("3", "🎮 Steam CDN Fake-TLS 1", ProxyType.MTPROTO, "udymau.server-space52.info", 443, "ee1603010200010001fc030386e24c3add6d656469612e737465616d706f77657265642e636f6d");
        addDefaultNode("4", "🎮 Steam CDN Fake-TLS 2", ProxyType.MTPROTO, "server3.server-space52.info", 443, "ee1603010200010001fc030386e24c3add6d656469612e737465616d706f77657265642e636f6d");
        addDefaultNode("5", "🎮 Steam CDN Fake-TLS 3", ProxyType.MTPROTO, "ping-pong.mangom-kangom.info", 443, "ee1603010200010001fc030386e24c3add6d656469612e737465616d706f77657265642e636f6d");
        addDefaultNode("6", "🌐 Meow Network (Port 443)", ProxyType.MTPROTO, "t.meow-network.com", 443, "ee5622e11fff3e49bcc85280197a6106b5742e6d656f772d6e6574776f726b2e636f6d");
        addDefaultNode("7", "🛡️ Cloud EU 1 (Port 2053)", ProxyType.MTPROTO, "run.golgoli2.co.uk", 2053, "eeNEgYdJvXrFGRMCIMJdCQ");
        addDefaultNode("8", "🛡️ Cloud EU 2 (Port 2096)", ProxyType.MTPROTO, "new.lambforkebeb.co.uk", 2096, "eeNEgYdJvXrFGRMCIMJdCQ");
        addDefaultNode("9", "🛡️ Cloud EU 3 (Port 2096)", ProxyType.MTPROTO, "gallery.talebi.co.uk", 2096, "eeNEgYdJvXrFGRMCIMJdCQ");
        addDefaultNode("10", "🛡️ Cloud EU 4 (Port 8880)", ProxyType.MTPROTO, "you.foltmeingop.co.uk", 8880, "eeNEgYdJvXrFGRMCIMJdCQ");
        addDefaultNode("11", "🛡️ Cloud EU 5 (Port 8443)", ProxyType.MTPROTO, "uptime.speed-benz.co.uk", 8443, "eeNEgYdJvXrFGRMCIMJdCQ");
        addDefaultNode("12", "🛡️ Cloud EU 6 (Port 2053)", ProxyType.MTPROTO, "hadaf.golgoli2.co.uk", 2053, "eeNEgYdJvXrFGRMCIMJdCQ");
    }

    public static void onConnectionState(int state) {
        if (!GgramConfig.isAutoProxyEnabled) return;

        if (state == ConnectionsManager.ConnectionStateConnected) {
            if (rotateRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(rotateRunnable);
                rotateRunnable = null;
            }
            return;
        }

        if (state == ConnectionsManager.ConnectionStateConnecting ||
            state == ConnectionsManager.ConnectionStateWaitingForNetwork ||
            state == ConnectionsManager.ConnectionStateConnectingToProxy) {

            if (rotateRunnable == null) {
                rotateRunnable = () -> {
                    rotateRunnable = null;
                    rotateToNextProxy();
                };
                AndroidUtilities.runOnUIThread(rotateRunnable, 7000);
            }
        }
    }

    public static synchronized void rotateToNextProxy() {
        if (proxyList.isEmpty()) return;
        currentProxyIndex = (currentProxyIndex + 1) % proxyList.size();
        ProxyServer next = proxyList.get(currentProxyIndex);
        Log.i(TAG, "Auto-failover: rotating to next proxy node: " + next.host + ":" + next.port);
        applyProxyToTelegram(next);
    }

    private static void addDefaultNode(String id, String name, ProxyType type, String host, int port, String secret) {
        ProxyServer server = new ProxyServer(id, name, type, host, port, secret);
        proxyList.add(server);
        try {
            SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(host, port, "", "", secret != null ? secret : "");
            SharedConfig.addProxy(info);
        } catch (Throwable ignore) {}
    }

    public static void fetchRemoteProxies() {
        executor.execute(() -> {
            String jsonContent = downloadUrl(GITLAB_SNIPPET_URL);
            if (TextUtils.isEmpty(jsonContent)) {
                jsonContent = downloadUrl(GITLAB_REPO_URL);
            }
            if (TextUtils.isEmpty(jsonContent)) {
                Log.w(TAG, "Remote proxy list unreachable, using built-in defaults");
                if (GgramConfig.isAutoProxyEnabled && SharedConfig.currentProxy == null) {
                    autoSelectFastestProxy();
                }
                return;
            }

            try {
                JSONObject root = new JSONObject(jsonContent);
                JSONArray array = root.optJSONArray("proxies");
                if (array != null && array.length() > 0) {
                    int addedCount = 0;
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String server = obj.optString("server", "").trim();
                        int port = obj.optInt("port", 443);
                        String secret = obj.optString("secret", "").trim();
                        String name = obj.optString("name", "Ggram Node " + (i + 1));

                        if (!TextUtils.isEmpty(server) && port > 0) {
                            boolean exists = false;
                            for (ProxyServer p : proxyList) {
                                if (p.host.equalsIgnoreCase(server) && p.port == port) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                ProxyServer ps = new ProxyServer("remote_" + i, name, ProxyType.MTPROTO, server, port, secret);
                                proxyList.add(ps);
                                SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(server, port, "", "", secret);
                                SharedConfig.addProxy(info);
                                addedCount++;
                            }
                        }
                    }
                    Log.i(TAG, "Successfully synced " + addedCount + " new proxies from GitLab");
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            if (GgramConfig.isAutoProxyEnabled && SharedConfig.currentProxy == null) {
                autoSelectFastestProxy();
            }
        });
    }

    private static String downloadUrl(String targetUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "Ggram-Client");
            conn.setRequestProperty("Accept", "application/json, text/plain");
            conn.connect();

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to download from " + targetUrl + ": " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {}
            }
        }
        return null;
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

            Context context = ApplicationLoader.applicationContext;
            if (context != null) {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                preferences.edit()
                        .putString("proxy_ip", info.address)
                        .putString("proxy_pass", info.password)
                        .putString("proxy_user", info.username)
                        .putString("proxy_secret", info.secret)
                        .putInt("proxy_port", info.port)
                        .putBoolean("proxy_enabled", true)
                        .putBoolean("proxy_enabled_calls", false)
                        .apply();
            }

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
