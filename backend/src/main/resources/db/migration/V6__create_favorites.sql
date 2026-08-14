CREATE TABLE favorites (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    fish_species_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_favorites PRIMARY KEY (id),
    CONSTRAINT uk_favorites_user_fish UNIQUE (user_id, fish_species_id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_fish FOREIGN KEY (fish_species_id)
        REFERENCES fish_species (id) ON DELETE RESTRICT,
    INDEX ix_favorites_user_created_id (user_id, created_at DESC, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
