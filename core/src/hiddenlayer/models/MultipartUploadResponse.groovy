package hiddenlayer.models

import groovy.transform.CompileDynamic

/**
 * MultipartUploadResponse class to store multipart upload response information
 */
@CompileDynamic
class MultipartUploadResponse {

    String uploadId
    MultipartUploadPart[] parts

}
