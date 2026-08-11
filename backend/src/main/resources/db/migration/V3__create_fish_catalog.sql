CREATE TABLE fish_species (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slug VARCHAR(120) NOT NULL,
    common_name_zh VARCHAR(100) NOT NULL,
    scientific_name VARCHAR(160) NOT NULL,
    family_name_zh VARCHAR(100) NOT NULL,
    family_scientific_name VARCHAR(160) NOT NULL,
    genus_name_zh VARCHAR(100) NOT NULL,
    genus_scientific_name VARCHAR(160) NOT NULL,
    appearance TEXT NOT NULL,
    size_description TEXT NOT NULL,
    habitat_description TEXT NOT NULL,
    distribution TEXT NOT NULL,
    description TEXT NOT NULL,
    image_path VARCHAR(255) NOT NULL,
    image_alt_text VARCHAR(255) NOT NULL,
    image_source_url VARCHAR(1000) NOT NULL,
    image_author VARCHAR(255) NOT NULL,
    image_license_name VARCHAR(100) NOT NULL,
    image_license_url VARCHAR(1000) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_fish_species PRIMARY KEY (id),
    CONSTRAINT uk_fish_species_slug UNIQUE (slug),
    CONSTRAINT uk_fish_species_common_name_zh UNIQUE (common_name_zh),
    CONSTRAINT uk_fish_species_scientific_name UNIQUE (scientific_name),
    CONSTRAINT ck_fish_species_display_order CHECK (display_order > 0),
    INDEX ix_fish_species_display_order (display_order, id),
    INDEX ix_fish_species_family_name_zh (family_name_zh)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fish_aliases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    fish_species_id BIGINT NOT NULL,
    alias VARCHAR(100) NOT NULL,
    CONSTRAINT pk_fish_aliases PRIMARY KEY (id),
    CONSTRAINT uk_fish_aliases_species_alias UNIQUE (fish_species_id, alias),
    CONSTRAINT fk_fish_aliases_species FOREIGN KEY (fish_species_id)
        REFERENCES fish_species (id) ON DELETE CASCADE,
    INDEX ix_fish_aliases_alias (alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fish_habitats (
    fish_species_id BIGINT NOT NULL,
    habitat_code VARCHAR(20) NOT NULL,
    CONSTRAINT pk_fish_habitats PRIMARY KEY (fish_species_id, habitat_code),
    CONSTRAINT fk_fish_habitats_species FOREIGN KEY (fish_species_id)
        REFERENCES fish_species (id) ON DELETE CASCADE,
    CONSTRAINT ck_fish_habitats_code CHECK (
        habitat_code IN ('RIVER', 'LAKE', 'RESERVOIR', 'POND', 'STREAM')
    ),
    INDEX ix_fish_habitats_code (habitat_code, fish_species_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
