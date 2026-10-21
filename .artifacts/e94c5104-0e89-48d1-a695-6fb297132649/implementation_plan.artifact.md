# Data Schema Design for EcoWatch Admin App

This plan outlines the necessary enhancements to the existing data models to support the Admin App's core functionalities: report management, workforce assignment, and status tracking.

## User Review Required

> [!IMPORTANT]
> **Role Definition**: We need to decide on the specific roles. Current proposal includes: `RESIDENT`, `ADMIN`, `CLEANER`, and `MANAGEMENT`.
> **Assignment Logic**: Should a report be assignable to *both* a group and an individual, or just one at a time? This plan assumes both can be stored.

## Proposed Changes

### Core Data Models

---

#### [MODIFY] [Report.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/models/Report.java)
We will add fields to track the assignment and resolution status.
- `String assignedToId`: The unique ID of the individual cleaner assigned to the task.
- `String assignedGroupId`: The ID of the workforce group assigned.
- `String priority`: Severity level (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`).
- `String handledById`: The ID of the Admin who approved or assigned the report.
- `String resolutionImageUrl`: URL of the image provided by the cleaner upon completion.
- `String resolvedAt`: Timestamp of when the report was marked as resolved.

#### [MODIFY] [User.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/models/User.java)
We will add role-based fields to distinguish between app users and staff.
- `String role`: Enum-like string (`RESIDENT`, `ADMIN`, `CLEANER`, `MANAGEMENT`).
- `String groupId`: The ID of the team/group the user belongs to (primarily for cleaners).
- `String phoneNumber`: Essential for contacting cleaners/residents.

#### [NEW] [WorkforceGroup.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/models/WorkforceGroup.java)
A new model to manage groups of cleaners or management units.
- `String id`: Unique identifier.
- `String groupName`: e.g., "District 1 Cleanup Crew".
- `String areaOfResponsibility`: Geographic or administrative area (e.g., "Barangay Holy Spirit").

#### [MODIFY] [History.java](file:///C:/Users/63975/Desktop/BSIT/3RD YEAR/IT MAJOR ELECTIVE/MyEcoWatch1/app/src/main/java/com/example/myecowatch1/data/models/History.java)
Ensure the history log captures admin-specific actions.
- `String actorId`: The ID of the user (Admin/Cleaner) who performed the action.
- `String actionDetails`: More descriptive text for admin actions (e.g., "Assigned to Team Alpha").

## Verification Plan

### Manual Verification
- Review the proposed Java class structures to ensure they align with the current Room database and Retrofit/Gson configurations.
- Verify that adding these fields won't break existing `RESIDENT` app functionality (nullability handling).
