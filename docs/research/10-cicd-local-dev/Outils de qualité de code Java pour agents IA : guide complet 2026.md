# Outils de qualité de code Java pour agents IA : guide complet 2026

La stack optimale pour améliorer le code généré par Claude Code combine **Spotless + google-java-format** pour le formatage, **PMD + SpotBugs + Error Prone** pour l'analyse statique, **OWASP Dependency-Check** pour la sécurité, et **SonarLint en mode standalone** pour le feedback temps réel. Tous ces outils supportent Java 25 LTS (sorti en septembre 2025), s'intègrent nativement à Maven, et fonctionnent ensemble sans conflits car ils opèrent à différentes phases du build.

Ce guide présente les configurations Maven testées pour chaque outil, les extensions VS Code recommandées, et un workflow optimisé pour le développement assisté par IA. Le code généré par des agents comme Claude Code bénéficie particulièrement du formatage automatique (consistance), de la détection de code mort (sur-génération), et de l'analyse de duplication (patterns copier-coller).

---

## Formatage automatique : Spotless + google-java-format

Pour le code généré par IA, **google-java-format via Spotless** représente le choix optimal grâce à sa politique "zéro configuration". Chaque exécution produit un résultat identique, éliminant les débats de style et garantissant une consistance parfaite entre le code humain et le code généré par Claude Code.

**Versions actuelles (janvier 2026)** :
- **google-java-format** : 1.25.2 — Support complet Java 25
- **spotless-maven-plugin** : 2.43.0 — Analyse incrémentale par défaut depuis v2.35.0

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>2.43.0</version>
    <configuration>
        <java>
            <googleJavaFormat>
                <version>1.25.2</version>
                <style>GOOGLE</style> <!-- ou AOSP pour indentation 4 espaces -->
            </googleJavaFormat>
            <removeUnusedImports/>
            <importOrder>
                <order>java,javax,org,com</order>
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

L'analyse incrémentale réduit le temps d'exécution à **quasi-instantané** après le premier build. Commandes essentielles : `mvn spotless:check` (vérification), `mvn spotless:apply` (correction automatique).

| Aspect | Impact |
|--------|--------|
| Premier build | +5-15 secondes |
| Builds incrémentaux | ~0 secondes |
| Recommandation | **Indispensable** |

**Intégration VS Code** : Configurer le formatter Eclipse avec le profil Google dans `settings.json` :

```json
{
    "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml",
    "java.format.settings.profile": "GoogleStyle",
    "editor.formatOnSave": true
}
```

---

## Linting avec Checkstyle pour le style de code

**Checkstyle 12.3.1** applique les conventions du Google Java Style Guide, complémentant le formatage avec des règles sémantiques (nommage, Javadoc, complexité). Le support du parsing Java 22 est complet ; Java 25 est en cours d'ajout via CircleCI #18091.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.6.0</version>
    <dependencies>
        <dependency>
            <groupId>com.puppycrawl.tools</groupId>
            <artifactId>checkstyle</artifactId>
            <version>12.3.1</version>
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

**Google vs Sun checks** : Google utilise **2 espaces** d'indentation et lignes de **100 caractères**, Sun utilise 4 espaces et 80 caractères. Google est recommandé pour les projets modernes avec meilleur support des records et sealed classes.

**Extension VS Code** : `shengchen.vscode-checkstyle` — Linting en temps réel avec quick fixes intégrés, supporte les configurations Google et Sun pré-intégrées.

| Recommandation | **Recommandé** |
|----------------|----------------|

---

## Analyse statique avancée : le trio PMD + SpotBugs + Error Prone

Ces trois outils se complètent sans conflit : **PMD** analyse le code source, **SpotBugs** analyse le bytecode compilé, **Error Prone** s'exécute pendant la compilation. Pour le code IA, cette combinaison détecte les erreurs de type (Error Prone), le code mort et les duplications (PMD), et les null pointers/fuites de ressources (SpotBugs).

### PMD 7.20.0 — Analyse de code source

