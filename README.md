# PaseRevista

App Android nativa **100% offline** para el **pase de revista médico diario**
(recorrido diario de pacientes hospitalizados). Sin backend, sin nube, sin
Internet: Room/SQLite es la fuente de verdad en el dispositivo.

- **Paquete:** `com.reynelbusto.paserevista`
- **Stack:** Kotlin · Jetpack Compose (Material 3) · Room · Coroutines/Flow ·
  ViewModel · Navigation · AndroidX
- **minSdk 26**, target/compile SDK 36
- **Identidad visual:** azul `#0B5E7A` + turquesa `#00B4D8`, tipografía Inter

## Abrir en Android Studio

1. `File → Open` y seleccione la carpeta `paserevista/` (la que contiene
   `settings.gradle.kts`).
2. Android Studio descargará Gradle 8.14.5 vía el wrapper y sincronizará
   las dependencias (se necesita conexión la primera vez).
3. Ejecute `assembleDebug` o pulse Run ▶.

También desde terminal:

```bash
./gradlew assembleDebug        # APK debug
./gradlew testDebugUnitTest    # tests unitarios JVM
```

El APK debug queda en `app/build/outputs/apk/debug/`.

## Reglas clínicas fundamentales

1. **HERENCIA ≠ COPIA:** un día nuevo nunca es copia clínica del anterior.
   Los datos diarios nacen vacíos (NULL); AYER es solo referencia visual.
2. **Abrir ≠ revisar:** solo una acción explícita de evolución marca ✓.
3. **"Sin cambios"** = 1 tap → revisado (con Deshacer 5 s). Los 3 deltas
   exigen "✓ Marcar como revisado".
4. **P1 abierto bloquea el alta silenciosa.**
5. Pendientes, tratamientos, dispositivos y procedimientos mantienen su
   identidad entre jornadas (cero duplicados).

## Estructura

```
app/src/main/java/com/reynelbusto/paserevista/
├── core/            # Time (fechas ISO, día de tratamiento), Ids, Clock
├── data/
│   ├── local/       # Room: entidades, DAOs, Database, UnitOfWork
│   ├── repository/  # Implementaciones + mappers entidad↔dominio
│   └── debug/       # Seeds solo-debug (marcados [PRUEBA])
├── di/              # AppContainer (DI manual)
├── domain/
│   ├── model/       # Modelos + enums (vocabularios cerrados)
│   ├── repository/  # Contratos + ClinicalUnitOfWork
│   └── usecase/     # Reglas clínicas (motor de jornadas, revisión, …)
├── navigation/      # NavGraph + Routes
└── presentation/    # Compose: home, chart, board, pending, patients,
                     #          history, more, components, theme
```

## Estado

v1.0 — Pase de revista diario: Hoy, ficha del paciente (AYER/HOY),
pendientes, pizarra quirúrgica, pacientes, historial. Fuera de v1:
WhatsApp, widgets, nube, IA.
