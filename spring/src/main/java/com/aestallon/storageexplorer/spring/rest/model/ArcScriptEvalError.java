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
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import jakarta.annotation.Generated;

/**
 * ArcScriptEvalError
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", comments = "Generator version: 7.8.0")
public class ArcScriptEvalError {

  private String msg;

  private Integer line;

  private Integer col;

  public ArcScriptEvalError() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ArcScriptEvalError(String msg, Integer line, Integer col) {
    this.msg = msg;
    this.line = line;
    this.col = col;
  }

  public ArcScriptEvalError msg(String msg) {
    this.msg = msg;
    return this;
  }

  /**
   * Get msg
   * @return msg
   */
  @NotNull 
  @Schema(name = "msg", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("msg")
  public String getMsg() {
    return msg;
  }

  public void setMsg(String msg) {
    this.msg = msg;
  }

  public ArcScriptEvalError line(Integer line) {
    this.line = line;
    return this;
  }

  /**
   * Get line
   * @return line
   */
  @NotNull 
  @Schema(name = "line", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("line")
  public Integer getLine() {
    return line;
  }

  public void setLine(Integer line) {
    this.line = line;
  }

  public ArcScriptEvalError col(Integer col) {
    this.col = col;
    return this;
  }

  /**
   * Get col
   * @return col
   */
  @NotNull 
  @Schema(name = "col", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("col")
  public Integer getCol() {
    return col;
  }

  public void setCol(Integer col) {
    this.col = col;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArcScriptEvalError arcScriptEvalError = (ArcScriptEvalError) o;
    return Objects.equals(this.msg, arcScriptEvalError.msg) &&
        Objects.equals(this.line, arcScriptEvalError.line) &&
        Objects.equals(this.col, arcScriptEvalError.col);
  }

  @Override
  public int hashCode() {
    return Objects.hash(msg, line, col);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ArcScriptEvalError {\n");
    sb.append("    msg: ").append(toIndentedString(msg)).append("\n");
    sb.append("    line: ").append(toIndentedString(line)).append("\n");
    sb.append("    col: ").append(toIndentedString(col)).append("\n");
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

