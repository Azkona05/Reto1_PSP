# Grupo6-PSP

## Requisitos

- JDK 25
- Maven 3.9 o posterior
- VS Code con las extensiones Extension Pack for Java y Maven for Java (opcional)

JavaFX se descarga y configura mediante Maven; no es necesario instalarlo por separado.

## Compilar y ejecutar

Desde la carpeta `demo`:

```powershell
mvn clean javafx:run
```

En VS Code, abre la carpeta del repositorio y acepta la importación del proyecto Maven. Si los imports de JavaFX aparecen como no resueltos, ejecuta **Java: Clean Java Language Server Workspace** y vuelve a abrir el proyecto.

El descriptor modular de Java está en `demo/src/main/java/module-info.java`. No lo muevas dentro de un paquete: Maven necesita encontrarlo en la raíz de las fuentes Java.
