package com.fishbook.administration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fishbook.catalog.domain.FishManagementRepository;
import com.fishbook.catalog.domain.FishManagementSearchCriteria;
import com.fishbook.catalog.domain.FishNotFoundException;
import com.fishbook.catalog.domain.FishPage;
import com.fishbook.catalog.domain.FishSpecies;
import com.fishbook.catalog.domain.HabitatType;
import com.fishbook.catalog.domain.InvalidFishSpeciesException;
import com.fishbook.catalog.domain.InvalidPublicationTransitionException;
import com.fishbook.catalog.domain.PublicationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultFishAdministrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private MemoryFishManagementRepository repository;
    private FishAdministrationService service;

    @BeforeEach
    void setUp() {
        repository = new MemoryFishManagementRepository();
        service = new DefaultFishAdministrationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void normalizesManagementQueryAndRejectsClientPageSize() {
        assertThat(AdminFishQuery.from("  鲤  ", "draft", "2", null))
                .isEqualTo(new AdminFishQuery("鲤", PublicationStatus.DRAFT, 2));
        assertThatThrownBy(() -> AdminFishQuery.from(null, null, "0", "20"))
                .isInstanceOf(InvalidAdminFishQueryException.class);
    }

    @Test
    void searchesManagedFishWithFixedPageSizeAndStatusFilter() {
        repository.save(FishSpecies.createDraft("draft-fish", content().toDomain(), NOW));
        repository.save(FishSpecies.createDraft("published-fish", publishedContent().toDomain(), NOW).publish(NOW));

        AdminFishPageView page = service.search(AdminFishQuery.from("  draft ", "DRAFT", "0", null));

        assertThat(repository.lastCriteria).isEqualTo(
                new FishManagementSearchCriteria("draft", PublicationStatus.DRAFT, 0, 20));
        assertThat(page.items()).extracting(AdminFishSummaryView::slug).containsExactly("draft-fish");
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void createsDraftAndKeepsSlugImmutableDuringEdit() {
        AdminFishDetailView created = service.create(new CreateFishCommand("new-fish", content()));
        AdminFishDetailView edited = service.update(created.id(), editedContent());

        assertThat(created.slug()).isEqualTo("new-fish");
        assertThat(created.status()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(created.publishedAt()).isNull();
        assertThat(edited.slug()).isEqualTo("new-fish");
        assertThat(edited.commonNameZh()).isEqualTo("新名称");
        assertThat(edited.status()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(edited.createdAt()).isEqualTo(created.createdAt());
        assertThat(edited.publishedAt()).isNull();
    }

    @Test
    void publishesUnpublishesAndRepublishesThroughExplicitActions() {
        long id = repository.save(FishSpecies.createDraft("new-fish", content().toDomain(), NOW)).id();

        assertThat(service.publish(id).status()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(service.unpublish(id).status()).isEqualTo(PublicationStatus.UNPUBLISHED);
        assertThat(service.publish(id).status()).isEqualTo(PublicationStatus.PUBLISHED);
    }

    @Test
    void rejectsMissingFishAndInvalidPublicationTransitions() {
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(FishNotFoundException.class);
        long id = repository.save(FishSpecies.createDraft("draft-fish", content().toDomain(), NOW)).id();

        assertThatThrownBy(() -> service.unpublish(id))
                .isInstanceOf(InvalidPublicationTransitionException.class);
    }

    @Test
    void propagatesDomainContentValidation() {
        FishContentCommand invalidContent = new FishContentCommand(
                "名称", "Scientific name", "科", "Family", "属", "Genus",
                List.of("别名", " 别名 "), EnumSet.of(HabitatType.LAKE),
                "外观", "体型", "栖息地", "分布", "描述",
                "/images/fish/new-fish.jpg", "图片", "https://example.com/source", "作者",
                "CC BY 4.0", "https://example.com/license", 1);

        assertThatThrownBy(() -> service.create(new CreateFishCommand("new-fish", invalidContent)))
                .isInstanceOf(InvalidFishSpeciesException.class);
    }

    private static FishContentCommand content() {
        return new FishContentCommand(
                "新鱼", "New fish", "新鱼科", "Newidae", "新鱼属", "Newus",
                List.of("新别名"), EnumSet.of(HabitatType.LAKE),
                "外观描述", "体型描述", "栖息环境描述", "分布描述", "综合介绍",
                "/images/fish/new-fish.jpg", "新鱼图片", "https://example.com/source", "测试作者",
                "CC BY 4.0", "https://example.com/license", 1);
    }

    private static FishContentCommand publishedContent() {
        return new FishContentCommand(
                "已发布鱼", "Published fish", "已发布科", "Publishedidae", "已发布属", "Publishedus",
                List.of("发布别名"), EnumSet.of(HabitatType.RIVER),
                "外观描述", "体型描述", "栖息环境描述", "分布描述", "综合介绍",
                "/images/fish/published-fish.jpg", "已发布鱼图片", "https://example.com/source", "测试作者",
                "CC BY 4.0", "https://example.com/license", 2);
    }

    private static FishContentCommand editedContent() {
        return new FishContentCommand(
                "新名称", "Edited fish", "编辑科", "Editedidae", "编辑属", "Editedus",
                List.of("编辑别名"), EnumSet.of(HabitatType.POND),
                "编辑外观", "编辑体型", "编辑栖息地", "编辑分布", "编辑介绍",
                "/images/fish/edited-fish.jpg", "编辑图片", "https://example.com/edited", "编辑作者",
                "CC BY 4.0", "https://example.com/license", 3);
    }

    private static final class MemoryFishManagementRepository implements FishManagementRepository {

        private final Map<Long, FishSpecies> fishById = new LinkedHashMap<>();
        private long nextId = 1L;
        private FishManagementSearchCriteria lastCriteria;

        @Override
        public FishPage searchManaged(FishManagementSearchCriteria criteria) {
            lastCriteria = criteria;
            List<FishSpecies> matches = fishById.values().stream()
                    .filter(fish -> criteria.status() == null || fish.status() == criteria.status())
                    .filter(fish -> criteria.query() == null || fish.slug().contains(criteria.query()))
                    .toList();
            return new FishPage(matches, criteria.page(), criteria.size(), matches.size(), 1);
        }

        @Override
        public Optional<FishSpecies> findManagedById(long id) {
            return Optional.ofNullable(fishById.get(id));
        }

        @Override
        public FishSpecies save(FishSpecies fish) {
            FishSpecies saved = fish;
            if (fish.id() == null) {
                saved = FishSpecies.restore(
                        nextId++, fish.slug(), fish.content(), fish.status(), fish.publishedAt(),
                        fish.createdAt(), fish.updatedAt());
            }
            fishById.put(saved.id(), saved);
            return saved;
        }
    }
}
