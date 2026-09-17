"""Contract tests for the public FastAPI route surface."""

from app.main import app


EXPECTED_ROUTES = {
    ("POST", "/api/auth/register"),
    ("POST", "/api/auth/login"),
    ("POST", "/api/auth/logout"),
    ("GET", "/api/me"),
    ("PUT", "/api/me"),
    ("GET", "/api/families/current"),
    ("GET", "/api/families/current/members"),
    ("POST", "/api/families/current/invites"),
    ("GET", "/api/family-invites/{token}"),
    ("POST", "/api/family-invites/{token}/accept"),
    ("DELETE", "/api/families/current/members/{member_id}"),
    ("GET", "/api/elders"),
    ("POST", "/api/elders"),
    ("PUT", "/api/elders/{elder_id}"),
    ("POST", "/api/elders/{elder_id}/invite"),
    ("POST", "/api/elders/{elder_id}/unbind"),
    ("GET", "/api/elder-invites/{token}"),
    ("POST", "/api/elder-invites/{token}/accept"),
    ("GET", "/api/elder-client/profile"),
    ("GET", "/api/elder-client/reminders"),
    ("POST", "/api/elder-client/reminders/{reminder_id}/complete"),
    ("POST", "/api/elder-client/sos"),
    ("GET", "/api/elders/{elder_id}/reminders"),
    ("POST", "/api/elders/{elder_id}/reminders"),
    ("PUT", "/api/elders/{elder_id}/reminders/{reminder_id}"),
    ("DELETE", "/api/elders/{elder_id}/reminders/{reminder_id}"),
    ("GET", "/api/elders/{elder_id}/health-records"),
    ("POST", "/api/elders/{elder_id}/health-records"),
    ("PUT", "/api/elders/{elder_id}/health-records/{record_id}"),
    ("DELETE", "/api/elders/{elder_id}/health-records/{record_id}"),
    ("GET", "/api/elders/{elder_id}/devices"),
    ("POST", "/api/elders/{elder_id}/devices"),
    ("DELETE", "/api/elders/{elder_id}/devices/{device_id}"),
    ("GET", "/api/care-tasks"),
    ("GET", "/api/care-tasks/{task_id}"),
    ("POST", "/api/care-tasks"),
    ("PUT", "/api/care-tasks/{task_id}"),
    ("POST", "/api/care-tasks/{task_id}/complete"),
    ("DELETE", "/api/care-tasks/{task_id}"),
    ("GET", "/api/sos-events"),
    ("POST", "/api/sos-events/{event_id}/handle"),
    ("POST", "/api/sos-events/{event_id}/close"),
    ("GET", "/api/notification-settings"),
    ("PUT", "/api/notification-settings"),
    ("GET", "/api/privacy-settings"),
    ("PUT", "/api/privacy-settings"),
    ("POST", "/api/feedbacks"),
}


def test_frontend_api_contract_is_present():
    """Ensure every route currently used by the frontend remains available."""
    actual = {
        (method, route.path)
        for route in app.routes
        for method in getattr(route, "methods", set())
    }
    missing = EXPECTED_ROUTES - actual
    assert not missing, f"Missing API routes: {sorted(missing)}"


def test_health_route_is_present():
    """Ensure the deployment health-check endpoint remains available."""
    actual = {
        (method, route.path)
        for route in app.routes
        for method in getattr(route, "methods", set())
    }
    assert ("GET", "/health") in actual
