package com.personal.finance.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCaseAndType(String name, CategoryType type);

    boolean existsByNameIgnoreCaseAndTypeAndIdNot(String name, CategoryType type, Long id);

    boolean existsByParentId(Long parentId);

    List<Category> findByParentId(Long parentId);

    List<Category> findAllByOrderByNameAsc();
}
