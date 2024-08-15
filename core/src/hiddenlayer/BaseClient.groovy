package hiddenlayer

import groovy.transform.CompileDynamic

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * BaseClient class to interact with the HiddenLayer API
 */
@CompileDynamic
class BaseClient {

    private final String apiPrefix

    BaseClient(String apiPrefix) {
        this.apiPrefix = apiPrefix
    }

    Boolean isSaaS() {
        URL url = new URL(apiPrefix)
        return url.host.endsWith('hiddenlayer.ai')
    }

    protected HttpRequest.Builder newRequestBuilder(String endpoint, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(apiPrefix + endpoint))
                .header('Content-Type', 'application/json')
                .header('Accept', 'application/json')
        if (token) {
            builder = builder.header('Authorization', 'Bearer ' + token)
        }
        return builder
    }

    protected HttpResponse<String> sendRequest(HttpRequest request, Number[] expectedStatusCodes) {
        HttpClient client = HttpClient.newBuilder().build()
        HttpResponse<String> response
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (Exception e) {
            log.error "Failed to send request $request: $e"
            throw e
        }

        Number responseCode = response.statusCode()
        String body = response.body()
        if (!expectedStatusCodes.contains(responseCode)) {
            log.error "Failed to send request $request: $response $body"
            throw new Exception("Failed to send request $request: $response")
        }

        return response
    }

}
