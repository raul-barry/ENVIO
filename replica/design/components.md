# Componentes de ENVIO

Especificación de cada componente de la lista del recon. Las medidas salen de lo que se sabe de la interfaz de WhatsApp para Android y de las convenciones de Material 3; **no se midieron sobre capturas** porque `replica/screens/` está vacía. Cuando haya capturas propias del usuario, se verifican estos números pantalla por pantalla.

Qué es propio y qué no:
- Colores: roles de `tokens.json`. El acento es un marcador azul neutro; `replica-brand` lo reemplaza. No se usa el verde de WhatsApp.
- Tipografía: Roboto (la del sistema Android, licencia abierta).
- Iconos: Material Icons (licencia Apache 2.0) o dibujados a mano. Las palomitas, la cola de la burbuja y la onda de audio se dibujan con `Canvas`: son originales.
- Textos: escritos desde cero en español. No se copia ningún texto de WhatsApp.
- Accesibilidad en todos: área táctil mínima de 48 dp, `contentDescription` en todo icono que actúa, contraste AA verificado por `contrast.py` (0 fallos en claro y oscuro).

Medidas base (`Dim`): barra superior 56, fila de pestañas 48, fila de chat 72, avatar de lista 48, avatar de cabecera 40, avatar grande 120, botón flotante 56, barra de entrada mínima 48, burbuja con radio 12 y cola de 4, ancho máximo de burbuja 80 % de la pantalla.

## Barra superior
```
variantes  principal (título + acciones), contextual (selección: cuenta + acciones), con búsqueda
alto       56
estados    normal, seleccionando (fondo accent suave), buscando (campo en lugar del título)
tokens     fondo bg, título text lg/600, iconos text, borde inferior border de 1
a11y       título como encabezado; cada icono con descripción; botón atrás siempre
usado en   S03 S04 S14
```

## Barra de pestañas
```
variantes  Chats / Estados / Llamadas (en el hito 0 solo Chats; las otras aparecen cuando existan)
alto       48
estados    seleccionada (texto accent y línea de 3), no seleccionada (text-muted), con insignia
tokens     texto xs/500 en mayúscula inicial, línea accent
a11y       rol de pestaña; la insignia se lee "3 sin leer"
usado en   S03 S22 S24
```

## Fila de chat
```
variantes  normal, fijada (icono de chincheta), silenciada (icono de campana tachada), con no leídos
alto       72
contenido  avatar 48 | nombre (base/500, 1 línea) + vista previa (sm, text-muted, 1 línea) | hora (meta) + insignia
estados    normal, presionada (surface), seleccionada (accent al 12 %), escribiendo ("escribiendo..." en accent)
tokens     separador border de 1, desde el borde del texto
a11y       toda la fila es un botón: "Ana, 2 mensajes sin leer, último mensaje: hola, 10:42"
usado en   S03 S21
```

## Burbuja de mensaje
```
variantes  entrante (bubble-in, izquierda), saliente (bubble-out, derecha), con cola (primer mensaje de una racha), sin cola
forma      radio 12 en todas las esquinas, 4 en la esquina de la cola
contenido  texto base, hora meta (text-muted) y palomitas al final a la derecha
ancho      máximo 80 % de la pantalla
estados    enviando (reloj), enviado (1 palomita), entregado (2 grises), leído (2 en tick-read), error (aviso danger "No se pudo enviar. Toca para reintentar"), eliminado ("Se eliminó este mensaje" en cursiva text-muted)
menú       mantener pulsado abre la barra contextual (S08)
a11y       lectura: "Tú, 10:42, entregado: hola"; las palomitas tienen texto, no solo forma
usado en   S04
```

## Burbuja de imagen y video
```
variantes  con pie de foto, sin pie (la hora se superpone abajo a la derecha sobre degradado), grupo de varias
tamaño     ancho de la burbuja; alto proporcional, máximo 300
estados    cargando (fondo surface con indicador), cargada, error (icono de reintento), video con botón de play 48 y duración
a11y       descripción "Imagen enviada por Ana"; el toque abre el visor
usado en   S04 S07
```

## Burbuja de audio
```
contenido  botón de play 40 | onda de 40 barras (tick-muted, reproducidas en accent) | duración | velocidad (1x, 1.5x, 2x)
estados    detenida, reproduciendo, pausada, descargando
a11y       "Nota de voz de 0:12, reproducir"; el progreso es un control deslizable accesible
usado en   S04
```

