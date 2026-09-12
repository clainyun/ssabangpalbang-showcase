"""Read-only persistence adapters used by the AI service."""

from app.repositories.report_input_repository import (
    ReportInputRepository,
    ReportInputRepositoryError,
    create_report_input_repository,
)

__all__ = [
    "ReportInputRepository",
    "ReportInputRepositoryError",
    "create_report_input_repository",
]
