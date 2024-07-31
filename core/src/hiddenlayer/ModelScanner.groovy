package hiddenlayer

import groovy.transform.CompileDynamic

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

    static Map<String, String> parseModelInfo(Request request, RepoPath modelPath) {
        String[] modelPathParts = modelPath.toPath().split('/')
        String modelPublisher = modelPathParts[2]
        String modelName = modelPathParts[3]
        String modelVersion = modelPathParts[4]
        String fileName = modelPathParts[-1]
        return [
                model_publisher: modelPublisher,
                model_name: modelName,
                model_version: modelVersion,
                file_name: fileName,
                url: request.alternativeRemoteDownloadUrl
        ]
    }

    static String parseModelStatus(Map<String, String> modelStatus) {
        if (modelStatus == null) {
            return null
        }

        if (modelStatus.get('status') != 'done') {
            return null
        }

        return modelStatus.get('detections') == '0' ? 'SAFE' : 'UNSAFE'
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

    Map<String, String> waitForStatus(String sensorId) {
        Number retries = 5
        Number delay = 5
        Map<String, String> modelStatus
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

    Error submitHiddenLayerScan(Map<String, String> modelInfo) {
        // create sensor
        String sensorId = api.createSensor(modelInfo)
        if (sensorId == api.authenticationError) {
            // retry once if authentication failed
            sensorId = api.createSensor(modelInfo)
        }
        if (!sensorId) {
            // todo: handle error
            return new Error('Failed to create sensor')
        }
        sensorCache.put(modelInfo.url, sensorId)

        Error err = api.createScanRequest(modelInfo, sensorId)
        if (err == api.authenticationError) {
            // retry once if authentication failed
            err = api.createScanRequest(modelInfo, sensorId)
        }
        if (err) {
            // todo: handle error
            return new Error('Failed to submit model for scanning')
        }

        return null
    }

    String getHiddenLayerStatus(Map<String, String> modelInfo) {
        String sensorId = sensorCache.get(modelInfo.url)
        if (!sensorId) {
            return null
        }

        Map<String, String> modelStatus = waitForStatus(sensorId)
        return parseModelStatus(modelStatus)
    }

    String getSensorIdForUrl(String url) {
        return sensorCache.get(url)
    }

    void startMissingScanOnBackground(Request request, RepoPath responseRepoPath) {
        Thread.start {
            Map<String, String> modelInfo = modelScanner.parseModelInfo(request, responseRepoPath)
            String sensorId = sensorCache.get(modelInfo.url)
            if (!sensorId) {
                sensorId = api.createSensor(modelInfo)
                if (sensorId == api.authenticationError) {
                    sensorId = api.createSensor(modelInfo)
                }
                if (!sensorId) {
                    log.error "Failed to create sensor for file $responseRepoPath"
                    return
                }
                sensorCache.put(modelInfo.url, sensorId)
            }
            modelScanner.submitHiddenLayerScan(modelInfo)
            repositories.setProperty(responseRepoPath, 'hiddenlayer.status', 'PENDING')
            String modelStatus = modelScanner.getHiddenLayerStatus(modelInfo)
            if (!modelStatus) {
                log.error "Failed to get model status for file $responseRepoPath"
                return
            }
            log.debug "file: $responseRepoPath status: $modelStatus"
            repositories.setProperty(responseRepoPath, 'hiddenlayer.status', modelStatus)
            sensorCache.remove(modelInfo.url)
        }
    }

}