Support complet Java 25 avec les règles modernisées pour records, sealed classes, et pattern matching. L'**analyse incrémentale** via cache réduit significativement les temps de build.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-pmd-plugin</artifactId>
    <version>3.28.0</version>
    <configuration>
        <targetJdk>25</targetJdk>
        <rulesets>
            <ruleset>/category/java/bestpractices.xml</ruleset>
            <ruleset>/category/java/errorprone.xml</ruleset>
            <ruleset>/category/java/codestyle.xml</ruleset>
        </rulesets>
        <analysisCache>true</analysisCache>
        <analysisCacheLocation>${project.build.directory}/pmd.cache</analysisCacheLocation>
        <printFailingErrors>true</printFailingErrors>
    </configuration>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
                <goal>cpd-check</goal> <!-- Détection de duplication -->
            </goals>
        </execution>
    </executions>
</plugin>
```

**Suppression de faux positifs** : `@SuppressWarnings("PMD.UnusedLocalVariable")` ou commentaire `//NOPMD`.

### SpotBugs 4.9.8 — Détection de bugs dans le bytecode

SpotBugs analyse le bytecode compilé avec support Java 25 via ASM 9.8. Le plugin **FindSecBugs** ajoute 150+ détecteurs de vulnérabilités sécurité.

```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.9.8.2</version>
    <dependencies>
        <dependency>
            <groupId>com.github.spotbugs</groupId>
            <artifactId>spotbugs</artifactId>
            <version>4.9.8</version>
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
                <version>1.12.0</version>
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

**Limitation** : Pas d'analyse incrémentale native. Chaque exécution analyse tout le bytecode.

### Error Prone 2.45.0 — Détection à la compilation

Error Prone de Google détecte les bugs au moment de la compilation avec un taux de faux positifs très bas. Requiert **JDK 21+** pour s'exécuter et une configuration JVM spéciale pour accéder aux APIs internes de javac.

```xml
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
        </compilerArgs>
        <annotationProcessorPaths>
            <path>
                <groupId>com.google.errorprone</groupId>
                <artifactId>error_prone_core</artifactId>
                <version>2.45.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

**Fichier `.mvn/jvm.config` requis** :
```properties
--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
--add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
--add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED
--add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED
--add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED
--add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
--add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
--add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
--add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
```

| Outil | Temps | Incrémental | Recommandation |
|-------|-------|-------------|----------------|
| PMD | Rapide | ✅ Cache | **Indispensable** |
| SpotBugs | Moyen | ❌ | **Recommandé** |
| Error Prone | Compilation | ✅ | **Recommandé** |

---

## Détection de vulnérabilités : OWASP Dependency-Check

Pour un projet self-hosted, **OWASP Dependency-Check 12.1.9** est le choix optimal : entièrement gratuit, open-source, sans limite d'utilisation, et analyse complète des dépendances transitives. Une **clé API NVD gratuite** est fortement recommandée pour des performances acceptables.

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>12.1.9</version>
    <configuration>
        <nvdApiKeyEnvironmentVariable>NVD_API_KEY</nvdApiKeyEnvironmentVariable>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <formats>
            <format>HTML</format>
            <format>JSON</format>
        </formats>
        <suppressionFiles>
            <suppressionFile>${project.basedir}/owasp-suppressions.xml</suppressionFile>
        </suppressionFiles>
        <!-- Désactiver analyseurs non-Java pour performance -->
        <assemblyAnalyzerEnabled>false</assemblyAnalyzerEnabled>
        <nodeAuditAnalyzerEnabled>false</nodeAuditAnalyzerEnabled>
        <retireJsAnalyzerEnabled>false</retireJsAnalyzerEnabled>
    </configuration>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

**Fichier de suppression des faux positifs** (`owasp-suppressions.xml`) :
```xml
<?xml version="1.0" encoding="UTF-8"?>
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">
    <suppress until="2026-06-01Z">
        <notes>False positive - not applicable to our usage</notes>
        <packageUrl regex="true">^pkg:maven/org\.springframework.*$</packageUrl>
        <cve>CVE-2024-XXXXX</cve>
    </suppress>
</suppressions>
```

| Aspect | Valeur |
|--------|--------|
| Premier scan | 20+ minutes (téléchargement NVD) |
| Scans suivants | 1-3 minutes avec API key |
| Recommandation | **Indispensable** |

**Alternative Snyk** : Offre un free tier (200 tests/mois) avec meilleure précision mais nécessite connexion cloud. Préférer OWASP pour projets self-hosted.

---

## Qualité de code temps réel : SonarLint standalone

