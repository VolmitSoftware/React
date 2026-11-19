package art.arcane.react.api.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;

public class ActionParamDto {
    public String key;
    public String label;
    public String type;
    public boolean required;
    @SerializedName("default")
    @JsonProperty("default")
    public Object defaultValue;
    public String[] options;
}
