# Async Movement

Mod de Fabric para Minecraft **1.16.5 – 26.3** que lee el estado físico de las
teclas, los botones del mouse y el cursor en un hilo propio y lo aplica a una
frecuencia fija configurable (300 Hz por defecto), sin tocar el renderizado. El
objetivo es desacoplar la **captura y aplicación del input** del framerate, no
reducir la latencia del render.

La cámara asíncrona está disponible en los módulos GLFW (1.16.5 – 26.2) de
Windows y Linux, y requiere desactivar **Raw Mouse Input**. En macOS el cursor
se disocia en modo deshabilitado y `glfwGetCursorPos` no refleja el movimiento,
así que la cámara queda en vanilla. En 26.3 el backend SDL tampoco ofrece una
consulta de cursor seguro entre hilos; la cámara de 26.3 también queda en
vanilla. Si Raw Input está activo, el mod deja la cámara en vanilla y avisa en
el log.

El proyecto es un hub de Gradle sin código en la raíz. Cada versión de Minecraft
tiene su propio submódulo en `src/mc...`, y la lógica independiente de Minecraft
está en `src/common`.

## Estructura

```
.
├── build.gradle                     # hub raíz, sin código de mod
├── settings.gradle                  # incluye common + un módulo por versión
├── gradle/version-module.gradle     # configuración común de los módulos MC
├── gradle.properties
└── src
    ├── common                       # Java 8 puro: config, poller, core
    ├── version-shared               # glue común de Minecraft
    ├── version-shared-keys-long     # fuente GLFW con handle long (<= 1.21.8)
    ├── version-shared-keys-window   # fuente GLFW con Window (1.21.10 - 26.2)
    ├── version-shared-keys-sdl      # fuente SDL (26.3)
    ├── version-shared-camera-legacy # mixin de cámara para MouseHandler antiguo
    ├── version-shared-camera-modern # mixin de cámara para MouseHandler nuevo
    ├── version-shared-keybind-binding # KeyBindingHelper (<= 1.21.11)
    ├── version-shared-keybind-mapping # KeyMappingHelper (26.x)
    ├── mc1_16_5
    ├── mc1_17_1
    ├── mc1_18_2
    ├── mc1_19_2
    ├── mc1_19_4
    ├── mc1_20_1
    ├── mc1_20_4
    ├── mc1_20_6
    ├── mc1_21_1
    ├── mc1_21_4
    ├── mc1_21_5
    ├── mc1_21_8
    ├── mc1_21_10
    ├── mc1_21_11
    ├── mc26_1
    ├── mc26_2
    └── mc26_3
```

Cada módulo `mc...` contiene únicamente su `build.gradle` y su adaptador
`AsyncMovement`, que aporta las tres o cuatro diferencias de API de esa línea:
constructor del keybind, categoría, fuente de input, comprobación de pantalla y
registro del keybind.

## Compilar

Requisitos:

- JDK 25 para compilar los módulos 26.x.
- Gradle Wrapper 9.8.2 (incluido). Loom 1.19.2 requiere Gradle >= 9.8.
- Acceso a Maven Central, Fabric Maven y Mojang.

```bash
JAVA_HOME=/ruta/al/jdk-25 ./gradlew buildAll
```

Los jars finales quedan en `src/<módulo>/build/libs/`. Para copiarlos todos a
`build/jars/`:

```bash
JAVA_HOME=/ruta/al/jdk-25 ./gradlew collectJars
```

También se puede compilar un solo módulo:

```bash
JAVA_HOME=/ruta/al/jdk-25 ./gradlew :mc1_20_1:build
```

> El build usa el JDK indicado en `JAVA_HOME`. Loom 1.19.2 necesita Java 21 o
> superior, y los módulos 26.x necesitan JDK 25. No hace falta instalar JDK 8,
> 16 o 17 por separado: `options.release` de cada módulo genera el bytecode
> correcto con el JDK 25.

## Uso

El mod se activa por defecto. La tecla **F6** lo activa/desactiva y el estado se
guarda en el archivo de configuración.

> Instala solo el jar de tu versión. Todos los jars usan el id `asyncmovement`
> y Fabric rechazará duplicados. Tampoco instales este jar junto a
> `am-1.1.jar`; el jar de ejemplo se conserva solo como referencia.

Archivo: `config/asyncmovement.properties`

```properties
enabled=true
poll-rate-hz=300
only-when-focused=true
mouse-buttons=true
camera=true
log-stats=false
```

