-- ENVIO: esquema objetivo (SQLite, gestionado por Room). Version 2 de la base.
-- La base v1 solo tiene `contacts` y `messages`. Se migra con una Migration(1, 2) escrita a mano:
-- ALTER TABLE ... ADD COLUMN para lo nuevo y CREATE TABLE para las tablas nuevas.
-- Todo el dato vive en el telefono del usuario. No hay servidor propio ni filas de otros usuarios,
-- asi que no hay reglas por fila: la proteccion es el almacenamiento privado de la app y las
-- comprobaciones de la capa de datos (ver architecture.md, "Reglas de acceso").
-- Tiempos: milisegundos epoch UTC en INTEGER.

CREATE TABLE contacts (
  id            TEXT PRIMARY KEY,                 -- ID de ENVIO de 10 caracteres
  name          TEXT NOT NULL,
  publicKey     TEXT NOT NULL,                    -- clave publica ECDH P-256 en base64
  isNew         INTEGER NOT NULL DEFAULT 0,       -- aparecio porque nos escribio primero
  about         TEXT,                             -- NUEVO: texto de info
  photoPath     TEXT,                             -- NUEVO: miniatura local del avatar
  photoHash     TEXT,                             -- NUEVO: para saber si cambio la foto
  blocked       INTEGER NOT NULL DEFAULT 0,       -- NUEVO: descartar todo lo que llegue
  groupOnly     INTEGER NOT NULL DEFAULT 0        -- NUEVO: conocido solo por un grupo, no sale en Chats
);

-- Una fila por conversacion. id = id del contacto (DIRECT) o id del grupo (GROUP).
CREATE TABLE chats (
  id               TEXT PRIMARY KEY,
  type             TEXT NOT NULL CHECK (type IN ('DIRECT','GROUP')),
  pinned           INTEGER NOT NULL DEFAULT 0,
  archived         INTEGER NOT NULL DEFAULT 0,
  mutedUntil       INTEGER NOT NULL DEFAULT 0,    -- 0 = no silenciado, Long.MAX = siempre
  unread           INTEGER NOT NULL DEFAULT 0,
  markedUnread     INTEGER NOT NULL DEFAULT 0,
  disappearAfterSec INTEGER NOT NULL DEFAULT 0,   -- 0 = apagado
  wallpaper        TEXT,
  draft            TEXT,
  updatedAt        INTEGER NOT NULL
);
CREATE INDEX idx_chats_list ON chats (archived, pinned, updatedAt);

CREATE TABLE groups (
  id             TEXT PRIMARY KEY,                -- UUID
  name           TEXT NOT NULL,
  about          TEXT,
  photoPath      TEXT,
  createdBy      TEXT NOT NULL,
  createdAt      INTEGER NOT NULL,
  adminsOnlyPost INTEGER NOT NULL DEFAULT 0,
  version        INTEGER NOT NULL DEFAULT 1       -- sube con cada cambio; gana la mayor
);

CREATE TABLE group_members (
  groupId   TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  userId    TEXT NOT NULL,                        -- sin FK: puede ser alguien que aun no es contacto
  role      TEXT NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('ADMIN','MEMBER')),
  addedAt   INTEGER NOT NULL,
  PRIMARY KEY (groupId, userId)
);
CREATE INDEX idx_group_members_user ON group_members (userId);

