# Artemis broker config (owner: C)

Days 3–7: `broker.xml` with the addresses locked on Day 2
(`doserunner.events` multicast, `robot.commands` anycast + groups, `robot.telemetry` multicast, `DLQ`, `ExpiryQueue`),
`address-settings` (`max-delivery-attempts=3`, redelivery delay + multiplier, `dead-letter-address=DLQ`),
then the live/backup HA pair in week 2.

Day 1 runs the stock `apache/activemq-artemis` image with default settings.
