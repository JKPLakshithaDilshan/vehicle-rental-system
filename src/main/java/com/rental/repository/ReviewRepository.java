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

import com.rental.model.Review;
import com.rental.util.FileHandler;

@Repository
public class ReviewRepository {

	private static final String DELIMITER = "|";
	private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

	private final Path reviewsFilePath;
	private final FileHandler fileHandler;

	public ReviewRepository(
			@Value("${app.data.reviews-file:src/main/resources/data/reviews.txt}") String reviewsFile,
			FileHandler fileHandler) {
		this.reviewsFilePath = Path.of(reviewsFile);
		this.fileHandler = fileHandler;
		this.fileHandler.ensureFileExists(this.reviewsFilePath);
	}

	public synchronized List<Review> findAll() {
		List<Review> reviews = new ArrayList<>();
		for (String line : fileHandler.readLines(reviewsFilePath)) {
			if (line == null || line.isBlank()) {
				continue;
			}
			reviews.add(parseLine(line));
		}
		return reviews;
	}

	public synchronized Optional<Review> findById(Long id) {
		return findAll().stream().filter(review -> review.getId().equals(id)).findFirst();
	}

	public synchronized Review save(Review review) {
		List<Review> reviews = findAll();
		if (review.getId() == null) {
			review.setId(nextId(reviews));
		}
		reviews.add(review);
		writeAll(reviews);
		return review;
	}

	public synchronized Review update(Review review) {
		List<Review> reviews = findAll();
		int index = -1;
		for (int i = 0; i < reviews.size(); i++) {
			if (reviews.get(i).getId().equals(review.getId())) {
				index = i;
				break;
			}
		}

		if (index < 0) {
			throw new IllegalArgumentException("Review not found.");
		}

		reviews.set(index, review);
		writeAll(reviews);
		return review;
	}

	public synchronized boolean deleteById(Long id) {
		List<Review> reviews = findAll();
		boolean removed = reviews.removeIf(review -> review.getId().equals(id));
		if (removed) {
			writeAll(reviews);
		}
		return removed;
	}

	private void writeAll(List<Review> reviews) {
		List<String> rows = reviews.stream().map(this::toLine).toList();
		fileHandler.writeLines(reviewsFilePath, rows);
	}

	private Long nextId(List<Review> reviews) {
		return reviews.stream().map(Review::getId).max(Comparator.naturalOrder()).orElse(0L) + 1;
	}

	private String toLine(Review review) {
		String createdAt = review.getCreatedAt() == null ? "" : DATETIME_FORMATTER.format(review.getCreatedAt());
		String updatedAt = review.getUpdatedAt() == null ? "" : DATETIME_FORMATTER.format(review.getUpdatedAt());

		return review.getId() + DELIMITER
				+ review.getVehicleId() + DELIMITER
				+ review.getBookingId() + DELIMITER
				+ review.getCustomerId() + DELIMITER
				+ review.getRenterId() + DELIMITER
				+ review.getRating() + DELIMITER
				+ escape(review.getComment()) + DELIMITER
				+ createdAt + DELIMITER
				+ updatedAt;
	}

	private Review parseLine(String line) {
		String[] parts = line.split("\\|", -1);
		if (parts.length != 9) {
			throw new IllegalStateException("Corrupted review row: " + line);
		}

		return new Review(
				Long.parseLong(parts[0]),
				Long.parseLong(parts[1]),
				Long.parseLong(parts[2]),
				Long.parseLong(parts[3]),
				Long.parseLong(parts[4]),
				Integer.parseInt(parts[5]),
				unescape(parts[6]),
				parseDateTime(parts[7]),
				parseDateTime(parts[8]));
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
