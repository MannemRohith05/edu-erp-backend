package com.eduerp.repository;

import com.eduerp.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByCourseId(Long courseId);

    List<Schedule> findByDayOfWeek(DayOfWeek dayOfWeek);

    List<Schedule> findByRoom(String room);

    @Query("SELECT s FROM Schedule s WHERE s.course.teacher.id = :teacherId")
    List<Schedule> findByTeacherId(@Param("teacherId") Long teacherId);

    @Query("SELECT s FROM Schedule s JOIN s.course c JOIN c.enrolledStudents st WHERE st.id = :studentId")
    List<Schedule> findByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT s FROM Schedule s WHERE s.course.teacher.id = :teacherId AND s.dayOfWeek = :dayOfWeek")
    List<Schedule> findByTeacherIdAndDayOfWeek(@Param("teacherId") Long teacherId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek);
}
