package com.mesalab.services.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeBaseRequest implements Serializable {
    @JsonProperty("kb_id")
    private String kbId;
    @JsonProperty("name")
    private String name;
    @JsonProperty("format")
    private String format;
    @JsonProperty("category")
    private String category;
    @JsonProperty("is_valid")
    private Integer isValid;
    @JsonProperty("is_encryption")
    private Integer isEncryption;
    @JsonProperty("version")
    private String version;
    @JsonProperty("sha256")
    private String sha256;
    @JsonProperty("origin_url")
    private String originUrl;
    @JsonProperty("generated_time")
    private String generatedTime;
    @JsonProperty("last_update_time")
    private String lastUpdateTime;

}
