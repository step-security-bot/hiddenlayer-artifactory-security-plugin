package hiddenlayer.models

class ModelInfo {

    String modelPublisher
    String modelName
    String modelVersion
    String fileName
    String repoPath

    String toSensorName() {
        return 'jfrog:' + modelPublisher + '/' + modelName +  ':' + modelVersion + ':' + fileName
    }

}
