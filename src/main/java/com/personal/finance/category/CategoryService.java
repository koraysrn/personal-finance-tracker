package com.personal.finance.category;

import com.personal.finance.budget.BudgetRepository;
import com.personal.finance.category.dto.CategoryRequest;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Category lifecycle with tree integrity (cycle prevention) and safe deletion
 * through a merge operation that moves transactions, subcategories and budgets
 * to another category.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    @Transactional
    public Category create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCaseAndType(request.name().trim(), request.type())) {
            throw new ApiException(ErrorCode.CONFLICT, "A category with this name and type already exists");
        }

        Category category = new Category();
        category.setName(request.name().trim());
        category.setType(request.type());
        category.setParent(resolveParent(request.parentId(), request.type(), null));
        category.setIcon(request.icon());
        category.setColor(request.color());
        category.setArchived(Boolean.TRUE.equals(request.archived()));
        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found"));
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public Category update(Long id, CategoryRequest request) {
        Category category = get(id);

        if (!category.getName().equalsIgnoreCase(request.name().trim())
                && categoryRepository.existsByNameIgnoreCaseAndType(request.name().trim(), request.type())) {
            throw new ApiException(ErrorCode.CONFLICT, "A category with this name and type already exists");
        }

        category.setName(request.name().trim());
        category.setParent(resolveParent(request.parentId(), request.type(), category));
        category.setIcon(request.icon());
        category.setColor(request.color());
        if (Boolean.TRUE.equals(request.archived())) {
            category.setArchived(true);
        }
        return categoryRepository.save(category);
    }

    /**
     * Deletes a category only when it is unused and has no children.
     * In use categories must be handled through {@link #mergeAndDelete(Long, Long)}.
     */
    @Transactional
    public void delete(Long id) {
        Category category = get(id);
        assertUnused(category);
        categoryRepository.delete(category);
    }

    /**
     * Moves all transactions, subcategories and budgets of {@code sourceId} onto
     * {@code targetId} and then deletes the source category atomically.
     */
    @Transactional
    public Category mergeAndDelete(Long sourceId, Long targetId) {
        Category source = get(sourceId);
        Category target = get(targetId);

        if (source.getType() != target.getType()) {
            throw new ApiException(ErrorCode.CATEGORY_TYPE_MISMATCH,
                    "Source and target categories must have the same type");
        }

        transactionRepository.reassignCategory(source.getId(), target.getId());
        budgetRepository.reassignCategory(source.getId(), target.getId());

        for (Category child : categoryRepository.findByParentId(source.getId())) {
            child.setParent(target);
            categoryRepository.save(child);
        }

        categoryRepository.delete(source);
        return target;
    }

    private void assertUnused(Category category) {
        if (transactionRepository.existsByCategoryId(category.getId())) {
            throw new ApiException(ErrorCode.CATEGORY_IN_USE,
                    "The category has transactions; merge it into another category instead");
        }
        if (budgetRepository.existsByCategoryId(category.getId())) {
            throw new ApiException(ErrorCode.CATEGORY_IN_USE,
                    "The category has budgets; remove or move them first");
        }
        if (categoryRepository.existsByParentId(category.getId())) {
            throw new ApiException(ErrorCode.CATEGORY_IN_USE,
                    "The category has subcategories; move or delete them first");
        }
    }

    private Category resolveParent(Long parentId, CategoryType type, Category self) {
        if (parentId == null) {
            return null;
        }
        Category parent = get(parentId);
        if (parent.getType() != type) {
            throw new ApiException(ErrorCode.CATEGORY_TYPE_MISMATCH,
                    "Parent category must have the same type as the child");
        }
        ensureNoCycle(self, parent);
        return parent;
    }

    private void ensureNoCycle(Category self, Category parent) {
        Category cursor = parent;
        while (cursor != null) {
            if (self != null && cursor.getId().equals(self.getId())) {
                throw new ApiException(ErrorCode.CATEGORY_CYCLE,
                        "A category cannot be its own ancestor");
            }
            cursor = cursor.getParent();
        }
    }
}
