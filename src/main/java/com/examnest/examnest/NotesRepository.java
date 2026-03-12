package com.examnest.examnest;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotesRepository extends JpaRepository<Notes, Long> {

    // ================= BASIC =================
    List<Notes> findByUser(User user);

    List<Notes> findByUserAndTitleContainingIgnoreCase(User user, String title);

    Page<Notes> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    List<Notes> findByYearAndBranch(String year, String branch);

    List<Notes> findByYearAndBranchAndTitleContainingIgnoreCase(
            String year, String branch, String title);

    boolean existsByTitle(String title);

    boolean existsByFileHash(String fileHash);

    // ================= DOWNLOAD STATS =================
    @Query("SELECT n.title, n.downloadCount FROM Notes n ORDER BY n.downloadCount DESC")
    List<Object[]> getNotesDownloadStats();

    @Query("SELECT SUM(n.fileSize) FROM Notes n")
    Long getTotalStorageUsed();

    @Query("""
           SELECT FUNCTION('MONTH', n.uploadedAt), COUNT(n)
           FROM Notes n
           WHERE n.user.id = :userId
           GROUP BY FUNCTION('MONTH', n.uploadedAt)
           """)
    List<Object[]> getUserMonthlyUploads(@Param("userId") Long userId);

    @Query("""
           SELECT FUNCTION('DATE_FORMAT', n.uploadedAt, '%Y-%m'),
                  SUM(n.downloadCount)
           FROM Notes n
           GROUP BY FUNCTION('DATE_FORMAT', n.uploadedAt, '%Y-%m')
           ORDER BY 1
           """)
    List<Object[]> getMonthlyDownloads();

    // ================= TOP USERS =================
    @Query("""
           SELECT n.user.username, SUM(n.downloadCount)
           FROM Notes n
           GROUP BY n.user.username
           ORDER BY SUM(n.downloadCount) DESC
           """)
    Page<Object[]> getTopUsers(Pageable pageable);
}