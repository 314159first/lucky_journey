package org.luckyjourney.service.mq;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class BehaviorEventTableInitializer implements CommandLineRunner {

    @Autowired
    private BehaviorEventLogService behaviorEventLogService;

    @Autowired
    private UserDataPersistenceService userDataPersistenceService;

    @Override
    public void run(String... args) {
        behaviorEventLogService.initTable();
        userDataPersistenceService.initTables();
        userDataPersistenceService.backfillUserInterestModelFromRedis();
        userDataPersistenceService.restoreAllUserInterestModelToRedis();
    }
}
