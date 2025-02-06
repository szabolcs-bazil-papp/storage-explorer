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

package com.aestallon.storageexplorer.spring.rest.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * ArcScriptEvalResponse
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public class ArcScriptEvalResponse {

  @Valid
  private List<Object> resultSet = new ArrayList<>();

  private ArcScriptEvalError err;

  public ArcScriptEvalResponse() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ArcScriptEvalResponse(List<Object> resultSet) {
    this.resultSet = resultSet;
  }

  public ArcScriptEvalResponse resultSet(List<Object> resultSet) {
    this.resultSet = resultSet;
    return this;
  }

  public ArcScriptEvalResponse addResultSetItem(Object resultSetItem) {
    if (this.resultSet == null) {
      this.resultSet = new ArrayList<>();
    }
    this.resultSet.add(resultSetItem);
    return this;
  }

  /**
   * Get resultSet
   * @return resultSet
   */
  @NotNull 
  @Schema(name = "resultSet", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("resultSet")
  public List<Object> getResultSet() {
    return resultSet;
  }

  public void setResultSet(List<Object> resultSet) {
    this.resultSet = resultSet;
  }

  public ArcScriptEvalResponse err(ArcScriptEvalError err) {
    this.err = err;
    return this;
  }

  /**
   * Get err
   * @return err
   */
  @Valid 
  @Schema(name = "err", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("err")
  public ArcScriptEvalError getErr() {
    return err;
  }

  public void setErr(ArcScriptEvalError err) {
    this.err = err;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArcScriptEvalResponse arcScriptEvalResponse = (ArcScriptEvalResponse) o;
    return Objects.equals(this.resultSet, arcScriptEvalResponse.resultSet) &&
        Objects.equals(this.err, arcScriptEvalResponse.err);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resultSet, err);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ArcScriptEvalResponse {\n");
    sb.append("    resultSet: ").append(toIndentedString(resultSet)).append("\n");
    sb.append("    err: ").append(toIndentedString(err)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

