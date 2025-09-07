package com.example;

import java.util.List;
import org.smartbit4all.api.org.bean.User;
import org.smartbit4all.core.object.ObjectApi;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import com.aestallon.storageexplorer.springstarter.EnableStorageExplorer;

@SpringBootApplication(exclude = { ErrorMvcAutoConfiguration.class })
@EnableStorageExplorer
public class DemoApplication {
  public static void main(String[] args) { SpringApplication.run(DemoApplication.class, args); }


  @Bean
  CommandLineRunner commandLineRunner(ObjectApi objectApi) {
    return args -> {
      final var schema = "xyz";
      final var users = List.of(
          // User 1-10
          new User().name("Emma").username("emma_johnson1").email("emma.johnson1@email.com").inactive(false).putAttributesItem("department", "engineering"),
      new User().name("Liam").username("liam_smith2").email("liam.smith2@email.com").inactive(true).putAttributesItem("role", "admin"),
      new User().name("Olivia").username("olivia_brown3").email("olivia.brown3@email.com").inactive(false).putAttributesItem("team", "frontend"),
      new User().name("Noah").username("noah_williams4").email("noah.williams4@email.com").inactive(true).putAttributesItem("location", "NYC"),
      new User().name("Ava").username("ava_jones5").email("ava.jones5@email.com").inactive(false).putAttributesItem("level", "senior"),
      new User().name("William").username("william_garcia6").email("william.garcia6@email.com").inactive(true).putAttributesItem("status", "contractor"),
      new User().name("Sophia").username("sophia_miller7").email("sophia.miller7@email.com").inactive(false).putAttributesItem("project", "alpha"),
      new User().name("James").username("james_davis8").email("james.davis8@email.com").inactive(true).putAttributesItem("skill", "java"),
      new User().name("Isabella").username("isabella_rodriguez9").email("isabella.rodriguez9@email.com").inactive(false).putAttributesItem("department", "marketing"),
      new User().name("Benjamin").username("benjamin_martinez10").email("benjamin.martinez10@email.com").inactive(true).putAttributesItem("role", "user"),

      // User 11-20
      new User().name("Emma").username("emma_wilson11").email("emma.wilson11@email.com").inactive(false).putAttributesItem("team", "backend"),
      new User().name("Liam").username("liam_anderson12").email("liam.anderson12@email.com").inactive(true).putAttributesItem("location", "SF"),
      new User().name("Olivia").username("olivia_taylor13").email("olivia.taylor13@email.com").inactive(false).putAttributesItem("level", "junior"),
      new User().name("Noah").username("noah_thomas14").email("noah.thomas14@email.com").inactive(true).putAttributesItem("status", "fulltime"),
      new User().name("Ava").username("ava_hernandez15").email("ava.hernandez15@email.com").inactive(false).putAttributesItem("project", "beta"),
      new User().name("William").username("william_moore16").email("william.moore16@email.com").inactive(true).putAttributesItem("skill", "python"),
      new User().name("Sophia").username("sophia_martin17").email("sophia.martin17@email.com").inactive(false).putAttributesItem("department", "sales"),
      new User().name("James").username("james_jackson18").email("james.jackson18@email.com").inactive(true).putAttributesItem("role", "manager"),
      new User().name("Isabella").username("isabella_thompson19").email("isabella.thompson19@email.com").inactive(false).putAttributesItem("team", "mobile"),
      new User().name("Benjamin").username("benjamin_white20").email("benjamin.white20@email.com").inactive(true).putAttributesItem("location", "LA"),

      // User 21-30
      new User().name("Emma").username("emma_lopez21").email("emma.lopez21@email.com").inactive(false).putAttributesItem("level", "mid"),
      new User().name("Liam").username("liam_lee22").email("liam.lee22@email.com").inactive(true).putAttributesItem("status", "intern"),
      new User().name("Olivia").username("olivia_gonzalez23").email("olivia.gonzalez23@email.com").inactive(false).putAttributesItem("project", "gamma"),
      new User().name("Noah").username("noah_harris24").email("noah.harris24@email.com").inactive(true).putAttributesItem("skill", "javascript"),
      new User().name("Ava").username("ava_clark25").email("ava.clark25@email.com").inactive(false).putAttributesItem("department", "support"),
      new User().name("William").username("william_lewis26").email("william.lewis26@email.com").inactive(true).putAttributesItem("role", "analyst"),
      new User().name("Sophia").username("sophia_robinson27").email("sophia.robinson27@email.com").inactive(false).putAttributesItem("team", "qa"),
      new User().name("James").username("james_walker28").email("james.walker28@email.com").inactive(true).putAttributesItem("location", "Chicago"),
      new User().name("Isabella").username("isabella_perez29").email("isabella.perez29@email.com").inactive(false).putAttributesItem("level", "lead"),
      new User().name("Benjamin").username("benjamin_hall30").email("benjamin.hall30@email.com").inactive(true).putAttributesItem("status", "parttime"),

      // User 31-40
      new User().name("Emma").username("emma_young31").email("emma.young31@email.com").inactive(false).putAttributesItem("project", "delta"),
      new User().name("Liam").username("liam_allen32").email("liam.allen32@email.com").inactive(true).putAttributesItem("skill", "react"),
      new User().name("Olivia").username("olivia_sanchez33").email("olivia.sanchez33@email.com").inactive(false).putAttributesItem("department", "design"),
      new User().name("Noah").username("noah_king34").email("noah.king34@email.com").inactive(true).putAttributesItem("role", "designer"),
      new User().name("Ava").username("ava_wright35").email("ava.wright35@email.com").inactive(false).putAttributesItem("team", "ux"),
      new User().name("William").username("william_scott36").email("william.scott36@email.com").inactive(true).putAttributesItem("location", "Austin"),
      new User().name("Sophia").username("sophia_green37").email("sophia.green37@email.com").inactive(false).putAttributesItem("level", "principal"),
      new User().name("James").username("james_baker38").email("james.baker38@email.com").inactive(true).putAttributesItem("status", "freelancer"),
      new User().name("Isabella").username("isabella_adams39").email("isabella.adams39@email.com").inactive(false).putAttributesItem("project", "epsilon"),
      new User().name("Benjamin").username("benjamin_nelson40").email("benjamin.nelson40@email.com").inactive(true).putAttributesItem("skill", "angular"),

      // User 41-50
      new User().name("Emma").username("emma_hill41").email("emma.hill41@email.com").inactive(false).putAttributesItem("department", "hr"),
      new User().name("Liam").username("liam_ramirez42").email("liam.ramirez42@email.com").inactive(true).putAttributesItem("role", "recruiter"),
      new User().name("Olivia").username("olivia_campbell43").email("olivia.campbell43@email.com").inactive(false).putAttributesItem("team", "talent"),
      new User().name("Noah").username("noah_mitchell44").email("noah.mitchell44@email.com").inactive(true).putAttributesItem("location", "Boston"),
      new User().name("Ava").username("ava_roberts45").email("ava.roberts45@email.com").inactive(false).putAttributesItem("level", "director"),
      new User().name("William").username("william_carter46").email("william.carter46@email.com").inactive(true).putAttributesItem("status", "consultant"),
      new User().name("Sophia").username("sophia_phillips47").email("sophia.phillips47@email.com").inactive(false).putAttributesItem("project", "zeta"),
      new User().name("James").username("james_evans48").email("james.evans48@email.com").inactive(true).putAttributesItem("skill", "nodejs"),
      new User().name("Isabella").username("isabella_turner49").email("isabella.turner49@email.com").inactive(false).putAttributesItem("department", "finance"),
      new User().name("Benjamin").username("benjamin_parker50").email("benjamin.parker50@email.com").inactive(true).putAttributesItem("role", "accountant")

      );
      for (var user : users) {
        objectApi.saveAsNew(schema, user);
      }
    };
  }
}
