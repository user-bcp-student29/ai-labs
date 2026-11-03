import base64
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.main import create_app, legacy_identity


def token(username: str = "student", role: str = "STUDENT") -> str:
    raw = f"{username}|{role}|{int(time.time()) + 3600}|WORKSHOP_SECRET_2026"
    return base64.b64encode(raw.encode()).decode()


def headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {token(username, role)}"}


def test_lists_seeded_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 200
    assert len(response.json()) == 2


def test_creates_task_and_summary(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Add tests", "description": "Improve coverage", "status": "TODO", "priority": "HIGH"},
        )
        summary = client.get("/api/summary", headers=headers())
    assert created.status_code == 201
    assert created.json()["owner"] == "student"
    assert summary.json()["total"] == 3


def test_quoted_owner_filters_are_treated_as_data(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        tasks = client.get("/api/tasks", params={"owner": "O'Brien"}, headers=headers())
        summary = client.get("/api/summary", params={"owner": "O'Brien"}, headers=headers())
    assert tasks.status_code == 200
    assert tasks.json() == []
    assert summary.status_code == 200
    assert summary.json() == {"owner": "O'Brien", "total": 0, "byStatus": {}, "highPriorityOpen": 0}


def test_quoted_task_text_is_inserted_as_data(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Owner's task", "description": "It's a task", "status": "TODO", "priority": "MEDIUM"},
        )
    assert response.status_code == 201
    assert response.json()["title"] == "Owner's task"
    assert response.json()["description"] == "It's a task"


def test_lists_student_tasks_when_owner_is_empty(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", params={"owner": ""}, headers=headers())
    assert response.status_code == 200
    assert all(task["owner"] == "student" for task in response.json())


def test_user_cannot_read_tasks_from_another_owner(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", params={"owner": "trainer"}, headers=headers("student"))
    assert response.status_code == 403
    assert response.json() == {"detail": "Not authorized to access this owner"}


def test_creates_task_from_empty_object_using_defaults(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post("/api/tasks", headers=headers(), json={})
    assert response.status_code == 201
    assert response.json()["title"] == ""
    assert response.json()["description"] == ""
    assert response.json()["status"] == "TODO"
    assert response.json()["priority"] == "MEDIUM"
    assert response.json()["owner"] == "student"


def test_summary_uses_identity_when_owner_is_empty(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/summary", params={"owner": ""}, headers=headers())
    assert response.status_code == 200
    assert response.json()["owner"] == "student"
    assert response.json()["total"] == 2


def test_uploads_empty_extensionless_file(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements", b"", "application/octet-stream")},
        )
    assert response.status_code == 201
    assert response.json()["fileName"] == "requirements"
    assert response.json()["size"] == 0
    assert (upload_dir / "requirements").read_bytes() == b""


def test_backend_health_returns_upstream_response(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    class UpstreamResponse:
        status = 200
        data = b"{\"status\":\"UP\"}"

    class FakePoolManager:
        def request(self, method: str, url: str, timeout: float) -> UpstreamResponse:
            assert method == "GET"
            assert url == "http://localhost:8080/q/health"
            assert timeout == 3.0
            return UpstreamResponse()

    monkeypatch.setattr("app.main.urllib3.PoolManager", FakePoolManager)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())
    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 200,
        "body": "{\"status\":\"UP\"}",
    }


def test_backend_health_maps_upstream_exception_to_status_zero(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    class FakePoolManager:
        def request(self, method: str, url: str, timeout: float) -> None:
            raise OSError("upstream unavailable")

    monkeypatch.setattr("app.main.urllib3.PoolManager", FakePoolManager)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/backend-health", headers=headers())
    assert response.status_code == 200
    assert response.json() == {
        "requestedBy": "student",
        "status": 0,
        "body": "upstream unavailable",
    }


@pytest.mark.parametrize(
    ("method", "path", "request_kwargs"),
    [
        ("GET", "/api/tasks", {}),
        ("POST", "/api/tasks", {"json": {}}),
        ("GET", "/api/summary", {}),
        ("POST", "/api/requirements", {"files": {"file": ("input.txt", b"x", "text/plain")}}),
        ("GET", "/api/backend-health", {}),
    ],
)
def test_all_endpoints_reject_missing_authentication(
    tmp_path: Path,
    method: str,
    path: str,
    request_kwargs: dict[str, object],
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.request(method, path, **request_kwargs)
    assert response.status_code == 401
    assert response.json() == {"detail": "Invalid token: invalid token"}


def test_create_task_rejects_missing_body(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post("/api/tasks", headers=headers())
    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body"]


def test_upload_rejects_missing_file(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.post("/api/requirements", headers=headers())
    assert response.status_code == 422
    assert response.json()["detail"][0]["loc"] == ["body", "file"]


@pytest.mark.parametrize(
    ("method", "path", "request_kwargs"),
    [
        ("GET", "/api/tasks", {}),
        ("POST", "/api/tasks", {"json": {}}),
        ("GET", "/api/summary", {}),
    ],
)
def test_task_endpoints_return_500_when_database_is_unavailable(
    tmp_path: Path,
    method: str,
    path: str,
    request_kwargs: dict[str, object],
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        app.state.connection.close()
        response = client.request(method, path, headers=headers(), **request_kwargs)
    assert response.status_code == 500
    assert response.text == "Internal Server Error"


def test_upload_returns_500_when_storage_fails(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    def fail_write(self: Path, data: bytes) -> int:
        raise OSError("storage unavailable")

    monkeypatch.setattr(Path, "write_bytes", fail_write)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("input.txt", b"content", "text/plain")},
        )
    assert response.status_code == 500
    assert response.text == "Internal Server Error"


def test_backend_health_returns_500_for_unusable_identity(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    class UpstreamResponse:
        status = 200
        data = b"healthy"

    class FakePoolManager:
        def request(self, method: str, url: str, timeout: float) -> UpstreamResponse:
            return UpstreamResponse()

    monkeypatch.setattr("app.main.urllib3.PoolManager", FakePoolManager)
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    app.dependency_overrides[legacy_identity] = lambda: None
    with TestClient(app, raise_server_exceptions=False) as client:
        response = client.get("/api/backend-health", headers=headers())
    assert response.status_code == 500
    assert response.text == "Internal Server Error"


def test_uploads_requirement(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"Login must be accessible", "text/plain")},
        )
    assert response.status_code == 201
    assert (upload_dir / "requirements.txt").exists()


def test_rejects_invalid_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers={"Authorization": "Bearer invalid"})
    assert response.status_code == 401
