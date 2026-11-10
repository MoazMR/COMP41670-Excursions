package ie.ucd.meitheal.shared.view;

public record ReportView(long memberCount, long memberBalanceTotal, long fundBalance) {
    public ReportView {
        if (memberCount < 0 || fundBalance < 0) {
            throw new IllegalArgumentException("member count and fund balance must be non-negative");
        }
    }

    public long actualCirculation() {
        return Math.addExact(memberBalanceTotal, fundBalance);
    }

    public long expectedCirculation() {
        return Math.multiplyExact(120, memberCount);
    }

    public boolean conserved() {
        return actualCirculation() == expectedCirculation();
    }
}
