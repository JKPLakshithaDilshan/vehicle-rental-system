package com.rental.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rental.model.Admin;
import com.rental.model.User;
import com.rental.service.AdminService;
import com.rental.service.UserService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class UserController {

	private static final String USER_ID_SESSION_KEY = "AUTH_USER_ID";
	private static final String ADMIN_ID_SESSION_KEY = "AUTH_ADMIN_ID";
	private static final String AUTH_ROLE_SESSION_KEY = "AUTH_ROLE";

	private final UserService userService;
	private final AdminService adminService;

	public UserController(UserService userService, AdminService adminService) {
		this.userService = userService;
		this.adminService = adminService;
	}

	@PostMapping("/auth/register")
	public ResponseEntity<?> register(@RequestBody RegisterRequest request, HttpSession session) {
		try {
			User user = userService.register(request.name(), request.email(), request.phone(), request.address(),
					request.password(), request.role());
			String role = normalizeRenterRole(user.getRole());
			session.removeAttribute(ADMIN_ID_SESSION_KEY);
			session.setAttribute(USER_ID_SESSION_KEY, user.getId());
			session.setAttribute(AUTH_ROLE_SESSION_KEY, role);
			return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
					"message", "Account created successfully.",
					"role", role,
					"user", toPublicUser(user),
					"redirectPage", "account.html"));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@PostMapping("/auth/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpSession session) {
		try {
			Admin admin = null;
			try {
				admin = adminService.authenticate(request.email(), request.password());
			} catch (IllegalArgumentException ignored) {
				// Try regular user authentication when admin auth fails.
			}

			if (admin != null) {
				String normalizedAdminRole = normalizeRole(admin.getRole());
				session.removeAttribute(USER_ID_SESSION_KEY);
				session.setAttribute(ADMIN_ID_SESSION_KEY, admin.getAdminId());
				session.setAttribute(AUTH_ROLE_SESSION_KEY, normalizedAdminRole);
				return ResponseEntity.ok(Map.of(
						"message", "Login successful.",
						"role", normalizedAdminRole,
						"admin", toAdminView(admin),
						"redirectPage", "admin-dashboard.html"));
			}

			User user = userService.login(request.email(), request.password());
			String role = normalizeRenterRole(user.getRole());
			session.removeAttribute(ADMIN_ID_SESSION_KEY);
			session.setAttribute(USER_ID_SESSION_KEY, user.getId());
			session.setAttribute(AUTH_ROLE_SESSION_KEY, role);
			return ResponseEntity.ok(Map.of(
					"message", "Login successful.",
					"role", role,
					"user", toPublicUser(user),
					"redirectPage", "account.html"));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", exception.getMessage()));
		}
	}

	@PostMapping("/auth/logout")
	public ResponseEntity<?> logout(HttpSession session) {
		session.invalidate();
		return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
	}

	@GetMapping("/auth/status")
	public ResponseEntity<?> authStatus(HttpSession session) {
		String role = currentRole(session);
		if (isAdminRole(role)) {
			String adminId = currentAdminId(session);
			if (adminId == null) {
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.body(Map.of("authenticated", false, "message", "Not authenticated."));
			}

			return adminService.searchByAdminId(adminId)
					.map(admin -> ResponseEntity.ok(Map.of(
							"authenticated", true,
							"role", "ADMIN",
							"admin", toAdminView(admin),
							"redirectPage", "admin-dashboard.html")))
					.orElseGet(() -> {
						session.invalidate();
						return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
								.body(Map.of("authenticated", false, "message", "Session expired."));
					});
		}

		Long userId = currentUserId(session);
		if (userId == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("authenticated", false, "message", "Not authenticated."));
		}

		return userService.findById(userId)
				.map(user -> ResponseEntity.ok(Map.of(
						"authenticated", true,
						"role", normalizeRenterRole(user.getRole()),
						"user", toPublicUser(user),
						"redirectPage", "account.html")))
				.orElseGet(() -> {
					session.invalidate();
					return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
							.body(Map.of("authenticated", false, "message", "Session expired."));
				});
	}

	@GetMapping("/account/me")
	public ResponseEntity<?> getMyAccount(HttpSession session) {
		if (!isRenterRole(currentRole(session))) {
			return unauthorized();
		}

		Long userId = currentUserId(session);
		if (userId == null) {
			return unauthorized();
		}

		User user = userService.findById(userId).orElse(null);
		if (user == null) {
			session.removeAttribute(USER_ID_SESSION_KEY);
			return unauthorized();
		}

		return ResponseEntity.ok(Map.of("user", toPublicUser(user)));
	}

	@PutMapping("/account/me")
	public ResponseEntity<?> updateMyAccount(@RequestBody UpdateRequest request, HttpSession session) {
		if (!isRenterRole(currentRole(session))) {
			return unauthorized();
		}

		Long userId = currentUserId(session);
		if (userId == null) {
			return unauthorized();
		}

		try {
			User user = userService.updateProfile(userId, request.name(), request.email(), request.phone(),
					request.address(), request.password());
			return ResponseEntity.ok(Map.of("message", "Account updated successfully.", "user", toPublicUser(user)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@DeleteMapping("/account/me")
	public ResponseEntity<?> deleteMyAccount(HttpSession session) {
		if (!isRenterRole(currentRole(session))) {
			return unauthorized();
		}

		Long userId = currentUserId(session);
		if (userId == null) {
			return unauthorized();
		}

		try {
			userService.deleteAccount(userId);
			session.invalidate();
			return ResponseEntity.ok(Map.of("message", "Account deleted successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
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

	private String currentAdminId(HttpSession session) {
		Object raw = session.getAttribute(ADMIN_ID_SESSION_KEY);
		if (raw instanceof String id) {
			return id;
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

	private boolean isAdminRole(String role) {
		return "ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role);
	}

	private boolean isRenterRole(String role) {
		return "RENTER".equalsIgnoreCase(role) || "USER".equalsIgnoreCase(role);
	}

	private String normalizeRenterRole(String role) {
		if (role == null) {
			return "RENTER";
		}
		String cleaned = role.trim().toUpperCase();
		if ("USER".equals(cleaned)) {
			return "RENTER";
		}
		return cleaned.isBlank() ? "RENTER" : cleaned;
	}

	private String normalizeRole(String role) {
		if (role == null) {
			return "ADMIN";
		}
		return role.trim().toUpperCase();
	}

	private ResponseEntity<?> unauthorized() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Please login first."));
	}

	private Map<String, Object> toPublicUser(User user) {
		return Map.of(
				"id", user.getId(),
				"name", user.getName(),
				"email", user.getEmail(),
				"phone", user.getPhone(),
				"address", user.getAddress(),
				"role", user.getRole() == null ? "USER" : user.getRole(),
				"createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString(),
				"updatedAt", user.getUpdatedAt() == null ? "" : user.getUpdatedAt().toString());
	}

	private Map<String, Object> toAdminView(Admin admin) {
		return Map.of(
				"adminId", admin.getAdminId(),
				"name", admin.getName(),
				"email", admin.getEmail(),
				"role", admin.getRole());
	}

	public record RegisterRequest(String name, String email, String phone, String address, String password,
			String role) {
	}

	public record LoginRequest(String email, String password) {
	}

	public record UpdateRequest(String name, String email, String phone, String address, String password) {
	}
}
