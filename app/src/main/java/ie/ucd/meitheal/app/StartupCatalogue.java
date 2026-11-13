package ie.ucd.meitheal.app;

import ie.ucd.meitheal.membermanager.FixedCategoryCatalog;
import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.port.CategoryCatalog;
import ie.ucd.meitheal.shared.view.CategoryView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

final class StartupCatalogue {
    private StartupCatalogue() {}

    static CategoryCatalog load(InputStream resource) {
        if (resource == null) throw new IllegalStateException("Missing categories.csv");
        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            if (!"id,name".equals(reader.readLine())) {
                throw new IllegalStateException("Expected id,name header in categories.csv");
            }
            var rows = new ArrayList<CategoryView>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length != 2) throw new IllegalStateException("Expected two category fields: " + line);
                rows.add(new CategoryView(new CategoryId(fields[0].trim()), fields[1].trim()));
            }
            return new FixedCategoryCatalog(rows);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Invalid categories.csv", failure);
        }
    }
}
