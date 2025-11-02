# midcup_chat_server – Protocol-aligned JSON I/O

This server now uses the protocol classes under `com.chat.protocol` as the single source of truth for all login and chat JSON send/receive.

## Message types
Defined in `com.chat.protocol.MessageType`:
- `login_request` (client -> server)
- `login_response` (server -> client)
- `chat_private_send` (client -> server)
- `chat_private_receive` (server -> client)

## Schemas

Login request (client -> server):
```
{
  "type": "login_request",
  "username": "alice",
  "password": "secret"
}
```
Login response (server -> client):
```
{
  "type": "login_response",
  "uid": "alice",            // null on failure
  "success": true,
  "message": "Welcome, alice" // or error message on failure
}
```

Private chat send (client -> server, after login):
```
{
  "type": "chat_private_send",
  "from": "alice",              // ignored by server; server uses logged-in user
  "to": "bob",
  "content": "Hi Bob",
  "timestamp": 1730540000000
}
```
Private chat receive (server -> client, to recipient if online):
```
{
  "type": "chat_private_receive",
  "from": "alice",
  "to": "bob",
  "content": "Hi Bob",
  "timestamp": 1730540001234
}
```

## Implementation notes
- ClientHandler reads one JSON object per line, requires a `type` field, and routes to protocol handlers.
- Login handling uses `LoginRequest`/`LoginResponse`. Passwords are not printed in logs.
- Private chat handling uses `ChatPrivateSend`/`ChatPrivateReceive`.
- Message persistence is done via `ChatService.savePrivateMessage(from, to, content)`.
- Online delivery uses `OnlineUserManager` to push messages if the recipient is connected.

## Build and Run (Windows cmd)
If Maven is on your PATH:
```bat
cd /d D:\code\IDEA\midcup_chat_server
mvn -DskipTests clean package
java -cp target\midcup_chat_server-1.0-SNAPSHOT.jar;target\classes;target\dependency\* Main
```
Or run `Main.main()` directly from your IDE. Ensure MySQL is reachable per `DatabaseManager` settings.

## Files touched
- `src/main/java/com/chat/handler/ClientHandler.java` – protocol-based routing and replies
- `src/main/java/com/chat/handler/ChatHandler.java` – private chat persist + push
- `src/main/java/com/chat/handler/LoginHandler.java` – returns `LoginResponse`
- `src/main/java/com/chat/core/ChatService.java` – `savePrivateMessage`
- `src/main/java/com/chat/protocol/*` – protocol POJOs and type constants

Non-protocol legacy envelopes (`com.chat.model.Request/Response`, `ChatMessage`) remain for now but are no longer used by the socket path.
