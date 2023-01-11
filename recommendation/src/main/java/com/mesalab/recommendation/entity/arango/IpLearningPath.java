package com.mesalab.recommendation.entity.arango;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * @description: for iplearning
 * @author: zhq
 * @create: 2020-07-15
 **/
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IpLearningPath {

//    private List<IpLearningVertices> vertices;

    private List<IplearningEdges> edges;



    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IplearningEdges extends BaseEdgeDocument {

        @JsonProperty(value = "FIRST_FOUND_TIME")
        private Long firstFoundTime;

        @JsonProperty(value = "LAST_FOUND_TIME")
        private Long lastFoundTime;

        @JsonProperty(value = "HTTP_CNT_RECENT")
        private List<Long> httpCntRecent;

        @JsonProperty(value = "HTTP_CNT_TOTAL")
        private Long httpCntTotal;

        @JsonProperty(value = "DNS_CNT_RECENT")
        private List<Long> dnsCntRecent;

        @JsonProperty(value = "DNS_CNT_TOTAL")
        private Long dnsCntTotal;

        @JsonProperty(value = "TLS_CNT_RECENT")
        private List<Long> tlsCntRecent;

        @JsonProperty(value = "TLS_CNT_TOTAL")
        private Long tlsCntTotal;

        @JsonProperty(value = "DIST_CIP_TS")
        private List<Long> distCipTs;

        @JsonProperty(value = "DIST_CIP")
        private List<String> distCip;

        @JsonProperty(value = "VSYS_ID")
        private Long vsysId;

        @JsonProperty(value = "IP")
        private String ip;

        @JsonProperty(value = "FQDN")
        private String fqdnName;

    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IpLearningVertices {

        @JsonProperty(value = "FQDN_NAME")
        private String fqdnName;

        @JsonProperty(value = "FIRST_FOUND_TIME")
        private String firstFoundTime;

        @JsonProperty(value = "LAST_FOUND_TIME")
        private String lastFoundTime;

        @JsonProperty(value = "IP")
        private String ip;

        @JsonProperty(value = "COMMON_LINK_INFO")
        private String commonLinkInfo;

        @JsonProperty(value = "VSYS_ID")
        private Long vsysId;
    }
}
