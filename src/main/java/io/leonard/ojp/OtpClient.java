package io.leonard.ojp;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;

/**
 * Forwards OJP requests unchanged to the OTP OJP endpoint configured via
 * {@code micronaut.http.services.otp.url}.
 */
@Singleton
public class OtpClient {

    private final HttpClient client;

    public OtpClient(@Client(id = "otp") HttpClient client) {
        this.client = client;
    }

    /** Returns OTP's response as-is, including error statuses. */
    public HttpResponse<byte[]> forward(byte[] body, MediaType contentType) {
        var request = HttpRequest.POST("", body).contentType(contentType);
        HttpResponse<byte[]> upstream;
        try {
            upstream = client.toBlocking().exchange(request, byte[].class);
        } catch (HttpClientResponseException e) {
            upstream = (HttpResponse<byte[]>) e.getResponse();
        }
        var response = HttpResponse.<byte[]>status(upstream.getStatus());
        upstream.getContentType().ifPresent(response::contentType);
        upstream.getBody(byte[].class).ifPresent(response::body);
        return response;
    }
}
