CREATE SEQUENCE user_seq INCREMENT BY 1 START WITH 3;

CREATE TABLE beatmaps
(
    id              INT          NOT NULL,
    mode            INT          NOT NULL,
    md5             VARCHAR(32)  NOT NULL,
    status          INT          NOT NULL,
    version         VARCHAR(128) NOT NULL,
    submission_date datetime     NOT NULL,
    last_updated    datetime     NOT NULL,
    playcount       BIGINT       NOT NULL,
    passcount       BIGINT       NOT NULL,
    total_length    INT          NOT NULL,
    drain_length    INT          NOT NULL,
    count_normal    INT          NOT NULL,
    count_slider    INT          NOT NULL,
    count_spinner   INT          NOT NULL,
    max_combo       INT          NOT NULL,
    bpm             REAL         NOT NULL,
    cs              REAL         NOT NULL,
    ar              REAL         NOT NULL,
    od              REAL         NOT NULL,
    hp              REAL         NOT NULL,
    star_rating     REAL         NOT NULL,
    beatmapset_id   INT          NOT NULL,
    CONSTRAINT pk_beatmaps PRIMARY KEY (id)
);

CREATE TABLE beatmapsets
(
    id                INT           NOT NULL,
    title             VARCHAR(128)  NULL,
    title_unicode     VARCHAR(128)  NULL,
    artist            VARCHAR(128)  NULL,
    artist_unicode    VARCHAR(128)  NULL,
    source            VARCHAR(128)  NULL,
    source_unicode    VARCHAR(128)  NULL,
    creator           VARCHAR(128)  NULL,
    tags              VARCHAR(1024) NULL,
    submission_status INT           NOT NULL,
    has_video         BIT(1)        NOT NULL,
    has_storyboard    BIT(1)        NOT NULL,
    submission_date   datetime      NOT NULL,
    approved_date     datetime      NULL,
    last_updated      datetime      NOT NULL,
    total_playcount   BIGINT        NOT NULL,
    language_id       INT           NOT NULL,
    genre_id          INT           NOT NULL,
    CONSTRAINT pk_beatmapsets PRIMARY KEY (id)
);

CREATE TABLE channels
(
    id               VARCHAR(36)  NOT NULL DEFAULT (UUID()),
    name             VARCHAR(96)  NOT NULL,
    topic            VARCHAR(256) NOT NULL,
    read_privileges  INT          NOT NULL,
    write_privileges INT          NOT NULL,
    auto_join        BIT(1)       NOT NULL,
    created_at       datetime     NOT NULL,
    updated_at       datetime     NOT NULL,
    temporary        BIT(1)       NOT NULL,
    CONSTRAINT pk_channels PRIMARY KEY (id)
);

CREATE TABLE scores
(
    id                 INT AUTO_INCREMENT NOT NULL,
    user_id            INT                NOT NULL,
    online_checksum    VARCHAR(32)        NOT NULL,
    beatmap_id         INT                NOT NULL,
    score              BIGINT             NOT NULL,
    performance_points FLOAT              NOT NULL,
    accuracy           FLOAT              NOT NULL,
    highest_combo      INT                NOT NULL,
    full_combo         BIT(1)             NOT NULL,
    mods               INT                NOT NULL,
    num_300s           INT                NOT NULL,
    num_100s           INT                NOT NULL,
    num_50s            INT                NOT NULL,
    num_misses         INT                NOT NULL,
    num_gekis          INT                NOT NULL,
    num_katus          INT                NOT NULL,
    grade              VARCHAR(2)         NOT NULL,
    submission_status  INT                NOT NULL,
    mode               INT                NOT NULL,
    passed             BIT(1)             NOT NULL,
    time_elapsed       INT                NOT NULL,
    created_at         datetime           NOT NULL,
    updated_at         datetime           NOT NULL,
    CONSTRAINT pk_scores PRIMARY KEY (id)
);

