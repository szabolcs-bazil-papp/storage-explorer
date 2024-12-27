package com.aestallon.storageexplorer.spring.rest.api;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;
import jakarta.annotation.Generated;

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
@Controller
@RequestMapping("${openapi.storageExplorerEmbeddedRESTful.base-path:}")
public class ExplorerApiController implements ExplorerApi {

    private final ExplorerApiDelegate delegate;

    public ExplorerApiController(@Autowired(required = false) ExplorerApiDelegate delegate) {
        this.delegate = Optional.ofNullable(delegate).orElse(new ExplorerApiDelegate() {});
    }

    @Override
    public ExplorerApiDelegate getDelegate() {
        return delegate;
    }

}
