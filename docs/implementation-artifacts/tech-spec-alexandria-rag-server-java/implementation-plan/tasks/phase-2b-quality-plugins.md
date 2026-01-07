# Phase 2B: Quality Plugins (pom.xml)

> **Note**: Cette phase n'utilise pas de TDD. Les plugins sont validés par l'exécution des analyses.

## Objectif

Configurer tous les plugins de qualité Maven pour l'analyse statique, le formatage et le mutation testing.

## Prérequis

- Phase 1 complétée (structure Maven fonctionnelle)
- Phase 2A complétée (Makefile et scripts disponibles)

---

## Vue d'ensemble des plugins

| Plugin | Rôle | Phase d'exécution |
|--------|------|-------------------|
| Spotless | Formatage automatique | compile |
| Checkstyle | Style de code | validate |
| PMD | Analyse de code source | verify |
| SpotBugs | Analyse de bytecode | verify |
| Error Prone | Détection bugs à la compilation | compile (profil) |
| PIT | Mutation testing | manuel |
| OWASP DC | Vulnérabilités dépendances | manuel |
| SonarCloud | Qualité de code | CI |
| Maven Enforcer | Règles de build | validate |

---

- [ ] **Task 1: Add Quality Plugin Versions to pom.xml**
  - File: `pom.xml`
  - Location: Section `<properties>`
  - Action: Ajouter les versions des plugins
    ```xml
    <properties>
        <!-- ... existing properties ... -->

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- Versions des plugins de qualité                              -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <spotless.version>3.1.0</spotless.version>
        <google-java-format.version>1.33.0</google-java-format.version>
        <checkstyle.version>13.0.0</checkstyle.version>
        <checkstyle-plugin.version>3.6.0</checkstyle-plugin.version>
        <pmd-plugin.version>3.28.0</pmd-plugin.version>
        <pmd.version>7.20.0</pmd.version>
        <spotbugs.version>4.9.8</spotbugs.version>
        <spotbugs-plugin.version>4.9.8.2</spotbugs-plugin.version>
        <findsecbugs.version>1.14.0</findsecbugs.version>
        <error-prone.version>2.45.0</error-prone.version>
        <owasp-dc.version>12.1.9</owasp-dc.version>
        <sonar.version>5.5.0.6356</sonar.version>
        <pitest.version>1.22.0</pitest.version>
        <pitest-junit5.version>1.2.3</pitest-junit5.version>
        <enforcer.version>3.6.2</enforcer.version>
        <git-code-format.version>5.4</git-code-format.version>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- Skips par défaut (profil dev actif par défaut)               -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <skipITs>true</skipITs>
        <spotless.check.skip>true</spotless.check.skip>
        <checkstyle.skip>true</checkstyle.skip>
        <pmd.skip>true</pmd.skip>
        <spotbugs.skip>true</spotbugs.skip>
        <dependency-check.skip>true</dependency-check.skip>

        <!-- SonarCloud -->
        <sonar.organization>${env.GITHUB_REPOSITORY_OWNER}</sonar.organization>
        <sonar.host.url>https://sonarcloud.io</sonar.host.url>
    </properties>
    ```
  - Notes:
    - Spotless 3.1.0 : JRE 17+ requis pour exécuter Maven
    - google-java-format 1.33.0 : JDK 21+ requis pour exécuter le formateur
    - Checkstyle 13.0.0 : JDK 21+ requis (breaking change depuis 13.0.0)
    - Les skips par défaut permettent un build rapide en dev
    - **Lombok 1.18.42+** requis si utilisé avec Error Prone sur JDK 25 (issue #3940)

---

- [ ] **Task 1.5: Create .mvn/jvm.config (OBLIGATOIRE)**
  - File: `.mvn/jvm.config`
  - Action: Créer le fichier avec les exports JDK obligatoires pour Error Prone et google-java-format
    ```
    --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
    --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
    --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
    ```
  - Notes:
    - **CRITIQUE** : Sans ce fichier, Error Prone et google-java-format échoueront sur JDK 16+ (JEP 396)
    - Requis pour les accès aux APIs internes du compilateur Java

---

- [ ] **Task 2: Configure Spotless Plugin**
  - File: `pom.xml`
  - Location: Section `<build><plugins>`
  - Action: Ajouter le plugin Spotless
    ```xml
    <!-- ════════════════════════════════════════════════════════ -->
    <!-- Spotless - Formatage automatique                         -->
    <!-- ════════════════════════════════════════════════════════ -->
    <plugin>
        <groupId>com.diffplug.spotless</groupId>
        <artifactId>spotless-maven-plugin</artifactId>
        <version>${spotless.version}</version>
        <configuration>
            <java>
                <googleJavaFormat>
                    <version>${google-java-format.version}</version>
                    <style>GOOGLE</style>
                </googleJavaFormat>
                <removeUnusedImports/>
                <importOrder>
                    <order>java,javax,org,com,dev.alexandria</order>
                </importOrder>
            </java>
            <upToDateChecking>
                <enabled>true</enabled>
            </upToDateChecking>
        </configuration>
        <executions>
            <execution>
                <goals><goal>check</goal></goals>
                <phase>compile</phase>
            </execution>
        </executions>
    </plugin>
    ```
  - Notes:
    - Utilise Google Java Format, ordre des imports cohérent
    - **Breaking change Spotless 3.x** : `removeWildcardImports` renommé en `forbidWildcardImports`
    - **Conflit potentiel** : Spotless et git-code-format-maven-plugin peuvent tous deux installer des hooks Git. Recommandation : utiliser l'un ou l'autre, pas les deux

---

- [ ] **Task 3: Configure Git Hooks Plugin (Cosium)**
  - File: `pom.xml`
  - Location: Section `<build><plugins>`
  - Action: Ajouter le plugin Git Code Format
    ```xml
    <!-- ════════════════════════════════════════════════════════ -->
    <!-- Git Hooks - Installation automatique (Cosium)            -->
    <!-- ════════════════════════════════════════════════════════ -->
    <plugin>
        <groupId>com.cosium.code</groupId>
        <artifactId>git-code-format-maven-plugin</artifactId>
        <version>${git-code-format.version}</version>
        <executions>
            <execution>
                <id>install-formatter-hook</id>
                <goals><goal>install-hooks</goal></goals>
            </execution>
        </executions>
        <dependencies>
            <dependency>
                <groupId>com.cosium.code</groupId>
                <artifactId>google-java-format</artifactId>
                <version>${git-code-format.version}</version>
            </dependency>
        </dependencies>
    </plugin>
    ```
  - Notes: Installe automatiquement les hooks Git lors du premier `mvn compile`

---

- [ ] **Task 4: Configure Checkstyle Plugin**
  - File: `pom.xml`
  - Location: Section `<build><plugins>`
  - Action: Ajouter le plugin Checkstyle
    ```xml
    <!-- ════════════════════════════════════════════════════════ -->
    <!-- Checkstyle - Style de code                               -->
    <!-- ════════════════════════════════════════════════════════ -->
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-checkstyle-plugin</artifactId>
        <version>${checkstyle-plugin.version}</version>
        <dependencies>
            <dependency>
                <groupId>com.puppycrawl.tools</groupId>
                <artifactId>checkstyle</artifactId>
                <version>${checkstyle.version}</version>
            </dependency>
        </dependencies>
        <configuration>
            <configLocation>google_checks.xml</configLocation>
            <consoleOutput>true</consoleOutput>
            <failsOnError>true</failsOnError>
            <violationSeverity>warning</violationSeverity>
            <includeTestSourceDirectory>true</includeTestSourceDirectory>
        </configuration>
        <executions>
            <execution>
                <phase>validate</phase>
                <goals><goal>check</goal></goals>
            </execution>
        </executions>
    </plugin>
    ```
  - Notes:
    - Utilise google_checks.xml (bundled dans Checkstyle)
    - **Support Java 25 partiel** : Le parser ANTLR a été mis à jour pour Java 25, incluant JEP 512 `import module` (PR #18079). Cependant, certains checks d'import ont des limitations :
      - `UnusedImports` : Ne peut pas valider les imports de modules
      - `CustomImportOrder`, `ImportOrder`, `IllegalImport` : Mises à jour en cours (#18127)
    - Issues de suivi : #18361 (NoClassDefFound JDK 25 - corrigé), #17919 (support `import module` - corrigé)

---

- [ ] **Task 5: Configure PMD Plugin**
  - File: `pom.xml`
  - Location: Section `<build><plugins>`
  - Action: Ajouter le plugin PMD
    ```xml
    <!-- ════════════════════════════════════════════════════════ -->
    <!-- PMD - Analyse de code source                             -->
    <!-- ════════════════════════════════════════════════════════ -->
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-pmd-plugin</artifactId>
        <version>${pmd-plugin.version}</version>
        <configuration>
            <targetJdk>25</targetJdk>
            <rulesets>
                <ruleset>/category/java/bestpractices.xml</ruleset>
                <ruleset>/category/java/errorprone.xml</ruleset>
                <ruleset>/category/java/codestyle.xml</ruleset>
            </rulesets>
            <analysisCache>true</analysisCache>
            <analysisCacheLocation>${project.build.directory}/pmd/pmd.cache</analysisCacheLocation>
            <printFailingErrors>true</printFailingErrors>
        </configuration>
        <executions>
            <execution>
                <phase>verify</phase>
                <goals>
                    <goal>check</goal>
                    <goal>cpd-check</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
    ```
  - Notes: CPD (Copy-Paste Detection) inclus. Cache activé pour builds incrémentaux.

---

- [ ] **Task 6: Configure SpotBugs Plugin**
  - File: `pom.xml`
  - Location: Section `<build><plugins>`
  - Action: Ajouter le plugin SpotBugs avec FindSecBugs
    ```xml
    <!-- ════════════════════════════════════════════════════════ -->
    <!-- SpotBugs - Analyse de bytecode                           -->
    <!-- ════════════════════════════════════════════════════════ -->
    <plugin>
        <groupId>com.github.spotbugs</groupId>
        <artifactId>spotbugs-maven-plugin</artifactId>
        <version>${spotbugs-plugin.version}</version>
        <dependencies>
            <dependency>
                <groupId>com.github.spotbugs</groupId>
                <artifactId>spotbugs</artifactId>
                <version>${spotbugs.version}</version>
            </dependency>
        </dependencies>
        <configuration>
            <effort>Max</effort>
            <threshold>Medium</threshold>
            <xmlOutput>true</xmlOutput>
            <plugins>
                <plugin>
                    <groupId>com.h3xstream.findsecbugs</groupId>
                    <artifactId>findsecbugs-plugin</artifactId>
                    <version>${findsecbugs.version}</version>
                </plugin>
            </plugins>
        </configuration>
        <executions>
            <execution>
                <phase>verify</phase>
                <goals><goal>check</goal></goals>
            </execution>
        </executions>
    </plugin>
    ```
  - Notes:
    - SpotBugs 4.9.8 supporte Java 25 grâce à **ASM 9.9** et BCEL 6.11.0 (support ajouté dans 4.9.7)
    - FindSecBugs 1.14.0 : support complet du namespace `jakarta.*` (Spring Boot 3.x), 144 types de vulnérabilités, requiert SpotBugs 4.8.3+

---

- [ ] **Task 7: Configure OWASP Dependency-Check Plugin**
  - File: `pom.xml`
  - Location: Section `<build><plugins>`
  - Action: Ajouter le plugin OWASP
    ```xml
    <!-- ════════════════════════════════════════════════════════ -->
    <!-- OWASP Dependency-Check - Vulnérabilités                  -->
    <!-- ════════════════════════════════════════════════════════ -->
    <plugin>
        <groupId>org.owasp</groupId>
        <artifactId>dependency-check-maven</artifactId>
        <version>${owasp-dc.version}</version>
        <configuration>
            <nvdApiKeyEnvironmentVariable>NVD_API_KEY</nvdApiKeyEnvironmentVariable>
            <!-- OSS Index credentials (requis depuis septembre 2025) -->
            <ossIndexUsername>${env.OSS_INDEX_USERNAME}</ossIndexUsername>
            <ossIndexPassword>${env.OSS_INDEX_PASSWORD}</ossIndexPassword>
            <failBuildOnCVSS>7</failBuildOnCVSS>
            <formats>
                <format>HTML</format>
                <format>JSON</format>
            </formats>
            <suppressionFiles>
                <suppressionFile>${project.basedir}/owasp-suppressions.xml</suppressionFile>
            </suppressionFiles>
            <!-- Désactiver analyseurs non-Java -->
            <assemblyAnalyzerEnabled>false</assemblyAnalyzerEnabled>
            <nodeAuditAnalyzerEnabled>false</nodeAuditAnalyzerEnabled>
            <retireJsAnalyzerEnabled>false</retireJsAnalyzerEnabled>
        </configuration>
    </plugin>
    ```
  - Notes:
    - NVD_API_KEY accélère l'analyse (plusieurs heures → quelques minutes). Voir Phase 2C Task 7.
    - **Breaking change septembre 2025** : Sonatype OSS Index n'accepte plus les requêtes anonymes. Sans credentials, l'analyseur OSS Index se désactive automatiquement.
    - **OSS Index credentials** : S'inscrire sur https://ossindex.sonatype.org/ pour obtenir un token API (email = username, token = password)
    - **Configuration OSS Index** (2 options) :
      1. Variables d'environnement : `OSS_INDEX_USERNAME` et `OSS_INDEX_PASSWORD`
      2. Dans `~/.m2/settings.xml` :
         ```xml
         <server>
             <id>ossindex</id>
             <username>your-email@example.com</username>
             <password>your-api-token</password>
         </server>
         ```
    - **Namespace suppressions** : Utiliser `https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd` (ne PAS utiliser `dependency-check.github.io` - issue #7627)
    - **Note** : Le repository original `jeremylong/DependencyCheck` a été archivé (septembre 2025). Le repo actif est maintenant `dependency-check/DependencyCheck`.

---

- [ ] **Task 8: Configure SonarCloud Plugin**
  - File: `pom.xml`
  - Location: Section `<build><plugins>`
  - Action: Ajouter le plugin Sonar
    ```xml
    <!-- ════════════════════════════════════════════════════════ -->
    <!-- SonarCloud - Qualité de code                             -->
    <!-- ════════════════════════════════════════════════════════ -->
    <plugin>
        <groupId>org.sonarsource.scanner.maven</groupId>
        <artifactId>sonar-maven-plugin</artifactId>
        <version>${sonar.version}</version>
    </plugin>
    ```
  - Notes:
    - Configuration principale via properties et CI (voir Phase 2C)
    - **INCOMPATIBILITÉ CRITIQUE Java 25** : SonarQube/SonarCloud ne peut pas analyser le code Java 25 (janvier 2026). L'analyseur supporte jusqu'à Java 24. Le support Java 25 est bloqué par Eclipse Compiler for Java (ECJ) upstream.
    - **Timeline** : Java 25 ne sera **pas ciblé avant SonarQube Server 2026.1 LTA**. La première LTA avec support complet Java 25 sera probablement **2027.1**.
    - **Options de contournement** :
      1. Accepter une analyse dégradée avec des erreurs de parsing potentielles
      2. Définir `sonar.java.source=24` si le code n'utilise pas de syntaxe Java 25 spécifique
      3. Utiliser `sonar.java.source=25` + `sonar.java.enablePreview=true` pour mitiger certains problèmes
      4. Désactiver temporairement SonarCloud jusqu'au support officiel (2027)

---

- [ ] **Task 9: Configure PIT Mutation Testing Plugin**
  - File: `pom.xml`
  - Location: Section `<build><plugins>`
  - Action: Ajouter le plugin PIT
    ```xml
    <!-- ════════════════════════════════════════════════════════ -->
    <!-- PIT - Mutation Testing                                   -->
    <!-- ════════════════════════════════════════════════════════ -->
    <plugin>
        <groupId>org.pitest</groupId>
        <artifactId>pitest-maven</artifactId>
        <version>${pitest.version}</version>
        <configuration>
            <targetClasses>
                <param>dev.alexandria.*</param>
            </targetClasses>
            <targetTests>
                <param>dev.alexandria.*Test</param>
                <param>dev.alexandria.**.*Test</param>
            </targetTests>
            <excludedClasses>
                <param>dev.alexandria.config.*</param>
                <param>dev.alexandria.*Application</param>
            </excludedClasses>
            <mutators>STRONGER</mutators>
            <threads>4</threads>
            <timeoutConstant>10000</timeoutConstant>
            <timeoutFactor>1.5</timeoutFactor>
            <outputFormats>
                <param>HTML</param>
                <param>XML</param>
            </outputFormats>
            <timestampedReports>false</timestampedReports>
            <mutationThreshold>70</mutationThreshold>
            <coverageThreshold>70</coverageThreshold>
            <withHistory>true</withHistory>
            <historyInputFile>.pitest-history</historyInputFile>
            <historyOutputFile>.pitest-history</historyOutputFile>
        </configuration>
        <dependencies>
            <dependency>
                <groupId>org.pitest</groupId>
                <artifactId>pitest-junit5-plugin</artifactId>
                <version>${pitest-junit5.version}</version>
            </dependency>
        </dependencies>
    </plugin>
    ```
  - Notes:
    - PITest 1.22.0 intègre ASM 9.9 qui garantit le support complet du bytecode Java 25
    - `mutators=STRONGER` pour une meilleure détection des mutations
    - `withHistory` accélère les runs suivants (stocke les résultats d'analyse)
    - Seuil de 70% mutation coverage
    - Exécution manuelle via `make pit`

---

- [ ] **Task 10: Configure Maven Enforcer Plugin**
  - File: `pom.xml`
  - Location: Section `<build><plugins>`
  - Action: Ajouter le plugin Enforcer
    ```xml
    <!-- ════════════════════════════════════════════════════════ -->
    <!-- Maven Enforcer - Règles de build                         -->
    <!-- ════════════════════════════════════════════════════════ -->
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-enforcer-plugin</artifactId>
        <version>${enforcer.version}</version>
        <executions>
            <execution>
                <id>enforce</id>
                <goals><goal>enforce</goal></goals>
                <configuration>
                    <rules>
                        <requireJavaVersion>
                            <version>[25,)</version>
                            <message>Java 25+ requis!</message>
                        </requireJavaVersion>
                        <requireMavenVersion>
                            <version>[3.8.1,)</version>
                        </requireMavenVersion>
                        <dependencyConvergence/>
                        <banDuplicatePomDependencyVersions/>
                    </rules>
                </configuration>
            </execution>
        </executions>
    </plugin>
    ```
  - Notes: Garantit Java 25+, Maven 3.8.1+, pas de conflits de dépendances

---

- [ ] **Task 11: Add Maven Profiles**
  - File: `pom.xml`
  - Location: Section `<profiles>`
  - Action: Ajouter les profils dev, ci, static-analysis, error-prone
    ```xml
    <profiles>
        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- Profil DEV (par défaut) - Build rapide                       -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <profile>
            <id>dev</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <!-- Utilise les skips définis dans properties -->
        </profile>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- Profil CI - Toutes les vérifications                         -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <profile>
            <id>ci</id>
            <activation>
                <property>
                    <name>env.CI</name>
                    <value>true</value>
                </property>
            </activation>
            <properties>
                <skipITs>false</skipITs>
                <spotless.check.skip>false</spotless.check.skip>
                <checkstyle.skip>false</checkstyle.skip>
                <pmd.skip>false</pmd.skip>
                <spotbugs.skip>false</spotbugs.skip>
                <dependency-check.skip>false</dependency-check.skip>
            </properties>
        </profile>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- Profil Static Analysis - Analyse statique locale             -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <profile>
            <id>static-analysis</id>
            <properties>
                <skipITs>true</skipITs>
                <spotless.check.skip>false</spotless.check.skip>
                <checkstyle.skip>false</checkstyle.skip>
                <pmd.skip>false</pmd.skip>
                <spotbugs.skip>false</spotbugs.skip>
                <dependency-check.skip>true</dependency-check.skip>
            </properties>
        </profile>

        <!-- ════════════════════════════════════════════════════════════ -->
        <!-- Profil Error Prone - Compilation avec détection de bugs      -->
        <!-- ════════════════════════════════════════════════════════════ -->
        <profile>
            <id>error-prone</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-compiler-plugin</artifactId>
                        <version>3.13.0</version>
                        <configuration>
                            <source>25</source>
                            <target>25</target>
                            <compilerArgs>
                                <arg>-XDcompilePolicy=simple</arg>
                                <arg>--should-stop=ifError=FLOW</arg>
                                <arg>-Xplugin:ErrorProne</arg>
                                <!-- Workaround Lombok < 1.18.42 + JDK 25 (issue projectlombok/lombok#3940) -->
                                <!-- Ces 3 checks causent AbstractMethodError avec JavaDoc -->
                                <!-- Solution recommandée : Lombok 1.18.42+ -->
                                <arg>-Xep:InvalidBlockTag:OFF</arg>
                                <arg>-Xep:InvalidInlineTag:OFF</arg>
                                <arg>-Xep:AlmostJavadoc:OFF</arg>
                            </compilerArgs>
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>com.google.errorprone</groupId>
                                    <artifactId>error_prone_core</artifactId>
                                    <version>${error-prone.version}</version>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
    ```
  - Notes:
    - `dev` : Build rapide, skips activés
    - `ci` : Auto-activé quand `CI=true`, toutes vérifications
    - `static-analysis` : Analyse statique locale sans OWASP
    - `error-prone` : Compilation avec détection bugs. **Requiert JDK 21 minimum** pour exécuter.
    - **Problème connu Lombok + JDK 25** : Les checks JavaDoc (`InvalidBlockTag`, `InvalidInlineTag`, `AlmostJavadoc`) causent `AbstractMethodError` avec Lombok < 1.18.42 sur JDK 25. **Solution recommandée** : Lombok 1.18.42+. Les workarounds `-Xep:*:OFF` sont inclus en attendant.

---

- [ ] **Task 12: Add Reporting Section**
  - File: `pom.xml`
  - Location: Section `<reporting>`
  - Action: Ajouter la configuration des rapports
    ```xml
    <reporting>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-pmd-plugin</artifactId>
                <version>${pmd-plugin.version}</version>
            </plugin>
            <plugin>
                <groupId>com.github.spotbugs</groupId>
                <artifactId>spotbugs-maven-plugin</artifactId>
                <version>${spotbugs-plugin.version}</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>${checkstyle-plugin.version}</version>
            </plugin>
        </plugins>
    </reporting>
    ```
  - Notes: Permet `mvn site` pour générer tous les rapports HTML

---

- [ ] **Task 13: Create OWASP Suppressions File**
  - File: `owasp-suppressions.xml`
  - Action: Créer le fichier de suppressions
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
        <!--
        Add suppressions for false positives here.
        Example:
        <suppress>
            <notes>False positive - not applicable to our usage</notes>
            <packageUrl regex="true">^pkg:maven/com\.example/.*$</packageUrl>
            <cve>CVE-2023-XXXXX</cve>
        </suppress>
        -->
    </suppressions>
    ```
  - Notes: À enrichir au fur et à mesure des faux positifs détectés

---

## Résumé des profils Maven

| Profil | Activation | Usage |
|--------|------------|-------|
| `dev` | Par défaut | Build rapide pendant le dev |
| `ci` | Automatique si `CI=true` | GitHub Actions |
| `static-analysis` | `-Pstatic-analysis` | Analyse locale complète |
| `error-prone` | `-Perror-prone` | Compilation avec détection bugs |

---

## Commandes utiles

| Commande | Description |
|----------|-------------|
| `mvn compile` | Build rapide (profil dev) |
| `mvn compile -Perror-prone` | Build avec Error Prone |
| `mvn verify -Pstatic-analysis` | Analyse statique complète |
| `mvn spotless:apply` | Formater le code |
| `mvn spotless:check` | Vérifier le formatage |
| `mvn pmd:pmd` | Générer rapport PMD HTML |
| `mvn spotbugs:gui` | Interface graphique SpotBugs |
| `mvn pitest:mutationCoverage` | Mutation testing |

---

## Critères de validation

- [ ] `mvn compile` réussit sans erreur
- [ ] `mvn spotless:check` valide le formatage
- [ ] `mvn checkstyle:check` passe
- [ ] `mvn pmd:check` passe
- [ ] `mvn spotbugs:check` passe
- [ ] `mvn verify -Pstatic-analysis` complète sans erreur
- [ ] `mvn compile -Perror-prone` compile avec détection bugs
