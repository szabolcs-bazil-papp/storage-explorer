package hu.aestallon.storageexplorer.core.model.instance.dto;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.*;

@JsonPropertyOrder({
  PortMapping.FROM,
  PortMapping.TO
})
@JsonTypeName("PortMapping")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen")
public class PortMapping {
  public static final String FROM = "from";
  private Integer from = -1;

  public static final String TO = "to";
  private Integer to = -1;

  public PortMapping() { 
  }

  public PortMapping from(Integer from) {
    
    this.from = from;
    return this;
  }

  @jakarta.annotation.Nonnull
  @NotNull
  @JsonProperty(FROM)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public Integer getFrom() {
    return from;
  }


  @JsonProperty(FROM)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setFrom(Integer from) {
    this.from = from;
  }

  public PortMapping to(Integer to) {
    
    this.to = to;
    return this;
  }

  @jakarta.annotation.Nonnull
  @NotNull
  @JsonProperty(TO)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getTo() {
    return to;
  }

  @JsonProperty(TO)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)
  public void setTo(Integer to) {
    this.to = to;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PortMapping portMapping = (PortMapping) o;
    return Objects.equals(this.from, portMapping.from) &&
        Objects.equals(this.to, portMapping.to);
  }

  @Override
  public int hashCode() {
    return Objects.hash(from, to);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PortMapping {\n");
    sb.append("    from: ").append(toIndentedString(from)).append("\n");
    sb.append("    to: ").append(toIndentedString(to)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

