package tn.rnu.eniso.doserunner.robotlink;

import jakarta.jms.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.MountableFile;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.contracts.robot.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RobotLinkIT {
    static final Path ROOT=Path.of(System.getProperty("repository.root")).normalize();
    static final Path TARGET=Path.of(System.getProperty("handoff.directory"));
    static final Network NETWORK=Network.newNetwork();
    @Container static final GenericContainer<?> BROKER=new GenericContainer<>("apache/activemq-artemis:2.44.0")
            .withNetwork(NETWORK).withNetworkAliases("c007-broker")
            .withEnv("ARTEMIS_USER","c007").withEnv("ARTEMIS_PASSWORD","c007-test")
            .withEnv("ANONYMOUS_LOGIN","false").withExposedPorts(61616)
            .withCopyFileToContainer(MountableFile.forHostPath(ROOT.resolve("deploy/artemis/broker.xml")),
                    "/var/lib/artemis-instance/etc-override/broker.xml")
            .withCopyFileToContainer(MountableFile.forHostPath(ROOT.resolve("deploy/artemis/start-broker.sh")),
                    "/opt/doserunner/start-broker.sh")
            .withCreateContainerCmdModifier(c -> c.withEntrypoint("/bin/bash","/opt/doserunner/start-broker.sh"))
            .withCommand("run").waitingFor(Wait.forLogMessage(".*AMQ221007: Server is now active.*",1))
            .withStartupTimeout(Duration.ofSeconds(90));
    ActiveMQConnectionFactory factory;
    @BeforeEach void setup() { factory=new ActiveMQConnectionFactory("tcp://"+BROKER.getHost()+":"+BROKER.getMappedPort(61616)+"?callTimeout=2000&reconnectAttempts=0"); }
    @AfterEach void cleanup() { factory.close(); }
    @AfterAll static void cleanupNetwork() { BROKER.stop(); NETWORK.close(); }
    RobotLink link(String robot) throws JMSException { return new JmsRobotLink(factory,robot,"c007","c007-test"); }
    Connection connection() throws JMSException { return factory.createConnection("c007","c007-test"); }
    MissionCommand command(String robot,int seq) {
        return new MissionCommand(UUID.randomUUID().toString(),robot,UUID.randomUUID().toString(),seq,
                List.of(new GridPoint(1,0)),"C1",Instant.ofEpochMilli(1234));
    }
    BytesMessage message(Session s,MissionCommand c) throws Exception {
        ByteBuffer bytes=c.toByteBuffer(); byte[] body=new byte[bytes.remaining()];bytes.get(body);
        BytesMessage m=s.createBytesMessage();m.writeBytes(body);
        m.setStringProperty(MessageHeaders.EVENT_TYPE,"MissionCommand");m.setStringProperty(MessageHeaders.ROBOT_ID,c.getRobotId());
        m.setStringProperty(MessageHeaders.ORDER_ID,c.getOrderId());m.setStringProperty(MessageHeaders.GROUP_ID,c.getRobotId());
        m.setStringProperty(MessageHeaders.TRACE_ID,"c007-trace");m.setJMSCorrelationID("c007-correlation");return m;
    }
    void send(MissionCommand c) throws Exception {
        try(Connection connection=connection();Session s=connection.createSession();MessageProducer p=s.createProducer(s.createQueue(Addresses.ROBOT_COMMANDS))) { p.send(message(s,c)); }
    }
    @Test void targetedCommandsOrderingAndIndependentTelemetryTransaction() throws Exception {
        try(RobotLink r1=link("R-01");RobotLink r2=link("R-02");Connection conn=connection();Session s=conn.createSession();
            MessageConsumer telemetry=s.createConsumer(s.createQueue(Addresses.fqqn(Addresses.ROBOT_TELEMETRY,Addresses.QUEUE_DISPATCH_TELEMETRY)),"robotId = 'R-01'")) {
            conn.start();MissionCommand first=command("R-01",1),second=command("R-01",2),other=command("R-02",1);
            send(first);send(other);send(second);
            CommandDelivery d1=r1.receive(5000).orElseThrow(),d2=r2.receive(5000).orElseThrow();
            assertEquals(first,d1.command());assertEquals(other,d2.command());
            assertThrows(java.lang.IllegalStateException.class,()->r1.receive(1));
            assertThrows(IllegalArgumentException.class,()->r1.acknowledge(d2));
            r1.publishTelemetry(new RobotTelemetry("R-01",1,0,99),d1);
            Message raw=telemetry.receive(5000);assertInstanceOf(BytesMessage.class,raw);assertEquals(DeliveryMode.PERSISTENT,raw.getJMSDeliveryMode());
            BytesMessage bytes=(BytesMessage)raw;byte[] body=new byte[(int)bytes.getBodyLength()];bytes.readBytes(body);
            assertEquals(new RobotTelemetry("R-01",1,0,99),RobotTelemetry.fromByteBuffer(ByteBuffer.wrap(body)));
            assertEquals("RobotTelemetry",raw.getStringProperty(MessageHeaders.EVENT_TYPE));
            assertEquals("R-01",raw.getStringProperty(MessageHeaders.ROBOT_ID));assertEquals(first.getOrderId(),raw.getStringProperty(MessageHeaders.ORDER_ID));
            assertEquals("c007-trace",raw.getStringProperty(MessageHeaders.TRACE_ID));assertEquals("c007-correlation",raw.getJMSCorrelationID());
            // Telemetry commit must not acknowledge the command.
            r1.rollback(d1);CommandDelivery replay=r1.receive(5000).orElseThrow();assertEquals(first,replay.command());assertEquals(2,replay.deliveryCount());
            r1.acknowledge(replay);r2.acknowledge(d2);
            CommandDelivery next=r1.receive(5000).orElseThrow();assertEquals(second,next.command());r1.acknowledge(next);
            assertTrue(r1.receive(100).isEmpty());assertTrue(r2.receive(100).isEmpty());
            assertThrows(IllegalArgumentException.class,()->r1.acknowledge(d1));
            assertThrows(IllegalArgumentException.class,()->r1.publishTelemetry(new RobotTelemetry("R-01",0,0,100),d2));
        }
    }
    @Test void closeRollsBackUnacknowledgedDelivery() throws Exception {
        MissionCommand command=command("CLOSE",1);send(command);
        try(RobotLink first=link("CLOSE")) { assertEquals(command,first.receive(5000).orElseThrow().command()); }
        try(RobotLink second=link("CLOSE")) { CommandDelivery replay=second.receive(5000).orElseThrow();assertEquals(command,replay.command());assertEquals(2,replay.deliveryCount());second.acknowledge(replay); }
    }
    @ParameterizedTest @ValueSource(strings={"commit","rollback"})
    void failedSettlementRetiresLinkAndAllowsBrokerRedelivery(String operation) throws Exception {
        String robot="SETTLE-"+operation;MissionCommand command=command(robot,1);send(command);
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        ConnectionFactory failingFactory=(ConnectionFactory)Proxy.newProxyInstance(ConnectionFactory.class.getClassLoader(),
                new Class<?>[]{ConnectionFactory.class},(p,m,a)-> {
                    Connection delegate=(Connection)invoke(m,factory,a);
                    java.util.concurrent.atomic.AtomicInteger sessions=new java.util.concurrent.atomic.AtomicInteger();
                    return Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(cp,cm,ca)-> {
                        Object result=invoke(cm,delegate,ca);
                        if(!cm.getName().equals("createSession") || sessions.getAndIncrement()!=0) return result;
                        Session session=(Session)result;
                        return Proxy.newProxyInstance(Session.class.getClassLoader(),new Class<?>[]{Session.class},(sp,sm,sa)-> {
                            if(sm.getName().equals(operation) && calls.incrementAndGet()==1) {
                                if(operation.equals("commit")) { session.rollback();throw new TransactionRolledBackException("injected commit rollback"); }
                                throw new JMSException("injected rollback failure");
                            }
                            return invoke(sm,session,sa);
                        });
                    });
                });
        try(RobotLink link=new JmsRobotLink(failingFactory,robot,"c007","c007-test")) {
            CommandDelivery delivery=link.receive(5000).orElseThrow();
            if(operation.equals("commit")) assertThrows(TransactionRolledBackException.class,()->link.acknowledge(delivery));
            else assertThrows(JMSException.class,()->link.rollback(delivery));
            assertThrows(java.lang.IllegalStateException.class,()->link.acknowledge(delivery));
            assertThrows(java.lang.IllegalStateException.class,()->link.rollback(delivery));
            assertThrows(java.lang.IllegalStateException.class,()->link.receive(1));
            assertEquals(1,calls.get(),"No second settlement may reach an uncertain session");
        }
        try(RobotLink recovered=link(robot)) {
            CommandDelivery replay=recovered.receive(5000).orElseThrow();assertEquals(command,replay.command());
            assertEquals(2,replay.deliveryCount());recovered.acknowledge(replay);
        }
    }
    static Object invoke(Method method,Object target,Object[] args) throws Throwable {
        try { return method.invoke(target,args); }
        catch(InvocationTargetException error) { throw error.getCause(); }
    }
    @ParameterizedTest @ValueSource(strings={"text","avro","target","event","seq","order","group","uuid","point"})
    void poisonRollsBackThreeTimesThenDlq(String kind) throws Exception {
        String robot="BAD-"+kind;
        try(Connection conn=connection();Session s=conn.createSession();RobotLink link=link(robot);
            MessageProducer p=s.createProducer(s.createQueue(Addresses.ROBOT_COMMANDS));
            MessageConsumer dlq=s.createConsumer(s.createQueue(Addresses.DLQ),"testId = '"+robot+"'")) {
            conn.start();MissionCommand c=command(robot,1);
            if(kind.equals("seq")) c.setSeq(0);
            if(kind.equals("uuid")) c.setMissionId("not-a-uuid");
            if(kind.equals("point")) c.setWaypoints(List.of(new GridPoint(-1,0)));
            Message m;
            if(kind.equals("text")) m=s.createTextMessage("bad");
            else if(kind.equals("avro")) { BytesMessage b=s.createBytesMessage();b.writeBytes(new byte[10]);m=b; }
            else { if(kind.equals("target")) c.setRobotId("WRONG");m=message(s,c); }
            m.setStringProperty(MessageHeaders.ROBOT_ID,robot);m.setStringProperty("testId",robot);
            if(kind.equals("event")) m.setStringProperty(MessageHeaders.EVENT_TYPE,"Other");
            if(kind.equals("order")) m.setStringProperty(MessageHeaders.ORDER_ID,"wrong");
            if(kind.equals("group")) m.setStringProperty(MessageHeaders.GROUP_ID,"wrong");
            p.send(m);
            for(int i=0;i<3;i++) assertThrows(IOException.class,()->link.receive(5000));
            Message dead=dlq.receive(5000);assertNotNull(dead);assertEquals(Addresses.ROBOT_COMMANDS,dead.getStringProperty("_AMQ_ORIG_ADDRESS"));
            assertTrue(link.receive(100).isEmpty());assertNull(dlq.receive(100));
        }
    }
    @ParameterizedTest @ValueSource(ints={-1,101})
    void invalidBatteryDoesNotAcknowledgeCommand(int battery) throws Exception {
        String robot="BAT"+Math.abs(battery);send(command(robot,1));
        try(RobotLink link=link(robot)) {
            CommandDelivery delivery=link.receive(5000).orElseThrow();
            assertThrows(IllegalArgumentException.class,()->link.publishTelemetry(new RobotTelemetry(robot,1,0,battery),delivery));
            assertThrows(IllegalArgumentException.class,()->link.publishTelemetry(new RobotTelemetry("WRONG",1,0,50),delivery));
            assertThrows(IllegalArgumentException.class,()->link.publishTelemetry(new RobotTelemetry(robot,-1,0,50),delivery));
            link.rollback(delivery);CommandDelivery replay=link.receive(5000).orElseThrow();assertEquals(2,replay.deliveryCount());link.acknowledge(replay);
        }
    }
    @Test void authenticationAndLifecycle() throws Exception {
        assertThrows(JMSException.class,()->new JmsRobotLink(factory,"AUTH","c007","wrong"));
        RobotLink link=link("LIFECYCLE");
        try {
            assertThrows(IllegalArgumentException.class,()->link.receive(0));assertThrows(IllegalArgumentException.class,()->link.receive(-1));
            ExecutorService executor=Executors.newSingleThreadExecutor();CountDownLatch started=new CountDownLatch(1);
            AtomicReference<Thread> receiver=new AtomicReference<>();
            try {
                Future<?> blocked=executor.submit(()-> { receiver.set(Thread.currentThread()); started.countDown();try { assertTrue(link.receive(30000).isEmpty()); } catch(JMSException | IOException expected) { } });
                assertTrue(started.await(2,TimeUnit.SECONDS));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while(!receiving(receiver.get()) && System.nanoTime()<deadline) Thread.sleep(5);
                assertTrue(receiving(receiver.get()), "Caller must be blocked inside Artemis receive before closing");
                link.close();blocked.get(5,TimeUnit.SECONDS);
            } finally { executor.shutdownNow(); }
            assertThrows(java.lang.IllegalStateException.class,()->link.receive(1));
        } finally { link.close(); }
    }
    static boolean receiving(Thread thread) {
        return Arrays.stream(thread.getStackTrace()).anyMatch(frame -> frame.getMethodName().equals("receive")
                && frame.getClassName().startsWith("org.apache.activemq.artemis"));
    }
    @Test void packagedHandoffRunsOnActualJava11() throws Exception {
        Path probe=TARGET.resolve("java11-probe");Files.createDirectories(probe);
        List<Path> jars;
        try(var paths=Files.list(TARGET.resolve("lib"))) { jars=paths.filter(p->p.toString().endsWith(".jar")).toList(); }
        jars=new ArrayList<>(jars);jars.add(TARGET.resolve("robot-link-0.1.0-SNAPSHOT.jar"));
        for(Path path:jars) {
            assertFalse(path.getFileName().toString().contains("spring"));
            try(JarFile jar=new JarFile(path.toFile())) {
                for(var entry:Collections.list(jar.entries())) {
                    if(!entry.getName().endsWith(".class") || entry.getName().startsWith("META-INF/versions/")) continue;
                    try(var input=jar.getInputStream(entry)) {
                        byte[] header=input.readNBytes(8);int major=((header[6]&255)<<8)|(header[7]&255);
                        assertTrue(major<=55,path+" contains Java "+major+" bytecode: "+entry.getName());
                    }
                }
            }
        }
        String classpath=String.join(java.io.File.pathSeparator,jars.stream().map(Path::toString).toList());
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","11","-cp",classpath,"-d",probe.toString(),"src/test/java11/Java11Probe.java"));
        send(command("JAVA11",1));
        try(Connection conn=connection();Session s=conn.createSession();MessageConsumer telemetry=s.createConsumer(
                s.createQueue(Addresses.fqqn(Addresses.ROBOT_TELEMETRY,Addresses.QUEUE_DISPATCH_TELEMETRY)),"robotId = 'JAVA11'")) {
            conn.start();
            try(GenericContainer<?> runtime=java11("Java11Probe", "tcp://c007-broker:61616?callTimeout=2000&reconnectAttempts=0")
                    .withCopyFileToContainer(MountableFile.forHostPath(probe),"/handoff/probe")) {
                runtime.start();assertTrue(runtime.getLogs().contains("JAVA11_LINK_OK"),runtime.getLogs());
                System.out.println(runtime.getLogs());
            }
            Message raw=telemetry.receive(5000);assertNotNull(raw);assertEquals(DeliveryMode.PERSISTENT,raw.getJMSDeliveryMode());
            BytesMessage bytes=(BytesMessage)raw;byte[] body=new byte[(int)bytes.getBodyLength()];bytes.readBytes(body);
            assertEquals(new RobotTelemetry("JAVA11",1,0,99),RobotTelemetry.fromByteBuffer(ByteBuffer.wrap(body)));
            assertEquals("c007-trace",raw.getStringProperty(MessageHeaders.TRACE_ID));assertEquals("c007-correlation",raw.getJMSCorrelationID());
        }
    }

    GenericContainer<?> java11(String... arguments) {
        List<String> command=new ArrayList<>(List.of("java","-cp","/handoff/robot-link.jar:/handoff/lib/*:/handoff/probe"));
        command.addAll(List.of(arguments));
        return new GenericContainer<>("eclipse-temurin:11-jre").withNetwork(NETWORK)
                .withCopyFileToContainer(MountableFile.forHostPath(TARGET.resolve("lib")),"/handoff/lib")
                .withCopyFileToContainer(MountableFile.forHostPath(TARGET.resolve("robot-link-0.1.0-SNAPSHOT.jar")),"/handoff/robot-link.jar")
                .withCommand(command.toArray(new String[0]))
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofSeconds(45)));
    }
    @Test void packagedExecutableExampleRunsOnJava11() throws Exception {
        MissionCommand command=command("EXAMPLE",1);
        try(Connection conn=connection();Session session=conn.createSession();MessageConsumer telemetry=session.createConsumer(
                session.createQueue(Addresses.fqqn(Addresses.ROBOT_TELEMETRY,Addresses.QUEUE_DISPATCH_TELEMETRY)),"robotId = 'EXAMPLE'")) {
            conn.start();send(command);
            try(GenericContainer<?> runtime=java11("tn.rnu.eniso.doserunner.robotlink.RobotLinkExample",
                    "tcp://c007-broker:61616?callTimeout=2000&reconnectAttempts=0","EXAMPLE")
                    .withEnv("ARTEMIS_USER","c007").withEnv("ARTEMIS_PASSWORD","c007-test")) {
                runtime.start();assertTrue(runtime.getLogs().contains("Received "+command.getMissionId()+" for EXAMPLE"),runtime.getLogs());
                System.out.println("JAVA11_EXAMPLE_OK "+command.getMissionId());
            }
            Message raw=telemetry.receive(5000);assertInstanceOf(BytesMessage.class,raw);assertEquals(DeliveryMode.PERSISTENT,raw.getJMSDeliveryMode());
            BytesMessage bytes=(BytesMessage)raw;byte[] body=new byte[(int)bytes.getBodyLength()];bytes.readBytes(body);
            assertEquals(new RobotTelemetry("EXAMPLE",1,0,100),RobotTelemetry.fromByteBuffer(ByteBuffer.wrap(body)));
            assertEquals(command.getOrderId(),raw.getStringProperty(MessageHeaders.ORDER_ID));
            assertEquals("c007-trace",raw.getStringProperty(MessageHeaders.TRACE_ID));assertEquals("c007-correlation",raw.getJMSCorrelationID());
            try(RobotLink link=link("EXAMPLE")) { assertTrue(link.receive(200).isEmpty()); }
            assertNull(telemetry.receive(100));
        }
    }
}
