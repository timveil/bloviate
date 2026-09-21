-- Issue #640: every MySQL temporal type, so a fill can be compared across JVM and session time
-- zones. DATE, TIME and DATETIME hold a wall clock with no zone attached; TIMESTAMP is an instant
-- that MySQL converts from the session zone on write and back to it on read, which is the type's
-- definition rather than anything Bloviate chooses.

CREATE TABLE temporal_types
(
    id INT NOT NULL AUTO_INCREMENT,
    d  DATE      NOT NULL,
    t  TIME      NOT NULL,
    dt DATETIME  NOT NULL,
    ts TIMESTAMP NULL,
    PRIMARY KEY (id)
);
