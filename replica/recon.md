# Mapa de reconocimiento: WhatsApp Messenger (Android)

Alcance: el bucle central de mensajería (lista de chats, chat uno a uno y de grupo, medios, notas de voz, acciones sobre mensajes, perfil y ajustes). Estados y llamadas se mapean pero van después.
Para: ENVIO, producto propio del usuario. Su diferencia es que no usa número de teléfono ni SIM y envía con y sin internet.
Fecha: 2026-11-03

Método: solo fuentes públicas. No se usó ninguna cuenta ni se leyó código ni tráfico de red de la app original. Las páginas oficiales (whatsapp.com/features, la ficha de Google Play y faq.whatsapp.com) no se pudieron abrir desde esta sesión, así que parte del mapa sale del conocimiento general de la app y está marcada con confianza media o "guess". Siguiente paso recomendado: que el usuario guarde capturas de su propio WhatsApp en `replica/screens/` para verificar cada pantalla.

## Fuentes

| # | fuente | URL | notas |
| --- | --- | --- | --- |
| 1 | TechRadar, "How to use WhatsApp" | https://www.techradar.com/how-to/how-to-use-whatsapp | leída. Lista de chats, pantalla de chat, adjuntos, ajustes, privacidad |
| 2 | Android Central, "How to handle messages with WhatsApp for Android" | https://www.androidcentral.com/how-handle-messages-whatsapp-android | leída. Destacar, borrar, borrar varios, palomitas |
| 3 | WhatsApp, página de grupos | https://www.whatsapp.com/groups?lang=en | aparece en la búsqueda, no leída |
| 4 | 9to5Mac, reacciones y comunidades | https://9to5mac.com/2022/04/14/whatsapp-announces-long-awaited-reactions-and-community-features/ | aparece en la búsqueda, no leída |
| 5 | WABetaInfo, novedades | https://wabetainfo.com/whatsapps-2022-a-look-back-at-a-great-year-and-what-we-expect-for-the-future/ | aparece en la búsqueda, no leída |
| 6 | Página de funciones de WhatsApp | https://www.whatsapp.com/features | bloqueada en esta sesión |
| 7 | Ficha de Google Play | https://play.google.com/store/apps/details?id=com.whatsapp | bloqueada en esta sesión |
| 8 | Centro de ayuda | https://faq.whatsapp.com/ | bloqueado en esta sesión |
| 9 | Cuenta propia del usuario | n/a | no usada |

## Bucle central

Escribir a una persona y que el mensaje llegue y se vea confirmado, sea cual sea el medio: texto, foto, audio o archivo, en un chat que se siente instantáneo.

## Pantallas

