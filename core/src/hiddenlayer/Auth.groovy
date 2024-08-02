package hiddenlayer

import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

import org.slf4j.Logger

/**
 * Auth class to authenticate with HiddenLayer
 */
@CompileDynamic
class Auth extends BaseClient {

    static Error authenticationError = new Error('Failed to authenticate with hiddenlayer')

    Config config
    Logger log

    Map<String, String> tokenCache = [:]

    Auth(Config config, Logger log) {
        super(config.AuthUrl)

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

        String authKey = config.AuthKey
        String endpoint = '/oauth2/token?grant_type=client_credentials'
        HttpRequest request = this.newRequestBuilder(endpoint, null)
                .header('Authorization', 'Basic ' + authKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
        Number[] expectedStatusCodes = [HttpURLConnection.HTTP_OK]

        HttpResponse<String> response = this.sendRequest(request, expectedStatusCodes)

        JsonSlurper jsonSlurper = new JsonSlurper()
        def jsonResponse = jsonSlurper.parseText(response.body())
        String accessToken = jsonResponse.access_token as String
        Number expiresIn = jsonResponse.expires_in as Number

        tokenCache.put('token', accessToken)
        tokenCache.put('expires', Instant.now().plusSeconds(expiresIn.toLong()))
        return accessToken
    }

    String reauthenticateWithHiddenLayer() {
        tokenCache.clear()
        return this.authenticateWithHiddenLayer()
    }

}