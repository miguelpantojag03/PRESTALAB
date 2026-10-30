package com.prestalab.arquitectura;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hace cumplir D7: las únicas dependencias entre paquetes de com.prestalab son las 15 del diagrama,
 * más las internas de domain (model → enums, model → exception).
 */
@AnalyzeClasses(packages = "com.prestalab", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    private static final String RAIZ = "com.prestalab.";

    /** Las clases de {@code paquete} solo pueden depender de sí mismas, de los paquetes permitidos y de librerías. */
    private static ArchRule soloDependeDe(String paquete, String... permitidos) {
        String[] paquetes = Stream.concat(Stream.of(paquete), Arrays.stream(permitidos))
                .map(p -> RAIZ + p + "..")
                .toArray(String[]::new);
        return classes().that().resideInAPackage(RAIZ + paquete + "..")
                .should().onlyDependOnClassesThat(
                        resideOutsideOfPackage("com.prestalab..").or(resideInAnyPackage(paquetes)))
                .as(paquete + " solo depende de " + (permitidos.length == 0 ? "sí mismo" : String.join(", ", permitidos)))
                .allowEmptyShould(false);
    }

    // ---- Las 15 dependencias de D7 (config no depende de ningún paquete de la app) ----
    @ArchTest
    static final ArchRule controller = soloDependeDe("controller", "service", "dto", "domain.enums");
    @ArchTest
    static final ArchRule service = soloDependeDe("service", "domain", "repository", "dto", "mapper", "notification");
    @ArchTest
    static final ArchRule repository = soloDependeDe("repository", "domain");
    @ArchTest
    static final ArchRule mapper = soloDependeDe("mapper", "domain", "dto");
    @ArchTest
    static final ArchRule notification = soloDependeDe("notification", "domain");
    @ArchTest
    static final ArchRule scheduler = soloDependeDe("scheduler", "service");
    @ArchTest
    static final ArchRule webError = soloDependeDe("web.error", "domain.exception");
    @ArchTest
    static final ArchRule config = soloDependeDe("config");
    @ArchTest
    static final ArchRule dto = soloDependeDe("dto", "domain.enums");

    // ---- Dependencias internas de domain ----
    @ArchTest
    static final ArchRule domainModel = soloDependeDe("domain.model", "domain.enums", "domain.exception");
    @ArchTest
    static final ArchRule domainEnums = soloDependeDe("domain.enums");
    @ArchTest
    static final ArchRule domainException = soloDependeDe("domain.exception");

    @ArchTest
    static final ArchRule dominioSinSpring = classes().that().resideInAPackage("com.prestalab.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "jakarta.persistence..", "com.prestalab.domain..")
            .as("domain no depende de Spring ni de otro paquete de la app (solo jakarta.persistence)");

    @ArchTest
    static final ArchRule controllerSinRepositorioNiModeloNiExcepciones = noClasses()
            .that().resideInAPackage("com.prestalab.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.prestalab.repository..", "com.prestalab.domain.model..", "com.prestalab.domain.exception..")
            .as("controller NO accede a repository, domain.model ni domain.exception");

    @ArchTest
    static final ArchRule controllerSoloUsaInterfacesDeServicio = noClasses()
            .that().resideInAPackage("com.prestalab.controller..")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("ServiceImpl")
            .as("los controladores solo dependen de interfaces de servicio");

    @ArchTest
    static final ArchRule implementacionesEnServiceConSuInterfaz = classes()
            .that().haveSimpleNameEndingWith("ServiceImpl")
            .should().resideInAPackage("com.prestalab.service")
            .andShould(implementarSuInterfaz());

    @ArchTest
    static final ArchRule prestamoServiceUsaElPuerto = classes()
            .that().haveFullyQualifiedName("com.prestalab.service.PrestamoServiceImpl")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.prestalab.notification.Notificador");

    @ArchTest
    static final ArchRule nadieEnServiceConoceElAdaptador = noClasses()
            .that().resideInAPackage("com.prestalab.service..")
            .should().dependOnClassesThat().haveFullyQualifiedName("com.prestalab.notification.EmailNotificador")
            .as("PrestamoServiceImpl depende de Notificador, nunca de EmailNotificador");

    @ArchTest
    static final ArchRule soloControladoresRest = noClasses()
            .should().beAnnotatedWith(org.springframework.stereotype.Controller.class)
            .as("no existe ningún @Controller: solo @RestController (API REST sin vistas)");

    @ArchTest
    static final ArchRule controladoresSonRest = classes()
            .that().resideInAPackage("com.prestalab.controller..")
            .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class);

    private static ArchCondition<JavaClass> implementarSuInterfaz() {
        return new ArchCondition<>("implementar la interfaz de servicio con el mismo nombre sin Impl") {
            @Override
            public void check(JavaClass clase, ConditionEvents eventos) {
                String interfaz = clase.getName().replaceFirst("Impl$", "");
                boolean implementa = clase.getRawInterfaces().stream()
                        .anyMatch(i -> i.getName().equals(interfaz) && i.isInterface());
                if (!implementa) {
                    eventos.add(SimpleConditionEvent.violated(clase, clase.getName() + " no implementa " + interfaz));
                }
            }
        };
    }

    /** Lista blanca: no existe ninguna clase fuera de las del diccionario canónico (sección 3). */
    @Test
    void soloExistenLasClasesDelDiccionario() {
        JavaClasses importadas = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.prestalab");

        Set<String> reales = importadas.stream()
                .filter(JavaClass::isTopLevelClass)
                .map(JavaClass::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> diccionario = new TreeSet<>(Set.of(
                "com.prestalab.PrestalabApplication",
                "com.prestalab.domain.model.Usuario",
                "com.prestalab.domain.model.Equipo",
                "com.prestalab.domain.model.Prestamo",
                "com.prestalab.domain.enums.Rol",
                "com.prestalab.domain.enums.CategoriaEquipo",
                "com.prestalab.domain.enums.EstadoEquipo",
                "com.prestalab.domain.enums.EstadoPrestamo",
                "com.prestalab.domain.exception.ReglaNegocioException",
                "com.prestalab.domain.exception.TransicionInvalidaException",
                "com.prestalab.domain.exception.RecursoNoEncontradoException",
                "com.prestalab.repository.UsuarioRepository",
                "com.prestalab.repository.EquipoRepository",
                "com.prestalab.repository.PrestamoRepository",
                "com.prestalab.dto.RegistroUsuarioDTO",
                "com.prestalab.dto.LoginDTO",
                "com.prestalab.dto.TokenDTO",
                "com.prestalab.dto.CambioEstadoEquipoDTO",
                "com.prestalab.dto.RechazoPrestamoDTO",
                "com.prestalab.dto.UsuarioDTO",
                "com.prestalab.dto.EquipoDTO",
                "com.prestalab.dto.EquipoFormDTO",
                "com.prestalab.dto.SolicitudPrestamoDTO",
                "com.prestalab.dto.PrestamoDTO",
                "com.prestalab.dto.ResumenDashboardDTO",
                "com.prestalab.mapper.UsuarioMapper",
                "com.prestalab.mapper.EquipoMapper",
                "com.prestalab.mapper.PrestamoMapper",
                "com.prestalab.notification.Notificador",
                "com.prestalab.notification.EmailNotificador",
                "com.prestalab.service.UsuarioService",
                "com.prestalab.service.UsuarioServiceImpl",
                "com.prestalab.service.EquipoService",
                "com.prestalab.service.EquipoServiceImpl",
                "com.prestalab.service.PrestamoService",
                "com.prestalab.service.PrestamoServiceImpl",
                "com.prestalab.service.DashboardService",
                "com.prestalab.service.DashboardServiceImpl",
                "com.prestalab.scheduler.VencimientoScheduler",
                "com.prestalab.scheduler.RecordatorioScheduler",
                "com.prestalab.config.SecurityConfig",
                "com.prestalab.web.error.GlobalExceptionHandler",
                "com.prestalab.controller.AuthController",
                "com.prestalab.controller.EquipoController",
                "com.prestalab.controller.PrestamoController",
                "com.prestalab.controller.DashboardController",
                "com.prestalab.controller.UsuarioController"));

        assertThat(reales).isEqualTo(diccionario);
    }
}
