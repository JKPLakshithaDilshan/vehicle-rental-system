package com.rental.repository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.rental.model.Booking;
import com.rental.util.FileHandler;

@Repository
public class BookingRepository {

	private static final String DELIMITER = "|";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
	private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final Path bookingsFilePath;
	private final FileHandler fileHandler;

	public BookingRepository(
			@Value("${app.data.bookings-file:src/main/resources/data/bookings.txt}") String bookingsFile,
			FileHandler fileHandler) {
		this.bookingsFilePath = Path.of(bookingsFile);
		this.fileHandler = fileHandler;
		this.fileHandler.ensureFileExists(this.bookingsFilePath);
	}

	public synchronized List<Booking> findAll() {
		List<Booking> bookings = new ArrayList<>();
		for (String line : fileHandler.readLines(bookingsFilePath)) {
			if (line == null || line.isBlank()) {
				continue;
			}
			bookings.add(parseLine(line));
		}
		return bookings;
	}

	public synchronized Optional<Booking> findById(Long id) {
		return findAll().stream().filter(booking -> booking.getId().equals(id)).findFirst();
	}

	public synchronized Booking save(Booking booking) {
		List<Booking> bookings = findAll();
		if (booking.getId() == null) {
			booking.setId(nextId(bookings));
		}
		bookings.add(booking);
		writeAll(bookings);
		return booking;
	}

	public synchronized Booking update(Booking booking) {
		List<Booking> bookings = findAll();
		int index = -1;
		for (int i = 0; i < bookings.size(); i++) {
			if (bookings.get(i).getId().equals(booking.getId())) {
				index = i;
				break;
			}
		}

		if (index < 0) {
			throw new IllegalArgumentException("Booking not found.");
		}

		bookings.set(index, booking);
		writeAll(bookings);
		return booking;
	}

	public synchronized boolean deleteById(Long id) {
		List<Booking> bookings = findAll();
		boolean removed = bookings.removeIf(booking -> booking.getId().equals(id));
		if (removed) {
			writeAll(bookings);
		}
		return removed;
	}

	private void writeAll(List<Booking> bookings) {
		List<String> rows = bookings.stream().map(this::toLine).toList();
		fileHandler.writeLines(bookingsFilePath, rows);
	}

	private Long nextId(List<Booking> bookings) {
		return bookings.stream().map(Booking::getId).max(Comparator.naturalOrder()).orElse(0L) + 1;
	}

	private String toLine(Booking booking) {
		String createdAt = booking.getCreatedAt() == null ? "" : DATETIME_FORMATTER.format(booking.getCreatedAt());
		String updatedAt = booking.getUpdatedAt() == null ? "" : DATETIME_FORMATTER.format(booking.getUpdatedAt());

		return booking.getId() + DELIMITER
				+ booking.getVehicleId() + DELIMITER
				+ booking.getRenterId() + DELIMITER
				+ booking.getCustomerId() + DELIMITER
				+ DATE_FORMATTER.format(booking.getStartDate()) + DELIMITER
				+ DATE_FORMATTER.format(booking.getEndDate()) + DELIMITER
				+ booking.getTotalDays() + DELIMITER
				+ booking.getTotalAmount() + DELIMITER
				+ escape(booking.getStatus()) + DELIMITER
				+ booking.isPaid() + DELIMITER
				+ createdAt + DELIMITER
				+ updatedAt;
	}

	private Booking parseLine(String line) {
		String[] parts = line.split("\\|", -1);
		if (parts.length != 12) {
			throw new IllegalStateException("Corrupted booking row: " + line);
		}

		return new Booking(
				Long.parseLong(parts[0]),
				Long.parseLong(parts[1]),
				Long.parseLong(parts[2]),
				Long.parseLong(parts[3]),
				LocalDate.parse(parts[4], DATE_FORMATTER),
				LocalDate.parse(parts[5], DATE_FORMATTER),
				Integer.parseInt(parts[6]),
				Double.parseDouble(parts[7]),
				unescape(parts[8]),
				Boolean.parseBoolean(parts[9]),
				parseDateTime(parts[10]),
				parseDateTime(parts[11]));
	}

	private LocalDateTime parseDateTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return LocalDateTime.parse(value, DATETIME_FORMATTER);
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
