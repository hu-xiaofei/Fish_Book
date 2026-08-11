package com.fishbook.catalog.persistence;

import com.fishbook.catalog.domain.HabitatType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SpringDataFishSpeciesJpaRepository extends JpaRepository<FishSpeciesJpaEntity, Long> {

    @Query(value = """
            select f.id from FishSpeciesJpaEntity f
            where (:pattern is null
                or lower(f.commonNameZh) like lower(:pattern) escape '\\'
                or lower(f.scientificName) like lower(:pattern) escape '\\'
                or exists (
                    select a.id from FishAliasJpaEntity a
                    where a.fishSpecies.id = f.id
                      and lower(a.alias) like lower(:pattern) escape '\\'
                ))
              and (:family is null or f.familyNameZh = :family)
              and (:habitat is null or exists (
                select h.id from FishHabitatJpaEntity h
                where h.fishSpecies.id = f.id and h.id.habitatCode = :habitat
              ))
            """,
            countQuery = """
            select count(f.id) from FishSpeciesJpaEntity f
            where (:pattern is null
                or lower(f.commonNameZh) like lower(:pattern) escape '\\'
                or lower(f.scientificName) like lower(:pattern) escape '\\'
                or exists (
                    select a.id from FishAliasJpaEntity a
                    where a.fishSpecies.id = f.id
                      and lower(a.alias) like lower(:pattern) escape '\\'
                ))
              and (:family is null or f.familyNameZh = :family)
              and (:habitat is null or exists (
                select h.id from FishHabitatJpaEntity h
                where h.fishSpecies.id = f.id and h.id.habitatCode = :habitat
              ))
            """)
    Page<Long> searchIds(
            @Param("pattern") String pattern,
            @Param("family") String family,
            @Param("habitat") HabitatType habitat,
            Pageable pageable);

    @EntityGraph(attributePaths = {"aliases", "habitats"})
    @Query("select distinct f from FishSpeciesJpaEntity f where f.id in :ids")
    List<FishSpeciesJpaEntity> findAllWithDetailsByIdIn(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = {"aliases", "habitats"})
    Optional<FishSpeciesJpaEntity> findBySlug(String slug);

    @Query("select distinct f.familyNameZh from FishSpeciesJpaEntity f")
    List<String> findAvailableFamilies();
}
