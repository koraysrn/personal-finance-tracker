package com.personal.finance.recurring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily job that turns due recurring rules into real transactions.
 * ShedLock guarantees only one application instance performs the generation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecurringScheduler {

    private final RecurringTransactionService recurringTransactionService;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "recurringTransactionGeneration",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT30M")
    public void generateDueTransactions() {
        int generated = recurringTransactionService.processDue();
        log.info("Recurring transaction generation produced {} transactions", generated);
    }
}
