"""Pydantic request/response models for the API boundary.

The models use ``camelCase`` aliases so responses match what the JavaFX client's
Jackson mapper expects (``scanId``, ``safeMode``, ...), while ``populate_by_name``
keeps ``snake_case`` input working too (handy for ``curl`` examples).
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, field_validator
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
    )


class ScanStartRequest(CamelModel):
    target: str
    scope: list[str] = Field(default_factory=lambda: ["full"])
    safe_mode: bool = True

    @field_validator("scope", mode="before")
    @classmethod
    def _coerce_scope(cls, value):
        if value is None:
            return ["full"]
        if isinstance(value, str):
            return [value]
        return value


class ScanStartResponse(CamelModel):
    scan_id: str
    status: str
    message: str


class ScanResultResponse(CamelModel):
    scan_id: str
    target: str
    status: str
    results: dict = Field(default_factory=dict)
