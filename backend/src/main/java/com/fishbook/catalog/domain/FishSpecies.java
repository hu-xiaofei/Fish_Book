package com.fishbook.catalog.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FishSpecies {
    private final Long id;
    private final String slug;
    private final FishSpeciesContent content;
    private final PublicationStatus status;
    private final Instant publishedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private FishSpecies(
            Long id,
            String slug,
            FishSpeciesContent content,
            PublicationStatus status,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.slug = requireCanonicalSlug(slug);
        this.content = requireArgument(content, "content");
        this.status = requireArgument(status, "status");
        this.publishedAt = publishedAt;
        this.createdAt = requireArgument(createdAt, "createdAt");
        this.updatedAt = requireArgument(updatedAt, "updatedAt");
    }

    public FishSpecies(
            Long id, String slug, String commonNameZh, String scientificName, String familyNameZh,
            String familyScientificName, String genusNameZh, String genusScientificName,
            List<String> aliases, Set<HabitatType> habitats, String appearance, String sizeDescription,
            String habitatDescription, String distribution, String description, ImageAttribution image,
            int displayOrder, Instant createdAt, Instant updatedAt) {
        this(id, slug, new FishSpeciesContent(
                        commonNameZh, scientificName, familyNameZh, familyScientificName, genusNameZh,
                        genusScientificName, aliases, habitats, appearance, sizeDescription,
                        habitatDescription, distribution, description, image, displayOrder),
                PublicationStatus.PUBLISHED, createdAt, createdAt, updatedAt);
    }

    public static FishSpecies createDraft(String slug, FishSpeciesContent content, Instant now) {
        requireAdministratorContent(content);
        return new FishSpecies(null, slug, content, PublicationStatus.DRAFT, null, now, now);
    }

    public static FishSpecies restore(
            Long id,
            String slug,
            FishSpeciesContent content,
            PublicationStatus status,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        requireArgument(status, "status");
        if ((status == PublicationStatus.DRAFT) != (publishedAt == null)) {
            throw new IllegalArgumentException("publishedAt must match publication status");
        }
        return new FishSpecies(id, slug, content, status, publishedAt, createdAt, updatedAt);
    }

    public FishSpecies edit(FishSpeciesContent content, Instant now) {
        requireAdministratorContent(content);
        return new FishSpecies(id, slug, content, status, publishedAt, createdAt, now);
    }

    public FishSpecies publish(Instant now) {
        if (status != PublicationStatus.DRAFT && status != PublicationStatus.UNPUBLISHED) {
            throw new InvalidPublicationTransitionException(status, PublicationStatus.PUBLISHED);
        }
        return new FishSpecies(id, slug, content, PublicationStatus.PUBLISHED, now, createdAt, now);
    }

    public FishSpecies unpublish(Instant now) {
        if (status != PublicationStatus.PUBLISHED) {
            throw new InvalidPublicationTransitionException(status, PublicationStatus.UNPUBLISHED);
        }
        return new FishSpecies(id, slug, content, PublicationStatus.UNPUBLISHED, publishedAt, createdAt, now);
    }

    public Long id() { return id; }
    public String slug() { return slug; }
    public FishSpeciesContent content() { return content; }
    public PublicationStatus status() { return status; }
    public Instant publishedAt() { return publishedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String commonNameZh() { return content.commonNameZh(); }
    public String scientificName() { return content.scientificName(); }
    public String familyNameZh() { return content.familyNameZh(); }
    public String familyScientificName() { return content.familyScientificName(); }
    public String genusNameZh() { return content.genusNameZh(); }
    public String genusScientificName() { return content.genusScientificName(); }
    public List<String> aliases() { return content.aliases(); }
    public Set<HabitatType> habitats() { return content.habitats(); }
    public String appearance() { return content.appearance(); }
    public String sizeDescription() { return content.sizeDescription(); }
    public String habitatDescription() { return content.habitatDescription(); }
    public String distribution() { return content.distribution(); }
    public String description() { return content.description(); }
    public ImageAttribution image() { return content.image(); }
    public int displayOrder() { return content.displayOrder(); }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FishSpecies fish)) {
            return false;
        }
        return Objects.equals(id, fish.id)
                && Objects.equals(slug, fish.slug)
                && Objects.equals(content, fish.content)
                && status == fish.status
                && Objects.equals(publishedAt, fish.publishedAt)
                && Objects.equals(createdAt, fish.createdAt)
                && Objects.equals(updatedAt, fish.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, slug, content, status, publishedAt, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "FishSpecies[id=" + id
                + ", slug=" + slug
                + ", content=" + content
                + ", status=" + status
                + ", publishedAt=" + publishedAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt + "]";
    }

    private static String requireCanonicalSlug(String slug) {
        if (slug == null
                || slug.codePointCount(0, slug.length()) > 120
                || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new InvalidFishSpeciesException("slug must be canonical");
        }
        return slug;
    }

    private static <T> T requireArgument(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        return value;
    }

    private static void requireAdministratorContent(FishSpeciesContent content) {
        if (content == null) {
            throw new InvalidFishSpeciesException("content must not be null");
        }
    }
}
