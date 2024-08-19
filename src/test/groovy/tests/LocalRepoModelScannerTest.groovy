package tests

import static org.hamcrest.Matchers.nullValue

import io.restassured.response.Response
import org.junit.Assert
import org.testng.Reporter

class LocalRepoModelScannerTest extends ModelScannerTest {

    LocalRepoModelScannerTest() {
        super('Local', 'hf-local', null)
    }

    @Override
    void setupRepoForDownloadModelTests() {
        String model = "models/$safeModel"
        Response response = copyArtifact(artifactoryURL, username, password, 'hf-cache', model, 'hf-local')
        if (response.getStatusCode() == 200) {
            Reporter.log('- Safe model copied to Local HuggingFace repository successfully', true)
        } else {
            Reporter.log('- Safe model copy to Local HuggingFace repository failed', true)
            Reporter.log("- Response: ${response.body().asString()}", true)
            Assert.fail()
        }

        model = "models/$unsafeModel"
        response = copyArtifact(artifactoryURL, username, password, 'hf-cache', model, 'hf-local')
        if (response.getStatusCode() == 200) {
            Reporter.log('- Unsafe model copied to Local HuggingFace repository successfully', true)
        } else {
            Reporter.log('- Unsafe model copy to Local HuggingFace repository failed', true)
            Reporter.log("- Response: ${response.body().asString()}", true)
            Assert.fail()
        }

        def safePath = this.getSafePath()
        response = deleteProperties(artifactoryURL, username, password, safePath, 'hiddenlayer.status')
        response = getInfo(artifactoryURL, username, password, safePath)
        response.then().assertThat().log().ifValidationFails().statusCode(200)
                .body("properties['hiddenlayer.status']", nullValue())
        def unsafePath = this.getUnsafePath()
        response = deleteProperties(artifactoryURL, username, password, unsafePath, 'hiddenlayer.status')
        response = getInfo(artifactoryURL, username, password, unsafePath)
        response.then().assertThat().log().ifValidationFails().statusCode(200)
                .body("properties['hiddenlayer.status']", nullValue())
    }
}
