package com.fishbook.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FishSpeciesTest {
    private static final Instant CREATED = Instant.parse("2026-08-30T01:00:00Z");
    private static final Instant LATER = Instant.parse("2026-08-30T02:00:00Z");

    @Test
    void createsACompleteDraftWithoutPublishingIt() {
        FishSpecies fish = FishSpecies.createDraft("test-fish", validContent(), CREATED);

        assertThat(fish.id()).isNull();
        assertThat(fish.status()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(fish.publishedAt()).isNull();
        assertThat(fish.createdAt()).isEqualTo(CREATED);
        assertThat(fish.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void editsContentWithoutChangingIdentityOrPublicationState() {
        FishSpecies published = FishSpecies.restore(
                7L, "test-fish", validContent(), PublicationStatus.PUBLISHED,
                CREATED, CREATED, CREATED);

        FishSpecies edited = published.edit(contentNamed("测试鱼二号"), LATER);

        assertThat(edited.id()).isEqualTo(7L);
        assertThat(edited.slug()).isEqualTo("test-fish");
        assertThat(edited.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(edited.publishedAt()).isEqualTo(CREATED);
        assertThat(edited.commonNameZh()).isEqualTo("测试鱼二号");
        assertThat(edited.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void allowsOnlyTheThreeApprovedPublicationTransitions() {
        FishSpecies draft = FishSpecies.createDraft("test-fish", validContent(), CREATED);
        FishSpecies published = draft.publish(LATER);
        FishSpecies unpublished = published.unpublish(LATER.plusSeconds(60));
        FishSpecies republished = unpublished.publish(LATER.plusSeconds(120));

        assertThat(published.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(unpublished.status()).isEqualTo(PublicationStatus.UNPUBLISHED);
        assertThat(unpublished.publishedAt()).isEqualTo(LATER);
        assertThat(republished.status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(republished.publishedAt()).isEqualTo(LATER.plusSeconds(120));

        assertThatThrownBy(() -> draft.unpublish(LATER))
                .isInstanceOf(InvalidPublicationTransitionException.class);
        assertThatThrownBy(() -> published.publish(LATER.plusSeconds(60)))
                .isInstanceOf(InvalidPublicationTransitionException.class);
        assertThatThrownBy(() -> unpublished.unpublish(LATER.plusSeconds(120)))
                .isInstanceOf(InvalidPublicationTransitionException.class);
    }

    @Test
    void acceptsTheCanonical120CharacterSlugAndRejectsLongerOrMalformedSlugs() {
        assertThat(FishSpecies.createDraft("a".repeat(120), validContent(), CREATED).slug())
                .hasSize(120);

        assertThatThrownBy(() -> FishSpecies.createDraft("a".repeat(121), validContent(), CREATED))
                .isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> FishSpecies.createDraft("test--fish", validContent(), CREATED))
                .isInstanceOf(InvalidFishSpeciesException.class);
    }

    @Test
    void validatesAliasesHabitatsAndDisplayOrder() {
        assertThatThrownBy(() -> content(
                "测试鱼", "Testus piscis", List.of("别名", " 别名 "), Set.of(HabitatType.RIVER),
                "外观描述", validContent().image(), 1))
                .isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> content(
                "测试鱼", "Testus piscis", List.of(" "), Set.of(HabitatType.RIVER),
                "外观描述", validContent().image(), 1))
                .isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> content(
                "测试鱼", "Testus piscis", List.of("鱼".repeat(101)), Set.of(HabitatType.RIVER),
                "外观描述", validContent().image(), 1))
                .isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> content(
                "测试鱼", "Testus piscis", List.of("别名"), Set.of(),
                "外观描述", validContent().image(), 1))
                .isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> content(
                "测试鱼", "Testus piscis", List.of("别名"), Set.of(HabitatType.RIVER),
                "外观描述", validContent().image(), 0))
                .isInstanceOf(InvalidFishSpeciesException.class);
    }

    @Test
    void enforcesSchemaSizedNamesAndContentFieldsByCodePointCount() {
        assertThat(content(
                "鱼".repeat(100), "鱼".repeat(160), List.of("别名"), Set.of(HabitatType.RIVER),
                "鱼".repeat(10_000), validContent().image(), 1).commonNameZh()).hasSize(100);

        assertThatThrownBy(() -> content(
                "鱼".repeat(101), "Testus piscis", List.of("别名"), Set.of(HabitatType.RIVER),
                "外观描述", validContent().image(), 1))
                .isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> content(
                "测试鱼", "鱼".repeat(161), List.of("别名"), Set.of(HabitatType.RIVER),
                "外观描述", validContent().image(), 1))
                .isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> content(
                "测试鱼", "Testus piscis", List.of("别名"), Set.of(HabitatType.RIVER),
                "鱼".repeat(10_001), validContent().image(), 1))
                .isInstanceOf(InvalidFishSpeciesException.class);
    }

    @Test
    void validatesAndNormalizesLocalImageAttribution() {
        ImageAttribution image = new ImageAttribution(
                " /images/fish/test-fish.jpg ", " 测试鱼 ", " https://example.com/source ",
                " 作者 ", " CC BY 4.0 ", " https://example.com/license ");

        assertThat(image.path()).isEqualTo("/images/fish/test-fish.jpg");
        assertThat(image.altText()).isEqualTo("测试鱼");
        assertThat(image.sourceUrl()).isEqualTo("https://example.com/source");
        assertThatThrownBy(() -> new ImageAttribution(
                "/images/fish/test-fish.jpg", "测试鱼", "ftp://example.com/source", "作者", "许可",
                "https://example.com/license"))
                .isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> new ImageAttribution(
                "/images/fish/" + "a".repeat(239) + ".jpg", "测试鱼", "https://example.com/source", "作者", "许可",
                "https://example.com/license"))
                .isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> new ImageAttribution(
                "/images/fish/test-fish.jpg", "测试鱼", "https://" + "a".repeat(994), "作者", "许可",
                "https://example.com/license"))
                .isInstanceOf(InvalidFishSpeciesException.class);
    }

    @Test
    void requiresNonNullAuditAndPublicationValues() {
        assertThatThrownBy(() -> FishSpecies.createDraft("test-fish", validContent(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FishSpecies.restore(
                7L, "test-fish", validContent(), null, null, CREATED, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FishSpecies.restore(
                7L, "test-fish", validContent(), PublicationStatus.PUBLISHED, null, CREATED, CREATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonCanonicalSlug() {
        assertThatThrownBy(() -> fish("Cyprinus carpio"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void imageMustBeLocalAndFullyAttributed() {
        assertThatThrownBy(() -> new ImageAttribution(
                "https://remote.example/carp.jpg", "鲤", "source", "author", "CC BY 4.0", "license"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensivelyCopiesAliasesAndHabitats() {
        List<String> aliases = new ArrayList<>(List.of("鲤鱼"));
        Set<HabitatType> habitats = new HashSet<>(Set.of(HabitatType.RIVER));
        FishSpecies fish = fish("cyprinus-carpio", aliases, habitats);
        aliases.add("污染值");
        habitats.add(HabitatType.POND);
        assertThat(fish.aliases()).containsExactly("鲤鱼");
        assertThat(fish.habitats()).containsExactly(HabitatType.RIVER);
    }

    private static FishSpecies fish(String slug) {
        return fish(slug, List.of("鲤鱼"), Set.of(HabitatType.RIVER));
    }

    private static FishSpecies fish(
            String slug, List<String> aliases, Set<HabitatType> habitats) {
        Instant now = Instant.parse("2026-08-11T00:00:00Z");
        return new FishSpecies(
                1L, slug, "鲤", "Cyprinus carpio", "鲤科", "Cyprinidae",
                "鲤属", "Cyprinus", aliases, habitats,
                "体形呈纺锤形。", "常见个体为中型鱼。", "生活在淡水水域。",
                "分布于中国多地。", "常见淡水鱼。",
                new ImageAttribution(
                        "/images/fish/cyprinus-carpio.jpg",
                        "鲤（Cyprinus carpio）",
                        "https://commons.wikimedia.org/wiki/File:Cyprinus_carpio.jpeg",
                        "Test Author", "CC BY 4.0",
                        "https://creativecommons.org/licenses/by/4.0/"),
                1, now, now);
    }

    private static FishSpeciesContent validContent() {
        return content(
                "测试鱼", "Testus piscis", List.of("测试鱼别名"), Set.of(HabitatType.RIVER), "外观描述",
                new ImageAttribution(
                        "/images/fish/test-fish.jpg", "测试鱼", "https://example.com/source",
                        "作者", "CC BY 4.0", "https://example.com/license"),
                1);
    }

    private static FishSpeciesContent content(
            String commonNameZh,
            String scientificName,
            List<String> aliases,
            Set<HabitatType> habitats,
            String appearance,
            ImageAttribution image,
            int displayOrder) {
        return new FishSpeciesContent(
                commonNameZh, scientificName, "测试科", "Testidae", "测试属", "Testus", aliases, habitats,
                appearance, "尺寸描述", "栖息地描述", "分布描述", "物种描述", image, displayOrder);
    }

    private static FishSpeciesContent contentNamed(String commonNameZh) {
        FishSpeciesContent content = validContent();
        return new FishSpeciesContent(
                commonNameZh, content.scientificName(), content.familyNameZh(),
                content.familyScientificName(), content.genusNameZh(), content.genusScientificName(),
                content.aliases(), content.habitats(), content.appearance(), content.sizeDescription(),
                content.habitatDescription(), content.distribution(), content.description(), content.image(),
                content.displayOrder());
    }
}
