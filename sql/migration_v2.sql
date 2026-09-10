USE suilin;

-- Run once when upgrading a database created by the earlier MVP schema.
ALTER TABLE elders ADD COLUMN family_id BIGINT NULL AFTER id;
ALTER TABLE elders MODIFY COLUMN birthday DATE NULL;
ALTER TABLE elders ADD INDEX idx_elders_family (family_id);
ALTER TABLE elders ADD CONSTRAINT fk_elders_family FOREIGN KEY (family_id) REFERENCES families(id);

ALTER TABLE sos_events ADD COLUMN handled_by_user_id BIGINT NULL AFTER created_at;
ALTER TABLE sos_events ADD COLUMN closed_at DATETIME NULL AFTER handled_at;

-- families, family_members, family_invites, care_tasks, notification_settings and feedbacks
-- are created by schema.sql. For an existing database, copy/run the corresponding CREATE TABLE
-- statements from schema.sql before the ALTER statements above that reference families.
