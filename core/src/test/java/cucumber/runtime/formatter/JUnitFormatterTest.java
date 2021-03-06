package cucumber.runtime.formatter;

import cucumber.api.PendingException;
import cucumber.runtime.Backend;
import cucumber.runtime.Env;
import cucumber.runtime.HookDefinition;
import cucumber.runtime.Runtime;
import cucumber.runtime.RuntimeGlue;
import cucumber.runtime.RuntimeOptions;
import cucumber.runtime.StepDefinitionMatch;
import cucumber.runtime.StopWatch;
import cucumber.runtime.TestHelper;
import cucumber.runtime.Utils;
import cucumber.runtime.io.ClasspathResourceLoader;
import cucumber.runtime.model.CucumberFeature;
import cucumber.runtime.snippets.FunctionNameSanitizer;
import gherkin.I18n;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.Step;
import gherkin.formatter.model.Tag;
import junit.framework.AssertionFailedError;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JUnitFormatterTest {

    @Test
    public void featureSimpleTest() throws Exception {
        File report = runFeaturesWithJunitFormatter(asList("cucumber/runtime/formatter/JUnitFormatterTest_1.feature"));
        assertXmlEqual("cucumber/runtime/formatter/JUnitFormatterTest_1.report.xml", report);
    }

    @Test
    public void featureWithBackgroundTest() throws Exception {
        File report = runFeaturesWithJunitFormatter(asList("cucumber/runtime/formatter/JUnitFormatterTest_2.feature"));
        assertXmlEqual("cucumber/runtime/formatter/JUnitFormatterTest_2.report.xml", report);
    }

    @Test
    public void featureWithOutlineTest() throws Exception {
        File report = runFeaturesWithJunitFormatter(asList("cucumber/runtime/formatter/JUnitFormatterTest_3.feature"));
        assertXmlEqual("cucumber/runtime/formatter/JUnitFormatterTest_3.report.xml", report);
    }

    @Test
    public void featureSimpleStrictTest() throws Exception {
        boolean strict = true;
        File report = runFeaturesWithJunitFormatter(asList("cucumber/runtime/formatter/JUnitFormatterTest_1.feature"), strict);
        assertXmlEqual("cucumber/runtime/formatter/JUnitFormatterTest_1_strict.report.xml", report);
    }

    @Test
    public void should_format_passed_scenario() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n" +
                        "  Scenario: scenario name\n" +
                        "    Given first step\n" +
                        "    When second step\n" +
                        "    Then third step\n");
        Map<String, String> stepsToResult = new HashMap<String, String>();
        stepsToResult.put("first step", "passed");
        stepsToResult.put("second step", "passed");
        stepsToResult.put("third step", "passed");
        long stepDuration = milliSeconds(1);

        String formatterOutput = runFeatureWithJUnitFormatter(feature, stepsToResult, stepDuration);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"0\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"0\" tests=\"1\" time=\"0.003\">\n" +
                "    <testcase classname=\"feature name\" name=\"scenario name\" time=\"0.003\">\n" +
                "        <system-out><![CDATA[" +
                "Given first step............................................................passed\n" +
                "When second step............................................................passed\n" +
                "Then third step.............................................................passed\n" +
                "]]></system-out>\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, formatterOutput);
    }

    @Test
    public void should_format_pending_scenario() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n" +
                        "  Scenario: scenario name\n" +
                        "    Given first step\n" +
                        "    When second step\n" +
                        "    Then third step\n");
        Map<String, String> stepsToResult = new HashMap<String, String>();
        stepsToResult.put("first step", "pending");
        stepsToResult.put("second step", "skipped");
        stepsToResult.put("third step", "undefined");
        long stepDuration = milliSeconds(1);

        String formatterOutput = runFeatureWithJUnitFormatter(feature, stepsToResult, stepDuration);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"0\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"1\" tests=\"1\" time=\"0.001\">\n" +
                "    <testcase classname=\"feature name\" name=\"scenario name\" time=\"0.001\">\n" +
                "        <skipped><![CDATA[" +
                "Given first step............................................................pending\n" +
                "When second step............................................................skipped\n" +
                "Then third step.............................................................undefined\n" +
                "]]></skipped>\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, formatterOutput);
    }

    @Test
    public void should_format_failed_scenario() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n" +
                        "  Scenario: scenario name\n" +
                        "    Given first step\n" +
                        "    When second step\n" +
                        "    Then third step\n");
        Map<String, String> stepsToResult = new HashMap<String, String>();
        stepsToResult.put("first step", "passed");
        stepsToResult.put("second step", "passed");
        stepsToResult.put("third step", "failed");
        long stepDuration = milliSeconds(1);

        String formatterOutput = runFeatureWithJUnitFormatter(feature, stepsToResult, stepDuration);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"1\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"0\" tests=\"1\" time=\"0.003\">\n" +
                "    <testcase classname=\"feature name\" name=\"scenario name\" time=\"0.003\">\n" +
                "        <failure message=\"the stack trace\"><![CDATA[" +
                "Given first step............................................................passed\n" +
                "When second step............................................................passed\n" +
                "Then third step.............................................................failed\n" +
                "\n" +
                "StackTrace:\n" +
                "the stack trace" +
                "]]></failure>\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, formatterOutput);
    }

    @Test
    public void should_handle_failure_in_before_hook() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n" +
                        "  Scenario: scenario name\n" +
                        "    Given first step\n" +
                        "    When second step\n" +
                        "    Then third step\n");
        Map<String, String> stepsToResult = new HashMap<String, String>();
        stepsToResult.put("first step", "passed");
        stepsToResult.put("second step", "passed");
        stepsToResult.put("third step", "passed");
        List<SimpleEntry<String, String>> hooks = new ArrayList<SimpleEntry<String, String>>();
        hooks.add(hookEntry("before", "failed"));
        long stepHookDuration = milliSeconds(1);

        String formatterOutput = runFeatureWithJUnitFormatter(feature, stepsToResult, hooks, stepHookDuration);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"1\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"0\" tests=\"1\" time=\"0.001\">\n" +
                "    <testcase classname=\"feature name\" name=\"scenario name\" time=\"0.001\">\n" +
                "        <failure message=\"the stack trace\"><![CDATA[" +
                "Given first step............................................................skipped\n" +
                "When second step............................................................skipped\n" +
                "Then third step.............................................................skipped\n" +
                "\n" +
                "StackTrace:\n" +
                "the stack trace" +
                "]]></failure>\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, formatterOutput);
    }

    @Test
    public void should_handle_failure_in_before_hook_with_background() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n" +
                        "  Background: background name\n" +
                        "    Given first step\n" +
                        "  Scenario: scenario name\n" +
                        "    When second step\n" +
                        "    Then third step\n");
        Map<String, String> stepsToResult = new HashMap<String, String>();
        stepsToResult.put("first step", "passed");
        stepsToResult.put("second step", "passed");
        stepsToResult.put("third step", "passed");
        List<SimpleEntry<String, String>> hooks = new ArrayList<SimpleEntry<String, String>>();
        hooks.add(hookEntry("before", "failed"));
        long stepHookDuration = milliSeconds(1);

        String formatterOutput = runFeatureWithJUnitFormatter(feature, stepsToResult, hooks, stepHookDuration);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"1\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"0\" tests=\"1\" time=\"0.001\">\n" +
                "    <testcase classname=\"feature name\" name=\"scenario name\" time=\"0.001\">\n" +
                "        <failure message=\"the stack trace\"><![CDATA[" +
                "Given first step............................................................skipped\n" +
                "When second step............................................................skipped\n" +
                "Then third step.............................................................skipped\n" +
                "\n" +
                "StackTrace:\n" +
                "the stack trace" +
                "]]></failure>\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, formatterOutput);
    }

    @Test
    public void should_handle_failure_in_after_hook() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n" +
                        "  Scenario: scenario name\n" +
                        "    Given first step\n" +
                        "    When second step\n" +
                        "    Then third step\n");
        Map<String, String> stepsToResult = new HashMap<String, String>();
        stepsToResult.put("first step", "passed");
        stepsToResult.put("second step", "passed");
        stepsToResult.put("third step", "passed");
        List<SimpleEntry<String, String>> hooks = new ArrayList<SimpleEntry<String, String>>();
        hooks.add(hookEntry("after", "failed"));
        long stepHookDuration = milliSeconds(1);

        String formatterOutput = runFeatureWithJUnitFormatter(feature, stepsToResult, hooks, stepHookDuration);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"1\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"0\" tests=\"1\" time=\"0.004\">\n" +
                "    <testcase classname=\"feature name\" name=\"scenario name\" time=\"0.004\">\n" +
                "        <failure message=\"the stack trace\"><![CDATA[" +
                "Given first step............................................................passed\n" +
                "When second step............................................................passed\n" +
                "Then third step.............................................................passed\n" +
                "\n" +
                "StackTrace:\n" +
                "the stack trace" +
                "]]></failure>\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, formatterOutput);
    }

    @Test
    public void should_accumulate_time_from_steps_and_hooks() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n" +
                        "  Scenario: scenario name\n" +
                        "    * first step\n" +
                        "    * second step\n");
        Map<String, String> stepsToResult = new HashMap<String, String>();
        stepsToResult.put("first step", "passed");
        stepsToResult.put("second step", "passed");
        List<SimpleEntry<String, String>> hooks = new ArrayList<SimpleEntry<String, String>>();
        hooks.add(hookEntry("before", "passed"));
        hooks.add(hookEntry("after", "passed"));
        long stepHookDuration = milliSeconds(1);

        String formatterOutput = runFeatureWithJUnitFormatter(feature, stepsToResult, hooks, stepHookDuration);

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"0\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"0\" tests=\"1\" time=\"0.004\">\n" +
                "    <testcase classname=\"feature name\" name=\"scenario name\" time=\"0.004\">\n" +
                "        <system-out><![CDATA[" +
                "* first step................................................................passed\n" +
                "* second step...............................................................passed\n" +
                "]]></system-out>\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, formatterOutput);
    }

    @Test
    public void should_handle_all_step_calls_first_execution() throws Exception {
        final File report = File.createTempFile("cucumber-jvm-junit", ".xml");
        final JUnitFormatter junitFormatter = createJUnitFormatter(report);

        junitFormatter.uri(uri());
        junitFormatter.feature(feature("feature name"));
        junitFormatter.scenario(scenario("scenario name"));
        junitFormatter.step(step("keyword ", "step name"));
        junitFormatter.step(step("keyword ", "step name"));
        junitFormatter.match(match());
        junitFormatter.result(result("passed"));
        junitFormatter.match(match());
        junitFormatter.result(result("passed"));
        junitFormatter.eof();
        junitFormatter.done();
        junitFormatter.close();

        String actual = new Scanner(new FileInputStream(report), "UTF-8").useDelimiter("\\A").next();
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"0\" tests=\"1\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"0\" time=\"0\">\n" +
                "    <testcase classname=\"feature name\" name=\"scenario name\" time=\"0\">\n" +
                "        <system-out><![CDATA[" +
                "keyword step name...........................................................passed\n" +
                "keyword step name...........................................................passed\n" +
                "]]></system-out>\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, actual);
    }

    @Test
    public void should_handle_one_step_at_the_time_execution() throws Exception {
        final File report = File.createTempFile("cucumber-jvm-junit", ".xml");
        final JUnitFormatter junitFormatter = createJUnitFormatter(report);

        junitFormatter.uri(uri());
        junitFormatter.feature(feature("feature name"));
        junitFormatter.scenario(scenario("scenario name"));
        junitFormatter.step(step("keyword ", "step name"));
        junitFormatter.match(match());
        junitFormatter.result(result("passed"));
        junitFormatter.step(step("keyword ", "step name"));
        junitFormatter.match(match());
        junitFormatter.result(result("passed"));
        junitFormatter.eof();
        junitFormatter.done();
        junitFormatter.close();

        String actual = new Scanner(new FileInputStream(report), "UTF-8").useDelimiter("\\A").next();
        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"0\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"0\" tests=\"1\" time=\"0\">\n" +
                "    <testcase classname=\"feature name\" name=\"scenario name\" time=\"0\">\n" +
                "        <system-out><![CDATA[" +
                "keyword step name...........................................................passed\n" +
                "keyword step name...........................................................passed\n" +
                "]]></system-out>\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, actual);
    }

    @Test
    public void should_add_dummy_testcase_if_no_scenarios_are_run_to_aviod_failed_jenkins_jobs() throws Throwable {
        CucumberFeature feature = TestHelper.feature("path/test.feature",
                "Feature: feature name\n");

        String formatterOutput = runFeatureWithJUnitFormatter(feature, Collections.<String, String>emptyMap(), Collections.<SimpleEntry<String, String>>emptyList(), milliSeconds(1));

        String expected = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n" +
                "<testsuite failures=\"0\" name=\"cucumber.runtime.formatter.JUnitFormatter\" skipped=\"0\" time=\"0\">\n" +
                "    <testcase classname=\"dummy\" name=\"dummy\">\n" +
                "        <skipped message=\"No features found\" />\n" +
                "    </testcase>\n" +
                "</testsuite>\n";
        assertXmlEqual(expected, formatterOutput);
    }

    private File runFeaturesWithJunitFormatter(final List<String> featurePaths) throws IOException {
        return runFeaturesWithJunitFormatter(featurePaths, false);
    }

    private File runFeaturesWithJunitFormatter(final List<String> featurePaths, boolean strict) throws IOException {
        File report = File.createTempFile("cucumber-jvm-junit", "xml");
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final ClasspathResourceLoader resourceLoader = new ClasspathResourceLoader(classLoader);

        List<String> args = new ArrayList<String>();
        if (strict) {
            args.add("--strict");
        }
        args.add("--format");
        args.add("junit:" + report.getAbsolutePath());
        args.addAll(featurePaths);

        RuntimeOptions runtimeOptions = new RuntimeOptions(new Env(), args.toArray(new String[args.size()]));
        Backend backend = mock(Backend.class);
        when(backend.getSnippet(any(Step.class), any(FunctionNameSanitizer.class))).thenReturn("TEST SNIPPET");
        final cucumber.runtime.Runtime runtime = new Runtime(resourceLoader, classLoader, asList(backend), runtimeOptions);
        runtime.run();
        return report;
    }

    private SimpleEntry<String, String> hookEntry(String type, String result) {
        return new SimpleEntry<String, String>(type, result);
    }

    private String runFeatureWithJUnitFormatter(final CucumberFeature feature, final Map<String, String> stepsToResult, final long stepHookDuration)
            throws Throwable {
        return runFeatureWithJUnitFormatter(feature, stepsToResult, Collections.<SimpleEntry<String, String>>emptyList(), stepHookDuration);
    }

    private String runFeatureWithJUnitFormatter(final CucumberFeature feature, final Map<String, String> stepsToResult,
                                                final List<SimpleEntry<String, String>> hooks, final long stepHookDuration) throws Throwable {
        final RuntimeOptions runtimeOptions = new RuntimeOptions(new Env());
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final ClasspathResourceLoader resourceLoader = new ClasspathResourceLoader(classLoader);
        final RuntimeGlue glue = createMockedRuntimeGlueThatMatchesTheSteps(stepsToResult, hooks);
        final Runtime runtime = new Runtime(resourceLoader, classLoader, asList(mock(Backend.class)), runtimeOptions, new StopWatch.Stub(stepHookDuration), glue);
        final File report = File.createTempFile("cucumber-jvm-junit", ".xml");
        final JUnitFormatter junitFormatter = createJUnitFormatter(report);

        feature.run(junitFormatter, junitFormatter, runtime);
        junitFormatter.done();
        junitFormatter.close();

        return new Scanner(new FileInputStream(report), "UTF-8").useDelimiter("\\A").next();
    }

    private void assertXmlEqual(String expectedPath, File actual) throws IOException, ParserConfigurationException, SAXException {
        XMLUnit.setIgnoreWhitespace(true);
        InputStreamReader control = new InputStreamReader(Thread.currentThread().getContextClassLoader().getResourceAsStream(expectedPath), "UTF-8");
        Diff diff = new Diff(control, new FileReader(actual));
        assertTrue("XML files are similar " + diff, diff.identical());
    }

    private void assertXmlEqual(String expected, String actual) throws SAXException, IOException {
        XMLUnit.setIgnoreWhitespace(true);
        Diff diff = new Diff(expected, actual);
        assertTrue("XML files are similar " + diff + "\nFormatterOutput = " + actual, diff.identical());
    }

    private JUnitFormatter createJUnitFormatter(final File report) throws IOException {
        return new JUnitFormatter(Utils.toURL(report.getAbsolutePath()));
    }

    private RuntimeGlue createMockedRuntimeGlueThatMatchesTheSteps(Map<String, String> stepsToResult,
                                                                   final List<SimpleEntry<String, String>> hooks) throws Throwable {
        RuntimeGlue glue = mock(RuntimeGlue.class);
        mockSteps(glue, stepsToResult);
        mockHooks(glue, hooks);
        return glue;
    }

    private void mockSteps(RuntimeGlue glue, Map<String, String> stepsToResult) throws Throwable {
        for (String stepName : stepsToResult.keySet()) {
            if (!"undefined".equals(stepsToResult.get(stepName))) {
                StepDefinitionMatch matchStep = mock(StepDefinitionMatch.class);
                when(glue.stepDefinitionMatch(anyString(), stepWithName(stepName), (I18n) any())).thenReturn(matchStep);
                if ("pending".equals(stepsToResult.get(stepName))) {
                    doThrow(new PendingException()).when(matchStep).runStep((I18n) any());
                } else if ("failed".equals(stepsToResult.get(stepName))) {
                    AssertionFailedError error = mockAssertionFailedError();
                    doThrow(error).when(matchStep).runStep((I18n) any());
                } else if (!"passed".equals(stepsToResult.get(stepName)) &&
                        !"skipped".equals(stepsToResult.get(stepName))) {
                    fail("Cannot mock step to the result: " + stepsToResult.get(stepName));
                }
            }
        }
    }

    private void mockHooks(RuntimeGlue glue, final List<SimpleEntry<String, String>> hooks) throws Throwable {
        List<HookDefinition> beforeHooks = new ArrayList<HookDefinition>();
        List<HookDefinition> afterHooks = new ArrayList<HookDefinition>();
        for (SimpleEntry<String, String> hookEntry : hooks) {
            mockHook(hookEntry, beforeHooks, afterHooks);
        }
        if (beforeHooks.size() != 0) {
            when(glue.getBeforeHooks()).thenReturn(beforeHooks);
        }
        if (afterHooks.size() != 0) {
            when(glue.getAfterHooks()).thenReturn(afterHooks);
        }
    }

    private void mockHook(SimpleEntry<String, String> hookEntry, List<HookDefinition> beforeHooks,
                          List<HookDefinition> afterHooks) throws Throwable {
        HookDefinition hook = mock(HookDefinition.class);
        when(hook.matches(anyCollectionOf(Tag.class))).thenReturn(true);
        if (hookEntry.getValue().equals("failed")) {
            AssertionFailedError error = mockAssertionFailedError();
            doThrow(error).when(hook).execute((cucumber.api.Scenario) any());
        }
        if ("before".equals(hookEntry.getKey())) {
            beforeHooks.add(hook);
        } else if ("after".equals(hookEntry.getKey())) {
            afterHooks.add(hook);
        } else {
            fail("Only before and after hooks are allowed, hook type found was: " + hookEntry.getKey());
        }
    }

    private Step stepWithName(String name) {
        return argThat(new StepMatcher(name));
    }

    private AssertionFailedError mockAssertionFailedError() {
        AssertionFailedError error = mock(AssertionFailedError.class);
        Answer<Object> printStackTraceHandler = new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                PrintWriter writer = (PrintWriter) invocation.getArguments()[0];
                writer.print("the stack trace");
                return null;
            }
        };
        doAnswer(printStackTraceHandler).when(error).printStackTrace((PrintWriter) any());
        return error;
    }

    private String uri() {
        return "uri";
    }

    private Feature feature(String featureName) {
        Feature feature = mock(Feature.class);
        when(feature.getName()).thenReturn(featureName);
        return feature;
    }

    private Scenario scenario(String scenarioName) {
        Scenario scenario = mock(Scenario.class);
        when(scenario.getName()).thenReturn(scenarioName);
        return scenario;
    }

    private Step step(String keyword, String stepName) {
        Step step = mock(Step.class);
        when(step.getKeyword()).thenReturn(keyword);
        when(step.getName()).thenReturn(stepName);
        return step;
    }

    private Match match() {
        return mock(Match.class);
    }

    private Result result(String status) {
        return result(status, null);
    }

    private Result result(String status, Long duration) {
        return new Result(status, duration, null);
    }

    private Long milliSeconds(int milliSeconds) {
        return milliSeconds * 1000000L;
    }
}
