package com.rental.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rental.model.Review;
import com.rental.service.ReviewService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

	private static final String USER_ID_SESSION_KEY = "AUTH_USER_ID";
	private static final String AUTH_ROLE_SESSION_KEY = "AUTH_ROLE";

	private final ReviewService reviewService;

	public ReviewController(ReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@PostMapping
	public ResponseEntity<?> createReview(@RequestBody ReviewRequest request, HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return unauthorized();
		}

		try {
			Review review = reviewService.createReview(userId, request.vehicleId(), request.bookingId(), request.rating(),
					request.comment());
			return ResponseEntity.status(HttpStatus.CREATED)
					.body(Map.of("message", "Review posted.", "review", toReviewView(review)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/vehicle/{vehicleId}")
	public ResponseEntity<?> getVehicleReviews(@PathVariable Long vehicleId) {
		List<Map<String, Object>> reviews = reviewService.getReviewsForVehicle(vehicleId).stream()
				.map(this::toReviewView)
				.toList();
		return ResponseEntity.ok(Map.of("reviews", reviews));
	}

	@GetMapping("/mine/customer")
	public ResponseEntity<?> getMyReviews(HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return unauthorized();
		}

		List<Map<String, Object>> reviews = reviewService.getReviewsByCustomer(userId).stream()
				.map(this::toReviewView)
				.toList();
		return ResponseEntity.ok(Map.of("reviews", reviews));
	}

	@GetMapping("/mine/renter")
	public ResponseEntity<?> getReviewsOnMyVehicles(HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return unauthorized();
		}

		List<Map<String, Object>> reviews = reviewService.getReviewsForRenter(userId).stream()
				.map(this::toReviewView)
				.toList();
		return ResponseEntity.ok(Map.of("reviews", reviews));
	}

	@PutMapping("/{reviewId}")
	public ResponseEntity<?> updateReview(@PathVariable Long reviewId, @RequestBody ReviewUpdateRequest request,
			HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return unauthorized();
		}

		try {
			Review review = reviewService.updateReview(userId, reviewId, request.rating(), request.comment());
			return ResponseEntity.ok(Map.of("message", "Review updated.", "review", toReviewView(review)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@DeleteMapping("/{reviewId}")
	public ResponseEntity<?> deleteReview(@PathVariable Long reviewId, HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return unauthorized();
		}

		try {
			reviewService.deleteReview(userId, reviewId);
			return ResponseEntity.ok(Map.of("message", "Review deleted."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	private Map<String, Object> toReviewView(Review review) {
		return Map.ofEntries(
				Map.entry("id", review.getId()),
				Map.entry("vehicleId", review.getVehicleId()),
				Map.entry("bookingId", review.getBookingId()),
				Map.entry("customerId", review.getCustomerId()),
				Map.entry("renterId", review.getRenterId()),
				Map.entry("rating", review.getRating()),
				Map.entry("comment", review.getComment() == null ? "" : review.getComment()),
				Map.entry("createdAt", review.getCreatedAt() == null ? "" : review.getCreatedAt().toString()),
				Map.entry("updatedAt", review.getUpdatedAt() == null ? "" : review.getUpdatedAt().toString()));
	}

	private Long currentUserId(HttpSession session) {
		Object raw = session.getAttribute(USER_ID_SESSION_KEY);
		if (raw instanceof Long id) {
			return id;
		}
		if (raw instanceof Integer intId) {
			return intId.longValue();
		}
		return null;
	}

	private String currentRole(HttpSession session) {
		Object raw = session.getAttribute(AUTH_ROLE_SESSION_KEY);
		if (raw instanceof String role) {
			return role;
		}
		return "";
	}

	private boolean isRenterRole(String role) {
		return "RENTER".equalsIgnoreCase(role) || "USER".equalsIgnoreCase(role);
	}

	private ResponseEntity<?> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
	}

	public record ReviewRequest(Long vehicleId, Long bookingId, int rating, String comment) {
	}

	public record ReviewUpdateRequest(int rating, String comment) {
	}
}
