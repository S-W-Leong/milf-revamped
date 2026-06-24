import httpx
import pytest

from milf.stt import IlmuSTT, MERaLiONSTT, MockSTT, RouterSTT, make_stt


async def test_mock_returns_canned():
    stt = MockSTT("nak tengok cucu")
    assert await stt.transcribe(b"audio", "zh") == "nak tengok cucu"


async def test_ilmu_posts_and_parses_text():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["auth"] = request.headers.get("authorization")
        captured["content_type"] = request.headers.get("content-type", "")
        captured["body"] = request.content
        return httpx.Response(200, json={"text": "call my son"})

    transport = httpx.MockTransport(handler)
    async with httpx.AsyncClient(transport=transport) as client:
        stt = IlmuSTT(api_url="https://ilmu.test/asr", api_key="k", http=client)
        out = await stt.transcribe(b"bytes", "zh")
    assert out == "call my son"
    assert captured["url"] == "https://ilmu.test/asr"
    assert captured["auth"] == "Bearer k"
    assert "multipart/form-data" in captured["content_type"]
    assert b'name="lang"' in captured["body"]
    assert b"zh" in captured["body"]
    assert b'name="audio"' in captured["body"]
    assert b"bytes" in captured["body"]


async def test_meralion_posts_and_parses_text():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"text": "我想见我孙"})

    transport = httpx.MockTransport(handler)
    async with httpx.AsyncClient(transport=transport) as client:
        stt = MERaLiONSTT(
            api_url="https://meralion.test/asr",
            api_key="k",
            http=client,
        )
        out = await stt.transcribe(b"bytes", "yue")
    assert out == "我想见我孙"


async def test_router_dispatches_by_lang_and_falls_back():
    ilmu = MockSTT("from-ilmu")
    meralion = MockSTT("from-meralion")
    router = RouterSTT(routes={"en": ilmu, "zh": ilmu, "yue": meralion}, default=ilmu)
    assert await router.transcribe(b"x", "yue") == "from-meralion"
    assert await router.transcribe(b"x", "zh") == "from-ilmu"
    assert await router.transcribe(b"x", "unknown") == "from-ilmu"


def test_make_stt_mock_mode(monkeypatch):
    monkeypatch.setenv("MILF_STT_BACKEND", "mock")
    monkeypatch.setenv("MILF_MOCK_TRANSCRIPT", "hello")
    assert isinstance(make_stt(), MockSTT)


def test_make_stt_router_mode(monkeypatch):
    monkeypatch.setenv("MILF_STT_BACKEND", "router")
    monkeypatch.setenv("ILMU_API_URL", "https://ilmu.test/asr")
    monkeypatch.setenv("ILMU_API_KEY", "ilmu-key")
    monkeypatch.setenv("MERALION_API_URL", "https://meralion.test/asr")
    monkeypatch.setenv("MERALION_API_KEY", "meralion-key")
    assert isinstance(make_stt(), RouterSTT)


def test_make_stt_defaults_to_router_mode(monkeypatch):
    monkeypatch.delenv("MILF_STT_BACKEND", raising=False)
    monkeypatch.setenv("ILMU_API_URL", "https://ilmu.test/asr")
    monkeypatch.setenv("ILMU_API_KEY", "ilmu-key")
    monkeypatch.setenv("MERALION_API_URL", "https://meralion.test/asr")
    monkeypatch.setenv("MERALION_API_KEY", "meralion-key")
    assert isinstance(make_stt(), RouterSTT)


def test_make_stt_rejects_unknown_backend(monkeypatch):
    monkeypatch.setenv("MILF_STT_BACKEND", "mockk")
    with pytest.raises(ValueError, match="MILF_STT_BACKEND"):
        make_stt()
