import json
from types import SimpleNamespace
from unittest.mock import patch

import pytest
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from langchain_google_genai import ChatGoogleGenerativeAI

from app.providers import GeminiProvider, InvalidGeneration, ProviderUnavailable


def test_real_langchain_embedding_adapter_contract(options):
    # Instantiate the installed LangChain adapter, replacing only the network client.
    configured = options.model_copy(update={"google_api_key": options.ai_service_key})
    provider = GeminiProvider(configured)
    with patch("langchain_google_genai.embeddings.Client") as constructor:
        constructor.return_value.models.embed_content.return_value = SimpleNamespace(
            embeddings=[SimpleNamespace(values=[1.0] * 128)]
        )
        assert len(provider.embed_documents(["Grammar"])[0]) == 128
        kwargs = constructor.return_value.models.embed_content.call_args.kwargs
        assert kwargs["config"].task_type == "RETRIEVAL_DOCUMENT"
        assert kwargs["config"].output_dimensionality == 128
        assert "vertexai" not in constructor.call_args.kwargs
        assert constructor.call_args.kwargs["http_options"].client_args["timeout"] == 45
        assert len(provider.embed_query("Grammar")) == 128
        kwargs = constructor.return_value.models.embed_content.call_args.kwargs
        assert kwargs["config"].task_type == "RETRIEVAL_QUERY"


def test_missing_key_fails_without_network(options):
    with pytest.raises(ProviderUnavailable):
        GeminiProvider(options).embed_query("Question")


@pytest.mark.parametrize("valid", [True, False])
def test_real_langchain_structured_output_chain(options, valid):
    configured = options.model_copy(update={"google_api_key": options.ai_service_key})
    payload = {
        "answer": "Present perfect uses have/has + past participle.",
        "sufficient_evidence": True,
        "cited_chunk_ids": ["chunk-123"],
        "exercises": [],
    }
    message = AIMessage(
        content=json.dumps(payload) if valid else "malformed output",
        usage_metadata={"input_tokens": 10, "output_tokens": 5, "total_tokens": 15},
    )
    # Exercise the installed model's structured-output Runnable, not a fake replacement chain.
    with patch.object(
        ChatGoogleGenerativeAI,
        "_generate",
        return_value=ChatResult(generations=[ChatGeneration(message=message)]),
    ):
        if valid:
            result = GeminiProvider(configured).generate("Tutor", "Question")
            assert result.answer.cited_chunk_ids == ["chunk-123"]
            assert result.total_tokens == 15
        else:
            with pytest.raises(InvalidGeneration):
                GeminiProvider(configured).generate("Tutor", "Question")
