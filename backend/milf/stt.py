import os
from abc import ABC, abstractmethod

import httpx


class STTAdapter(ABC):
    @abstractmethod
    async def transcribe(self, audio: bytes, lang: str) -> str:
        raise NotImplementedError


class MockSTT(STTAdapter):
    def __init__(self, canned: str):
        self._canned = canned

    async def transcribe(self, audio: bytes, lang: str) -> str:
        return self._canned


class _MultipartSTT(STTAdapter):
    def __init__(
        self,
        api_url: str,
        api_key: str,
        http: httpx.AsyncClient | None = None,
    ):
        self._api_url = api_url
        self._api_key = api_key
        self._http = http

    async def transcribe(self, audio: bytes, lang: str) -> str:
        if self._http is not None:
            return await self._post_and_parse(self._http, audio, lang)

        async with httpx.AsyncClient() as http:
            return await self._post_and_parse(http, audio, lang)

    async def _post_and_parse(
        self,
        http: httpx.AsyncClient,
        audio: bytes,
        lang: str,
    ) -> str:
        response = await http.post(
            self._api_url,
            data={"lang": lang},
            files={"audio": ("audio", audio, "application/octet-stream")},
            headers=self._headers(),
        )
        response.raise_for_status()
        return self._parse_transcript(response)

    def _headers(self) -> dict[str, str]:
        if not self._api_key:
            return {}
        return {"Authorization": f"Bearer {self._api_key}"}

    def _parse_transcript(self, response: httpx.Response) -> str:
        data = response.json()
        text = data.get("text")
        if not isinstance(text, str):
            raise ValueError("STT response missing text transcript")
        return text


class IlmuSTT(_MultipartSTT):
    pass


class MERaLiONSTT(_MultipartSTT):
    pass


class RouterSTT(STTAdapter):
    def __init__(self, routes: dict[str, STTAdapter], default: STTAdapter):
        self._routes = routes
        self._default = default

    async def transcribe(self, audio: bytes, lang: str) -> str:
        adapter = self._routes.get(lang, self._default)
        return await adapter.transcribe(audio, lang)


def make_stt() -> STTAdapter:
    backend = os.environ.get("MILF_STT_BACKEND", "mock").lower()
    if backend == "mock":
        canned = os.environ.get("MILF_MOCK_TRANSCRIPT", "I want to see my grandson")
        return MockSTT(canned)
    if backend != "router":
        raise ValueError(f"Unknown MILF_STT_BACKEND: {backend}")

    ilmu = IlmuSTT(
        api_url=os.environ["ILMU_API_URL"],
        api_key=os.environ["ILMU_API_KEY"],
    )
    meralion = MERaLiONSTT(
        api_url=os.environ["MERALION_API_URL"],
        api_key=os.environ["MERALION_API_KEY"],
    )
    return RouterSTT(
        routes={"en": ilmu, "zh": ilmu, "yue": meralion},
        default=ilmu,
    )
