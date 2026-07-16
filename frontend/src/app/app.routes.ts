import { Routes } from '@angular/router';
import { LoginComponent } from './features/login.component';
import { MascotasComponent } from './features/mascotas.component';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'mascotas', component: MascotasComponent, canActivate: [authGuard] }
];
