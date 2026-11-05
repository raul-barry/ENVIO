# Build log ENVIO

| Hito | Estado | Contenido | Falta |
| --- | --- | --- | --- |
| 0 | hecho (compila en CI) | Lista de chats con no leidos y palomitas, chat con burbujas con cola, separadores de fecha, 5 estados de palomitas, responder, copiar, destacar, eliminar para mi, destacados, perfil, ajustes (tema, letra, Enter, notificaciones, privacidad, almacenamiento), recibos de lectura con cola de control, migracion Room 1 a 2 | Probar en telefono real |
| 1 | pendiente | Emojis, adjuntos con camara, visor de imagen, video, notas de voz con mantener pulsado, bloquear, borrar chat, grupos, respaldo cifrado de identidad | todo |
| 2 y 3 | pendiente | Segun architecture.md | todo |

Notas: la tabla `chats` se aplaza al hito 2. Marca propia: acento propio, sin logos ni textos de WhatsApp. Paso replica-brand/launch/deploy pendiente (requiere dominio y cuentas del usuario).

## Test (replica-test, sin dispositivo)
Compila en CI. No se ejecuto en telefono. Casos manuales a probar: enviar con y sin internet, doble palomita azul al leer, responder con cita, borrar para mi, reinstalar sobre APK anterior (migracion v1 a v2), tema oscuro.

## Parity (replica-diff, estimado)
Must-haves de hito 0 cubiertos. Paridad global estimada ~35% de la matriz.
