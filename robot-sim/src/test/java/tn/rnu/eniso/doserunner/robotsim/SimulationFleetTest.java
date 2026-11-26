package tn.rnu.eniso.doserunner.robotsim;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import tn.rnu.eniso.doserunner.contracts.common.GridPoint;
import tn.rnu.eniso.doserunner.contracts.robot.*;
import tn.rnu.eniso.doserunner.robotsim.facade.SimulationFleet;
import tn.rnu.eniso.doserunner.robotsim.messaging.TelemetryPublisher;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationFleetTest {
    final AtomicLong time = new AtomicLong();
    final TelemetryPublisher publisher = mock(TelemetryPublisher.class);
    final SimulationFleet fleet = new SimulationFleet(publisher, time::get, 0, 0, false);
    static MissionCommand command(String robot, int seq, GridPoint... points) {
        return new MissionCommand(UUID.randomUUID().toString(), robot, UUID.randomUUID().toString(), seq,
                new ArrayList<>(List.of(points)), "C1", Instant.now());
    }
    void tick(String id, long seconds) { time.set(TimeUnit.SECONDS.toNanos(seconds)); fleet.tick(id); }
    @AfterEach void close() { fleet.close(); }
    @Test void initialPositionAndDeterministicMovementAndFinalDestination() {
        var c = command("R-01", 1, new GridPoint(1,0), new GridPoint(1,1));
        assertThat(fleet.snapshot("R-01").x()).isZero();
        assertThat(fleet.snapshot("R-01").battery()).isEqualTo(100);
        assertThat(fleet.accept(c,"trace","correlation")).isTrue();
        fleet.tick("R-01"); verifyNoInteractions(publisher);
        tick("R-01",1); assertThat(fleet.snapshot("R-01").x()).isEqualTo(1);
        assertThat(fleet.snapshot("R-01").y()).isZero();
        tick("R-01",2); assertThat(fleet.snapshot("R-01").activeMission()).isNull();
        assertThat(fleet.snapshot("R-01").y()).isEqualTo(1);
        assertThat(fleet.snapshot("R-01").battery()).isEqualTo(98);
        var order = inOrder(publisher);
        order.verify(publisher).publish(eq(new RobotTelemetry("R-01",1,0,99)),any());
        order.verify(publisher).publish(eq(new RobotTelemetry("R-01",1,1,98)),any());
    }
    @Test void independentRobotsAndOneWaitingSlotExecuteInOrder() {
        var first = command("R-01",1,new GridPoint(1,0));
        var second = command("R-01",3,new GridPoint(0,1));
        fleet.accept(first,null,null); fleet.accept(second,null,null);
        fleet.accept(command("R-02",1,new GridPoint(1,0)),null,null);
        var overflow = command("R-01",4,new GridPoint(1,0));
        assertThatThrownBy(() -> fleet.accept(overflow,null,null)).hasMessageContaining("slot full");
        assertThat(fleet.snapshot("R-01").lastAcceptedSeq()).isEqualTo(3);
        tick("R-01",1); assertThat(fleet.snapshot("R-01").activeMission()).isEqualTo(second.getMissionId());
        assertThat(fleet.snapshot("R-02").x()).isZero();
        tick("R-02",1); tick("R-01",2);
        assertThat(fleet.snapshot("R-01").y()).isEqualTo(1);
        assertThat(fleet.snapshot("R-02").x()).isEqualTo(1);
        assertThat(fleet.accept(overflow,null,null)).isTrue();
    }
    @Test void replaysIncludingOldAcceptedCommandsAreNoOpsButConflictsReject() {
        var first=command("R-01",1,new GridPoint(1,0));
        fleet.accept(first,null,null); tick("R-01",1);
        fleet.accept(command("R-01",5,new GridPoint(0,1)),null,null);
        assertThat(fleet.accept(first,"other-trace",null)).isFalse();
        assertThat(fleet.accept(first,null,null)).isFalse();
        first.setSeq(6);
        assertThatThrownBy(() -> fleet.accept(first,null,null))
                .hasMessageContaining("Conflicting");
        assertThatThrownBy(() -> fleet.accept(command("R-01",2),null,null)).hasMessageContaining("stale");
        tick("R-01",2); verify(publisher,times(2)).publish(any(),any());
    }
    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void uuidCasingCannotBypassMissionConflictChecks(boolean uppercaseFirst, boolean completed) {
        String id="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
        var c=command("R-01",1,new GridPoint(1,0));
        c.setMissionId(uppercaseFirst ? id.toUpperCase(Locale.ROOT) : id);
        fleet.accept(c,null,null);
        if (completed) tick("R-01",1);
        String original=c.getMissionId();
        c.setMissionId(uppercaseFirst ? id : id.toUpperCase(Locale.ROOT));
        c.setSeq(2);
        assertThatThrownBy(() -> fleet.accept(c,null,null)).hasMessageContaining("Conflicting");
        assertThat(fleet.snapshot("R-01").lastAcceptedSeq()).isEqualTo(1);
        c.setSeq(1);
        assertThatThrownBy(() -> fleet.accept(c,null,null)).hasMessageContaining("Conflicting");
        c.setMissionId(original);
        assertThat(fleet.accept(c,null,null)).isFalse();
        tick("R-01",2);
        verify(publisher,times(1)).publish(any(),any());
        assertThat(fleet.snapshot("R-01").battery()).isEqualTo(99);
    }
    @Test void shutdownInterruptsInFlightPublicationAndCancelsWaitingMission() throws Exception {
        var entered=new CountDownLatch(1); var interrupted=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            try { assertThat(release.await(5,TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException error) {
                interrupted.countDown(); Thread.currentThread().interrupt();
                throw new IllegalStateException("Publication interrupted by shutdown",error);
            }
            return null;
        }).when(publisher).publish(any(),any());
        var real=new SimulationFleet(publisher,System::nanoTime,0,0,true);
        try(var closer=Executors.newSingleThreadExecutor()) {
            real.start();
            real.accept(command("R-01",1,new GridPoint(1,0),new GridPoint(2,0)),null,null);
            real.accept(command("R-01",2,new GridPoint(0,1)),null,null);
            assertThat(entered.await(4,TimeUnit.SECONDS)).isTrue();
            closer.submit(real::close).get(3,TimeUnit.SECONDS);
            assertThat(interrupted.await(1,TimeUnit.SECONDS)).isTrue();
            assertThat(real.snapshot("R-01").activeMission()).isNull();
            assertThat(real.snapshot("R-01").waiting()).isFalse();
            assertThat(real.snapshot("R-01").battery()).isEqualTo(100);
            assertThat(real.snapshot("R-01").x()).isZero();
            real.tick("R-01");
            verify(publisher,times(1)).publish(any(),any());
            assertThatThrownBy(() -> real.accept(command("R-01",3),null,null)).hasMessageContaining("stopped");
        } finally { release.countDown(); real.close(); }
    }
    @Test void routeIsCopiedAndEmptyRouteCompletesImmediately() {
        fleet.accept(command("R-03",1),null,null);
        assertThat(fleet.snapshot("R-03").activeMission()).isNull(); verifyNoInteractions(publisher);
        var c=command("R-01",1,new GridPoint(1,0)); fleet.accept(c,null,null);
        c.getWaypoints().getFirst().setX(100);
        tick("R-01",1); assertThat(fleet.snapshot("R-01").x()).isEqualTo(1);
    }
    @Test void telemetryFailurePausesAndRetriesWithCappedBackoff() {
        fleet.accept(command("R-01",1,new GridPoint(1,0),new GridPoint(2,0)),null,null);
        doThrow(new IllegalStateException("offline")).when(publisher).publish(any(),any());
        for (int second : new int[]{1,2,4,8,16,24}) {
            assertThatThrownBy(() -> tick("R-01",second)).hasMessage("offline");
            tick("R-01",second); // no retry until the due time
        }
        assertThat(fleet.snapshot("R-01").x()).isZero();
        verify(publisher,times(6)).publish(eq(new RobotTelemetry("R-01",1,0,99)),any());
        reset(publisher); tick("R-01",32); tick("R-01",33);
        assertThat(fleet.snapshot("R-01").x()).isEqualTo(2);
        assertThat(fleet.snapshot("R-01").battery()).isEqualTo(98);
    }
    @Test void batteryFloorsAtZeroAndRestartLosesHistory() {
        var points=new GridPoint[101]; for(int i=0;i<101;i++) points[i]=new GridPoint(i+1,0);
        var c=command("R-01",1,points); fleet.accept(c,null,null);
        for(int i=1;i<=101;i++) tick("R-01",i);
        assertThat(fleet.snapshot("R-01").battery()).isZero();
        var restarted=new SimulationFleet(publisher,System::nanoTime,0,0,false);
        try {
            assertThat(restarted.accept(c,null,null)).isTrue();
            assertThat(restarted.snapshot("R-01").battery()).isEqualTo(100);
        } finally { restarted.close(); }
    }
    @Test void configuredPharmacyAndShutdown() {
        var custom=new SimulationFleet(publisher,time::get,3,4,false);
        custom.accept(command("R-01",1,new GridPoint(4,4)),null,null);
        assertThat(custom.snapshot("R-01").x()).isEqualTo(3);
        custom.close(); time.set(TimeUnit.SECONDS.toNanos(2)); custom.tick("R-01"); verifyNoInteractions(publisher);
        assertThatThrownBy(() -> custom.accept(command("R-01",2),null,null)).hasMessageContaining("stopped");
    }
    @ParameterizedTest @ValueSource(strings={"unknown","seq","uuid","compartment","time","negative","jump","repeat","null","large"})
    void invalidCommandsDoNotMutateState(String kind) {
        var c=command("R-01",1,new GridPoint(1,0));
        switch(kind) {
            case "unknown" -> c.setRobotId("R-99");
            case "seq" -> c.setSeq(0);
            case "uuid" -> c.setMissionId("bad");
            case "compartment" -> c.setCompartmentCode(" ");
            case "time" -> c.setIssuedAt(Instant.EPOCH.minusMillis(1));
            case "negative" -> c.setWaypoints(List.of(new GridPoint(-1,0)));
            case "jump" -> c.setWaypoints(List.of(new GridPoint(2,0)));
            case "repeat" -> c.setWaypoints(List.of(new GridPoint(1,0),new GridPoint(0,0)));
            case "null" -> c.setWaypoints(Arrays.asList((GridPoint)null));
            case "large" -> c.setWaypoints(Collections.nCopies(10001,new GridPoint(1,0)));
        }
        assertThatThrownBy(() -> fleet.accept(c,null,null)).isInstanceOf(RuntimeException.class);
        assertThat(fleet.snapshot("R-01").lastAcceptedSeq()).isZero(); verifyNoInteractions(publisher);
    }
    @Test void acceptanceDelayStartsAfterRobotLockIsAcquired() throws Exception {
        fleet.accept(command("R-01",1,new GridPoint(1,0)),null,null);
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertThat(release.await(3,TimeUnit.SECONDS)).isTrue(); return null; })
                .when(publisher).publish(any(),any());
        time.set(TimeUnit.SECONDS.toNanos(1));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var movement=executor.submit(() -> fleet.tick("R-01"));
            assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            var acceptance=executor.submit(() -> fleet.accept(command("R-01",2,new GridPoint(0,1)),null,null));
            time.set(TimeUnit.SECONDS.toNanos(5)); release.countDown();
            movement.get(3,TimeUnit.SECONDS); acceptance.get(3,TimeUnit.SECONDS);
        } finally { release.countDown(); }
        reset(publisher); tick("R-01",5); verifyNoInteractions(publisher);
        tick("R-01",6); verify(publisher).publish(any(),any());
    }
    @Test void slowPublicationDoesNotCauseCatchUpOrImmediateRetry() {
        fleet.accept(command("R-01",1,new GridPoint(1,0),new GridPoint(2,0)),null,null);
        doAnswer(call -> { time.addAndGet(TimeUnit.SECONDS.toNanos(3)); return null; }).when(publisher).publish(any(),any());
        tick("R-01",1); // publication finishes at second 4
        fleet.tick("R-01"); verify(publisher,times(1)).publish(any(),any());
        reset(publisher);
        doAnswer(call -> { time.addAndGet(TimeUnit.SECONDS.toNanos(3)); throw new IllegalStateException("slow failure"); })
                .when(publisher).publish(any(),any());
        assertThatThrownBy(() -> tick("R-01",5)).hasMessage("slow failure"); // failure at second 8
        fleet.tick("R-01"); verify(publisher,times(1)).publish(any(),any());
        reset(publisher); tick("R-01",9);
        assertThat(fleet.snapshot("R-01").x()).isEqualTo(2);
    }
    @Test void concurrentDuplicatesAcceptOnlyOnce() throws Exception {
        var c=command("R-01",1,new GridPoint(1,0));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(() -> fleet.accept(c,null,null));
            var b=executor.submit(() -> fleet.accept(c,null,null));
            assertThat(List.of(a.get(3,TimeUnit.SECONDS),b.get(3,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        tick("R-01",1); verify(publisher,times(1)).publish(any(),any());
    }
    @Test void blockedRobotDoesNotBlockAnotherRobot() throws Exception {
        fleet.accept(command("R-01",1,new GridPoint(1,0)),null,null);
        fleet.accept(command("R-02",1,new GridPoint(0,1)),null,null);
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); assertThat(release.await(3,TimeUnit.SECONDS)).isTrue(); return null; })
                .when(publisher).publish(eq(new RobotTelemetry("R-01",1,0,99)),any());
        time.set(TimeUnit.SECONDS.toNanos(1));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var blocked=executor.submit(() -> fleet.tick("R-01"));
            try {
                assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
                executor.submit(() -> fleet.tick("R-02")).get(2,TimeUnit.SECONDS);
                assertThat(fleet.snapshot("R-02").y()).isEqualTo(1);
            } finally { release.countDown(); }
            blocked.get(3,TimeUnit.SECONDS);
        }
    }
    @Test void realSchedulerMovesAtOneCellPerSecond() throws Exception {
        var times=new CopyOnWriteArrayList<Long>(); var done=new CountDownLatch(3);
        doAnswer(call -> { times.add(System.nanoTime()); done.countDown(); return null; }).when(publisher).publish(any(),any());
        var real=new SimulationFleet(publisher,System::nanoTime,0,0,true);
        try {
            real.start(); long start=System.nanoTime();
            real.accept(command("R-01",1,new GridPoint(1,0),new GridPoint(2,0),new GridPoint(3,0)),null,null);
            assertThat(done.await(7,TimeUnit.SECONDS)).isTrue();
            long previous=start;
            for(long current:times) { assertThat((current-previous)/1e9).isBetween(0.8,2.0); previous=current; }
        } finally { real.close(); }
    }
}
