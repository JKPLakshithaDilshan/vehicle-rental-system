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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rental.model.Vehicle;
import com.rental.service.VehicleService;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

	private static final String USER_ID_SESSION_KEY = "AUTH_USER_ID";
	private static final String AUTH_ROLE_SESSION_KEY = "AUTH_ROLE";

	private final VehicleService vehicleService;

	public VehicleController(VehicleService vehicleService) {
		this.vehicleService = vehicleService;
	}

	@PostMapping
	public ResponseEntity<?> createVehicle(@RequestBody VehicleRequest request, HttpSession session) {
		Long renterId = currentUserId(session);
		if (renterId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("message", "Only renters can add vehicles."));
		}

		try {
			Vehicle vehicle = vehicleService.createVehicle(
					renterId,
					request.brand(),
					request.model(),
					request.year(),
					request.pricePerDay(),
					request.description(),
					request.imageUrl());
			return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
					"message", "Vehicle submitted for admin approval.",
					"vehicle", toVehicleView(vehicle)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/mine")
	public ResponseEntity<?> getMyVehicles(HttpSession session) {
		Long renterId = currentUserId(session);
		if (renterId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("message", "Only renters can view their vehicle dashboard."));
		}

		List<Map<String, Object>> vehicles = vehicleService.getVehiclesByRenter(renterId).stream()
				.map(this::toVehicleView)
				.toList();
		return ResponseEntity.ok(Map.of("vehicles", vehicles));
	}

	@GetMapping
	public ResponseEntity<?> getApprovedVehicles(@RequestParam(required = false) String query) {
		List<Map<String, Object>> vehicles = vehicleService.getApprovedVehicles(query).stream()
				.map(this::toVehicleView)
				.toList();
		return ResponseEntity.ok(Map.of("vehicles", vehicles));
	}

	@GetMapping("/{vehicleId}")
	public ResponseEntity<?> getApprovedVehicleById(@PathVariable Long vehicleId) {
		Vehicle vehicle = vehicleService.findVehicle(vehicleId).orElse(null);
		if (vehicle == null || !"APPROVED".equalsIgnoreCase(vehicle.getStatus())) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Vehicle not found."));
		}
		return ResponseEntity.ok(Map.of("vehicle", toVehicleView(vehicle)));
	}

	@PutMapping("/{vehicleId}")
	public ResponseEntity<?> updateVehicle(@PathVariable Long vehicleId, @RequestBody VehicleRequest request,
			HttpSession session) {
		Long renterId = currentUserId(session);
		if (renterId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("message", "Only renters can update vehicles."));
		}

		try {
			Vehicle vehicle = vehicleService.updateVehicleByRenter(renterId, vehicleId, request.brand(), request.model(),
					request.year(), request.pricePerDay(), request.description(), request.imageUrl());
			return ResponseEntity.ok(Map.of("message", "Vehicle updated and resubmitted for approval.", "vehicle",
					toVehicleView(vehicle)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@DeleteMapping("/{vehicleId}")
	public ResponseEntity<?> deleteVehicle(@PathVariable Long vehicleId, HttpSession session) {
		Long renterId = currentUserId(session);
		if (renterId == null || !isRenterRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("message", "Only renters can delete vehicles."));
		}

		try {
			vehicleService.deleteVehicleByRenter(renterId, vehicleId);
			return ResponseEntity.ok(Map.of("message", "Vehicle removed successfully."));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@GetMapping("/admin/pending")
	public ResponseEntity<?> getPendingVehicles(HttpSession session) {
		if (!isAdminRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required."));
		}

		List<Map<String, Object>> vehicles = vehicleService.getPendingVehiclesForApproval().stream()
				.map(this::toVehicleView)
				.toList();
		return ResponseEntity.ok(Map.of("vehicles", vehicles));
	}

	@PostMapping("/admin/{vehicleId}/approve")
	public ResponseEntity<?> approveVehicle(@PathVariable Long vehicleId, HttpSession session) {
		if (!isAdminRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required."));
		}

		try {
			Vehicle vehicle = vehicleService.approveVehicle(vehicleId);
			return ResponseEntity.ok(Map.of("message", "Vehicle approved.", "vehicle", toVehicleView(vehicle)));
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
		}
	}

	@PostMapping("/admin/{vehicleId}/reject")
	public ResponseEntity<?> rejectVehicle(@PathVariable Long vehicleId, HttpSession session) {
		if (!isAdminRole(currentRole(session))) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Admin access required."));
		}

		try {
			Vehicle vehicle = vehicleService.rejectVehicle(vehicleId);
			return ResponseEntity.ok(Map.of("message", "Vehicle rejected.", "vehicle", toVehicleView(vehicle)));
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

	private Map<String, Object> toVehicleView(Vehicle vehicle) {
		return Map.ofEntries(
				Map.entry("id", vehicle.getId()),
				Map.entry("renterId", vehicle.getRenterId()),
				Map.entry("brand", vehicle.getBrand()),
				Map.entry("model", vehicle.getModel()),
				Map.entry("year", vehicle.getYear()),
				Map.entry("pricePerDay", vehicle.getPricePerDay()),
				Map.entry("description", vehicle.getDescription()),
				Map.entry("imageUrl", vehicle.getImageUrl() == null ? "" : vehicle.getImageUrl()),
				Map.entry("status", vehicle.getStatus() == null ? "PENDING" : vehicle.getStatus()),
				Map.entry("createdAt", vehicle.getCreatedAt() == null ? "" : vehicle.getCreatedAt().toString()),
				Map.entry("updatedAt", vehicle.getUpdatedAt() == null ? "" : vehicle.getUpdatedAt().toString()));
	}

	public record VehicleRequest(String brand, String model, int year, double pricePerDay, String description,
			String imageUrl) {
	}
}
