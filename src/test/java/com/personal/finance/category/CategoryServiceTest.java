package com.personal.finance.category;

import com.personal.finance.budget.BudgetRepository;
import com.personal.finance.category.dto.CategoryRequest;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Category tree integrity and merge rules (CAT-01, CAT-02, CAT-03, CAT-04, CAT-05).
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void parentWithDifferentTypeIsRejected() {
        Category parent = category(1L, "Food", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parent));

        CategoryRequest request = new CategoryRequest("Salary", CategoryType.INCOME, 1L, null, null, null);

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CATEGORY_TYPE_MISMATCH));
    }

    @Test
    void parentCycleIsRejected() {
        Category a = category(1L, "A", CategoryType.EXPENSE);
        Category b = category(2L, "B", CategoryType.EXPENSE);
        b.setParent(a);

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(a));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(b));

        CategoryRequest request = new CategoryRequest("A", CategoryType.EXPENSE, 2L, null, null, null);

        assertThatThrownBy(() -> categoryService.update(1L, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CATEGORY_CYCLE));
    }

    @Test
    void categoryInUseCannotBeDeleted() {
        Category category = category(1L, "Food", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(transactionRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CATEGORY_IN_USE));
    }

    @Test
    void mergeMovesDataAndDeletesSource() {
        Category source = category(1L, "Old", CategoryType.EXPENSE);
        Category target = category(2L, "New", CategoryType.EXPENSE);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(source));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(target));
        when(categoryRepository.findByParentId(1L)).thenReturn(List.of());

        categoryService.mergeAndDelete(1L, 2L);

        verify(transactionRepository).reassignCategory(1L, 2L);
        verify(budgetRepository).reassignCategory(1L, 2L);
        verify(categoryRepository).delete(source);
    }

    private Category category(Long id, String name, CategoryType type) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setType(type);
        return category;
    }
}
