from __future__ import annotations

import logging

SENSITIVE_DEPENDENCY_LOGGERS = (
    "mobilerun",
    "websockets",
)


def configure_dependency_logging() -> None:
    for name in SENSITIVE_DEPENDENCY_LOGGERS:
        logger = logging.getLogger(name)
        logger.setLevel(logging.WARNING)
        for handler in logger.handlers:
            handler.setLevel(logging.WARNING)
