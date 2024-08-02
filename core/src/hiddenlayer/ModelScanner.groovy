package hiddenlayer

import groovy.transform.CompileDynamic

import hiddenlayer.models.ModelInfo
import hiddenlayer.models.ModelStatus

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import org.slf4j.Logger
import java.security.SecureRandom

/**
 * ModelScanner class to scan models
 */
@CompileDynamic
class ModelScanner {

    Config config
    Api api
    Logger log
    Map<String, String> sensorCache = [:]

    ModelScanner(Config config, Api api, Logger log) {
        this.config = config
        this.api = api
        this.log = log
    }

    static ModelInfo parseModelInfo(RepoPath modelPath) {
        String[] modelPathParts = modelPath.toPath().split('/')
        String modelPublisher = modelPathParts[2]
        String modelName = modelPathParts[3]
        String modelVersion = modelPathParts[4]
        String fileName = modelPathParts[-1]
        return [
                modelPublisher: modelPublisher,
                modelName: modelName,
                modelVersion: modelVersion,
                fileName: fileName,
                repoPath: modelPath.toPath(),
        ]
    }

    static String parseModelStatus(ModelStatus modelStatus) {
        if (modelStatus == null) {
            return null
        }

        if (modelStatus.status != 'done') {
            return null
        }

        return modelStatus.detections == '0' ? 'SAFE' : 'UNSAFE'
    }

    boolean shouldScanRepo(String repoKey) {
        try {
            String[] scanRepos = config.scanRepos
            if (repoKey.endsWith('-cache')) {
                repoKey = repoKey.substring(0, repoKey.length() - 6)
            }

            return scanRepos && scanRepos.contains(repoKey)
        } catch (Exception e) {
            log.error "Error checking if repo should be scanned: $e"
            throw e
        }
    }

    ModelStatus waitForStatus(String sensorId) {
        Number retries = 5
        Number delay = 5
        ModelStatus modelStatus
        while (true) {
            if (retries == 0) {
                break
            }
            retries--
            delay *= 2 + (new SecureRandom().nextDouble() * 0.1)
            Thread.sleep(delay.toLong() * 1000)

            log.info "Checking model status for sensor $sensorId"
            modelStatus = api.requestModelStatus(sensorId)
            if (modelStatus.status == 'done' || modelStatus.status == 'failed') {
                break
            }
        }
        if (modelStatus == null) {
            /* groovylint-disable-next-line ReturnsNullInsteadOfEmptyCollection */
            return null
        }
        return modelStatus
    }

    void submitHiddenLayerScan(ModelInfo modelInfo, content) {
        // create sensor
        String sensorId = api.createSensor(modelInfo)
        if (sensorId == Auth.authenticationError) {
            // retry once if authentication failed
            sensorId = api.createSensor(modelInfo)
        }
        if (!sensorId) {
            // todo: handle error
            throw new Exception('Failed to create sensor')
        }
        sensorCache.put(modelInfo.repoPath, sensorId)

        def upload = api.beginMultipartUpload(sensorId, content.size)
        def inputStream = content.getInputStream()
        for (Number i = 0; i < upload.parts.size(); i++) {
            def part = upload.parts[i]
            def partSize = part.end_offset - part.start_offset
            def buffer = new byte[partSize]
            byte[] bytes = inputStream.readNBytes(partSize)
            if (part.upload_url) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(part.upload_url))
                        .header('Content-Type', 'application/octet-stream')
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .build()
                HttpClient client = HttpClient.newBuilder().build()
                HttpResponse<String> response
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofString())
                } catch (Exception e) {
                    log.error "Failed to upload model part: $e"
                    throw e
                }

                Number responseCode = response.statusCode()
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    log.error "Failed to upload model part: $response"
                    /* groovylint-disable-next-line ReturnsNullInsteadOfEmptyCollection */
                    throw new Exception("Failed to upload model part: $response")
                }
            } else {
                api.uploadModelPart(sensorId, upload.uploadId, part.part_number, buffer)
            }
        }
        api.completeMultipartUpload(sensorId, upload.uploadId)
        api.createScanRequest(modelInfo, sensorId)
    }

    String getHiddenLayerStatus(ModelInfo modelInfo) {
        String sensorId = sensorCache.get(modelInfo.repoPath)
        if (!sensorId) {
            return null
        }

        ModelStatus modelStatus = waitForStatus(sensorId)
        return parseModelStatus(modelStatus)
    }

    String getSensorIdForUrl(String url) {
        return sensorCache.get(url)
    }

    void startMissingScanOnBackground(RepoPath responseRepoPath) {
        Thread.start {
            ModelInfo modelInfo = modelScanner.parseModelInfo(responseRepoPath)
            String sensorId = sensorCache.get(modelInfo.repoPath)
            if (!sensorId) {
                sensorId = api.createSensor(modelInfo)
                sensorCache.put(modelInfo.repoPath, sensorId)
            }
            submitHiddenLayerScan(modelInfo)
            repositories.setProperty(responseRepoPath, 'hiddenlayer.status', 'PENDING')
            String modelStatus = getHiddenLayerStatus(modelInfo)
            if (!modelStatus) {
                log.error "Failed to get model status for file $responseRepoPath"
                return
            }
            log.debug "file: $responseRepoPath status: $modelStatus"
            repositories.setProperty(responseRepoPath, 'hiddenlayer.status', modelStatus)
            sensorCache.remove(modelInfo.repoPath)
        }
    }

}
