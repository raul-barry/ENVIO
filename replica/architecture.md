# Arquitectura: ENVIO (reconstruccion de las funciones centrales de WhatsApp)

ENVIO ya existe como app Android sin servidor propio. Este plan la lleva a la experiencia de WhatsApp sin cambiar esa base: todo el dato vive en el telefono, los mensajes salen por conexion directa (Bluetooth / Wi-Fi Direct) o por internet (relevo MQTT publico), y todo va cifrado de extremo a extremo.

## Pila

La pila por defecto del plan (web, Postgres, Stripe, Vercel) no aplica: ENVIO es una app nativa, ya tiene pila y no tiene servidor.

| capa | eleccion | por que |
| --- | --- | --- |
| app | Kotlin 2.0 + Jetpack Compose + Material 3 | ya es la base; Compose permite copiar el diseño de WhatsApp sin XML |
| base de datos | Room 2.6 (SQLite) con KSP | ya esta; migracion v1 a v2 escrita a mano |
| busqueda | Room FTS4 | busqueda de mensajes sin libreria nueva |
| identidad | claves ECDH P-256 + ID de 10 caracteres, EncryptedSharedPreferences | ya esta; reemplaza al numero de telefono |
| cifrado | AES-256-GCM sobre clave compartida ECDH | ya esta; los grupos cifran por separado para cada miembro |
| transporte directo | Google Nearby Connections | ya esta; Bluetooth, BLE y Wi-Fi Direct sin preguntar |
| transporte internet | Eclipse Paho MQTT contra EMQX y HiveMQ publicos | ya esta; sin cuentas. Se puede cambiar por servidor propio en `RelayConfig` |
| servidor | ninguno | el relevo publico solo reenvia datos cifrados |
| ajustes | SharedPreferences | pocas claves; no hace falta DataStore |
| emojis | `androidx.emoji2:emoji2-emojipicker` | panel de emojis completo sin catalogo propio |
| camara | contrato del sistema `TakePicture` / `CaptureVideo` | sin permiso de camara nuevo ni CameraX |
| video | `androidx.media3:media3-exoplayer` | reproductor y miniaturas |
| imagenes | Coil (ya esta) | miniaturas y visor |
| pagos, correo, trabajos | ninguno | el servicio en primer plano ya reintenta la cola |
| distribucion | GitHub Actions + Releases | ya esta; el APK sale firmado con llave fija |

## Esquema

Tablas: 9 (contacts, chats, groups, group_members, messages, deliveries, reactions, control_queue, tombstones) mas la virtual de busqueda `messages_fts`. Tres tablas mas quedan para la fase 3 (estados y llamadas). SQL completo en `replica/schema.sql`.

Reglas de acceso: **comprobaciones en la capa de datos**, no reglas por fila. No hay servidor ni datos de otras personas: cada base es privada del telefono. Las comprobaciones que si importan son las de lo que llega de fuera:

- Un sobre solo se acepta si descifra con la clave guardada del remitente.
- Un contacto bloqueado se descarta antes de tocar la base.
- `delete`, `edit` y `react` solo valen si el remitente es el autor del mensaje (`delete` y `edit`) o miembro del chat (`react`).
- Las operaciones de grupo solo valen si el remitente es ADMIN en la copia local del grupo (o si es el primer `grp.create`, que fija al creador como admin).

Decisiones que conviene saber:

- `messages.contactId` ahora significa "id del chat". Se conserva el nombre para no reconstruir la tabla en la migracion; si molesta, se renombra en una migracion aparte.
- Los mensajes de control tienen cola propia (`control_queue`) para que lleguen aunque el contacto este lejos, igual que los mensajes.
- Un mensaje de grupo es una fila en `messages` y una fila por miembro en `deliveries`. El archivo se cifra una sola vez; la clave del archivo va dentro del sobre de cada miembro.
- Las marcas de un grupo: una palomita gris al salir, dos grises cuando todos recibieron, dos azules cuando todos leyeron (igual que el original).

## Protocolo (reemplaza a la API)

No hay rutas HTTP. La "API" son los tipos de sobre que viajan por los dos canales. Cada sobre sigue siendo `Envelope(id, from, to, ts, type, ct, spk, sname)` y el contenido real va cifrado en `ct`.

| tipo / op | hace | quien lo puede enviar | contenido (cifrado) | respuesta | flujo |
| --- | --- | --- | --- | --- | --- |
| TEXT | mensaje de texto | cualquier contacto no bloqueado | texto, replyTo, reenviado | ACK | F03, F06 |
| IMAGE | foto con pie | igual | pie, mime, clave de archivo, replyTo | ACK | F04 |
| VIDEO | video con pie | igual | pie, duracion, miniatura, clave de archivo | ACK | F04 |
| AUDIO | nota de voz o audio | igual | duracion, onda, clave de archivo | ACK | F05 |
| FILE | documento | igual | nombre, mime, clave de archivo | ACK | F04 |
| ACK | entregado (solo internet; en conexion directa vale el exito de la transferencia) | receptor | "ack:<id>" | ninguna | F03 |
| CTRL read | leido | receptor | lista de ids | ACK | F03 |
| CTRL delete | eliminar para todos (hasta 48 h) | autor | id | ACK | F06 |
| CTRL edit | editar (hasta 15 min) | autor | id, texto nuevo | ACK | F06 |
| CTRL react | reaccion con emoji o quitar | miembro del chat | id, emoji o vacio | ACK | F06 |
| CTRL profile | nombre, info y miniatura de foto (<= 30 KB) | el dueno del perfil | campos y foto | ACK | F09 |
| CTRL typing | escribiendo (efimero, sin cola ni ACK) | contacto | on / off | ninguna | F03 |
| CTRL grp.create | crear grupo | creador | id, nombre, miembros con su clave publica | ACK | F07 |
| CTRL grp.update | nombre, foto, descripcion | admin | campos, version | ACK | F07 |
| CTRL grp.add / grp.remove | agregar o quitar miembros | admin | miembros, version | ACK | F07 |
| CTRL grp.leave | salir del grupo | el que sale | id | ACK | F07 |

