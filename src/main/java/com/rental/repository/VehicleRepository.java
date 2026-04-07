package com.rental.repository;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.rental.model.Vehicle;
import com.rental.util.FileHandler;

@Repository
public class VehicleRepository {

	private static final String DELIMITER = "|";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final Path vehiclesFilePath;
	private final FileHandler fileHandler;

	public VehicleRepository(
			@Value("${app.data.vehicles-file:src/main/resources/data/vehicles.txt}") String vehiclesFile,
			FileHandler fileHandler) {
		this.vehiclesFilePath = Path.of(vehiclesFile);
		this.fileHandler = fileHandler;
		this.fileHandler.ensureFileExists(this.vehiclesFilePath);
	}

	public synchronized List<Vehicle> findAll() {
		List<Vehicle> vehicles = new ArrayList<>();
		for (String line : fileHandler.readLines(vehiclesFilePath)) {
			if (line == null || line.isBlank()) {
				continue;
			}
			vehicles.add(parseLine(line));
		}
		return vehicles;
	}

	public synchronized Optional<Vehicle> findById(Long id) {
		return findAll().stream().filter(vehicle -> vehicle.getId().equals(id)).findFirst();
	}

	public synchronized Vehicle save(Vehicle vehicle) {
		List<Vehicle> vehicles = findAll();
		if (vehicle.getId() == null) {
			vehicle.setId(nextId(vehicles));
		}
		vehicles.add(vehicle);
		writeAll(vehicles);
		return vehicle;
	}

	public synchronized Vehicle update(Vehicle vehicle) {
		List<Vehicle> vehicles = findAll();
		int index = -1;
		for (int i = 0; i < vehicles.size(); i++) {
			if (vehicles.get(i).getId().equals(vehicle.getId())) {
				index = i;
				break;
			}
		}

		if (index < 0) {
			throw new IllegalArgumentException("Vehicle not found.");
		}

		vehicles.set(index, vehicle);
		writeAll(vehicles);
		return vehicle;
	}

	public synchronized boolean deleteById(Long id) {
		List<Vehicle> vehicles = findAll();
		boolean removed = vehicles.removeIf(vehicle -> vehicle.getId().equals(id));
		if (removed) {
			writeAll(vehicles);
		}
		return removed;
	}

	private void writeAll(List<Vehicle> vehicles) {
		List<String> rows = vehicles.stream().map(this::toLine).toList();
		fileHandler.writeLines(vehiclesFilePath, rows);
	}

	private Long nextId(List<Vehicle> vehicles) {
		return vehicles.stream().map(Vehicle::getId).max(Comparator.naturalOrder()).orElse(0L) + 1;
	}

	private String toLine(Vehicle vehicle) {
		String createdAt = vehicle.getCreatedAt() == null ? "" : DATE_FORMATTER.format(vehicle.getCreatedAt());
		String updatedAt = vehicle.getUpdatedAt() == null ? "" : DATE_FORMATTER.format(vehicle.getUpdatedAt());

		return vehicle.getId() + DELIMITER
				+ vehicle.getRenterId() + DELIMITER
				+ escape(vehicle.getBrand()) + DELIMITER
				+ escape(vehicle.getModel()) + DELIMITER
				+ vehicle.getYear() + DELIMITER
				+ vehicle.getPricePerDay() + DELIMITER
				+ escape(vehicle.getDescription()) + DELIMITER
				+ escape(vehicle.getImageUrl()) + DELIMITER
				+ escape(vehicle.getStatus()) + DELIMITER
				+ createdAt + DELIMITER
				+ updatedAt;
	}

	private Vehicle parseLine(String line) {
		String[] parts = line.split("\\|", -1);
		if (parts.length != 11) {
			throw new IllegalStateException("Corrupted vehicle row: " + line);
		}

		return new Vehicle(
				Long.parseLong(parts[0]),
				Long.parseLong(parts[1]),
				unescape(parts[2]),
				unescape(parts[3]),
				Integer.parseInt(parts[4]),
				Double.parseDouble(parts[5]),
				unescape(parts[6]),
				unescape(parts[7]),
				unescape(parts[8]),
				parseDate(parts[9]),
				parseDate(parts[10]));
	}

	private LocalDateTime parseDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return LocalDateTime.parse(value, DATE_FORMATTER);
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		return value
				.replace("\\", "\\\\")
				.replace("|", "\\|")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	private String unescape(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		boolean escaping = false;
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (escaping) {
				switch (character) {
					case 'n' -> result.append('\n');
					case 'r' -> result.append('\r');
					default -> result.append(character);
				}
				escaping = false;
			} else if (character == '\\') {
				escaping = true;
			} else {
				result.append(character);
			}
		}

		if (escaping) {
			result.append('\\');
		}
		return result.toString();
	}
}
