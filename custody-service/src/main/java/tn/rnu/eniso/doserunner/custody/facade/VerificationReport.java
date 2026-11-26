package tn.rnu.eniso.doserunner.custody.facade;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of {@link CustodyFacade#verify(String)}: {@code {valid, links, brokenAtSeq?, reason?}} (README contract).
 *
 * @param brokenAtSeq seq of the first link that fails verification; absent when the chain is valid
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationReport(String orderId, boolean valid, int links, Long brokenAtSeq, String reason) {
}
