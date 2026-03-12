package com.examnest.examnest;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogRepository
        extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByTimestampBetween(
            LocalDateTime start,
            LocalDateTime end);

    List<ActivityLog> findTop10ByOrderByTimestampDesc();
    List<ActivityLog> findByUsernameOrderByTimestampDesc(String username);
}