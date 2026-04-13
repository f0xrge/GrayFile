import json

from deploy.ext_proc.extractor import extract_usage, extract_usage_from_chunks


def test_extract_usage_success():
    payload = {
        "usage": {
            "prompt_tokens": 11,
            "completion_tokens": 7,
            "total_tokens": 18,
        }
    }

    result = extract_usage(json.dumps(payload).encode())

    assert result.status == "ok"
    assert result.prompt_tokens == 11
    assert result.completion_tokens == 7
    assert result.total_tokens == 18


def test_extract_usage_incomplete_payload_returns_invalid_json():
    result = extract_usage(b'{"usage": {"prompt_tokens": 1')
    assert result.status == "invalid_json"


def test_extract_usage_chunked_payload():
    chunks = [
        b'{"usage":{"prompt_tokens":12,',
        b'"completion_tokens":8,"total_tokens":20}}',
    ]
    result = extract_usage_from_chunks(chunks, end_of_stream=True)
    assert result.status == "ok"
    assert result.total_tokens == 20


def test_extract_usage_chunked_incomplete_stream():
    result = extract_usage_from_chunks([b'{"usage":{"prompt_tokens":12'], end_of_stream=False)
    assert result.status == "incomplete_payload"


def test_extract_usage_missing_usage():
    result = extract_usage(b'{"id": "chatcmpl-1"}')
    assert result.status == "usage_missing"


def test_extract_usage_invalid_usage_values():
    result = extract_usage(b'{"usage": {"prompt_tokens": -1, "completion_tokens": 3, "total_tokens": 2}}')
    assert result.status == "usage_invalid"
