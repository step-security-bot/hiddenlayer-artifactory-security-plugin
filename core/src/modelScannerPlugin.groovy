import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import hiddenlayer.Api
import hiddenlayer.Auth
import hiddenlayer.Config
import hiddenlayer.models.ModelInfo
import hiddenlayer.ModelScanner

config = new Config(ctx)
api = new Api(config, log)
auth = new Auth(config, log)
modelScanner = new ModelScanner(config, api, log)

ARTIFACT_STATUS_SAFE = 'SAFE'
ARTIFACT_STATUS_UNSAFE = 'UNSAFE'
ARTIFACT_STATUS_PENDING = 'PENDING'

download {
    altResponse { Request request, RepoPath responseRepoPath ->
        try {
            if (!modelScanner.shouldScanRepo(responseRepoPath.repoKey)) {
                return
            }

            def artifactStatus = repositories.getProperties(responseRepoPath).getFirst('hiddenlayer.status')
            log.info "file: $responseRepoPath status: $artifactStatus"
            if (artifactStatus == ARTIFACT_STATUS_UNSAFE) {
                status = HttpURLConnection.HTTP_FORBIDDEN
                message = 'Artifact is unsafe'
            }
            if (artifactStatus == ARTIFACT_STATUS_PENDING) {
                status = HttpURLConnection.HTTP_FORBIDDEN
                message = 'Artifact is being scanned by hiddenlayer'
            }

            if (artifactStatus != ARTIFACT_STATUS_SAFE) {
                log.error("Artifact is not in an expected state: $artifactStatus")
            // we should not be here, but here we are!
            // this means the model was allowed to download without a decision from hiddenlayer,
            // so we will let the user decide what to do from here.

                if (config.scanMissingRetry == true) {
                    modelScanner.startMissingScanOnBackground(responseRepoPath)
                }
                if (config.scanDecisionMissing == 'deny') {
                    status = HttpURLConnection.HTTP_FORBIDDEN
                    message = 'Artifact has not been scanned by hiddenlayer'
                }
            }
    } catch (Exception e) {
            log.error "Error handling altResponse: $e"
            throw e
        }
    }

    beforeDownload { Request request, RepoPath responseRepoPath ->
        try {
            if (!modelScanner.shouldScanRepo(responseRepoPath.repoKey)) {
                return
            }

            log.info "beforeDownload: $responseRepoPath"

            ModelInfo modelInfo = modelScanner.parseModelInfo(responseRepoPath)
            def properties = repositories.getProperties(responseRepoPath)
            log.info "file: $responseRepoPath properties: $properties"
            def artifactStatus = repositories.getProperties(responseRepoPath).getFirst('hiddenlayer.status')
            String sensorId = modelScanner.getSensorIdForUrl(modelInfo.repoPath)

            if (artifactStatus == ARTIFACT_STATUS_UNSAFE) {
                log.warn "Attempted to download unsafe file $responseRepoPath"
                return new Error('Artifact is unsafe')
            }
            if (artifactStatus == ARTIFACT_STATUS_PENDING && sensorId) {
                return new Error('Artifact is being scanned by hiddenlayer')
            }
            if (artifactStatus != ARTIFACT_STATUS_SAFE || (artifactStatus == ARTIFACT_STATUS_PENDING && !sensorId)) {
                repositories.setProperty(responseRepoPath, 'hiddenlayer.status', ARTIFACT_STATUS_PENDING)
                def content = repositories.getContent(responseRepoPath)
                modelScanner.submitHiddenLayerScan(modelInfo, content)
                String modelStatus = modelScanner.getHiddenLayerStatus(modelInfo)
                if (!modelStatus) {
                    log.error "Failed to get model status for file $responseRepoPath"
                    return new Error('Failed to get model status')
                }
                log.debug "file: $responseRepoPath status: $modelStatus"
                repositories.setProperty(responseRepoPath, 'hiddenlayer.status', modelStatus)
                if (modelStatus == ARTIFACT_STATUS_UNSAFE) {
                    log.warn "Attempted to download unsafe file $responseRepoPath"
                    return new Error('Artifact is unsafe')
                }
            }
    } catch (Exception e) {
            log.error "Error handling beforeDownload: $e"

            throw e
        }
    }
}