| Clave | Significado |
| --- | --- |
| `enabled` | Estado inicial. También se puede cambiar con F6. |
| `poll-rate-hz` | Frecuencia de sondeo, limitada a 1–1000 Hz. |
| `only-when-focused` | No sondea si la ventana no tiene foco. |
| `mouse-buttons` | Incluye los botones del mouse en el poller. |
| `camera` | Captura y aplica la cámara a la frecuencia del poller. Requiere Raw Mouse Input desactivado; solo Windows/Linux. |
| `log-stats` | Escribe la frecuencia medida cada 5 s en el log. |

> Para usar la cámara asíncrona: **Opciones → Controles → Ratón → Raw Mouse
> Input: OFF**. El mod no cambia esa opción por ti. Si está activa, la cámara
> sigue funcionando en vanilla, sin doble aplicación ni congelación.

La configuración se lee al iniciar el cliente. Si se edita el archivo con el
juego abierto, hay que reiniciar para aplicar los cambios.

## Cómo funciona

1. `AsyncPoller` crea un hilo daemon con prioridad algo superior a la normal y
   usa deadlines absolutos (`System.nanoTime`) para mantener la frecuencia media.
2. En cada tick del poller, `AsyncMovementCore` lee las teclas físicas y los
   botones del mouse, y los compara con el estado anterior. Solo aplica los
   cambios.
3. Si `camera=true` y Raw Input está desactivado, el poller lee la posición del
   cursor (`glfwGetCursorPos`), calcula el delta y lo entrega a
   `MinecraftCameraSink`. El mixin de `MouseHandler` desactiva el manejo vanilla
   mientras el poller es dueño de la cámara y llama a la misma lógica de vanilla
   (`turnPlayer`) desde el hilo asíncrono. Así se conservan sensibilidad,
   inversión y smooth camera. No se sintetizan eventos de mouse.
4. `MinecraftKeySink` usa la misma API estática que vanilla (`KeyMapping.set`)
   para actualizar los mappings. No se llama a `click()`: vanilla ya genera los
   eventos de click al recibir el input; sintetizarlos duplicaría acciones. Los
   botones del mouse actualizan el estado mantenido, pero los clicks de
   ataque/uso siguen viniendo de `MouseHandler`.
5. No se toca ningún código de renderizado. El hilo solo lee input, escribe
   booleanos de key mappings y actualiza la rotación del jugador; la imagen se
   sigue dibujando al ritmo del render.
6. Si se abre una pantalla, se pierde el foco, el jugador sale del mundo o el mod
   se desactiva, se llama a `KeyMapping.releaseAll()` y se devuelve la cámara a
   vanilla para no dejar teclas atascadas ni suprimir input de GUI. Al volver al
   juego se reaplica el estado físico.

### Por qué no se copia el ejemplo `am-1.1.jar`

El ejemplo usa `Timer.scheduleAtFixedRate` y llama a `MouseHandler.unlockCursor()`
(o `Mouse.unlockCursor()` en Yarn) en cada sondeo. Eso no es necesario para leer
teclas y puede liberar el cursor: se ha omitido deliberadamente. El ejemplo
también sintetiza eventos de click; aquí tampoco se hace.

## Matriz de versiones

| Módulo | Minecraft | Java | Fabric API | Backend de input |
| --- | --- | --- | --- | --- |
| `mc1_16_5` | 1.16.5 | 8 | 0.42.0+1.16 | GLFW (`long`) |
| `mc1_17_1` | 1.17.1 | 16 | 0.46.1+1.17 | GLFW (`long`) |
| `mc1_18_2` | 1.18.2 | 17 | 0.77.0+1.18.2 | GLFW (`long`) |
| `mc1_19_2` | 1.19.2 | 17 | 0.77.0+1.19.2 | GLFW (`long`) |
| `mc1_19_4` | 1.19.4 | 17 | 0.87.2+1.19.4 | GLFW (`long`) |
| `mc1_20_1` | 1.20.1 | 17 | 0.92.12+1.20.1 | GLFW (`long`) |
| `mc1_20_4` | 1.20.4 | 17 | 0.97.3+1.20.4 | GLFW (`long`) |
| `mc1_20_6` | 1.20.6 | 21 | 0.100.8+1.20.6 | GLFW (`long`) |
| `mc1_21_1` | 1.21.1 | 21 | 0.116.17+1.21.1 | GLFW (`long`) |
| `mc1_21_4` | 1.21.4 | 21 | 0.119.4+1.21.4 | GLFW (`long`) |
| `mc1_21_5` | 1.21.5 | 21 | 0.128.2+1.21.5 | GLFW (`long`) |
| `mc1_21_8` | 1.21.8 | 21 | 0.136.1+1.21.8 | GLFW (`long`) |
| `mc1_21_10` | 1.21.10 | 21 | 0.138.4+1.21.10 | GLFW (`Window`) |
| `mc1_21_11` | 1.21.11 | 21 | 0.141.6+1.21.11 | GLFW (`Window`) |
| `mc26_1` | 26.1 | 25 | 0.145.1+26.1 | GLFW (`Window`) |
| `mc26_2` | 26.2 | 25 | 0.161.0+26.2 | GLFW (`Window`) |
| `mc26_3` | 26.3 | 25 | 0.163.0+26.3 | SDL (`scancode`) |

