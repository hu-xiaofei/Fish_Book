package com.fishbook.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    private static final String FISH_EMOJI = "\uD83D\uDC1F";

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
    void retainsRecordLikeValueSemanticsAcrossEveryAggregateField() {
        FishSpecies fish = FishSpecies.createDraft("test-fish", validContent(), CREATED);
        FishSpecies equivalent = FishSpecies.createDraft("test-fish", validContent(), CREATED);

        assertThat(fish).isEqualTo(equivalent).hasSameHashCodeAs(equivalent);
        assertThat(new HashSet<>(Set.of(fish))).contains(equivalent);
        assertThat(fish.toString()).contains("FishSpecies[").contains("slug=test-fish");

        assertThat(fish).isNotEqualTo(FishSpecies.createDraft("other-fish", validContent(), CREATED));
        assertThat(fish).isNotEqualTo(FishSpecies.createDraft("test-fish", contentNamed("其他鱼"), CREATED));
        assertThat(fish).isNotEqualTo(FishSpecies.createDraft("test-fish", validContent(), LATER));
        assertThat(fish).isNotEqualTo(fish.edit(validContent(), LATER));
        assertThat(fish).isNotEqualTo(FishSpecies.restore(
                7L, "test-fish", validContent(), PublicationStatus.DRAFT, null, CREATED, CREATED));
        assertThat(FishSpecies.restore(
                7L, "test-fish", validContent(), PublicationStatus.PUBLISHED, CREATED, CREATED, CREATED))
                .isNotEqualTo(FishSpecies.restore(
                        7L, "test-fish", validContent(), PublicationStatus.PUBLISHED,
                        LATER, CREATED, CREATED));
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
    void rejectsAliasesEquivalentUnderTheStorageCollation() {
        assertThatThrownBy(() -> contentWithAliases(List.of("Black Fish", "black fish")))
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
    void acceptsAndRejectsEverySchemaSizedContentFieldByUnicodeCodePointCount() {
        List<StringField> fields = List.of(
                new StringField("commonNameZh", 100),
                new StringField("scientificName", 160),
                new StringField("familyNameZh", 100),
                new StringField("familyScientificName", 160),
                new StringField("genusNameZh", 100),
                new StringField("genusScientificName", 160),
                new StringField("appearance", 10_000),
                new StringField("sizeDescription", 10_000),
                new StringField("habitatDescription", 10_000),
                new StringField("distribution", 10_000),
                new StringField("description", 10_000));

        for (StringField field : fields) {
            assertThatCode(() -> contentWith(field.name(), FISH_EMOJI.repeat(field.limit())))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> contentWith(field.name(), FISH_EMOJI.repeat(field.limit() + 1)))
                    .isInstanceOf(InvalidFishSpeciesException.class);
        }
    }

    @Test
    void acceptsAndRejectsEverySchemaSizedImageFieldByUnicodeCodePointCount() {
        List<ImageField> fields = List.of(
                new ImageField("path", localImagePath(255), localImagePath(256)),
                new ImageField("altText", FISH_EMOJI.repeat(255), FISH_EMOJI.repeat(256)),
                new ImageField("sourceUrl", urlOfCodePointLength(1_000), urlOfCodePointLength(1_001)),
                new ImageField("author", FISH_EMOJI.repeat(255), FISH_EMOJI.repeat(256)),
                new ImageField("licenseName", FISH_EMOJI.repeat(100), FISH_EMOJI.repeat(101)),
                new ImageField("licenseUrl", urlOfCodePointLength(1_000), urlOfCodePointLength(1_001)));

        for (ImageField field : fields) {
            assertThatCode(() -> imageWith(field.name(), field.atLimit())).doesNotThrowAnyException();
            assertThatThrownBy(() -> imageWith(field.name(), field.overLimit()))
                    .isInstanceOf(InvalidFishSpeciesException.class);
        }
    }

    @Test
    void acceptsAndRejectsAliasBoundariesByUnicodeCodePointCount() {
        FishSpeciesContent accepted = contentWithAliases(List.of(FISH_EMOJI.repeat(100)));

        assertThat(accepted.aliases()).containsExactly(FISH_EMOJI.repeat(100));
        assertThatThrownBy(() -> contentWithAliases(List.of(FISH_EMOJI.repeat(101))))
                .isInstanceOf(InvalidFishSpeciesException.class);
    }

    @Test
    void rejectsNullForEveryAdministratorContentInput() {
        for (String field : List.of(
                "commonNameZh", "scientificName", "familyNameZh", "familyScientificName",
                "genusNameZh", "genusScientificName", "appearance", "sizeDescription",
                "habitatDescription", "distribution", "description")) {
            assertThatThrownBy(() -> contentWith(field, null))
                    .isInstanceOf(InvalidFishSpeciesException.class);
        }
        assertThatThrownBy(() -> contentWithAliases(null)).isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> contentWithHabitats(null)).isInstanceOf(InvalidFishSpeciesException.class);
        assertThatThrownBy(() -> contentWithImage(null)).isInstanceOf(InvalidFishSpeciesException.class);

        for (String field : List.of("path", "altText", "sourceUrl", "author", "licenseName", "licenseUrl")) {
            assertThatThrownBy(() -> imageWith(field, null))
                    .isInstanceOf(InvalidFishSpeciesException.class);
        }
    }

    @Test
    void requiresEveryValidPublicationStateAndAuditCombinationWhenRestoring() {
        List<RestoreCase> validStates = List.of(
                new RestoreCase(PublicationStatus.DRAFT, null),
                new RestoreCase(PublicationStatus.PUBLISHED, CREATED),
                new RestoreCase(PublicationStatus.UNPUBLISHED, CREATED));
        for (RestoreCase state : validStates) {
            assertThatCode(() -> FishSpecies.restore(
                    7L, "test-fish", validContent(), state.status(), state.publishedAt(), CREATED, LATER))
                    .doesNotThrowAnyException();
        }

        List<RestoreCase> invalidStates = List.of(
                new RestoreCase(PublicationStatus.DRAFT, CREATED),
                new RestoreCase(PublicationStatus.PUBLISHED, null),
                new RestoreCase(PublicationStatus.UNPUBLISHED, null));
        for (RestoreCase state : invalidStates) {
            assertThatThrownBy(() -> FishSpecies.restore(
                    7L, "test-fish", validContent(), state.status(), state.publishedAt(), CREATED, LATER))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThatThrownBy(() -> FishSpecies.restore(
                null, "test-fish", validContent(), PublicationStatus.DRAFT, null, CREATED, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FishSpecies.restore(
                7L, "test-fish", validContent(), null, null, CREATED, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FishSpecies.restore(
                7L, "test-fish", null, PublicationStatus.DRAFT, null, CREATED, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FishSpecies.restore(
                7L, "test-fish", validContent(), PublicationStatus.DRAFT, null, null, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FishSpecies.restore(
                7L, "test-fish", validContent(), PublicationStatus.DRAFT, null, CREATED, null))
                .isInstanceOf(IllegalArgumentException.class);

        FishSpecies draft = FishSpecies.createDraft("test-fish", validContent(), CREATED);
        FishSpecies published = draft.publish(LATER);
        assertThatThrownBy(() -> draft.edit(validContent(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> draft.publish(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> published.unpublish(null)).isInstanceOf(IllegalArgumentException.class);
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

    private static FishSpeciesContent contentWith(String field, String replacement) {
        FishSpeciesContent content = validContent();
        return new FishSpeciesContent(
                "commonNameZh".equals(field) ? replacement : content.commonNameZh(),
                "scientificName".equals(field) ? replacement : content.scientificName(),
                "familyNameZh".equals(field) ? replacement : content.familyNameZh(),
                "familyScientificName".equals(field) ? replacement : content.familyScientificName(),
                "genusNameZh".equals(field) ? replacement : content.genusNameZh(),
                "genusScientificName".equals(field) ? replacement : content.genusScientificName(),
                content.aliases(), content.habitats(),
                "appearance".equals(field) ? replacement : content.appearance(),
                "sizeDescription".equals(field) ? replacement : content.sizeDescription(),
                "habitatDescription".equals(field) ? replacement : content.habitatDescription(),
                "distribution".equals(field) ? replacement : content.distribution(),
                "description".equals(field) ? replacement : content.description(),
                content.image(), content.displayOrder());
    }

    private static FishSpeciesContent contentWithAliases(List<String> aliases) {
        FishSpeciesContent content = validContent();
        return new FishSpeciesContent(
                content.commonNameZh(), content.scientificName(), content.familyNameZh(),
                content.familyScientificName(), content.genusNameZh(), content.genusScientificName(), aliases,
                content.habitats(), content.appearance(), content.sizeDescription(), content.habitatDescription(),
                content.distribution(), content.description(), content.image(), content.displayOrder());
    }

    private static FishSpeciesContent contentWithHabitats(Set<HabitatType> habitats) {
        FishSpeciesContent content = validContent();
        return new FishSpeciesContent(
                content.commonNameZh(), content.scientificName(), content.familyNameZh(),
                content.familyScientificName(), content.genusNameZh(), content.genusScientificName(), content.aliases(),
                habitats, content.appearance(), content.sizeDescription(), content.habitatDescription(),
                content.distribution(), content.description(), content.image(), content.displayOrder());
    }

    private static FishSpeciesContent contentWithImage(ImageAttribution image) {
        FishSpeciesContent content = validContent();
        return new FishSpeciesContent(
                content.commonNameZh(), content.scientificName(), content.familyNameZh(),
                content.familyScientificName(), content.genusNameZh(), content.genusScientificName(), content.aliases(),
                content.habitats(), content.appearance(), content.sizeDescription(), content.habitatDescription(),
                content.distribution(), content.description(), image, content.displayOrder());
    }

    private static ImageAttribution imageWith(String field, String replacement) {
        ImageAttribution image = validContent().image();
        return new ImageAttribution(
                "path".equals(field) ? replacement : image.path(),
                "altText".equals(field) ? replacement : image.altText(),
                "sourceUrl".equals(field) ? replacement : image.sourceUrl(),
                "author".equals(field) ? replacement : image.author(),
                "licenseName".equals(field) ? replacement : image.licenseName(),
                "licenseUrl".equals(field) ? replacement : image.licenseUrl());
    }

    private static String localImagePath(int length) {
        return "/images/fish/" + "a".repeat(length - 17) + ".jpg";
    }

    private static String urlOfCodePointLength(int length) {
        return "https://example.com/" + FISH_EMOJI.repeat(length - 20);
    }

    private record StringField(String name, int limit) {}

    private record ImageField(String name, String atLimit, String overLimit) {}

    private record RestoreCase(PublicationStatus status, Instant publishedAt) {}
}
