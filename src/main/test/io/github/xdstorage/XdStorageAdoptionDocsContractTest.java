package io.github.xdstorage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DOCSLINT (docs/tasks/2.1.0/21): the adoption-surface docs contract — DECISION-GUIDE.md,
 * README.md and the quickstart example stay mechanically honest:
 * <ol>
 *   <li>every relative markdown link resolves to an existing file;</li>
 *   <li>every benchmark scenario cited in the decision guide's numbers table exists as a
 *       row in BENCHMARKS.md's canonical baseline;</li>
 *   <li>the quickstart example's {@code xdstorage.version} matches the root pom version.</li>
 * </ol>
 *
 * <p>The README test count rotted for months before task 12 found it; this lint deletes that
 * class of stale-claim issues for the surface evaluators see first (the T-2 source-walking
 * precedent: plain JUnit in the default run, walking the repo checkout relative to the
 * working directory). External URL liveness is deliberately out of scope — network in tests
 * is a flake vector.
 */
@DisplayName("DOCSLINT: adoption-surface docs contract")
public class XdStorageAdoptionDocsContractTest {

    private static final Path REPO_ROOT = Paths.get("").toAbsolutePath();
    private static final String GUIDE = "docs/DECISION-GUIDE.md";
    private static final String README = "README.md";
    private static final String QUICKSTART_README = "examples/quickstart/README.md";
    private static final String QUICKSTART_POM = "examples/quickstart/pom.xml";
    private static final String ROOT_POM = "pom.xml";
    private static final String BENCHMARKS = "docs/BENCHMARKS.md";

    /** [text](target) — the common markdown link/image shape in these docs. */
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\]\\(([^)]+)\\)");

    @Test
    public void relativeLinksResolve() throws IOException {
        final List<String> violations = new ArrayList<>();
        for (final String doc : new String[]{GUIDE, README, QUICKSTART_README}) {
            final Path docPath = repoRoot(doc);
            final Path docDir = docPath.getParent();
            final String text = new String(Files.readAllBytes(docPath), StandardCharsets.UTF_8);
            final Matcher matcher = MARKDOWN_LINK.matcher(text);
            while (matcher.find()) {
                final String target = matcher.group(1).trim();
                if (target.isEmpty()
                        || target.startsWith("http://") || target.startsWith("https://")
                        || target.startsWith("mailto:") || target.startsWith("#")) {
                    continue; // external or same-document — out of scope by the task's contract
                }
                final String filePart = target.split("#", 2)[0];
                if (filePart.isEmpty()) {
                    continue; // pure same-document anchor
                }
                final Path resolved = docDir.resolve(filePart).normalize();
                if (!Files.exists(resolved)) {
                    violations.add(doc + ": link target '" + target + "' does not exist (resolved: "
                            + resolved + ") — fix the link or restore the target file");
                }
            }
        }
        assertTrue(violations.isEmpty(),
                "broken relative links (" + violations.size() + "):\n  " + String.join("\n  ", violations));
    }

    @Test
    public void citedBenchmarkScenariosExistInTheCanonicalBaseline() throws IOException {
        final String guide = new String(Files.readAllBytes(repoRoot(GUIDE)), StandardCharsets.UTF_8);
        final String benchmarks = new String(Files.readAllBytes(repoRoot(BENCHMARKS)), StandardCharsets.UTF_8);

        // the guide's NUMBERS TABLE section only — the rest of the guide backticks ordinary
        // words (`Map`, `cp -r`) that are not scenario citations
        final String numbersSection = sectionBetween(guide, "## The numbers, in one table");
        final List<String> violations = new ArrayList<>();
        int cited = 0;
        for (final String line : numbersSection.split("\n")) {
            if (!line.trim().startsWith("|") || !line.contains("`")) {
                continue;
            }
            final Matcher matcher = Pattern.compile("`([A-Za-z][A-Za-z0-9_]*)`").matcher(line);
            while (matcher.find()) {
                final String candidate = matcher.group(1);
                // scenario names are lowercase-start camelCase (loadById_indexBaseline...);
                // criterion operators like `BETWEEN`/`GT` are ALL-CAPS, other prose backticks
                // start uppercase — both are not citations
                if (!candidate.isEmpty() && Character.isUpperCase(candidate.charAt(0))) {
                    continue;
                }
                cited++;
                if (!benchmarks.contains(candidate)) {
                    violations.add(GUIDE + ": cited scenario '" + candidate + "' has no row in "
                            + BENCHMARKS + " — regenerate the baseline or fix the citation");
                }
            }
        }
        assertTrue(cited >= 10,
                "the numbers table shrank to " + cited + " cited scenarios — was the table truncated?");
        assertTrue(violations.isEmpty(),
                "cited scenarios missing from the canonical baseline (" + violations.size() + "):\n  "
                        + String.join("\n  ", violations));
    }

    @Test
    public void quickstartVersionMatchesRootPom() throws IOException {
        final String rootPom = new String(Files.readAllBytes(repoRoot(ROOT_POM)), StandardCharsets.UTF_8);
        final String quickstartPom = new String(Files.readAllBytes(repoRoot(QUICKSTART_POM)), StandardCharsets.UTF_8);

        final String rootVersion = firstTagValue(rootPom, "version");
        final String quickstartVersion = propertyValue(quickstartPom, "xdstorage.version");

        assertTrue(rootVersion != null && quickstartVersion != null,
                "could not read the versions: root <version>=" + rootVersion + ", quickstart xdstorage.version="
                        + quickstartVersion);
        assertTrue(quickstartVersion.equals(rootVersion),
                QUICKSTART_POM + " pins xdstorage.version " + quickstartVersion + " but the root pom is "
                        + rootVersion + " — align the example's dependency (the quickstart resolves the "
                        + "library from the local repo; a mismatch silently tests against a stale engine)");
    }

    // --- helpers ---

    /** the text from a `## heading` line (prefix match) up to the next `## ` heading */
    private static String sectionBetween(final String document, final String headingPrefix) {
        final int start = document.indexOf(headingPrefix);
        if (start < 0) {
            return "";
        }
        final int next = document.indexOf("\n## ", start + headingPrefix.length());
        return next < 0 ? document.substring(start) : document.substring(start, next);
    }

    private static Path repoRoot(final String relative) {
        final Path p = REPO_ROOT.resolve(relative);
        assertTrue(Files.exists(p), "required file missing: " + p);
        return p;
    }

    private static String firstTagValue(final String pom, final String tag) {
        final Matcher m = Pattern.compile("<" + tag + ">([^<]+)</" + tag + ">").matcher(pom);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String propertyValue(final String pom, final String propertyName) {
        final Matcher m = Pattern.compile("<" + propertyName + ">([^<]+)</" + propertyName + ">").matcher(pom);
        return m.find() ? m.group(1).trim() : null;
    }
}
