package com.fishbook.administration.photos.persistence;

import com.fishbook.administration.photos.application.AdminPhotoPageView;
import com.fishbook.administration.photos.application.AdminPhotoQuery;
import com.fishbook.administration.photos.application.AdminPhotoQueryRepository;
import com.fishbook.administration.photos.application.AdminPhotoSummaryView;
import java.util.ArrayList;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminPhotoQueryRepository implements AdminPhotoQueryRepository {
    private static final String JOIN = " FROM catch_records c JOIN users u ON u.id=c.user_id JOIN fish_species f ON f.id=c.fish_species_id ";
    private static final String SELECT = "SELECT c.id,c.user_id,u.nickname,f.common_name_zh,c.caught_on,(c.photo_object_key IS NOT NULL) AS has_photo,c.version,c.updated_at" + JOIN;
    private static final RowMapper<AdminPhotoSummaryView> MAPPER = (rs, row) -> new AdminPhotoSummaryView(
            rs.getLong("id"), rs.getLong("user_id"), rs.getString("nickname"), rs.getString("common_name_zh"),
            rs.getDate("caught_on").toLocalDate(), rs.getBoolean("has_photo"), Long.toString(rs.getLong("version")),
            rs.getTimestamp("updated_at").toInstant());
    private final JdbcTemplate jdbc;
    public JdbcAdminPhotoQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public AdminPhotoPageView search(AdminPhotoQuery query) {
        String where = " WHERE c.photo_object_key IS NOT NULL";
        var params = new ArrayList<Object>();
        if (query.userId() != null) { where += " AND c.user_id=?"; params.add(query.userId()); }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + JOIN + where, Long.class, params.toArray());
        params.add(query.size()); params.add((long) query.page() * query.size());
        var items = jdbc.query(SELECT + where + " ORDER BY c.updated_at DESC,c.id DESC LIMIT ? OFFSET ?", MAPPER, params.toArray());
        return new AdminPhotoPageView(items, query.page(), query.size(), total, (int) Math.min(Integer.MAX_VALUE, (total + query.size() - 1) / query.size()));
    }
    @Override public Optional<AdminPhotoSummaryView> findByRecordId(long recordId) {
        return jdbc.query(SELECT + " WHERE c.id=?", MAPPER, recordId).stream().findFirst();
    }
}
