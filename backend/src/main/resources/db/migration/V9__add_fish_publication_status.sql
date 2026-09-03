ALTER TABLE fish_species
    ADD COLUMN publication_status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' AFTER display_order,
    ADD COLUMN published_at TIMESTAMP(6) NULL AFTER publication_status,
    ADD CONSTRAINT ck_fish_species_publication_status CHECK (
        publication_status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED')
    );

UPDATE fish_species
SET published_at = created_at
WHERE publication_status = 'PUBLISHED' AND published_at IS NULL;

ALTER TABLE fish_species
    ALTER COLUMN publication_status DROP DEFAULT;

CREATE INDEX ix_fish_species_publication_display_order
    ON fish_species (publication_status, display_order, id);
