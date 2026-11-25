package io.github.esesemdi.allmetar.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class HttpFetcher implements Fetcher {
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private final String userAgent;
    private final int connectMs;
    private final int readMs;

    public HttpFetcher(String userAgent, int connectMs, int readMs) {
        this.userAgent = userAgent;
        this.connectMs = connectMs;
        this.readMs = readMs;
    }

    @Override
    public String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(connectMs);
            c.setReadTimeout(readMs);
            c.setRequestProperty("User-Agent", userAgent);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code == HttpURLConnection.HTTP_NO_CONTENT) {
                return "";
            }
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server answered HTTP " + code);
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) {
                        throw new IOException("Response too large");
                    }
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }
}
