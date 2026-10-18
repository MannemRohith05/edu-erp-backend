package com.eduerp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDTO {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private Long id;
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String employeeId;
    private String department;
    private String qualification;
    private String specialization;
    private LocalDate joiningDate;
    private List<String> courseCodes;
}
