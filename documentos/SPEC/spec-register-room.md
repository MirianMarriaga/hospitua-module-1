# Feature Specification: Register Room

**Created**: 2026-08-29

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Creating a new room in the inventory (Priority: P1)

As an Administrator, I want to register a new room on the platform indicating its identification, categorization, and commercial data, so that it becomes available in the hotel's inventory and can begin to be booked by front-desk staff or through the configured sales channels.


**Why this priority**: This is the foundational operation of Module 1 (Room and Inventory Management). Without the ability to create rooms, no other use case in the module (edit, deactivate, block, mark statuses, generate reports) has data to operate on. It is the central database of the system.

**Independent Test**: Can be tested independently by logging in as the system Administrator, completing the registration form with all the data (identification, categorization, and commercial), and verifying that the room appears in the inventory with the status "Available," with a unique identifier assigned and with its base rate.

**Acceptance Scenarios**:

1. **Scenario**: Successful registration with complete and valid data
   - **Given** the Administrator has logged into the system and is in the Room Inventory section
   - **When** they complete the registration form with room number, floor/wing, type, maximum capacity, base rate, and confirm the creation
   - **Then** the system creates the room with a unique ID (UUID), assigns it the status "Available," stores the base rate, and displays it in the inventory listing

2. **Scenario**: Attempt to register with a duplicate room number
   - **Given** a room with the number "204" already exists
   - **When** the Administrator attempts to register a new room using the same number "204"
   - **Then** the system rejects the operation and displays a message indicating that the room number already exists

3. **Scenario**: Attempt to register with incomplete required fields
   - **Given** the Administrator is filling out the registration form
   - **When** they attempt to confirm the creation without filling in one or more required fields (for example, room type or maximum capacity)
   - **Then** the system prevents saving and flags the pending fields to be completed

4. **Scenario**: Attempt to register with a base rate that is negative or equal to zero
   - **Given** the Administrator is filling out the registration form
   - **When** they enter a base rate less than or equal to zero
   - **Then** the system rejects the value and requests a valid base rate be entered (greater than zero)

---

### Edge Cases

- What happens if the Administrator attempts to register a room with a maximum occupancy of zero or a negative number?
- What happens if the Administrator attempts to register two rooms with the same number but on different floors/wings?
- How does the system handle the registration of a room type that is not among the predefined categories (Single, Double, Suite, Boutique)?
- How does the system handle a lost connection or a save failure partway through registration? Is a room left in an inconsistent or partially created state?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow only the "Administrator" actor to register new rooms in the inventory.
- **FR-002**: The system MUST automatically generate a unique identifier (UUID) for each registered room, without manual user intervention.
- **FR-003**: The system MUST require and validate the following fields as mandatory when registering a room: room number, floor/wing, room type, and maximum occupancy.
- **FR-004**: The system MUST validate that the room number is unique within the inventory, rejecting the registration if an active room with the same number already exists.
- **FR-005**: The system MUST restrict the "Type" field to the predefined categories: Single, Double, Suite, Boutique.
- **FR-006**: The system MUST validate that the maximum occupancy is a positive integer (greater than zero).
- **FR-007**: The system MUST validate that the entered base rate is a positive numeric value (greater than zero).
- **FR-008**: The system MUST automatically assign the status "Available" to every newly registered room.
- **FR-009**: The system MUST persist the registered room so that it is immediately visible in the room inventory listing.
- **FR-010**: The system MUST record the date and the user responsible for the creation of each room, for traceability purposes.


### Key Entities *(include if feature involves data)*

- **Room**: Represents a lodging unit of the hotel. Key attributes: unique ID (UUID), room number, floor/wing, type (Single, Double, Suite, Boutique), maximum occupancy, base rate, and current status (defaults to "Available" upon creation). It relates to Module 2 (Reservations) by being queried to check availability, and to Module 3 (Billing) through the query of its base rate.
- **Administrator**: Actor responsible for managing the room inventory, including its registration, editing, and deactivation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The system Administrator can complete the registration of a new room in less than 5 minutes.
- **SC-002**: 100% of successfully registered rooms end up in "Available" status and visible in the inventory immediately (without a manual refresh).
- **SC-003**: The system rejects 100% of registration attempts with a duplicate room number or missing required fields, displaying a clear error message.
- **SC-004**: Zero rooms are left registered without a unique ID or without an initial status assigned after the registration process.
