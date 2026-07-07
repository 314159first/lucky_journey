package org.luckyjourney.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "search.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RestHighLevelClient restHighLevelClient(ElasticsearchSearchProperties properties) {
        HttpHost[] hosts = Arrays.stream(properties.getUris().split(","))
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(HttpHost::create)
                .toArray(HttpHost[]::new);

        return new RestHighLevelClient(
                RestClient.builder(hosts)
                        .setRequestConfigCallback(builder -> builder
                                .setConnectTimeout(properties.getConnectTimeout())
                                .setSocketTimeout(properties.getSocketTimeout()))
        );
    }
}
