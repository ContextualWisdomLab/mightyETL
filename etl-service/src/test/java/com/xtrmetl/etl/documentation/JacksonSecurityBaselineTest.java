package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shared Maven dependency-management boundary against Jackson Databind versions below
 * the selected patched 2.21.5 security baseline.
 */
class JacksonSecurityBaselineTest {

    private static final Path PROJECT_ROOT = projectRoot();

    @Test
    void jacksonSecurityBomPrecedesImportedSpringBootDependencyManagement() throws IOException {
        String pom = Files.readString(PROJECT_ROOT.resolve("pom.xml"), StandardCharsets.UTF_8);

        assertJacksonBomContract(pom);
    }

    @Test
    void rejectsJacksonBomDecoysOutsideRootDependencyManagement() {
        String pomWithDecoy = """
                <project>
                  <properties>
                    <jackson-bom.version>2.21.5</jackson-bom.version>
                  </properties>
                  <!-- <artifactId>jackson-bom</artifactId> -->
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <artifactId>spring-boot-dependencies</artifactId>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """;

        assertThrows(AssertionError.class, () -> assertJacksonBomContract(pomWithDecoy));
    }

    private static void assertJacksonBomContract(String pom) {
        Element project = parseProject(pom);
        Element properties = requireDirectChild(project, "properties");
        assertEquals(
                "2.21.5",
                requireDirectChild(properties, "jackson-bom.version").getTextContent().trim(),
                "Root properties must pin Jackson 2.21.5, the current patched 2.21 LTS baseline"
        );

        Element dependencyManagement = requireDirectChild(project, "dependencyManagement");
        Element dependencies = requireDirectChild(dependencyManagement, "dependencies");
        List<Element> managedDependencies = directChildren(dependencies, "dependency");

        int jacksonIndex = -1;
        int springBootIndex = -1;
        int jacksonCount = 0;
        for (int index = 0; index < managedDependencies.size(); index++) {
            Element dependency = managedDependencies.get(index);
            String artifactId = directChildText(dependency, "artifactId");
            if ("jackson-bom".equals(artifactId)) {
                jacksonCount++;
                jacksonIndex = index;
                assertEquals("com.fasterxml.jackson", directChildText(dependency, "groupId"));
                assertEquals("${jackson-bom.version}", directChildText(dependency, "version"));
                assertEquals("pom", directChildText(dependency, "type"));
                assertEquals("import", directChildText(dependency, "scope"));
            }
            if ("spring-boot-dependencies".equals(artifactId)) {
                assertEquals("org.springframework.boot", directChildText(dependency, "groupId"));
                springBootIndex = index;
            }
        }

        assertEquals(1, jacksonCount, "Root dependencyManagement must import exactly one Jackson BOM");
        assertTrue(springBootIndex >= 0, "Root dependencyManagement must continue importing Spring Boot dependencies");
        assertTrue(
                jacksonIndex < springBootIndex,
                "Without the Spring Boot parent POM, the explicit Jackson override BOM must precede spring-boot-dependencies"
        );
    }

    private static Element parseProject(String pom) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(pom)))
                    .getDocumentElement();
        } catch (Exception exception) {
            throw new AssertionError("Root pom.xml must be parseable XML for structural dependency validation", exception);
        }
    }

    private static Element requireDirectChild(Element parent, String name) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(elementName(element))) {
                return element;
            }
        }
        throw new AssertionError("Missing direct <" + name + "> under <" + elementName(parent) + ">");
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && name.equals(elementName(element))) {
                children.add(element);
            }
        }
        return children;
    }

    private static String directChildText(Element parent, String name) {
        return requireDirectChild(parent, name).getTextContent().trim();
    }

    private static String elementName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
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
