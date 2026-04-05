# Tg-Gallery | Telegram Bot

Telegram bot that helps organize shared media into collections and stores them on a host machine.

## What the bot can do
- Restricts access to a whitelist of Telegram user IDs.
- Creates collections (directories) inside the storage directory.
- Adds media to a chosen collection:
  - Photos (highest available resolution)
  - Files/documents
  - Videos
  - Video messages
- Lists collections with pagination when choosing where to upload.

## Configuration
The bot reads configuration from environment variables or from a properties file pointed to by
`CONFIGURATION_FILEPATH`.

Required environment variables:
- `TOKEN` — Telegram bot token (from BotFather).
- `DIRECTORY` — host path where collections are stored.
- `WHITELIST` — comma-separated Telegram user IDs allowed to use the bot (e.g., `100001,100002`).

Optional:
- `CONFIGURATION_FILEPATH` — path to a `config` file with the same keys.

See `.env.example` for a template.

## Run with Docker Compose
1. Copy the example env file and fill in your values:
   ```bash
   cp .env.example .env
   ```
2. Start the container:
   ```bash
   docker compose up -d --build
   ```

The `compose.yaml` mounts `${DIRECTORY}` from your host to `/app/Gallery` in the container,
and sets `DIRECTORY=/app/Gallery` inside the container.

## Build the Docker image
```bash
docker build -t tg-gallery:latest .
```

## Run a container (docker run)
```bash
docker run -d \
  --name tg-gallery-bot \
  -e TOKEN="<your_bot_token>" \
  -e WHITELIST="100001,100002" \
  -e DIRECTORY="/app/Gallery" \
  -v /host/path/Gallery:/app/Gallery \
  tg-gallery:latest
```

