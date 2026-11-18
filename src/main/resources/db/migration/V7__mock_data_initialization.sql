-- The demo initializer is opt-in; migrations only create its completion marker.
CREATE TABLE mock_data_runs (
    dataset VARCHAR(64) NOT NULL PRIMARY KEY,
    initialized_on DATE NOT NULL,
    completed_at DATETIME(6) NULL
) ENGINE=InnoDB;
