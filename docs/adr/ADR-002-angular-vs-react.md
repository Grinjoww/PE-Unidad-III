# ADR-002: Elección de Angular frente a React para el frontend del PFC

## Estado

Aceptado.

## Contexto

El PFC requiere un frontend que consuma una API REST protegida por autenticación JWT en cookie `HttpOnly`, con rutas protegidas por rol (`ADMIN`, `VETERINARIO`, `AUXILIAR`, `DUENO`), formularios validados y peticiones HTTP centralizadas hacia el backend Spring Boot. El enunciado del proyecto exige explícitamente Angular 17+ como parte del stack (Java 21 + Spring Boot 3.x + Angular 17+ + PostgreSQL 16 + Redis), por lo que esta decisión no parte de cero: el propósito de este ADR es documentar por qué Angular es una elección técnicamente sólida para estos requisitos y no solo una imposición del enunciado, comparándolo objetivamente frente a React como alternativa de mercado más común.

## Decisión

Se usa **Angular 17+** como framework de frontend para todo el PFC.

## Criterios de comparación

- **Framework completo frente a biblioteca de interfaz.** Angular es un framework integral que incluye enrutamiento, formularios, cliente HTTP, inyección de dependencias y herramientas de compilación en una sola distribución oficial (Angular, s.f.). React, en cambio, es una biblioteca centrada exclusivamente en la capa de vista; enrutamiento (React Router), formularios y cliente HTTP se resuelven con librerías de terceros elegidas por cada equipo (React, s.f.).
- **TypeScript, organización modular e inyección de dependencias.** Angular está construido sobre TypeScript de forma nativa y obligatoria, con un sistema de inyección de dependencias jerárquico integrado en el framework. React admite TypeScript, pero es opcional y no viene con un contenedor de inyección de dependencias propio.
- **Router, formularios, HttpClient, interceptores y guards.** Angular ofrece `RouterModule`, `ReactiveFormsModule`, `HttpClient` con interceptores funcionales y `CanActivateFn` (guards) como parte del mismo ecosistema oficial, con contratos estables entre versiones. Este proyecto usa exactamente estas piezas: `auth.guard.ts` protege rutas según el estado de autenticación, y `jwt.interceptor.ts` añade `withCredentials: true` a cada petición HTTP para que la cookie `HttpOnly` del backend viaje y se reciba correctamente en cada llamada.
- **Curva de aprendizaje, mantenimiento, escalabilidad del código y trabajo en equipo.** Angular impone una estructura más rígida (módulos o standalone components, servicios inyectables, convenciones de nomenclatura), lo que exige una curva de aprendizaje inicial mayor pero reduce la variabilidad de decisiones arquitectónicas entre integrantes de un mismo equipo. React da más libertad, lo que agiliza el arranque pero traslada a cada equipo la responsabilidad de decidir enrutamiento, manejo de formularios y estructura de carpetas, generando mayor riesgo de inconsistencia en un equipo universitario con integrantes de distinto nivel de experiencia.
- **Compatibilidad con el PFC y alineación con la directriz del docente.** El enunciado exige Angular 17+ de forma explícita, lo que por sí solo ya resuelve la decisión a nivel de restricción del proyecto; los criterios anteriores muestran que, además de ser un requisito, Angular es una opción razonable en términos técnicos para el alcance de este PFC.

## Consecuencias

**Positivas:**

- Estructura uniforme entre los tres integrantes del equipo, al no tener que decidir router, gestor de formularios ni cliente HTTP por separado.
- Los guards e interceptores oficiales de Angular encajan naturalmente con el modelo de autenticación por cookie `HttpOnly` del backend, sin necesitar librerías adicionales para proteger rutas o adjuntar credenciales.
- Al ser TypeScript obligatorio, los DTOs del backend (`MascotaResponse`, `UsuarioResponse`, etc.) se pueden reflejar directamente como interfaces tipadas en el frontend (`mascota.model.ts`), reduciendo errores de forma en tiempo de compilación.

**Negativas:**

- Curva de aprendizaje inicial más pronunciada que React para integrantes sin experiencia previa en el framework.
- Mayor cantidad de conceptos propios del framework (decoradores, inyección jerárquica, ciclo de vida de componentes) que dominar antes de ser productivo.
- Menor flexibilidad para sustituir piezas del ecosistema (router, formularios) si en algún momento se necesitara una solución distinta a la oficial.

## Alternativas consideradas

- **React:** descartado porque, aunque tiene un ecosistema más grande y una curva de entrada más suave para componentes aislados, requiere ensamblar manualmente router, formularios y cliente HTTP de terceros, lo que introduce variabilidad de decisiones entre los integrantes del equipo y no está alineado con el requisito explícito del enunciado del PFC.
- **Vue.js:** no evaluado en profundidad por no formar parte del stack exigido por el enunciado del proyecto ni ser una alternativa discutida por el equipo.

## Referencias

Angular. (s.f.). *Angular documentation*. https://angular.dev

React. (s.f.). *React documentation*. Meta. https://react.dev
