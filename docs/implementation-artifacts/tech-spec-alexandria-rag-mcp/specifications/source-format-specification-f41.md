# Source Format Specification (F41)

| Aspect | Spécification |
|--------|---------------|
| Format | Path relatif depuis la racine projet (ex: `docs/guide.md`) |
| Longueur max | 500 caractères |
| Caractères autorisés | Alphanumériques, `/`, `-`, `_`, `.` |
| Validation | Regex: `^[a-zA-Z0-9/_.-]{1,500}$` |
| Unicité | UNIQUE constraint en DB |

**Exemples valides:**
- `README.md`
- `docs/api/endpoints.md`
- `conventions/typescript-style.md`

**Exemples invalides:**
- `/absolute/path.md` (pas de slash initial)
- `docs/../secret.md` (pas de `..`)
- `file with spaces.md` (pas d'espaces)
