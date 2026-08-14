package com.fishbook.favorites.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface SpringDataFavoriteJpaRepository extends JpaRepository<FavoriteJpaEntity, Long> {
    @Modifying
    @Query(value = "INSERT IGNORE INTO favorites(user_id, fish_species_id, created_at) "
            + "VALUES (:userId, :fishId, :createdAt)", nativeQuery = true)
    int insertIfAbsent(long userId, long fishId, Instant createdAt);

    void deleteByUserIdAndFishSpeciesId(long userId, long fishSpeciesId);

    Page<FavoriteJpaEntity> findByUserId(long userId, Pageable pageable);

    List<FavoriteJpaEntity> findAllByUserIdAndFishSpeciesIdIn(long userId, Set<Long> fishSpeciesIds);
}
