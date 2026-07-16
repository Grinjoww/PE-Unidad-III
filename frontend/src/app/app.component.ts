import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, Router } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink],
  template: `
    <header>
      <h1>BIOPET</h1>
      <p>Entrega 1B — JWT + Spring Data JPA</p>
      <nav>
        <a routerLink="/login">Login</a> · <a routerLink="/mascotas">Mascotas</a>
        <button *ngIf="auth.isLogged()" (click)="logout()">Cerrar sesión</button>
      </nav>
    </header>
    <router-outlet />
  `
})
export class AppComponent implements OnInit {
  constructor(protected auth: AuthService, private router: Router) {}

  ngOnInit() {
    this.auth.me().subscribe();
  }

  logout() {
    this.auth.logout().subscribe(() => this.router.navigate(['/login']));
  }
}
