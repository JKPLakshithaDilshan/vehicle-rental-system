package com.rental.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rental.model.Admin;
import com.rental.model.Review;
import com.rental.model.User;
import com.rental.model.Vehicle;
import com.rental.service.AdminService;
import com.rental.service.ReviewService;
import com.rental.service.UserService;
import com.rental.service.VehicleService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private static final String AUTH_ROLE_SESSION_KEY = "AUTH_ROLE";
	private static final String AUTH_ADMIN_ID_SESSION_KEY = "AUTH_ADMIN_ID";

	private final AdminService adminService;
	private final UserService userService;
	private final VehicleService vehicleService;
	private final ReviewService reviewService;

	public AdminController(AdminService adminService, UserService userService, VehicleService vehicleService,
			ReviewService reviewService) {
		this.adminService = adminService;
		this.userService = userService;
		this.vehicleService = vehicleService;
		this.reviewService = reviewService;
	}

	@PostMapping("/admins")
	public ResponseEntity<?> createAdmin(@RequestBody AdminRequest request, HttpSession session) {
		if (!isAdmin(session) && adminService.countAdmins() > 0) {
			return forbidden();
		}

		try {
			Admin admin = adminService.createAdmin(request.name(), request.email(), request.password(), request.role());
			return ResponseEntity.status(HttpStatus.CREATED)
					.body(Map.of("message", "Admin created successfully.", "admin", toAdminView(admin)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/me")
	public ResponseEntity<?> getMyAdminAccount(HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		String adminId = currentAdminId(session);
		if (adminId == null || adminId.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
		}

		Admin admin = adminService.searchByAdminId(adminId).orElse(null);
		if (admin == null) {
			session.invalidate();
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Session expired."));
		}

		return ResponseEntity.ok(Map.of("admin", toAdminView(admin)));
	}

	@PutMapping("/me")
	public ResponseEntity<?> updateMyAdminAccount(@RequestBody AdminSelfUpdateRequest request, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		String adminId = currentAdminId(session);
		if (adminId == null || adminId.isBlank()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
		}

		try {
			Admin current = adminService.searchByAdminId(adminId)
					.orElseThrow(() -> new IllegalArgumentException("Admin not found."));
			Admin updated = adminService.updateAdmin(adminId, request.name(), request.email(), request.password(),
					current.getRole());
			return ResponseEntity.ok(Map.of("message", "Admin account updated successfully.", "admin", toAdminView(updated)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/admins")
	public ResponseEntity<?> viewAllAdmins(HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		List<Map<String, Object>> admins = adminService.getAllAdmins().stream().map(this::toAdminView).toList();
		return ResponseEntity.ok(Map.of("admins", admins));
	}

	@GetMapping("/admins/{adminId}")
	public ResponseEntity<?> searchAdminById(@PathVariable String adminId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		Admin admin = adminService.searchByAdminId(adminId).orElse(null);
		if (admin == null) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Admin not found."));
		}

		return ResponseEntity.ok(Map.of("admin", toAdminView(admin)));
	}

	@PutMapping("/admins/{adminId}")
	public ResponseEntity<?> updateAdmin(@PathVariable String adminId, @RequestBody AdminRequest request,
			HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		try {
			Admin updated = adminService.updateAdmin(adminId, request.name(), request.email(), request.password(),
					request.role());
			return ResponseEntity.ok(Map.of("message", "Admin updated successfully.", "admin", toAdminView(updated)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@DeleteMapping("/admins/{adminId}")
	public ResponseEntity<?> deleteAdmin(@PathVariable String adminId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		try {
			adminService.deleteAdmin(adminId);
			return ResponseEntity.ok(Map.of("message", "Admin deleted successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/users")
	public ResponseEntity<?> viewAndSearchUsers(@RequestParam(required = false) String query, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		List<Map<String, Object>> users = userService.searchUsers(query).stream()
				.map(this::toUserView)
				.collect(Collectors.toList());

		return ResponseEntity.ok(Map.of("users", users));
	}

	@DeleteMapping("/users/{userId}")
	public ResponseEntity<?> deleteUser(@PathVariable Long userId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		try {
			userService.deleteUserByAdmin(userId);
			return ResponseEntity.ok(Map.of("message", "User deleted successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/vehicles")
	public ResponseEntity<?> viewAndSearchVehicles(@RequestParam(required = false) String query, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		List<Map<String, Object>> vehicles = vehicleService.getAllVehicles(query).stream()
				.map(this::toVehicleView)
				.toList();
		return ResponseEntity.ok(Map.of("vehicles", vehicles));
	}

	@DeleteMapping("/vehicles/{vehicleId}")
	public ResponseEntity<?> deleteVehicleByAdmin(@PathVariable Long vehicleId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		try {
			vehicleService.deleteVehicleByAdmin(vehicleId);
			return ResponseEntity.ok(Map.of("message", "Vehicle removed successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/reviews")
	public ResponseEntity<?> viewAndSearchReviews(@RequestParam(required = false) String query, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		List<Map<String, Object>> reviews = reviewService.getAllReviews(query).stream()
				.map(this::toReviewView)
				.toList();
		return ResponseEntity.ok(Map.of("reviews", reviews));
	}

	@DeleteMapping("/reviews/{reviewId}")
	public ResponseEntity<?> deleteReviewByAdmin(@PathVariable Long reviewId, HttpSession session) {
		if (!isAdmin(session)) {
			return forbidden();
		}

		try {
			reviewService.deleteReviewByAdmin(reviewId);
			return ResponseEntity.ok(Map.of("message", "Review removed successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	private boolean isAdmin(HttpSession session) {
		Object role = session.getAttribute(AUTH_ROLE_SESSION_KEY);
		if (!(role instanceof String roleText)) {
			return false;
		}
		return "ADMIN".equalsIgnoreCase(roleText) || "SUPERADMIN".equalsIgnoreCase(roleText);
	}

	private ResponseEntity<Map<String, String>> forbidden() {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required."));
	}

	private String currentAdminId(HttpSession session) {
		Object raw = session.getAttribute(AUTH_ADMIN_ID_SESSION_KEY);
		if (raw instanceof String adminId) {
			return adminId;
		}
		return null;
	}

	private Map<String, Object> toAdminView(Admin admin) {
		return Map.of(
				"adminId", admin.getAdminId(),
				"name", admin.getName(),
				"email", admin.getEmail(),
				"role", admin.getRole());
	}

	private Map<String, Object> toUserView(User user) {
		String effectiveRole = resolveUserType(user);
		return Map.of(
				"id", user.getId(),
				"name", user.getName(),
				"email", user.getEmail(),
				"phone", user.getPhone(),
				"address", user.getAddress(),
				"role", effectiveRole,
				"userType", effectiveRole);
	}

	private Map<String, Object> toVehicleView(Vehicle vehicle) {
		return Map.ofEntries(
				Map.entry("id", vehicle.getId()),
				Map.entry("renterId", vehicle.getRenterId()),
				Map.entry("brand", vehicle.getBrand()),
				Map.entry("model", vehicle.getModel()),
				Map.entry("year", vehicle.getYear()),
				Map.entry("pricePerDay", vehicle.getPricePerDay()),
				Map.entry("status", vehicle.getStatus() == null ? "PENDING" : vehicle.getStatus()),
				Map.entry("imageUrl", vehicle.getImageUrl() == null ? "" : vehicle.getImageUrl()),
				Map.entry("description", vehicle.getDescription() == null ? "" : vehicle.getDescription()));
	}

	private Map<String, Object> toReviewView(Review review) {
		Vehicle vehicle = vehicleService.findVehicle(review.getVehicleId()).orElse(null);
		String vehicleName = vehicle == null
				? ("Vehicle #" + review.getVehicleId())
				: ((vehicle.getBrand() == null ? "" : vehicle.getBrand()) + " "
						+ (vehicle.getModel() == null ? "" : vehicle.getModel())).trim();

		return Map.ofEntries(
				Map.entry("id", review.getId()),
				Map.entry("vehicleId", review.getVehicleId()),
				Map.entry("vehicleName", vehicleName.isBlank() ? ("Vehicle #" + review.getVehicleId()) : vehicleName),
				Map.entry("bookingId", review.getBookingId()),
				Map.entry("customerId", review.getCustomerId()),
				Map.entry("renterId", review.getRenterId()),
				Map.entry("rating", review.getRating()),
				Map.entry("comment", review.getComment() == null ? "" : review.getComment()),
				Map.entry("updatedAt", review.getUpdatedAt() == null ? "" : review.getUpdatedAt().toString()));
	}

	private String resolveUserType(User user) {
		if (user == null || user.getId() == null) {
			return "CLIENT";
		}

		boolean hasListedVehicles = !vehicleService.getVehiclesByRenter(user.getId()).isEmpty();
		return hasListedVehicles ? "RENTER" : "CLIENT";
	}

	public record AdminRequest(String name, String email, String password, String role) {
	}

	public record AdminSelfUpdateRequest(String name, String email, String password) {
	}
}
