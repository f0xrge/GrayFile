import json
from dataclasses import dataclass
from typing import Optional


@dataclass
class ExtractionResult:
    status: str
    prompt_tokens: Optional[int] = None
    completion_tokens: Optional[int] = None
    total_tokens: Optional[int] = None


def extract_usage(payload: bytes) -> ExtractionResult:
    if payload is None or len(payload) == 0:
        return ExtractionResult(status="missing_body")

    try:
        doc = json.loads(payload)
    except json.JSONDecodeError:
        return ExtractionResult(status="invalid_json")

    if not isinstance(doc, dict):
        return ExtractionResult(status="invalid_json")

    usage = doc.get("usage")
    if not isinstance(usage, dict):
        return ExtractionResult(status="usage_missing")

    prompt = usage.get("prompt_tokens")
    completion = usage.get("completion_tokens")
    total = usage.get("total_tokens")
    if not all(isinstance(v, int) and v >= 0 for v in (prompt, completion, total)):
        return ExtractionResult(status="usage_invalid")

    return ExtractionResult("ok", prompt, completion, total)


def extract_usage_from_chunks(chunks: list[bytes], end_of_stream: bool) -> ExtractionResult:
    if not end_of_stream:
        return ExtractionResult(status="incomplete_payload")
    return extract_usage(b"".join(chunks))
