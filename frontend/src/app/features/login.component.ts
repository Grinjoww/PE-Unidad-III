import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  standalone: true,
  imports: [FormsModule],
  template: `
  <div class="container">
    <h2>Iniciar sesión</h2>
    <input [(ngModel)]="email" placeholder="Email" />
    <input [(ngModel)]="password" placeholder="Contraseña" type="password" />
    <button (click)="login()">Entrar</button>
    <p class="error">{{error}}</p>
  </div>`
})
export class LoginComponent {
  email = 'jaime@biopet.com';
  password = 'ClaveSegura123*';
  error = '';
  constructor(private auth: AuthService, private router: Router) {}
  login() {
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/mascotas']),
      error: () => this.error = 'Credenciales inválidas o usuario no registrado.'
    });
  }
}
