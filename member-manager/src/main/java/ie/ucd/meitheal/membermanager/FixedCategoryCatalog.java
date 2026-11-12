package ie.ucd.meitheal.membermanager;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.CategoryView;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable catalogue backed only by the configured category views. */
public final class FixedCategoryCatalog implements CategoryCatalog {
    private final List<CategoryView> categories;

    public FixedCategoryCatalog(List<CategoryView> configuredCategories) {
        Objects.requireNonNull(configuredCategories, "configuredCategories");
        var snapshot = new java.util.ArrayList<CategoryView>(configuredCategories.size());
        Set<String> ids = new HashSet<>();
        for (var category : configuredCategories) {
            if (category == null) {
                throw new IllegalArgumentException("Configured categories must contain valid rows");
            }
            var id = category.id().value().trim();
            var name = category.name().trim();
            if (id.isBlank() || name.isBlank()) {
                throw new IllegalArgumentException("Configured category ID and name must not be blank");
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Configured category IDs must be unique");
            }
            snapshot.add(new CategoryView(new CategoryId(id), name));
        }
        this.categories = List.copyOf(snapshot);
    }

    @Override
    public Result<CategoryView> find(CategoryId categoryId) {
        if (categoryId == null) {
            return Result.failure(FailureCode.MALFORMED_INPUT, "Category ID must not be null");
        }
        if (categoryId.value().trim().isBlank()) {
            return Result.failure(FailureCode.MALFORMED_INPUT, "Category ID must not be blank");
        }
        return categories.stream()
                .filter(category -> category.id().equals(categoryId))
                .findFirst()
                .<Result<CategoryView>>map(Result::success)
                .orElseGet(() -> Result.failure(FailureCode.CATEGORY_NOT_FOUND,
                        "Category is not configured"));
    }

    @Override
    public List<CategoryView> list() {
        return categories;
    }
}
