package io.github.spencerpark.ijava.magics;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class JavaMagicsTest {
    private static final String SOURCE = """
            package com.example;

            /**
             * Sample class docs.
             */
            public class SampleClass {

                /**
                 * Count docs.
                 */
                private int count;

                public SampleClass() {
                }

                /**
                 * Target docs.
                 */
                @Annotated
                public void targetMethod() {
                }

                public void overloaded(int value) {
                }

                public void overloaded(String value) {
                }
            }

            /**
             * Sample interface docs.
             */
            interface SampleInterface {
                void marker();
            }
            """;

    private static Path sourceFile;
    private CapturingKernels.CapturingKernel kernel;

    @BeforeClass
    public static void createFixture() throws Exception {
        Path dir = Files.createTempDirectory(Path.of("build", "tmp"), "java-magics");
        sourceFile = dir.resolve("Sample.java");
        Files.writeString(sourceFile, SOURCE);
    }

    @Before
    public void setUp() throws Exception {
        kernel = CapturingKernels.install();
        kernel.reset();
    }

    @After
    public void tearDown() throws Exception {
        CapturingKernels.uninstall();
    }

    @Test
    public void methodByNameHelpDisplaysUsage() throws Exception {
        new JavaMagics().javasrcMethodByName(List.of("--help"), "");
        Assert.assertTrue(displayedText().contains("%%javasrcMethodByName"));
    }

    @Test
    public void methodByNameNoArgsDisplaysError() throws Exception {
        new JavaMagics().javasrcMethodByName(List.of(), "");
        Assert.assertTrue(displayedText().contains("Error: expected usage"));
    }

    @Test
    public void methodByNameMissingFileDisplaysError() throws Exception {
        Path missing = sourceFile.getParent().resolve("missing-" + UUID.randomUUID() + ".java");
        new JavaMagics().javasrcMethodByName(List.of("com.example.SampleClass", "targetMethod"), missing.toString());
        Assert.assertTrue(displayedText().contains("Error: failed to read file"));
    }

    @Test
    public void methodByNameExtractsMethod() throws Exception {
        new JavaMagics().javasrcMethodByName(List.of("com.example.SampleClass", "targetMethod"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("targetMethod"));
    }

    @Test
    public void methodByNameMultipleMatchesRequiresIndex() throws Exception {
        new JavaMagics().javasrcMethodByName(List.of("com.example.SampleClass", "overloaded"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Found 2 matching methods"));
    }

    @Test
    public void methodByNameSelectIndexPicksMethod() throws Exception {
        new JavaMagics().javasrcMethodByName(
                List.of("selectIndex=1", "com.example.SampleClass", "overloaded"),
                sourceFile.toString());
        Assert.assertTrue(displayedText().contains("String value"));
    }

    @Test
    public void methodByNameMethodRegexFindsMethods() throws Exception {
        new JavaMagics().javasrcMethodByName(List.of("methodRegex=overloaded", "selectIndex=0", "com.example.SampleClass"),
                sourceFile.toString());
        Assert.assertTrue(displayedText().contains("int value"));
    }

    @Test
    public void methodByNameMissingClassDisplaysError() throws Exception {
        new JavaMagics().javasrcMethodByName(List.of("com.example.MissingClass", "targetMethod"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Class `com.example.MissingClass` not found"));
    }

    @Test
    public void methodByNameInterfaceDisplaysInterfaceMessage() throws Exception {
        new JavaMagics().javasrcMethodByName(List.of("com.example.SampleInterface", "marker"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("is an interface in file"));
    }

    @Test
    public void methodByAnnotationNameHelpDisplaysUsage() throws Exception {
        new JavaMagics().javasrcMethodByAnnotationName(List.of("--help"), "");
        Assert.assertTrue(displayedText().contains("%%javasrcMethodByAnnotationName"));
    }

    @Test
    public void methodByAnnotationNameNoArgsDisplaysError() throws Exception {
        new JavaMagics().javasrcMethodByAnnotationName(List.of(), "");
        Assert.assertTrue(displayedText().contains("Error: expected usage"));
    }

    @Test
    public void methodByAnnotationNameExtractsAnnotatedMethod() throws Exception {
        new JavaMagics().javasrcMethodByAnnotationName(List.of("com.example.SampleClass", "Annotated"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("targetMethod"));
    }

    @Test
    public void methodByAnnotationNameMissingAnnotationDisplaysMessage() throws Exception {
        new JavaMagics().javasrcMethodByAnnotationName(List.of("com.example.SampleClass", "MissingAnnotation"),
                sourceFile.toString());
        Assert.assertTrue(displayedText().contains("No methods annotated"));
    }

    @Test
    public void interfaceByNameHelpDisplaysUsage() throws Exception {
        new JavaMagics().javasrcInterfaceByName(List.of("--help"), "");
        Assert.assertTrue(displayedText().contains("%%javasrcInterfaceByName"));
    }

    @Test
    public void interfaceByNameNoArgsDisplaysError() throws Exception {
        new JavaMagics().javasrcInterfaceByName(List.of(), "");
        Assert.assertTrue(displayedText().contains("Error: expected usage"));
    }

    @Test
    public void interfaceByNameExtractsInterface() throws Exception {
        new JavaMagics().javasrcInterfaceByName(List.of("com.example.SampleInterface"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("SampleInterface"));
    }

    @Test
    public void interfaceByNameClassDisplaysClassMessage() throws Exception {
        new JavaMagics().javasrcInterfaceByName(List.of("com.example.SampleClass"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Found class `SampleClass`"));
    }

    @Test
    public void classByNameHelpDisplaysUsage() throws Exception {
        new JavaMagics().javasrcClassByName(List.of("--help"), "");
        Assert.assertTrue(displayedText().contains("%%javasrcClassByName"));
    }

    @Test
    public void classByNameNoArgsDisplaysError() throws Exception {
        new JavaMagics().javasrcClassByName(List.of(), "");
        Assert.assertTrue(displayedText().contains("Error: expected usage"));
    }

    @Test
    public void classByNameExtractsClass() throws Exception {
        new JavaMagics().javasrcClassByName(List.of("com.example.SampleClass"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("public class SampleClass"));
    }

    @Test
    public void classByNameInterfaceDisplaysInterfaceMessage() throws Exception {
        new JavaMagics().javasrcClassByName(List.of("com.example.SampleInterface"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Found interface `SampleInterface`"));
    }

    @Test
    public void javasrcListHelpDisplaysUsage() throws Exception {
        new JavaMagics().javasrcList(List.of("--help"), "");
        Assert.assertTrue(displayedText().contains("%%javasrcList"));
    }

    @Test
    public void javasrcListSummarizesFile() throws Exception {
        new JavaMagics().javasrcList(List.of(), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Summary of"));
        Assert.assertTrue(displayedText().contains("SampleClass"));
    }

    @Test
    public void constructorByNameHelpDisplaysUsage() throws Exception {
        new JavaMagics().javasrcConstructorByName(List.of("--help"), "");
        Assert.assertTrue(displayedText().contains("%%javasrcConstructorByName"));
    }

    @Test
    public void constructorByNameNoArgsDisplaysError() throws Exception {
        new JavaMagics().javasrcConstructorByName(List.of(), "");
        Assert.assertTrue(displayedText().contains("Error: expected usage"));
    }

    @Test
    public void constructorByNameExtractsConstructor() throws Exception {
        new JavaMagics().javasrcConstructorByName(List.of("com.example.SampleClass"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("SampleClass()"));
    }

    @Test
    public void constructorByNameMissingClassDisplaysError() throws Exception {
        new JavaMagics().javasrcConstructorByName(List.of("com.example.MissingClass"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Class `com.example.MissingClass` not found"));
    }

    @Test
    public void fieldByNameHelpDisplaysUsage() throws Exception {
        new JavaMagics().javasrcFieldByName(List.of("--help"), "");
        Assert.assertTrue(displayedText().contains("%%javasrcFieldByName"));
    }

    @Test
    public void fieldByNameNoArgsDisplaysError() throws Exception {
        new JavaMagics().javasrcFieldByName(List.of(), "");
        Assert.assertTrue(displayedText().contains("Error: expected usage"));
    }

    @Test
    public void fieldByNameFullShowsDeclaration() throws Exception {
        new JavaMagics().javasrcFieldByName(List.of("com.example.SampleClass"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("int count"));
        Assert.assertTrue(displayedText().contains("private"));
    }

    @Test
    public void fieldByNameFilterMatchesField() throws Exception {
        new JavaMagics().javasrcFieldByName(List.of("com.example.SampleClass", "count"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("count"));
    }

    @Test
    public void fieldByNameMissingClassDisplaysError() throws Exception {
        new JavaMagics().javasrcFieldByName(List.of("com.example.MissingClass"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Class `com.example.MissingClass` not found"));
    }

    @Test
    public void javadocHelpDisplaysUsage() throws Exception {
        new JavaMagics().javasrcJavadoc(List.of("--help"), "");
        Assert.assertTrue(displayedText().contains("%%javasrcJavadoc"));
    }

    @Test
    public void javadocNoArgsDisplaysError() throws Exception {
        new JavaMagics().javasrcJavadoc(List.of(), "");
        Assert.assertTrue(displayedText().contains("Error: expected usage"));
    }

    @Test
    public void javadocShowsClassJavadoc() throws Exception {
        new JavaMagics().javasrcJavadoc(List.of("com.example.SampleClass"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Sample class docs."));
    }

    @Test
    public void javadocShowsMethodJavadoc() throws Exception {
        new JavaMagics().javasrcJavadoc(List.of("com.example.SampleClass", "targetMethod"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Target docs."));
    }

    @Test
    public void javadocShowsFieldJavadoc() throws Exception {
        new JavaMagics().javasrcJavadoc(List.of("com.example.SampleClass", "count"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Count docs."));
    }

    @Test
    public void javadocMissingMemberDisplaysError() throws Exception {
        new JavaMagics().javasrcJavadoc(List.of("com.example.SampleClass", "missingMember"), sourceFile.toString());
        Assert.assertTrue(displayedText().contains("Member `missingMember` not found"));
    }

    private String displayedText() {
        return String.join("\n", kernel.displayedTexts());
    }
}
