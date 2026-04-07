package com.rental.controller;

import java.time.LocalDate;
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

import com.rental.model.Booking;
import com.rental.model.Vehicle;
import com.rental.service.BookingService;
import com.rental.service.VehicleService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

	private static final String USER_ID_SESSION_KEY = "AUTH_USER_ID";
	private static final String AUTH_ROLE_SESSION_KEY = "AUTH_ROLE";

	private final BookingService bookingService;
	private final VehicleService vehicleService;

	public BookingController(BookingService bookingService, VehicleService vehicleService) {
		this.bookingService = bookingService;
		this.vehicleService = vehicleService;
	}

	@PostMapping
	public ResponseEntity<?> createBooking(@RequestBody BookingRequest request, HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("message", "To place a booking, you must be a registered user and login first."));
		}

		try {
			Booking booking = bookingService.createBooking(userId, request.vehicleId(), request.startDate(),
					request.endDate());
			return ResponseEntity.status(HttpStatus.CREATED)
					.body(Map.of("message", "Booking request submitted.", "booking", toBookingView(booking)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/mine/customer")
	public ResponseEntity<?> getMyBookings(HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
		}

		List<Map<String, Object>> bookings = bookingService.getBookingsByCustomer(userId).stream()
				.map(this::toBookingView)
				.toList();
		return ResponseEntity.ok(Map.of("bookings", bookings));
	}

	@GetMapping("/mine/renter")
	public ResponseEntity<?> getRenterBookingRequests(HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
		}

		List<Map<String, Object>> bookings = bookingService.getBookingsForRenter(userId).stream()
				.map(this::toBookingView)
				.toList();
		return ResponseEntity.ok(Map.of("bookings", bookings));
	}

	@PutMapping("/{bookingId}")
	public ResponseEntity<?> updateBooking(@PathVariable Long bookingId, @RequestBody BookingUpdateRequest request,
			HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
		}

		try {
			Booking booking = bookingService.updateBookingByCustomer(userId, bookingId, request.startDate(),
					request.endDate());
			return ResponseEntity.ok(Map.of("message", "Booking updated.", "booking", toBookingView(booking)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@DeleteMapping("/{bookingId}")
	public ResponseEntity<?> deleteBooking(@PathVariable Long bookingId, HttpSession session) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
		}

		try {
			bookingService.deleteBookingByCustomer(userId, bookingId);
			return ResponseEntity.ok(Map.of("message", "Booking deleted."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@PostMapping("/{bookingId}/confirm")
	public ResponseEntity<?> confirmBooking(@PathVariable Long bookingId, HttpSession session) {
		return updateStatusByRenter(session, bookingId, "confirm");
	}

	@PostMapping("/{bookingId}/reject")
	public ResponseEntity<?> rejectBooking(@PathVariable Long bookingId, HttpSession session) {
		return updateStatusByRenter(session, bookingId, "reject");
	}

	@PostMapping("/{bookingId}/rented")
	public ResponseEntity<?> markRented(@PathVariable Long bookingId, HttpSession session) {
		return updateStatusByRenter(session, bookingId, "rented");
	}

	private ResponseEntity<?> updateStatusByRenter(HttpSession session, Long bookingId, String action) {
		Long userId = currentUserId(session);
		if (userId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
		}

		try {
			Booking booking;
			switch (action) {
				case "confirm" -> booking = bookingService.confirmBookingByRenter(userId, bookingId);
				case "reject" -> booking = bookingService.rejectBookingByRenter(userId, bookingId);
				case "rented" -> booking = bookingService.markBookingRentedByRenter(userId, bookingId);
				default -> {
					return ResponseEntity.badRequest().body(Map.of("message", "Unsupported action."));
				}
			}

			return ResponseEntity.ok(Map.of("message", "Booking status updated.", "booking", toBookingView(booking)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	private Map<String, Object> toBookingView(Booking booking) {
		Vehicle vehicle = vehicleService.findVehicle(booking.getVehicleId()).orElse(null);
		return Map.ofEntries(
				Map.entry("id", booking.getId()),
				Map.entry("vehicleId", booking.getVehicleId()),
				Map.entry("vehicleBrand", vehicle == null ? "" : vehicle.getBrand()),
				Map.entry("vehicleModel", vehicle == null ? "" : vehicle.getModel()),
				Map.entry("vehicleImageUrl", vehicle == null || vehicle.getImageUrl() == null ? "" : vehicle.getImageUrl()),
				Map.entry("renterId", booking.getRenterId()),
				Map.entry("customerId", booking.getCustomerId()),
				Map.entry("startDate", booking.getStartDate().toString()),
				Map.entry("endDate", booking.getEndDate().toString()),
				Map.entry("totalDays", booking.getTotalDays()),
				Map.entry("totalAmount", booking.getTotalAmount()),
				Map.entry("status", booking.getStatus()),
				Map.entry("paid", booking.isPaid()),
				Map.entry("createdAt", booking.getCreatedAt() == null ? "" : booking.getCreatedAt().toString()),
				Map.entry("updatedAt", booking.getUpdatedAt() == null ? "" : booking.getUpdatedAt().toString()));
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

	public record BookingRequest(Long vehicleId, LocalDate startDate, LocalDate endDate) {
	}

	public record BookingUpdateRequest(LocalDate startDate, LocalDate endDate) {
	}
}
