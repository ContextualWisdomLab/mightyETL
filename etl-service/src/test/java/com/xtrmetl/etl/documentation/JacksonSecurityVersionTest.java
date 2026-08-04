package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
 * <p>Spring Boot exposes {@code jackson-bom.version} as the supported Maven override for the
 * complete, mutually compatible Jackson component set. Pinning only {@code jackson-databind}
 * could create an unsupported mix of core, annotations, datatype, and module artifacts, so this
 * contract requires the patched FasterXML BOM instead.</p>
 */
class JacksonSecurityVersionTest {

    private static final String PATCHED_JACKSON_BOM_VERSION = "2.21.5";

    /**
     * Requires the root Maven project to override Spring Boot's Jackson BOM with the first 2.21
     * patch release that fixes CVE-2026-54515, CVE-2026-59889, and GHSA-mhm7-754m-9p8w.
     *
     * @throws Exception when the root Maven model cannot be parsed securely
     */
    @Test
    void usesPatchedCompatibleJacksonBom() throws Exception {
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

        Document document = factory.newDocumentBuilder().parse(rootPom.toFile());
        Element version = (Element) document.getElementsByTagName("jackson-bom.version").item(0);
        assertNotNull(
                version,
                "The root POM must declare jackson-bom.version so every Jackson module is aligned"
        );
        assertEquals(PATCHED_JACKSON_BOM_VERSION, version.getTextContent().trim());
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
