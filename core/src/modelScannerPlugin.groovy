import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.artifactory.repo.RepoPath
import org.artifactory.request.Request

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

AuthenticationError = new Error("Failed to authenticate with hiddenlayer")

tokenCache = [:]
configCache = [:]
sensorCache = [:]

static JsonBuilder addFileToList(String fileList, String fileName) {
    def json = new JsonBuilder()
    if (!fileList) {
        json {
            fileName "pending"
        }
        return json
    }

    def jsonSlurper = new JsonSlurper()
    def text = jsonSlurper.parseText(fileList)
    if (text[fileName] == "pending") {
        json {
            text
        }
        return json
    }
    text[fileName] = "pending"
    json {
        text
    }
    return json
}

String authenticateWithHiddenLayer() {
    if (tokenCache.containsKey('token') && tokenCache.containsKey('expires') && tokenCache.expires.isAfter(Instant.now())) {
        return tokenCache.token
    }

    loadConfig()
    if (!configCache.containsKey('auth_key')) {
        return null
    }

    def auth_url = configCache.auth_url as String
    def auth_key = configCache.auth_key as String
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(auth_url + "/oauth2/token?grant_type=client_credentials"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + auth_key)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
    HttpClient client = HttpClient.newBuilder().build();
    HttpResponse<String> response;
    try {
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
        log.error "Failed to authenticate with hiddenlayer: $e"
        return null
    }

    def responseCode = response.statusCode();
    if (responseCode != 200) {
        log.error "Failed to authenticate with hiddenlayer: $response"
        return null
    }

    def jsonSlurper = new JsonSlurper()
    def jsonResponse = jsonSlurper.parseText(response.body())
    def accessToken = jsonResponse.access_token as String
    def expiresIn = jsonResponse.expires_in as Number

    tokenCache.put('token', accessToken)
    tokenCache.put('expires', Instant.now().plusSeconds(expiresIn.toLong()))
    return accessToken;
}

Error createScanRequest(modelInfo, String sensorId) {
    def api_url = configCache.api_url as String
    def api_version = configCache.api_version as String
    def token = authenticateWithHiddenLayer()
    def request_body = new JsonBuilder();
    request_body {
        location modelInfo.url
    }
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(api_url + "/api/" + api_version + "/scan/create/" + sensorId))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(request_body.toString()))
            .build();
    HttpClient client = HttpClient.newBuilder().build();
    HttpResponse<String> response;
    try {
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
        log.error "Failed to submit model for scanning: $e"
        return new Error("Failed to submit model for scanning")
    }

    def responseCode = response.statusCode();
    if (responseCode != 201) {
        if (responseCode == 401) {
            tokenCache.remove('token')
            return AuthenticationError
        }
        log.error "Failed to submit model for scanning: $response"
        return new Error("Failed to submit model for scanning")
    }

    log.info "Submitted model for scanning: $modelInfo"
    return null
}

String createSensor(modelInfo) {
    def api_url = configCache.api_url as String
    def api_version = configCache.api_version as String
    def token = authenticateWithHiddenLayer()

    def publisher = modelInfo.model_publisher as String
    def name = modelInfo.model_name as String
    def version = modelInfo.model_version as String
    def file = modelInfo.file_name as String

    def request_body = new JsonBuilder();
    def sensor_name = "jfrog:" + publisher + "/" + name +  ":" + version + ":" + file
    request_body {
        plaintext_name sensor_name
        active true
    }
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(api_url + "/api/" + api_version + "/sensors/create"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(request_body.toString()))
            .build();
    HttpClient client = HttpClient.newBuilder().build();
    HttpResponse<String> response;
    try {
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
        log.error "Failed to create sensor: $e"
        return null
    }

    def responseCode = response.statusCode();
    if (responseCode != 201) {
        if (responseCode == 401) {
            tokenCache.remove('token')
            return AuthenticationError
        }
        log.error "Failed to create sensor: $response"
        return null
    }

    def jsonSlurper = new JsonSlurper()
    def jsonResponse = jsonSlurper.parseText(response.body())

    log.info "Created sensor: $sensor_name, id: $jsonResponse.sensor_id"

    return jsonResponse.sensor_id as String
}

