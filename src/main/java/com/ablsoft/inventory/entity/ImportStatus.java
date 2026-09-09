package com.ablsoft.inventory.entity;

/** Lifecycle of one upload. */
public enum ImportStatus {

    /** Accepted and queued, not yet picked up by a worker. */
    PENDING,
    /** A worker is reading the file; the row counters are climbing. */
    RUNNING,
    /** Finished. Counters are final. */
    COMPLETED,
    /** Stopped on an error, or left behind by a crash. Some chunks may already be applied. */
    FAILED
}
