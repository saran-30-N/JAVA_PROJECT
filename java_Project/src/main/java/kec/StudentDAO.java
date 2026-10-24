package kec;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class StudentDAO {
    public void addStudent(String rollNumber, String name, String department, int semester) throws SQLException {
        String normalizedRollNumber = rollNumber.trim().toUpperCase();
        String sql = "INSERT INTO students (roll_number, name, department, current_semester) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT(roll_number) DO NOTHING";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedRollNumber);
            statement.setString(2, name.trim());
            statement.setString(3, department.trim().toUpperCase());
            statement.setInt(4, semester);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException(
                        "Roll number " + normalizedRollNumber + " already exists. Enter a unique roll number.");
            }
        }
    }

    public void addCourse(String courseCode, String title, double credits, String courseType) throws SQLException {
        String normalizedCourseCode = courseCode.trim().toUpperCase();
        String sql = "INSERT INTO courses (course_code, title, credits, course_type) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT(course_code) DO NOTHING";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedCourseCode);
            statement.setString(2, title.trim());
            statement.setDouble(3, credits);
            statement.setString(4, courseType.trim().toUpperCase());
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException(
                        "Course code " + normalizedCourseCode + " already exists. Enter a unique course code.");
            }
        }
    }

    public void enrollStudent(String rollNumber, String courseCode, int semester) throws SQLException {
        String sql = "INSERT INTO enrollments (roll_number, course_code, semester) VALUES (?, ?, ?)";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, rollNumber.trim().toUpperCase());
            statement.setString(2, courseCode.trim().toUpperCase());
            statement.setInt(3, semester);
            statement.executeUpdate();
        }
    }
}
