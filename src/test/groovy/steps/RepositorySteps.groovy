package steps

import tests.TestSetup

import static io.restassured.RestAssured.given

class RepositorySteps extends TestSetup {
    static def getHealthCheckResponse(artifactoryURL) {
        return given()
                .when()
                .get("${artifactoryURL}/router/api/v1/system/health")
                .then()
                .extract().response()
    }

    static def ping(artifactoryURL) {
        return given()
                .when()
                .get("${artifactoryURL}/api/system/ping")
                .then()
                .extract().response()
    }

    static def setBaseUrl(artifactoryURL, username, password, String baseUrl) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("content-Type", "text/plain")
                .body(baseUrl)
                .when()
                .put("${artifactoryURL}/api/system/configuration/baseUrl")
                .then()
                .extract().response()
    }

    static def createRepository(artifactoryURL, username, password, repoKey, body) {
        return given()
                .auth()
                .preemptive()
                .basic(username, password)
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body(body)
                .when()
                .put("${artifactoryURL}/api/repositories/${repoKey}")
                .then()
                .extract().response()
    }
}