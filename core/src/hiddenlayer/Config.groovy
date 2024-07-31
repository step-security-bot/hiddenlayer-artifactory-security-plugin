package hiddenlayer

import groovy.transform.CompileDynamic
import org.artifactory.spring.InternalArtifactoryContext

/**
 * The Config class for HiddenLayer plugin
 */
@CompileDynamic
class Config {

    String authKey
    String authUrl
    String apiUrl
    String apiVersion
    String[] scanRepos = []
    String scanDecisionMissing
    boolean scanMissingRetry

    Config(InternalArtifactoryContext ctx) {
        loadConfig(ctx)
    }

    void loadConfig(InternalArtifactoryContext ctx) {
        Properties props = new Properties()
        props.load(new File("${ctx.artifactoryHome.etcDir}/plugins/hiddenlayer.properties").newDataInputStream())
        def config = new ConfigSlurper().parse(props).get('hiddenlayer')

        def auth = config.get('auth')
        def api = config.get('api')
        def scan = config.get('scan')

        String clientId = System.getenv('HL_CLIENT_ID') != null ? System.getenv('HL_CLIENT_ID') : auth.get('client_id')
        String clientSecret = System.getenv('HL_CLIENT_SECRET') != null
            ? System.getenv('HL_CLIENT_SECRET')
            : auth.get('client_secret')

        String hlauth = Base64.encoder.encodeToString((clientId + ':' + clientSecret).bytes)
        authKey = hlauth
        authUrl = auth.get('url') as String
        apiUrl = api.get('url') as String
        String version = api.get('version') as String
        apiVersion = version ?: 'v2'
        String repos = scan.get('repo_ids') as String
        scanRepos = repos.split(',')
        scanDecisionMissing = scan.get('decision_missing') as String
        String retry = scan.get('missing_decision_retry') as String
        scanMissingRetry = retry != 'false'
    }

}