| ID | pantalla | cómo se llega | propósito | componentes clave | estados vistos |
| --- | --- | --- | --- | --- | --- |
| S01 | Bienvenida y permisos | primer arranque | aceptar condiciones y dar permisos. En el original aquí se verifica el número por SMS; en ENVIO se reemplaza por nombre más ID | logo, botón principal, campo de texto | inicial, permisos denegados |
| S02 | Configurar perfil | tras S01 | nombre y foto | avatar editable, campo de nombre | vacío, lleno |
| S03 | Lista de chats (pestaña Chats) | pantalla principal | ver conversaciones | barra superior, buscador, filtros, fila de chat, botón flotante de nuevo chat, fila Archivados | vacío, lleno, buscando, selección múltiple |
| S04 | Chat | tocar una fila de S03 | conversar | cabecera (avatar, nombre, en línea), burbujas, separador de fecha, citas, palomitas, barra de entrada | vacío, con mensajes, escribiendo, grabando, error de envío, sin conexión |
| S05 | Hoja de adjuntos | clip en S04 | elegir qué enviar | cuadrícula de iconos (documento, cámara, galería, audio, ubicación, contacto, encuesta) | cerrada, abierta |
| S06 | Grabación de nota de voz | mantener el micrófono en S04 | grabar audio | contador, deslizar para cancelar, candado | grabando, bloqueada, cancelada |
| S07 | Visor de imagen y video | tocar un medio | ver a pantalla completa, enviar con pie de foto | barra superior, zoom, campo de pie de foto | cargando, cargado, error |
| S08 | Barra de acciones de mensaje | mantener pulsado un mensaje | responder, copiar, destacar, reenviar, borrar, info | barra contextual, selector de reacciones | un mensaje, varios |
| S09 | Info del mensaje | acción de S08 | ver entregado y leído | lista con horas | cargado |
| S10 | Info de contacto o grupo | tocar la cabecera de S04 | medios, silenciar, bloquear, mensajes temporales | avatar grande, filas de ajustes, cuadrícula de medios | contacto, grupo, admin |
| S11 | Nuevo chat | botón flotante en S03 | elegir contacto o crear grupo | lista de contactos, buscador | vacío, lleno |
| S12 | Agregar contacto | desde S11 | en el original es por número; en ENVIO por código QR | QR propio, escáner | escaneando, error, agregado |
| S13 | Nuevo grupo | desde S11 | elegir miembros, nombre y foto | chips de miembros, campo de nombre | sin miembros, con miembros |
| S14 | Ajustes | menú de tres puntos en S03 | entrada a todos los ajustes | cabecera de perfil, lista de filas | cargado |
| S15 | Ajustes, perfil | S14 | editar nombre, info, foto | avatar, campos | cargado |
| S16 | Ajustes, chats | S14 | tema, fondo, tamaño de letra, Enter envía | filas, interruptores, selector | cargado |
| S17 | Ajustes, notificaciones | S14 | tonos y vibración | filas, interruptores | cargado |
| S18 | Ajustes, privacidad | S14 | última vez, confirmación de lectura, bloqueados | filas, interruptores | cargado |
| S19 | Ajustes, almacenamiento y datos | S14 | descarga automática, uso | filas, barra de uso | cargado |
| S20 | Mensajes destacados | menú del chat | ver mensajes marcados | lista de mensajes | vacío, lleno |
| S21 | Chats archivados | fila Archivados en S03 | ver chats archivados | igual que S03 | vacío, lleno |
| S22 | Estados (pestaña) | pestaña Estados | ver y publicar estados de 24 horas | anillos de avatar, botón de cámara | vacío, lleno |
| S23 | Visor de estado | tocar un estado | ver historias | barra de progreso, respuesta rápida | cargando, cargado |
| S24 | Llamadas (pestaña) | pestaña Llamadas | historial de llamadas | fila de llamada, botón de nueva llamada | vacío, lleno |
| S25 | Llamada en curso | tocar llamar | voz y video | botones silenciar, altavoz, colgar | llamando, conectada, fallida |
| S26 | Búsqueda | lupa en S03 o S04 | buscar chats y mensajes | barra de búsqueda, resultados | sin resultados, con resultados |
| S27 | Fondo del chat | S16 | elegir fondo | cuadrícula de fondos, colores lisos | cargado |
| S28 | Bloqueados | S18 | gestionar bloqueos | lista | vacío, lleno |
| S29 | Exportar chat | menú de S04 | guardar la conversación como texto | diálogo con o sin medios | cargado |

## Flujos

```
F01 Primer arranque
    S01 -> S02 -> S03
    clics en el camino feliz: 4 (escribir nombre, permitir, aceptar permisos, entrar)
    edge: permisos denegados, nombre vacío

F02 Agregar un contacto (ENVIO por QR)
    S03 -> S11 -> S12 -> S04
    clics: 4
    edge: QR inválido, contacto ya existente, sin cámara

F03 Enviar texto
    S04
    clics: 2 (escribir, enviar)
    edge: sin conexión (queda pendiente), mensaje largo, contacto lejos

F04 Enviar foto
    S04 -> S05 -> S07 -> S04
    clics: 4
    edge: archivo enorme, sin permisos, cancelar

F05 Enviar nota de voz
    S04 -> S06 -> S04
    clics: 1 (mantener y soltar)
    edge: cancelar deslizando, bloquear para manos libres, sin permiso de micrófono

F06 Responder, reenviar, destacar o borrar un mensaje
    S04 -> S08 -> S04
    clics: 2
    edge: selección múltiple, borrar para todos tras el límite de tiempo

F07 Crear un grupo
    S03 -> S11 -> S13 -> S04
    clics: 5+
    edge: sin miembros, nombre vacío, miembro sin conexión

F08 Archivar, fijar o silenciar un chat
    S03 (mantener pulsado)
    clics: 2

F09 Editar mi perfil
    S03 -> S14 -> S15
    clics: 3

F10 Buscar en un chat
    S04 -> S26
    clics: 2

F11 Ver un estado
    S22 -> S23
    clics: 1

F12 Llamar
    S04 -> S25
    clics: 1
    edge: sin conexión, el otro no responde
```

## Componentes

