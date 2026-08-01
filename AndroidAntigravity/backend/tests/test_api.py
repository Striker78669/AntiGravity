from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_health() -> None:
    assert client.get("/health").json()["status"] == "ok"


def test_chat_stream_returns_deltas_and_done(monkeypatch) -> None:
    monkeypatch.setenv("AI_PROVIDER", "demo")
    response = client.post("/v1/chat/stream", json={"messages": [{"role": "user", "content": "Hello"}]})
    assert response.status_code == 200
    assert '"type": "delta"' in response.text
    assert '"type": "done"' in response.text


def test_chat_rejects_invalid_final_role() -> None:
    response = client.post("/v1/chat/stream", json={"messages": [{"role": "assistant", "content": "Hello"}]})
    assert response.status_code == 422
