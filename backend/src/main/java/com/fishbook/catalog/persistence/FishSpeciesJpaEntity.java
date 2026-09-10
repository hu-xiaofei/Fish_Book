package com.fishbook.catalog.persistence;

import com.fishbook.catalog.domain.FishSpecies;
import com.fishbook.catalog.domain.PublicationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20)
    private PublicationStatus publicationStatus;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "fishSpecies", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FishAliasJpaEntity> aliases = new HashSet<>();

    @OneToMany(mappedBy = "fishSpecies", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FishHabitatJpaEntity> habitats = new HashSet<>();

    protected FishSpeciesJpaEntity() {}

    FishSpeciesJpaEntity(FishSpecies fish) {
        slug = fish.slug();
        apply(fish);
    }

    void apply(FishSpecies fish) {
        commonNameZh = fish.commonNameZh();
        scientificName = fish.scientificName();
        familyNameZh = fish.familyNameZh();
        familyScientificName = fish.familyScientificName();
        genusNameZh = fish.genusNameZh();
        genusScientificName = fish.genusScientificName();
        appearance = fish.appearance();
        sizeDescription = fish.sizeDescription();
        habitatDescription = fish.habitatDescription();
        distribution = fish.distribution();
        description = fish.description();
        imagePath = fish.image().path();
        imageAltText = fish.image().altText();
        imageSourceUrl = fish.image().sourceUrl();
        imageAuthor = fish.image().author();
        imageLicenseName = fish.image().licenseName();
        imageLicenseUrl = fish.image().licenseUrl();
        displayOrder = fish.displayOrder();
        publicationStatus = fish.status();
        publishedAt = fish.publishedAt();
        createdAt = fish.createdAt();
        updatedAt = fish.updatedAt();
        fish.aliases().forEach(alias -> aliases.add(new FishAliasJpaEntity(this, alias)));
        fish.habitats().forEach(habitat -> habitats.add(new FishHabitatJpaEntity(this, habitat)));
    }

    void clearDetails() {
        aliases.clear();
        habitats.clear();
    }

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

    PublicationStatus getPublicationStatus() { return publicationStatus; }

    Instant getPublishedAt() { return publishedAt; }

    Instant getCreatedAt() { return createdAt; }

    Instant getUpdatedAt() { return updatedAt; }

    Set<FishAliasJpaEntity> getAliases() { return aliases; }

    Set<FishHabitatJpaEntity> getHabitats() { return habitats; }
}
