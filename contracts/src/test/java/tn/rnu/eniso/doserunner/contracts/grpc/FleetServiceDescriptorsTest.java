package tn.rnu.eniso.doserunner.contracts.grpc;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.rnu.eniso.doserunner.contracts.common.RobotRef;
import tn.rnu.eniso.doserunner.contracts.fixtures.ContractFixtures;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FleetServiceDescriptorsTest {

    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void startInProcessFleetService() throws Exception {
        String name = InProcessServerBuilder.generateName();
        ServerServiceDefinition fleet = ServerServiceDefinition.builder(FleetServiceDescriptors.SERVICE)
                .addMethod(FleetServiceDescriptors.GET_ETA, ServerCalls.asyncUnaryCall((request, observer) -> {
                    observer.onNext(EtaReply.newBuilder().setOrder(request.getOrder()).setAvailable(true)
                            .setEtaSeconds(request.getControlled() ? 120 : 60).setRobot(new RobotRef("R-01")).build());
                    observer.onCompleted();
                }))
                .addMethod(FleetServiceDescriptors.RESERVE_ROBOT, ServerCalls.asyncUnaryCall((request, observer) -> {
                    observer.onNext(ReserveReply.newBuilder().setOrder(request.getOrder()).setReserved(true)
                            .setRobot(new RobotRef("R-01")).setEtaSeconds(request.getHoldSeconds()).build());
                    observer.onCompleted();
                }))
                .build();
        server = InProcessServerBuilder.forName(name).directExecutor().addService(fleet).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void stop() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void getEtaTravelsOverARealGrpcChannelWithAvroBodies() {
        EtaReply reply = ClientCalls.blockingUnaryCall(channel, FleetServiceDescriptors.GET_ETA,
                CallOptions.DEFAULT, ContractFixtures.etaRequest());

        assertThat(reply.getOrder().getOrderId()).isEqualTo("o1");
        assertThat(reply.getEtaSeconds()).isEqualTo(60);
        assertThat(reply.getRobot().getRobotId()).isEqualTo("R-01");
    }

    @Test
    void reserveRobotTravelsOverARealGrpcChannelWithAvroBodies() {
        ReserveReply reply = ClientCalls.blockingUnaryCall(channel, FleetServiceDescriptors.RESERVE_ROBOT,
                CallOptions.DEFAULT, ContractFixtures.reserveRequest());

        assertThat(reply.getReserved()).isTrue();
        assertThat(reply.getEtaSeconds()).isEqualTo(120);
    }

    @Test
    void descriptorsAreUnaryAndNamedUnderTheFleetService() {
        assertThat(FleetServiceDescriptors.GET_ETA.getFullMethodName()).isEqualTo("doserunner.FleetService/GetEta");
        assertThat(FleetServiceDescriptors.RESERVE_ROBOT.getFullMethodName())
                .isEqualTo("doserunner.FleetService/ReserveRobot");
        assertThat(FleetServiceDescriptors.GET_ETA.getType()).isEqualTo(MethodDescriptor.MethodType.UNARY);
        assertThat(FleetServiceDescriptors.GET_ETA.isSafe()).isTrue();
        assertThat(FleetServiceDescriptors.RESERVE_ROBOT.isIdempotent()).isTrue();
        assertThat(FleetServiceDescriptors.RESERVE_ROBOT.isSafe()).isFalse();
        assertThat(FleetServiceDescriptors.SERVICE.getMethods()).hasSize(2);
    }
}