void loadConfig() {
    if (tokenCache.containsKey('api_key')) {
        return
    }

    def props = new Properties()
    props.load(new File("${ctx.artifactoryHome.etcDir}/plugins/hiddenlayer.properties").newDataInputStream())
    def config = new ConfigSlurper().parse(props).get("hiddenlayer")

    def auth = config.get("auth")
    def api = config.get("api")
    def scan = config.get("scan")

    def hlauth = Base64.getEncoder().encodeToString(((auth.get("client_id") as String) + ":" + (auth.get("client_secret") as String)).getBytes())
    configCache.put('auth_key', hlauth)
    configCache.put('auth_url', auth.get("url") as String)
    configCache.put('api_url', api.get("url") as String)
    def version = api.get("version") as String
    configCache.put('api_version', version ? version : "v2")
    def repos = scan.get("repo_ids") as String
    configCache.put('scan_repos', repos.split(','))
    configCache.put('scan_decision_missing', scan.get("decision_missing") as String)
    def retry = scan.get("missing_decision_retry") as String
    configCache.put('scan_missing_retry', retry != "false")
}

static Map<String, String> parseModelInfo(request, RepoPath modelPath) {
    def modelPathParts = modelPath.toPath().split('/')
    def model_publisher = modelPathParts[2]
    def model_name = modelPathParts[2]
    def model_version = modelPathParts[4]
    def file_name = modelPathParts[modelPathParts.length - 1]
    return [
            model_publisher: model_publisher,
            model_name: model_name,
            model_version: model_version,
            file_name: file_name,
            url: request.alternativeRemoteDownloadUrl
    ] as Map<String, String>
}

static String parseModelStatus(modelStatus) {
    if (modelStatus == null) {
        return null
    }

    if (modelStatus.get("status") != "done") {
        return null
    }

    if (modelStatus.get("detections") == "0") {
        return "SAFE"
    }

    return "UNSAFE"
}

boolean shouldScanRepo(String repoKey) {
    def scanRepos = configCache.scan_repos
    if (repoKey.endsWith("-cache")) {
        repoKey = repoKey.substring(0, repoKey.length() - 6)
    }

    return scanRepos && scanRepos.contains(repoKey)
}

Object requestModelStatus(String sensorId) {
    def api_url = configCache.api_url as String
    def api_version = configCache.api_version as String
    def token = authenticateWithHiddenLayer()

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(api_url + "/api/" + api_version + "/scan/status/" + sensorId))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    HttpClient client = HttpClient.newBuilder().build();
    HttpResponse<String> response;
    try {
        response = client.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
        log.error "Failed to get model status: $e"
        return null
    }

    def responseCode = response.statusCode();
    if (responseCode != 200) {
        if (responseCode == 401) {
            tokenCache.remove('token')
            return AuthenticationError
        }
        log.error "Failed to get model status: $response"
        return null
    }

    def jsonSlurper = new JsonSlurper()
    def jsonResponse = jsonSlurper.parseText(response.body())
    return [
        status: jsonResponse.status,
        detections: jsonResponse.detections.size.toString()
    ] as Map<String, String>
}

Object waitForStatus(String sensorId) {
    def retries = 5
    def delay = 5
    def modelStatus
    while (true) {
        if (retries == 0) {
            break
        }
        retries--
        delay *= 2 + (Math.random() * 0.1)
        Thread.sleep(delay.toLong() * 1000)

        log.info "Checking model status for sensor $sensorId"
        modelStatus = requestModelStatus(sensorId)
        if (modelStatus.status == "done" || modelStatus.status == "failed") {
            break
        }
    }
    if (modelStatus == null) {
        return null
    }
    return modelStatus
}

Error submitHiddenLayerScan(modelInfo) {
    // create sensor
    def sensorId = createSensor(modelInfo)
    if (sensorId == AuthenticationError) {
        // retry once if authentication failed
        sensorId = createSensor(modelInfo)
    }
    if (!sensorId) {
        // todo: handle error
        return new Error("Failed to create sensor")
    }
    sensorCache.put(modelInfo.url, sensorId)

    def err = createScanRequest(modelInfo, sensorId)
    if (err == AuthenticationError) {
        // retry once if authentication failed
        err = createScanRequest(modelInfo, sensorId)
    }
    if (err) {
        // todo: handle error
        return new Error("Failed to submit model for scanning")
    }

    return null
}

