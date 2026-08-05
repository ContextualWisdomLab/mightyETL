package com.xtrmetl.etl.documentation;

import com.fasterxml.jackson.databind.cfg.PackageVersion;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prevents the Maven dependency graph from returning to Jackson Databind versions affected by
 * the 2026 creator-property authorization-bypass advisories.
 *
 * <p>The root project imports the patched FasterXML BOM before Spring Boot's dependency BOM so
 * Maven's first-declaration precedence keeps the complete Jackson component set on one compatible
 * security line. These tests verify the configured version, the explicit import and its order, and
 * the Jackson Databind version that the test runtime actually resolved.</p>
 */
class JacksonSecurityVersionTest {

    private static final String PATCHED_JACKSON_BOM_VERSION = "2.21.5";

    /**
     * Requires the root Maven project to declare the reviewed patched Jackson BOM version.
     *
     * @throws Exception when the root Maven model cannot be parsed securely
     */
    @Test
    void usesPatchedCompatibleJacksonBom() throws Exception {
        Document document = rootPomDocument();
        Element version = (Element) document.getElementsByTagName("jackson-bom.version").item(0);
        assertNotNull(
                version,
                "The root POM must declare jackson-bom.version so every Jackson module is aligned"
        );
        assertEquals(PATCHED_JACKSON_BOM_VERSION, version.getTextContent().trim());
    }

    /**
     * Requires the explicit Jackson BOM import to precede Spring Boot's broader dependency BOM.
     *
     * <p>Maven uses the first declaration when imported dependency-management entries overlap.
     * Therefore, merely declaring {@code jackson-bom.version} is insufficient unless the Jackson
     * BOM is imported explicitly before Spring Boot's BOM.</p>
     *
     * @throws Exception when the root Maven model cannot be parsed securely
     */
    @Test
    void importsJacksonBomBeforeSpringBootBom() throws Exception {
        Document document = rootPomDocument();
        Element dependencyManagement = (Element) document
                .getElementsByTagName("dependencyManagement")
                .item(0);
        assertNotNull(dependencyManagement, "The root POM must declare dependencyManagement");
        Element dependencies = directChild(dependencyManagement, "dependencies");
        assertNotNull(dependencies, "dependencyManagement must contain dependencies");

        int jacksonBomIndex = -1;
        int springBootBomIndex = -1;
        Element jacksonBom = null;
        int dependencyIndex = 0;
        NodeList children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (!(child instanceof Element dependency)
                    || !"dependency".equals(dependency.getTagName())) {
                continue;
            }
            String groupId = childText(dependency, "groupId");
            String artifactId = childText(dependency, "artifactId");
            if ("com.fasterxml.jackson".equals(groupId)
                    && "jackson-bom".equals(artifactId)) {
                jacksonBomIndex = dependencyIndex;
                jacksonBom = dependency;
            }
            if ("org.springframework.boot".equals(groupId)
                    && "spring-boot-dependencies".equals(artifactId)) {
                springBootBomIndex = dependencyIndex;
            }
            dependencyIndex++;
        }

        assertTrue(jacksonBomIndex >= 0, "The root POM must explicitly import jackson-bom");
        assertTrue(springBootBomIndex >= 0, "The root POM must import Spring Boot dependencies");
        assertTrue(
                jacksonBomIndex < springBootBomIndex,
                "jackson-bom must appear before Spring Boot's BOM to retain Maven precedence"
        );
        assertNotNull(jacksonBom, "The located Jackson BOM dependency must be available");
        assertEquals("${jackson-bom.version}", childText(jacksonBom, "version"));
        assertEquals("pom", childText(jacksonBom, "type"));
        assertEquals("import", childText(jacksonBom, "scope"));
    }

    /**
     * Requires the resolved Jackson Databind artifact to match the reviewed BOM security line.
     */
    @Test
    void resolvesPatchedJacksonDatabindVersion() {
        assertEquals(
                PATCHED_JACKSON_BOM_VERSION,
                PackageVersion.VERSION.toString(),
                "The resolved jackson-databind version must match jackson-bom.version"
        );
    }

    /**
     * Parses the root Maven model with external entities and external schemas disabled.
     *
     * @return securely parsed root Maven document
     * @throws Exception when the root Maven model cannot be parsed
     */
    private static Document rootPomDocument() throws Exception {
        Path rootPom = projectRoot().resolve("pom.xml");
        assertTrue(Files.exists(rootPom), "The root Maven POM must exist");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(rootPom.toFile());
    }

    /**
     * Finds one direct child element by its tag name.
     *
     * @param parent parent element to inspect
     * @param tagName required child tag name
     * @return matching direct child, or {@code null} when absent
     */
    private static Element directChild(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    /**
     * Reads and trims one required direct child value.
     *
     * @param parent dependency element
     * @param tagName child element name
     * @return trimmed child text
     */
    private static String childText(Element parent, String tagName) {
        Element child = directChild(parent, tagName);
        assertNotNull(child, "Expected child element " + tagName);
        return child.getTextContent().trim();
    }

    /**
     * Finds the repository root from either root or module-local Maven execution.
     *
     * @return absolute repository root containing the root Maven project
     * @throws IllegalStateException when no repository or Maven root can be found
     */
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
