package com.mahghuuuls.statusworks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fails when a class in the main source tree uses a mod package its own package may not depend on. Each package may
 * use itself, the packages listed for it below, and code outside the mod; the root package may use everything, and
 * no other package may use the root's classes. Only the CraftTweaker bridge may name CraftTweaker, and the pure rules
 * may not name Minecraft or Forge. A package the table does not list fails, so a new package needs a decision.
 *
 * <p>The scan reads imports and fully qualified names in code, with comments and string literals removed. It checks
 * packages, not classes: finer limits (only read views for commands, only the server senders of the network package,
 * only messages for the client, which libraries a package may use) and side-only loading are left to review. When a
 * package's allowed uses change, change this table on purpose.</p>
 */
class DependencyRulesTest {

    private static final String ROOT = "com.mahghuuuls.statusworks";
    private static final Path SOURCES = Paths.get("src", "main", "java");

    /** Package (relative to the root) to the mod packages it may use besides itself. */
    private static final Map<String, List<String>> ALLOWED = new LinkedHashMap<>();

    static {
        ALLOWED.put("api", Collections.<String>emptyList());
        ALLOWED.put("core.rule", Collections.singletonList("api.definition"));
        ALLOWED.put("core.definition", Arrays.asList("api", "core.rule"));
        ALLOWED.put("core.state", Arrays.asList("api", "core.rule", "core.definition", "core.contribution",
                "core.callback", "core.diagnostic"));
        ALLOWED.put("core.mark", Arrays.asList("api", "core.rule", "core.definition", "core.contribution",
                "core.callback", "core.diagnostic", "core.state"));
        // The one accepted two-way link: the entity state stores the contribution record, and the contribution
        // manager reads the entity state's instances to compute what to apply.
        ALLOWED.put("core.contribution", Arrays.asList("api", "core.definition", "core.diagnostic", "core.state",
                "core.callback"));
        ALLOWED.put("core.lifecycle", Arrays.asList("core.state", "core.mark", "core.contribution", "core.callback",
                "core.diagnostic", "network", "core.definition", "core.rule"));
        ALLOWED.put("core.callback", Arrays.asList("api", "core.diagnostic"));
        ALLOWED.put("core.diagnostic", Collections.<String>emptyList());
        ALLOWED.put("network", Arrays.asList("api", "core.definition", "core.state", "core.mark", "core.rule",
                "core.diagnostic"));
        ALLOWED.put("client", Arrays.asList("api", "network"));
        ALLOWED.put("command", Arrays.asList("api", "core"));
        ALLOWED.put("config", Collections.<String>emptyList());
        ALLOWED.put("compat.crafttweaker", Collections.singletonList("api"));
    }

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    /** A mod name: the package path (lower-case parts) and, when written, the class or {@code *} after it. */
    private static final Pattern MOD_REFERENCE = Pattern.compile(Pattern.quote(ROOT)
            + "(?!\\w)((?:\\.[a-z]\\w*)*)(?:\\.([A-Z]\\w*|\\*))?");
    private static final Pattern CRAFTTWEAKER =
            Pattern.compile("(?<![\\w.])(crafttweaker|stanhebben|com\\.blamejared\\.ctgui)\\.");
    private static final Pattern MINECRAFT = Pattern.compile("(?<![\\w.])net\\.minecraft(forge)?\\.");
    private static final Pattern COMMENT_OR_STRING = Pattern.compile(
            "/\\*.*?\\*/|//[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'", Pattern.DOTALL);

