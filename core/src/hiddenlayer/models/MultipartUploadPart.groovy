package hiddenlayer.models

class MultipartUploadPart {

    // Property naming does not follow conventions used in other models because this is representing a JSON object
    // TODO: figure out a better approach to getting this object so we can follow the conventions
    Number part_number
    Number start_offset
    Number end_offset
    String upload_url

}