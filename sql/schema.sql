CREATE DATABASE IF NOT EXISTS suilin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE suilin;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(50) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'FAMILY',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS elders (
  id BIGINT PRIMARY KEY,
  creator_user_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  relation VARCHAR(30) NOT NULL,
  birthday DATE NOT NULL,
  phone VARCHAR(20) NULL,
  health_tags_json JSON NULL,
  bind_status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
  bound_client_id VARCHAR(128) NULL,
  bound_client_token VARCHAR(128) NULL,
  bound_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_elders_creator (creator_user_id),
  UNIQUE KEY uk_elders_client_token (bound_client_token),
  CONSTRAINT fk_elders_creator FOREIGN KEY (creator_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS elder_invites (
  id BIGINT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  inviter_user_id BIGINT NOT NULL,
  invite_token VARCHAR(64) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
  expires_at DATETIME NOT NULL,
  accepted_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_invite_elder (elder_id),
  CONSTRAINT fk_invite_elder FOREIGN KEY (elder_id) REFERENCES elders(id),
  CONSTRAINT fk_invite_user FOREIGN KEY (inviter_user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reminders (
  id BIGINT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  type VARCHAR(30) NOT NULL,
  schedule_time VARCHAR(20) NOT NULL,
  dosage VARCHAR(100) NULL,
  repeat_rule VARCHAR(100) NULL,
  notify_after_minutes INT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_reminder_elder (elder_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reminder_records (
  id BIGINT PRIMARY KEY,
  reminder_id BIGINT NOT NULL,
  elder_id BIGINT NOT NULL,
  scheduled_at DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL,
  completed_at DATETIME NULL,
  source_type VARCHAR(20) NOT NULL DEFAULT 'ELDER_ACTION',
  created_at DATETIME NOT NULL,
  INDEX idx_record_elder_time (elder_id, scheduled_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS health_records (
  id BIGINT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  metric_type VARCHAR(30) NOT NULL,
  value_text VARCHAR(255) NOT NULL,
  unit VARCHAR(30) NULL,
  source_type VARCHAR(30) NOT NULL,
  source_ref VARCHAR(128) NULL,
  recorded_by_user_id BIGINT NULL,
  measured_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_health_elder_metric_time (elder_id, metric_type, measured_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS devices (
  id BIGINT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  device_type VARCHAR(30) NOT NULL,
  device_sn VARCHAR(100) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
  last_online_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_device_elder (elder_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sos_events (
  id BIGINT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  source_ref VARCHAR(128) NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL,
  handled_at DATETIME NULL,
  INDEX idx_sos_elder_time (elder_id, created_at)
) ENGINE=InnoDB;
