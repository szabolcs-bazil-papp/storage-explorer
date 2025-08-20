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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Generated;
import jakarta.validation.constraints.NotNull;

/**
 * ArcScriptEvalRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public class ArcScriptEvalRequest {

  private String script;

  public ArcScriptEvalRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ArcScriptEvalRequest(String script) {
    this.script = script;
  }

  public ArcScriptEvalRequest script(String script) {
    this.script = script;
    return this;
  }

  /**
   * Get script
   * @return script
   */
  @NotNull 
  @Schema(name = "script", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("script")
  public String getScript() {
    return script;
  }

  public void setScript(String script) {
    this.script = script;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArcScriptEvalRequest arcScriptEvalRequest = (ArcScriptEvalRequest) o;
    return Objects.equals(this.script, arcScriptEvalRequest.script);
  }

  @Override
  public int hashCode() {
    return Objects.hash(script);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ArcScriptEvalRequest {\n");
    sb.append("    script: ").append(toIndentedString(script)).append("\n");
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

