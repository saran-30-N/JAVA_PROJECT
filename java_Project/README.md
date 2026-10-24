# KEC R2024 Student Academic Records

A menu-driven Java/JDBC application for student and course records, enrollment,
incremental assessment entry, attendance eligibility, and academic reports.
The application uses a local SQLite database (`kec_academics.db`).

## Open and run in VS Code

1. Open this project folder in VS Code.
2. Install the **Extension Pack for Java** and **Maven for Java** if prompted.
3. Make sure a JDK 17 or newer and Maven are installed and available in `PATH`.
4. Allow Maven to download the SQLite JDBC driver on the first build.
5. Run `mvn compile exec:java` in the VS Code terminal, or open
   `.vscode/launch.json` and start **Run KEC Academic Records**.

The first run creates the database and tables from `kec_schema.sql`. The database
file is created in the project folder and is ignored by Git.

## Typical data-entry sequence

1. Add a student and a course. Each student must have a unique roll number and
   each course must have a unique course code; trying to add an existing value
   reports the conflict without changing the existing record.
2. Enroll the student in the course and semester.
3. Record attendance periodically. Each entry adds held and attended classes to
   the enrollment totals.
4. Enter CIA Test 1, CIA Test 2, and coursework separately as those marks become
   available; re-entering a component updates that component without replacing
   the other marks.
5. Record whether medical condonation was approved for a student in the
   65%-to-under-75% attendance band.
6. After the examination, enter an ESE score or `AB`.
7. Generate the student mark sheet, attendance defaulter report, or department
   result summary.

All assessment components are entered on a 0-100 scale. The internal score is
the average of the CIA1, CIA2, and coursework components that have been entered,
multiplied by the course's CIA weightage. The ESE score is multiplied by its
course-type weightage. Until the ESE mark or absence is entered, the course
result is shown as pending. This component-average convention is an application
assumption for incremental entry; confirm the institution's detailed CIA
combination method before using the output as an official result.

Attendance under 65% results in SA. Attendance from 65% to under 75% results in
SA until medical condonation is approved. Attendance of 75% or higher is
eligible without condonation.

## Schema and screenshots

`kec_schema.sql` is the standalone SQLite database schema. To capture the
requested evidence, run the application in the VS Code terminal and use Windows
Snipping Tool (`Win+Shift+S`) to save screenshots showing:

- a student and course successfully added;
- CIA1, CIA2, and ESE entered in separate menu operations; and
- a completed student mark sheet showing the calculated grade and SGPA.

Screenshots should be captured from your own run because the database starts
empty and the screenshots must reflect your actual records and results.
