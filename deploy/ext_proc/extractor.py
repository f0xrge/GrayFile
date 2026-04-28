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
        return ExtractionResult(status="incomplete_stream")

    payload = b"".join(chunks)
    text = payload.decode("utf-8", errors="ignore")
    if _looks_like_sse_payload(text):
        return _extract_usage_from_sse(text)
    return extract_usage(payload)


def _looks_like_sse_payload(payload_text: str) -> bool:
    return "data:" in payload_text and "[DONE]" in payload_text


def _extract_usage_from_sse(payload_text: str) -> ExtractionResult:
    usage_candidate: Optional[ExtractionResult] = None
    for raw_line in payload_text.splitlines():
        line = raw_line.strip()
        if not line.startswith("data:"):
            continue

        data = line[5:].strip()
        if not data or data == "[DONE]":
            continue

        extraction = extract_usage(data.encode("utf-8"))
        if extraction.status == "ok":
            usage_candidate = extraction

    if usage_candidate is None:
        return ExtractionResult(status="stream_final_missing_usage")

    return usage_candidate
