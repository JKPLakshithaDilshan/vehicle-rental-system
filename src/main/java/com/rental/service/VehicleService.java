package com.rental.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.rental.model.Vehicle;
import com.rental.repository.VehicleRepository;

@Service
public class VehicleService {

	private final VehicleRepository vehicleRepository;

	public VehicleService(VehicleRepository vehicleRepository) {
		this.vehicleRepository = vehicleRepository;
	}

	public Vehicle createVehicle(Long renterId, String brand, String model, int year, double pricePerDay,
			String description, String imageUrl) {
		validateVehicleInput(brand, model, year, pricePerDay, description);

		Vehicle vehicle = new Vehicle();
		vehicle.setRenterId(renterId);
		vehicle.setBrand(clean(brand));
		vehicle.setModel(clean(model));
		vehicle.setYear(year);
		vehicle.setPricePerDay(pricePerDay);
		vehicle.setDescription(clean(description));
		vehicle.setImageUrl(clean(imageUrl));
		vehicle.setStatus("PENDING");
		vehicle.setCreatedAt(LocalDateTime.now());
		vehicle.setUpdatedAt(LocalDateTime.now());

		return vehicleRepository.save(vehicle);
	}

	public List<Vehicle> getApprovedVehicles(String query) {
		List<Vehicle> approved = vehicleRepository.findAll().stream()
				.filter(vehicle -> "APPROVED".equalsIgnoreCase(vehicle.getStatus()))
				.toList();

		if (query == null || query.isBlank()) {
			return approved;
		}

		String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);
		return approved.stream()
				.filter(vehicle -> contains(vehicle.getBrand(), normalizedQuery)
						|| contains(vehicle.getModel(), normalizedQuery)
						|| contains(vehicle.getDescription(), normalizedQuery)
						|| String.valueOf(vehicle.getYear()).contains(normalizedQuery))
				.toList();
	}

	public List<Vehicle> getVehiclesByRenter(Long renterId) {
		return vehicleRepository.findAll().stream()
				.filter(vehicle -> vehicle.getRenterId().equals(renterId))
				.toList();
	}

	public List<Vehicle> getAllVehicles(String query) {
		List<Vehicle> vehicles = vehicleRepository.findAll();
		if (query == null || query.isBlank()) {
			return vehicles;
		}

		String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);
		return vehicles.stream()
				.filter(vehicle -> contains(vehicle.getBrand(), normalizedQuery)
						|| contains(vehicle.getModel(), normalizedQuery)
						|| contains(vehicle.getDescription(), normalizedQuery)
						|| contains(vehicle.getStatus(), normalizedQuery)
						|| String.valueOf(vehicle.getYear()).contains(normalizedQuery)
						|| String.valueOf(vehicle.getRenterId()).contains(normalizedQuery)
						|| String.valueOf(vehicle.getId()).contains(normalizedQuery))
				.toList();
	}

	public Vehicle updateVehicleByRenter(Long renterId, Long vehicleId, String brand, String model, int year,
			double pricePerDay, String description, String imageUrl) {
		Vehicle existing = findVehicle(vehicleId)
				.orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));

		if (!existing.getRenterId().equals(renterId)) {
			throw new IllegalArgumentException("You can update only your own vehicles.");
		}

		validateVehicleInput(brand, model, year, pricePerDay, description);

		existing.setBrand(clean(brand));
		existing.setModel(clean(model));
		existing.setYear(year);
		existing.setPricePerDay(pricePerDay);
		existing.setDescription(clean(description));
		existing.setImageUrl(clean(imageUrl));
		existing.setStatus("PENDING");
		existing.setUpdatedAt(LocalDateTime.now());

		return vehicleRepository.update(existing);
	}

	public void deleteVehicleByRenter(Long renterId, Long vehicleId) {
		Vehicle existing = findVehicle(vehicleId)
				.orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));

		if (!existing.getRenterId().equals(renterId)) {
			throw new IllegalArgumentException("You can delete only your own vehicles.");
		}

		vehicleRepository.deleteById(vehicleId);
	}

	public void deleteVehicleByAdmin(Long vehicleId) {
		if (!vehicleRepository.deleteById(vehicleId)) {
			throw new IllegalArgumentException("Vehicle not found.");
		}
	}

	public List<Vehicle> getPendingVehiclesForApproval() {
		return vehicleRepository.findAll().stream()
				.filter(vehicle -> "PENDING".equalsIgnoreCase(vehicle.getStatus()))
				.toList();
	}

	public Vehicle approveVehicle(Long vehicleId) {
		Vehicle vehicle = findVehicle(vehicleId).orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
		vehicle.setStatus("APPROVED");
		vehicle.setUpdatedAt(LocalDateTime.now());
		return vehicleRepository.update(vehicle);
	}

	public Vehicle rejectVehicle(Long vehicleId) {
		Vehicle vehicle = findVehicle(vehicleId).orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
		vehicle.setStatus("REJECTED");
		vehicle.setUpdatedAt(LocalDateTime.now());
		return vehicleRepository.update(vehicle);
	}

	public Optional<Vehicle> findVehicle(Long vehicleId) {
		return vehicleRepository.findById(vehicleId);
	}

	private void validateVehicleInput(String brand, String model, int year, double pricePerDay, String description) {
		String cleanedBrand = clean(brand);
		String cleanedModel = clean(model);
		String cleanedDescription = clean(description);
		int currentYear = LocalDateTime.now().getYear();

		if (cleanedBrand.isBlank() || cleanedBrand.length() < 2 || cleanedBrand.length() > 60) {
			throw new IllegalArgumentException("Brand must be between 2 and 60 characters.");
		}
		if (cleanedModel.isBlank() || cleanedModel.length() < 2 || cleanedModel.length() > 80) {
			throw new IllegalArgumentException("Model must be between 2 and 80 characters.");
		}
		if (year < 1990 || year > currentYear) {
			throw new IllegalArgumentException("Vehicle year cannot be in the future.");
		}
		if (pricePerDay <= 0 || pricePerDay > 100000) {
			throw new IllegalArgumentException("Price per day must be greater than 0 and less than 100000.");
		}
		if (cleanedDescription.isBlank() || cleanedDescription.length() < 10 || cleanedDescription.length() > 500) {
			throw new IllegalArgumentException("Description must be between 10 and 500 characters.");
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
