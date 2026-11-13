package ie.ucd.meitheal.membermanager;

import ie.ucd.meitheal.shared.id.CategoryId;
import ie.ucd.meitheal.shared.result.FailureCode;
import ie.ucd.meitheal.shared.view.CategoryView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedCategoryCatalogTest {
    @Test
    void findsAndListsCanonicalCategoriesInConfigurationOrder() {
        var gardening = new CategoryView(new CategoryId(" gardening "), " Gardening ");
        var cooking = new CategoryView(new CategoryId("cooking"), "Cooking");
        var catalog = new FixedCategoryCatalog(List.of(gardening, cooking));

        assertEquals(List.of(
                new CategoryView(new CategoryId("gardening"), "Gardening"), cooking), catalog.list());
        var found = catalog.find(new CategoryId("gardening"));
        assertTrue(found.isSuccess());
        assertEquals(new CategoryId("gardening"), found.value().orElseThrow().id());
        assertEquals("Gardening", found.value().orElseThrow().name());
    }

    @Test
    void rejectsMalformedConfigurationAndDuplicateNormalizedIds() {
        assertThrows(NullPointerException.class, () -> new FixedCategoryCatalog(null));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedCategoryCatalog(java.util.Arrays.asList((CategoryView) null)));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedCategoryCatalog(List.of(
                        new CategoryView(new CategoryId("valid"), " \t "))));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedCategoryCatalog(List.of(
                        new CategoryView(new CategoryId("same"), "One"),
                        new CategoryView(new CategoryId(" same "), "Two"))));
        assertThrows(IllegalArgumentException.class,
                () -> new FixedCategoryCatalog(List.of(
                        new CategoryView(new CategoryId(Character.toString(0)), "Empty ID"))));
    }

    @Test
    void distinguishesMalformedAndMissingLookups() {
        var catalog = new FixedCategoryCatalog(List.of(
                new CategoryView(new CategoryId("gardening"), "Gardening")));

        assertFailure(FailureCode.MALFORMED_INPUT, catalog.find(null));
        assertFailure(FailureCode.MALFORMED_INPUT,
                catalog.find(new CategoryId(Character.toString(0))));
        assertFailure(FailureCode.MALFORMED_INPUT,
                catalog.find(new CategoryId(Character.toString(0) + Character.toString(0x2003)
                        + Character.toString(0))));
        assertFailure(FailureCode.CATEGORY_NOT_FOUND, catalog.find(new CategoryId("cooking")));
        assertFailure(FailureCode.CATEGORY_NOT_FOUND, catalog.find(new CategoryId("Gardening")));
        assertFailure(FailureCode.CATEGORY_NOT_FOUND, catalog.find(new CategoryId(" gardening ")));
    }

    @Test
    void copiesInputAndExposesAnImmutableList() {
        var configured = new ArrayList<CategoryView>();
        configured.add(new CategoryView(new CategoryId("gardening"), "Gardening"));
        var catalog = new FixedCategoryCatalog(configured);
        configured.add(new CategoryView(new CategoryId("cooking"), "Cooking"));

        assertEquals(1, catalog.list().size());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.list().add(new CategoryView(new CategoryId("music"), "Music")));
        assertEquals(1, catalog.list().size());
    }

    private static void assertFailure(FailureCode expected, ie.ucd.meitheal.shared.result.Result<?> result) {
        assertTrue(result.isFailure());
        assertEquals(expected, result.failureCode().orElseThrow());
    }
}
