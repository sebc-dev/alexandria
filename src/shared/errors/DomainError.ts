/**
 * Base class for all domain-related errors
 * Domain layer MUST NOT depend on external libraries
 */
export class DomainError extends Error {
  constructor(
    message: string,
    public readonly code: string
  ) {
    super(message)
    this.name = 'DomainError'
    Object.setPrototypeOf(this, DomainError.prototype)
  }
}
