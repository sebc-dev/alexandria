import { env } from '@/config/env.schema'

/**
 * Alexandria - Active Compliance Filter for Claude Code
 *
 * Application entry point
 * Validates environment configuration at startup (fail-fast)
 */

console.log('🚀 Starting Alexandria...')
console.log(`📊 Environment: ${env.LOG_LEVEL}`)
console.log(`✅ Configuration validated successfully`)

// TODO: Story 1.5 - Dependency Injection & Bootstrap
// Application initialization will be implemented here
