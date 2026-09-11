package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the JaCoCo durable-job coverage gate against an empty production-class selection that can
 * otherwise satisfy zero-missed counters vacuously.
 */
class JaCoCoCoverageConfigurationTest {

    private static final Path PROJECT_ROOT = projectRoot();
    private static final Set<String> EXPECTED_CLASS_FILE_PATTERNS = Set.of(
            "com/xtrmetl/etl/job/*.class",
            "com/xtrmetl/etl/controller/EtlJobController*.class"
    );

    @Test
    void reportAndCheckSelectCompiledClassFilesInsteadOfReusingAgentClassNames() throws Exception {
        Element plugin = jacocoPlugin(parsePom());
        Element pluginConfiguration = directChild(plugin, "configuration");
        assertTrue(
                pluginConfiguration == null || directChild(pluginConfiguration, "includes") == null,
                "Plugin-level includes leak one filter syntax into prepare-agent, report, and check"
        );

        assertEquals(
                EXPECTED_CLASS_FILE_PATTERNS,
                executionIncludes(plugin, "report-durable-job-coverage")
        );
        assertEquals(
                EXPECTED_CLASS_FILE_PATTERNS,
                executionIncludes(plugin, "check-durable-job-coverage")
        );
    }

    @Test
    void coverageCheckRequiresAtLeastOneAnalyzedProductionClass() throws Exception {
        Element plugin = jacocoPlugin(parsePom());
        Element checkExecution = execution(plugin, "check-durable-job-coverage");
        Element configuration = requireDirectChild(checkExecution, "configuration");
        Element rules = requireDirectChild(configuration, "rules");
        Element rule = requireDirectChild(rules, "rule");

        assertEquals("BUNDLE", directChildText(rule, "element"));

        Element limits = requireDirectChild(rule, "limits");
        boolean foundNonEmptyGuard = false;
        for (Element limit : directChildren(limits, "limit")) {
            if ("CLASS".equals(directChildText(limit, "counter"))
                    && "TOTALCOUNT".equals(directChildText(limit, "value"))
                    && "1".equals(directChildText(limit, "minimum"))) {
                foundNonEmptyGuard = true;
            }
        }
        assertTrue(
                foundNonEmptyGuard,
                "Coverage must fail closed when the selected production bundle contains zero classes"
        );
    }

    @Test
    void intendedCoverageTargetContainsCompiledProductionClasses() {
        Path classes = PROJECT_ROOT.resolve("etl-service/target/classes");
        assertTrue(
                Files.isRegularFile(classes.resolve("com/xtrmetl/etl/job/EtlJobService.class")),
                "EtlJobService must be a real compiled production coverage target"
        );
        assertTrue(
                Files.isRegularFile(classes.resolve("com/xtrmetl/etl/controller/EtlJobController.class")),
                "EtlJobController must be a real compiled production coverage target"
        );
    }

    private static Document parsePom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(PROJECT_ROOT.resolve("etl-service/pom.xml").toFile());
    }

    private static Element jacocoPlugin(Document document) {
        NodeList plugins = document.getElementsByTagName("plugin");
        for (int index = 0; index < plugins.getLength(); index++) {
            Element plugin = (Element) plugins.item(index);
            if ("org.jacoco".equals(directChildText(plugin, "groupId"))
                    && "jacoco-maven-plugin".equals(directChildText(plugin, "artifactId"))) {
                return plugin;
            }
        }
        throw new AssertionError("etl-service POM is missing jacoco-maven-plugin");
    }

    private static Set<String> executionIncludes(Element plugin, String executionId) {
        Element configuration = requireDirectChild(execution(plugin, executionId), "configuration");
        Element includes = requireDirectChild(configuration, "includes");
        Set<String> values = new LinkedHashSet<>();
        for (Element include : directChildren(includes, "include")) {
            values.add(include.getTextContent().trim());
        }
        assertFalse(values.isEmpty(), "JaCoCo class-file selection must not be empty");
        return values;
    }

    private static Element execution(Element plugin, String executionId) {
        Element executions = requireDirectChild(plugin, "executions");
        for (Element execution : directChildren(executions, "execution")) {
            if (executionId.equals(directChildText(execution, "id"))) {
                return execution;
            }
        }
        throw new AssertionError("Missing JaCoCo execution: " + executionId);
    }

    private static Element requireDirectChild(Element parent, String name) {
        Element child = directChild(parent, name);
        assertNotNull(child, () -> "Missing <" + name + "> under <" + parent.getTagName() + ">");
        return child;
    }

    private static Element directChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static Set<Element> directChildren(Element parent, String name) {
        Set<Element> matches = new LinkedHashSet<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static String directChildText(Element parent, String name) {
        Element child = directChild(parent, name);
        return child == null ? null : child.getTextContent().trim();
    }

    /** Finds the repository root from root- or module-scoped Maven execution. */
    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path lastPomParent = null;
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            if (Files.exists(current.resolve("pom.xml"))) {
                lastPomParent = current;
            }
            current = current.getParent();
        }
        if (lastPomParent != null) {
            return lastPomParent;
        }
        throw new IllegalStateException("Could not find project root");
    }
}
