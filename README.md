# Vehicle Rental System

A Spring Boot MVC web application for renting, managing, and reviewing vehicles. Data is persisted using flat text files (no database required).

## Features
- Browse and search available vehicles
- User registration, login, and account management
- Book, reschedule, and cancel vehicle rentals
- Admin panel for managing users, vehicles, and bookings
- Review and rating system for vehicles
- Data stored in flat text files (CSV format)

## Tech Stack
- **Backend**: Java 17, Spring Boot 3.2, Spring MVC
- **Frontend**: Thymeleaf, HTML5, CSS3, JavaScript
- **Data Storage**: Flat text files (CSV format)
- **Data Structures**: Custom file handlers, sorting/search utilities

## Project Structure
```
src/main/java/com/rental/
├── controller/   MVC controllers
├── model/        Domain models (User, Vehicle, Booking, Payment, Review)
├── repository/   Data access layer (file-based)
├── service/      Business logic layer
├── util/         FileHandler, IdGenerator, utilities
```

## Educational Client Project

This system was developed as part of an academic client-based project  
under the Software Engineering degree program at SLIIT.

The implementation reflects student design decisions, software  
engineering practices, and technical experimentation for educational  
purposes.

The project is shared publicly for portfolio demonstration and  
knowledge sharing only.

All trademarks, business names, or sample data used in this project  
are for academic simulation purposes.

## Running the Application
```bash
mvn spring-boot:run
```
Then open `http://localhost:8080` in your browser.

## Data Files
All data is stored in `src/main/resources/data/` as plain text CSV files:
- `admins.txt`, `users.txt`, `vehicles.txt`, `bookings.txt`, `payments.txt`, `reviews.txt`
