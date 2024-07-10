package steps

import tests.TestSetup

import static io.restassured.RestAssured.given

class SecuritySteps extends TestSetup {
    def changePassword(artifactoryURL, username, password, newPassword) {
        return given().log().uri()
                .auth()
                .preemptive()
                .basic("${username}", "${password}")
                .header("Cache-Control", "no-cache")
                .header("content-Type", "application/json")
                .body("{\n" +
                        "  \"userName\": \"${username}\",\n" +
                        "  \"oldPassword\": \"${password}\",\n" +
                        "  \"newPassword1\": \"${newPassword}\",\n" +
                        "  \"newPassword2\": \"${newPassword}\"\n" +
                        "}")
                .when()
                .post("${artifactoryURL}/api/security/users/authorization/changePassword")
                .then()
                .extract().response()
    }

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
}