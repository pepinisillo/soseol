# soseol

App de Android para escribir relatos, historias, fanfics cortos cada dia. Se utiliza una IA local, API o una lista en el caso de palabras: para ideas de escritura, pero tambien puedes escribir libremente una idea propia. Se escribe un minimo y maximo de palabras, esto para tener una meta alcanzable. Puedes bloquear aplicaciones distractoras en caso de aun no haber escrito nada dada cierta hora. 

![Pantallas](capturas/portada.png)

## Qué hace

- Reto del día al azar: palabra, frase, tema, etc.
- Contador de palabras con anillo, mínimo y máximo.
- Historias con portada, historial y racha.
- Bloquea las apps que elijas a partir de cierta hora si no has escrito.
- Cuenta cuánto tardaste en cada historia (solo mientras escribes).

## Retos

Cada día sale uno al azar entre los que tengas activados. En Ajustes → Tu musa puedes apagar los que no quieras y elegir de dónde sale cada uno: la **lista** hecha a mano o la **musa** (la IA). Nunca se repite algo que ya usaste en una historia.

| Reto | Qué te da | Sale de |
|---|---|---|
| Palabra | Una palabra de otro idioma o poco común, con su significado | Lista |
| Palabra japonesa | Una palabra en japonés y qué significa | Lista |
| Frase | Una frase corta; si es de otro idioma, con su traducción | Lista |
| Tema | Un tropo, universo alterno o situación | Musa |
| Idea | Una premisa en una sola frase | Musa |
| Idea en 10 palabras | Una premisa en exactamente 10 palabras | Musa |
| Idea en 5 palabras | Una premisa en exactamente 5 palabras | Musa |
| Tres palabras raras | Tres palabras poco comunes del español que debes usar | Lista |
| Tres palabras al azar | Tres palabras cualquiera que debes usar | Musa |

Las palabras salen por defecto de la lista porque los modelos del teléfono a veces se equivocan al darlas de memoria.

## IA

- **Gemma 4 E4B** (3.7 GB): rápida.
- **Gemma 4 12B** (6.9 GB): mejor calidad, más lenta.
- Se descargan desde la app (Ajustes → Musa con IA) y funcionan sin internet.
- Opcional: usar Claude con tu API key.

## Cómo correrla

1. Abre esta carpeta en Android Studio.
2. Conecta tu teléfono con depuración USB activada.
3. Dale a **Run**.

O desde la terminal:

```bash
./gradlew installDebug
```

## Bloqueo de apps distractoras

A partir de la hora que elijas, si todavía no escribes tu historia del día, al abrir una de las apps que marcaste (Instagram, TikTok, X…) se abre soseol encima. Cuando llegas al mínimo de palabras, quedan libres el resto de ese día. Al día siguiente, desde la misma hora, se vuelven a bloquear hasta que escribas.

Para activarlo: Ajustes de Android → Accesibilidad → Apps instaladas → soseol → Activar. El permiso solo se usa para saber qué app está al frente; la app no lee lo que hay en la pantalla.

En Ajustes → Bloqueo de apps verás si está **Funcionando** o **Desconectado**. El **?** de esa sección lo explica.

### En Xiaomi, Redmi y POCO (MIUI / HyperOS)

Estos teléfonos cierran a la fuerza las apps al limpiar recientes, y con eso se desconecta el bloqueo aunque siga activado en Accesibilidad. Para evitarlo:

1. **Candado en recientes:** abre recientes, deja presionada la tarjeta de soseol y toca el candado.
2. **Batería:** Ajustes → Apps → Administrar apps → soseol → Ahorro de batería → **Sin restricciones**.
3. **Inicio automático:** Seguridad → Apps → Permisos → Inicio automático → activa soseol.
4. Si en soseol dice **Desconectado**: Accesibilidad → soseol, apágalo y vuelve a prenderlo.

Si las opciones 2 o 3 no aparecen para soseol (pasa con apps instaladas fuera de la tienda), se pueden activar desde la computadora con el teléfono conectado por USB:

```bash
adb shell dumpsys deviceidle whitelist +com.soseol.app          # sin límite de batería
adb shell cmd appops set com.soseol.app RUN_ANY_IN_BACKGROUND allow
adb shell cmd appops set com.soseol.app 10008 allow             # inicio automático de Xiaomi
```
