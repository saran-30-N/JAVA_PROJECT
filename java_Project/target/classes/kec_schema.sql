PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS students (
    roll_number TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    department TEXT NOT NULL,
    current_semester INTEGER NOT NULL CHECK (current_semester BETWEEN 1 AND 8)
);

CREATE TABLE IF NOT EXISTS courses (
    course_code TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    credits REAL NOT NULL CHECK (credits > 0),
    course_type TEXT NOT NULL CHECK (course_type IN ('THEORY', 'PRACTICAL', 'EMBEDDED'))
);

CREATE TABLE IF NOT EXISTS enrollments (
    roll_number TEXT NOT NULL,
    course_code TEXT NOT NULL,
    semester INTEGER NOT NULL CHECK (semester BETWEEN 1 AND 8),
    classes_held INTEGER NOT NULL DEFAULT 0 CHECK (classes_held >= 0),
    classes_attended INTEGER NOT NULL DEFAULT 0
        CHECK (classes_attended >= 0 AND classes_attended <= classes_held),
    condonation_approved INTEGER NOT NULL DEFAULT 0 CHECK (condonation_approved IN (0, 1)),
    PRIMARY KEY (roll_number, course_code, semester),
    FOREIGN KEY (roll_number) REFERENCES students(roll_number),
    FOREIGN KEY (course_code) REFERENCES courses(course_code)
);

CREATE TABLE IF NOT EXISTS assessment_marks (
    roll_number TEXT NOT NULL,
    course_code TEXT NOT NULL,
    semester INTEGER NOT NULL,
    cia1 REAL CHECK (cia1 BETWEEN 0 AND 100),
    cia2 REAL CHECK (cia2 BETWEEN 0 AND 100),
    coursework REAL CHECK (coursework BETWEEN 0 AND 100),
    ese REAL CHECK (ese BETWEEN 0 AND 100),
    ese_absent INTEGER NOT NULL DEFAULT 0 CHECK (ese_absent IN (0, 1)),
    PRIMARY KEY (roll_number, course_code, semester),
    FOREIGN KEY (roll_number, course_code, semester)
        REFERENCES enrollments(roll_number, course_code, semester)
        ON DELETE CASCADE
);