La cámara asíncrona está incluida en los módulos GLFW (1.16.5 – 26.2) de
Windows y Linux. En macOS queda en vanilla porque el cursor disociado de GLFW no
expone el movimiento mediante `glfwGetCursorPos`. En `mc26_3` el poller sigue
cubriendo teclado y botones, pero la cámara también queda en vanilla porque SDL
no expone una posición de cursor consultable de forma segura desde otro hilo.

Cada jar declara un rango `minecraft` cerrado a la versión exacta contra la que
se compiló (por ejemplo `>=1.20.1 <1.20.2`). No se declara compatibilidad con
parches que no se hayan probado. La matriz cubre las líneas principales de
1.16.5 a 26.3; si un parche intermedio comparte la misma API de input, su rango
se puede ampliar después de probarlo.

## Limitaciones y decisiones técnicas

- **GLFW y el hilo secundario.** La documentación de GLFW dice que
  `glfwGetKey`, `glfwGetMouseButton`, `glfwGetInputMode` y `glfwGetCursorPos`
  deben llamarse desde el hilo principal. Este mod captura el handle de la
  ventana en el hilo del cliente y consulta esas funciones desde el poller,
  porque el requisito principal es que el sondeo sea asíncrono. Es la misma
  operación que hacía el ejemplo. Según la plataforma, GLFW puede devolver el
  estado consultado a la ventana o una instantánea actualizada por el bombeo de
  eventos; el mod no fuerza eventos de ventana.
- **SDL en 26.3.** El backend cambia a SDL. `InputConstants.isKeyDown(int)` usa
  `SDL_GetKeyboardState`, que está documentado como seguro entre hilos. Los
  botones se leen con `SDL_GetMouseState`, que SDL documenta como
  main-thread-only; se consulta desde el poller por el mismo compromiso de
  asincronía que en GLFW. Aun así, SDL actualiza esas instantáneas cuando el
  hilo principal procesa eventos (`SDL_PumpEvents`); el
  poller no puede forzar al sistema a refrescarlas. El mod sigue aplicando el
  estado a 300 Hz, pero la latencia final depende del event pump de SDL. No se
  llama a `SDL_PumpEvents` desde el hilo asíncrono porque no es seguro hacerlo
  junto al bucle de video.
- **Escritura de booleanos desde el hilo asíncrono.** `KeyMapping.set` modifica
  los booleanos `isDown` de los mappings. Es una carrera de datos benigna: no se
  asigna memoria nueva ni se toca el render. Si se quisiera cumplir
  estrictamente el modelo de un solo hilo, habría que encolar los cambios y
  aplicarlos en el client tick, lo que añadiría un frame de latencia.
- **Toggle mappings.** `ToggleKeyMapping` cambia de estado con cada
  `set(true)`, por lo que reaplicar el estado físico lo apagaría. Se filtran los
  mappings vanilla de usar, sprint y atacar tanto para teclado como para mouse;
  un toggle añadido por otro mod podría no filtrarse.
- **Alcance del input asíncrono.** El poller cubre **teclado, botones del mouse
  y, en los módulos GLFW de Windows/Linux, la cámara**. En GLFW se consultan los
  botones 0–7; en SDL/26.3 se consulta `SDL_GetMouseState`, cuyos códigos son
  1–8. Los botones actualizan el estado mantenido de los mappings, pero no se
  sintetizan clicks: `MouseHandler` sigue generando los clicks de ataque/uso. Si
  un mapping de ataque/uso/sprint es `ToggleKeyMapping` (modo toggle activado),
  se filtra para no alternarlo dos veces; en ese caso ese botón vuelve a
  depender del hilo principal.
