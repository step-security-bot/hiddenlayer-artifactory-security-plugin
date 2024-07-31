package hiddenlayer

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

import org.slf4j.Logger

/**
 * API class to interact with the HiddenLayer API
 */
@CompileDynamic
class Api {

    Error authenticationError = new Error('Failed to authenticate with hiddenlayer')

    Config config
    Logger log

    Map<String, String> tokenCache = [:]

    Api(Config config, Logger log) {
        this.config = config
        this.log = log
    }

    String authenticateWithHiddenLayer() {
        if (tokenCache.containsKey('token')
            && tokenCache.containsKey('expires')
            && tokenCache.expires.isAfter(Instant.now())) {
            return tokenCache.token
        }

        if (config.AuthKey == '' || config.AuthKey == null) {
            return null
        }

        String authUrl = config.AuthUrl
        String authKey = config.AuthKey
        HttpRequest request = newRequestBuilder()
                .uri(URI.create(authUrl + '/oauth2/token?grant_type=client_credentials'))
                .header('Authorization', 'Basic ' + authKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
        HttpClient client = HttpClient.newBuilder().build()
        HttpResponse<String> response
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (Exception e) {
            log.error "Failed to authenticate with hiddenlayer: $e"
            throw e
        }

        Number responseCode = response.statusCode()
        if (responseCode != HttpURLConnection.HTTP_OK) {
            log.error "Failed to authenticate with hiddenlayer: $response"
            return null
        }

        JsonSlurper jsonSlurper = new JsonSlurper()
        def jsonResponse = jsonSlurper.parseText(response.body())
        String accessToken = jsonResponse.access_token as String
        Number expiresIn = jsonResponse.expires_in as Number

        tokenCache.put('token', accessToken)
        tokenCache.put('expires', Instant.now().plusSeconds(expiresIn.toLong()))
        return accessToken
    }

    String createSensor(Map<String, String> modelInfo) {
        String apiUrl = config.apiUrl
        String apiVersion = config.apiVersion
        String token = authenticateWithHiddenLayer()

        String publisher = modelInfo.model_publisher
        String name = modelInfo.model_name
        String version = modelInfo.model_version
        String file = modelInfo.file_name

        JsonBuilder requestBody = new JsonBuilder()
        String sensorName = 'jfrog:' + publisher + '/' + name +  ':' + version + ':' + file
        requestBody {
            plaintext_name sensorName
            active true
        }
        HttpRequest request = newRequestBuilder(token)
                .uri(URI.create(apiUrl + '/api/' + apiVersion + '/sensors/create'))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build()
        HttpClient client = HttpClient.newBuilder().build()
        HttpResponse<String> response
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (Exception e) {
            log.error "Failed to create sensor: $e"
            throw e
        }

        Number responseCode = response.statusCode()
        if (responseCode != HttpURLConnection.HTTP_CREATED) {
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                tokenCache.remove('token')
                return authenticationError
            }
            log.error "Failed to create sensor: $response"
            return null
        }

        JsonSlurper jsonSlurper = new JsonSlurper()
        def jsonResponse = jsonSlurper.parseText(response.body())

        log.info "Created sensor: $sensorName, id: $jsonResponse.sensor_id"

        return jsonResponse.sensor_id as String
    }

    Error createScanRequest(Map<String, String> modelInfo, String sensorId) {
        String apiUrl = config.apiUrl
        String apiVersion = config.apiVersion
        String token = authenticateWithHiddenLayer()
        JsonBuilder requestBody = new JsonBuilder()
        requestBody {
            location modelInfo.url
        }
        HttpRequest request = newRequestBuilder(token)
                .uri(URI.create(apiUrl + '/api/' + apiVersion + '/scan/create/' + sensorId))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build()
        HttpClient client = HttpClient.newBuilder().build()
        HttpResponse<String> response
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (Exception e) {
            log.error "Failed to submit model for scanning: $e"
            return new Error('Failed to submit model for scanning')
        }

        Number responseCode = response.statusCode()
        if (responseCode != HttpURLConnection.HTTP_CREATED) {
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                tokenCache.remove('token')
                return authenticationError
            }
            log.error "Failed to submit model for scanning: $response"
            return new Error('Failed to submit model for scanning')
        }

        log.info "Submitted model for scanning: $modelInfo"
        return null
    }

    Map<String, String> requestModelStatus(String sensorId) {
        String apiUrl = config.apiUrl
        String apiVersion = config.apiVersion
        String token = authenticateWithHiddenLayer()

        HttpRequest request = newRequestBuilder(token)
                .uri(URI.create(apiUrl + '/api/' + apiVersion + '/scan/status/' + sensorId))
                .GET()
                .build()
        HttpClient client = HttpClient.newBuilder().build()
        HttpResponse<String> response
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (Exception e) {
            log.error "Failed to get model status: $e"
            throw e
        }

        Number responseCode = response.statusCode()
        if (responseCode != HttpURLConnection.HTTP_OK) {
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                tokenCache.remove('token')
                return authenticationError
            }
            log.error "Failed to get model status: $response"
            /* groovylint-disable-next-line ReturnsNullInsteadOfEmptyCollection */
            return null
        }

        JsonSlurper jsonSlurper = new JsonSlurper()
        def jsonResponse = jsonSlurper.parseText(response.body())
        return [
            status: jsonResponse.status,
            detections: jsonResponse.detections.size.toString()
        ]
    }

    private HttpRequest.Builder newRequestBuilder(String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header('Content-Type', 'application/json')
                .header('Accept', 'application/json')
        if (token) {
            builder = builder.header('Authorization', 'Bearer ' + token)
        }
        return builder
    }

}
