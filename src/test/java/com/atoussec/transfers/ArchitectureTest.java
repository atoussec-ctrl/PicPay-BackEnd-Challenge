package com.atoussec.transfers;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

@AnalyzeClasses(
    packages = "com.atoussec.transfers",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule DOMAIN_IS_FRAMEWORK_INDEPENDENT =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("..domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta..",
              "..application..",
              "..adapter..",
              "..bootstrap..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("..application..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule INCOMING_ADAPTERS_DO_NOT_DEPEND_ON_OUTGOING_ADAPTERS =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("..adapter.in..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.out..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule OUTGOING_ADAPTERS_DO_NOT_DEPEND_ON_INCOMING_ADAPTERS =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("..adapter.out..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..adapter.in..")
          .allowEmptyShould(true);
}
