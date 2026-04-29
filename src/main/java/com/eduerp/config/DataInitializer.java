package com.eduerp.config;

import com.eduerp.entity.*;
import com.eduerp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class DataInitializer implements CommandLineRunner {

        private final UserRepository userRepository;
        private final StudentRepository studentRepository;
        private final TeacherRepository teacherRepository;
        private final CourseRepository courseRepository;
        private final AttendanceRepository attendanceRepository;
        private final GradeRepository gradeRepository;
        private final ScheduleRepository scheduleRepository;
        private final MessageRepository messageRepository;
        private final PasswordEncoder passwordEncoder;

        private static final String[] BRANCHES = { "CSE", "ECE", "CSIT", "EEE", "ME", "CIVIL" };

        // 60 first names
        private static final String[] FIRST_NAMES = {
                "Arjun", "Priya", "Rahul", "Sneha", "Vikram", "Ananya", "Karthik", "Deepika",
                "Rohit", "Pooja", "Aditya", "Kavya", "Suresh", "Lakshmi", "Venkat", "Divya",
                "Harsha", "Meera", "Sanjay", "Nisha", "Ravi", "Swathi", "Pranav", "Anjali",
                "Varun", "Sravani", "Nikhil", "Bhavana", "Tarun", "Keerthi", "Ashwin", "Ramya",
                "Chetan", "Manasa", "Akash", "Sindhu", "Pavan", "Sahithi", "Girish", "Tejaswi",
                "Manoj", "Harika", "Naveen", "Vyshnavi", "Srikar", "Mounika", "Akhil", "Lavanya",
                "Kiran", "Amrutha", "Vamsi", "Deepthi", "Hemanth", "Suchitra", "Rajesh", "Pallavi",
                "Siddharth", "Chinmayi", "Abhinav", "Srujana"
        };

        // 40 last names
        private static final String[] LAST_NAMES = {
                "Reddy", "Sharma", "Kumar", "Patel", "Singh", "Rao", "Gupta", "Naidu",
                "Verma", "Joshi", "Iyer", "Nair", "Menon", "Pillai", "Das", "Shetty",
                "Patil", "Desai", "Kulkarni", "Banerjee", "Mishra", "Saxena", "Agarwal", "Mehta",
                "Bhat", "Hegde", "Choudhary", "Prasad", "Varma", "Murthy", "Rajan", "Sethi",
                "Khanna", "Dubey", "Tiwari", "Pandey", "Sinha", "Jain", "Kapoor", "Chauhan"
        };

        // Teacher qualifications per branch
        private static final Map<String, String[]> QUALIFICATIONS = Map.of(
                "CSE", new String[]{"Ph.D. Computer Science", "M.Tech Computer Science", "Ph.D. AI & ML", "M.Tech Software Engineering", "Ph.D. Data Science"},
                "ECE", new String[]{"Ph.D. Electronics", "M.Tech VLSI Design", "Ph.D. Signal Processing", "M.Tech Embedded Systems", "Ph.D. Communication Systems"},
                "CSIT", new String[]{"Ph.D. Information Technology", "M.Tech Cybersecurity", "Ph.D. Cloud Computing", "M.Tech Data Analytics"},
                "EEE", new String[]{"Ph.D. Electrical Engineering", "M.Tech Power Systems", "Ph.D. Control Systems", "M.Tech Power Electronics"},
                "ME", new String[]{"Ph.D. Mechanical Engineering", "M.Tech Thermal Engineering", "Ph.D. Manufacturing", "M.Tech Design Engineering"},
                "CIVIL", new String[]{"Ph.D. Structural Engineering", "M.Tech Geotechnical", "Ph.D. Transportation Engineering", "M.Tech Environmental Engineering"}
        );

        private static final Map<String, String[]> SPECIALIZATIONS = Map.of(
                "CSE", new String[]{"Data Structures & Algorithms", "Machine Learning", "Database Systems", "Computer Networks", "Software Engineering", "Cybersecurity", "Web Technologies", "Cloud Computing"},
                "ECE", new String[]{"VLSI Design", "Signal Processing", "Analog Communication", "Digital Electronics", "Microprocessors", "Embedded Systems", "IoT"},
                "CSIT", new String[]{"Information Security", "Big Data Analytics", "Cloud Architecture", "Data Mining", "Network Security"},
                "EEE", new String[]{"Power Systems", "Control Systems", "Electrical Machines", "Power Electronics", "Renewable Energy"},
                "ME", new String[]{"Thermodynamics", "Fluid Mechanics", "Manufacturing Technology", "CAD/CAM", "Robotics"},
                "CIVIL", new String[]{"Structural Analysis", "Geotechnical Engineering", "Concrete Technology", "Surveying", "Water Resources"}
        );

        private static final String[] CITIES = {
                "Hyderabad, Telangana", "Visakhapatnam, AP", "Bengaluru, Karnataka", "Chennai, Tamil Nadu",
                "Mumbai, Maharashtra", "Vijayawada, AP", "Warangal, Telangana", "Guntur, AP",
                "Tirupati, AP", "Kurnool, AP", "Rajahmundry, AP", "Kakinada, AP",
                "Nellore, AP", "Nizamabad, Telangana", "Karimnagar, Telangana", "Khammam, Telangana",
                "Pune, Maharashtra", "Kolkata, West Bengal", "Delhi, NCR", "Jaipur, Rajasthan"
        };

        @Override
        @Transactional
        public void run(String... args) {
                if (userRepository.count() > 0) {
                        log.info("Database already contains data. Skipping initialization.");
                        return;
                }

                log.info("🚀 Initializing large demo dataset (~500 users)...");
                Random random = new Random(42);
                String encodedPassword = passwordEncoder.encode("password123");

                // ── 1. Admin ─────────────────────────────────────────────────────
                userRepository.save(User.builder()
                        .email("rohithmannemofficial@gmail.com").password(encodedPassword)
                        .firstName("System").lastName("Admin").role(Role.ADMIN).active(true).build());

                // ── 2. Create Teachers (~50, spread across branches) ─────────────
                // Teachers per branch: CSE=12, ECE=10, CSIT=6, EEE=7, ME=7, CIVIL=7
                int[] teachersPerBranch = {12, 10, 6, 7, 7, 7};
                Map<String, List<Teacher>> branchTeachers = new LinkedHashMap<>();
                int teacherCounter = 1;
                Set<String> usedEmails = new HashSet<>();

                for (int b = 0; b < BRANCHES.length; b++) {
                        String branch = BRANCHES[b];
                        List<Teacher> teachers = new ArrayList<>();
                        String[] quals = QUALIFICATIONS.get(branch);
                        String[] specs = SPECIALIZATIONS.get(branch);

                        for (int t = 0; t < teachersPerBranch[b]; t++) {
                                String firstName = FIRST_NAMES[(teacherCounter * 7 + t * 3) % FIRST_NAMES.length];
                                String lastName = LAST_NAMES[(teacherCounter * 5 + t * 2) % LAST_NAMES.length];
                                String email = generateUniqueEmail(firstName, lastName, "eduerp.com", usedEmails);

                                User user = userRepository.save(User.builder()
                                        .email(email).password(encodedPassword)
                                        .firstName(firstName).lastName(lastName).role(Role.TEACHER).active(true).build());

                                Teacher teacher = teacherRepository.save(Teacher.builder()
                                        .user(user)
                                        .employeeId(String.format("TCH-%03d", teacherCounter))
                                        .department(branch)
                                        .qualification(quals[t % quals.length])
                                        .specialization(specs[t % specs.length])
                                        .joiningDate(LocalDate.of(2015 + random.nextInt(8), 1 + random.nextInt(12), 1 + random.nextInt(28)))
                                        .build());
                                teachers.add(teacher);
                                teacherCounter++;
                        }
                        branchTeachers.put(branch, teachers);
                }
                int totalTeachers = teacherCounter - 1;
                log.info("   👨‍🏫 Created {} teachers across {} branches", totalTeachers, BRANCHES.length);

                // ── 3. Create Courses (per branch, assigned to teachers) ──────────
                Map<String, List<Course>> branchCourses = new LinkedHashMap<>();

                // CSE Courses (Sem 1-8, covering all 4 years)
                branchCourses.put("CSE", createBranchCourses("CSE", branchTeachers.get("CSE"), new String[][]{
                        {"CS101", "Programming in C", "3", "1"}, {"CS102", "Digital Logic Design", "3", "1"},
                        {"CS201", "Object Oriented Programming", "4", "2"}, {"CS202", "Discrete Mathematics", "3", "2"},
                        {"CS301", "Data Structures & Algorithms", "4", "3"}, {"CS302", "Database Management Systems", "4", "3"},
                        {"CS303", "Operating Systems", "3", "3"},
                        {"CS401", "Computer Networks", "3", "4"}, {"CS402", "Software Engineering", "3", "4"},
                        {"CS501", "Machine Learning", "4", "5"}, {"CS502", "Web Technologies", "3", "5"},
                        {"CS503", "Compiler Design", "3", "5"},
                        {"CS601", "Artificial Intelligence", "4", "6"}, {"CS602", "Cloud Computing", "3", "6"},
                        {"CS603", "Information Security", "3", "6"},
                        {"CS701", "Deep Learning", "4", "7"}, {"CS702", "Big Data Analytics", "3", "7"},
                        {"CS801", "Blockchain Technology", "3", "8"}, {"CS802", "Project Work", "6", "8"},
                }));

                branchCourses.put("ECE", createBranchCourses("ECE", branchTeachers.get("ECE"), new String[][]{
                        {"EC101", "Basic Electronics", "3", "1"}, {"EC102", "Circuit Theory", "3", "1"},
                        {"EC201", "Analog Electronics", "4", "2"}, {"EC202", "Network Analysis", "3", "2"},
                        {"EC301", "Signals and Systems", "4", "3"}, {"EC302", "Digital Electronics", "4", "3"},
                        {"EC303", "Analog Communication", "3", "3"},
                        {"EC401", "Electromagnetic Theory", "3", "4"}, {"EC402", "VLSI Design", "4", "4"},
                        {"EC501", "Digital Communication", "3", "5"}, {"EC502", "Microprocessors", "4", "5"},
                        {"EC601", "DSP", "4", "6"}, {"EC602", "Embedded Systems", "3", "6"},
                        {"EC701", "IoT Systems", "3", "7"}, {"EC702", "Antenna Design", "3", "7"},
                        {"EC801", "VLSI Project", "6", "8"},
                }));

                branchCourses.put("CSIT", createBranchCourses("CSIT", branchTeachers.get("CSIT"), new String[][]{
                        {"CI101", "Computer Fundamentals", "3", "1"}, {"CI102", "Programming Basics", "3", "1"},
                        {"CI201", "Data Structures", "4", "2"}, {"CI202", "Web Development", "3", "2"},
                        {"CI301", "Information Security", "4", "3"}, {"CI302", "Cloud Computing", "3", "3"},
                        {"CI401", "Database Administration", "3", "4"}, {"CI402", "DevOps", "3", "4"},
                        {"CI501", "AI for IT", "4", "5"}, {"CI502", "Network Administration", "3", "5"},
                        {"CI601", "Cyber Forensics", "4", "6"}, {"CI701", "IT Project", "6", "7"},
                }));

                branchCourses.put("EEE", createBranchCourses("EEE", branchTeachers.get("EEE"), new String[][]{
                        {"EE101", "Electrical Circuits", "3", "1"}, {"EE102", "Basic Electronics", "3", "1"},
                        {"EE201", "Electromagnetism", "4", "2"}, {"EE202", "Network Theory", "3", "2"},
                        {"EE301", "Power Systems", "4", "3"}, {"EE302", "Control Systems", "4", "3"},
                        {"EE303", "Electrical Machines", "3", "3"},
                        {"EE401", "Power Electronics", "3", "4"}, {"EE402", "Switchgear & Protection", "3", "4"},
                        {"EE501", "Renewable Energy", "4", "5"}, {"EE502", "Smart Grid", "4", "5"},
                        {"EE601", "Electric Vehicles", "3", "6"}, {"EE701", "EEE Project", "6", "7"},
                }));

                branchCourses.put("ME", createBranchCourses("ME", branchTeachers.get("ME"), new String[][]{
                        {"ME101", "Engineering Drawing", "3", "1"}, {"ME102", "Workshop Practice", "2", "1"},
                        {"ME201", "Engineering Mechanics", "4", "2"}, {"ME202", "Material Science", "3", "2"},
                        {"ME301", "Thermodynamics", "4", "3"}, {"ME302", "Fluid Mechanics", "4", "3"},
                        {"ME303", "Manufacturing Technology", "3", "3"},
                        {"ME401", "Strength of Materials", "3", "4"}, {"ME402", "Heat Transfer", "4", "4"},
                        {"ME501", "Machine Design", "3", "5"}, {"ME502", "CAD/CAM", "4", "5"},
                        {"ME601", "Robotics", "3", "6"}, {"ME701", "ME Project", "6", "7"},
                }));

                branchCourses.put("CIVIL", createBranchCourses("CIVIL", branchTeachers.get("CIVIL"), new String[][]{
                        {"CE101", "Engineering Graphics", "3", "1"}, {"CE102", "Engineering Geology", "3", "1"},
                        {"CE201", "Building Materials", "3", "2"}, {"CE202", "Fluid Mechanics", "4", "2"},
                        {"CE301", "Structural Analysis", "4", "3"}, {"CE302", "Geotechnical Engineering", "4", "3"},
                        {"CE303", "Concrete Technology", "3", "3"},
                        {"CE401", "Surveying", "3", "4"}, {"CE402", "Steel Design", "4", "4"},
                        {"CE501", "Transportation Engineering", "3", "5"}, {"CE502", "Earthquake Engineering", "3", "5"},
                        {"CE601", "Environmental Engineering", "3", "6"}, {"CE701", "Civil Project", "6", "7"},
                }));

                int totalCourses = branchCourses.values().stream().mapToInt(List::size).sum();
                log.info("   📚 Created {} courses across {} branches", totalCourses, BRANCHES.length);

                // ── 4. Create Students (~450, distributed across 4 years) ─────
                // Students per branch: CSE=120, ECE=80, CSIT=60, EEE=65, ME=65, CIVIL=60
                // Each branch split: ~30% 1st yr, ~25% 2nd yr, ~25% 3rd yr, ~20% 4th yr
                int[] studentsPerBranch = {120, 80, 60, 65, 65, 60};
                Map<String, List<Student>> branchStudents = new LinkedHashMap<>();
                int studentCounter = 1;

                // Year distribution percentages (1st=30%, 2nd=25%, 3rd=25%, 4th=20%)
                double[] yearPcts = {0.30, 0.25, 0.25, 0.20};
                // Semesters for each year: each year has 2 semesters, student is in even/odd based on current term
                // Assume current term is even semester (2nd, 4th, 6th, 8th)
                int[] currentSemesters = {2, 4, 6, 8}; // 1st yr=sem2, 2nd yr=sem4, 3rd yr=sem6, 4th yr=sem8
                int[] enrollYears = {2025, 2024, 2023, 2022}; // admission years

                for (int b = 0; b < BRANCHES.length; b++) {
                        String branch = BRANCHES[b];
                        List<Student> students = new ArrayList<>();
                        int branchTotal = studentsPerBranch[b];
                        int assigned = 0;

                        for (int year = 0; year < 4; year++) {
                                int count = (year < 3) ? (int) Math.round(branchTotal * yearPcts[year]) : (branchTotal - assigned);
                                int semester = currentSemesters[year];
                                int enrollYear = enrollYears[year];

                                for (int s = 0; s < count; s++) {
                                        String firstName = FIRST_NAMES[(studentCounter * 3 + s) % FIRST_NAMES.length];
                                        String lastName = LAST_NAMES[(studentCounter * 2 + s * 3) % LAST_NAMES.length];
                                        String email = generateUniqueEmail(firstName, lastName, "eduerp.com", usedEmails);

                                        User user = userRepository.save(User.builder()
                                                .email(email).password(encodedPassword)
                                                .firstName(firstName).lastName(lastName).role(Role.STUDENT).active(true).build());

                                        Student student = studentRepository.save(Student.builder()
                                                .user(user)
                                                .studentId(String.format("STU-%d-%03d", enrollYear, studentCounter))
                                                .dateOfBirth(LocalDate.of(2001 + year, 1 + random.nextInt(12), 1 + random.nextInt(28)))
                                                .enrollmentDate(LocalDate.of(enrollYear, 7, 1))
                                                .department(branch)
                                                .semester(semester)
                                                .parentContact("9" + String.format("%09d", 800000000L + random.nextInt(199999999)))
                                                .address(CITIES[random.nextInt(CITIES.length)])
                                                .build());
                                        students.add(student);
                                        studentCounter++;
                                }
                                assigned += count;
                        }
                        branchStudents.put(branch, students);
                }
                int totalStudents = studentCounter - 1;
                log.info("   🎓 Created {} students across {} branches (4 years)", totalStudents, BRANCHES.length);

                // ── 5. Enroll students in courses of their CURRENT semester only ─
                int totalEnrollments = 0;
                for (String branch : BRANCHES) {
                        List<Course> courses = branchCourses.get(branch);
                        List<Student> students = branchStudents.get(branch);

                        for (Course course : courses) {
                                if (course.getEnrolledStudents() == null) {
                                        course.setEnrolledStudents(new HashSet<>());
                                }
                                // Only enroll students whose semester matches the course semester
                                List<Student> eligible = students.stream()
                                        .filter(st -> st.getSemester() == course.getSemester())
                                        .collect(Collectors.toList());
                                course.getEnrolledStudents().addAll(eligible);
                                courseRepository.save(course);
                                totalEnrollments += eligible.size();
                        }
                }
                log.info("   📝 Created {} course enrollments", totalEnrollments);

                // ── 6. Create Schedules for ALL courses ────────────────────────
                DayOfWeek[] days = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY};
                String[][] timeSlots = {{"09:00","10:00"}, {"10:00","11:00"}, {"11:00","12:00"}, {"13:00","14:00"}, {"14:00","15:00"}, {"15:00","16:00"}};
                String[] rooms = {"LH-101","LH-102","LH-103","LH-201","LH-202","LH-203","Lab-301","Lab-302","Lab-303","LH-301","LH-302"};
                String[] buildings = {"Main Block","Science Block","CS Block","ECE Block","Workshop Block"};
                int scheduleCount = 0;

                for (String branch : BRANCHES) {
                        List<Course> courses = branchCourses.get(branch);
                        int slotIdx = 0;
                        for (Course course : courses) {
                                DayOfWeek day1 = days[slotIdx % days.length];
                                DayOfWeek day2 = days[(slotIdx + 2) % days.length];
                                String[] slot = timeSlots[slotIdx % timeSlots.length];
                                String room = rooms[slotIdx % rooms.length];
                                String building = buildings[slotIdx % buildings.length];
                                createSchedule(course, day1, slot[0], slot[1], room, building);
                                createSchedule(course, day2, slot[0], slot[1], room, building);
                                scheduleCount += 2;
                                slotIdx++;
                        }
                }
                log.info("   📅 Created {} schedule entries", scheduleCount);

                // ── 7. Create Attendance (last 15 days for current semester courses) ───
                LocalDate today = LocalDate.now();
                // Use CSE sem 2 (1st year) for attendance demo, since that's the current batch
                List<Course> cseSem3 = branchCourses.get("CSE").stream().filter(c -> c.getSemester() == 2).collect(Collectors.toList());
                List<Student> cseSem3Students = branchStudents.get("CSE").stream().filter(st -> st.getSemester() == 2).limit(36).collect(Collectors.toList());
                int attendanceCount = 0;

                for (int dayOffset = 15; dayOffset >= 1; dayOffset--) {
                        LocalDate date = today.minusDays(dayOffset);
                        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;

                        for (Student student : cseSem3Students) {
                                for (Course course : cseSem3) {
                                        int r = random.nextInt(100);
                                        AttendanceStatus status;
                                        if (r < 78) status = AttendanceStatus.PRESENT;
                                        else if (r < 90) status = AttendanceStatus.LATE;
                                        else status = AttendanceStatus.ABSENT;

                                        attendanceRepository.save(Attendance.builder()
                                                .student(student).course(course).date(date).status(status)
                                                .markedBy(course.getTeacher().getUser())
                                                .markedAt(date.atTime(10, 0)).build());
                                        attendanceCount++;
                                }
                        }
                }
                log.info("   📋 Created {} attendance records", attendanceCount);

                // ── 8. Create Grades for Sem 3 CSE students ──────────────────────
                String[][] gradeTypes = {
                        {"Assignment 1", "ASSIGNMENT", "25"}, {"Quiz 1", "QUIZ", "10"},
                        {"Midterm Exam", "MIDTERM", "50"}, {"Assignment 2", "ASSIGNMENT", "25"},
                        {"Quiz 2", "QUIZ", "10"}, {"Project", "PROJECT", "100"}
                };
                int gradeCount = 0;

                for (Student student : cseSem3Students) {
                        for (Course course : cseSem3) {
                                for (String[] gt : gradeTypes) {
                                        double totalMarks = Double.parseDouble(gt[2]);
                                        double marks = Math.round(totalMarks * (0.45 + random.nextDouble() * 0.50) * 10.0) / 10.0;
                                        double pct = (marks / totalMarks) * 100;
                                        String lg = pct >= 90 ? "A" : pct >= 80 ? "B" : pct >= 70 ? "C" : pct >= 60 ? "D" : "F";

                                        gradeRepository.save(Grade.builder()
                                                .student(student).course(course)
                                                .assignmentName(gt[0]).marks(marks).totalMarks(totalMarks)
                                                .gradeType(gt[1]).letterGrade(lg)
                                                .gradedBy(course.getTeacher().getUser())
                                                .gradedAt(LocalDateTime.now().minusDays(random.nextInt(20) + 1))
                                                .feedback(generateFeedback(lg, random)).build());
                                        gradeCount++;
                                }
                        }
                }
                log.info("   📊 Created {} grade records", gradeCount);

                // ── 9. Sample Messages ───────────────────────────────────────────
                List<Teacher> cseTeachers = branchTeachers.get("CSE");
                List<Student> firstFewStudents = branchStudents.get("CSE").subList(0, Math.min(5, branchStudents.get("CSE").size()));
                for (Student st : firstFewStudents) {
                        Teacher t = cseTeachers.get(random.nextInt(cseTeachers.size()));
                        createMessage(t.getUser(), st.getUser(), "Welcome to " + t.getSpecialization(),
                                "Dear " + st.getUser().getFirstName() + ", welcome to the course. Please review the syllabus.", random.nextBoolean());
                }
                log.info("   ✉️  Created sample messages");

                // ── Summary ──────────────────────────────────────────────────────
                log.info("═══════════════════════════════════════════════════════════");
                log.info("✅ Demo data initialized successfully!");
                log.info("   Total Users:      {} (1 admin + {} teachers + {} students)", 1 + totalTeachers + totalStudents, totalTeachers, totalStudents);
                log.info("   Total Courses:    {}", totalCourses);
                log.info("   Total Enrollments:{}", totalEnrollments);
                log.info("═══════════════════════════════════════════════════════════");
                log.info("   Branch Distribution:");
                for (String branch : BRANCHES) {
                        log.info("     {} → {} teachers, {} students, {} courses",
                                String.format("%-5s", branch),
                                branchTeachers.get(branch).size(),
                                branchStudents.get(branch).size(),
                                branchCourses.get(branch).size());
                }
                log.info("═══════════════════════════════════════════════════════════");
                log.info("   🔑 Login Credentials (all passwords: password123)");
                log.info("   👤 Admin:    rohithmannemofficial@gmail.com");
                log.info("   👨‍🏫 Teachers: {} accounts (e.g. {} )", totalTeachers,
                        branchTeachers.get("CSE").get(0).getUser().getEmail());
                log.info("   🎓 Students: {} accounts (e.g. {} )", totalStudents,
                        branchStudents.get("CSE").get(0).getUser().getEmail());
                log.info("═══════════════════════════════════════════════════════════");
        }

        // ── Helpers ──────────────────────────────────────────────────────────

        private String generateUniqueEmail(String first, String last, String domain, Set<String> used) {
                String base = (first + "." + last).toLowerCase().replaceAll("[^a-z.]", "");
                String email = base + "@" + domain;
                int counter = 1;
                while (used.contains(email)) {
                        email = base + counter + "@" + domain;
                        counter++;
                }
                used.add(email);
                return email;
        }

        private List<Course> createBranchCourses(String branch, List<Teacher> teachers, String[][] courseData) {
                List<Course> courses = new ArrayList<>();
                for (int i = 0; i < courseData.length; i++) {
                        String[] cd = courseData[i];
                        Teacher teacher = teachers.get(i % teachers.size());
                        Course course = courseRepository.save(Course.builder()
                                .courseCode(cd[0])
                                .courseName(cd[1])
                                .description("Comprehensive study of " + cd[1].toLowerCase() + " concepts and practical applications.")
                                .credits(Integer.parseInt(cd[2]))
                                .semester(Integer.parseInt(cd[3]))
                                .department(branch)
                                .teacher(teacher)
                                .build());
                        courses.add(course);
                }
                return courses;
        }

        private void createSchedule(Course course, DayOfWeek day, String start, String end, String room, String building) {
                scheduleRepository.save(Schedule.builder()
                        .course(course).dayOfWeek(day)
                        .startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end))
                        .room(room).building(building).build());
        }

        private void createMessage(User sender, User receiver, String subject, String content, boolean isRead) {
                Message msg = Message.builder()
                        .sender(sender).receiver(receiver)
                        .subject(subject).content(content).isRead(isRead).build();
                if (isRead) msg.setReadAt(LocalDateTime.now().minusDays(1));
                messageRepository.save(msg);
        }

        private String generateFeedback(String lg, Random r) {
                String[][] fb = {
                        {"Excellent work!", "Outstanding!", "Great understanding."},
                        {"Good effort.", "Well done.", "Solid work."},
                        {"Satisfactory.", "Average.", "Acceptable."},
                        {"Below average.", "Needs improvement.", "More effort needed."},
                        {"Poor. Seek help.", "Meet during office hours.", "Improvement needed."}
                };
                int idx = switch (lg) { case "A" -> 0; case "B" -> 1; case "C" -> 2; case "D" -> 3; default -> 4; };
                return fb[idx][r.nextInt(3)];
        }
}
