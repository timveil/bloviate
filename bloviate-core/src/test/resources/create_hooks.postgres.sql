-- Schema for PostgresSqlHooksTest: a detail table the fill populates, and a summary table the
-- after hook derives from it.
CREATE TABLE detail
(
    id     INT PRIMARY KEY,
    grp    INT,
    amount INT
);

CREATE TABLE summary
(
    grp   INT,
    total BIGINT,
    n     BIGINT
);
