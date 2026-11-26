package tn.rnu.eniso.doserunner.order.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

public final class TestStock {

    private final JdbcTemplate jdbc;

    public TestStock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String newDrug(boolean controlled, int available) {
        String code = "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("INSERT INTO drug (code, name, form, controlled) VALUES (?, ?, 'TABLET', ?)",
                code, "Test drug " + code, controlled);
        jdbc.update("INSERT INTO stock_item (drug_code, available, reserved) VALUES (?, ?, 0)", code, available);
        return code;
    }

    public int available(String drugCode) {
        return jdbc.queryForObject("SELECT available FROM stock_item WHERE drug_code = ?", Integer.class, drugCode);
    }

    public int reserved(String drugCode) {
        return jdbc.queryForObject("SELECT reserved FROM stock_item WHERE drug_code = ?", Integer.class, drugCode);
    }

    public int orderCount(String drugCode) {
        return jdbc.queryForObject("SELECT count(*) FROM medication_order WHERE drug_code = ?", Integer.class,
                drugCode);
    }
}
