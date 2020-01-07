package com.mesalab.qgw.monitor;
import com.mesalab.qgw.model.api.ClickHouseHttpSource;
import com.mesalab.qgw.model.api.HBaseHttpSource;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class HbaseHealthIndicator extends AbstractHealthIndicator {


    public RestTemplate restTemplate = new RestTemplate();
    @Autowired
    private HBaseHttpSource hBaseHttpSource;

    @Override
    protected void doHealthCheck(Health.Builder builder) {

        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(hBaseHttpSource.getUrl()).append("/");
        String url = urlBuilder.toString();

        try {
            ResponseEntity responseEntity  = restTemplate.getForEntity(url, Map.class);

            if (responseEntity.getStatusCodeValue() == HttpStatus.SC_OK) {
                builder.up()
                        .withDetail("app", "Hbase")
                        .withDetail("url", hBaseHttpSource.getUrl())
                        .withDetail("message", "ok");
            } else {
                builder.down()
                        .withDetail("app", "Hbase")
                        .withDetail("message", "error, status code " + responseEntity.getStatusCodeValue());


            }
        } catch (Exception e) {
            builder.down()
                    .withDetail("app", "Hbase")
                    .withDetail("message", e.getMessage());

        }

    }


}
