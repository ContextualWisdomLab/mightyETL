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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards repository-wide owned-production coverage authority against silently unmeasured modules
 * or Java source classes.
 *
 * <p>The contract inventories the root Maven reactor and production source tree directly. A module
 * is covered only when its effective repository configuration provides an explicit JaCoCo agent,
 * a fail-closed {@code check} execution, and a class-file selection that reaches every owned
 * top-level Java source file. This prevents a strict-looking zero-missed rule over a narrow subset
 * from being mistaken for repository-wide 100% coverage.</p>
 */
class RepositoryCoverageAuthorityTest {

    private static final Path PROJECT_ROOT = projectRoot();
    private static final String JACOCO_GROUP_ID = "org.jacoco";
    private static final String JACOCO_ARTIFACT_ID = "jacoco-maven-plugin";
    private static final Set<String> ZERO_MISSED_COUNTERS =
            Set.of("INSTRUCTION", "LINE", "METHOD", "BRANCH");

    @Test
    void everyOwnedProductionModuleHasFailClosedCoverageAuthority() throws Exception {
        Document rootPom = parsePom(PROJECT_ROOT.resolve("pom.xml"));
        List<String> productionModules = productionModules(rootPom);
        assertFalse(productionModules.isEmpty(), "Repository must contain owned production modules");

        List<String> deficiencies = new ArrayList<>();
        for (String moduleName : productionModules) {
            Path moduleRoot = PROJECT_ROOT.resolve(moduleName);
            Document modulePom = parsePom(moduleRoot.resolve("pom.xml"));
            Element plugin = localJacocoPlugin(modulePom);
            if (plugin == null) {
                plugin = inheritedRootJacocoPlugin(rootPom);
            }
            if (plugin == null) {
                deficiencies.add(moduleName + ": missing executable jacoco-maven-plugin authority");
                continue;
            }

            Element prepare = executionWithGoal(plugin, "prepare-agent");
            if (prepare == null) {
                deficiencies.add(moduleName + ": missing prepare-agent execution");
            }

            Element check = executionWithGoal(plugin, "check");
            if (check == null) {
                deficiencies.add(moduleName + ": missing check execution");
                continue;
            }

            Element checkConfiguration = effectiveConfiguration(plugin, check);
            if (!hasNonEmptyClassGuard(checkConfiguration)) {
                deficiencies.add(moduleName + ": missing CLASS TOTALCOUNT minimum 1 fail-closed guard");
            }
            Set<String> missingCounters = missingZeroMissedCounters(checkConfiguration);
            if (!missingCounters.isEmpty()) {
                deficiencies.add(moduleName + ": missing zero MISSEDCOUNT limits for " + missingCounters);
            }
        }

        assertTrue(
                deficiencies.isEmpty(),
                () -> "Every owned production module must have non-vacuous zero-missed JaCoCo authority: "
                        + String.join("; ", deficiencies)
        );
    }

    @Test
    void everyOwnedProductionSourceClassIsInsideItsCoverageScope() throws Exception {
        Document rootPom = parsePom(PROJECT_ROOT.resolve("pom.xml"));
        List<String> uncovered = new ArrayList<>();

        for (String moduleName : productionModules(rootPom)) {
            Path moduleRoot = PROJECT_ROOT.resolve(moduleName);
            Document modulePom = parsePom(moduleRoot.resolve("pom.xml"));
            Element plugin = localJacocoPlugin(modulePom);
            if (plugin == null) {
                plugin = inheritedRootJacocoPlugin(rootPom);
            }
            if (plugin == null) {
                for (String classFile : ownedTopLevelClassFiles(moduleRoot)) {
                    uncovered.add(moduleName + ":" + classFile);
                }
                continue;
            }

            Element check = executionWithGoal(plugin, "check");
            if (check == null) {
                for (String classFile : ownedTopLevelClassFiles(moduleRoot)) {
                    uncovered.add(moduleName + ":" + classFile);
                }
                continue;
            }

            List<String> includes = coverageIncludes(plugin, check);
            for (String classFile : ownedTopLevelClassFiles(moduleRoot)) {
                if (!isIncluded(classFile, includes)) {
                    uncovered.add(moduleName + ":" + classFile);
                }
            }
        }

        uncovered.sort(Comparator.naturalOrder());
        assertTrue(
                uncovered.isEmpty(),
                () -> "Owned production classes outside repository coverage authority: "
                        + String.join(", ", uncovered)
        );
    }

