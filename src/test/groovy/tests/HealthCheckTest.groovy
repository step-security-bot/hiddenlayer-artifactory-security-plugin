package tests

import io.restassured.path.json.JsonPath
import io.restassured.response.Response
import org.hamcrest.Matchers
import org.junit.Assert
import org.testng.Reporter
import org.testng.annotations.Ignore
import org.testng.annotations.Test
import steps.RepositorySteps
import steps.SecuritySteps

class HealthCheckTest extends RepositorySteps {
    def artifactoryURL = "${artifactoryBaseURL}/artifactory"
    def securitySteps = new SecuritySteps()

    @Test(priority=0, groups="common", testName = "Health check for all 4 services")
    void healthCheckTest(){
        Response response = getHealthCheckResponse(artifactoryBaseURL)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body("router.state", Matchers.equalTo("HEALTHY"))

        int bodySize = response.body().jsonPath().getList("services").size()
        for (int i = 0; i < bodySize; i++) {
            JsonPath jsonPathEvaluator = response.jsonPath()
            String serviceID = jsonPathEvaluator.getString("services[" + i + "].service_id")
            String nodeID = jsonPathEvaluator.getString("services[" + i + "].node_id")
            response.then().
                    body("services[" + i + "].state", Matchers.equalTo("HEALTHY"))

            Reporter.log("- Health check. Service \"" + serviceID + "\" on node \"" + nodeID + "\" is healthy", true)
        }
    }

    @Test(priority=1, groups=["common"], testName = "Ping (In HA 200 only when licences were added)")
    void pingTest() {
        Response response = ping(artifactoryURL)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body(Matchers.hasToString("OK"))
        Reporter.log("- Ping test. Service is OK", true)
    }

    @Test(priority=2, groups=["common"], testName = "Set base URL")
    void setBaseURLTest() {
        Response response = setBaseUrl(artifactoryURL, username, password, artifactoryBaseURL)
        response.then().assertThat().log().ifValidationFails().statusCode(200).
                body(Matchers.startsWith("URL base has been successfully updated to"))
        Reporter.log("- Update Custom URL Base. Updated with ${artifactoryBaseURL}", true)
    }

    @Test(priority=3, groups=["common"], testName = "Install license")
    void installLicenseTest() throws AssertionError {
        if (System.env.RT_LICENSE_KEY != null) {
            Response response = securitySteps.installLicense(artifactoryURL, username, password, System.env.RT_LICENSE_KEY)
            if(response.getStatusCode()==200){
                Reporter.log("- License installed successfully", true)
            } else if (response.getStatusCode() == 400 && response.body().asString().contains("License already exists.")) {
                Reporter.log("- License has already been installed.", true)
            } else {
                Reporter.log("- License installation failed. Please check the license key", true)
                Assert.fail("License installation failed. Please check the license key")
            }
        } else {
            Reporter.log("- RT_LICENSE_KEY env var was not set. License has not been installed!", true)
        }
    }

    @Test(priority=4, groups=["common"], testName = "Check number of licenses/nodes")
    void checkLicensesTest() throws AssertionError {
        Response licenses = securitySteps.getLicenseInformation(artifactoryURL, username, password)
        licenses.then().log().ifValidationFails().statusCode(200)
        def body = licenses.then().extract().path("licenses")
        if (body == null){
            def licensedTo = licenses.then().extract().path("licensedTo")
            Reporter.log("- Get license information. Non-HA installation, licensed to ${licensedTo}", true)
        } else {
            def totalNumber = licenses.then().extract().path("licenses.size()")
            List nodeIDs = licenses.jsonPath().getList("licenses.nodeId")
            def numberNotInUse = nodeIDs.findAll {it == "Not in use"}.size()
            def numberInUse = nodeIDs.findAll {it != "Not in use"}.size()
            if (numberInUse > 1){
                Reporter.log("- Get license information. Installation is HA, more than one license installed and used." +
                        " Number of licenses installed: ${totalNumber}, not used: ${numberNotInUse}", true)
            } else if (numberInUse == 1){
                Reporter.log("- Get license information. Number of licenses installed: ${totalNumber}}. " +
                        "Installation in HA, but only one node is up and has a license installed", true)
            }
        }
    }
}
