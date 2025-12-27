import { z, type ZodIssue } from 'zod'

/**
 * Environment variables schema using Zod 4.2.1
 * Validates configuration at application startup (fail-fast)
 */
export const envSchema = z.object({
  // Database
  ALEXANDRIA_DB_URL: z
    .string()
    .url()
    .startsWith('postgresql://', {
      message: 'ALEXANDRIA_DB_URL must be a valid PostgreSQL connection URL',
    }),

  // External Services
  OPENAI_API_KEY: z
    .string()
    .min(1, { message: 'OPENAI_API_KEY is required' })
    .startsWith('sk-', { message: 'OPENAI_API_KEY must start with sk-' }),

  // Logging (Optional with defaults)
  LOG_LEVEL: z
    .enum(['DEBUG', 'INFO', 'WARN', 'ERROR'])
    .default('INFO'),

  LOG_RETENTION_DAYS: z
    .string()
    .default('30')
    .transform((val) => parseInt(val, 10))
    .pipe(z.number().int().positive()),
})

export type Env = z.infer<typeof envSchema>

/**
 * Validates environment variables and returns typed config
 * Throws error with clear message if validation fails (fail-fast behavior)
 */
export function validateEnv(): Env {
  const result = envSchema.safeParse(process.env)

  if (!result.success) {
    const errors = result.error.issues
      .map((err: ZodIssue) => `  - ${err.path.join('.')}: ${err.message}`)
      .join('\n')

    throw new Error(
      `❌ Environment validation failed:\n${errors}\n\nPlease check your .env file and ensure all required variables are set correctly.`
    )
  }

  return result.data
}

/**
 * Validated and typed environment configuration
 * Exported for use throughout the application
 */
export const env = validateEnv()
