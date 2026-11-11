package ie.ucd.meitheal.membermanager;

public record CategoryId(String value) {
    public CategoryId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Category ID must not be blank");
        }
        value = value.trim();
    }
}
