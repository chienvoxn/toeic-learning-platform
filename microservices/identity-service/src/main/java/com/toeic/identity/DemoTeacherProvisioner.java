package com.toeic.identity;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Locale;
@Component class DemoTeacherProvisioner implements CommandLineRunner {
 private final IdentityUserRepository repo;private final RbacService rbac;
 @Value("${demo.teacher-email:}") String teacherEmail;
 @Value("${demo.teacher-password:}") String teacherPassword;
 @Value("${demo.admin-email:}") String adminEmail;
 @Value("${demo.admin-password:}") String adminPassword;
 DemoTeacherProvisioner(IdentityUserRepository repo,RbacService rbac){this.repo=repo;this.rbac=rbac;}
 public void run(String... args){provision(teacherEmail,teacherPassword,"TEACHER");provision(adminEmail,adminPassword,"ADMIN");}
 private void provision(String email,String password,String role){
  if(email.isBlank()||password.isBlank())return;
  if(password.length()<8)throw new IllegalStateException("Demo account passwords must be >=8 chars");
  String canonical=email.toLowerCase(Locale.ROOT).trim();
  var user=repo.findByEmailIgnoreCase(canonical).orElseGet(()->{
   IdentityUser created=new IdentityUser(canonical,"Demo "+role,new BCryptPasswordEncoder().encode(password));created.role=role;return repo.saveAndFlush(created);
  });
  rbac.grant(user.userId,role);
 }
}
