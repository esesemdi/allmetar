package io.github.esesemdi.allmetar.core;

import java.io.IOException;

/** Minimal HTTP GET abstraction so the sync logic can be tested without a network. */
public interface Fetcher {
    /** Returns the response body; an empty string when the server has no content. */
    String get(String url) throws IOException;
}
