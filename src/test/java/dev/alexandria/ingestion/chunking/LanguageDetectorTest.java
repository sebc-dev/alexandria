package dev.alexandria.ingestion.chunking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageDetectorTest {

    @Test
    void detectsJavaFromPublicClassAndStaticVoidMain() {
        assertThat(LanguageDetector.detect("public class Foo { public static void main(String[] args) {} }"))
                .isEqualTo("java");
    }

    @Test
    void detectsJavaFromPublicClassAndImport() {
        assertThat(LanguageDetector.detect("public class Foo { }\nimport java.util.List;"))
                .isEqualTo("java");
    }

    @Test
    void detectsPythonFromDefAndSelf() {
        assertThat(LanguageDetector.detect("def foo():\n    self.bar = 1\n    pass"))
                .isEqualTo("python");
    }

    @Test
    void detectsPythonFromDefAndPrint() {
        assertThat(LanguageDetector.detect("def foo():\n    print(\"hello\")"))
                .isEqualTo("python");
    }

    @Test
    void detectsJavascriptFromConstAndConsoleLog() {
        assertThat(LanguageDetector.detect("const x = 1; console.log(x)"))
                .isEqualTo("javascript");
    }

    @Test
    void detectsSqlFromSelectFromWhere() {
        assertThat(LanguageDetector.detect("SELECT * FROM users WHERE id = 1"))
                .isEqualTo("sql");
    }

    @Test
    void detectsSqlCaseInsensitive() {
        assertThat(LanguageDetector.detect("select * from users where id = 1"))
                .isEqualTo("sql");
    }

    @Test
    void returnsUnknownWhenNoPatternsMatchAboveThreshold() {
        assertThat(LanguageDetector.detect("some random text with no indicators"))
                .isEqualTo("unknown");
    }

    @Test
    void detectsGoFromFuncAndFmt() {
        assertThat(LanguageDetector.detect("func main() { fmt.Println(\"hello\") }"))
                .isEqualTo("go");
    }

    @Test
    void detectsBashFromEchoAndExport() {
        assertThat(LanguageDetector.detect("echo \"hello\"\nexport PATH=/usr/local/bin:$PATH"))
                .isEqualTo("bash");
    }

    @Test
    void detectsRustFromFnAndLetMut() {
        assertThat(LanguageDetector.detect("fn main() {\n    let mut x = 5;\n}"))
                .isEqualTo("rust");
    }

    @Test
    void detectsYamlFromApiVersionAndKind() {
        assertThat(LanguageDetector.detect("apiVersion: v1\nkind: Service\nmetadata:\n  name: my-service"))
                .isEqualTo("yaml");
    }

    @Test
    void detectsXmlFromXmlDeclarationAndXmlns() {
        assertThat(LanguageDetector.detect("<?xml version=\"1.0\"?>\n<beans xmlns:context=\"http://example.com\">"))
                .isEqualTo("xml");
    }

    @Test
    void returnsUnknownForSinglePatternMatch() {
        // Only one pattern match ("const ") is not enough -- need >= 2
        assertThat(LanguageDetector.detect("const x = 42"))
                .isEqualTo("unknown");
    }

    @Test
    void returnsUnknownForEmptyInput() {
        assertThat(LanguageDetector.detect(""))
                .isEqualTo("unknown");
    }

    @Test
    void returnsUnknownForNullInput() {
        assertThat(LanguageDetector.detect(null))
                .isEqualTo("unknown");
    }
}
