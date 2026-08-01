"""Phase 1 API: validates requests and streams assistant deltas as SSE.

Keep model credentials and any Antigravity/Gemini SDK integration on this server;
the Android app only speaks to this narrow API contract.
"""

from __future__ import annotations

import asyncio
import json
import os
from collections.abc import AsyncIterator
from pathlib import Path

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from dotenv import load_dotenv


load_dotenv(Path(__file__).resolve().parents[1] / ".env")


class ChatTurn(BaseModel):
    role: str = Field(pattern="^(user|assistant|system)$")
    content: str = Field(min_length=1, max_length=20_000)


class ChatRequest(BaseModel):
    messages: list[ChatTurn] = Field(min_length=1, max_length=50)


class GoogleAuthRequest(BaseModel):
    id_token: str = Field(min_length=20, max_length=10_000)


class AuthenticatedProfile(BaseModel):
    subject: str
    email: str
    name: str | None = None
    picture: str | None = None


class AssistantProvider:
    async def stream(self, messages: list[ChatTurn]) -> AsyncIterator[str]:
        raise NotImplementedError


class DemoProvider(AssistantProvider):
    """Predictable development provider that exercises real streaming without an API key."""

    async def stream(self, messages: list[ChatTurn]) -> AsyncIterator[str]:
        prompt = next(message.content for message in reversed(messages) if message.role == "user")
        reply = (
            "I received your request: **%s**\n\n"
            "The Android client is connected to the FastAPI streaming endpoint. "
            "Next, set `AI_PROVIDER` to a verified production adapter and keep its key on the server.\n\n"
            "```text\nstream: working\nhistory: saved locally\n```"
        ) % prompt
        for index in range(0, len(reply), 12):
            await asyncio.sleep(0.02)
            yield reply[index : index + 12]


class GeminiProvider(AssistantProvider):
    """Gemini adapter. The key is read only from the backend environment."""

    def __init__(self) -> None:
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            raise RuntimeError("GEMINI_API_KEY is required when AI_PROVIDER=gemini.")
        # Deferred imports retain a dependency-free demo mode and make configuration errors clear.
        from google import genai
        from google.genai import types

        self.client = genai.Client(api_key=api_key)
        self.types = types
        self.model = os.getenv("GEMINI_MODEL", "gemini-3.6-flash")

    async def stream(self, messages: list[ChatTurn]) -> AsyncIterator[str]:
        contents = [
            self.types.Content(
                role="model" if message.role == "assistant" else "user",
                parts=[self.types.Part.from_text(text=message.content)],
            )
            for message in messages
            if message.role != "system"
        ]
        response = self.client.models.generate_content_stream(model=self.model, contents=contents)
        for chunk in response:
            if chunk.text:
                yield chunk.text


def provider_from_environment() -> AssistantProvider:
    provider_name = os.getenv("AI_PROVIDER", "demo").lower()
    if provider_name == "demo":
        return DemoProvider()
    if provider_name == "gemini":
        return GeminiProvider()
    raise RuntimeError(
        f"AI_PROVIDER={provider_name!r} is not configured. "
        "Implement an authenticated server-side adapter that subclasses AssistantProvider."
    )


def sse(event: dict[str, str]) -> bytes:
    return f"data: {json.dumps(event)}\n\n".encode("utf-8")


app = FastAPI(title="Android Antigravity API", version="0.1.0")
origins = [origin.strip() for origin in os.getenv("ALLOWED_ORIGINS", "").split(",") if origin.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=False,
    allow_methods=["POST", "GET"],
    allow_headers=["Content-Type", "Authorization"],
)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "provider": os.getenv("AI_PROVIDER", "demo")}


@app.post("/v1/auth/google", response_model=AuthenticatedProfile)
async def verify_google_token(request: GoogleAuthRequest) -> AuthenticatedProfile:
    """Verify ID-token signature, audience, issuer and expiry before trusting user data."""
    audience = os.getenv("GOOGLE_WEB_CLIENT_ID")
    if not audience:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Google sign-in is not configured.")

    from google.auth.transport import requests as google_requests
    from google.oauth2 import id_token

    try:
        claims = id_token.verify_oauth2_token(request.id_token, google_requests.Request(), audience)
    except ValueError as error:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid Google ID token.") from error

    if not claims.get("email_verified") or not claims.get("sub") or not claims.get("email"):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="A verified Google email is required.")
    return AuthenticatedProfile(
        subject=claims["sub"],
        email=claims["email"],
        name=claims.get("name"),
        picture=claims.get("picture"),
    )


@app.post("/v1/chat/stream")
async def chat_stream(request: ChatRequest) -> StreamingResponse:
    if request.messages[-1].role != "user":
        raise HTTPException(status_code=422, detail="The final message must be from the user.")

    async def events() -> AsyncIterator[bytes]:
        try:
            async for text in provider_from_environment().stream(request.messages):
                yield sse({"type": "delta", "text": text})
            yield sse({"type": "done"})
        except Exception:
            # Do not expose model/provider details or credentials to the app.
            yield sse({"type": "error", "message": "The assistant is temporarily unavailable."})

    return StreamingResponse(events(), media_type="text/event-stream", headers={"Cache-Control": "no-cache"})
