package org.luckyjourney.schedul;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class HotRankRebuildRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HotRankRebuildRunner.class);

    @Autowired
    private HotRank hotRank;

    @Override
    public void run(ApplicationArguments args) {
        new Thread(() -> {
            try {
                hotRank.hotRank();
                hotRank.hotVideo();
            } catch (Exception e) {
                log.warn("Rebuild hot rank cache failed on startup.", e);
            }
        }, "hot-rank-rebuild").start();
    }
}
