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
}
