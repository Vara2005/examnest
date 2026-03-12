package com.examnest.examnest;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class DownloadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;   // Who downloaded

    @ManyToOne
    private Notes note;  // Which note

    private LocalDateTime downloadedAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public Notes getNote() {
		return note;
	}

	public void setNote(Notes note) {
		this.note = note;
	}

	public LocalDateTime getDownloadedAt() {
		return downloadedAt;
	}

	public void setDownloadedAt(LocalDateTime downloadedAt) {
		this.downloadedAt = downloadedAt;
	}

    // Getters & Setters
}