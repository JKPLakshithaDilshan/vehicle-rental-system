(function () {
  "use strict";

  function setFeedback(id, message, type) {
    var element = document.getElementById(id);
    if (!element) {
      return;
    }
    element.textContent = message || "";
    element.classList.remove("is-error", "is-success");
    if (type) {
      element.classList.add(type === "error" ? "is-error" : "is-success");
    }
  }

  function escapeHtml(value) {
    return String(value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function requestJson(url, options) {
    return fetch(url, {
      credentials: "same-origin",
      headers: { "Content-Type": "application/json" },
      ...options
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (!response.ok) {
          throw new Error(body.message || "Request failed.");
        }
        return body;
      });
    });
  }

  function getVehicleIdFromUrl() {
    var params = new URLSearchParams(window.location.search);
    var raw = params.get("vehicleId");
    if (!raw) {
      return null;
    }
    var id = Number(raw);
    return Number.isFinite(id) && id > 0 ? id : null;
  }

  var state = {
    auth: null,
    vehicleId: null,
    vehicle: null,
    cards: [],
    myBookings: [],
    editingReviewByBooking: {},
    modalMode: null,
    modalContext: {}
  };

  function todayIsoDate() {
    return new Date().toISOString().split("T")[0];
  }

  function applyDateMinimums() {
    var today = todayIsoDate();
    var start = document.getElementById("bookingStartDate");
    var end = document.getElementById("bookingEndDate");
    var modalStart = document.getElementById("modalStartDate");
    var modalEnd = document.getElementById("modalEndDate");

    if (start) {
      start.setAttribute("min", today);
    }
    if (end) {
      end.setAttribute("min", today);
    }
    if (modalStart) {
      modalStart.setAttribute("min", today);
    }
    if (modalEnd) {
      modalEnd.setAttribute("min", today);
    }
  }

  function validateBookingDateRange(startDate, endDate) {
    var today = todayIsoDate();
    if (!startDate || !endDate) {
      return "Start date and end date are required.";
    }
    if (startDate < today) {
      return "Start date must be today or a future date.";
    }
    if (endDate < today) {
      return "End date must be today or a future date.";
    }
    if (endDate < startDate) {
      return "End date must be on or after start date.";
    }
    return "";
  }

  function isRegisteredBookingUser(auth) {
    if (!auth || !auth.authenticated) {
      return false;
    }
    var role = String(auth.role || "").toUpperCase();
    return role === "RENTER" || role === "USER";
  }

  function showActionModal(mode, context) {
    var modal = document.getElementById("bookingActionModal");
    if (!modal) {
      return;
    }

    state.modalMode = mode;
    state.modalContext = context || {};

    var title = document.getElementById("bookingActionTitle");
    var saveBtn = document.getElementById("bookingActionSaveBtn");
    var bookingFields = document.getElementById("modalBookingFields");
    var paymentFields = document.getElementById("modalPaymentFields");
    var reviewFields = document.getElementById("modalReviewFields");
    var deleteFields = document.getElementById("modalDeleteFields");
    var deleteText = document.getElementById("modalDeleteText");

    [bookingFields, paymentFields, reviewFields, deleteFields].forEach(function (el) {
      if (el) {
        el.classList.remove("is-active");
      }
    });
    setFeedback("bookingActionFeedback", "", null);

    if (mode === "edit-booking") {
      if (title) {
        title.textContent = "Edit Booking";
      }
      if (saveBtn) {
        saveBtn.textContent = "Update Booking";
      }
      if (bookingFields) {
        bookingFields.classList.add("is-active");
      }
      document.getElementById("modalStartDate").value = context.startDate || "";
      document.getElementById("modalEndDate").value = context.endDate || "";
      var modalEnd = document.getElementById("modalEndDate");
      if (modalEnd) {
        modalEnd.setAttribute("min", context.startDate || todayIsoDate());
      }
    } else if (mode === "pay-booking") {
      if (title) {
        title.textContent = "Pay Booking";
      }
      if (saveBtn) {
        saveBtn.textContent = "Pay Now";
      }
      if (paymentFields) {
        paymentFields.classList.add("is-active");
      }
      var select = document.getElementById("modalCardSelect");
      if (select) {
        select.innerHTML = state.cards.map(function (card) {
          return "<option value='" + card.id + "'>" + card.id + " - " + escapeHtml(card.cardHolderName || "Card") + "</option>";
        }).join("");
      }
    } else if (mode === "create-review" || mode === "edit-review") {
      if (title) {
        title.textContent = mode === "create-review" ? "Add Review" : "Update Review";
      }
      if (saveBtn) {
        saveBtn.textContent = mode === "create-review" ? "Post Review" : "Save Review";
      }
      if (reviewFields) {
        reviewFields.classList.add("is-active");
      }
      document.getElementById("modalRating").value = context.rating || "5";
      document.getElementById("modalComment").value = context.comment || "";
    } else if (mode === "delete-card" || mode === "delete-booking" || mode === "delete-review") {
      if (title) {
        title.textContent = "Confirm Delete";
      }
      if (saveBtn) {
        saveBtn.textContent = "Delete";
      }
      if (deleteFields) {
        deleteFields.classList.add("is-active");
      }
      if (deleteText) {
        deleteText.textContent = context.message || "Are you sure you want to delete this item?";
      }
    }

    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
  }

  function closeActionModal() {
    var modal = document.getElementById("bookingActionModal");
    if (!modal) {
      return;
    }
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
    state.modalMode = null;
    state.modalContext = {};
    setFeedback("bookingActionFeedback", "", null);
  }

  function wireActionModal() {
    var modal = document.getElementById("bookingActionModal");
    var closeBtn = document.getElementById("bookingActionCloseBtn");
    var cancelBtn = document.getElementById("bookingActionCancelBtn");
    var form = document.getElementById("bookingActionForm");

    if (!modal || !form) {
      return;
    }

    modal.addEventListener("click", function (event) {
      var target = event.target;
      if (target && target.getAttribute("data-modal-close") === "true") {
        closeActionModal();
      }
    });

    if (closeBtn) {
      closeBtn.addEventListener("click", closeActionModal);
    }
    if (cancelBtn) {
      cancelBtn.addEventListener("click", closeActionModal);
    }

    form.addEventListener("submit", function (event) {
      event.preventDefault();

      if (state.modalMode === "edit-booking") {
        var modalStartDate = document.getElementById("modalStartDate").value;
        var modalEndDate = document.getElementById("modalEndDate").value;
        var modalDateError = validateBookingDateRange(modalStartDate, modalEndDate);
        if (modalDateError) {
          setFeedback("bookingActionFeedback", modalDateError, "error");
          return;
        }

        requestJson("/api/bookings/" + state.modalContext.bookingId, {
          method: "PUT",
          body: JSON.stringify({
            startDate: modalStartDate,
            endDate: modalEndDate
          })
        }).then(function () {
          closeActionModal();
          setFeedback("myBookingsFeedback", "Booking updated.", "success");
          return loadBookings();
        }).catch(function (error) {
          setFeedback("bookingActionFeedback", error.message, "error");
        });
        return;
      }

      if (state.modalMode === "pay-booking") {
        requestJson("/api/payments/bookings/" + state.modalContext.bookingId + "/pay", {
          method: "POST",
          body: JSON.stringify({ cardId: Number(document.getElementById("modalCardSelect").value) })
        }).then(function () {
          closeActionModal();
          setFeedback("myBookingsFeedback", "Payment successful.", "success");
          return loadBookings();
        }).catch(function (error) {
          setFeedback("bookingActionFeedback", error.message, "error");
        });
        return;
      }

      if (state.modalMode === "create-review") {
        requestJson("/api/reviews", {
          method: "POST",
          body: JSON.stringify({
            vehicleId: state.vehicleId,
            bookingId: state.modalContext.bookingId,
            rating: Number(document.getElementById("modalRating").value),
            comment: document.getElementById("modalComment").value
          })
        }).then(function () {
          closeActionModal();
          setFeedback("reviewsFeedback", "Review submitted.", "success");
          return loadVehicleReviews();
        }).catch(function (error) {
          setFeedback("bookingActionFeedback", error.message, "error");
        });
        return;
      }

      if (state.modalMode === "edit-review") {
        requestJson("/api/reviews/" + state.modalContext.reviewId, {
          method: "PUT",
          body: JSON.stringify({
            rating: Number(document.getElementById("modalRating").value),
            comment: document.getElementById("modalComment").value
          })
        }).then(function () {
          closeActionModal();
          setFeedback("reviewsFeedback", "Review updated.", "success");
          return loadVehicleReviews();
        }).catch(function (error) {
          setFeedback("bookingActionFeedback", error.message, "error");
        });
        return;
      }

      if (state.modalMode === "delete-card") {
        requestJson("/api/payments/cards/" + state.modalContext.cardId, { method: "DELETE" })
          .then(function () {
            closeActionModal();
            setFeedback("cardFeedback", "Card deleted.", "success");
            return loadCards();
          })
          .catch(function (error) {
            setFeedback("bookingActionFeedback", error.message, "error");
          });
        return;
      }

      if (state.modalMode === "delete-booking") {
        requestJson("/api/bookings/" + state.modalContext.bookingId, { method: "DELETE" })
          .then(function () {
            closeActionModal();
            setFeedback("myBookingsFeedback", "Booking deleted.", "success");
            return loadBookings();
          })
          .catch(function (error) {
            setFeedback("bookingActionFeedback", error.message, "error");
          });
        return;
      }

      if (state.modalMode === "delete-review") {
        requestJson("/api/reviews/" + state.modalContext.reviewId, { method: "DELETE" })
          .then(function () {
            closeActionModal();
            setFeedback("reviewsFeedback", "Review deleted.", "success");
            return loadVehicleReviews();
          })
          .catch(function (error) {
            setFeedback("bookingActionFeedback", error.message, "error");
          });
      }
    });
  }

  function toDateRange(booking) {
    return booking.startDate + " to " + booking.endDate + " (" + booking.totalDays + " days)";
  }

  function renderVehicleCard() {
    var card = document.getElementById("bookingVehicleCard");
    if (!card) {
      return;
    }
    if (!state.vehicle) {
      card.innerHTML = "<p class='vehicle-empty'>Vehicle not found.</p>";
      return;
    }

    var image = state.vehicle.imageUrl
      ? "<img class='vehicle-public-image-tag' src='" + escapeHtml(state.vehicle.imageUrl) + "' alt='vehicle'>"
      : "";

    card.innerHTML = "<article class='vehicle-public-card'>"
      + image
      + "<div class='vehicle-public-body'>"
      + "<h3>" + escapeHtml(state.vehicle.brand) + " " + escapeHtml(state.vehicle.model) + "</h3>"
      + "<p class='vehicle-public-meta'>Year: " + escapeHtml(state.vehicle.year) + "</p>"
      + "<p class='vehicle-public-price'>$" + Number(state.vehicle.pricePerDay || 0).toFixed(2) + " <span>/ day</span></p>"
      + "<p class='vehicle-public-desc'>" + escapeHtml(state.vehicle.description || "") + "</p>"
      + "</div></article>";
  }

  function renderCards() {
    var body = document.getElementById("cardsTableBody");
    if (!body) {
      return;
    }
    if (!state.cards.length) {
      body.innerHTML = "<tr><td colspan='5'>No cards saved.</td></tr>";
      return;
    }

    body.innerHTML = state.cards.map(function (card) {
      return "<tr>"
        + "<td>" + card.id + "</td>"
        + "<td>" + escapeHtml(card.cardHolderName) + "</td>"
        + "<td>" + escapeHtml(card.cardNumber) + "</td>"
        + "<td>" + escapeHtml(card.expiryMonth) + "/" + escapeHtml(card.expiryYear) + "</td>"
        + "<td>"
        + "<button type='button' class='account-btn-secondary card-edit-btn' data-card-id='" + card.id + "'>Edit</button> "
        + "<button type='button' class='account-btn-danger card-delete-btn' data-card-id='" + card.id + "'>Delete</button>"
        + "</td>"
        + "</tr>";
    }).join("");

    body.querySelectorAll(".card-edit-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-card-id"));
        var card = state.cards.find(function (item) { return item.id === id; });
        if (!card) {
          return;
        }
        document.getElementById("cardIdField").value = card.id;
        document.getElementById("cardHolderField").value = card.cardHolderName || "";
        document.getElementById("cardNumberField").value = card.cardNumber || "";
        document.getElementById("cardMonthField").value = card.expiryMonth || "";
        document.getElementById("cardYearField").value = card.expiryYear || "";
        document.getElementById("cardCvvField").value = "";
        document.getElementById("cardSubmitBtn").textContent = "Update Card";
        setFeedback("cardFeedback", "Editing card " + card.id + ".", "success");
      });
    });

    body.querySelectorAll(".card-delete-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-card-id"));
        showActionModal("delete-card", {
          cardId: id,
          message: "Delete card " + id + "?"
        });
      });
    });
  }

  function renderBookings() {
    var body = document.getElementById("myBookingsTableBody");
    if (!body) {
      return;
    }
    if (!state.myBookings.length) {
      body.innerHTML = "<tr><td colspan='6'>No bookings created yet.</td></tr>";
      return;
    }

    body.innerHTML = state.myBookings.map(function (booking) {
      var actions = [
        "<button type='button' class='account-btn-secondary booking-edit-btn' data-booking-id='" + booking.id + "'>Edit</button>",
        "<button type='button' class='account-btn-danger booking-delete-btn' data-booking-id='" + booking.id + "'>Delete</button>"
      ];

      if (booking.status === "CONFIRMED" && !booking.paid) {
        actions.push("<button type='button' class='account-btn-primary booking-pay-btn' data-booking-id='" + booking.id + "'>Pay</button>");
      }

      if (booking.paid) {
        actions.push("<button type='button' class='account-btn-secondary booking-review-btn' data-booking-id='" + booking.id + "'>Review</button>");
      }

      return "<tr>"
        + "<td>" + booking.id + "</td>"
        + "<td>" + escapeHtml((booking.vehicleBrand || "") + " " + (booking.vehicleModel || "")) + "</td>"
        + "<td>" + escapeHtml(toDateRange(booking)) + "</td>"
        + "<td>" + escapeHtml(booking.status || "") + (booking.paid ? " / PAID" : "") + "</td>"
        + "<td>$" + Number(booking.totalAmount || 0).toFixed(2) + "</td>"
        + "<td class='booking-actions-cell'>" + actions.join(" ") + "</td>"
        + "</tr>";
    }).join("");

    body.querySelectorAll(".booking-edit-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-booking-id"));
        var booking = state.myBookings.find(function (item) { return item.id === id; });
        if (!booking) {
          return;
        }
        showActionModal("edit-booking", {
          bookingId: id,
          startDate: booking.startDate,
          endDate: booking.endDate
        });
      });
    });

    body.querySelectorAll(".booking-delete-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-booking-id"));
        showActionModal("delete-booking", {
          bookingId: id,
          message: "Delete booking " + id + "?"
        });
      });
    });

    body.querySelectorAll(".booking-pay-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-booking-id"));
        if (!state.cards.length) {
          setFeedback("myBookingsFeedback", "Add a payment card before paying.", "error");
          return;
        }
        showActionModal("pay-booking", { bookingId: id });
      });
    });

    body.querySelectorAll(".booking-review-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-booking-id"));
        showActionModal("create-review", { bookingId: id, rating: 5, comment: "" });
      });
    });
  }

  function renderReviews(reviews) {
    var body = document.getElementById("vehicleReviewsBody");
    if (!body) {
      return;
    }
    if (!reviews.length) {
      body.innerHTML = "<tr><td colspan='4'>No reviews posted for this vehicle yet.</td></tr>";
      return;
    }

    body.innerHTML = reviews.map(function (review) {
      var canEdit = state.auth && state.auth.user && review.customerId === state.auth.user.id;
      var actions = canEdit
        ? "<button type='button' class='account-btn-secondary review-edit-btn' data-review-id='" + review.id + "'>Edit</button> <button type='button' class='account-btn-danger review-delete-btn' data-review-id='" + review.id + "'>Delete</button>"
        : "-";
      return "<tr>"
        + "<td>" + review.rating + "/5</td>"
        + "<td>" + escapeHtml(review.comment || "") + "</td>"
        + "<td>" + review.customerId + "</td>"
        + "<td>" + actions + "</td>"
        + "</tr>";
    }).join("");

    body.querySelectorAll(".review-edit-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-review-id"));
        var review = reviews.find(function (item) { return item.id === id; });
        showActionModal("edit-review", {
          reviewId: id,
          rating: review ? review.rating : 5,
          comment: review ? review.comment : ""
        });
      });
    });

    body.querySelectorAll(".review-delete-btn").forEach(function (button) {
      button.addEventListener("click", function () {
        var id = Number(button.getAttribute("data-review-id"));
        showActionModal("delete-review", {
          reviewId: id,
          message: "Delete review " + id + "?"
        });
      });
    });
  }

  function loadAuth() {
    return requestJson("/api/auth/status", { method: "GET" }).then(function (auth) {
      if (!auth || !auth.authenticated || (auth.role !== "RENTER" && auth.role !== "USER" && auth.role !== "ADMIN" && auth.role !== "SUPERADMIN")) {
        window.location.href = "login.html";
        return Promise.reject(new Error("Unauthorized"));
      }
      if (auth.role === "ADMIN" || auth.role === "SUPERADMIN") {
        window.location.href = "admin-dashboard.html";
        return Promise.reject(new Error("Admin cannot book."));
      }
      state.auth = auth;
      var name = document.getElementById("bookingProfileName");
      var meta = document.getElementById("bookingProfileMeta");
      if (name) {
        name.textContent = auth.user ? auth.user.name : "User";
      }
      if (meta) {
        meta.textContent = auth.user ? auth.user.email : "";
      }
      return auth;
    });
  }

  function loadVehicle() {
    if (!state.vehicleId) {
      return requestJson("/api/vehicles", { method: "GET" })
        .then(function (data) {
          var vehicles = data.vehicles || [];
          if (!vehicles.length) {
            setFeedback("bookingCreateFeedback", "No approved vehicles available for booking.", "error");
            return;
          }
          state.vehicleId = vehicles[0].id;
          state.vehicle = vehicles[0];
          renderVehicleCard();
          setFeedback("bookingCreateFeedback", "Tip: open from a vehicle card to select a specific vehicle.", "success");
        })
        .catch(function (error) {
          setFeedback("bookingCreateFeedback", error.message, "error");
        });
    }
    return requestJson("/api/vehicles/" + state.vehicleId, { method: "GET" })
      .then(function (data) {
        state.vehicle = data.vehicle || null;
        renderVehicleCard();
      })
      .catch(function (error) {
        setFeedback("bookingCreateFeedback", error.message, "error");
      });
  }

  function loadBookings() {
    return requestJson("/api/bookings/mine/customer", { method: "GET" })
      .then(function (data) {
        state.myBookings = (data.bookings || []).filter(function (item) {
          return item.vehicleId === state.vehicleId;
        });
        renderBookings();
      })
      .catch(function (error) {
        setFeedback("myBookingsFeedback", error.message, "error");
      });
  }

  function loadCards() {
    return requestJson("/api/payments/cards", { method: "GET" })
      .then(function (data) {
        state.cards = data.cards || [];
        renderCards();
      })
      .catch(function (error) {
        setFeedback("cardFeedback", error.message, "error");
      });
  }

  function loadVehicleReviews() {
    return requestJson("/api/reviews/vehicle/" + state.vehicleId, { method: "GET" })
      .then(function (data) {
        renderReviews(data.reviews || []);
      })
      .catch(function (error) {
        setFeedback("reviewsFeedback", error.message, "error");
      });
  }

  function wireBookingForm() {
    var form = document.getElementById("bookingCreateForm");
    var startDateField = document.getElementById("bookingStartDate");
    var endDateField = document.getElementById("bookingEndDate");
    if (!form) {
      return;
    }

    if (startDateField && endDateField) {
      startDateField.addEventListener("change", function () {
        var startValue = startDateField.value || todayIsoDate();
        endDateField.setAttribute("min", startValue);
      });
    }

    form.addEventListener("submit", function (event) {
      event.preventDefault();

      if (!isRegisteredBookingUser(state.auth)) {
        setFeedback("bookingCreateFeedback", "To place a booking, you must be a registered user and login first.", "error");
        window.location.href = "login.html";
        return;
      }

      var startDate = startDateField ? startDateField.value : "";
      var endDate = endDateField ? endDateField.value : "";

      if (!state.vehicleId) {
        setFeedback("bookingCreateFeedback", "Please select a vehicle first.", "error");
        return;
      }
      if (!startDate || !endDate) {
        setFeedback("bookingCreateFeedback", "Start date and end date are required.", "error");
        return;
      }
      var dateError = validateBookingDateRange(startDate, endDate);
      if (dateError) {
        setFeedback("bookingCreateFeedback", dateError, "error");
        return;
      }

      requestJson("/api/bookings", {
        method: "POST",
        body: JSON.stringify({
          vehicleId: state.vehicleId,
          startDate: startDate,
          endDate: endDate
        })
      }).then(function () {
        setFeedback("bookingCreateFeedback", "Booking request submitted.", "success");
        form.reset();
        return loadBookings();
      }).catch(function (error) {
        setFeedback("bookingCreateFeedback", error.message, "error");
      });
    });
  }

  function validateCardForm() {
    var holderName = document.getElementById("cardHolderField").value.trim();
    var cardNumber = document.getElementById("cardNumberField").value.trim().replace(/\s+/g, "");
    var expiryMonth = document.getElementById("cardMonthField").value.trim();
    var expiryYear = document.getElementById("cardYearField").value.trim();
    var cvv = document.getElementById("cardCvvField").value.trim();

    // Validate cardholder name
    if (!holderName || holderName.length < 3) {
      return "Card holder name must be at least 3 characters.";
    }

    // Validate card number is exactly 16 digits
    if (!/^\d{16}$/.test(cardNumber)) {
      return "Card number must be 16 digits.";
    }

    // Validate expiry month (01-12)
    if (!/^(0[1-9]|1[0-2])$/.test(expiryMonth)) {
      return "Expiry month must be between 01 and 12.";
    }

    // Validate expiry year (4 digits)
    if (!/^\d{4}$/.test(expiryYear)) {
      return "Expiry year must be 4 digits (YYYY).";
    }

    // Check if card is expired
    var currentDate = new Date();
    var currentYear = currentDate.getFullYear();
    var currentMonth = currentDate.getMonth() + 1; // getMonth() returns 0-11
    var expMonth = parseInt(expiryMonth, 10);
    var expYear = parseInt(expiryYear, 10);

    if (expYear < currentYear || (expYear === currentYear && expMonth < currentMonth)) {
      return "Card has expired.";
    }

    // Validate CVV is exactly 3 digits
    if (!/^\d{3}$/.test(cvv)) {
      return "CVV must be 3 digits.";
    }

    return null; // All validations passed
  }

  function wireCardForm() {
    var form = document.getElementById("cardForm");
    var clearBtn = document.getElementById("cardClearBtn");
    if (!form) {
      return;
    }

    form.addEventListener("submit", function (event) {
      event.preventDefault();

      // Validate card before submission
      var validationError = validateCardForm();
      if (validationError) {
        setFeedback("cardFeedback", validationError, "error");
        return;
      }

      var payload = {
        cardHolderName: document.getElementById("cardHolderField").value,
        cardNumber: document.getElementById("cardNumberField").value,
        expiryMonth: document.getElementById("cardMonthField").value,
        expiryYear: document.getElementById("cardYearField").value,
        cvv: document.getElementById("cardCvvField").value
      };
      var cardId = document.getElementById("cardIdField").value;
      var url = cardId ? "/api/payments/cards/" + cardId : "/api/payments/cards";
      var method = cardId ? "PUT" : "POST";

      requestJson(url, { method: method, body: JSON.stringify(payload) })
        .then(function () {
          setFeedback("cardFeedback", cardId ? "Card updated." : "Card added.", "success");
          form.reset();
          document.getElementById("cardIdField").value = "";
          document.getElementById("cardSubmitBtn").textContent = "Save Card";
          return loadCards();
        })
        .catch(function (error) {
          setFeedback("cardFeedback", error.message, "error");
        });
    });

    if (clearBtn) {
      clearBtn.addEventListener("click", function () {
        form.reset();
        document.getElementById("cardIdField").value = "";
        document.getElementById("cardSubmitBtn").textContent = "Save Card";
      });
    }
  }

  function wireLogout() {
    var logoutBtn = document.getElementById("bookingLogoutBtn");
    if (!logoutBtn) {
      return;
    }
    logoutBtn.addEventListener("click", function () {
      requestJson("/api/auth/logout", { method: "POST", body: "{}" }).finally(function () {
        window.location.href = "login.html";
      });
    });
  }

  function init() {
    state.vehicleId = getVehicleIdFromUrl();
    applyDateMinimums();
    wireActionModal();
    wireBookingForm();
    wireCardForm();
    wireLogout();

    loadAuth()
      .then(loadVehicle)
      .then(loadBookings)
      .then(loadCards)
      .then(loadVehicleReviews)
      .catch(function () {
        // Handled by UI feedback/redirects.
      });
  }

  init();
})();
