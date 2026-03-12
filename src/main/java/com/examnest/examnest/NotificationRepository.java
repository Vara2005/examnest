package com.examnest.examnest;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop5ByOrderByCreatedAtDesc();

    long countByReadStatusFalse();
    
    List<Notification> findByIsReadFalseOrderByCreatedAtDesc();

    long countByIsReadFalse();
    
}