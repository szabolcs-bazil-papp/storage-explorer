package hu.aestallon.storageexplorer.spring.rest.api;

import hu.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionRequest;
import hu.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionResult;
import hu.aestallon.storageexplorer.spring.rest.model.EntryLoadRequest;
import hu.aestallon.storageexplorer.spring.rest.model.EntryLoadResult;
import hu.aestallon.storageexplorer.spring.rest.model.StorageIndexDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.Optional;
import jakarta.annotation.Generated;

/**
 * A delegate to be called by the {@link ExplorerApiController}}.
 * Implement this interface with a {@link org.springframework.stereotype.Service} annotated class.
 */
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public interface ExplorerApiDelegate {

    default Optional<NativeWebRequest> getRequest() {
        return Optional.empty();
    }

    /**
     * POST /acquire : Acquires one or more entries from the storage overseen by the application
     * ... 
     *
     * @param entryAcquisitionRequest  (required)
     * @return Ok (status code 200)
     * @see ExplorerApi#getStorageEntry
     */
    default ResponseEntity<EntryAcquisitionResult> getStorageEntry(EntryAcquisitionRequest entryAcquisitionRequest) throws Exception {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"entries\" : [ { \"schema\" : \"schema\", \"seqVal\" : 0, \"references\" : [ { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" }, { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" } ], \"name\" : \"name\", \"typeName\" : \"typeName\", \"scopeHost\" : \"https://openapi-generator.tech\", \"type\" : \"LIST\", \"uri\" : \"https://openapi-generator.tech\" }, { \"schema\" : \"schema\", \"seqVal\" : 0, \"references\" : [ { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" }, { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" } ], \"name\" : \"name\", \"typeName\" : \"typeName\", \"scopeHost\" : \"https://openapi-generator.tech\", \"type\" : \"LIST\", \"uri\" : \"https://openapi-generator.tech\" } ] }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

    /**
     * GET /storageIndex : Retrieves the index for the storage overseen by the application.
     * ... 
     *
     * @param method  (required)
     * @return Ok (status code 200)
     * @see ExplorerApi#getStorageIndex
     */
    default ResponseEntity<StorageIndexDto> getStorageIndex(String method) throws Exception {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"entries\" : [ { \"schema\" : \"schema\", \"seqVal\" : 0, \"references\" : [ { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" }, { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" } ], \"name\" : \"name\", \"typeName\" : \"typeName\", \"scopeHost\" : \"https://openapi-generator.tech\", \"type\" : \"LIST\", \"uri\" : \"https://openapi-generator.tech\" }, { \"schema\" : \"schema\", \"seqVal\" : 0, \"references\" : [ { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" }, { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" } ], \"name\" : \"name\", \"typeName\" : \"typeName\", \"scopeHost\" : \"https://openapi-generator.tech\", \"type\" : \"LIST\", \"uri\" : \"https://openapi-generator.tech\" } ] }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

    /**
     * POST /load : Fully loads an entry for inspection from the storage overseen by the application
     * ... 
     *
     * @param entryLoadRequest  (required)
     * @return Ok (status code 200)
     * @see ExplorerApi#loadStorageEntry
     */
    default ResponseEntity<EntryLoadResult> loadStorageEntry(EntryLoadRequest entryLoadRequest) throws Exception {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"versions\" : [ { \"meta\" : { \"createdAt\" : \"createdAt\", \"lastModifiedAt\" : 6, \"qualifiedName\" : \"qualifiedName\", \"storageSchema\" : \"storageSchema\", \"uri\" : \"https://openapi-generator.tech\", \"versionNr\" : 0 }, \"objectAsMap\" : { \"key\" : \"{}\" } }, { \"meta\" : { \"createdAt\" : \"createdAt\", \"lastModifiedAt\" : 6, \"qualifiedName\" : \"qualifiedName\", \"storageSchema\" : \"storageSchema\", \"uri\" : \"https://openapi-generator.tech\", \"versionNr\" : 0 }, \"objectAsMap\" : { \"key\" : \"{}\" } } ], \"type\" : \"FAILED\" }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

}
