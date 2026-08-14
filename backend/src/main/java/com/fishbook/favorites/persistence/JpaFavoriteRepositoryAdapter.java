package com.fishbook.favorites.persistence;

import com.fishbook.favorites.domain.FavoriteEntry;
import com.fishbook.favorites.domain.FavoritePage;
import com.fishbook.favorites.domain.FavoriteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaFavoriteRepositoryAdapter implements FavoriteRepository {
    private final SpringDataFavoriteJpaRepository repository;

    public JpaFavoriteRepositoryAdapter(SpringDataFavoriteJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void add(long userId, long fishId, Instant now) {
        repository.insertIfAbsent(userId, fishId, now);
    }

    @Override
    @Transactional
    public void remove(long userId, long fishId) {
        repository.deleteIfPresent(userId, fishId);
    }

    @Override
    public FavoritePage findByUserId(long userId, int page, int size) {
        Page<FavoriteJpaEntity> favoritePage = repository.findByUserId(
                userId,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id"))));
        List<FavoriteEntry> items = favoritePage.getContent().stream()
                .map(favorite -> new FavoriteEntry(
                        favorite.getFishSpeciesId(), favorite.getCreatedAt()))
                .toList();
        return new FavoritePage(
                items,
                page,
                size,
                favoritePage.getTotalElements(),
                favoritePage.getTotalPages());
    }

    @Override
    public Set<Long> findFavoritedFishIds(long userId, Set<Long> fishIds) {
        if (fishIds.isEmpty()) {
            return Set.of();
        }
        return repository.findAllByUserIdAndFishSpeciesIdIn(userId, fishIds).stream()
                .map(FavoriteJpaEntity::getFishSpeciesId)
                .collect(Collectors.toSet());
    }
}
