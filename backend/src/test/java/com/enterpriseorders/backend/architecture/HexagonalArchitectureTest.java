package com.enterpriseorders.backend.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Hexagonal (ports-and-adapters) structure enforcement (A7).
 *
 * <p>Layout per module ({@code order}, {@code tenant}, {@code reporting}):
 * {@code adapter/web} (driving) and {@code adapter/persistence} (driven)
 * talk to {@code application} only through {@code port/input} and
 * {@code port/output}; neutral read/write models live in {@code model/}
 * (order/tenant) or {@code port/output/dto} (reporting, port-local).
 *
 * <p>Evaluated green against the current tree (2026-09-26): every rule below
 * was checked class-by-class; narrower per-module scoping is used wherever a
 * broad rule would hit a legitimate port-local type (e.g. reporting's
 * port-local DTOs, the idempotency JSON codec in the order application).
 */
@AnalyzeClasses(packages = "com.enterpriseorders.backend",
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    // 1a. Driving adapters never touch driven adapters directly.
    @ArchTest
    static final ArchRule webDoesNotDependOnPersistence = noClasses()
            .that().resideInAPackage("..adapter.web..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.persistence..");

    // 1b. Controllers/filters never bypass ports to reach services directly.
    @ArchTest
    static final ArchRule webDoesNotDependOnApplication = noClasses()
            .that().resideInAPackage("..adapter.web..")
            .should().dependOnClassesThat().resideInAPackage("..application..");

    // 2. Application (use cases) never depends on adapters: OrderService and
    // TenantService use TenantContextProvider/ExecutionContext ports only;
    // ReportingService uses ReportingQueryPort/RefreshMaterializedViewsPort/
    // RateLimiterPort only; IdempotentOperationExecutor uses IdempotencyStore/
    // OrderRepository ports plus a Jackson codec (serialization, not a driven
    // adapter) — no reference to any ..adapter.. package exists today.
    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..");

    // 3. Application stays free of container/JDBC APIs: none of OrderService,
    // TenantService, ReportingService or IdempotentOperationExecutor imports
    // org.springframework.jdbc or jakarta.servlet (they use only stereotype
    // and @Transactional from Spring). Transport mapping (HTTP statuses)
    // lives in GlobalExceptionHandler, never in application or persistence.
    // Jackson is deliberately NOT banned here — the executor's JSON payload
    // codec needs it.
    @ArchTest
    static final ArchRule applicationDoesNotTouchContainerApis = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.jdbc..", "jakarta.servlet..");

    // 3b. No application class touches the MDC directly; correlation/actor
    // propagation goes through the execution.ExecutionContext port.
    @ArchTest
    static final ArchRule applicationDoesNotTouchMdc = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().haveSimpleName("MDC");

    // 3c/7. MDC is owned by the execution bridge and the web layer only:
    // MdcSecurityExecutionContext (..execution..), CorrelationFilter and
    // GlobalExceptionHandler (..web..), TenantContextFilter (..adapter.web..).
    // (ArchUnit has no dependOnClassesThat() on the that() side, hence the
    // explicit predicate over direct dependencies.)
    @ArchTest
    static final ArchRule mdcStaysInExecutionAndWeb = classes()
            .that(dependsOnSimpleName("MDC", "depend on org.slf4j.MDC"))
            .should().resideInAnyPackage("..execution..", "..web..", "..adapter.web..");

    // 4a. Ports are web-DTO-free: order ports use order.model.OrderView;
    // tenant ports use tenant.model.NewTenant/TenantView. Scoped per module
    // because reporting's port-local records (port.output.dto) legitimately
    // live under a "..dto.." segment and must not trip this rule.
    @ArchTest
    static final ArchRule orderPortsDoNotUseWebDto = noClasses()
            .that().resideInAPackage("..order.port..")
            .should().dependOnClassesThat().resideInAPackage("..order.dto..");

    @ArchTest
    static final ArchRule tenantPortsDoNotUseWebDto = noClasses()
            .that().resideInAPackage("..tenant.port..")
            .should().dependOnClassesThat().resideInAPackage("..tenant.dto..");

    // 4b. Ports are framework-free: no port imports Spring or Jakarta today
    // (order/tenant ports use model/ + java only; reporting ports use
    // port-local DTOs + java only).
    @ArchTest
    static final ArchRule portsAreFrameworkFree = noClasses()
            .that().resideInAPackage("..port..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..");

    // 5. Only driven adapters touch JDBC: the four DAOs (OrderDao,
    // IdempotencyDao, TenantDao, ReportingDao) all live in
    // ..adapter.persistence..; TenantAwareDataSource (config) works at the
    // DataSource level, not via JdbcTemplate.
    @ArchTest
    static final ArchRule onlyPersistenceAdaptersUseJdbc = classes()
            .that(dependsOnPackagePrefix("org.springframework.jdbc",
                    "depend on org.springframework.jdbc"))
            .should().resideInAPackage("..adapter.persistence..");

    // 6. Driven adapters depend only on ports (+model/exception): no
    // reference to ..adapter.web.. or ..application.. exists in any DAO.
    @ArchTest
    static final ArchRule persistenceDoesNotDependOnWebOrApplication = noClasses()
            .that().resideInAPackage("..adapter.persistence..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..adapter.web..", "..application..");

    // 7. Application never touches web DTOs: use cases take primitives and
    // return model/ records; wire mapping (from()) lives at the web edge.
    // Scoped to order/tenant like rule 4: reporting's port-local DTOs live
    // under ..port.output.dto.. (deliberate, documented above) and must not
    // trip this rule.
    @ArchTest
    static final ArchRule orderApplicationDoesNotDependOnDto = noClasses()
            .that().resideInAPackage("..order.application..")
            .should().dependOnClassesThat().resideInAPackage("..order.dto..");

    @ArchTest
    static final ArchRule tenantApplicationDoesNotDependOnDto = noClasses()
            .that().resideInAPackage("..tenant.application..")
            .should().dependOnClassesThat().resideInAPackage("..tenant.dto..");

    // 8. Read models are framework-free: ..model.. records must not import
    // Spring or Jakarta, so they stay usable by any future adapter.
    @ArchTest
    static final ArchRule modelsAreFrameworkFree = noClasses()
            .that().resideInAPackage("..model..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..");

    // 9. Shared web infrastructure (error bodies, filters) is driving-side
    // plumbing: it must never reach into driven adapters or the application
    // layer directly (that would bypass ports).
    @ArchTest
    static final ArchRule sharedWebDoesNotDependOnApplicationOrPersistence = noClasses()
            .that().resideInAPackage("com.enterpriseorders.backend.web..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..adapter.persistence..", "..application..");

    private static DescribedPredicate<JavaClass> dependsOnSimpleName(
            String simpleName, String description) {
        return new DescribedPredicate<JavaClass>(description) {
            @Override
            public boolean test(JavaClass javaClass) {
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    if (dependency.getTargetClass().getSimpleName().equals(simpleName)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private static DescribedPredicate<JavaClass> dependsOnPackagePrefix(
            String packagePrefix, String description) {
        return new DescribedPredicate<JavaClass>(description) {
            @Override
            public boolean test(JavaClass javaClass) {
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    if (dependency.getTargetClass().getPackageName().startsWith(packagePrefix)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