- **Cámara asíncrona y Raw Input.** El poller captura la posición del cursor,
  no el delta relativo que entrega el sistema con Raw Input. Por eso la cámara
  asíncrona solo se activa con **Raw Mouse Input desactivado**. Si está activo,
  el cursor no refleja el movimiento relativo y la cámara podría congelarse; en
  ese caso el mod no suprime el manejo vanilla: la cámara sigue funcionando
  como siempre y se escribe un aviso en el log. El mod no cambia la opción por
  ti.
- **Cámara: qué se desacopla y qué no.** Se desacoplan la captura (consulta del
  cursor a 300 Hz) y la aplicación de la rotación (llamada a `turnPlayer` desde
  el poller). El render sigue mostrando la rotación en el siguiente frame; el
  mod no dibuja ni fuerza frames. La rotación se actualiza aunque el render vaya
  lento, que es el objetivo. `turnPlayer` se ejecuta desde el hilo asíncrono y
  conserva la sensibilidad, la inversión y el smooth camera de vanilla; es una
  escritura de estado del jugador desde otro hilo, con el mismo compromiso
  práctico que `KeyMapping.set`, no una operación de render.
- **Transiciones y recentrado de cámara.** Mientras la cámara asíncrona está
  activa, el mixin ignora `MouseHandler.onMove` para no aplicar el delta dos
  veces, pero mantiene actualizado el cursor de referencia de vanilla. GLFW
  recentra el cursor oculto al centro al final de `glfwPollEvents`; el mixin
  toma una muestra adicional al inicio de `onMove`, antes de ese recentrado, y
  el poller la consume. El salto sintético al centro se detecta y se descarta.
  Si se abre una pantalla, se pierde el foco, el jugador sale del mundo o Raw
  Input se activa, el poller devuelve el control a vanilla en el siguiente
  sondeo. El primer delta grande tras un cambio de foco se descarta para evitar
  saltos.
- **Límite del cursor con Raw Input off.** Al no usar movimiento relativo, el
  cursor puede acercarse al borde de la ventana si un frame dura mucho y el
  movimiento es muy rápido; GLFW lo recentra al centro en cada `glfwPollEvents`,
  pero un frame anormalmente largo puede perder movimiento. Para uso normal con
  Raw Input off el recentrado y la muestra previa cubren el movimiento; para
  movimiento relativo sin bordes haría falta un backend raw nativo, que este mod
  no implementa.
- **Cámara en macOS.** GLFW disocia el cursor del sistema en modo deshabilitado
  (`CGAssociateMouseAndMouseCursorPosition(false)`), y `glfwGetCursorPos` sigue
  devolviendo la posición del cursor real, que no avanza con el movimiento
  relativo. Para no congelar la cámara, `supportsCameraCapture()` devuelve
  `false` en macOS y el mod deja el manejo vanilla intacto. Teclado y botones
  siguen siendo asíncronos.
- **Cámara en 26.3.** El backend SDL usa modo relativo para la cámara y sus
  consultas de mouse (`SDL_GetMouseState`, estado relativo) están documentadas
  como main-thread-only. No hay una posición de cursor que el poller pueda leer
  para reconstruir el delta de forma segura, así que `mc26_3` mantiene la cámara
  vanilla. Teclado y botones sí siguen asincrónicos.
- **Smooth camera.** La cámara asíncrona llama a la misma función de vanilla,
  pero lo hace a la frecuencia del poller. Con `Smooth Camera` activado, el
  suavizado se calcula con deltas de tiempo más pequeños que un frame; el
  resultado puede sentirse algo distinto al de vanilla. Si se busca el
  comportamiento exacto, se puede desactivar `camera` o `Smooth Camera`.
- **No se acelera el hardware.** No se modifican los report rates de USB, GLFW
  ni el renderizado. Solo se lee input con más frecuencia.
- **No se actualiza la pantalla.** El poller no llama a `Minecraft` para
  renderizar, ni fuerza frames, ni desbloquea el cursor.
- **Configuración estática.** Se lee al inicio. El keybind F6 sí se puede usar en
  caliente.

## Estado del proyecto

Los 17 módulos compilan con Loom 1.19.2 (`buildAll` verificado). El módulo
`common` produce bytecode Java 8; los módulos de versión usan el `--release`
que corresponde a su línea de Minecraft (8, 16, 17, 21 o 25). `collectJars`
copia los 17 jars finales a `build/jars/`. El smoke test del core cubre teclado,
botones del mouse, release al desactivar, la frecuencia de 300 Hz, el baseline
de cámara, los deltas asíncronos, la muestra previa al recentrado, el descarte
del salto al centro y la caída a vanilla cuando Raw Input está activo.

El jar de ejemplo original se conserva en `am-1.1.jar` como referencia.
