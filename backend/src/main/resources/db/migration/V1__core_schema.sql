-- SmartRent core schema (MySQL 8). Flyway owns the schema; Hibernate ddl-auto is "none".

CREATE TABLE locations (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    city        VARCHAR(80)  NOT NULL,
    address     VARCHAR(200),
    latitude    DOUBLE       NOT NULL,
    longitude   DOUBLE       NOT NULL,
    CONSTRAINT uq_locations_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    phone         VARCHAR(30),
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE vehicles (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_number VARCHAR(20)  NOT NULL,
    make                VARCHAR(60)  NOT NULL,
    model               VARCHAR(60)  NOT NULL,
    model_year          INT          NOT NULL,
    vehicle_type        VARCHAR(20)  NOT NULL,
    fuel_type           VARCHAR(20)  NOT NULL,
    transmission        VARCHAR(20)  NOT NULL,
    seats               INT          NOT NULL,
    daily_rate          DECIMAL(10,2) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    location_id         BIGINT       NOT NULL,
    image_url           VARCHAR(500),
    description         VARCHAR(500),
    mileage_km          INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    CONSTRAINT uq_vehicles_registration UNIQUE (registration_number),
    CONSTRAINT fk_vehicles_location FOREIGN KEY (location_id) REFERENCES locations (id),
    CONSTRAINT ck_vehicles_seats CHECK (seats > 0),
    CONSTRAINT ck_vehicles_rate CHECK (daily_rate > 0)
) ENGINE=InnoDB;

CREATE INDEX idx_vehicles_search ON vehicles (status, vehicle_type, location_id, daily_rate);
CREATE INDEX idx_vehicles_seats ON vehicles (seats);

CREATE TABLE bookings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference       VARCHAR(20)   NOT NULL,
    user_id         BIGINT        NOT NULL,
    vehicle_id      BIGINT        NOT NULL,
    pickup_date     DATE          NOT NULL,   -- inclusive start of the half-open interval [pickup, return)
    return_date     DATE          NOT NULL,   -- exclusive end
    status          VARCHAR(20)   NOT NULL,
    daily_base_rate DECIMAL(10,2) NOT NULL,   -- snapshot of the vehicle rate at booking time
    total_price     DECIMAL(12,2) NOT NULL,   -- agreed price, never recalculated afterwards
    price_breakdown TEXT          NOT NULL,   -- JSON snapshot of the pricing explanation
    idempotency_key VARCHAR(64),
    cancellation_fee DECIMAL(12,2),
    created_at      DATETIME(6)   NOT NULL,
    cancelled_at    DATETIME(6),
    picked_up_at    DATETIME(6),
    returned_at     DATETIME(6),
    CONSTRAINT uq_bookings_reference UNIQUE (reference),
    CONSTRAINT uq_bookings_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT ck_bookings_dates CHECK (return_date > pickup_date)
) ENGINE=InnoDB;

CREATE INDEX idx_bookings_vehicle_dates ON bookings (vehicle_id, status, pickup_date, return_date);
CREATE INDEX idx_bookings_user ON bookings (user_id, created_at);
CREATE INDEX idx_bookings_status_pickup ON bookings (status, pickup_date);

CREATE TABLE booking_status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id  BIGINT       NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_by  VARCHAR(160) NOT NULL,
    note        VARCHAR(255),
    changed_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_history_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE=InnoDB;

CREATE INDEX idx_history_booking ON booking_status_history (booking_id, changed_at);

-- Single-row configuration for the dynamic pricing engine (id is always 1).
CREATE TABLE pricing_config (
    id                       BIGINT        NOT NULL PRIMARY KEY,
    weekend_surcharge        DECIMAL(6,4)  NOT NULL,
    demand_window_days       INT           NOT NULL,
    demand_baseline          DECIMAL(6,4)  NOT NULL,
    demand_weight            DECIMAL(6,4)  NOT NULL,
    max_demand_surcharge     DECIMAL(6,4)  NOT NULL,
    max_demand_discount      DECIMAL(6,4)  NOT NULL,
    availability_weight      DECIMAL(6,4)  NOT NULL,
    availability_threshold   DECIMAL(6,4)  NOT NULL,
    discount_7_days          DECIMAL(6,4)  NOT NULL,
    discount_14_days         DECIMAL(6,4)  NOT NULL,
    discount_30_days         DECIMAL(6,4)  NOT NULL,
    min_multiplier           DECIMAL(6,4)  NOT NULL,
    max_multiplier           DECIMAL(6,4)  NOT NULL,
    free_cancel_days         INT           NOT NULL,
    late_cancel_fee_rate     DECIMAL(6,4)  NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    updated_by               VARCHAR(160)  NOT NULL
) ENGINE=InnoDB;

