package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the durable-job production slice bound to an executable, non-empty 100% coverage policy.
 *
 * <p>JaCoCo's agent instrumentation filters and Maven report filters consume different name
 * forms. A plugin-wide dotted include can therefore match neither compiled class-file paths nor
 * the names seen by the agent, creating a report with zero analyzed classes that still satisfies
 * zero-missed rules vacuously. This contract requires unrestricted test instrumentation,
 * execution-specific class-file filters, and an explicit non-empty bundle check before the
 * zero-missed instruction, line, method, and branch rules can pass.</p>
 */
class EtlJobCoveragePolicyTest {

    private static final Set<String> DURABLE_JOB_CLASS_FILES = Set.of(
            "com/xtrmetl/etl/job/*.class",
            "com/xtrmetl/etl/controller/EtlJobController*.class"
    );

    /**
     * Requires the ETL module build to analyze at least one intended production class and fail
     * when any analyzed durable-job path is untested.
     *
     * @throws IOException when the module build descriptor cannot be read
     * @throws ParserConfigurationException when the JDK XML parser cannot be created
     * @throws SAXException when the Maven descriptor is not well-formed XML
     */
    @Test
    void etlModuleEnforcesNonEmptyCompleteCoverageForTheDurableJobSlice()
            throws IOException, ParserConfigurationException, SAXException {
        Document modulePom = parseModulePom();
        Element jacocoPlugin = findPlugin(modulePom, "jacoco-maven-plugin");

        assertEquals("0.8.15", directText(jacocoPlugin, "version"));
        Element pluginConfiguration = directChild(jacocoPlugin, "configuration");
        assertTrue(
                pluginConfiguration == null || directChild(pluginConfiguration, "includes") == null,
                "JaCoCo includes must not be shared across agent and report goals"
        );

        Element prepareExecution = findExecution(jacocoPlugin, "prepare-durable-job-coverage");
        assertEquals("initialize", directText(prepareExecution, "phase"));
        assertTrue(goalNames(prepareExecution).contains("prepare-agent"));
        Element prepareConfiguration = directChild(prepareExecution, "configuration");
        assertTrue(
                prepareConfiguration == null || directChild(prepareConfiguration, "includes") == null,
                "The test agent must instrument all application classes; report filtering is separate"
        );

        Element reportExecution = findExecution(jacocoPlugin, "report-durable-job-coverage");
        assertEquals("test", directText(reportExecution, "phase"));
        assertTrue(goalNames(reportExecution).contains("report"));
        assertEquals(DURABLE_JOB_CLASS_FILES, configuredIncludes(reportExecution));

        Element checkExecution = findExecution(jacocoPlugin, "check-durable-job-coverage");
        assertEquals("test", directText(checkExecution, "phase"));
        assertTrue(goalNames(checkExecution).contains("check"));
        assertEquals(DURABLE_JOB_CLASS_FILES, configuredIncludes(checkExecution));
        assertTrue(hasLimit(checkExecution, "BUNDLE", "INSTRUCTION", "TOTALCOUNT", "minimum", "1"));

        for (String counter : Set.of("INSTRUCTION", "LINE", "METHOD", "BRANCH")) {
            assertTrue(
                    hasLimit(checkExecution, "CLASS", counter, "MISSEDCOUNT", "maximum", "0"),
                    () -> "Missing zero-missed class rule for " + counter
            );
        }

        String serializedPom = Files.readString(projectRoot().resolve("etl-service/pom.xml"));
        assertFalse(serializedPom.contains("<include>com.xtrmetl.etl.job.*</include>"));
        assertFalse(serializedPom.contains(
                "<include>com.xtrmetl.etl.controller.EtlJobController*</include>"
        ));
    }

    private static Document parseModulePom()
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(projectRoot().resolve("etl-service/pom.xml").toFile());
    }

    private static Element findPlugin(Document document, String artifactId) {
        NodeList plugins = document.getElementsByTagName("plugin");
        for (int index = 0; index < plugins.getLength(); index++) {
            Element plugin = (Element) plugins.item(index);
            if (artifactId.equals(directText(plugin, "artifactId"))) {
                return plugin;
            }
        }
        throw new AssertionError("Missing Maven plugin " + artifactId);
    }

    private static Element findExecution(Element plugin, String executionId) {
        NodeList executions = plugin.getElementsByTagName("execution");
        for (int index = 0; index < executions.getLength(); index++) {
            Element execution = (Element) executions.item(index);
            if (executionId.equals(directText(execution, "id"))) {
                return execution;
            }
        }
        throw new AssertionError("Missing JaCoCo execution " + executionId);
    }

    private static Set<String> goalNames(Element execution) {
        Element goals = directChild(execution, "goals");
        assertNotNull(goals, "Every JaCoCo execution must declare goals");
        return directTexts(goals, "goal");
    }

    private static Set<String> configuredIncludes(Element execution) {
        Element configuration = directChild(execution, "configuration");
        assertNotNull(configuration, "Report and check executions require explicit configuration");
        Element includes = directChild(configuration, "includes");
        assertNotNull(includes, "Report and check executions require class-file include patterns");
        return directTexts(includes, "include");
    }

    private static boolean hasLimit(
            Element execution,
            String elementName,
            String counter,
            String value,
            String boundName,
            String boundValue
    ) {
        Element configuration = directChild(execution, "configuration");
        assertNotNull(configuration);
        NodeList rules = configuration.getElementsByTagName("rule");
        for (int ruleIndex = 0; ruleIndex < rules.getLength(); ruleIndex++) {
            Element rule = (Element) rules.item(ruleIndex);
            if (!elementName.equals(directText(rule, "element"))) {
                continue;
            }
            NodeList limits = rule.getElementsByTagName("limit");
            for (int limitIndex = 0; limitIndex < limits.getLength(); limitIndex++) {
                Element limit = (Element) limits.item(limitIndex);
                if (counter.equals(directText(limit, "counter"))
                        && value.equals(directText(limit, "value"))
                        && boundValue.equals(directText(limit, boundName))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> directTexts(Element parent, String childName) {
        Set<String> values = new HashSet<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && childName.equals(element.getTagName())) {
                values.add(element.getTextContent().trim());
            }
        }
        return Set.copyOf(values);
    }

    private static String directText(Element parent, String childName) {
        Element child = directChild(parent, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private static Element directChild(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && childName.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

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
