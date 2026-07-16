import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';

@Component({
  standalone: true,
  imports: [CommonModule],
  template: `
  <div class="container">
    <h2>Mascotas registradas</h2>
    <button (click)="cargar()">Actualizar</button>
    <div class="card" *ngFor="let m of mascotas">
      <strong>{{m.nombre}}</strong><br>
      {{m.especie}} · {{m.raza}} · Dueño: {{m.duenioNombre}}
    </div>
  </div>`
})
export class MascotasComponent implements OnInit {
  mascotas: any[] = [];
  constructor(private http: HttpClient) {}
  ngOnInit() { this.cargar(); }
  cargar() {
    this.http.get<any>('/api/mascotas?page=0&size=10&sort=id,asc')
      .subscribe(res => this.mascotas = res.content ?? []);
  }
}
