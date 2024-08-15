package hiddenlayer

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic

import hiddenlayer.models.ModelInfo
import hiddenlayer.models.ModelStatus
import hiddenlayer.models.MultipartUploadResponse

import java.net.http.HttpRequest
import java.net.http.HttpResponse

import java.util.function.Supplier

import org.slf4j.Logger

/**
 * API class to interact with the HiddenLayer API
 */
@CompileDynamic
class Api extends BaseClient {

    Config config
    Logger log
    Auth auth
    Boolean isSaaS

    Api(Config config, Logger log) {
        super(config.apiUrl + '/api/' + config.apiVersion)

        this.config = config
        this.log = log
        this.auth = new Auth(config, log)
        this.isSaaS = super.isSaaS()
    }

    String createSensor(ModelInfo modelInfo) {
        String endpoint = '/sensors/create'
        String token = auth.authenticateWithHiddenLayer()

        JsonBuilder requestBody = new JsonBuilder()
        String sensorName = modelInfo.toSensorName()
        requestBody {
            plaintext_name sensorName
            adhoc true
            active true
        }
        HttpRequest request = this.newRequestBuilder(endpoint, token)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build()

        Number[] expectedStatusCodes = [HttpURLConnection.HTTP_CREATED]
        HttpResponse<String> response = this.sendRequest(request, expectedStatusCodes)

        JsonSlurper jsonSlurper = new JsonSlurper()
        def jsonResponse = jsonSlurper.parseText(response.body())

        log.info "Created sensor: $sensorName, id: $jsonResponse.sensor_id"

        return jsonResponse.sensor_id as String
    }

    /* groovylint-disable-next-line BuilderMethodWithSideEffects */
    void createScanRequest(ModelInfo modelInfo, String sensorId) {
        String endpoint = '/scan/create/' + sensorId
        String token = auth.authenticateWithHiddenLayer()

        JsonBuilder requestBody = new JsonBuilder()
        requestBody {
        }
        HttpRequest request = this.newRequestBuilder(endpoint, token)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build()

        Number[] expectedStatusCodes = [HttpURLConnection.HTTP_CREATED]
        this.sendRequest(request, expectedStatusCodes)

        log.info "Submitted model for scanning: $modelInfo"
    }

    void submitEnterpriseScanRequest(ModelInfo modelInfo, String sensorId, InputStream inputStream) {
        String endpoint = '/create/' + sensorId

        Supplier<InputStream> streamSupplier = new Supplier<InputStream>() {

            @Override
            InputStream get() {
                return inputStream
            }

        }

        HttpRequest request = this.newRequestBuilder(endpoint, null)
                .setHeader('Content-Type', 'application/octet-stream')
                .POST(HttpRequest.BodyPublishers.ofInputStream(streamSupplier))
                .build()

        Number[] expectedStatusCodes = [HttpURLConnection.HTTP_CREATED]
        this.sendRequest(request, expectedStatusCodes)

        log.info "Submitted model for scanning: $modelInfo"
    }

    MultipartUploadResponse beginMultipartUpload(String sensorId, Long contentLength) {
        String endpoint = '/sensors/' + sensorId + '/upload/begin'
        String token = auth.authenticateWithHiddenLayer()

        HttpRequest request = this.newRequestBuilder(endpoint, token)
                .header('X-Content-Length', String.valueOf(contentLength))
                .POST(HttpRequest.BodyPublishers.ofString(''))
                .build()
        Number[] expectedStatusCodes = [HttpURLConnection.HTTP_OK]

        HttpResponse<String> response = this.sendRequest(request, expectedStatusCodes)

        JsonSlurper jsonSlurper = new JsonSlurper()
        def jsonResponse = jsonSlurper.parseText(response.body())
        return [
            uploadId: jsonResponse.upload_id,
            parts: jsonResponse.parts
        ]
    }

    void completeMultipartUpload(String sensorId, String uploadId) {
        String endpoint = '/sensors/' + sensorId + '/upload/' + uploadId + '/complete'
        String token = auth.authenticateWithHiddenLayer()

        HttpRequest request = this.newRequestBuilder(endpoint, token)
                .POST(HttpRequest.BodyPublishers.ofString(''))
                .build()

        Number[] expectedStatusCodes = [HttpURLConnection.HTTP_NO_CONTENT]

        this.sendRequest(request, expectedStatusCodes)
    }

    void uploadModelPart(String sensorId, String uploadId, Number part, byte[] data) {
        String endpoint = '/sensors/' + sensorId + '/upload/' + uploadId + '/part/' + part
        String token = auth.authenticateWithHiddenLayer()

        HttpRequest request = this.newRequestBuilder(endpoint, token)
                .header('Content-Type', 'application/octet-stream')
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build()

        Number[] expectedStatusCodes = [HttpURLConnection.HTTP_OK]
        this.sendRequest(request, expectedStatusCodes)
    }

    ModelStatus requestModelStatus(String sensorId) {
        String endpoint = this.isSaaS ? '/scan/status/' + sensorId : '/status/' + sensorId
        String token = null
        if (this.isSaaS) {
            // We don't need to authenticate if this is an Enterprise Model Scanner
            token = auth.authenticateWithHiddenLayer()
        }

        HttpRequest request = this.newRequestBuilder(endpoint, token)
                .GET()
                .build()

        Number[] expectedStatusCodes = [HttpURLConnection.HTTP_OK]
        HttpResponse<String> response = this.sendRequest(request, expectedStatusCodes)

        JsonSlurper jsonSlurper = new JsonSlurper()
        def jsonResponse = jsonSlurper.parseText(response.body())
        return [
            status: jsonResponse.status,
            detections: jsonResponse.detections.size.toString()
        ]
    }

    void deleteModel(String sensorId) {
        String endpoint = '/sensors/' + sensorId
        String token = auth.authenticateWithHiddenLayer()

        HttpRequest request = this.newRequestBuilder(endpoint, token)
                .DELETE()
                .build()

        Number[] expectedStatusCodes = [HttpURLConnection.HTTP_NO_CONTENT]
        this.sendRequest(request, expectedStatusCodes)
    }

    protected HttpResponse<String> sendRequest(HttpRequest request, Number[] expectedStatusCodes) {
        Number[] expectedStatusCodesWithAuthError = expectedStatusCodes + [HttpURLConnection.HTTP_UNAUTHORIZED]

        HttpResponse<String> response = super.sendRequest(request, expectedStatusCodesWithAuthError)
        // If unauthorized, attempt to re-auth and try again
        if (response.statusCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            auth.reauthenticateWithHiddenLayer()
            response = super.sendRequest(request, expectedStatusCodes)
        }

        return response
    }

}
