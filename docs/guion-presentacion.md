# Guion — Recorrido de una petición (ProyecTwin)

> Idea para decir en una frase:
> **“La pantalla pide → la petición viaja por internet con un token → el backend verifica y consulta la base de datos → y la respuesta vuelve como JSON para pintar la pantalla.”**

Ejemplo que usamos: el aprendiz abre **“Mis Propuestas”** → `GET /v1/projects`.

## Paso a paso (qué decir)

1. **Usuario / Pantalla (Mockup)**
   “El aprendiz entra a la pantalla **Mis Propuestas** y hace clic. Es lo que ve el usuario.”

2. **Componente React (.jsx)**
   “React dibuja la interfaz. Pero **no tiene los datos**: cuando los necesita, los pide.”

3. **Capa de datos (useApi + recursos)**
   “Un *hook* pide la lista de propuestas y maneja el estado: **cargando**, **error** o **datos**.”

4. **Cliente HTTP (api.js)**
   “Aquí se **arma la petición**: el método (`GET`), la dirección (`/v1/projects`) y el **token** que identifica al usuario. `fetch` la envía por internet.”

5. **Proxy (Vite)**
   “En desarrollo, todo sale por el **mismo origen**; el proxy manda `/v1` al servidor backend.”

6. **Ruta + Middleware (Laravel)**
   “El backend la recibe en una **ruta**. Antes de atenderla, **verifica permisos**: token válido, cuenta activa y rol autorizado.”

7. **Controlador**
   “El **controlador** valida los datos, aplica las reglas (por ejemplo, mostrar solo lo que te corresponde) y pide los datos.”

8. **Modelo Eloquent**
   “El **modelo** traduce esa petición a una **consulta SQL**.”

9. **Base de datos (MySQL)**
   “La **base de datos** ejecuta el `SELECT` y devuelve las filas.”

10. **Respuesta (JSON)**
    “El backend arma un **JSON** (texto) con las propuestas y lo devuelve.”

11. **Vuelve a la pantalla**
    “El cliente recibe el JSON y **React vuelve a dibujar** la pantalla con la lista.”

## Si algo falla (nota breve)
- **401** → la sesión no es válida (hay que iniciar sesión).
- **403** → no tienes permiso para esa acción.
- **422** → los datos enviados no son válidos.
> “En esos casos, el backend devuelve un error y la pantalla lo muestra.”

## Cierre (para la ficha)
“Ese es **todo el recorrido**: de la pantalla a la base de datos y de vuelta. El frontend solo **muestra y pide**; el backend **valida, autoriza y consulta**; y la base de datos **guarda** la información.”
