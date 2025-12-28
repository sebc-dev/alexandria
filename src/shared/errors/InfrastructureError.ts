/**
 * Base class for all infrastructure-related errors
 * Used by adapters and infrastructure components
 */
export class InfrastructureError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    cause?: Error
  ) {
    super(message, { cause })
    this.name = 'InfrastructureError'
    Object.setPrototypeOf(this, InfrastructureError.prototype)
  }
}
