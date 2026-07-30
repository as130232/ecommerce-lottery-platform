-- ---------------------------------------------------------------------------
-- Lottery platform schema (MySQL 8)
-- Probability is stored as an integer in basis of 10000 (萬分比) to avoid
-- floating point rounding; per activity the prize probabilities must sum to
-- exactly 10000 (= 100%), including the THANKS row.
-- ---------------------------------------------------------------------------

CREATE TABLE app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE lottery_activity (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    per_user_draw_limit INT          NOT NULL DEFAULT 10,
    total_draw_limit    BIGINT       NULL,
    start_time          DATETIME     NULL,
    end_time            DATETIME     NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_activity_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE prize (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    activity_id     BIGINT       NOT NULL,
    name            VARCHAR(128) NOT NULL,
    type            VARCHAR(16)  NOT NULL COMMENT 'PRIZE / THANKS',
    probability     INT          NOT NULL COMMENT 'basis of 10000',
    total_stock     INT          NOT NULL DEFAULT 0,
    remaining_stock INT          NOT NULL DEFAULT 0,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_prize_activity (activity_id),
    CONSTRAINT fk_prize_activity FOREIGN KEY (activity_id) REFERENCES lottery_activity (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE draw_record (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    activity_id     BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    prize_id        BIGINT      NOT NULL,
    result          VARCHAR(16) NOT NULL COMMENT 'WIN / THANKS',
    idempotency_key VARCHAR(80) NOT NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_draw_idem (idempotency_key),
    KEY idx_draw_user_activity (activity_id, user_id),
    CONSTRAINT fk_draw_activity FOREIGN KEY (activity_id) REFERENCES lottery_activity (id),
    CONSTRAINT fk_draw_prize FOREIGN KEY (prize_id) REFERENCES prize (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
