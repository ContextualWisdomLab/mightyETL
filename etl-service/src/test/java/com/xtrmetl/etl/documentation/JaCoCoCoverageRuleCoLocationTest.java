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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that the non-vacuity guard and every zero-missed JaCoCo threshold remain co-located in
 * the same {@code BUNDLE} rule. Keeping the limits together prevents a future refactor from leaving
 * the non-empty guard on one rule while silently moving coverage thresholds elsewhere.
 */
class JaCoCoCoverageRuleCoLocationTest {

    private static final Set<String> REQUIRED_LIMITS = Set.of(
            "CLASS|TOTALCOUNT|min=1",
            "INSTRUCTION|MISSEDCOUNT|max=0",
            "LINE|MISSEDCOUNT|max=0",
            "METHOD|MISSEDCOUNT|max=0",
            "BRANCH|MISSEDCOUNT|max=0"
    );

    @Test
    void durableJobCoverageKeepsEveryRequiredLimitInOneBundleRule() throws Exception {
        Element plugin = jacocoPlugin(parsePom());
        Element execution = execution(plugin, "check-durable-job-coverage");
        Element rules = requireDirectChild(requireDirectChild(execution, "configuration"), "rules");
        Element bundleRule = null;
        for (Element rule : directChildren(rules, "rule")) {
            if ("BUNDLE".equals(directChildText(rule, "element"))) {
                bundleRule = rule;
                break;
            }
        }
        assertNotNull(bundleRule, "Coverage check must contain a BUNDLE rule");

        Set<String> actualLimits = new LinkedHashSet<>();
        for (Element limit : directChildren(requireDirectChild(bundleRule, "limits"), "limit")) {
            String counter = directChildText(limit, "counter");
            String value = directChildText(limit, "value");
            String minimum = directChildText(limit, "minimum");
            String maximum = directChildText(limit, "maximum");
            if (minimum != null) {
                actualLimits.add(counter + "|" + value + "|min=" + minimum);
            }
            if (maximum != null) {
                actualLimits.add(counter + "|" + value + "|max=" + maximum);
            }
        }

        assertEquals(
                REQUIRED_LIMITS,
                actualLimits,
                "The BUNDLE rule must keep its non-empty guard and exact zero-missed thresholds together"
        );
    }

    private static Document parsePom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(projectRoot().resolve("etl-service/pom.xml").toFile());
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

    private static Element execution(Element plugin, String executionId) {
        for (Element execution : directChildren(requireDirectChild(plugin, "executions"), "execution")) {
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
