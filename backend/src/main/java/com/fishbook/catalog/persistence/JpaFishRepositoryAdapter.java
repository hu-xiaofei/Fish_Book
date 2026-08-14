package com.fishbook.catalog.persistence;

import com.fishbook.catalog.domain.FishPage;
import com.fishbook.catalog.domain.FishRepository;
import com.fishbook.catalog.domain.FishSearchCriteria;
import com.fishbook.catalog.domain.FishSpecies;
import com.fishbook.catalog.domain.HabitatType;
import com.fishbook.catalog.domain.ImageAttribution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class JpaFishRepositoryAdapter implements FishRepository {

    private final SpringDataFishSpeciesJpaRepository repository;

    public JpaFishRepositoryAdapter(SpringDataFishSpeciesJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public FishPage search(FishSearchCriteria criteria) {
        PageRequest pageRequest = PageRequest.of(
                criteria.page(),
                criteria.size(),
                Sort.by("displayOrder").ascending().and(Sort.by("id")));
        Page<Long> idPage = repository.searchIds(
                likePattern(criteria.query()), criteria.family(), criteria.habitat(), pageRequest);
        Map<Long, FishSpeciesJpaEntity> entitiesById = repository
                .findAllWithDetailsByIdIn(idPage.getContent())
                .stream()
                .collect(Collectors.toMap(FishSpeciesJpaEntity::getId, Function.identity()));
        List<FishSpecies> items = idPage.getContent().stream()
                .map(entitiesById::get)
                .map(this::toDomain)
                .toList();
        return new FishPage(items, criteria.page(), criteria.size(),
                idPage.getTotalElements(), idPage.getTotalPages());
    }

    @Override
    public Optional<FishSpecies> findBySlug(String slug) {
        return repository.findBySlug(slug).map(this::toDomain);
    }

    @Override
    public List<FishSpecies> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, FishSpeciesJpaEntity> entitiesById = repository.findAllWithDetailsByIdIn(ids).stream()
                .collect(Collectors.toMap(FishSpeciesJpaEntity::getId, Function.identity()));
        return ids.stream()
                .map(entitiesById::get)
                .filter(Objects::nonNull)
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<FishSpecies> findAllBySlugs(List<String> slugs) {
        if (slugs.isEmpty()) {
            return List.of();
        }
        Map<String, FishSpeciesJpaEntity> entitiesBySlug = repository.findAllWithDetailsBySlugIn(slugs).stream()
                .collect(Collectors.toMap(FishSpeciesJpaEntity::getSlug, Function.identity()));
        return slugs.stream()
                .map(entitiesBySlug::get)
                .filter(Objects::nonNull)
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<String> findAvailableFamilies() {
        return repository.findAvailableFamilies().stream().sorted().toList();
    }

    private String likePattern(String query) {
        if (query == null) {
            return null;
        }
        return "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private FishSpecies toDomain(FishSpeciesJpaEntity entity) {
        List<String> aliases = entity.getAliases().stream()
                .map(FishAliasJpaEntity::getAlias)
                .sorted()
                .toList();
        LinkedHashSet<HabitatType> habitats = entity.getHabitats().stream()
                .map(FishHabitatJpaEntity::getId)
                .map(FishHabitatId::getHabitatCode)
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new FishSpecies(
                entity.getId(),
                entity.getSlug(),
                entity.getCommonNameZh(),
                entity.getScientificName(),
                entity.getFamilyNameZh(),
                entity.getFamilyScientificName(),
                entity.getGenusNameZh(),
                entity.getGenusScientificName(),
                aliases,
                habitats,
                entity.getAppearance(),
                entity.getSizeDescription(),
                entity.getHabitatDescription(),
                entity.getDistribution(),
                entity.getDescription(),
                new ImageAttribution(
                        entity.getImagePath(),
                        entity.getImageAltText(),
                        entity.getImageSourceUrl(),
                        entity.getImageAuthor(),
                        entity.getImageLicenseName(),
                        entity.getImageLicenseUrl()),
                entity.getDisplayOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