**SonarQube for VS Code** (anciennement SonarLint) en **mode standalone** offre une analyse temps réel sans nécessiter de serveur SonarQube. Cette approche est idéale pour un développeur solo travaillant avec Claude Code.

⚠️ **Support Java 25** : Non disponible dans SonarQube avant mi-2026. L'analyse fonctionne mais peut générer des warnings pour les features spécifiques Java 25.

**Installation** : Extension VS Code `sonarsource.sonarlint-vscode`

**Capacités en mode standalone** :
- Analyse temps réel pendant l'écriture
- 5000+ règles Java intégrées
- Quick fixes automatiques
- Aucune configuration requise

**Pour CI** : Utiliser **SonarCloud free tier** (50K lignes pour projets privés) qui offre PR decoration et analyse de branches.

```xml
<!-- pom.xml pour SonarCloud -->
<properties>
    <sonar.organization>votre-github-username</sonar.organization>
    <sonar.host.url>https://sonarcloud.io</sonar.host.url>
</properties>

<plugin>
    <groupId>org.sonarsource.scanner.maven</groupId>
    <artifactId>sonar-maven-plugin</artifactId>
    <version>5.5.0.6356</version>
</plugin>
```

Exécution : `mvn verify sonar:sonar -Dsonar.token=$SONAR_TOKEN`

| Mode | Cas d'usage | Recommandation |
|------|-------------|----------------|
| Standalone (VS Code) | Développement local | **Indispensable** |
| SonarCloud free | CI/CD | **Recommandé** |
| SonarQube self-hosted | Data privacy | Optionnel |

---

## Git hooks Java-natif sans Node.js/Husky

Deux plugins Maven permettent d'installer des Git hooks automatiquement sans dépendance Node.js :

### Option 1 : git-code-format-maven-plugin (Cosium) — Formatage auto des fichiers staged

Le plugin **Cosium** formate automatiquement **uniquement les fichiers staged** lors du commit, maximisant l'efficacité.

```xml
<plugin>
    <groupId>com.cosium.code</groupId>
    <artifactId>git-code-format-maven-plugin</artifactId>
    <version>5.4</version>
    <executions>
        <execution>
            <id>install-formatter-hook</id>
            <goals><goal>install-hooks</goal></goals>
        </execution>
        <execution>
            <id>validate-code-format</id>
            <goals><goal>validate-code-format</goal></goals>
        </execution>
    </executions>
    <dependencies>
        <dependency>
            <groupId>com.cosium.code</groupId>
            <artifactId>google-java-format</artifactId>
            <version>5.4</version>
        </dependency>
    </dependencies>
</plugin>
```

### Option 2 : git-build-hook-maven-plugin — Hooks personnalisés

Pour des hooks plus flexibles combinant plusieurs outils :

```xml
<plugin>
    <groupId>com.rudikershaw.gitbuildhook</groupId>
    <artifactId>git-build-hook-maven-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <installHooks>
            <pre-commit>hooks/pre-commit</pre-commit>
        </installHooks>
    </configuration>
    <executions>
        <execution>
            <goals><goal>install</goal></goals>
        </execution>
    </executions>
</plugin>
```

**Script `hooks/pre-commit`** optimisé pour Claude Code :
```bash
#!/bin/bash
set -e
echo "Formatting staged files..."
mvn spotless:apply -q
git add -u
echo "Running quick checks..."
mvn checkstyle:check -q
echo "Pre-commit passed!"
```

| Plugin | Formatage staged | Hooks custom | Recommandation |
|--------|------------------|--------------|----------------|
| Cosium | ✅ Automatique | ❌ | **Indispensable** pour formatage |
| git-build-hook | ❌ | ✅ | Optionnel pour hooks custom |

---

## Maven Enforcer et profils CI/dev

### Maven Enforcer Plugin 3.6.2

Garantit la cohérence de l'environnement de build avec des règles strictes :

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <version>3.6.2</version>
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
                    <bannedDependencies>
                        <excludes>
                            <exclude>commons-logging:*</exclude>
                            <exclude>log4j:log4j</exclude>
                        </excludes>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Profils Maven : développement rapide vs CI complet

```xml
<profiles>
    <!-- Build rapide local (par défaut) -->
    <profile>
        <id>dev</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <properties>
            <skipITs>true</skipITs>
            <spotless.check.skip>true</spotless.check.skip>
            <checkstyle.skip>true</checkstyle.skip>
            <dependency-check.skip>true</dependency-check.skip>
        </properties>
    </profile>
    
    <!-- Build complet CI -->
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
            <dependency-check.skip>false</dependency-check.skip>
        </properties>
    </profile>
</profiles>
```

