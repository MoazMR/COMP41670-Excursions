package ie.ucd.meitheal.shared.port;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.result.Result;
import ie.ucd.meitheal.shared.view.CategoryView;
import java.util.List;

/** Read-only provider for the configured category catalogue. */
public interface CategoryCatalog {
    Result<CategoryView> find(CategoryId categoryId);

    /** Returns an immutable snapshot of all configured categories. */
    List<CategoryView> list();
}
