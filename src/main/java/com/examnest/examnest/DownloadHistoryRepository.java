package com.examnest.examnest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface DownloadHistoryRepository extends JpaRepository<DownloadHistory, Long> {

    @Query("""
        SELECT d.user.username, COUNT(d)
        FROM DownloadHistory d
        GROUP BY d.user.username
        ORDER BY COUNT(d) DESC
    """)
    List<Object[]> getTopDownloaders();
}