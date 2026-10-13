package io.leonard.ojp;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;
import java.util.Map;

/** Calls the Pelias autocomplete endpoint configured via {@code micronaut.http.services.pelias.url}. */
@Singleton
public class PeliasClient {

    private final HttpClient client;

    public PeliasClient(@Client(id = "pelias") HttpClient client) {
        this.client = client;
    }

    public PeliasResponse autocomplete(Map<String, String> params) {
        var uri = UriBuilder.of("/v1/autocomplete");
        params.forEach(uri::queryParam);
        return client.toBlocking().retrieve(HttpRequest.GET(uri.build()), PeliasResponse.class);
    }
}
