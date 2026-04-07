package com.rental.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.rental.model.Booking;
import com.rental.model.Vehicle;
import com.rental.repository.BookingRepository;

@Service
public class BookingService {

	private final BookingRepository bookingRepository;
	private final VehicleService vehicleService;

	public BookingService(BookingRepository bookingRepository, VehicleService vehicleService) {
		this.bookingRepository = bookingRepository;
		this.vehicleService = vehicleService;
	}

	public Booking createBooking(Long customerId, Long vehicleId, LocalDate startDate, LocalDate endDate) {
		Vehicle vehicle = vehicleService.findVehicle(vehicleId)
				.orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));

		if (!"APPROVED".equalsIgnoreCase(vehicle.getStatus())) {
			throw new IllegalArgumentException("Only approved vehicles can be booked.");
		}
		if (vehicle.getRenterId().equals(customerId)) {
			throw new IllegalArgumentException("You cannot book your own vehicle.");
		}

		validateDates(startDate, endDate);
		ensureVehicleNotBooked(vehicleId, null);

		int days = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);

		Booking booking = new Booking();
		booking.setVehicleId(vehicleId);
		booking.setRenterId(vehicle.getRenterId());
		booking.setCustomerId(customerId);
		booking.setStartDate(startDate);
		booking.setEndDate(endDate);
		booking.setTotalDays(days);
		booking.setTotalAmount(days * vehicle.getPricePerDay());
		booking.setStatus("PENDING");
		booking.setPaid(false);
		booking.setCreatedAt(LocalDateTime.now());
		booking.setUpdatedAt(LocalDateTime.now());

		return bookingRepository.save(booking);
	}

	public List<Booking> getBookingsByCustomer(Long customerId) {
		return bookingRepository.findAll().stream()
				.filter(booking -> booking.getCustomerId().equals(customerId))
				.toList();
	}

	public List<Booking> getBookingsForRenter(Long renterId) {
		return bookingRepository.findAll().stream()
				.filter(booking -> booking.getRenterId().equals(renterId))
				.toList();
	}

	public Booking updateBookingByCustomer(Long customerId, Long bookingId, LocalDate startDate, LocalDate endDate) {
		Booking booking = findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));

		if (!booking.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can edit only your own bookings.");
		}
		if ("RENTED".equalsIgnoreCase(booking.getStatus())) {
			throw new IllegalArgumentException("Already rented booking cannot be edited.");
		}

		validateDates(startDate, endDate);
		ensureVehicleNotBooked(booking.getVehicleId(), booking.getId());

		Vehicle vehicle = vehicleService.findVehicle(booking.getVehicleId())
				.orElseThrow(() -> new IllegalArgumentException("Vehicle not found."));
		int days = Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);

		booking.setStartDate(startDate);
		booking.setEndDate(endDate);
		booking.setTotalDays(days);
		booking.setTotalAmount(days * vehicle.getPricePerDay());
		if (!"REJECTED".equalsIgnoreCase(booking.getStatus())) {
			booking.setStatus("PENDING");
		}
		booking.setUpdatedAt(LocalDateTime.now());

		return bookingRepository.update(booking);
	}

	public void deleteBookingByCustomer(Long customerId, Long bookingId) {
		Booking booking = findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));
		if (!booking.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can delete only your own bookings.");
		}
		if ("RENTED".equalsIgnoreCase(booking.getStatus())) {
			throw new IllegalArgumentException("Already rented booking cannot be deleted.");
		}
		bookingRepository.deleteById(bookingId);
	}

	public Booking confirmBookingByRenter(Long renterId, Long bookingId) {
		Booking booking = getRenterOwnedBooking(renterId, bookingId);
		booking.setStatus("CONFIRMED");
		booking.setUpdatedAt(LocalDateTime.now());
		return bookingRepository.update(booking);
	}

	public Booking rejectBookingByRenter(Long renterId, Long bookingId) {
		Booking booking = getRenterOwnedBooking(renterId, bookingId);
		booking.setStatus("REJECTED");
		booking.setPaid(false);
		booking.setUpdatedAt(LocalDateTime.now());
		return bookingRepository.update(booking);
	}

	public Booking markBookingRentedByRenter(Long renterId, Long bookingId) {
		Booking booking = getRenterOwnedBooking(renterId, bookingId);
		if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
			throw new IllegalArgumentException("Only confirmed bookings can be marked as rented.");
		}
		booking.setStatus("RENTED");
		booking.setUpdatedAt(LocalDateTime.now());
		return bookingRepository.update(booking);
	}

	public Booking markPaid(Long customerId, Long bookingId) {
		Booking booking = findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));
		if (!booking.getCustomerId().equals(customerId)) {
			throw new IllegalArgumentException("You can pay only your own booking.");
		}
		if (!"CONFIRMED".equalsIgnoreCase(booking.getStatus())) {
			throw new IllegalArgumentException("Only confirmed bookings can be paid.");
		}
		booking.setPaid(true);
		booking.setUpdatedAt(LocalDateTime.now());
		return bookingRepository.update(booking);
	}

	public Optional<Booking> findById(Long bookingId) {
		return bookingRepository.findById(bookingId);
	}

	private Booking getRenterOwnedBooking(Long renterId, Long bookingId) {
		Booking booking = findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));
		if (!booking.getRenterId().equals(renterId)) {
			throw new IllegalArgumentException("You can manage only bookings for your vehicles.");
		}
		return booking;
	}

	private void validateDates(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null) {
			throw new IllegalArgumentException("Booking dates are required.");
		}
		if (startDate.isBefore(LocalDate.now())) {
			throw new IllegalArgumentException("Start date cannot be in the past.");
		}
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("End date must be on or after start date.");
		}
	}

	private void ensureVehicleNotBooked(Long vehicleId, Long ignoreBookingId) {
		boolean alreadyBooked = bookingRepository.findAll().stream()
				.filter(booking -> booking.getVehicleId().equals(vehicleId))
				.filter(booking -> ignoreBookingId == null || !booking.getId().equals(ignoreBookingId))
				.anyMatch(booking -> isActiveBookingStatus(booking.getStatus()));

		if (alreadyBooked) {
			throw new IllegalArgumentException("This vehicle is already booked and cannot be booked again right now.");
		}
	}

	private boolean isActiveBookingStatus(String status) {
		if (status == null) {
			return false;
		}
		return "PENDING".equalsIgnoreCase(status)
				|| "CONFIRMED".equalsIgnoreCase(status)
				|| "RENTED".equalsIgnoreCase(status);
	}
}
