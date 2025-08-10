/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.spring.rest.api;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.NativeWebRequest;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptEvalRequest;
import com.aestallon.storageexplorer.spring.rest.model.ArcScriptEvalResponse;
import com.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionRequest;
import com.aestallon.storageexplorer.spring.rest.model.EntryAcquisitionResult;
import com.aestallon.storageexplorer.spring.rest.model.EntryLoadRequest;
import com.aestallon.storageexplorer.spring.rest.model.EntryLoadResult;
import com.aestallon.storageexplorer.spring.rest.model.StorageIndexDto;
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
     * POST /eval : Evaluates ArcScript against the storage overseen by the application
     * ... 
     *
     * @param arcScriptEvalRequest  (required)
     * @return Ok (status code 200)
     * @see ExplorerApi#eval
     */
    default ResponseEntity<ArcScriptEvalResponse> eval(ArcScriptEvalRequest arcScriptEvalRequest) throws Exception {
        getRequest().ifPresent(request -> {
            for (MediaType mediaType: MediaType.parseMediaTypes(request.getHeader("Accept"))) {
                if (mediaType.isCompatibleWith(MediaType.valueOf("application/json"))) {
                    String exampleString = "{ \"err\" : { \"msg\" : \"msg\", \"col\" : 6, \"line\" : 0 }, \"columns\" : [ { \"column\" : \"column\", \"alias\" : \"alias\" }, { \"column\" : \"column\", \"alias\" : \"alias\" } ], \"resultSet\" : [ \"{}\", \"{}\" ] }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

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
                    String exampleString = "{ \"entry\" : { \"schema\" : \"schema\", \"seqVal\" : 0, \"references\" : [ { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" }, { \"pos\" : 6, \"propName\" : \"propName\", \"uri\" : \"https://openapi-generator.tech\" } ], \"name\" : \"name\", \"typeName\" : \"typeName\", \"scopeHost\" : \"https://openapi-generator.tech\", \"type\" : \"LIST\", \"uri\" : \"https://openapi-generator.tech\" }, \"versions\" : [ { \"meta\" : { \"createdAt\" : \"2000-01-23T04:56:07.000+00:00\", \"lastModifiedAt\" : 6, \"qualifiedName\" : \"qualifiedName\", \"storageSchema\" : \"storageSchema\", \"uri\" : \"https://openapi-generator.tech\", \"versionNr\" : 0 }, \"objectAsMap\" : { \"key\" : \"{}\" } }, { \"meta\" : { \"createdAt\" : \"2000-01-23T04:56:07.000+00:00\", \"lastModifiedAt\" : 6, \"qualifiedName\" : \"qualifiedName\", \"storageSchema\" : \"storageSchema\", \"uri\" : \"https://openapi-generator.tech\", \"versionNr\" : 0 }, \"objectAsMap\" : { \"key\" : \"{}\" } } ], \"type\" : \"FAILED\" }";
                    ApiUtil.setExampleResponse(request, "application/json", exampleString);
                    break;
                }
            }
        });
        return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);

    }

}
