package tests

import org.yaml.snakeyaml.Yaml

class TestSetup {
    protected static final Map config = new Yaml().load(new FileReader("./src/test/resources/testenv.yaml"))
    protected final protocol = System.env.RT_PROTOCOL ?: config.artifactory.protocol
    protected final ip = System.env.RT_URL ?: config.artifactory.external_ip
    protected final username = System.env.RT_USERNAME ?: config.artifactory.rt_username
    protected final default_password = System.env.RT_PASSWORD ?: config.artifactory.rt_password
    protected final password = System.env.NEW_RT_PASSWORD ?: config.artifactory.rt_password
    protected final dockerURL = System.env.RT_DOCKER_URL ?: config.artifactory.url
    protected final artifactoryBaseURL = "${protocol}${ip}"
}