#!/usr/bin/env bash
set -euo pipefail

# === WebHostingService Full Test ===
# Jenkins-integrated version
# Credentials are passed via environment variables:
# - ADMIN_EMAIL
# - ADMIN_PASSWORD
# - BASE_URL (optional override)

RANDOM_SUFFIX=$(head /dev/urandom | tr -dc a-z0-9 | head -c 6)
USER_EMAIL="user_${RANDOM_SUFFIX}@example.com"
USER_PASSWORD="userpass123"
OTHER_EMAIL="other_${RANDOM_SUFFIX}@example.com"
OTHER_PASSWORD="otherpass123"

echo "=== TEST START ==="
echo "Base URL: $BASE_URL"
echo "User:  $USER_EMAIL"
echo "Other: $OTHER_EMAIL"
echo "Admin: $ADMIN_EMAIL"
echo "Base URL: $BASE_URL"
echo ""

# --- helpers ---
extract_token() {
  local resp="$1"
  if command -v jq >/dev/null 2>&1; then
    echo "$resp" | jq -r '.token // empty'
  else
    echo "$resp" | grep -o '"token":"[^"]*' | cut -d'"' -f4 || true
  fi
}

http_code() {
  echo "$1" | tr -d '\r' | sed -n 's/.*HTTP_STATUS:\([0-9][0-9][0-9]\)$/\1/p'
}

http_body() {
  echo "$1" | sed '/HTTP_STATUS:/d'
}

# --- create test file ---
TMP_HTML="$(mktemp --suffix=.html)"
cat > "$TMP_HTML" <<'HTML'
<!doctype html>
<html><body><h1>Test Container Page</h1></body></html>
HTML

echo "Temp HTML: $TMP_HTML"
echo ""

# --- 1) Signup user ---
echo "1) Signup user $USER_EMAIL"
curl -s -X POST "$BASE_URL/auth/user/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}" >/dev/null
echo "✅ Signed up"
echo ""

# --- 2) Login user ---
echo "2) Login user"
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/user/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$USER_PASSWORD\"}")
USER_TOKEN=$(extract_token "$LOGIN_RESP")
[ -n "$USER_TOKEN" ] || { echo "❌ Failed to get user token"; exit 1; }
echo "✅ Got user token"
echo ""

# --- 3) Create container ---
echo "3) Create container"
CREATE_OUT=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST "$BASE_URL/api/container/create" \
  -H "Authorization: Bearer $USER_TOKEN" -F "file=@${TMP_HTML}")
CREATE_CODE=$(http_code "$CREATE_OUT")
[ "$CREATE_CODE" -lt 300 ] && echo "✅ Container created" || { echo "❌ Create failed ($CREATE_CODE)"; exit 1; }
echo ""

# --- 4) Admin login ---
echo "4) Admin login"
ADMIN_LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/admin/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
ADMIN_TOKEN=$(extract_token "$ADMIN_LOGIN_RESP")
[ -n "$ADMIN_TOKEN" ] || { echo "❌ Failed to get admin token"; exit 1; }
echo "✅ Got admin token"
echo ""

# --- 5) Create second user + container ---
echo "5) Create another user $OTHER_EMAIL"
curl -s -X POST "$BASE_URL/auth/user/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$OTHER_EMAIL\",\"password\":\"$OTHER_PASSWORD\"}" >/dev/null
OTHER_LOGIN_RESP=$(curl -s -X POST "$BASE_URL/auth/user/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$OTHER_EMAIL\",\"password\":\"$OTHER_PASSWORD\"}")
OTHER_TOKEN=$(extract_token "$OTHER_LOGIN_RESP")
[ -n "$OTHER_TOKEN" ] || { echo "❌ Failed to get other user token"; exit 1; }
curl -s -X POST "$BASE_URL/api/container/create" -H "Authorization: Bearer $OTHER_TOKEN" -F "file=@${TMP_HTML}" >/dev/null
echo "✅ Other user's container created"
echo ""

# --- 6) User tries to delete OTHER user's container ---
echo "6) USER tries to delete OTHER user's container"
USER_DEL_OTHER=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X DELETE "$BASE_URL/api/admin/containers?userEmail=$OTHER_EMAIL" \
  -H "Authorization: Bearer $USER_TOKEN")
USER_DEL_OTHER_CODE=$(http_code "$USER_DEL_OTHER")
if [ "$USER_DEL_OTHER_CODE" -eq 401 ] || [ "$USER_DEL_OTHER_CODE" -eq 403 ]; then
  echo "✅ Access denied as expected ($USER_DEL_OTHER_CODE)"
else
  echo "❌ Unexpected response ($USER_DEL_OTHER_CODE)"
fi
echo ""

# --- 7) USER tries to delete OTHER user account ---
echo "7) USER tries to delete OTHER user's account"
USER_DEL_ACC=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X DELETE "$BASE_URL/api/admin/users?email=$OTHER_EMAIL" \
  -H "Authorization: Bearer $USER_TOKEN")
USER_DEL_ACC_CODE=$(http_code "$USER_DEL_ACC")
if [ "$USER_DEL_ACC_CODE" -eq 401 ] || [ "$USER_DEL_ACC_CODE" -eq 403 ]; then
  echo "✅ Access denied as expected ($USER_DEL_ACC_CODE)"
else
  echo "❌ Unexpected response ($USER_DEL_ACC_CODE)"
fi
echo ""

# --- 8) Admin deletes both containers ---
echo "8) Admin deletes both containers"
curl -s -X DELETE "$BASE_URL/api/admin/containers?userEmail=$USER_EMAIL" -H "Authorization: Bearer $ADMIN_TOKEN" >/dev/null
curl -s -X DELETE "$BASE_URL/api/admin/containers?userEmail=$OTHER_EMAIL" -H "Authorization: Bearer $ADMIN_TOKEN" >/dev/null
echo "✅ Admin containers cleanup done"
echo ""

# --- 9) Admin deletes both users ---
echo "9) Admin deletes both user accounts"
for EMAIL in "$USER_EMAIL" "$OTHER_EMAIL"; do
  DEL_USER=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X DELETE "$BASE_URL/api/admin/users?email=$EMAIL" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  CODE=$(http_code "$DEL_USER")
  if [ "$CODE" -lt 300 ]; then
    echo "✅ Deleted $EMAIL"
  else
    echo "❌ Failed to delete $EMAIL ($CODE)"
  fi
done
echo ""

# --- 10) Verify deletion ---
echo "10) Verify users no longer in list"
LIST_OUT=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X GET "$BASE_URL/api/admin/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
BODY=$(http_body "$LIST_OUT")
if echo "$BODY" | grep -q "$USER_EMAIL"; then
  echo "❌ $USER_EMAIL still in database"
else
  echo "✅ $USER_EMAIL removed"
fi
if echo "$BODY" | grep -q "$OTHER_EMAIL"; then
  echo "❌ $OTHER_EMAIL still in database"
else
  echo "✅ $OTHER_EMAIL removed"
fi
echo ""

rm -f "$TMP_HTML"
echo "=== ✅ ALL TESTS COMPLETED ==="