CREATE TABLE sessions
(
    id                        VARCHAR(36)  NOT NULL DEFAULT (UUID()),
    user_id                   INT          NOT NULL,
    utc_offset                INT          NOT NULL,
    gamemode                  INT          NOT NULL,
    country                   SMALLINT     NOT NULL,
    latitude                  FLOAT        NOT NULL,
    longitude                 FLOAT        NOT NULL,
    display_city_location     BIT(1)       NOT NULL,
    action                    INT          NOT NULL,
    info_text                 VARCHAR(128) NOT NULL,
    beatmap_md5               VARCHAR(32)  NOT NULL,
    beatmap_id                INT          NOT NULL,
    mods                      INT          NOT NULL,
    pm_private                BIT(1)       NOT NULL,
    receive_match_updates     BIT(1)       NOT NULL,
    spectator_host_session_id VARCHAR(36)  NULL,
    away_message              VARCHAR(64)  NOT NULL,
    multiplayer_match_id      INT          NULL,
    last_communicated_at      datetime     NOT NULL,
    last_np_beatmap_id        INT          NOT NULL,
    is_primary_session        BIT(1)       NOT NULL,
    osu_version               VARCHAR(255) NOT NULL,
    osu_path_md5              VARCHAR(255) NOT NULL,
    adapters_str              VARCHAR(255) NOT NULL,
    adapters_md5              VARCHAR(255) NOT NULL,
    uninstall_md5             VARCHAR(255) NOT NULL,
    disk_signature_md5        VARCHAR(255) NOT NULL,
    created_at                datetime     NOT NULL,
    updated_at                datetime     NOT NULL,
    CONSTRAINT pk_sessions PRIMARY KEY (id)
);

CREATE TABLE stats
(
    id                 INT AUTO_INCREMENT NOT NULL,
    user_id            INT                NULL,
    gamemode           INT                NULL,
    total_score        BIGINT             NOT NULL DEFAULT 0,
    ranked_score       BIGINT             NOT NULL DEFAULT 0,
    performance_points INT                NOT NULL DEFAULT 0,
    play_count         INT                NOT NULL DEFAULT 0,
    play_time          INT                NOT NULL DEFAULT 0,
    accuracy           FLOAT              NOT NULL DEFAULT 0.0,
    highest_combo      INT                NOT NULL DEFAULT 0,
    total_hits         INT                NOT NULL DEFAULT 0,
    replay_views       INT                NOT NULL DEFAULT 0,
    xh_count           INT                NOT NULL DEFAULT 0,
    x_count            INT                NOT NULL DEFAULT 0,
    sh_count           INT                NOT NULL DEFAULT 0,
    s_count            INT                NOT NULL DEFAULT 0,
    a_count            INT                NOT NULL DEFAULT 0,
    CONSTRAINT pk_stats PRIMARY KEY (id)
);

CREATE TABLE users
(
    id           INT         NOT NULL,
    username     VARCHAR(32) NOT NULL,
    email        VARCHAR(64) NOT NULL,
    password_md5 VARCHAR(32) NOT NULL,
    country      SMALLINT    NOT NULL,
    silence_end  datetime    NULL,
    privileges   INT         NOT NULL DEFAULT 1,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

ALTER TABLE channels
    ADD CONSTRAINT uc_channels_name UNIQUE (name);

ALTER TABLE users
    ADD CONSTRAINT uc_users_email UNIQUE (email);

ALTER TABLE users
    ADD CONSTRAINT uc_users_username UNIQUE (username);

CREATE INDEX beatmap_status_idx ON scores (submission_status);

CREATE INDEX beatmaps_md5_idx ON beatmaps (md5);

CREATE INDEX channels_name_idx ON channels (name);

CREATE INDEX score_user_mode_status_pp_idx ON scores (user_id, mode, submission_status, performance_points DESC);

ALTER TABLE beatmaps
    ADD CONSTRAINT FK_BEATMAPS_ON_BEATMAPSET FOREIGN KEY (beatmapset_id) REFERENCES beatmapsets (id);

ALTER TABLE scores
    ADD CONSTRAINT FK_SCORES_ON_BEATMAP FOREIGN KEY (beatmap_id) REFERENCES beatmaps (id);

CREATE INDEX beatmap_mode_status_idx ON scores (beatmap_id);

ALTER TABLE scores
    ADD CONSTRAINT FK_SCORES_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE sessions
    ADD CONSTRAINT FK_SESSIONS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);

ALTER TABLE stats
    ADD CONSTRAINT FK_STATS_ON_USER FOREIGN KEY (user_id) REFERENCES users (id);