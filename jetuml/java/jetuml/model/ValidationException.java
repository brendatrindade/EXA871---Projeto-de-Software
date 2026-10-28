package jetuml.model;

/**
 * Operação rejeitada pelas regras do diagrama. {@code silent} representa o "salto no fluxo de controle"
 * de RF30: falha sem notificação.
 */
public class ValidationException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final boolean silent;

  public ValidationException(String message) { this(message, false); }

  public ValidationException(String message, boolean silent) {
    super(message);
    this.silent = silent;
  }

  public boolean silent() { return silent; }
}
