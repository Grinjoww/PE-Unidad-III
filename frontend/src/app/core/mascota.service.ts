import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Mascota, MascotaFiltro, MascotaRequest, PageResponse } from './mascota.model';

@Injectable({ providedIn: 'root' })
export class MascotaService {
  private api = '/api/mascotas';

  constructor(private http: HttpClient) {}

  listar(filtro: MascotaFiltro): Observable<PageResponse<Mascota>> {
    let params = new HttpParams()
      .set('page', filtro.page)
      .set('size', filtro.size)
      .set('sort', filtro.sort);
    if (filtro.nombre) params = params.set('nombre', filtro.nombre);
    if (filtro.especie) params = params.set('especie', filtro.especie);
    if (filtro.raza) params = params.set('raza', filtro.raza);
    return this.http.get<PageResponse<Mascota>>(this.api, { params });
  }

  buscar(id: number): Observable<Mascota> {
    return this.http.get<Mascota>(`${this.api}/${id}`);
  }

  crear(request: MascotaRequest): Observable<Mascota> {
    return this.http.post<Mascota>(this.api, request);
  }

  actualizar(id: number, request: MascotaRequest): Observable<Mascota> {
    return this.http.put<Mascota>(`${this.api}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`);
  }
}
