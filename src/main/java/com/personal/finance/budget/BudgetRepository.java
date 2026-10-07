package com.personal.finance.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    boolean existsByCategoryIdAndYearAndMonth(Long categoryId, int year, int month);

    boolean existsByCategoryIdAndYearAndMonthAndIdNot(Long categoryId, int year, int month, Long id);

    boolean existsByCategoryId(Long categoryId);

    List<Budget> findByYearAndMonth(int year, int month);

    @Modifying
    @Query("update Budget b set b.category.id = :targetId where b.category.id = :sourceId")
    int reassignCategory(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
}
