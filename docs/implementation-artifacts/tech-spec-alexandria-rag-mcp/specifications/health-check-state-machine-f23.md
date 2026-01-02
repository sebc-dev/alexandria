# Health Check State Machine (F23)

**États du modèle:**

```
                    ┌─────────────┐
        startup     │   initial   │
           ┌───────>│  (no model) │
           │        └──────┬──────┘
           │               │ first embed call
           │               v
           │        ┌─────────────┐
           │        │   loading   │──── timeout 60s ────> error
           │        └──────┬──────┘
           │               │ success
           │               v
           │        ┌─────────────┐
           │        │   loaded    │<─── recover ────┐
           │        └──────┬──────┘                 │
           │               │ inference error        │
           │               v                        │
           │        ┌─────────────┐                 │
           └────────│    error    │─── retry ok ────┘
                    └─────────────┘
```

**Transitions:**

| From | To | Trigger |
|------|-----|---------|
| initial | loading | Premier appel embed() |
| loading | loaded | Pipeline créé avec succès |
| loading | error | Timeout 60s ou exception |
| loaded | error | Erreur d'inférence (OOM, etc.) |
| error | loaded | Prochain embed() réussit |

**Health response pendant loading:**
- Retourne immédiatement `{ model: "loading" }`, ne bloque pas
