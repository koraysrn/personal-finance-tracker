package com.personal.finance.recurring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    @Query("""
            select rt from RecurringTransaction rt
            where rt.active = true
              and rt.nextRun <= :today
              and (rt.endDate is null or rt.nextRun <= rt.endDate)
            order by rt.nextRun asc
            """)
    List<RecurringTransaction> findDue(@Param("today") LocalDate today);
}
