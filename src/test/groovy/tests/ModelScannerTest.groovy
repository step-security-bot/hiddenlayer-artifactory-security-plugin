package tests

import groovy.json.JsonOutput
import io.restassured.response.Response
import org.junit.Assert
import org.testng.Reporter
import org.testng.annotations.Test
import steps.RepositorySteps
import steps.SecuritySteps

import static io.restassured.RestAssured.given

class ModelScannerTest extends RepositorySteps {
    def artifactoryURL = "${artifactoryBaseURL}/artifactory"
    def securitySteps = new SecuritySteps()

    @Test(priority=0, groups="model-scanner", testName = "Set up Remote HuggingFace repo")
    void setupRemoteHuggingFaceRepoTest() {
        String body = JsonOutput.toJson([
            key: "hf",
            rclass: "remote",
            packageType: "huggingfaceml",
            url: "https://huggingface.co"
        ])
        Response response = createRepository(artifactoryURL, username, password, "hf", body)
        if (response.getStatusCode() == 200) {
            Reporter.log("- HuggingFace repository created successfully", true)
        } else if (response.getStatusCode() == 400 && response.body().asString().contains("Case insensitive repository key already exists")) {
            Reporter.log("- HuggingFace repository already exists", true)
        } else {
            Reporter.log("- HuggingFace repository creation failed", true)
            Reporter.log("- Response: ${response.body().asString()}", true)
            Assert.fail()
        }
    }

    @Test(priority=1, groups="model-scanner", testName = "Download model from Remote HuggingFace Repo")
    void downloadModelFromRemoteHuggingFaceTest() {
        downloadModelTest("hf", "prajjwal1/bert-tiny", "resolve/4746226cbb28f1e1c99977204b3ac22ecfe3a072/pytorch_model.bin", true)
    }
    @Test(priority=2, groups="model-scanner", testName = "Download unsafe file is prevented from Remote HuggingFace Repo")
    void downloadUnsafeModelFromRemoteHuggingFaceTest() {
        downloadModelTest("hf", "matanby/unsafe-diffusion", "resolve/243eb928376792047369d2ef072d03528c611909/diffusion_pytorch_model.bin", false)
    }

    void downloadModelTest(repo, model, file, isFileSafe) {
        Response response = securitySteps.createToken(artifactoryBaseURL, username, password)
        String token = ""
        if (response.getStatusCode() == 200) {
            Reporter.log("- Token created successfully", true)
            token = response.jsonPath().getString("reference_token")
        } else {
            Reporter.log("- Token creation failed", true)
            Reporter.log("- Response: ${response.body().asString()}", true)
            Assert.fail()
        }

        response = given()
                .header("Authorization", "Bearer ${token}")
                .when()
                .get("${artifactURL}/artifactory/api/huggingfaceml/${repo}/api/models/${model}")
                .then()
                .extract().response()

        if (response.getStatusCode() == 200) {
            Reporter.log("- Model downloaded successfully", true)
            Reporter.log("- Response: ${response.body().asString()}", true)
        } else {
            Reporter.log("- Model download failed", true)
            Reporter.log("- Response: ${response.body().asString()}", true)
            Assert.fail()
        }

        def maxRetries = 5
        def retryCount = 0

        // Retry downloading the file if it is not safe
        // The first download will succeed, but once the scan comes back, the download should be blocked.
        while (retryCount < maxRetries) {
            response = given()
                    .header("Authorization", "Bearer ${token}")
                    .when()
                    .get("${artifactURL}/artifactory/api/huggingfaceml/${repo}/${model}/${file}")
                    .then()
                    .extract().response()

            if (response.getStatusCode() == 200) {
                Reporter.log("- File downloaded successfully", true)
                if (isFileSafe) {
                    // We expect to be able to download the file. That expectation is met and we can early return
                    return
                }
            } else if (response.getStatusCode() == 403 && !isFileSafe) {
                // Unsafe file has been blocked. We can early return
                Reporter.log("- File download prevented as expected", true)
                return
            } else {
                Reporter.log("- File download failed", true)
                Reporter.log("- Response: ${response.body().asString()}", true)
                Assert.fail()
            }
            // Wait for 5 seconds before retrying
            Thread.sleep(5000)
            retryCount++
        }
        // We should not reach here. If we do, we have retried 5 times without hitting our expected condition
        Reporter.log("- Expected condition not met after ${maxRetries} retries", true)
        Assert.fail()
    }
}