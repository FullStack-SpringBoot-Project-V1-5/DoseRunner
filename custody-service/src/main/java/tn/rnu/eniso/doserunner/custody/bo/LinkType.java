package tn.rnu.eniso.doserunner.custody.bo;

/** What a custody link records. One value per contracts event that custody-service turns into a link. */
public enum LinkType {
    ORDER_PLACED,
    ORDER_VALIDATED,
    ROBOT_ASSIGNED,
    /** Link #1 of the physical handoff: pharmacy to robot. */
    PICKUP_CONFIRMED,
    /** Link #2 of the physical handoff: robot to nurse. */
    DELIVERY_CONFIRMED,
    MISSION_ABORTED
}
