# Grupo6-PSP

Aplicación cliente-servidor desarrollada en Java, con interfaz gráfica JavaFX y comunicación mediante sockets.

## Requisitos previos

Antes de ejecutar el proyecto, asegúrate de tener instaladas las siguientes herramientas:

- **JDK 25** o superior.
- **Apache Maven 3.9** o superior.
- **Visual Studio Code** con las extensiones:
  - Extension Pack for Java.
  - Maven for Java.

  *La instalación de las extensiones es opcional, pero recomendable.*

> **Nota:** JavaFX se descarga y configura automáticamente mediante Maven, por lo que no es necesario instalarlo por separado.

---

## Compilación y ejecución

Para ejecutar la aplicación, abre dos terminales en la carpeta `demo` del proyecto.

### 1. Compilar el proyecto

En primer lugar, ejecuta el siguiente comando para limpiar compilaciones anteriores y compilar el código fuente:

```powershell
mvn clean compile
```

### 2. Iniciar el servidor

En la primera terminal, ejecuta:

```powershell
java -cp target/classes com.example.sever.Servidor
```

El servidor quedará a la escucha de conexiones en el puerto `5005`.

### 3. Iniciar el cliente

En la segunda terminal, ejecuta:

```powershell
mvn javafx:run
```

Se iniciará la interfaz gráfica JavaFX, desde la que podrás conectarte al servidor.

> **Importante:** El servidor debe estar iniciado antes de ejecutar el cliente. Puedes iniciar varias instancias del cliente desde diferentes terminales para conectar distintos usuarios.

---

## Estructura del proyecto

El código fuente Java y los recursos de la aplicación están organizados por
responsabilidad bajo el paquete raíz `com.example`:

```text
demo/
└── src/main/
    ├── java/
    │   ├── module-info.java
    │   └── com/example/
    │       ├── client/
    │       │   ├── ChatApplication.java
    │       │   ├── controller/
    │       │   └── comunicacion/   # Cliente, lector y escritor de mensajes
    │       ├── common/              # Mensajes compartidos cliente-servidor
    │       └── sever/               # Servidor y gestor de conexiones
    └── resources/com/example/client/
        └── ventana-principal.fxml
```

## Configuración en Visual Studio Code

1. Abre la carpeta del repositorio en Visual Studio Code.
2. Acepta la importación del proyecto Maven cuando aparezca la notificación.
3. Espera a que Maven descargue las dependencias y configure el proyecto.

Si los imports de JavaFX aparecen como no resueltos, ejecuta el comando:

`Java: Clean Java Language Server Workspace`

Después, vuelve a abrir el proyecto.

### Descriptor modular

El descriptor modular de Java (`module-info.java`) se encuentra en:

```text
demo/
└── src/
    └── main/
        └── java/
            └── module-info.java
```

**No muevas este archivo dentro de un paquete.** Maven necesita que permanezca directamente en la raíz de las fuentes Java.

---

## Funcionamiento de la aplicación

La aplicación sigue una arquitectura cliente-servidor. El servidor gestiona las conexiones y la comunicación entre los diferentes clientes, mientras que cada cliente dispone de una interfaz gráfica para interactuar con los demás usuarios.

### 1. Inicio de la conexión

Primero debes iniciar el servidor y, posteriormente, ejecutar el cliente.

Al abrir la aplicación, encontrarás los siguientes campos:

| Campo | Descripción |
|---|---|
| **IP** | Dirección IP del equipo donde se encuentra el servidor. Utiliza `127.0.0.1` si servidor y cliente se ejecutan en el mismo ordenador. Si están en equipos diferentes, introduce la IP del equipo servidor. |
| **Puerto** | Puerto utilizado por el servidor. El valor predeterminado es `5005`. |
| **Usuario** | Nombre de usuario con el que deseas conectarte. Debe ser diferente al de los usuarios que ya han iniciado sesión. |
| **Conectar** | Establece la conexión con el servidor utilizando los datos introducidos. |

### 2. Envío de mensajes

Una vez establecida la conexión, podrás enviar mensajes a través de la interfaz.

Para ello, dispones de los siguientes elementos:

| Elemento | Descripción |
|---|---|
| **Privado** | Permite seleccionar la visibilidad del mensaje, indicando si será público o privado. |
| **Para** | Permite indicar el nombre del usuario destinatario del mensaje privado. |
| **Campo de mensaje** | Cuadro de texto donde debes escribir el contenido del mensaje. |
| **Enviar** | Envía el mensaje al servidor para que sea distribuido según la visibilidad y el destinatario seleccionados. |

**Consideraciones:**
- Los mensajes públicos están destinados a los usuarios conectados.
- Los mensajes privados están dirigidos al usuario especificado en el campo **Para**.
- Para enviar un mensaje privado, asegúrate de introducir correctamente el nombre del destinatario.
