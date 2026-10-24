package kec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicDAO {
    public enum Assessment {
        CIA1("cia1"),
        CIA2("cia2"),
        COURSEWORK("coursework"),
        ESE("ese");

        private final String column;

        Assessment(String column) {
            this.column = column;
        }
    }

    public record CourseResult(String rollNumber, String studentName, String department, int semester,
                               String courseCode, String courseTitle, double credits, String courseType,
                               int classesHeld, int classesAttended, double attendancePercentage,
                               boolean condonationApproved, Double cia1, Double cia2, Double coursework,
                               Double ese, boolean eseAbsent, double internalWeighted, double externalWeighted,
                               double total, String grade, String status, int gradePoints) {
    }

    public record StudentResult(String rollNumber, String studentName, String department, int semester,
                                double sgpa, double percentage, List<CourseResult> courses) {
    }

    public record DepartmentSummary(String department, int passCount, int failCount, double averageSgpa) {
    }

    public void updateAssessment(String rollNumber, String courseCode, int semester, Assessment assessment,
                                Double score, boolean absent) throws SQLException {
        if (assessment != Assessment.ESE && absent) {
            throw new IllegalArgumentException("Absence can only be recorded for the ESE.");
        }
        if (score != null && (score < 0 || score > 100)) {
            throw new IllegalArgumentException("Marks must be between 0 and 100.");
        }
        if (assessment == Assessment.ESE && absent && score != null) {
            throw new IllegalArgumentException("An absent ESE cannot also have a mark.");
        }

        String sql = "INSERT INTO assessment_marks (roll_number, course_code, semester, " + assessment.column
                + ", ese_absent) VALUES (?, ?, ?, ?, ?) ON CONFLICT (roll_number, course_code, semester) "
                + "DO UPDATE SET " + assessment.column + " = excluded." + assessment.column
                + (assessment == Assessment.ESE ? ", ese_absent = excluded.ese_absent" : "");
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase());
            statement.setString(2, courseCode.trim().toUpperCase());
            statement.setInt(3, semester);
            if (score == null) {
                statement.setNull(4, java.sql.Types.REAL);
            } else {
                statement.setDouble(4, score);
            }
            statement.setInt(5, assessment == Assessment.ESE && absent ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public void recordAttendance(String rollNumber, String courseCode, int semester,
                                 int classesHeld, int classesAttended) throws SQLException {
        if (classesHeld <= 0 || classesAttended < 0 || classesAttended > classesHeld) {
            throw new IllegalArgumentException("Enter a positive number of held classes and attendance from 0 to held.");
        }
        String sql = "UPDATE enrollments SET classes_held = classes_held + ?, "
                + "classes_attended = classes_attended + ? "
                + "WHERE roll_number = ? AND course_code = ? AND semester = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, classesHeld);
            statement.setInt(2, classesAttended);
            statement.setString(3, rollNumber.trim().toUpperCase());
            statement.setString(4, courseCode.trim().toUpperCase());
            statement.setInt(5, semester);
            requireUpdatedEnrollment(statement.executeUpdate());
        }
    }

    public void setCondonationApproval(String rollNumber, String courseCode, int semester,
                                       boolean approved) throws SQLException {
        String sql = "UPDATE enrollments SET condonation_approved = ? "
                + "WHERE roll_number = ? AND course_code = ? AND semester = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, approved ? 1 : 0);
            statement.setString(2, rollNumber.trim().toUpperCase());
            statement.setString(3, courseCode.trim().toUpperCase());
            statement.setInt(4, semester);
            requireUpdatedEnrollment(statement.executeUpdate());
        }
    }

    public StudentResult getStudentReport(String rollNumber, int semester) throws SQLException {
        List<CourseResult> results = queryResults(semester, rollNumber.trim().toUpperCase(), false);
        if (results.isEmpty()) {
            throw new IllegalArgumentException("No courses are enrolled for that student and semester.");
        }
        return summarizeStudent(results);
    }

    public List<CourseResult> getAttendanceDefaulters(int semester) throws SQLException {
        return queryResults(semester, null, true);
    }

    public List<DepartmentSummary> getDepartmentSummaries(int semester) throws SQLException {
        List<CourseResult> results = queryResults(semester, null, false);
        Map<String, List<CourseResult>> byStudent = new LinkedHashMap<>();
        for (CourseResult result : results) {
            byStudent.computeIfAbsent(result.department() + "\u0000" + result.rollNumber(),
                    ignored -> new ArrayList<>()).add(result);
        }

        Map<String, List<StudentResult>> byDepartment = new LinkedHashMap<>();
        for (List<CourseResult> studentCourses : byStudent.values()) {
            StudentResult student = summarizeStudent(studentCourses);
            byDepartment.computeIfAbsent(student.department(), ignored -> new ArrayList<>()).add(student);
        }

        List<DepartmentSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<StudentResult>> entry : byDepartment.entrySet()) {
            int passes = 0;
            double sgpaSum = 0;
            for (StudentResult student : entry.getValue()) {
                boolean passed = student.courses().stream().allMatch(course -> course.status().equals("PASS"));
                if (passed) {
                    passes++;
                }
                sgpaSum += student.sgpa();
            }
            int studentCount = entry.getValue().size();
            summaries.add(new DepartmentSummary(entry.getKey(), passes, studentCount - passes,
                    sgpaSum / studentCount));
        }
        summaries.sort(Comparator.comparing(DepartmentSummary::department));
        return summaries;
    }

    private List<CourseResult> queryResults(int semester, String rollNumber, boolean defaultersOnly)
            throws SQLException {
        String sql = """
                SELECT s.roll_number, s.name, s.department, e.semester,
                       c.course_code, c.title, c.credits, c.course_type,
                       e.classes_held, e.classes_attended, e.condonation_approved,
                       m.cia1, m.cia2, m.coursework, m.ese, COALESCE(m.ese_absent, 0) AS ese_absent
                FROM enrollments e
                JOIN students s ON s.roll_number = e.roll_number
                JOIN courses c ON c.course_code = e.course_code
                LEFT JOIN assessment_marks m ON m.roll_number = e.roll_number
                    AND m.course_code = e.course_code AND m.semester = e.semester
                WHERE e.semester = ? AND (? IS NULL OR s.roll_number = ?)
                ORDER BY s.department, s.roll_number, c.course_code
                """;
        List<CourseResult> results = new ArrayList<>();
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, semester);
            statement.setString(2, rollNumber);
            statement.setString(3, rollNumber);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    CourseResult result = evaluate(row);
                    if (!defaultersOnly || result.attendancePercentage() < 65.0) {
                        results.add(result);
                    }
                }
            }
        }
        return results;
    }

    private CourseResult evaluate(ResultSet row) throws SQLException {
        int classesHeld = row.getInt("classes_held");
        int classesAttended = row.getInt("classes_attended");
        double attendance = classesHeld == 0 ? 0 : classesAttended * 100.0 / classesHeld;
        boolean condonation = row.getInt("condonation_approved") == 1;
        Double cia1 = nullableDouble(row, "cia1");
        Double cia2 = nullableDouble(row, "cia2");
        Double coursework = nullableDouble(row, "coursework");
        Double ese = nullableDouble(row, "ese");
        boolean absent = row.getInt("ese_absent") == 1;
        String type = row.getString("course_type");
        double ciaWeight = switch (type) {
            case "THEORY" -> 0.40;
            case "PRACTICAL" -> 0.60;
            case "EMBEDDED" -> 0.50;
            default -> throw new SQLException("Unsupported course type in database: " + type);
        };
        double eseWeight = 1.0 - ciaWeight;
        double internalAverage = averagePresent(cia1, cia2, coursework);
        double internalWeighted = internalAverage * ciaWeight;
        double externalWeighted = ese == null ? 0 : ese * eseWeight;
        double total = internalWeighted + externalWeighted;

        String grade;
        String status;
        int gradePoints;
        if (attendance < 65.0 || (attendance < 75.0 && !condonation)) {
            grade = "SA";
            status = "SHORTAGE OF ATTENDANCE";
            gradePoints = 0;
        } else if (absent) {
            grade = "AB";
            status = "ABSENT";
            gradePoints = 0;
        } else if (ese == null) {
            grade = "PENDING";
            status = "PENDING ESE";
            gradePoints = 0;
        } else {
            double eseMinimum = switch (type) {
                case "THEORY" -> 27.0;
                case "PRACTICAL" -> 20.0;
                case "EMBEDDED" -> 22.5;
                default -> throw new SQLException("Unsupported course type in database: " + type);
            };
            if (externalWeighted < eseMinimum || total < 50.0) {
                grade = "RA";
                status = "FAIL";
                gradePoints = 0;
            } else if (total >= 91.0) {
                grade = "O";
                status = "PASS";
                gradePoints = 10;
            } else if (total >= 81.0) {
                grade = "A+";
                status = "PASS";
                gradePoints = 9;
            } else if (total >= 71.0) {
                grade = "A";
                status = "PASS";
                gradePoints = 8;
            } else if (total >= 61.0) {
                grade = "B+";
                status = "PASS";
                gradePoints = 7;
            } else {
                grade = "B";
                status = "PASS";
                gradePoints = 6;
            }
        }

        return new CourseResult(row.getString("roll_number"), row.getString("name"), row.getString("department"),
                row.getInt("semester"), row.getString("course_code"), row.getString("title"),
                row.getDouble("credits"), type, classesHeld, classesAttended, attendance, condonation,
                cia1, cia2, coursework, ese, absent, internalWeighted, externalWeighted, total, grade, status,
                gradePoints);
    }

    private StudentResult summarizeStudent(List<CourseResult> results) {
        CourseResult first = results.get(0);
        double weightedPoints = 0;
        double credits = 0;
        for (CourseResult result : results) {
            weightedPoints += result.credits() * result.gradePoints();
            credits += result.credits();
        }
        double sgpa = credits == 0 ? 0 : weightedPoints / credits;
        return new StudentResult(first.rollNumber(), first.studentName(), first.department(), first.semester(),
                sgpa, sgpa * 10, List.copyOf(results));
    }

    private static double averagePresent(Double... values) {
        double sum = 0;
        int count = 0;
        for (Double value : values) {
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private static Double nullableDouble(ResultSet row, String column) throws SQLException {
        double value = row.getDouble(column);
        return row.wasNull() ? null : value;
    }

    private static void requireUpdatedEnrollment(int updated) {
        if (updated == 0) {
            throw new IllegalArgumentException("Enrollment not found. Enroll the student in the course first.");
        }
    }
}
