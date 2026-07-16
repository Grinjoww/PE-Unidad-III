import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Mascota, MascotaRequest } from '../core/mascota.model';
import { MascotaService } from '../core/mascota.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
  <div class="container">
    <h2>Mascotas</h2>

    <div class="toolbar">
      <input [(ngModel)]="filtroNombre" placeholder="Filtrar por nombre" (keyup.enter)="buscar()" />
      <input [(ngModel)]="filtroEspecie" placeholder="Filtrar por especie" (keyup.enter)="buscar()" />
      <input [(ngModel)]="filtroRaza" placeholder="Filtrar por raza" (keyup.enter)="buscar()" />
      <button (click)="buscar()">Buscar</button>
      <button (click)="limpiarFiltros()">Limpiar</button>
    </div>

    <div class="toolbar">
      <label>Ordenar por
        <select [ngModel]="sort" (ngModelChange)="cambiarOrden($event)">
          <option value="id,asc">ID</option>
          <option value="nombre,asc">Nombre (A-Z)</option>
          <option value="nombre,desc">Nombre (Z-A)</option>
          <option value="especie,asc">Especie</option>
          <option value="raza,asc">Raza</option>
          <option value="fechaNacimiento,desc">Más jóvenes primero</option>
          <option value="fechaNacimiento,asc">Más viejos primero</option>
        </select>
      </label>
      <label>Tamaño de página
        <select [ngModel]="size" (ngModelChange)="cambiarTamano($event)">
          <option [ngValue]="5">5</option>
          <option [ngValue]="10">10</option>
          <option [ngValue]="20">20</option>
          <option [ngValue]="50">50</option>
        </select>
      </label>
      <button (click)="nuevaMascota()">Nueva mascota</button>
    </div>

    <p class="success" *ngIf="mensaje">{{mensaje}}</p>
    <p class="error" *ngIf="error">{{error}}</p>

    <div class="card" *ngIf="mostrandoFormulario">
      <h3>{{editando ? 'Editar mascota' : 'Nueva mascota'}}</h3>
      <form (ngSubmit)="guardar()">
        <input [(ngModel)]="form.duenioId" name="duenioId" type="number" placeholder="ID del dueño" required min="1" />
        <input [(ngModel)]="form.nombre" name="nombre" placeholder="Nombre" required maxlength="50" />
        <input [(ngModel)]="form.especie" name="especie" placeholder="Especie" required maxlength="30" />
        <input [(ngModel)]="form.raza" name="raza" placeholder="Raza" required maxlength="50" />
        <input [(ngModel)]="form.fechaNacimiento" name="fechaNacimiento" type="date" required />
        <button type="submit">{{editando ? 'Guardar cambios' : 'Crear'}}</button>
        <button type="button" (click)="cancelarEdicion()">Cancelar</button>
      </form>
    </div>

    <p *ngIf="cargando">Cargando...</p>
    <p *ngIf="!cargando && mascotas.length === 0">No hay mascotas que coincidan con los filtros.</p>

    <table class="table" *ngIf="!cargando && mascotas.length > 0">
      <thead>
        <tr>
          <th>Nombre</th><th>Especie</th><th>Raza</th><th>Nacimiento</th><th>Dueño</th><th></th>
        </tr>
      </thead>
      <tbody>
        <tr *ngFor="let m of mascotas">
          <td>{{m.nombre}}</td>
          <td>{{m.especie}}</td>
          <td>{{m.raza}}</td>
          <td>{{m.fechaNacimiento}}</td>
          <td>{{m.duenioNombre}}</td>
          <td>
            <button (click)="editar(m)">Editar</button>
            <button (click)="eliminar(m)">Eliminar</button>
          </td>
        </tr>
      </tbody>
    </table>

    <div class="pagination" *ngIf="totalElements > 0">
      <button (click)="paginaAnterior()" [disabled]="first">Anterior</button>
      <span>Página {{page + 1}} de {{totalPages}} · {{totalElements}} resultados</span>
      <button (click)="paginaSiguiente()" [disabled]="last">Siguiente</button>
    </div>
  </div>`
})
export class MascotasComponent implements OnInit {
  mascotas: Mascota[] = [];
  totalElements = 0;
  totalPages = 0;
  page = 0;
  size = 10;
  sort = 'id,asc';
  first = true;
  last = true;

  filtroNombre = '';
  filtroEspecie = '';
  filtroRaza = '';

  cargando = false;
  error = '';
  mensaje = '';

  mostrandoFormulario = false;
  editando: Mascota | null = null;
  form: MascotaRequest = this.formVacio();

  constructor(private mascotaService: MascotaService) {}

  ngOnInit() {
    this.cargar();
  }

  cargar() {
    this.cargando = true;
    this.error = '';
    this.mascotaService.listar({
      nombre: this.filtroNombre || undefined,
      especie: this.filtroEspecie || undefined,
      raza: this.filtroRaza || undefined,
      page: this.page,
      size: this.size,
      sort: this.sort
    }).subscribe({
      next: res => {
        this.mascotas = res.content;
        this.totalElements = res.totalElements;
        this.totalPages = res.totalPages;
        this.first = res.first;
        this.last = res.last;
        this.cargando = false;
      },
      error: (err: HttpErrorResponse) => {
        this.error = err.error?.message ?? 'No se pudieron cargar las mascotas.';
        this.cargando = false;
      }
    });
  }

  buscar() {
    this.page = 0;
    this.cargar();
  }

  limpiarFiltros() {
    this.filtroNombre = '';
    this.filtroEspecie = '';
    this.filtroRaza = '';
    this.page = 0;
    this.cargar();
  }

  paginaAnterior() {
    if (!this.first) {
      this.page--;
      this.cargar();
    }
  }

  paginaSiguiente() {
    if (!this.last) {
      this.page++;
      this.cargar();
    }
  }

  cambiarTamano(size: number) {
    this.size = size;
    this.page = 0;
    this.cargar();
  }

  cambiarOrden(sort: string) {
    this.sort = sort;
    this.page = 0;
    this.cargar();
  }

  nuevaMascota() {
    this.editando = null;
    this.form = this.formVacio();
    this.mostrandoFormulario = true;
  }

  editar(m: Mascota) {
    this.editando = m;
    this.form = {
      duenioId: m.duenioId,
      nombre: m.nombre,
      especie: m.especie,
      raza: m.raza,
      fechaNacimiento: m.fechaNacimiento
    };
    this.mostrandoFormulario = true;
  }

  cancelarEdicion() {
    this.editando = null;
    this.form = this.formVacio();
    this.mostrandoFormulario = false;
  }

  guardar() {
    this.error = '';
    this.mensaje = '';
    const accion = this.editando
      ? this.mascotaService.actualizar(this.editando.id, this.form)
      : this.mascotaService.crear(this.form);

    accion.subscribe({
      next: () => {
        this.mensaje = this.editando ? 'Mascota actualizada correctamente.' : 'Mascota creada correctamente.';
        this.cancelarEdicion();
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.error = err.error?.message ?? 'No se pudo guardar la mascota.';
      }
    });
  }

  eliminar(m: Mascota) {
    if (!confirm(`¿Eliminar a ${m.nombre}?`)) {
      return;
    }
    this.error = '';
    this.mensaje = '';
    this.mascotaService.eliminar(m.id).subscribe({
      next: () => {
        this.mensaje = 'Mascota eliminada correctamente.';
        this.cargar();
      },
      error: (err: HttpErrorResponse) => {
        this.error = err.error?.message ?? 'No se pudo eliminar la mascota.';
      }
    });
  }

  private formVacio(): MascotaRequest {
    return { duenioId: 0, nombre: '', especie: '', raza: '', fechaNacimiento: '' };
  }
}
