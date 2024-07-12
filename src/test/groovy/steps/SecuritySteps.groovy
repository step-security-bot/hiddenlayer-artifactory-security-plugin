package steps

import java.util.Map
import org.yaml.snakeyaml.Yaml
import tests.TestSetup

import io.restassured.RestAssured
import io.restassured.config.EncoderConfig
import io.restassured.http.ContentType

import static io.restassured.RestAssured.given

class SecuritySteps extends TestSetup {
    def getLicenseInformation(artifactoryURL, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .when()
                .get("${artifactoryURL}/api/system/licenses")
                .then()
                .extract().response()
    }

    def installLicense(artifactoryURL, username, password, licenseKey) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"licenseKey\": \"${licenseKey}\"\n" +
                        "}")
                .when()
                .post("${artifactoryURL}/api/system/licenses")
                .then()
                .extract().response()
    }

    def createToken(artifactoryURL, username, password) {
        return given()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"username\": \"${username}\",\n" +
                        "  \"scope\": \"applied-permissions/user\",\n" +
                        "  \"expires_in\": 3600,\n" +
                        "  \"include_reference_token\": true\n" +
                        "}")
                .when()
                .post("${artifactoryURL}/access/api/v1/tokens")
                .then()
                .extract().response()
    }
}