| componente | variantes | estados | usado en |
| --- | --- | --- | --- |
| Barra superior | principal, contextual (selección), con búsqueda | normal, seleccionando | S03 S04 S14 |
| Barra de pestañas | Chats / Estados / Llamadas | seleccionada, con insignia | S03 S22 S24 |
| Fila de chat | normal, fijada, silenciada, con no leídos | normal, presionada, seleccionada | S03 S21 |
| Burbuja de mensaje | entrante, saliente, con cola | enviando, enviado, entregado, leído, error | S04 |
| Burbuja de imagen | con pie de foto, grupal | cargando, cargada | S04 |
| Burbuja de audio | con onda, con velocidad | reproduciendo, pausada | S04 |
| Burbuja de documento | con icono por tipo | normal | S04 |
| Cita de respuesta | texto, imagen | normal | S04 |
| Indicador de palomitas | una gris, dos grises, dos azules, reloj | los cinco | S04 |
| Separador de fecha | chip | normal | S04 |
| Barra de entrada | texto, grabando, con respuesta | vacía, escribiendo, grabando | S04 |
| Panel de emojis | emoji, stickers, GIF | cerrado, abierto | S04 |
| Hoja de adjuntos | cuadrícula | cerrada, abierta | S05 |
| Botón flotante | nuevo chat, cámara | normal, presionado | S03 S22 |
| Avatar | pequeño, grande, con anillo de estado | con foto, con inicial | todas |
| Insignia de no leídos | número, punto | visible, oculta | S03 |
| Fila de ajustes | navegación, interruptor, selector | normal, apagada, encendida | S14 a S19 |
| Diálogo y hoja inferior | confirmación, opciones | abierto, cerrado | todas |
| Aviso breve (snackbar) | simple, con acción | visible | todas |

## Modelo de datos inferido

```
Perfil        id, nombre, info, foto, clave_publica
              evidencia: S02, S15, fuente 1
              confianza: alta

Contacto      id, nombre, foto, clave_publica, bloqueado
              evidencia: S11, S12, S28
              confianza: alta

Chat          id, tipo (uno_a_uno | grupo), contacto_id o grupo_id, fijado, archivado,
              silenciado_hasta, mensajes_temporales_seg, fondo, no_leidos, ultimo_mensaje_id
              evidencia: S03, S10, S21, fuente 1 (silenciar)
              confianza: alta

Mensaje       id, chat_id, remitente_id, tipo (texto | imagen | video | audio | documento | ubicacion | contacto | encuesta),
              texto, respuesta_a, reenviado, destacado, estado (pendiente | enviado | entregado | leido | error),
              borrado_para_todos, editado, creado, entregado_en, leido_en
              evidencia: S04, S08, S09, fuente 2 (palomitas, destacar, borrar)
              confianza: alta

Adjunto       id, mensaje_id, ruta, mime, tamano, duracion, miniatura, onda
              evidencia: S05, S06, S07
              confianza: media

Reaccion      mensaje_id, usuario_id, emoji
              evidencia: S08, fuente 4
              confianza: media

Grupo         id, nombre, foto, descripcion, creado_por, solo_admins_escriben
              evidencia: S13, S10, fuente 3
              confianza: media

MiembroGrupo  grupo_id, usuario_id, rol (admin | miembro), agregado_en
              evidencia: S10, S13
              confianza: media

Estado        id, autor_id, tipo, ruta, texto, creado, expira_en
              evidencia: S22, S23
              confianza: media

VistaEstado   estado_id, usuario_id, visto_en
              evidencia: S23
              confianza: guess

Llamada       id, chat_id, direccion, tipo (voz | video), inicio, duracion, resultado
              evidencia: S24, S25, fuente 1
              confianza: media

Ajustes       tema, tamano_letra, enter_envia, descarga_automatica, tonos, confirmacion_lectura, ultima_vez
              evidencia: S16 a S19, fuente 1
              confianza: media
```

Relaciones: Perfil 1-n Chat, Chat 1-n Mensaje, Mensaje 1-n Adjunto, Mensaje 1-n Reaccion, Grupo 1-n MiembroGrupo, Grupo 1-1 Chat, Estado 1-n VistaEstado.

## Matriz de funciones

Ver `features.csv`. La columna `clone` refleja lo que ENVIO ya hace hoy (`yes`, `partial` o `no`); el resto se llena durante la construcción.

## Fuera de alcance (no se puede o no se debe clonar)

- Verificación por número de teléfono y SMS, y el cambio de número: ENVIO lo reemplaza por ID más QR, a propósito.
- Sincronizar la agenda de contactos: depende de números de teléfono.
- La red y sus usuarios: el valor de WhatsApp es que todos están ahí; no se puede copiar.
- Canales, comunidades, WhatsApp Business, pagos, catálogos y Meta AI: servicios del propietario.
- Copia de seguridad en Google Drive y WhatsApp Web o varios dispositivos: dependen de infraestructura del propietario.
- Marca, logo, iconos, nombre, textos y catálogos de stickers de WhatsApp: ENVIO usa los suyos.

## Tamaño

Pantallas 29, flujos 12, entidades 12. Partes difíciles: grupos con cifrado de extremo a extremo sobre dos canales a la vez (directo e internet), confirmación de lectura y "escribiendo" sin servidor propio, borrar o editar para todos (hay que propagar mensajes de control por ambos canales), llamadas de voz y video (necesitan un servidor de señalización), estados que caducan. Tamaño: L (un trimestre para todo). Una primera rebanada vertical de 2 a 3 semanas cubre el bucle central: pestañas, chat con burbujas y palomitas, responder, borrar, destacar, perfil y ajustes.
