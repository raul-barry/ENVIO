# ENVIO

App Android para enviar mensajes, imagenes, audios y archivos de cualquier tipo **con o sin internet**, sin numero de telefono ni SIM.

Kotlin + Jetpack Compose. Android 8.0+ (API 26).

## Como se abre y se prueba

1. Abre la carpeta `ENVIO` con **Android Studio** (Koala o mas nuevo). Descargara Gradle y las librerias solo.
2. Conecta un telefono Android (o dos) con depuracion USB y pulsa Run.
3. **Prueba offline (no necesita configurar nada):** instala en dos telefonos, abre ENVIO, acepta los permisos, en uno abre `+` y muestra el QR, en el otro escanealo. Apaga datos y Wi-Fi de internet (deja Bluetooth y Wi-Fi encendidos) y envia mensajes: llegan directo.
   - El emulador **no** sirve para probar lo directo: se necesitan telefonos reales.

## Activar el modo internet (opcional, Firebase)

Sin esto la app funciona completa en modo directo. Para agregar el canal internet:

1. Crea un proyecto en https://console.firebase.google.com
2. Agrega una app Android con el paquete `com.envio.app` y descarga `google-services.json` a la carpeta `app/`.
3. En Firebase activa: **Authentication > Anonymous**, **Firestore Database** y **Storage**.
4. Pega el contenido de `firebase/firestore.rules` en Firestore > Reglas, y `firebase/storage.rules` en Storage > Reglas.
5. Vuelve a compilar. La app detecta el archivo y activa el canal internet.

## Como funciona

| Pieza | Que hace |
|---|---|
| Identidad | Al instalar se genera un ID de 10 letras y un par de claves (ECDH P-256). Los contactos se agregan escaneando un QR. |
| Cifrado | Extremo a extremo (clave compartida AES-256, AES-GCM). Archivos cifrados por bloques de 64 KB. El servidor nunca ve el contenido. |
| Cola de envio | Todo mensaje se guarda como *Pendiente* y sale por el mejor canal: directo si el contacto esta cerca, internet si hay, o espera hasta que aparezca uno. El usuario siempre toca "Enviar" igual. |
| Canal directo | Google Nearby Connections: elige solo entre Bluetooth, BLE y Wi-Fi Direct. Se conecta automaticamente a contactos conocidos sin preguntar. |
| Canal internet | Firebase (Firestore para mensajes, Storage para archivos). |
| Servicio | Servicio en primer plano que mantiene la busqueda de contactos cercanos con la app cerrada y reintenta la cola. |

## Limites de Android que conviene conocer

- **Permisos:** Android no permite concederlos al instalar. ENVIO los pide todos juntos, una sola vez, en la pantalla de bienvenida.
- **Encender Bluetooth/Wi-Fi:** Android no deja que una app los encienda en silencio. ENVIO detecta si estan apagados y muestra un aviso con boton "Activar" (un toque). Si el telefono del contacto los tiene apagados, el mensaje queda *Pendiente* y sale solo cuando ese telefono los encienda y este cerca.
- **Alcance:** unos 10 a 100 metros segun el metodo que se use.
- **Bateria:** buscar contactos cercanos todo el tiempo consume bateria. El servicio muestra una notificacion permanente (lo exige Android).
- **Fabricantes:** Xiaomi, Huawei, Samsung, etc. matan servicios en segundo plano. Hay que desactivar la optimizacion de bateria para ENVIO.
- **Solo Android:** entre Android y iPhone no hay canal directo comun.

## Estructura

```
app/src/main/java/com/envio/app/
  Core.kt              cola de envio, recepcion, descifrado
  net/NearbyTransport  canal directo
  net/OnlineTransport  canal internet (Firebase)
  crypto/Crypto        cifrado
  data/                base de datos (Room) e identidad
  service/             servicio en primer plano
  ui/                  pantallas (Compose)
firebase/              reglas de seguridad
```

## Ideas para seguir

- Reenvio tipo malla (los telefonos intermedios llevan mensajes cifrados a contactos lejanos).
- Grupos, confirmacion de lectura, miniaturas de imagen.
- Respaldo de la identidad (si se desinstala la app, se pierde el ID y las claves).

## Obtener el APK sin instalar Android Studio

**Opcion A, con GitHub (gratis, solo navegador):**
1. Crea una cuenta en https://github.com y un repositorio nuevo (privado esta bien).
2. Sube todo el contenido de la carpeta `ENVIO` (incluida la carpeta oculta `.github`).
3. Ve a la pestana **Actions** > **Compilar APK** > **Run workflow**.
4. Cuando termine (unos 5 a 10 minutos), entra a la ejecucion y descarga **ENVIO-apk** (contiene `app-debug.apk`).
5. Pasa el APK al telefono e instalalo (permite "instalar apps desconocidas").

**Opcion B, con Android Studio:** menu Build > Build Bundle(s) / APK(s) > Build APK(s).