## Burbuja de documento
```
contenido  icono por tipo (PDF, texto, hoja, otro) | nombre en 1 línea | tamaño y tipo en text-muted
estados    normal, descargando
usado en   S04
```

## Cita de respuesta
```
variantes  texto, imagen (miniatura de 40), audio
forma      barra vertical de 4 en accent a la izquierda, fondo accent al 8 %, radio 8
contenido  nombre del autor (xs/600, accent) + vista previa de 2 líneas (sm)
estados    dentro de burbuja, encima de la barra de entrada (con botón cerrar)
a11y       "Respondiendo a Ana: hola". Tocarla salta al mensaje original
usado en   S04
```

## Indicador de palomitas
```
variantes  reloj (pendiente), una gris (enviado), dos grises (entregado), dos en tick-read (leído), error (círculo danger con signo)
tamaño     16 de alto, dibujo propio con Canvas, trazo 1.5
a11y       texto equivalente en el mensaje (ver burbuja)
usado en   S04, fila de chat (último mensaje propio)
```

## Separador de fecha
```
forma      pastilla centrada, radio pill, fondo bg al 90 %, texto xs
contenido  "Hoy", "Ayer", día de la semana, o fecha
usado en   S04
```

## Barra de entrada
```
contenido  botón de emojis | campo de texto (1 a 6 líneas, radio pill) | clip | cámara | botón redondo 48 (micrófono si vacío, enviar si hay texto)
estados    vacía, escribiendo, grabando (contador rojo + "Desliza para cancelar" + candado), con respuesta (cita arriba), sin permiso de micrófono (aviso)
tokens     fondo surface, botón redondo accent con on-accent
a11y       el campo se llama "Mensaje"; el botón cambia de descripción entre "Grabar nota de voz" y "Enviar"
usado en   S04
```

## Panel de emojis
```
variantes  emojis (picker del sistema androidx.emoji2), stickers y GIF (hito 3)
estados    cerrado, abierto (reemplaza el teclado sin saltar el alto)
usado en   S04
```

## Hoja de adjuntos
```
forma      hoja inferior con cuadrícula 3 x 2 de círculos de 56 con icono y etiqueta
opciones   Documento, Cámara, Galería, Audio (y luego Ubicación, Contacto, Encuesta)
estados    cerrada, abierta
usado en   S05
```

## Botón flotante
```
variantes  nuevo chat (icono de mensaje), cámara (en Estados)
tamaño     56, radio lg, fondo accent, esquina inferior derecha a 16
estados    normal, presionado, oculto al desplazar hacia abajo
a11y       "Nuevo chat"
usado en   S03 S22
```

## Avatar
```
variantes  40, 48, 120; con foto o con inicial sobre un color de 8 posibles derivados del ID
estados    foto, inicial, con anillo de estado (hito 3)
a11y       decorativo cuando el nombre está al lado; con descripción si va solo
usado en   todas
```

## Insignia de no leídos
```
forma      círculo o pastilla, alto 20, fondo accent, texto on-accent xs/600
contenido  número (99+ como máximo) o solo un punto si está marcado como no leído
usado en   S03
```

## Fila de ajustes
```
variantes  navegación (flecha), interruptor, selector (valor a la derecha), informativa
alto       56 (72 con subtítulo)
estados    normal, presionada, desactivada (text-muted al 60 %), encendida / apagada
a11y       la fila entera es el control; el interruptor no es un objetivo aparte
usado en   S14 a S19
```

## Diálogo y hoja inferior
```
variantes  confirmación (título, texto, 2 botones), opciones (lista), peligro (botón danger)
reglas     el botón destructivo nunca es el principal por defecto; Escape y toque fuera cierran
usado en   todas
```

## Aviso breve (snackbar)
```
variantes  simple, con acción ("Deshacer")
duración   4 s, 8 s si tiene acción
usado en   todas (por ejemplo "Mensaje eliminado. Deshacer")
```

## Estados que no se pueden olvidar

Vacío (sin chats, sin mensajes, sin destacados), cargando, error de envío, sin permiso, sin conexión de ningún tipo (los mensajes quedan "Pendiente" con un aviso fijo arriba del chat), y todos con tema claro y oscuro.
