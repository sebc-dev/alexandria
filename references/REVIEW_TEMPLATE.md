# Template de rapport CodeRabbit

## Métadonnées
- **Date** : {{date}}
- **Scope** : {{scope}}
- **Fichiers analysés** : {{files_count}}
- **Branche** : {{branch}}

## Résumé exécutif
{{summary}}

## Findings critiques (🔴)
{{#critical}}
### {{file}}:{{line}}
- **Issue** : {{description}}
- **Impact** : {{impact}}
- **Fix suggéré** : {{suggestion}}
{{/critical}}

## Warnings (🟡)
{{#warnings}}
- **{{file}}:{{line}}** : {{description}}
{{/warnings}}

## Suggestions (🔵)
{{#suggestions}}
- {{description}}
{{/suggestions}}

## Statistiques
- Critical : {{critical_count}}
- Warnings : {{warning_count}}
- Suggestions : {{suggestion_count}}