Total: 17 tipos de sobre. La API interna de `Core` (funciones que usa la interfaz): `sendText`, `sendMedia`, `reply`, `forward`, `star`, `deleteForMe`, `deleteForAll`, `edit`, `react`, `markRead`, `setTyping`, `createGroup`, `updateGroup`, `addMember`, `removeMember`, `leaveGroup`, `block`, `pin`, `archive`, `mute`, `deleteChat`, `search`, `exportChat`.

Webhooks de entrada y salida: no hay. Trabajos: el servicio en primer plano ya hace cada 15 s `watchdog` de conexion cercana, reconexion a internet y `flush` de las colas; se agrega un barrido diario que borra mensajes temporales vencidos, estados caducados (fase 3) y cache de relevo.

## Las partes que muerden

- **Orden y tiempos:** los dos canales no garantizan orden. Se ordena por la hora del remitente, recortada a "ahora" si viene del futuro (relojes distintos). `delete` y `edit` que llegan antes que el mensaje se guardan en `tombstones` y se aplican al llegar.
- **Idempotencia:** todos los sobres tienen id unico y el receptor ignora repetidos (ya funciona para mensajes); los de control deben ser idempotentes (borrar dos veces no rompe nada).
- **Carreras:** dos canales pueden entregar el mismo mensaje a la vez; la clave primaria en `messages.id` con `IGNORE` lo resuelve. Un mismo grupo puede cambiar a la vez en dos telefonos: gana la `version` mayor, y a igual version, el ID de admin menor.
- **Grupos sin servidor:** cifrar por miembro cuesta N sobres por mensaje. Con 3 a 20 miembros es aceptable; se limita el grupo a 32. Los miembros que no son contactos se conocen por la clave publica que viaja en `grp.create`.
- **Lectura y "escribiendo":** viajan como mensajes de control; "escribiendo" no se reintenta. Los recibos de lectura se pueden apagar en Ajustes, como en el original.
- **Servidor publico:** sin garantia de servicio ni de guardar mensajes. Por eso existe el ACK y el reintento durante 7 dias. Limite de 25 MB por archivo por internet; mas grande solo por conexion directa.
- **Bateria y segundo plano:** el servicio con la CPU despierta gasta bateria; es el precio de recibir con la pantalla apagada sin servidor de notificaciones push.
- **Almacenamiento:** los medios se guardan en claro dentro del almacenamiento privado de la app. Falta limpieza (ajustes de almacenamiento) y la copia de seguridad.
- **Identidad:** si se desinstala, se pierde el ID y las claves, y con ello los contactos y el historial. La copia de seguridad cifrada con frase de paso es la primera tarea del hito 2.
- **Android 14 y 15:** los servicios en primer plano de tipo `connectedDevice` exigen permisos de Bluetooth concedidos; sin ellos el servicio no arranca. Ya se maneja; hay que probar en equipos reales de varias marcas.
- **Llamadas:** necesitan senalizacion y, a veces, servidor TURN. Es el unico punto donde el modelo sin servidor propio no alcanza del todo. Se deja para el final.
- **Privacidad de metadatos:** el relevo publico ve quien escribe a quien y cuando. No se resuelve sin servidor propio.

## Orden de construccion

1. **Rebanada vertical (hito 0).** Pantallas S03, S04, S08, S14, S15, S16, S20. Tablas: `chats`, mas las columnas nuevas de `messages` y `contacts`. Sobres: TEXT existente mas `CTRL read`.
   - Migracion Room v1 a v2.
   - Lista de chats con contador de no leidos, hora, vista previa y fila de chat nueva.
   - Chat con burbujas con cola, separadores de fecha y marcas de envio de uno a cinco estados.
   - Barra de acciones al mantener pulsado: responder (cita), copiar, destacar, eliminar para mi.
   - Pantalla de destacados, perfil propio (nombre, info, foto) y Ajustes (perfil, chats, notificaciones, privacidad, almacenamiento).
   - Recibos de lectura.
2. **Obligatorias (hito 1).** S01, S02, S05, S06, S07, S11, S12, S13. Panel de emojis, hoja de adjuntos, foto con camara, visor de imagen con pie de foto, video con miniatura y reproductor, nota de voz mantener para grabar con onda, pantalla de nuevo chat, bloquear, eliminar chat, grupos (crear y escribir). Mas la copia de seguridad cifrada de la identidad.
3. **Recomendadas (hito 2).** S09, S10, S19, S21, S26, S29. Reenviar, eliminar para todos, reacciones, seleccion multiple, buscar mensajes, fijar, archivar, silenciar, escribiendo, responder desde la notificacion, admin de grupo, info de contacto y de grupo, exportar chat, pestanas Estados y Llamadas cuando existan.
4. **Opcionales (hito 3).** Estados de 24 horas, mensajes temporales, ver una vez, encuestas, ubicacion, llamadas de voz y video, bloqueo con huella.
5. **Arreglos de replica-entrepreneur:** pendiente hasta correr esa skill.
