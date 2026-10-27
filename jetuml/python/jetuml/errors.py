class ValidationError(Exception):
    """Operação rejeitada pelas regras do diagrama.

    `silent=True` representa o "salto no fluxo de controle" de RF30: falha sem notificação.
    """

    def __init__(self, message: str, silent: bool = False) -> None:
        super().__init__(message)
        self.message = message
        self.silent = silent
