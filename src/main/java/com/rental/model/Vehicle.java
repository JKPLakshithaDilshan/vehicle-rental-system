package com.rental.model;

import java.time.LocalDateTime;

public class Vehicle {
	private Long id;
	private Long renterId;
	private String brand;
	private String model;
	private int year;
	private double pricePerDay;
	private String description;
	private String imageUrl;
	private String status;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	public Vehicle() {
	}

	public Vehicle(Long id, Long renterId, String brand, String model, int year, double pricePerDay, String description,
			String imageUrl, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.renterId = renterId;
		this.brand = brand;
		this.model = model;
		this.year = year;
		this.pricePerDay = pricePerDay;
		this.description = description;
		this.imageUrl = imageUrl;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getRenterId() {
		return renterId;
	}

	public void setRenterId(Long renterId) {
		this.renterId = renterId;
	}

	public String getBrand() {
		return brand;
	}

	public void setBrand(String brand) {
		this.brand = brand;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
	}

	public double getPricePerDay() {
		return pricePerDay;
	}

	public void setPricePerDay(double pricePerDay) {
		this.pricePerDay = pricePerDay;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public void setImageUrl(String imageUrl) {
		this.imageUrl = imageUrl;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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
