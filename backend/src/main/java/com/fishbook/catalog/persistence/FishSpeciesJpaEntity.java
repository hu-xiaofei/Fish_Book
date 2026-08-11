package com.fishbook.catalog.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "fish_species")
class FishSpeciesJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120, unique = true)
    private String slug;

    @Column(name = "common_name_zh", nullable = false, length = 100, unique = true)
    private String commonNameZh;

    @Column(name = "scientific_name", nullable = false, length = 160, unique = true)
    private String scientificName;

    @Column(name = "family_name_zh", nullable = false, length = 100)
    private String familyNameZh;

    @Column(name = "family_scientific_name", nullable = false, length = 160)
    private String familyScientificName;

    @Column(name = "genus_name_zh", nullable = false, length = 100)
    private String genusNameZh;

    @Column(name = "genus_scientific_name", nullable = false, length = 160)
    private String genusScientificName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String appearance;

    @Column(name = "size_description", nullable = false, columnDefinition = "TEXT")
    private String sizeDescription;

    @Column(name = "habitat_description", nullable = false, columnDefinition = "TEXT")
    private String habitatDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String distribution;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_path", nullable = false, length = 255)
    private String imagePath;

    @Column(name = "image_alt_text", nullable = false, length = 255)
    private String imageAltText;

    @Column(name = "image_source_url", nullable = false, length = 1000)
    private String imageSourceUrl;

    @Column(name = "image_author", nullable = false, length = 255)
    private String imageAuthor;

    @Column(name = "image_license_name", nullable = false, length = 100)
    private String imageLicenseName;

    @Column(name = "image_license_url", nullable = false, length = 1000)
    private String imageLicenseUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "fishSpecies", fetch = FetchType.LAZY)
    private Set<FishAliasJpaEntity> aliases = new HashSet<>();

    @OneToMany(mappedBy = "fishSpecies", fetch = FetchType.LAZY)
    private Set<FishHabitatJpaEntity> habitats = new HashSet<>();

    protected FishSpeciesJpaEntity() {}

    Long getId() { return id; }

    String getSlug() { return slug; }

    String getCommonNameZh() { return commonNameZh; }

    String getScientificName() { return scientificName; }

    String getFamilyNameZh() { return familyNameZh; }

    String getFamilyScientificName() { return familyScientificName; }

    String getGenusNameZh() { return genusNameZh; }

    String getGenusScientificName() { return genusScientificName; }

    String getAppearance() { return appearance; }

    String getSizeDescription() { return sizeDescription; }

    String getHabitatDescription() { return habitatDescription; }

    String getDistribution() { return distribution; }

    String getDescription() { return description; }

    String getImagePath() { return imagePath; }

    String getImageAltText() { return imageAltText; }

    String getImageSourceUrl() { return imageSourceUrl; }

    String getImageAuthor() { return imageAuthor; }

    String getImageLicenseName() { return imageLicenseName; }

    String getImageLicenseUrl() { return imageLicenseUrl; }

    int getDisplayOrder() { return displayOrder; }

    Instant getCreatedAt() { return createdAt; }

    Instant getUpdatedAt() { return updatedAt; }

    Set<FishAliasJpaEntity> getAliases() { return aliases; }

    Set<FishHabitatJpaEntity> getHabitats() { return habitats; }
}
