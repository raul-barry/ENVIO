# ENVIO

App Android para enviar mensajes, imagenes, audios y archivos de cualquier tipo **con o sin internet**, sin numero de telefono ni SIM.

Kotlin + Jetpack Compose. Android 8.0+ (API 26). No hay que configurar nada: ni cuentas ni servidores.

## Como funciona (en simple)

ENVIO tiene dos caminos y elige solo el mejor para cada mensaje. Tu siempre tocas "Enviar" igual.

1. **Sin internet (directo):** cada telefono con ENVIO se anuncia por Bluetooth y busca a los demas. Cuando encuentra a un contacto tuyo se conecta solo, sin preguntar, y le pasa el mensaje directo (Bluetooth para lo chico, Wi-Fi Direct para fotos y archivos grandes). No usa router, ni datos, ni SIM. Alcance: de 10 a 100 metros.
2. **Con internet:** el mensaje viaja cifrado por un servidor publico de relevo (MQTT) hasta el telefono del contacto, aunque esten lejos. El telefono del contacto responde con una confirmacion; hasta que llega, ENVIO reintenta solo.

Estados de cada mensaje: **Pendiente** (esperando algun camino) > **Enviado** (ya salio por internet, falta confirmacion) > **Entregado** (el contacto lo recibio).

## Instalar

Abre en el telefono: https://github.com/raul-barry/ENVIO/releases/tag/latest y descarga `ENVIO.apk`. Permite "instalar apps desconocidas".

Los APK nuevos se instalan **encima** del anterior sin perder contactos (todos van firmados con la misma llave). Ojo: si tenias instalada una version vieja (la primera, firmada con otra llave) hay que desinstalarla una sola vez.

## Probar

1. Instala ENVIO en dos telefonos, escribe tu nombre y acepta los permisos (se piden una sola vez).
2. En uno abre `+` y muestra el QR; en el otro escanealo. Con que uno escanee al otro basta.
3. **Con internet:** envia un mensaje; en la pantalla de chats veras "Internet: conectado".
4. **Sin internet:** apaga datos y Wi-Fi de internet, deja Bluetooth y Wi-Fi encendidos, y acerca los telefonos. En la pantalla de chats pasa a "Cercanos: conexion directa" y los mensajes salen directo.
5. Si pones el telefono con pantalla apagada mucho rato, acepta el aviso "ignorar ahorro de bateria" para que Android no lo duerma.

El emulador no sirve para lo directo: se necesitan telefonos reales.

## Piezas

| Pieza | Que hace |
|---|---|
| Identidad | Al instalar se genera un ID de 10 letras y un par de claves (ECDH P-256). Los contactos se agregan escaneando un QR. |
| Cifrado | Extremo a extremo (clave compartida AES-256, AES-GCM). Archivos cifrados por bloques de 64 KB. El servidor solo ve datos cifrados. |
| Cola de envio | Todo mensaje se guarda como *Pendiente* y sale por el mejor canal. Si falla uno, prueba el otro. Sin canal, espera. |
| Canal directo | Google Nearby Connections (Bluetooth, BLE, Wi-Fi Direct). Un vigilante reinicia la busqueda cada 2 minutos mientras falte algun contacto, descarta conexiones muertas y reintenta. |
| Canal internet | Relevo MQTT publico (EMQX y HiveMQ a la vez; si uno cae, el otro sigue). Archivos partidos en bloques de 32 KB, confirmacion de entrega cifrada y reintentos durante 7 dias. |
| Servicio | Servicio en primer plano con la pantalla apagada: mantiene la busqueda, la conexion a internet y la cola. |

## Limites que conviene conocer

- **Servidor publico:** los relevos gratuitos de internet son compartidos y **sin garantia de servicio**. El contenido va cifrado de extremo a extremo, pero el servidor ve quien escribe a quien y cuando. Para algo serio, usa un servidor propio: cambia la lista en `RelayConfig` (archivo `OnlineTransport.kt`).
- **Tamano por internet:** archivos de hasta 25 MB. Los mas grandes quedan *Pendientes* y salen solos por conexion directa cuando el contacto este cerca.
- **Permisos:** Android no permite concederlos al instalar. ENVIO los pide todos juntos, una sola vez.
- **Bluetooth/Wi-Fi apagados:** Android no deja que una app los encienda en silencio. ENVIO muestra un aviso con boton "Activar". Si el telefono del contacto los tiene apagados, el mensaje queda *Pendiente*.
- **Bateria:** buscar contactos y mantener la conexion todo el tiempo gasta bateria; hay una notificacion permanente (la exige Android).
- **Fabricantes:** Xiaomi, Huawei, Samsung, etc. matan servicios en segundo plano. Hay que desactivar la optimizacion de bateria para ENVIO.
- **Solo Android:** entre Android y iPhone no hay canal directo comun.
- **Identidad:** si se desinstala la app, se pierde el ID, las claves y los contactos.

## Estructura

```
app/src/main/java/com/envio/app/
  Core.kt              cola de envio, reintentos, confirmaciones, recepcion
  net/NearbyTransport  canal directo
  net/OnlineTransport  canal internet (MQTT) y RelayConfig
  crypto/Crypto        cifrado
  data/                base de datos (Room) e identidad
  service/             servicio en primer plano
  ui/                  pantallas (Compose)
.github/workflows/     compila el APK en GitHub y lo publica en Releases
```
