package tests

import org.testng.annotations.Test

class RemoteRepoModelScannerTest extends ModelScannerTest {

    RemoteRepoModelScannerTest() {
        super('Remote', 'hf', 'https://huggingface.co')
    }

}
