# Grupo6-PSP

## Requisitos

- JDK 25
- Maven 3.9 o posterior
- VS Code con las extensiones Extension Pack for Java y Maven for Java (opcional)

JavaFX se descarga y configura mediante Maven; no es necesario instalarlo por separado.

## Compilar y ejecutar

Abre dos terminales en la carpeta `demo`. Primero compila el proyecto:

```powershell
mvn clean compile
```

En la primera terminal, arranca el servidor:

```powershell
java -cp target/classes server.Servidor
```

En la segunda terminal, arranca el cliente JavaFX:

```powershell
mvn javafx:run
```

El servidor escucha en el puerto `5005`. Ejecuta el servidor antes de conectar
clientes; para iniciar otra instancia, usa otra terminal y el mismo comando.

En VS Code, abre la carpeta del repositorio y acepta la importación del proyecto Maven. Si los imports de JavaFX aparecen como no resueltos, ejecuta **Java: Clean Java Language Server Workspace** y vuelve a abrir el proyecto.

El descriptor modular de Java está en `demo/src/main/java/module-info.java`. No lo muevas dentro de un paquete: Maven necesita encontrarlo en la raíz de las fuentes Java.