    @Test
    void everyPackageUsesOnlyThePackagesItMayDependOn() throws IOException {
        List<Path> files = sources();
        List<String> broken = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Path file : files) {
            String code = code(file);
            broken.addAll(check(SOURCES.relativize(file).toString(), code));
            Matcher declared = declaration(stripped(code));
            if (declared != null) {
                seen.add(declared.group(1));
            }
        }
        assertTrue(broken.isEmpty(), "Dependency rules broken:\n" + String.join("\n", broken));
        // Guards against a scan that read nothing: every package of the table has at least one class.
        for (String pkg : ALLOWED.keySet()) {
            assertTrue(seen.stream().anyMatch(s -> within(s, ROOT + "." + pkg)), "No class found in " + pkg);
        }
    }

    @Test
    void aClientClassThatUsesCoreStateIsReportedWithTheClassItUses() {
        String code = "package com.mahghuuuls.statusworks.client;\nimport com.mahghuuuls.statusworks.core.state.EntityState;\n";
        List<String> broken = check("Planted.java", code);
        assertEquals(1, broken.size(), broken.toString());
        assertTrue(broken.get(0).contains("core.state.EntityState"), broken.get(0));
    }

    @Test
    void aFullyQualifiedNameCountsAndACommentOrAStringDoesNot() {
        String inCode = "package com.mahghuuuls.statusworks.core.rule;\nclass X { net.minecraft.entity.Entity e; }\n";
        assertEquals(1, check("A.java", inCode).size());
        String inCommentsAndStrings = "package com.mahghuuuls.statusworks.core.rule;\n"
                + "// net.minecraft.entity.Entity and com.mahghuuuls.statusworks.client.ClientStore\n"
                + "/* crafttweaker.api.IAction */\n"
                + "class X { String s = \"crafttweaker.api\"; String t = \"com.mahghuuuls.statusworks.client.X\"; }\n";
        List<String> broken = check("B.java", inCommentsAndStrings);
        assertTrue(broken.isEmpty(), broken.toString());
    }

    @Test
    void onlyTheBridgeNamesCraftTweakerAndTheBridgesOwnPackageNameIsNotCraftTweaker() {
        assertTrue(check("A.java", "package com.mahghuuuls.statusworks.compat.crafttweaker;\n"
                + "import crafttweaker.annotations.ZenRegister;\nimport stanhebben.zenscript.annotations.ZenClass;\n")
                .isEmpty());
        assertEquals(1, check("B.java", "package com.mahghuuuls.statusworks.api;\n"
                + "import crafttweaker.annotations.ZenRegister;\n").size());
        assertEquals(1, check("C.java", "package com.mahghuuuls.statusworks.client;\n"
                + "import stanhebben.zenscript.annotations.ZenClass;\n").size());
        assertEquals(1, check("D.java", "package com.mahghuuuls.statusworks.client;\n"
                + "import com.blamejared.ctgui.MainClass;\n").size());
        // The root may use the bridge; the bridge's package name ends in "crafttweaker" but is not CraftTweaker.
        assertTrue(check("E.java", "package com.mahghuuuls.statusworks;\n"
                + "import com.mahghuuuls.statusworks.compat.crafttweaker.ZsStatuses;\n").isEmpty());
    }

    @Test
    void onlyTheRootUsesTheRootsClasses() {
        assertEquals(1, check("A.java", "package com.mahghuuuls.statusworks.api;\n"
                + "import com.mahghuuuls.statusworks.StatusworksMod;\n").size());
        assertEquals(1, check("B.java", "package com.mahghuuuls.statusworks.client;\n"
                + "import com.mahghuuuls.statusworks.*;\n").size());
        assertTrue(check("C.java", "package com.mahghuuuls.statusworks;\n"
                + "import com.mahghuuuls.statusworks.client.ClientStore;\nclass X { StatusworksMod m; }\n").isEmpty());
    }

    @Test
    void aPackageTheTableDoesNotListAndAFileWithoutAPackageFail() {
        assertEquals(1, check("A.java", "package com.mahghuuuls.statusworks.core.foo;\n").size());
        assertEquals(1, check("B.java", "package com.mahghuuuls.statusworks.util;\n").size());
        assertEquals(1, check("C.java", "class NoPackage {}\n").size());
        assertEquals(1, check("D.java", "package org.example;\n").size());
    }

    private static List<Path> sources() throws IOException {
        assertTrue(Files.isDirectory(SOURCES), "No source tree at " + SOURCES.toAbsolutePath());
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().collect(Collectors.toList());
        }
    }

    private static String code(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** The broken rules of one source file, one line each. */
    static List<String> check(String name, String source) {
        String code = stripped(source);
        Matcher declared = declaration(code);
        if (declared == null || !within(declared.group(1), ROOT)) {
            return Collections.singletonList(name + " has no package inside " + ROOT);
        }
        String own = declared.group(1).length() > ROOT.length() ? declared.group(1).substring(ROOT.length() + 1) : "";
        String body = code.substring(declared.end());
        List<String> broken = new ArrayList<>();
        String rule = own.isEmpty() ? null : rule(own);
        if (!own.isEmpty() && rule == null) {
            broken.add(name + " (" + own + ") is in a package the dependency table does not list");
        }
        if (rule != null) {
            Matcher m = MOD_REFERENCE.matcher(body);
            while (m.find()) {
                String used = m.group(1).isEmpty() ? "" : m.group(1).substring(1);
                String shown = used + (m.group(2) == null ? "" : (used.isEmpty() ? "" : ".") + m.group(2));
                boolean ok = !used.isEmpty() && (within(used, rule) || allowed(rule, used));
                String line = name + " (" + own + ") uses " + (used.isEmpty() ? "the root's " + shown : shown);
                if (!ok && !broken.contains(line)) {
                    broken.add(line);
                }
            }
        }
        if (!within(own, "compat.crafttweaker") && CRAFTTWEAKER.matcher(body).find()) {
            broken.add(name + " (" + own + ") uses CraftTweaker");
        }
        if (within(own, "core.rule") && MINECRAFT.matcher(body).find()) {
            broken.add(name + " (" + own + ") uses net.minecraft");
        }
        return broken;
    }

    /** The source with comments and string and character literals blanked out. */
    private static String stripped(String source) {
        return COMMENT_OR_STRING.matcher(source).replaceAll(" ");
    }

    /** The matched package declaration of stripped code, or null when it has none. */
    private static Matcher declaration(String strippedCode) {
        Matcher m = PACKAGE.matcher(strippedCode);
        return m.find() ? m : null;
    }

    /** The most specific table entry for a package, or null when the table does not list it. */
    private static String rule(String own) {
        String best = null;
        for (String key : ALLOWED.keySet()) {
            if (within(own, key) && (best == null || key.length() > best.length())) {
                best = key;
            }
        }
        return best;
    }

    private static boolean allowed(String rule, String used) {
        for (String target : ALLOWED.get(rule)) {
            if (within(used, target)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code name} is {@code pkg} or inside it. */
    private static boolean within(String name, String pkg) {
        return name.equals(pkg) || name.startsWith(pkg + ".");
    }
}
