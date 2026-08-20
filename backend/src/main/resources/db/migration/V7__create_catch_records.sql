CREATE TABLE catch_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    fish_species_id BIGINT NOT NULL,
    caught_on DATE NOT NULL,
    location VARCHAR(200) NOT NULL,
    length_cm DECIMAL(8,2) NULL,
    weight_g DECIMAL(10,2) NULL,
    method VARCHAR(100) NULL,
    notes TEXT NULL,
    photo_object_key VARCHAR(512) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_catch_records PRIMARY KEY (id),
    CONSTRAINT fk_catch_records_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_catch_records_fish FOREIGN KEY (fish_species_id)
        REFERENCES fish_species (id) ON DELETE RESTRICT,
    INDEX ix_catch_records_user_caught_created_id
        (user_id, caught_on DESC, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
