package com.rental.model;

import java.time.LocalDateTime;

public class Review {
	private Long id;
	private Long vehicleId;
	private Long bookingId;
	private Long customerId;
	private Long renterId;
	private int rating;
	private String comment;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public Review() {
	}

	public Review(Long id, Long vehicleId, Long bookingId, Long customerId, Long renterId, int rating,
			String comment, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.vehicleId = vehicleId;
		this.bookingId = bookingId;
		this.customerId = customerId;
		this.renterId = renterId;
		this.rating = rating;
		this.comment = comment;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getVehicleId() {
		return vehicleId;
	}

	public void setVehicleId(Long vehicleId) {
		this.vehicleId = vehicleId;
	}

	public Long getBookingId() {
		return bookingId;
	}

	public void setBookingId(Long bookingId) {
		this.bookingId = bookingId;
	}

	public Long getCustomerId() {
		return customerId;
	}

	public void setCustomerId(Long customerId) {
		this.customerId = customerId;
	}

	public Long getRenterId() {
		return renterId;
	}

	public void setRenterId(Long renterId) {
		this.renterId = renterId;
	}

	public int getRating() {
		return rating;
	}

	public void setRating(int rating) {
		this.rating = rating;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}
}
