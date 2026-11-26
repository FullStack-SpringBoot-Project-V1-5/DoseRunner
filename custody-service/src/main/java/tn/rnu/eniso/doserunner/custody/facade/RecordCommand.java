package tn.rnu.eniso.doserunner.custody.facade;

import tn.rnu.eniso.doserunner.custody.bo.LinkType;

import java.time.Instant;

/**
 * Input of {@link CustodyFacade#record(RecordCommand)}, built by the Artemis adapter from a contracts event.
 *
 * @param payload    the raw event bytes as received (Avro single-object encoding); only its hash is kept
 * @param occurredAt the event's {@code occurredAt}
 */
public record RecordCommand(String eventId, String orderId, LinkType type, String actorId, String robotId,
                            byte[] payload, Instant occurredAt) {
}
