package com.bank.avpiteam.settings;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class NlpSettingsRepository {
    private final JdbcTemplate jdbc;

    private static List<String> list(Array a) throws SQLException {
        return List.of((String[]) a.getArray());
    }

    private static final RowMapper<NlpSettings> MAPPER = (rs, i) -> new NlpSettings(
            rs.getInt("version"), rs.getBigDecimal("threshold").doubleValue(),
            list(rs.getArray("trigger_words")), list(rs.getArray("negation_words")),
            rs.getObject("at", OffsetDateTime.class), rs.getString("by_user"), rs.getString("comment"));

    NlpSettings current() {
        return jdbc.queryForObject("""
                SELECT version, threshold, trigger_words, negation_words,
                       updated_at AS at, updated_by AS by_user, NULL::text AS comment
                FROM nlp_settings WHERE id = 1
                """, MAPPER);
    }

    int lockCurrentVersion() {
        return jdbc.queryForObject("SELECT version FROM nlp_settings WHERE id = 1 FOR UPDATE", Integer.class);
    }

    List<NlpSettings> history() {
        return jdbc.query("""
                SELECT version, threshold, trigger_words, negation_words,
                       changed_at AS at, changed_by AS by_user, comment
                FROM nlp_settings_history ORDER BY version DESC
                """, MAPPER);
    }

    Optional<NlpSettings> historyVersion(int version) {
        return jdbc.query("""
                SELECT version, threshold, trigger_words, negation_words,
                       changed_at AS at, changed_by AS by_user, comment
                FROM nlp_settings_history WHERE version = ?
                """, MAPPER, version).stream().findFirst();
    }

    /** Обновляет текущую строку, только если версия совпала. Возвращает число изменённых строк. */
    int updateCurrent(int expectedVersion, double threshold, List<String> trigger, List<String> negation, String by) {
        return jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    UPDATE nlp_settings
                    SET version = version + 1, threshold = ?, trigger_words = ?, negation_words = ?,
                        updated_at = now(), updated_by = ?
                    WHERE id = 1 AND version = ?
                    """);
            ps.setBigDecimal(1, BigDecimal.valueOf(threshold));
            ps.setArray(2, con.createArrayOf("text", trigger.toArray()));
            ps.setArray(3, con.createArrayOf("text", negation.toArray()));
            ps.setString(4, by);
            ps.setInt(5, expectedVersion);
            return ps;
        });
    }

    void insertHistory(int version, double threshold, List<String> trigger, List<String> negation,
                       String by, String comment) {
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO nlp_settings_history
                        (version, threshold, trigger_words, negation_words, changed_by, comment)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """);
            ps.setInt(1, version);
            ps.setBigDecimal(2, BigDecimal.valueOf(threshold));
            ps.setArray(3, con.createArrayOf("text", trigger.toArray()));
            ps.setArray(4, con.createArrayOf("text", negation.toArray()));
            ps.setString(5, by);
            ps.setString(6, comment);
            return ps;
        });
    }
}