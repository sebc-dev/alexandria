# Retry Logic (F22)

**Spécification exacte:**

```typescript
const RETRY_CONFIG = {
  maxAttempts: 3,        // Total: 1 initial + 2 retries
  baseDelayMs: 100,      // Premier retry après 100ms
  multiplier: 4,         // Backoff: 100ms, 400ms
  jitterPercent: 20,     // ±20% randomisation
};

// Délais effectifs:
// Attempt 1: immédiat
// Attempt 2: 80-120ms (100ms ± 20%)
// Attempt 3: 320-480ms (400ms ± 20%)
```

**Calcul du jitter:**
```typescript
const jitter = 1 + (Math.random() - 0.5) * 2 * (jitterPercent / 100);
const actualDelay = baseDelay * jitter;
```
