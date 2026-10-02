package com.alex.voicedevbot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** docs/architecture.md qoidalarini majburlaydi. */
@AnalyzeClasses(
    packages = "com.alex.voicedevbot",
    importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

  private static final String ROOT = "com.alex.voicedevbot";

  @ArchTest
  static final ArchRule domain_depends_only_on_itself_and_jdk =
      classes()
          .that()
          .resideInAPackage(ROOT + ".domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(ROOT + ".domain..", "java..");

  @ArchTest
  static final ArchRule application_depends_only_on_domain_and_jdk =
      classes()
          .that()
          .resideInAPackage(ROOT + ".application..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(ROOT + ".application..", ROOT + ".domain..", "java..");

  @ArchTest
  static final ArchRule core_does_not_know_adapters_or_config =
      noClasses()
          .that()
          .resideInAnyPackage(ROOT + ".domain..", ROOT + ".application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ROOT + ".adapter..", ROOT + ".config..");

  @ArchTest
  static final ArchRule adapters_do_not_depend_on_each_other =
      slices().matching(ROOT + ".adapter.(*).(*)..").should().notDependOnEachOther();

  @ArchTest
  static final ArchRule adapters_do_not_depend_on_config =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".adapter..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ROOT + ".config..");

  @ArchTest
  static final ArchRule spring_is_used_only_in_config =
      noClasses()
          .that()
          .resideOutsideOfPackage(ROOT + ".config..")
          .and()
          .doNotHaveSimpleName("VoiceDevBotApplication")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..");

  @ArchTest static final ArchRule no_field_injection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

  @ArchTest static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

  @ArchTest static final ArchRule no_system_out = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
}
