# ros-bridge — ROSJava node (owner: R)

**Not part of the Maven reactor.** Separate Gradle build, Java 11, in its own `ros:noetic` container
(rosjava is ROS1-only and does not build on Java 25).

Scope (blueprint §11): one node `doserunner_bridge` that
1. consumes `MissionCommand` from Artemis through C's `RobotLink` jar (compiled with `--release 11`),
2. publishes `geometry_msgs/Twist` on `/turtle1/cmd_vel`,
3. subscribes to `/turtle1/pose` and republishes it as `RobotTelemetry` v2.

The demo never depends on this module: `robot-sim` implements the same contract.
