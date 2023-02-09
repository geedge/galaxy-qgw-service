package com.mesalab.tsg.entity.arango;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * @description: for path
 * @author: zhq
 * @create: 2020-07-17
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriberIdPath {

    private List<SubscriberIdVertices> vertices;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubscriberIdVertices extends BaseDocument {

        @JsonProperty(value = "SUBSCRIBER_ID")
        private String fqdnName;

        @JsonProperty(value = "FIRST_FOUND_TIME")
        private String firstFoundTime;

        @JsonProperty(value = "LAST_FOUND_TIME")
        private String lastFoundTime;

        @JsonProperty(value = "IP")
        private String ip;

        @JsonProperty(value = "VSYS_ID")
        private List<Long> vsysId;

    }
}