-- `contactId` es el nombre heredado de la v1 y ahora significa "id del chat" (contacto o grupo).
-- No se renombra para no reconstruir la tabla; ver architecture.md.
CREATE TABLE messages (
  id            TEXT PRIMARY KEY,                 -- UUID
  contactId     TEXT NOT NULL,                    -- id del chat
  senderId      TEXT,                             -- NUEVO: quien lo escribio (null = yo)
  outgoing      INTEGER NOT NULL,
  type          TEXT NOT NULL CHECK (type IN ('TEXT','IMAGE','VIDEO','AUDIO','FILE')),
  text          TEXT,                             -- texto o pie de foto
  fileName      TEXT,
  mime          TEXT,
  filePath      TEXT,
  size          INTEGER NOT NULL DEFAULT 0,
  durationMs    INTEGER NOT NULL DEFAULT 0,       -- NUEVO: audio y video
  waveform      TEXT,                             -- NUEVO: ~40 amplitudes en base64 para la onda
  thumbPath     TEXT,                             -- NUEVO: miniatura de video
  replyToId     TEXT,                             -- NUEVO
  replyPreview  TEXT,                             -- NUEVO: copia del texto citado, por si se borra el original
  forwarded     INTEGER NOT NULL DEFAULT 0,       -- NUEVO
  starred       INTEGER NOT NULL DEFAULT 0,       -- NUEVO
  editedAt      INTEGER NOT NULL DEFAULT 0,       -- NUEVO
  deletedForAll INTEGER NOT NULL DEFAULT 0,       -- NUEVO: queda el aviso "Se elimino este mensaje"
  ts            INTEGER NOT NULL,
  status        TEXT NOT NULL CHECK (status IN ('PENDING','SENT','DELIVERED','READ','FAILED','RECEIVED')),
  channel       TEXT CHECK (channel IN ('NEARBY','INTERNET')),
  deliveredAt   INTEGER NOT NULL DEFAULT 0,       -- NUEVO
  readAt        INTEGER NOT NULL DEFAULT 0        -- NUEVO
);
CREATE INDEX idx_messages_chat ON messages (contactId, ts);
CREATE INDEX idx_messages_queue ON messages (outgoing, status);
CREATE INDEX idx_messages_starred ON messages (starred, ts);
CREATE INDEX idx_messages_reply ON messages (replyToId);

-- Busqueda de mensajes (Room FTS4, tabla externa).
CREATE VIRTUAL TABLE messages_fts USING fts4(content="messages", text, fileName);

-- Estado de entrega por miembro. Solo para mensajes de grupo (en 1:1 vale el estado del mensaje).
CREATE TABLE deliveries (
  messageId  TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  memberId   TEXT NOT NULL,
  status     TEXT NOT NULL CHECK (status IN ('PENDING','SENT','DELIVERED','READ','FAILED')),
  channel    TEXT CHECK (channel IN ('NEARBY','INTERNET')),
  updatedAt  INTEGER NOT NULL,
  PRIMARY KEY (messageId, memberId)
);
CREATE INDEX idx_deliveries_queue ON deliveries (status, memberId);

CREATE TABLE reactions (
  messageId TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  userId    TEXT NOT NULL,
  emoji     TEXT NOT NULL,
  ts        INTEGER NOT NULL,
  PRIMARY KEY (messageId, userId)                 -- una reaccion por persona y mensaje
);

-- Cola de mensajes de control (leido, borrar, editar, reaccion, perfil, grupo).
-- Misma politica que los mensajes: se guardan, salen por el mejor canal y se reintentan.
CREATE TABLE control_queue (
  id        TEXT PRIMARY KEY,                     -- UUID
  toId      TEXT NOT NULL,
  op        TEXT NOT NULL,                        -- read | delete | edit | react | profile | grp.*
  payload   TEXT NOT NULL,                        -- JSON, va cifrado en el sobre
  ts        INTEGER NOT NULL,
  status    TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','DELIVERED','FAILED'))
);
CREATE INDEX idx_control_queue ON control_queue (status, toId);

-- Si llega "borrar" o "editar" antes que el mensaje (los canales no garantizan orden), se anota aqui.
CREATE TABLE tombstones (
  messageId TEXT PRIMARY KEY,
  op        TEXT NOT NULL CHECK (op IN ('delete','edit')),
  payload   TEXT,
  ts        INTEGER NOT NULL
);

-- Ajustes: SharedPreferences, no tabla. Claves: theme, fontSize, enterIsSend, wallpaper,
-- notifSound, notifVibrate, readReceipts, autoDownloadWifi, autoDownloadData, lastBackupAt.

-- ---- Fase 3 (no se crean todavia) ----
-- statuses(id, authorId, type, filePath, text, createdAt, expiresAt)
-- status_views(statusId, viewerId, viewedAt)
-- calls(id, chatId, direction, kind, startedAt, durationMs, result)
