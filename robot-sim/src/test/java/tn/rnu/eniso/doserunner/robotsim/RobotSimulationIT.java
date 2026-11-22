package tn.rnu.eniso.doserunner.robotsim;

import jakarta.jms.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.MountableFile;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.contracts.robot.*;
import tn.rnu.eniso.doserunner.robotsim.facade.SimulationFleet;
import tn.rnu.eniso.doserunner.robotsim.pres.MissionCommandListener;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class RobotSimulationIT {
    static final int BROKER_PORT=availablePort();
    static int availablePort() {
        try(var socket=new java.net.ServerSocket(0)) { return socket.getLocalPort(); }
        catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    @Container static final GenericContainer<?> BROKER=new GenericContainer<>("apache/activemq-artemis:2.44.0")
            .withEnv("ARTEMIS_USER","c006").withEnv("ARTEMIS_PASSWORD","c006-test")
            .withEnv("ANONYMOUS_LOGIN","false").withExposedPorts(61616)
            .withCopyFileToContainer(MountableFile.forHostPath(System.getProperty("broker.configuration")),
                    "/var/lib/artemis-instance/etc-override/broker.xml")
            .withCopyFileToContainer(MountableFile.forHostPath(System.getProperty("broker.startup")),"/opt/doserunner/start-broker.sh")
            .withCreateContainerCmdModifier(c -> {
                c.withEntrypoint("/bin/bash","/opt/doserunner/start-broker.sh");
                // Keep the endpoint stable when the outage test restarts this same container.
                c.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(
                        com.github.dockerjava.api.model.Ports.Binding.bindPort(BROKER_PORT),
                        new com.github.dockerjava.api.model.ExposedPort(61616)));
            })
            .withCommand("run").waitingFor(Wait.forLogMessage(".*AMQ221007: Server is now active.*",1))
            .withStartupTimeout(Duration.ofSeconds(90));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.artemis.broker-url",() -> "tcp://"+BROKER.getHost()+":"+BROKER.getMappedPort(61616)+"?callTimeout=2000&reconnectAttempts=0");
        r.add("spring.artemis.user",() -> "c006"); r.add("spring.artemis.password",() -> "c006-test");
    }
    @Autowired ConnectionFactory factory;
    @Autowired AvroJmsMessages adapter;
    @Autowired SimulationFleet fleet;
    @Autowired ApplicationContext context;
    @Value("${local.server.port}") int port;
    @MockitoSpyBean MissionCommandListener listener;
    void send(MissionCommand c) throws Exception {
        try(var connection=factory.createConnection(); var s=connection.createSession(); var p=s.createProducer(s.createQueue(Addresses.ROBOT_COMMANDS))) {
            p.send(message(s,c));
        }
    }
    BytesMessage message(Session s,MissionCommand c) throws Exception {
        var m=adapter.toMessage(s,c); m.setStringProperty(MessageHeaders.ROBOT_ID,c.getRobotId());
        m.setStringProperty(MessageHeaders.GROUP_ID,c.getRobotId()); m.setStringProperty(MessageHeaders.ORDER_ID,c.getOrderId());
        m.setStringProperty(MessageHeaders.TRACE_ID,"c006-trace"); m.setJMSCorrelationID("c006-correlation"); return m;
    }
    @Test void brokerBackedMovementTargetingWaitingOrderAndReplay() throws Exception {
        var first=SimulationFleetTest.command("R-01",1,new GridPoint(1,0),new GridPoint(2,0));
        var next=SimulationFleetTest.command("R-01",2,new GridPoint(0,1));
        var other=SimulationFleetTest.command("R-02",1,new GridPoint(0,1));
        try(var connection=factory.createConnection();var s=connection.createSession()) {
            connection.start();
            var telemetry=s.createConsumer(s.createQueue(Addresses.ROBOT_TELEMETRY+"::"+Addresses.QUEUE_DISPATCH_TELEMETRY));
            send(first); send(next); send(other); send(first);
            var r1=new ArrayList<RobotTelemetry>(); var r2=new ArrayList<RobotTelemetry>();
            long deadline=System.nanoTime()+Duration.ofSeconds(12).toNanos();
            while(r1.size()+r2.size()<4 && System.nanoTime()<deadline) {
                var m=telemetry.receive(2000); assertThat(m).isInstanceOf(BytesMessage.class);
                var t=adapter.fromMessage(m,RobotTelemetry.class);
                assertThat(m.getStringProperty(MessageHeaders.EVENT_TYPE)).isEqualTo("RobotTelemetry");
                assertThat(m.getStringProperty(MessageHeaders.ROBOT_ID)).isEqualTo(t.getRobotId());
                assertThat(m.getStringProperty(MessageHeaders.TRACE_ID)).isEqualTo("c006-trace");
                assertThat(m.getJMSCorrelationID()).isEqualTo("c006-correlation");
                assertThat(m.getJMSDeliveryMode()).isEqualTo(DeliveryMode.PERSISTENT);
                assertThat(t.getSchema().getFields()).hasSize(4);
                (t.getRobotId().equals("R-01")?r1:r2).add(t);
            }
            assertThat(r1).containsExactly(new RobotTelemetry("R-01",1,0,99),new RobotTelemetry("R-01",2,0,98),new RobotTelemetry("R-01",0,1,97));
            assertThat(r2).containsExactly(new RobotTelemetry("R-02",0,1,99));
            send(first); assertThat(telemetry.receive(1500)).isNull();
            assertThat(fleet.snapshot("R-03").lastAcceptedSeq()).isZero();
            assertThat(fleet.snapshot("R-01").activeMission()).isNull();
            verify(listener,timeout(3000).times(5)).onMessage(any());
        }
    }
    @ParameterizedTest @ValueSource(strings={"text","avro","unknown-body","conflict"})
    void rejectedCommandsRollBackThreeTimesThenDeadLetter(String kind) throws Exception {
        var c=SimulationFleetTest.command("R-03",1,new GridPoint(1,0));
        var attempts=new CopyOnWriteArrayList<Integer>();
        doAnswer(call -> {
            Message m=call.getArgument(0);
            if(c.getOrderId().equals(m.getStringProperty(MessageHeaders.ORDER_ID))) attempts.add(m.getIntProperty("JMSXDeliveryCount"));
            return call.callRealMethod();
        }).when(listener).onMessage(any());
        try(var connection=factory.createConnection();var s=connection.createSession()) {
            connection.start();
            var dlq=s.createConsumer(s.createQueue(Addresses.DLQ),"orderId='"+c.getOrderId()+"'");
            Message m;
            if(kind.equals("text")) m=s.createTextMessage("invalid");
            else if(kind.equals("avro")) { var bytes=s.createBytesMessage(); bytes.writeBytes(new byte[]{1,2}); m=bytes; }
            else {
                if(kind.equals("unknown-body")) c.setRobotId("R-99");
                m=message(s,c);
            }
            m.setStringProperty(MessageHeaders.ROBOT_ID,"R-03");
            m.setStringProperty(MessageHeaders.ORDER_ID,c.getOrderId());
            m.setStringProperty(MessageHeaders.EVENT_TYPE,kind.equals("conflict")?"Wrong":"MissionCommand");
            try(var p=s.createProducer(s.createQueue(Addresses.ROBOT_COMMANDS))) { p.send(m); }
            assertThat(dlq.receive(12000)).isNotNull();
            assertThat(attempts).containsExactly(1,2,3);
            assertThat(dlq.receive(300)).isNull();
            assertThat(fleet.snapshot("R-03").lastAcceptedSeq()).isZero();
        } finally { reset(listener); }
    }
    @Test void readinessDetectsBrokerOutageAndRecoversWhileLivenessStaysUp() throws Exception {
        try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            assertThat(healthStatus(client,"readiness")).isEqualTo(200);
            try {
                BROKER.getDockerClient().stopContainerCmd(BROKER.getContainerId()).withTimeout(5).exec();
                await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                        assertThat(healthStatus(client,"readiness")).isEqualTo(503));
                assertThat(healthStatus(client,"liveness")).isEqualTo(200);
            } finally {
                BROKER.getDockerClient().startContainerCmd(BROKER.getContainerId()).exec();
                await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                        assertThat(healthStatus(client,"readiness")).isEqualTo(200));
            }
            System.out.println("C006 broker outage: readiness 200 -> 503 -> 200; liveness remained 200");
        }
    }
    int healthStatus(HttpClient client,String probe) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/actuator/health/"+probe))
                .timeout(Duration.ofSeconds(4)).build();
        return client.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    @Test void healthAndReadinessWorkWithoutDatabase() throws Exception {
        assertThat(context.containsBean("dataSource")).isFalse();
        assertThat(context.containsBean("entityManagerFactory")).isFalse();
        assertThat(context.containsBean("flyway")).isFalse();
        try(var client=HttpClient.newHttpClient()) {
            for(String path:List.of("health","health/readiness","health/liveness")) {
                var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/actuator/"+path)).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200); assertThat(response.body()).contains("\"status\":\"UP\"");
            }
        }
    }
}
