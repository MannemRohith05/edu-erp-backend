package com.eduerp.controller;
import com.eduerp.dto.AssessmentDTO; import com.eduerp.entity.*; import com.eduerp.repository.UserRepository; import com.eduerp.service.AssessmentService; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.security.core.userdetails.UserDetails; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/assessments") @RequiredArgsConstructor public class AssessmentController { private final AssessmentService service; private final UserRepository users;
 @PostMapping @PreAuthorize("hasRole('TEACHER')") public AssessmentDTO create(@RequestBody AssessmentDTO dto,@AuthenticationPrincipal UserDetails d){return service.create(dto,current(d).getId());}
 @GetMapping("/course/{courseId}") @PreAuthorize("hasAnyRole('ADMIN','ADMINISTRATOR','TEACHER')") public List<AssessmentDTO> course(@PathVariable Long courseId,@AuthenticationPrincipal UserDetails d){return service.byCourse(courseId,current(d).getId());}
 @PostMapping("/{id}/submit") @PreAuthorize("hasRole('TEACHER')") public AssessmentDTO submit(@PathVariable Long id,@AuthenticationPrincipal UserDetails d){return service.transition(id,AssessmentStatus.SUBMITTED,current(d).getId());}
 @PostMapping("/{id}/approve") @PreAuthorize("hasAnyRole('ADMIN','ADMINISTRATOR')") public AssessmentDTO approve(@PathVariable Long id,@AuthenticationPrincipal UserDetails d){return service.transition(id,AssessmentStatus.APPROVED,current(d).getId());}
 @PostMapping("/{id}/publish") @PreAuthorize("hasAnyRole('ADMIN','ADMINISTRATOR')") public AssessmentDTO publish(@PathVariable Long id,@AuthenticationPrincipal UserDetails d){return service.transition(id,AssessmentStatus.PUBLISHED,current(d).getId());}
 private User current(UserDetails d){return users.findByEmail(d.getUsername()).orElseThrow();}
}
