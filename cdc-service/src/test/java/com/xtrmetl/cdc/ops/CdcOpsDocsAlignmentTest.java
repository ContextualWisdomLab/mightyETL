package com.xtrmetl.cdc.ops;

import com.xtrmetl.cdc.controller.CdcController;
import com.xtrmetl.cdc.health.CdcEngineHealthIndicator;
import com.xtrmetl.cdc.service.CdcService;
import com.xtrmetl.cdc.service.ReplicationSlotProbe;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gates sale-ready ops honesty: docs/cdc/ops-and-reliability.md must name the same
 * HTTP paths and health component that the shipped controller/health classes expose.
 */
class CdcOpsDocsAlignmentTest {

    private static final String[] DOCUMENTED_API_PATHS = {
            "/api/cdc/status",
            "/api/cdc/sources",
            "/api/cdc/targets",
            "/api/cdc/start",
            "/api/cdc/stop",
    };

    @Test
    void opsDocExistsAndNamesLiveControlPaths() throws Exception {
        Path ops = findProjectRoot().resolve("docs/cdc/ops-and-reliability.md");
        assertTrue(Files.isRegularFile(ops), "ops-and-reliability.md must exist for sale-ready ops evidence");
        String body = new String(Files.readAllBytes(ops), StandardCharsets.UTF_8);

        for (String path : DOCUMENTED_API_PATHS) {
            assertTrue(body.contains(path), "ops doc must document " + path);
        }
        assertTrue(body.contains("cdcEngine"), "ops doc must document cdcEngine health component");
        assertTrue(body.contains("replicationSlot") || body.contains("replication slot"),
                "ops doc must describe replication slot probe");
        assertTrue(body.contains("restartLagBytes") || body.contains("slot lag"),
                "ops doc must describe slot lag fields");
    }

    @Test
    void shippedControllerExposesDocumentedMappings() {
        RequestMapping base = CdcController.class.getAnnotation(RequestMapping.class);
        assertEquals("/api/cdc", base.value()[0]);

        Set<String> getPaths = mappingPaths(CdcController.class, GetMapping.class);
        Set<String> postPaths = mappingPaths(CdcController.class, PostMapping.class);

        assertTrue(getPaths.contains("/status"), "GET /status");
        assertTrue(getPaths.contains("/sources"), "GET /sources");
        assertTrue(getPaths.contains("/targets"), "GET /targets");
        assertTrue(postPaths.contains("/start"), "POST /start");
        assertTrue(postPaths.contains("/stop"), "POST /stop");
    }

    @Test
    void cdcEngineHealthIndicatorIsShippedHealthIndicatorNamedCdcEngine() {
        org.springframework.stereotype.Component component =
                CdcEngineHealthIndicator.class.getAnnotation(org.springframework.stereotype.Component.class);
        assertEquals("cdcEngine", component.value());
        assertTrue(HealthIndicator.class.isAssignableFrom(CdcEngineHealthIndicator.class));
        // Production types only — constructor must accept shipped collaborators.
        assertEquals(2, CdcEngineHealthIndicator.class.getConstructors()[0].getParameterCount());
        assertEquals(CdcService.class, CdcEngineHealthIndicator.class.getConstructors()[0].getParameterTypes()[0]);
        assertEquals(ReplicationSlotProbe.class, CdcEngineHealthIndicator.class.getConstructors()[0].getParameterTypes()[1]);
    }

    private static Set<String> mappingPaths(Class<?> type, Class<? extends java.lang.annotation.Annotation> mapping) {
        Set<String> paths = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (mapping == GetMapping.class && method.isAnnotationPresent(GetMapping.class)) {
                paths.addAll(Arrays.asList(method.getAnnotation(GetMapping.class).value()));
            }
            if (mapping == PostMapping.class && method.isAnnotationPresent(PostMapping.class)) {
                paths.addAll(Arrays.asList(method.getAnnotation(PostMapping.class).value()));
            }
        }
        return paths;
    }

    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path lastPom = null;
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            if (Files.exists(current.resolve("pom.xml"))) {
                lastPom = current;
            }
            current = current.getParent();
        }
        if (lastPom != null) {
            return lastPom;
        }
        throw new IllegalStateException("project root not found");
    }
}
