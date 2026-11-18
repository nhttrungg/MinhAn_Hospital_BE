-- Optional descriptive copy; does not change prices, appointments or clinical snapshots.
ALTER TABLE rooms ADD COLUMN description VARCHAR(1000) NULL;
ALTER TABLE services ADD COLUMN description VARCHAR(1500) NULL;
