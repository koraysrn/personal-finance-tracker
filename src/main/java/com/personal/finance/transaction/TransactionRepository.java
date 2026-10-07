package com.personal.finance.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByAccountId(Long accountId);

    boolean existsByCategoryId(Long categoryId);

    @Query("""
            select t.type, coalesce(sum(t.amount), 0)
            from Transaction t
            where t.account.id = :accountId
            group by t.type
            """)
    List<Object[]> sumAmountsGroupedByType(@Param("accountId") Long accountId);

    @Modifying
    @Query("update Transaction t set t.category.id = :targetId where t.category.id = :sourceId")
    int reassignCategory(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);

    @Query("""
            select t from Transaction t
            where (:accountId is null or t.account.id = :accountId)
              and (:categoryId is null or t.category.id = :categoryId)
              and (:type is null or t.type = :type)
              and (:startDate is null or t.date >= :startDate)
              and (:endDate is null or t.date <= :endDate)
              and (:search is null or lower(coalesce(t.note, '')) like lower(concat('%', :search, '%')))
            order by t.date desc, t.id desc
            """)
    Page<Transaction> search(@Param("accountId") Long accountId,
                             @Param("categoryId") Long categoryId,
                             @Param("type") TransactionType type,
                             @Param("startDate") LocalDate startDate,
                             @Param("endDate") LocalDate endDate,
                             @Param("search") String search,
                             Pageable pageable);

    List<Transaction> findByDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from Transaction t
            where t.type = com.personal.finance.transaction.TransactionType.EXPENSE
              and t.category.id = :categoryId
              and t.date >= :start
              and t.date <= :end
            """)
    BigDecimal sumExpenseByCategoryBetween(@Param("categoryId") Long categoryId,
                                           @Param("start") LocalDate start,
                                           @Param("end") LocalDate end);
}
