package com.rental.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.rental.model.Booking;
import com.rental.model.Review;
import com.rental.repository.ReviewRepository;

@Service
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final BookingService bookingService;

	public ReviewService(ReviewRepository reviewRepository, BookingService bookingService) {
		this.reviewRepository = reviewRepository;
		this.bookingService = bookingService;
	}

	public Review createReview(Long customerId, Long vehicleId, Long bookingId, int rating, String comment) {
		Booking booking = validateReviewEligibility(customerId, vehicleId, bookingId);
		if (hasExistingReviewForBooking(bookingId)) {
			throw new IllegalArgumentException("You already posted a review for this booking.");
		}

		validateReviewContent(rating, comment);

		Review review = new Review();
		review.setVehicleId(vehicleId);
		review.setBookingId(bookingId);
		review.setCustomerId(customerId);
		review.setRenterId(booking.getRenterId());
		review.setRating(rating);
		review.setComment(clean(comment));
		review.setCreatedAt(LocalDateTime.now());
		review.setUpdatedAt(LocalDateTime.now());

		return reviewRepository.save(review);
	}

	public List<Review> getReviewsForVehicle(Long vehicleId) {
		return reviewRepository.findAll().stream()
				.filter(review -> review.getVehicleId().equals(vehicleId))
				.toList();
	}

	public List<Review> getAllReviews(String query) {
		List<Review> reviews = reviewRepository.findAll();
		if (query == null || query.isBlank()) {
			return reviews;
		}

		String normalized = clean(query).toLowerCase(Locale.ROOT);
		return reviews.stream()
				.filter(review -> contains(String.valueOf(review.getId()), normalized)
						|| contains(String.valueOf(review.getVehicleId()), normalized)
						|| contains(String.valueOf(review.getCustomerId()), normalized)
						|| contains(String.valueOf(review.getRenterId()), normalized)
						|| contains(String.valueOf(review.getRating()), normalized)
						|| contains(review.getComment(), normalized))
				.toList();
	}

	public List<Review> getReviewsByCustomer(Long customerId) {
		return reviewRepository.findAll().stream()
				.filter(review -> review.getCustomerId().equals(customerId))
				.toList();
	}

	public List<Review> getReviewsForRenter(Long renterId) {
		return reviewRepository.findAll().stream()
				.filter(review -> review.getRenterId().equals(renterId))
				.toList();
	}

	public Review updateReview(Long customerId, Long reviewId, int rating, String comment) {
		Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new IllegalArgumentException("Review not found."));
		if (!review.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can update only your own reviews.");
		}

		validateReviewContent(rating, comment);
		review.setRating(rating);
		review.setComment(clean(comment));
		review.setUpdatedAt(LocalDateTime.now());
		return reviewRepository.update(review);
	}

	public void deleteReview(Long customerId, Long reviewId) {
		Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new IllegalArgumentException("Review not found."));
		if (!review.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can delete only your own reviews.");
		}
		reviewRepository.deleteById(reviewId);
	}

	public void deleteReviewByAdmin(Long reviewId) {
		if (!reviewRepository.deleteById(reviewId)) {
			throw new IllegalArgumentException("Review not found.");
		}
	}

	private Booking validateReviewEligibility(Long customerId, Long vehicleId, Long bookingId) {
		Booking booking = bookingService.findById(bookingId)
				.orElseThrow(() -> new IllegalArgumentException("Booking not found."));
		if (!booking.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can review only bookings made by you.");
		}
		if (!booking.getVehicleId().equals(vehicleId)) {
			throw new IllegalArgumentException("Booking does not belong to this vehicle.");
		}
		if (!booking.isPaid()) {
			throw new IllegalArgumentException("Review is allowed only after payment.");
		}
		return booking;
	}

	private boolean hasExistingReviewForBooking(Long bookingId) {
		return reviewRepository.findAll().stream().anyMatch(review -> review.getBookingId().equals(bookingId));
	}

	private void validateReviewContent(int rating, String comment) {
		String cleaned = clean(comment);
		if (rating < 1 || rating > 5) {
			throw new IllegalArgumentException("Rating must be between 1 and 5.");
		}
		if (cleaned.isBlank() || cleaned.length() < 5 || cleaned.length() > 500) {
			throw new IllegalArgumentException("Review comment must be between 5 and 500 characters.");
		}
	}

	private String clean(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ");
	}

	private boolean contains(String value, String query) {
		if (value == null) {
			return false;
		}
		return value.toLowerCase(Locale.ROOT).contains(query);
	}
}
