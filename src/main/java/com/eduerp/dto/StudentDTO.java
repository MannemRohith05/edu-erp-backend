package com.eduerp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentDTO {
    private Long id;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String studentId;
    private LocalDate dateOfBirth;
    private LocalDate enrollmentDate;
    private String department;
    private Integer semester;
    private String parentContact;
    private String address;
    private List<String> courseCodes;
}
