package tn.rnu.eniso.doserunner.contracts.grpc;

import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;

public final class FleetServiceDescriptors {

    public static final String SERVICE_NAME = "doserunner.FleetService";

    public static final MethodDescriptor<EtaRequest, EtaReply> GET_ETA =
            MethodDescriptor.<EtaRequest, EtaReply>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "GetEta"))
                    .setRequestMarshaller(AvroMarshaller.of(EtaRequest.class))
                    .setResponseMarshaller(AvroMarshaller.of(EtaReply.class))
                    .setSafe(true)
                    .setIdempotent(true)
                    .build();

    public static final MethodDescriptor<ReserveRequest, ReserveReply> RESERVE_ROBOT =
            MethodDescriptor.<ReserveRequest, ReserveReply>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, "ReserveRobot"))
                    .setRequestMarshaller(AvroMarshaller.of(ReserveRequest.class))
                    .setResponseMarshaller(AvroMarshaller.of(ReserveReply.class))
                    .setIdempotent(true)
                    .build();

    public static final ServiceDescriptor SERVICE = ServiceDescriptor.newBuilder(SERVICE_NAME)
            .addMethod(GET_ETA)
            .addMethod(RESERVE_ROBOT)
            .build();

    private FleetServiceDescriptors() {
    }
}
