package com.examnest.examnest;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

	User findByUsername(String username);
	Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
	List<User> findByRole(String role);
	@Query("SELECT DATE(u.createdAt), COUNT(u) FROM User u GROUP BY DATE(u.createdAt)")
	List<Object[]> countUsersByDate();
	@Query("""
			SELECT FUNCTION('DATE_FORMAT', u.createdAt, '%Y-%m') AS month,
			       COUNT(u)
			FROM User u
			GROUP BY FUNCTION('DATE_FORMAT', u.createdAt, '%Y-%m')
			ORDER BY month
			""")
			List<Object[]> getMonthlyUserRegistrations();
			User findByEmail(String email);
			User findByVerificationToken(String token);
			User findByResetToken(String resetToken);
}