**Commandes types** :
- `mvn clean install` — Build rapide local (profil dev)
- `mvn clean install -Pci` — Build complet avec tous les checks
- `mvn compile -Dspotless.check.skip` — Compilation sans formatage

---

## Configuration VS Code complète pour Java + Claude Code

### Extensions recommandées

| Extension | ID | Usage |
|-----------|-----|-------|
| Extension Pack for Java | `vscjava.vscode-java-pack` | **Indispensable** — Inclut Language Support, Debugger, Maven |
| Checkstyle for Java | `shengchen.vscode-checkstyle` | Linting temps réel |
| SonarQube for VS Code | `sonarsource.sonarlint-vscode` | Analyse qualité standalone |
| Error Lens | `usernamehw.errorlens` | Affichage inline des erreurs |

### settings.json recommandé

```json
{
    "java.format.settings.url": "https://raw.githubusercontent.com/google/styleguide/gh-pages/eclipse-java-google-style.xml",
    "java.format.settings.profile": "GoogleStyle",
    "editor.formatOnSave": true,
    "editor.formatOnPaste": false,
    "java.compile.nullAnalysis.mode": "automatic",
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.checkstyle.configuration": "/google_checks.xml",
    "java.checkstyle.version": "12.3.1",
    "sonarlint.rules": {
        "java:S1135": { "level": "off" }
    },
    "files.exclude": {
        "**/target": true
    }
}
```

---

## Workflow optimal avec Claude Code

Le workflow recommandé sépare l'itération rapide pendant la génération de la validation complète avant commit :

**Pendant l'itération avec Claude Code** :
1. Désactiver les checks automatiques (`-Dspotless.check.skip`)
2. Laisser SonarLint signaler les problèmes en temps réel
3. Utiliser `mvn compile` pour feedback rapide

**Après génération** :
1. `mvn spotless:apply` — Formatage automatique
2. Review visuel du code formaté
3. Commit déclenche le pre-commit hook (validation légère)

**CI/Push** :
1. Build complet avec profil `ci`
2. Tous les analyseurs actifs (PMD, SpotBugs, OWASP)
3. SonarCloud pour métriques et historique

Cette séparation évite de ralentir le cycle d'itération avec Claude Code tout en garantissant la qualité du code committé.

---

## Récapitulatif des versions et compatibilité Java 25

| Outil | Version | Java 25 | Maven Config | Recommandation |
|-------|---------|---------|--------------|----------------|
| google-java-format | 1.25.2 | ✅ Complet | Via Spotless | Indispensable |
| Spotless | 2.43.0 | ✅ Complet | Plugin | Indispensable |
| Checkstyle | 12.3.1 | ⚠️ En cours | Plugin | Recommandé |
| PMD | 7.20.0 | ✅ Complet | Plugin | Indispensable |
| SpotBugs | 4.9.8 | ✅ Via ASM | Plugin | Recommandé |
| Error Prone | 2.45.0 | ✅ Complet | Compiler | Recommandé |
| OWASP DC | 12.1.9 | ✅ N/A | Plugin | Indispensable |
| SonarLint | 4.9+ | ⚠️ Partiel | VS Code ext | Indispensable |
| Maven Enforcer | 3.6.2 | ✅ N/A | Plugin | Recommandé |
| git-code-format | 5.4 | ✅ | Plugin | Indispensable |

## Conclusion

Cette stack d'outils forme un pipeline de qualité complet pour le code Java généré par Claude Code. Les éléments **indispensables** (Spotless, PMD, OWASP, SonarLint, git-code-format) couvrent formatage, analyse statique, sécurité et feedback temps réel sans configuration complexe. Les outils **recommandés** (Checkstyle, SpotBugs, Error Prone, Enforcer) ajoutent des couches de détection complémentaires pour les projets nécessitant une rigueur accrue.

Le point clé pour l'efficacité avec Claude Code : utiliser le profil `dev` pendant l'itération pour des builds rapides, et laisser le pre-commit hook et la CI appliquer la validation complète. Cette approche maximise la productivité tout en maintenant une qualité de code élevée.
