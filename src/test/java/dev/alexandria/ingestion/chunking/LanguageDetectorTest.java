package dev.alexandria.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageDetectorTest {

  @Test
  void detectsJavaFromPublicClassAndStaticVoidMain() {
    assertThat(
            LanguageDetector.detect(
                "public class Foo { public static void main(String[] args) {} }"))
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
    assertThat(LanguageDetector.detect("def foo():\n    print(\"hello\")")).isEqualTo("python");
  }

  @Test
  void detectsJavascriptFromConstAndConsoleLog() {
    assertThat(LanguageDetector.detect("const x = 1; console.log(x)")).isEqualTo("javascript");
  }

  @Test
  void detectsSqlFromSelectFromWhere() {
    assertThat(LanguageDetector.detect("SELECT * FROM users WHERE id = 1")).isEqualTo("sql");
  }

  @Test
  void detectsSqlCaseInsensitive() {
    assertThat(LanguageDetector.detect("select * from users where id = 1")).isEqualTo("sql");
  }

  @Test
  void returnsUnknownWhenNoPatternsMatchAboveThreshold() {
    assertThat(LanguageDetector.detect("some random text with no indicators")).isEqualTo("unknown");
  }

  @Test
  void detectsGoFromFuncAndFmt() {
    assertThat(LanguageDetector.detect("func main() { fmt.Println(\"hello\") }")).isEqualTo("go");
  }

  @Test
  void detectsBashFromEchoAndExport() {
    assertThat(LanguageDetector.detect("echo \"hello\"\nexport PATH=/usr/local/bin:$PATH"))
        .isEqualTo("bash");
  }

  @Test
  void detectsRustFromFnAndLetMut() {
    assertThat(LanguageDetector.detect("fn main() {\n    let mut x = 5;\n}")).isEqualTo("rust");
  }

  @Test
  void detectsYamlFromApiVersionAndKind() {
    assertThat(
            LanguageDetector.detect("apiVersion: v1\nkind: Service\nmetadata:\n  name: my-service"))
        .isEqualTo("yaml");
  }

  @Test
  void detectsXmlFromXmlDeclarationAndXmlns() {
    assertThat(
            LanguageDetector.detect(
                "<?xml version=\"1.0\"?>\n<beans xmlns:context=\"http://example.com\">"))
        .isEqualTo("xml");
  }

  @Test
  void returnsUnknownForSinglePatternMatch() {
    // Only one pattern match ("const ") is not enough -- need >= 2
    assertThat(LanguageDetector.detect("const x = 42")).isEqualTo("unknown");
  }

  @Test
  void returnsUnknownForEmptyInput() {
    assertThat(LanguageDetector.detect("")).isEqualTo("unknown");
  }

  @Test
  void returnsUnknownForNullInput() {
    assertThat(LanguageDetector.detect(null)).isEqualTo("unknown");
  }

  @Test
  void languageWithExactlyOneMatchIsNotDetected() {
    // Kills boundary mutation at line 58: score > 0 changed to score >= 0
    // With score >= 0, languages with 0 matches would be stored in the scores map,
    // but the >= 2 filter still blocks them. However, storing score 0 means
    // the max() could pick a language with score 1 if no language has score >= 2.
    // "const " alone matches javascript (1 pattern), which should return "unknown"
    // because score >= 2 is required. But if score > 0 becomes score >= 0,
    // ALL languages with 0 matches get stored, and max() could still return the
    // highest scorer. The real kill is: ensure a language with exactly 1 match
    // is stored correctly (score > 0 is true for score=1) but filtered by >= 2.
    // Actually, the mutation changes `if (score > 0)` to `if (score >= 0)`.
    // This means even languages scoring 0 get added. max() still returns highest.
    // We need a test where the behavior would differ. With score >= 0, all languages
    // (even 0-scoring) are in the map. The filter >= 2 still catches it.
    // The mutation SURVIVES because the filter >= 2 is the real guard.
    // This is an equivalent mutation -- document as justified.

    // Verify the boundary: score of exactly 2 IS detected (boundary of >= 2 filter)
    assertThat(LanguageDetector.detect("fn main() {\n    let mut x = 5;\n}"))
        .isEqualTo("rust"); // exactly 2 matches: "fn " and "let mut "

    // Score of exactly 1 is NOT detected
    assertThat(LanguageDetector.detect("fn something()"))
        .isEqualTo("unknown"); // only 1 match: "fn "
  }
}
