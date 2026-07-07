package org.luckyjourney.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "search.elasticsearch")
public class ElasticsearchSearchProperties {

    private Boolean enabled = true;

    private String uris = "http://127.0.0.1:9200";

    private String videoIndex = "lucky_video";

    private String userIndex = "lucky_user";

    private Integer connectTimeout = 1000;

    private Integer socketTimeout = 3000;

    private Boolean rebuildOnStartup = true;
}
