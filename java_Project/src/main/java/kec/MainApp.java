package kec;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class MainApp {
    private final Scanner input = new Scanner(System.in);
    private final StudentDAO studentDAO = new StudentDAO();
    private final AcademicDAO academicDAO = new AcademicDAO();

    public static void main(String[] args) {
        try {
            DBConnection.initialize();
            new MainApp().run();
        } catch (SQLException | IOException exception) {
            System.err.println("Could not start the application: " + exception.getMessage());
            System.exit(1);
        }
    }

    private void run() {
        System.out.println("Kongu Engineering College (Autonomous) - R2024 Academic Records");
        boolean running = true;
        while (running) {
            printMenu();
            String choice = prompt("Select an option: ");
            try {
                switch (choice) {
                    case "1" -> addStudent();
                    case "2" -> addCourse();
                    case "3" -> enrollStudent();
                    case "4" -> recordAttendance();
                    case "5" -> recordAssessment(AcademicDAO.Assessment.CIA1, "CIA Test 1");
                    case "6" -> recordAssessment(AcademicDAO.Assessment.CIA2, "CIA Test 2");
                    case "7" -> recordAssessment(AcademicDAO.Assessment.COURSEWORK, "Assignments / Quizzes / Lab");
                    case "8" -> recordEse();
                    case "9" -> setCondonation();
                    case "10" -> printStudentReport();
                    case "11" -> printDefaulters();
                    case "12" -> printDepartmentSummary();
                    case "0" -> running = false;
                    default -> System.out.println("Choose a listed menu option.");
                }
            } catch (SQLException | IllegalArgumentException exception) {
                System.out.println("Operation failed: " + exception.getMessage());
            }
        }
        System.out.println("Goodbye.");
    }

    private void printMenu() {
        System.out.println("""

                1. Add student
                2. Add course
                3. Enroll student in course
                4. Record attendance (increment held / attended classes)
                5. Enter CIA Test 1
                6. Enter CIA Test 2
                7. Enter assignments / quizzes / lab evaluation
                8. Enter ESE mark or record AB
                9. Record medical condonation decision
                10. Student semester mark sheet
                11. Attendance defaulters (<65%)
                12. Department result summary
                0. Exit
                """);
    }

    private void addStudent() throws SQLException {
        studentDAO.addStudent(prompt("Roll number: "), prompt("Name: "), prompt("Department: "),
                readInt("Current semester (1-8): "));
        System.out.println("Student added.");
    }

    private void addCourse() throws SQLException {
        studentDAO.addCourse(prompt("Course code: "), prompt("Course title: "), readDouble("Credits: "),
                prompt("Course type (THEORY / PRACTICAL / EMBEDDED): "));
        System.out.println("Course added.");
    }

    private void enrollStudent() throws SQLException {
        studentDAO.enrollStudent(prompt("Roll number: "), prompt("Course code: "),
                readInt("Semester (1-8): "));
        System.out.println("Student enrolled.");
    }

    private void recordAttendance() throws SQLException {
        String roll = prompt("Roll number: ");
        String course = prompt("Course code: ");
        int semester = readInt("Semester: ");
        int held = readInt("Additional classes held: ");
        int attended = readInt("Of those classes, attended: ");
        academicDAO.recordAttendance(roll, course, semester, held, attended);
        System.out.println("Attendance totals updated.");
    }

    private void recordAssessment(AcademicDAO.Assessment assessment, String label) throws SQLException {
        String roll = prompt("Roll number: ");
        String course = prompt("Course code: ");
        int semester = readInt("Semester: ");
        double mark = readDouble(label + " mark (0-100): ");
        academicDAO.updateAssessment(roll, course, semester, assessment, mark, false);
        System.out.println(label + " mark saved.");
    }

    private void recordEse() throws SQLException {
        String roll = prompt("Roll number: ");
        String course = prompt("Course code: ");
        int semester = readInt("Semester: ");
        String value = prompt("ESE mark (0-100) or AB: ").trim();
        if (value.equalsIgnoreCase("AB")) {
            academicDAO.updateAssessment(roll, course, semester, AcademicDAO.Assessment.ESE, null, true);
            System.out.println("ESE absence recorded.");
        } else {
            double mark;
            try {
                mark = Double.parseDouble(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Enter a numeric mark from 0 to 100, or AB.");
            }
            academicDAO.updateAssessment(roll, course, semester, AcademicDAO.Assessment.ESE, mark, false);
            System.out.println("ESE mark saved.");
        }
    }

    private void setCondonation() throws SQLException {
        String roll = prompt("Roll number: ");
        String course = prompt("Course code: ");
        int semester = readInt("Semester: ");
        String answer = prompt("Medical condonation approved? (Y/N): ");
        if (!answer.equalsIgnoreCase("Y") && !answer.equalsIgnoreCase("N")) {
            throw new IllegalArgumentException("Enter Y or N.");
        }
        academicDAO.setCondonationApproval(roll, course, semester, answer.equalsIgnoreCase("Y"));
        System.out.println("Condonation decision saved.");
    }

    private void printStudentReport() throws SQLException {
        AcademicDAO.StudentResult report = academicDAO.getStudentReport(prompt("Roll number: "),
                readInt("Semester: "));
        System.out.printf("%nKEC R2024 STUDENT SEMESTER MARK SHEET%nStudent: %s | %s | Semester %d%n",
                report.studentName(), report.rollNumber(), report.semester());
        System.out.println("Course | Type | CIA1 | CIA2 | Coursework | ESE | Internal | External | Total | Grade | Status");
        for (AcademicDAO.CourseResult course : report.courses()) {
            System.out.printf(Locale.ROOT, "%s | %s | %s | %s | %s | %s | %.2f | %.2f | %.2f | %s | %s%n",
                    course.courseCode(), course.courseType(), showMark(course.cia1()), showMark(course.cia2()),
                    showMark(course.coursework()), course.eseAbsent() ? "AB" : showMark(course.ese()),
                    course.internalWeighted(), course.externalWeighted(), course.total(),
                    course.grade(), course.status());
            System.out.printf(Locale.ROOT, "  Attendance: %d/%d (%.2f%%)%s | Credits: %.1f%n",
                    course.classesAttended(), course.classesHeld(), course.attendancePercentage(),
                    course.condonationApproved() ? " | Condonation approved" : "", course.credits());
        }
        System.out.printf(Locale.ROOT, "SGPA: %.2f | Equivalent percentage: %.2f%%%n",
                report.sgpa(), report.percentage());
    }

    private void printDefaulters() throws SQLException {
        int semester = readInt("Semester: ");
        List<AcademicDAO.CourseResult> defaulters = academicDAO.getAttendanceDefaulters(semester);
        System.out.printf("%nATTENDANCE DEFAULTERS - SEMESTER %d (<65%%)%n", semester);
        if (defaulters.isEmpty()) {
            System.out.println("No attendance defaulters.");
            return;
        }
        for (AcademicDAO.CourseResult result : defaulters) {
            System.out.printf(Locale.ROOT, "%s | %s | %s | %s | %.2f%% | Grade SA%n",
                    result.rollNumber(), result.studentName(), result.department(), result.courseCode(),
                    result.attendancePercentage());
        }
    }

    private void printDepartmentSummary() throws SQLException {
        int semester = readInt("Semester: ");
        List<AcademicDAO.DepartmentSummary> summaries = academicDAO.getDepartmentSummaries(semester);
        System.out.printf("%nDEPARTMENT RESULT SUMMARY - SEMESTER %d%n", semester);
        System.out.println("Department | Students passing all courses | Students with non-pass | Average SGPA");
        for (AcademicDAO.DepartmentSummary summary : summaries) {
            System.out.printf(Locale.ROOT, "%s | %d | %d | %.2f%n",
                    summary.department(), summary.passCount(), summary.failCount(), summary.averageSgpa());
        }
        if (summaries.isEmpty()) {
            System.out.println("No enrolled students for this semester.");
        }
    }

    private String prompt(String label) {
        System.out.print(label);
        return input.nextLine().trim();
    }

    private int readInt(String label) {
        return parseNumber(prompt(label), Integer::parseInt, label);
    }

    private double readDouble(String label) {
        return parseNumber(prompt(label), Double::parseDouble, label);
    }

    private <T> T parseNumber(String value, Parser<T> parser, String label) {
        try {
            return parser.parse(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid number for " + label);
        }
    }

    private String showMark(Double mark) {
        return mark == null ? "-" : String.format(Locale.ROOT, "%.2f", mark);
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse(String value);
    }
}
