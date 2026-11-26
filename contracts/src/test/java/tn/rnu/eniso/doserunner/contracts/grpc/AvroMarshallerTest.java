package tn.rnu.eniso.doserunner.contracts.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.contracts.fixtures.ContractFixtures;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvroMarshallerTest {

    @Test
    void everyGrpcMessageRoundTrips() throws Exception {
        for (SpecificRecord original : ContractFixtures.grpcMessages()) {
            assertThat(roundTrip(original)).as(original.getSchema().getName()).isEqualTo(original);
        }
    }

    @Test
    void optionalFieldsAndDefaultsSurviveTheRoundTrip() throws Exception {
        ReserveRequest request = ContractFixtures.reserveRequest();
        ReserveReply refused = ContractFixtures.reserveReply();

        assertThat(roundTrip(request).getHoldSeconds()).isEqualTo(120);
        ReserveReply decoded = roundTrip(refused);
        assertThat(decoded.getRobot()).isNull();
        assertThat(decoded.getReason()).isEqualTo("no idle robot");
    }

    @Test
    void garbageInputBecomesAnInternalStatus() {
        AvroMarshaller<EtaReply> marshaller = AvroMarshaller.of(EtaReply.class);

        assertThatThrownBy(() -> marshaller.parse(new ByteArrayInputStream(new byte[]{1, 2, 3})))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL));
    }

    @Test
    void bytesOfAnotherMessageTypeAreRejected() throws Exception {
        InputStream request = AvroMarshaller.of(EtaRequest.class).stream(ContractFixtures.etaRequest());

        assertThatThrownBy(() -> AvroMarshaller.of(ReserveReply.class).parse(request))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getDescription()).contains("ReserveReply"));
    }

    @Test
    void marshallerExposesItsMessageType() {
        assertThat(AvroMarshaller.of(EtaRequest.class).messageType()).isEqualTo(EtaRequest.class);
    }

    @SuppressWarnings("unchecked")
    private static <T extends SpecificRecord> T roundTrip(T original) throws Exception {
        AvroMarshaller<T> marshaller = AvroMarshaller.of((Class<T>) original.getClass());
        try (InputStream wire = marshaller.stream(original)) {
            return marshaller.parse(wire);
        }
    }
}
