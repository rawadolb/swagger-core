package io.swagger.v3.plugin.maven;

import com.fasterxml.jackson.annotation.JsonView;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.links.Link;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Optional;

public class OperationParser {

    public static final String COMPONENTS_REF = "#/components/schemas/";

    public static Optional<RequestBody> getRequestBody(io.swagger.v3.oas.annotations.parameters.RequestBody requestBody, Components components, JsonView jsonViewAnnotation) {
        if (requestBody == null) {
            return Optional.empty();
        }
        RequestBody requestBodyObject = new RequestBody();
        boolean isEmpty = true;

        if (StringUtils.isNotBlank(requestBody.ref())) {
            requestBodyObject.set$ref(requestBody.ref());
            return Optional.of(requestBodyObject);
        }

        if (StringUtils.isNotBlank(requestBody.description())) {
            requestBodyObject.setDescription(requestBody.description());
            isEmpty = false;
        }
        if (requestBody.required()) {
            requestBodyObject.setRequired(requestBody.required());
            isEmpty = false;
        }
        if (requestBody.extensions().length > 0) {
            Map<String, Object> extensions = AnnotationsUtils.getExtensions(requestBody.extensions());
            if (extensions != null) {
                for (String ext : extensions.keySet()) {
                    requestBodyObject.addExtension(ext, extensions.get(ext));
                }
            }
            isEmpty = false;
        }

        if (requestBody.content().length > 0) {
            isEmpty = false;
        }

        if (isEmpty) {
            return Optional.empty();
        }
        AnnotationsUtils.getContent(requestBody.content(), new String[0], new String[0], null, components, jsonViewAnnotation).ifPresent(requestBodyObject::setContent);
        return Optional.of(requestBodyObject);
    }

    public static Optional<ApiResponses> getApiResponses(final io.swagger.v3.oas.annotations.responses.ApiResponse[] responses, Components components, JsonView jsonViewAnnotation) {
        if (responses == null) {
            return Optional.empty();
        }
        ApiResponses apiResponsesObject = new ApiResponses();
        for (io.swagger.v3.oas.annotations.responses.ApiResponse response : responses) {
            ApiResponse apiResponseObject = new ApiResponse();
            if (StringUtils.isNotBlank(response.ref())) {
                apiResponseObject.set$ref(response.ref());
                if (StringUtils.isNotBlank(response.responseCode())) {
                    apiResponsesObject.addApiResponse(response.responseCode(), apiResponseObject);
                } else {
                    apiResponsesObject._default(apiResponseObject);
                }
                continue;
            }
            if (StringUtils.isNotBlank(response.description())) {
                apiResponseObject.setDescription(response.description());
            }
            if (response.extensions().length > 0) {
                Map<String, Object> extensions = AnnotationsUtils.getExtensions(response.extensions());
                if (extensions != null) {
                    for (String ext : extensions.keySet()) {
                        apiResponseObject.addExtension(ext, extensions.get(ext));
                    }
                }
            }

            AnnotationsUtils.getContent(response.content(), new String[0], new String[0], null, components, jsonViewAnnotation).ifPresent(apiResponseObject::content);
            AnnotationsUtils.getHeaders(response.headers(), jsonViewAnnotation).ifPresent(apiResponseObject::headers);
            if (StringUtils.isNotBlank(apiResponseObject.getDescription()) || apiResponseObject.getContent() != null || apiResponseObject.getHeaders() != null) {

                Map<String, Link> links = AnnotationsUtils.getLinks(response.links());
                if (links.size() > 0) {
                    apiResponseObject.setLinks(links);
                }
                if (StringUtils.isNotBlank(response.responseCode())) {
                    apiResponsesObject.addApiResponse(response.responseCode(), apiResponseObject);
                } else {
                    apiResponsesObject._default(apiResponseObject);
                }
            }
        }

        if (apiResponsesObject.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(apiResponsesObject);
    }

}