    private static List<String> productionModules(Document rootPom) throws Exception {
        Element modules = directChild(rootPom.getDocumentElement(), "modules");
        if (modules == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Element module : directChildren(modules, "module")) {
            String moduleName = module.getTextContent().trim();
            if (!ownedTopLevelClassFiles(PROJECT_ROOT.resolve(moduleName)).isEmpty()) {
                result.add(moduleName);
            }
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private static List<String> ownedTopLevelClassFiles(Path moduleRoot) throws Exception {
        Path sourceRoot = moduleRoot.resolve("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        List<String> classFiles = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .filter(path -> !path.getFileName().toString().equals("module-info.java"))
                    .forEach(path -> {
                        String relative = sourceRoot.relativize(path).toString().replace('\\', '/');
                        classFiles.add(relative.substring(0, relative.length() - ".java".length()) + ".class");
                    });
        }
        classFiles.sort(Comparator.naturalOrder());
        return classFiles;
    }

    private static Element localJacocoPlugin(Document pom) {
        Element build = directChild(pom.getDocumentElement(), "build");
        if (build == null) {
            return null;
        }
        Element plugins = directChild(build, "plugins");
        return plugins == null ? null : jacocoPluginFrom(plugins);
    }

    private static Element inheritedRootJacocoPlugin(Document rootPom) {
        Element plugin = localJacocoPlugin(rootPom);
        if (plugin == null || "false".equalsIgnoreCase(directChildText(plugin, "inherited"))) {
            return null;
        }
        return plugin;
    }

    private static Element jacocoPluginFrom(Element plugins) {
        for (Element plugin : directChildren(plugins, "plugin")) {
            if (JACOCO_GROUP_ID.equals(directChildText(plugin, "groupId"))
                    && JACOCO_ARTIFACT_ID.equals(directChildText(plugin, "artifactId"))) {
                return plugin;
            }
        }
        return null;
    }

    private static Element executionWithGoal(Element plugin, String goalName) {
        Element executions = directChild(plugin, "executions");
        if (executions == null) {
            return null;
        }
        for (Element execution : directChildren(executions, "execution")) {
            Element goals = directChild(execution, "goals");
            if (goals == null) {
                continue;
            }
            for (Element goal : directChildren(goals, "goal")) {
                if (goalName.equals(goal.getTextContent().trim())) {
                    return execution;
                }
            }
        }
        return null;
    }

    private static Element effectiveConfiguration(Element plugin, Element execution) {
        Element executionConfiguration = directChild(execution, "configuration");
        if (executionConfiguration != null) {
            return executionConfiguration;
        }
        Element pluginConfiguration = directChild(plugin, "configuration");
        if (pluginConfiguration != null) {
            return pluginConfiguration;
        }
        throw new AssertionError("JaCoCo check execution is missing configuration");
    }

    private static boolean hasNonEmptyClassGuard(Element configuration) {
        for (Element limit : descendants(configuration, "limit")) {
            if ("CLASS".equals(directChildText(limit, "counter"))
                    && "TOTALCOUNT".equals(directChildText(limit, "value"))
                    && minimumAtLeastOne(directChildText(limit, "minimum"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean minimumAtLeastOne(String minimum) {
        if (minimum == null) {
            return false;
        }
        try {
            return Double.parseDouble(minimum) >= 1.0d;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Set<String> missingZeroMissedCounters(Element configuration) {
        Set<String> present = new LinkedHashSet<>();
        for (Element limit : descendants(configuration, "limit")) {
            String counter = directChildText(limit, "counter");
            if (ZERO_MISSED_COUNTERS.contains(counter)
                    && "MISSEDCOUNT".equals(directChildText(limit, "value"))
                    && "0".equals(directChildText(limit, "maximum"))) {
                present.add(counter);
            }
        }
        Set<String> missing = new LinkedHashSet<>(ZERO_MISSED_COUNTERS);
        missing.removeAll(present);
        return missing;
    }

    private static List<String> coverageIncludes(Element plugin, Element check) {
        Element configuration = directChild(check, "configuration");
        if (configuration == null) {
            configuration = directChild(plugin, "configuration");
        }
        if (configuration == null) {
            return List.of();
        }
        Element includes = directChild(configuration, "includes");
        if (includes == null) {
            return List.of();
        }
        List<String> patterns = new ArrayList<>();
        for (Element include : directChildren(includes, "include")) {
            String value = include.getTextContent().trim();
            if (!value.isEmpty()) {
                patterns.add(value.replace('\\', '/'));
            }
        }
        return patterns;
    }

    private static boolean isIncluded(String classFile, List<String> includePatterns) {
        if (includePatterns.isEmpty()) {
            return true;
        }
        for (String pattern : includePatterns) {
            if (mavenGlob(pattern).matcher(classFile).matches()) {
                return true;
            }
        }
        return false;
    }

    private static Pattern mavenGlob(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char character = glob.charAt(index);
            if (character == '*') {
                if (index + 1 < glob.length() && glob.charAt(index + 1) == '*') {
                    regex.append(".*");
                    index++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (character == '?') {
                regex.append("[^/]");
            } else {
                if ("\\.[]{}()+-^$|".indexOf(character) >= 0) {
                    regex.append('\\');
                }
                regex.append(character);
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }

    private static Document parsePom(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(pom.toFile());
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

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static List<Element> descendants(Element parent, String name) {
        List<Element> matches = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName(name);
        for (int index = 0; index < nodes.getLength(); index++) {
            matches.add((Element) nodes.item(index));
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
