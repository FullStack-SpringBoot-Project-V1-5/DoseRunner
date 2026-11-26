package tn.rnu.eniso.doserunner.dispatch.dao;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DispatchMessageStore {
    private final JdbcTemplate jdbc;
    public DispatchMessageStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // shortcut: one database lock serializes Week 1 processing/relays, revisit before scaling.
    public void lock() {
        jdbc.queryForObject("SELECT id FROM dispatch_message_lock WHERE id = 1 FOR UPDATE", Integer.class);
    }

    public List<String> eventHashes(UUID eventId) {
        return jdbc.queryForList("SELECT payload_hash FROM dispatch_inbox WHERE event_id = ?", String.class, eventId);
    }

    public void recordEvent(UUID eventId, UUID orderId, String hash) {
        jdbc.update("INSERT INTO dispatch_inbox(event_id, order_id, payload_hash) VALUES (?, ?, ?)", eventId, orderId, hash);
    }

    public boolean hasPublication(UUID missionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM dispatch_outbox WHERE mission_id = ?)", Boolean.class, missionId));
    }

    public int nextSequence(String robotId) {
        try {
            return jdbc.queryForObject("INSERT INTO dispatch_command_sequence(robot_id, last_value) VALUES (?, 1) "
                + "ON CONFLICT(robot_id) DO UPDATE SET last_value = dispatch_command_sequence.last_value + 1 "
                + "WHERE dispatch_command_sequence.last_value < 2147483647 RETURNING last_value", Integer.class, robotId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exhausted) {
            throw new IllegalStateException("Command sequence exhausted for " + robotId, exhausted);
        }
    }

    public void stage(UUID missionId, UUID orderId, String robotId, int sequence, String traceId,
                      byte[] assigned, byte[] command) {
        jdbc.update("INSERT INTO dispatch_outbox(mission_id, order_id, robot_id, command_seq, trace_id, assigned, command) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", missionId, orderId, robotId, sequence, traceId, assigned, command);
    }

    public record Publication(long id, UUID missionId, UUID orderId, String robotId, String traceId,
                              byte[] assigned, byte[] command) { }

    public List<Publication> nextPending() {
        return jdbc.query("SELECT * FROM dispatch_outbox WHERE sent_at IS NULL ORDER BY id LIMIT 1", (r, n) ->
                new Publication(r.getLong("id"), r.getObject("mission_id", UUID.class), r.getObject("order_id", UUID.class),
                        r.getString("robot_id"), r.getString("trace_id"), r.getBytes("assigned"), r.getBytes("command")));
    }

    public void markSent(long id) {
        jdbc.update("UPDATE dispatch_outbox SET sent_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }
}