String getHiddenLayerStatus(modelInfo) {
    def sensorId = sensorCache.get(modelInfo.url)
    if (!sensorId) {
        return null
    }

    def modelStatus = waitForStatus(sensorId)
    return parseModelStatus(modelStatus)
}

loadConfig()
download {
    altResponse { Request request, RepoPath responseRepoPath ->
        if (!shouldScanRepo(responseRepoPath.repoKey)) {
            return
        }

        def artifactStatus = repositories.getProperties(responseRepoPath).getFirst('hiddenlayer.status')
        if (artifactStatus == 'UNSAFE') {
            status = 403
            message = "Artifact is unsafe"
            return new Error("Artifact is unsafe")
        }
        if (artifactStatus == 'PENDING') {
            status = 403
            message = "Artifact is being scanned by hiddenlayer"
            return new Error("Artifact is being scanned by hiddenlayer")
        }

        if (artifactStatus != 'SAFE') {
            // we should not be here, but here we are!
            // this means the model was allowed to download without a decision from hiddenlayer,
            // so we will let the user decide what to do from here.
            if (configCache.scan_missing_retry == true) {
                Thread.start {
                    def modelInfo = parseModelInfo(request, responseRepoPath)
                    def sensorId = sensorCache.get(modelInfo.url)
                    if (!sensorId) {
                        sensorId = createSensor(modelInfo)
                        if (sensorId == AuthenticationError) {
                            sensorId = createSensor(modelInfo)
                        }
                        if (!sensorId) {
                            log.error "Failed to create sensor for file $responseRepoPath"
                            return
                        }
                        sensorCache.put(modelInfo.url, sensorId)
                    }
                    submitHiddenLayerScan(modelInfo)
                    repositories.setProperty(responseRepoPath, 'hiddenlayer.status', 'PENDING')
                    def modelStatus = getHiddenLayerStatus(modelInfo)
                    if (!modelStatus) {
                        log.error "Failed to get model status for file $responseRepoPath"
                        return
                    }
                    log.debug "file: $responseRepoPath status: $modelStatus"
                    repositories.setProperty(responseRepoPath, 'hiddenlayer.status', modelStatus)
                    sensorCache.remove(modelInfo.url)
                }
            }

            if (configCache.scan_decision_missing == 'deny') {
                status = 403
                message = "Artifact has not been scanned by hiddenlayer"
                return new Error("Artifact has not been scanned by hiddenlayer")
            }
        }
    }
    beforeDownload { Request request, RepoPath responseRepoPath ->
        if (!shouldScanRepo(responseRepoPath.repoKey)) {
            return
        }

        def modelInfo = parseModelInfo(request, responseRepoPath)
        def artifactStatus = repositories.getProperties(responseRepoPath).getFirst('hiddenlayer.status')
        def sensorId = sensorCache.get(modelInfo.url)

        if (artifactStatus == 'UNSAFE') {
            log.warn "Attempted to download unsafe file $responseRepoPath"
            return new Error("Artifact is unsafe")
        }
        if (artifactStatus == 'PENDING' && sensorId) {
            return new Error("Artifact is being scanned by hiddenlayer")
        }
        if (artifactStatus != 'SAFE' || (artifactStatus == 'PENDING' && !sensorId)) {
            def token = authenticateWithHiddenLayer()
            if (!token) {
                log.error "Failed to authenticate with hiddenlayer"
                return new Error("Failed to authenticate with hiddenlayer")
            }
            submitHiddenLayerScan(modelInfo)
            repositories.setProperty(responseRepoPath, 'hiddenlayer.status', 'PENDING')
            def modelStatus = getHiddenLayerStatus(modelInfo)
            if (!modelStatus) {
                log.error "Failed to get model status for file $responseRepoPath"
                return new Error("Failed to get model status")
            }
            log.debug "file: $responseRepoPath status: $modelStatus"
            repositories.setProperty(responseRepoPath, 'hiddenlayer.status', modelStatus)
            sensorCache.remove(modelInfo.url)
            if (modelStatus == 'UNSAFE') {
                log.warn "Attempted to download unsafe file $responseRepoPath"
                return new Error("Artifact is unsafe")
            }
        }
    }
}
