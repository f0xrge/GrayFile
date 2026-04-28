import logging
import os

import grpc
from envoy.config.core.v3 import base_pb2
from envoy.service.ext_proc.v3 import external_processor_pb2 as ep_pb2
from envoy.service.ext_proc.v3 import external_processor_pb2_grpc as ep_grpc
from .extractor import extract_usage_from_chunks

LOG = logging.getLogger("grayfile-ext-proc")


def _set_header(response: ep_pb2.ProcessingResponse, key: str, value: str) -> None:
    response.response_body.response.header_mutation.set_headers.append(
        base_pb2.HeaderValueOption(
            header=base_pb2.HeaderValue(key=key, value=value),
            append=base_pb2.HeaderValueOption.HeaderAppendAction.OVERWRITE_IF_EXISTS_OR_ADD,
        )
    )


class ExternalProcessor(ep_grpc.ExternalProcessorServicer):

    async def Process(self, request_iterator, context):  # noqa: N802
        response_chunks: list[bytes] = []
        async for request in request_iterator:
            response = ep_pb2.ProcessingResponse()

            if request.HasField("response_body"):
                if request.response_body.body:
                    response_chunks.append(request.response_body.body)

                if not request.response_body.end_of_stream:
                    yield response
                    continue

                result = extract_usage_from_chunks(response_chunks, end_of_stream=True)
                _set_header(response, "x-edge-usage-extraction", result.status)
                if result.status == "ok":
                    _set_header(response, "x-edge-usage-prompt-tokens", str(result.prompt_tokens))
                    _set_header(response, "x-edge-usage-completion-tokens", str(result.completion_tokens))
                    _set_header(response, "x-edge-usage-total-tokens", str(result.total_tokens))

                response_chunks.clear()
                yield response
                continue

            yield response


async def serve() -> None:
    listen_address = os.getenv("EXT_PROC_LISTEN", "0.0.0.0:18080")
    server = grpc.aio.server()
    ep_grpc.add_ExternalProcessorServicer_to_server(ExternalProcessor(), server)
    server.add_insecure_port(listen_address)
    await server.start()
    LOG.info("ext_proc listening on %s", listen_address)
    await server.wait_for_termination()


if __name__ == "__main__":
    logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))
    import asyncio

    asyncio.run(serve())
