package dev.alexandria.ingestion.chunking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageDetectorTest {

    @Test
    void detectsJavaFromPublicClassAndStaticVoidMain() {
        assertEquals("java", LanguageDetector.detect("public class Foo { public static void main(String[] args) {} }"));
    }

    @Test
    void detectsJavaFromPublicClassAndImport() {
        assertEquals("java", LanguageDetector.detect("public class Foo { }\nimport java.util.List;"));
    }

    @Test
    void detectsPythonFromDefAndSelf() {
        assertEquals("python", LanguageDetector.detect("def foo():\n    self.bar = 1\n    pass"));
    }

    @Test
    void detectsPythonFromDefAndPrint() {
        assertEquals("python", LanguageDetector.detect("def foo():\n    print(\"hello\")"));
    }

    @Test
    void detectsJavascriptFromConstAndConsoleLog() {
        assertEquals("javascript", LanguageDetector.detect("const x = 1; console.log(x)"));
    }

    @Test
    void detectsSqlFromSelectFromWhere() {
        assertEquals("sql", LanguageDetector.detect("SELECT * FROM users WHERE id = 1"));
    }

    @Test
    void returnsUnknownWhenNoPatternsMatchAboveThreshold() {
        assertEquals("unknown", LanguageDetector.detect("some random text with no indicators"));
    }

    @Test
    void detectsGoFromFuncAndFmt() {
        assertEquals("go", LanguageDetector.detect("func main() { fmt.Println(\"hello\") }"));
    }

    @Test
    void detectsBashFromEchoAndExport() {
        assertEquals("bash", LanguageDetector.detect("echo \"hello\"\nexport PATH=/usr/local/bin:$PATH"));
    }

    @Test
    void detectsRustFromFnAndLetMut() {
        assertEquals("rust", LanguageDetector.detect("fn main() {\n    let mut x = 5;\n}"));
    }

    @Test
    void detectsYamlFromApiVersionAndKind() {
        assertEquals("yaml", LanguageDetector.detect("apiVersion: v1\nkind: Service\nmetadata:\n  name: my-service"));
    }

    @Test
    void detectsXmlFromXmlDeclarationAndXmlns() {
        assertEquals("xml", LanguageDetector.detect("<?xml version=\"1.0\"?>\n<beans xmlns:context=\"http://example.com\">"));
    }

    @Test
    void returnsUnknownForSinglePatternMatch() {
        // Only one pattern match ("const ") is not enough -- need >= 2
        assertEquals("unknown", LanguageDetector.detect("const x = 42"));
    }

    @Test
    void returnsUnknownForEmptyInput() {
        assertEquals("unknown", LanguageDetector.detect(""));
    }
}
