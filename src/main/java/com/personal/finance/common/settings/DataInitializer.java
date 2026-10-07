package com.personal.finance.common.settings;

import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryRepository;
import com.personal.finance.category.CategoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a sensible set of default income and expense categories on first start
 * so the category dropdowns are never empty. Users can add, edit and delete
 * categories afterwards. Only runs when no category exists yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String[] COLORS = {
            "#2563eb", "#16a34a", "#dc2626", "#f59e0b", "#8b5cf6",
            "#06b6d4", "#ec4899", "#84cc16", "#f97316", "#14b8a6",
            "#6366f1", "#eab308", "#0ea5e9"
    };

    private final CategoryRepository categoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            return;
        }
        seed(CategoryType.INCOME, List.of(
                "Salary", "Freelance", "Investments", "Gifts", "Refunds", "Other Income"));
        seed(CategoryType.EXPENSE, List.of(
                "Housing", "Groceries", "Utilities", "Transport", "Dining", "Health",
                "Entertainment", "Shopping", "Education", "Insurance", "Subscriptions",
                "Travel", "Other"));
        log.info("Seeded default categories");
    }

    private void seed(CategoryType type, List<String> names) {
        for (int i = 0; i < names.size(); i++) {
            Category category = new Category();
            category.setName(names.get(i));
            category.setType(type);
            category.setColor(COLORS[i % COLORS.length]);
            categoryRepository.save(category);
        }
    }
}
