package hiddenlayer.models

import groovy.transform.CompileDynamic

/**
 * ModelInfo class to store model information
 */
@CompileDynamic
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
