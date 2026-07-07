package org.luckyjourney.service.impl;

import org.luckyjourney.config.ElasticsearchSearchProperties;
import org.luckyjourney.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexStartupRunner implements ApplicationRunner {

    @Autowired
    private ElasticsearchSearchProperties properties;

    @Autowired
    private SearchService searchService;

    @Override
    public void run(ApplicationArguments args) {
        if (!Boolean.TRUE.equals(properties.getRebuildOnStartup())) {
            return;
        }
        new Thread(() -> searchService.rebuildAll(), "search-index-rebuild").start();
    }
}
