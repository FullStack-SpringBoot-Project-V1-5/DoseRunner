package tn.rnu.eniso.doserunner.dispatch;

import jakarta.jms.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tn.rnu.eniso.doserunner.contracts.events.*;
import tn.rnu.eniso.doserunner.contracts.robot.MissionCommand;
import tn.rnu.eniso.doserunner.contracts.messaging.*;
import tn.rnu.eniso.doserunner.dispatch.facade.*;
import tn.rnu.eniso.doserunner.dispatch.dao.DispatchMessageStore;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE, properties={"dispatch.messaging.relay-enabled=false"})
@DirtiesContext
class DispatchMessagingIT {
    @Container static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:16.16");
    @Container static final GenericContainer<?> BROKER=ArtemisTestBroker.create();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",DB::getJdbcUrl); r.add("spring.datasource.username",DB::getUsername);
        r.add("spring.datasource.password",DB::getPassword);
        r.add("spring.artemis.broker-url",() -> "tcp://"+BROKER.getHost()+":"+BROKER.getMappedPort(61616)+"?callTimeout=2000&reconnectAttempts=0");
        r.add("spring.artemis.user",() -> "c004"); r.add("spring.artemis.password",() -> "c004-test-password");
    }
    @Autowired ConnectionFactory factory;
    @Autowired AvroJmsMessages messages;
    @Autowired AvroMessageCodec codec;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxRelay relay;
    @Autowired OrderValidatedFacade facade;
    @Autowired DispatchMessageStore store;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    tn.rnu.eniso.doserunner.dispatch.pres.OrderValidatedListener listener;

    @BeforeEach void reset() {
        jdbc.update("DELETE FROM dispatch_inbox"); jdbc.update("DELETE FROM dispatch_outbox");
        jdbc.update("DELETE FROM dispatch_command_sequence"); jdbc.update("DELETE FROM missions");
        jdbc.update("UPDATE robots SET status='IDLE', battery=100");
    }
    int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class); }
    void send(OrderValidated e) throws Exception {
        try(var c=factory.createConnection();var s=c.createSession();var p=s.createProducer(s.createTopic(Addresses.EVENTS))) {
            var m=messages.toMessage(s,e); m.setStringProperty(MessageHeaders.ORDER_ID,e.getOrderId());
            m.setStringProperty(MessageHeaders.TRACE_ID,"integration-trace"); p.send(m);
        }
    }
    static void until(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        while(!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(25);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    @Test void consumesPublishesAndDeduplicatesRealAvroMessages() throws Exception {
        var e=OrderValidatedListenerTest.event();
        try(var c=factory.createConnection();var s=c.createSession()) {
            c.start();
            var events=s.createConsumer(s.createQueue(Addresses.EVENTS+"::"+Addresses.QUEUE_ORDER_SERVICE),
                    "eventType='RobotAssigned' AND orderId='"+e.getOrderId()+"'");
            var commands=s.createConsumer(s.createQueue(Addresses.ROBOT_COMMANDS),"orderId='"+e.getOrderId()+"'");
            send(e); until(() -> count("dispatch_inbox")==1);
            assertThat(count("missions")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM robots WHERE id='R-01'",String.class)).isEqualTo("BUSY");
            assertThat(relay.relayNext()).isTrue();
            var am=events.receive(5000); var cm=commands.receive(5000);
            assertThat(am).isInstanceOf(BytesMessage.class); assertThat(cm).isInstanceOf(BytesMessage.class);
            var assigned=messages.fromMessage(am,RobotAssigned.class);
            var command=messages.fromMessage(cm,MissionCommand.class);
            assertThat(assigned.getOrderId()).isEqualTo(e.getOrderId());
            assertThat(UUID.fromString(assigned.getEventId())).isNotNull();
            assertThat(assigned.getRobotId()).isEqualTo("R-01");
            assertThat(assigned.getEtaSeconds()).isEqualTo(5);
            assertThat(assigned.getPath()).containsExactly(new tn.rnu.eniso.doserunner.contracts.common.GridPoint(0,0),
                    new tn.rnu.eniso.doserunner.contracts.common.GridPoint(1,0),new tn.rnu.eniso.doserunner.contracts.common.GridPoint(2,0),
                    new tn.rnu.eniso.doserunner.contracts.common.GridPoint(2,1),new tn.rnu.eniso.doserunner.contracts.common.GridPoint(2,2),
                    new tn.rnu.eniso.doserunner.contracts.common.GridPoint(2,3));
            assertThat(command.getMissionId()).isEqualTo(assigned.getMissionId());
            assertThat(command.getWaypoints()).isEqualTo(assigned.getPath().subList(1,6));
            assertThat(command.getSeq()).isEqualTo(1); assertThat(command.getCompartmentCode()).isEqualTo("C1");
            for(var m:new Message[]{am,cm}) {
                assertThat(m.getStringProperty(MessageHeaders.TRACE_ID)).isEqualTo("integration-trace");
                assertThat(m.getStringProperty(MessageHeaders.ORDER_ID)).isEqualTo(e.getOrderId());
                assertThat(m.getJMSDeliveryMode()).isEqualTo(DeliveryMode.PERSISTENT);
                assertThat(m.getStringProperty("_AMQ_DUPL_ID")).isNotBlank();
            }
            assertThat(cm.getStringProperty(MessageHeaders.GROUP_ID)).isEqualTo("R-01");
            assertThat(cm.getStringProperty(MessageHeaders.ROBOT_ID)).isEqualTo("R-01");
            assertThat(cm.getStringProperty(MessageHeaders.EVENT_TYPE)).isEqualTo("MissionCommand");
            // Simulate broker commit followed by a crash before the outbox DB commit.
            jdbc.update("UPDATE dispatch_outbox SET sent_at=NULL");
            assertThat(relay.relayNext()).isTrue();
            assertThat(events.receive(500)).isNull(); assertThat(commands.receive(500)).isNull();
            var duplicateDone=new CountDownLatch(1);
            org.mockito.Mockito.doAnswer(call -> {
                try { return call.callRealMethod(); } finally { duplicateDone.countDown(); }
            }).when(listener).onMessage(org.mockito.ArgumentMatchers.any(Message.class));
            send(e);
            assertThat(duplicateDone.await(5,TimeUnit.SECONDS)).isTrue();
            org.mockito.Mockito.reset(listener);
            facade.process(e,"different-trace");
            var another=OrderValidated.newBuilder(e).setEventId(UUID.randomUUID().toString()).build();
            facade.process(another,"trace");
            assertThat(count("dispatch_inbox")).isEqualTo(2); assertThat(count("dispatch_outbox")).isEqualTo(1);
            assertThat(count("missions")).isEqualTo(1); assertThat(relay.relayNext()).isFalse();
        }
    }
    @Test void concurrentDuplicatesAndSequencePersistence() throws Exception {
        var e=OrderValidatedListenerTest.event();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> facade.process(e,"trace"));
            var second=executor.submit(() -> facade.process(e,"trace"));
            first.get(10,TimeUnit.SECONDS); second.get(10,TimeUnit.SECONDS);
        }
        assertThat(count("missions")).isEqualTo(1); assertThat(count("dispatch_outbox")).isEqualTo(1);
        jdbc.update("UPDATE robots SET status='IDLE' WHERE id='R-01'");
        facade.process(OrderValidatedListenerTest.event(),"trace");
        assertThat(jdbc.queryForList("SELECT command_seq FROM dispatch_outbox ORDER BY id",Integer.class)).containsExactly(1,2);
        assertThatThrownBy(() -> facade.process(OrderValidated.newBuilder(e).setControlled(false).build(),"trace"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Conflicting");
    }
    @Test void databaseFailureRollsBackAllEffects() {
        jdbc.execute("CREATE FUNCTION reject_outbox() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic DB failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_outbox BEFORE INSERT ON dispatch_outbox FOR EACH ROW EXECUTE FUNCTION reject_outbox()");
        try {
            assertThatThrownBy(() -> facade.process(OrderValidatedListenerTest.event(),"trace")).isInstanceOf(RuntimeException.class);
            for(String table:new String[]{"missions","dispatch_inbox","dispatch_outbox","dispatch_command_sequence"}) assertThat(count(table)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM robots WHERE status='BUSY'",Integer.class)).isZero();
        } finally { jdbc.execute("DROP TRIGGER reject_outbox ON dispatch_outbox"); jdbc.execute("DROP FUNCTION reject_outbox()"); }
    }
    @Test void brokerFailureRetainsPendingForRecovery() throws Exception {
        facade.process(OrderValidatedListenerTest.event(),"trace");
        try(var wrong=new org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory(
                "tcp://"+BROKER.getHost()+":"+BROKER.getMappedPort(61616),"wrong-user","wrong-password")) {
            var template=new org.springframework.jms.core.JmsTemplate(wrong); template.setSessionTransacted(true);
            var failing=new tn.rnu.eniso.doserunner.dispatch.messaging.DispatchEventPublisher(template,codec,messages);
            var manager=new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource());
            assertThatThrownBy(() -> new org.springframework.transaction.support.TransactionTemplate(manager)
                    .execute(status -> { new OutboxRelay(store,failing).relayNext(); return null; })).isInstanceOf(RuntimeException.class);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispatch_outbox WHERE sent_at IS NULL",Integer.class)).isOne();
        assertThat(relay.relayNext()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dispatch_outbox WHERE sent_at IS NULL",Integer.class)).isZero();
    }
    @Test void rejectsInvalidPayloadsAndUnavailableFleetThroughRollbackAndDlq() throws Exception {
        for(int kind=0;kind<5;kind++) {
            var attempts=new CopyOnWriteArrayList<Integer>();
            var redelivered=new CopyOnWriteArrayList<Boolean>();
            var entered=new Semaphore(0);
            var failDelivery=new Semaphore(0);
            org.mockito.Mockito.doAnswer(call -> {
                Message incoming=call.getArgument(0);
                attempts.add(incoming.getIntProperty("JMSXDeliveryCount"));
                redelivered.add(incoming.getJMSRedelivered());
                // Hold the real listener before failure so the DLQ can be checked at every attempt.
                entered.release();
                assertThat(failDelivery.tryAcquire(10,TimeUnit.SECONDS)).isTrue();
                return call.callRealMethod();
            }).when(listener).onMessage(org.mockito.ArgumentMatchers.any(Message.class));
            String probe=UUID.randomUUID().toString();
            if(kind==4) jdbc.update("UPDATE robots SET battery=0");
            try(var c=factory.createConnection();var s=c.createSession();
                var p=s.createProducer(s.createTopic(Addresses.EVENTS));
                var dlq=s.createConsumer(s.createQueue(Addresses.DLQ),"probeId='"+probe+"'")) {
                c.start(); Message m;
                if(kind==0) { var bytes=s.createBytesMessage(); bytes.writeBytes(new byte[]{1,2,3}); m=bytes; }
                else if(kind==1) m=s.createTextMessage("unsupported");
                else { var event=OrderValidatedListenerTest.event(); if(kind==2) event.setOrderId("invalid-uuid");
                    m=messages.toMessage(s,event); if(kind==3) m.setStringProperty(MessageHeaders.ORDER_ID,"conflict"); }
                m.setStringProperty(MessageHeaders.EVENT_TYPE,"OrderValidated");
                m.setStringProperty(MessageHeaders.TRACE_ID,"poison-trace-"+probe);
                m.setJMSCorrelationID(probe);
                m.setStringProperty("probeId",probe); p.send(m);
                long failedAt=0;
                for(int attempt=1;attempt<=3;attempt++) {
                    assertThat(entered.tryAcquire(8,TimeUnit.SECONDS)).as("kind %s delivery %s",kind,attempt).isTrue();
                    if(attempt>1) assertThat(System.nanoTime()-failedAt)
                            .isGreaterThanOrEqualTo(Duration.ofMillis(attempt==2 ? 800 : 1600).toNanos());
                    assertThat(attempts).hasSize(attempt);
                    assertThat(dlq.receive(100)).as("DLQ before failure %s of kind %s",attempt,kind).isNull();
                    failedAt=System.nanoTime();
                    failDelivery.release();
                }
                var dead=dlq.receive(15000); assertThat(dead).as("poison kind %s",kind).isNotNull();
                assertThat(dead.getStringProperty("_AMQ_ORIG_ADDRESS")).isEqualTo(Addresses.EVENTS);
                assertThat(dead.getStringProperty("_AMQ_ORIG_QUEUE")).isEqualTo(Addresses.QUEUE_DISPATCH_SERVICE);
                assertThat(dead.getStringProperty(MessageHeaders.TRACE_ID)).isEqualTo("poison-trace-"+probe);
                assertThat(dead.getJMSCorrelationID()).isEqualTo(probe);
                if(m instanceof BytesMessage bytes) {
                    bytes.reset();
                    assertThat(dead.getBody(byte[].class)).isEqualTo(bytes.getBody(byte[].class));
                } else assertThat(dead.getBody(String.class)).isEqualTo("unsupported");
                assertThat(entered.tryAcquire(2500,TimeUnit.MILLISECONDS)).as("no fourth delivery").isFalse();
                assertThat(dlq.receive(250)).as("one dead letter only").isNull();
                assertThat(attempts).containsExactly(1,2,3);
                assertThat(redelivered).containsExactly(false,true,true);
                System.out.println("C009 poison kind="+kind+": listener attempts 1/2/3, delayed retries, one DLQ copy, no fourth delivery");
            } finally {
                failDelivery.release(4);
                org.mockito.Mockito.reset(listener);
            }
            for(String table:new String[]{"missions","dispatch_inbox","dispatch_outbox","dispatch_command_sequence"})
                assertThat(count(table)).as(table).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM robots WHERE status='BUSY'",Integer.class)).isZero();
        }
    }
    @Test void newApplicationContextRecoversPendingAndRetainsSequenceAndInbox() throws Exception {
        var e=OrderValidatedListenerTest.event();
        facade.process(e,"restart-trace");
        var args=new String[]{"--spring.datasource.url="+DB.getJdbcUrl(),
                "--spring.datasource.username="+DB.getUsername(),"--spring.datasource.password="+DB.getPassword(),
                "--spring.artemis.broker-url=tcp://"+BROKER.getHost()+":"+BROKER.getMappedPort(61616),
                "--spring.artemis.user=c004","--spring.artemis.password=c004-test-password",
                "--dispatch.messaging.listener-enabled=false","--dispatch.messaging.relay-enabled=true",
                "--dispatch.messaging.relay-delay-ms=50"};
        try(var restarted=new org.springframework.boot.builder.SpringApplicationBuilder(DispatchServiceApplication.class)
                .web(org.springframework.boot.WebApplicationType.NONE).run(args)) {
            until(() -> jdbc.queryForObject("SELECT count(*) FROM dispatch_outbox WHERE sent_at IS NULL",Integer.class)==0);
            var recovered=restarted.getBean(OrderValidatedFacade.class);
            recovered.process(e,"trace");
            assertThat(count("missions")).isOne(); assertThat(count("dispatch_outbox")).isOne();
            jdbc.update("UPDATE robots SET status='IDLE' WHERE id='R-01'");
            recovered.process(OrderValidatedListenerTest.event(),"trace");
            until(() -> jdbc.queryForObject("SELECT count(*) FROM dispatch_outbox WHERE sent_at IS NULL",Integer.class)==0);
            assertThat(jdbc.queryForList("SELECT command_seq FROM dispatch_outbox ORDER BY id",Integer.class)).containsExactly(1,2);
        }
    }
    @Test void validatesFieldsMappingsAndSequenceOverflowWithoutPartialWrites() {
        var valid=OrderValidatedListenerTest.event();
        var blank=OrderValidated.newBuilder(valid).setPharmacistId(" ").build();
        assertThatThrownBy(() -> facade.process(blank,"trace")).isInstanceOf(IllegalArgumentException.class);
        var invalid=OrderValidated.newBuilder(valid).setEventId("1-1-1-1-1").build();
        assertThatThrownBy(() -> facade.process(invalid,"trace")).isInstanceOf(IllegalArgumentException.class);
        var destination=OrderValidated.newBuilder(valid).build();
        destination.getDestination().getPoint().setX(-1);
        assertThatThrownBy(() -> facade.process(destination,"trace")).isInstanceOf(IllegalArgumentException.class);
        jdbc.update("INSERT INTO dispatch_command_sequence VALUES ('R-01',2147483647)");
        assertThatThrownBy(() -> facade.process(OrderValidatedListenerTest.event(),"trace")).isInstanceOf(RuntimeException.class);
        assertThat(count("missions")).isZero(); assertThat(count("dispatch_inbox")).isZero();
        assertThat(jdbc.queryForObject("SELECT last_value FROM dispatch_command_sequence WHERE robot_id='R-01'",Integer.class))
                .isEqualTo(Integer.MAX_VALUE);
        jdbc.update("UPDATE robots SET status='OFFLINE' WHERE id<>'R-03'");
        jdbc.update("UPDATE robots SET id='R-NO-MAPPING' WHERE id='R-03'");
        try {
            assertThatThrownBy(() -> facade.process(OrderValidatedListenerTest.event(),"trace"))
                    .isInstanceOf(java.lang.IllegalStateException.class).hasMessageContaining("Missing compartment");
            assertThat(count("missions")).isZero(); assertThat(count("dispatch_outbox")).isZero();
        } finally { jdbc.update("UPDATE robots SET id='R-03' WHERE id='R-NO-MAPPING'"); }
    }
    @Test void databaseCommitFailureAfterJmsCommitRetriesTheSamePair() throws Exception {
        var e=OrderValidatedListenerTest.event(); facade.process(e,"trace");
        jdbc.execute("CREATE FUNCTION reject_sent_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'synthetic relay commit failure'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER reject_sent_commit AFTER UPDATE ON dispatch_outbox "
                +"DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION reject_sent_commit()");
        try {
            assertThatThrownBy(() -> relay.relayNext()).isInstanceOf(RuntimeException.class)
                    .hasStackTraceContaining("synthetic relay commit failure");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM dispatch_outbox WHERE sent_at IS NULL",Integer.class)).isOne();
        } finally {
            jdbc.execute("DROP TRIGGER reject_sent_commit ON dispatch_outbox"); jdbc.execute("DROP FUNCTION reject_sent_commit()");
        }
        assertThat(relay.relayNext()).isTrue();
        try(var c=factory.createConnection();var session=c.createSession();
            var commands=session.createConsumer(session.createQueue(Addresses.ROBOT_COMMANDS),"orderId='"+e.getOrderId()+"'")) {
            c.start(); assertThat(commands.receive(5000)).isNotNull(); assertThat(commands.receive(500)).isNull();
        }
    }
    @Test void brokerFilterPreventsUnrelatedEventsEnteringDispatchQueue() throws Exception {
        String probe=UUID.randomUUID().toString();
        try(var c=factory.createConnection();var s=c.createSession();
            var p=s.createProducer(s.createTopic(Addresses.EVENTS))) {
            var m=s.createBytesMessage(); m.setStringProperty(MessageHeaders.EVENT_TYPE,"OrderPlaced");
            m.setStringProperty("probeId",probe); p.send(m);
            var browser=s.createBrowser(s.createQueue(Addresses.EVENTS+"::dispatch-service"),"probeId='"+probe+"'");
            assertThat(browser.getEnumeration().hasMoreElements()).isFalse();
            var other=s.createConsumer(s.createQueue(Addresses.EVENTS+"::order-service"),"probeId='"+probe+"'");
            c.start(); assertThat(other.receive(5000)).isNotNull();
        }
    }
}
