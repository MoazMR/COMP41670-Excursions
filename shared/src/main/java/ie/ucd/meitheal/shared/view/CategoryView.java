package ie.ucd.meitheal.shared.view;

import ie.ucd.meitheal.shared.id.CategoryId;

public record CategoryView(CategoryId id, String name) {
    public CategoryView {
        if (id == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("category fields required");
        }
    }
}
