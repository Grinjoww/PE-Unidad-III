import { Component } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <header>
      <h1>BIOPET</h1>
      <p>Entrega 1B — JWT + Spring Data JPA</p>
      <nav><a routerLink="/login">Login</a> · <a routerLink="/mascotas">Mascotas</a></nav>
    </header>
    <router-outlet />
  `
})
export class AppComponent {}