INSERT INTO pricing_config (id, weekend_surcharge, demand_window_days, demand_baseline, demand_weight,
    max_demand_surcharge, max_demand_discount, availability_weight, availability_threshold,
    discount_7_days, discount_14_days, discount_30_days, min_multiplier, max_multiplier,
    free_cancel_days, late_cancel_fee_rate, updated_at, updated_by)
VALUES (1, 0.1500, 14, 0.3000, 0.5000, 0.2500, 0.1000, 0.3000, 0.5000,
    0.0500, 0.1000, 0.1500, 0.8000, 1.6000, 2, 0.2500, UTC_TIMESTAMP(6), 'system');

CREATE TABLE peak_periods (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    start_date DATE         NOT NULL,      -- inclusive
    end_date   DATE         NOT NULL,      -- inclusive
    surcharge  DECIMAL(6,4) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_peak_dates CHECK (end_date >= start_date)
) ENGINE=InnoDB;

-- Transactional outbox: written in the same DB transaction as the booking change.
CREATE TABLE outbox_events (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id    VARCHAR(36)  NOT NULL,
    event_type  VARCHAR(40)  NOT NULL,
    booking_id  BIGINT       NOT NULL,
    payload     TEXT         NOT NULL,
    status      VARCHAR(12)  NOT NULL,     -- PENDING, SENT, FAILED
    attempts    INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    sent_at     DATETIME(6),
    last_error  VARCHAR(500),
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id)
) ENGINE=InnoDB;

CREATE INDEX idx_outbox_status ON outbox_events (status, id);

CREATE TABLE notifications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id     VARCHAR(36)  NOT NULL,    -- unique => idempotent consumer
    event_type   VARCHAR(40)  NOT NULL,
    booking_id   BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(160) NOT NULL,
    message      VARCHAR(500) NOT NULL,
    status       VARCHAR(12)  NOT NULL,    -- PROCESSED, FAILED
    created_at   DATETIME(6)  NOT NULL,    -- when the event was produced
    processed_at DATETIME(6)  NOT NULL,
    CONSTRAINT uq_notifications_event UNIQUE (event_id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;

CREATE INDEX idx_notifications_user ON notifications (user_id, processed_at);

CREATE TABLE maintenance_records (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id        BIGINT        NOT NULL,
    description       VARCHAR(300)  NOT NULL,
    performed_on      DATE          NOT NULL,
    cost              DECIMAL(10,2) NOT NULL,
    odometer_km       INT,
    next_service_date DATE,
    created_by        VARCHAR(160)  NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    CONSTRAINT fk_maintenance_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
) ENGINE=InnoDB;

CREATE INDEX idx_maintenance_vehicle ON maintenance_records (vehicle_id, performed_on);

CREATE TABLE audit_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor       VARCHAR(160) NOT NULL,
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   VARCHAR(40),
    details     VARCHAR(500),
    created_at  DATETIME(6)  NOT NULL
) ENGINE=InnoDB;

CREATE INDEX idx_audit_created ON audit_logs (created_at);

CREATE TABLE report_runs (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_type        VARCHAR(40)  NOT NULL,
    from_date          DATE         NOT NULL,
    to_date            DATE         NOT NULL,   -- exclusive, same convention as rentals
    status             VARCHAR(12)  NOT NULL,   -- RUNNING, COMPLETED, FAILED, SKIPPED
    trigger_source     VARCHAR(160) NOT NULL,   -- SCHEDULER or the admin's e-mail
    started_at         DATETIME(6)  NOT NULL,
    finished_at        DATETIME(6),
    file_name          VARCHAR(200),
    file_path          VARCHAR(500),
    row_count          BIGINT,
    error_message      VARCHAR(500),
    batch_execution_id BIGINT
) ENGINE=InnoDB;

CREATE INDEX idx_report_runs_started ON report_runs (started_at);
